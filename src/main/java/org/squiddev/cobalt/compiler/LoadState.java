/*
 * The MIT License (MIT)
 *
 * Original Source: Copyright (c) 2009-2011 Luaj.org. All rights reserved.
 * Modifications: Copyright (c) 2015-2020 SquidDev
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package org.squiddev.cobalt.compiler;

import org.squiddev.cobalt.LuaState;
import org.squiddev.cobalt.LuaString;
import org.squiddev.cobalt.LuaTable;
import org.squiddev.cobalt.Prototype;
import org.squiddev.cobalt.function.LuaClosure;
import org.squiddev.cobalt.function.LuaFunction;
import org.squiddev.cobalt.function.LuaInterpretedFunction;
import org.squiddev.cobalt.function.Upvalue;
import org.squiddev.cobalt.lib.jse.JsePlatform;

import java.io.IOException;
import java.io.InputStream;

import static org.squiddev.cobalt.ValueFactory.valueOf;

/**
 * Class to manage loading of {@link Prototype} instances.
 *
 * The {@link LoadState} class exposes one main function,
 * namely {@link #load(LuaState, InputStream, LuaString, LuaTable)},
 * to be used to load code from a particular input stream.
 *
 * A simple pattern for loading and executing code is
 * <pre> {@code
 * LuaValue _G = JsePlatform.standardGlobals();
 * LoadState.load( new FileInputStream("main.lua"), "main.lua", _G ).call();
 * } </pre>
 * This should work regardless of which {@link LuaCompiler}
 * has been installed.
 *
 * Prior to loading code, a compiler should be installed.
 *
 * By default, when using {@link JsePlatform} to construct globals, the {@link LuaC} compiler is installed.
 *
 * @see LuaCompiler
 * @see LuaClosure
 * @see LuaFunction
 * @see LoadState#load(LuaState, InputStream, LuaString, LuaTable)
 * @see LuaC
 */
public final class LoadState {
	/**
	 * Interface for the compiler, if it is installed.
	 *
	 * See the {@link LuaClosure} documentation for examples of how to use the compiler.
	 *
	 * @see LuaClosure
	 * @see #load(InputStream, LuaString, LuaString, LuaTable)
	 */
	public interface LuaCompiler {
		/**
		 * Load into a Closure or LuaFunction from a Stream and initializes the environment
		 *
		 * @param stream   Stream to read
		 * @param filename Name of chunk
		 * @param mode
		 * @param env      Environment to load
		 * @return The loaded function
		 * @throws IOException      On stream read error
		 * @throws CompileException If the stream cannot be loaded.
		 */
		LuaFunction load(InputStream stream, LuaString filename, LuaString mode, LuaTable env) throws IOException, CompileException;
	}

	/**
	 * Signature byte indicating the file is a compiled binary chunk
	 */
	private static final byte[] LUA_SIGNATURE = {27, 'L', 'u', 'a'};

	/**
	 * Name for compiled chunks
	 */
	public static final LuaString SOURCE_BINARY_STRING = valueOf("=?");


	public static LuaFunction load(LuaState state, InputStream stream, String name, LuaTable env) throws IOException, CompileException {
		return load(state, stream, valueOf(name), env);
	}

	/**
	 * Load lua in either binary or text form from an input stream.
	 *
	 * @param state  The current lua state
	 * @param stream InputStream to read, after having read the first byte already
	 * @param name   Name to apply to the loaded chunk
	 * @param env    Environment to load into
	 * @return {@link Prototype} that was loaded
	 * @throws IllegalArgumentException If the signature is bac
	 * @throws IOException              If an IOException occurs
	 * @throws CompileException         If the stream cannot be loaded.
	 */
	public static LuaFunction load(LuaState state, InputStream stream, LuaString name, LuaTable env) throws IOException, CompileException {
		return load(state, stream, name, null, env);
	}

	public static LuaFunction load(LuaState state, InputStream stream, LuaString name, LuaString mode, LuaTable env) throws IOException, CompileException {
		if (state.compiler != null) return state.compiler.load(stream, name, mode, env);

		int firstByte = stream.read();
		if (firstByte != LUA_SIGNATURE[0]) throw new CompileException("no compiler");
		checkMode(mode, "binary");

		Prototype p = loadBinaryChunk(firstByte, stream, name);
		LuaInterpretedFunction closure = new LuaInterpretedFunction(p, env);
		closure.nilUpvalues();
		if (p.isLua52 && p.nups == 1) {
			closure.setUpvalue(0, new Upvalue(env));
		}
		return closure;
	}

	/**
	 * Load lua thought to be a binary chunk from its first byte from an input stream.
	 *
	 * @param firstByte the first byte of the input stream
	 * @param stream    InputStream to read, after having read the first byte already
	 * @param name      Name to apply to the loaded chunk
	 * @return {@link Prototype} that was loaded
	 * @throws IllegalArgumentException If the signature is bac
	 * @throws IOException              If an IOException occurs
	 * @throws CompileException         If the stream cannot be loaded.
	 */
	public static Prototype loadBinaryChunk(int firstByte, InputStream stream, LuaString name) throws IOException, CompileException {
		name = getSourceName(name);
		// check rest of signature
		if (firstByte != LUA_SIGNATURE[0]
			|| stream.read() != LUA_SIGNATURE[1]
			|| stream.read() != LUA_SIGNATURE[2]
			|| stream.read() != LUA_SIGNATURE[3]) {
			throw new IllegalArgumentException("bad signature");
		}

		// load file as a compiled chunk
		BytecodeLoader s = new BytecodeLoader(stream);
		s.loadHeader();
		return s.loadFunction(name);
	}

	/**
	 * Construct a source name from a supplied chunk name
	 *
	 * @param name String name that appears in the chunk
	 * @return source file name
	 */
	public static LuaString getSourceName(LuaString name) {
		if (name.length > 0) {
			byte first = name.bytes[name.offset];
			switch (first) {
				case '@':
				case '=':
					return name.substring(1);
				case 27:
					return SOURCE_BINARY_STRING;
				default:
					return name;
			}
		}

		return name;
	}

	private static final int NAME_LENGTH = 60;
	private static final int FILE_LENGTH = NAME_LENGTH - " '...' ".length() - 1;
	private static final int STRING_LENGTH = NAME_LENGTH - " [string \"...\"] ".length() - 1;

	private static final LuaString REMAINING = valueOf("...");
	private static final LuaString STRING = valueOf("[string \"");
	private static final LuaString EMPTY_STRING = valueOf("[string \"\"]");
	private static final LuaString NEW_LINES = valueOf("\r\n");

	public static LuaString getShortName(LuaString name) {
		if (name.length == 0) return EMPTY_STRING;
		switch (name.luaByte(0)) {
			case '=':
				return name.substring(1, Math.min(NAME_LENGTH, name.length));
			case '@': { // out = "source", or "...source"
				if (name.length - 1 > FILE_LENGTH) {
					byte[] bytes = new byte[FILE_LENGTH + 3];
					REMAINING.copyTo(bytes, 0);
					name.copyTo(name.length - FILE_LENGTH, bytes, REMAINING.length, FILE_LENGTH);
					return valueOf(bytes);
				} else {
					return name.substring(1);
				}
			}
		}

		int len = name.indexOfAny(NEW_LINES);
		boolean truncate = false;

		if (len < 0) {
			len = name.length;
		} else {
			// We're not at the end of the string so we add a ...
			truncate = true;
		}

		if (len > STRING_LENGTH) {
			truncate = true;
			len = STRING_LENGTH;
		}

		byte[] out = new byte[NAME_LENGTH];
		STRING.copyTo(out, 0);
		int offset = STRING.length;
		offset = name.copyTo(0, out, offset, len);
		if (truncate) offset = REMAINING.copyTo(out, offset);

		out[offset++] = '"';
		out[offset++] = ']';

		return LuaString.valueOf(out, 0, offset);
	}

	public static void checkMode(LuaString mode, String current) throws CompileException {
		if (mode != null && mode.indexOf((byte) current.charAt(0), 0) == -1) {
			throw new CompileException("attempt to load a " + current + " chunk (mode is " + mode + ")");
		}
	}
}

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
package org.squiddev.cobalt;

import org.squiddev.cobalt.compiler.CompileException;
import org.squiddev.cobalt.compiler.LoadState;
import org.squiddev.cobalt.debug.DebugFrame;
import org.squiddev.cobalt.debug.DebugState;
import org.squiddev.cobalt.function.LuaFunction;
import org.squiddev.cobalt.function.VarArgFunction;
import org.squiddev.cobalt.lib.jse.JseIoLib;
import org.squiddev.cobalt.lib.jse.JsePlatform;
import org.squiddev.cobalt.lib.platform.FileResourceManipulator;

import java.io.*;
import java.time.ZoneOffset;
import java.util.TimeZone;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

public class ScriptHelper extends FileResourceManipulator {
	private final String subdir;
	public LuaState state;
	public LuaTable globals;

	public ScriptHelper(String subdir) {
		this.subdir = subdir;
	}

	public void setup() {
		setup(x -> {
		});
	}

	public void setup(Consumer<LuaState.Builder> extend) {
		LuaState.Builder builder = LuaState.builder()
			.resourceManipulator(this)
			.stdin(new InputStream() {
				@Override
				public int read() {
					return -1;
				}
			});
		extend.accept(builder);
		setupCommon(builder.build());
	}

	public void setupQuiet() {
		setupCommon(LuaState.builder()
			.resourceManipulator(this)
			.stdout(new PrintStream(new OutputStream() {
				@Override
				public void write(int b) {
				}

				@Override
				public void write(byte[] b, int off, int len) {
				}
			}))
			.build());
	}

	private void setupCommon(LuaState state) {
		this.state = state;
		globals = JsePlatform.debugGlobals(state);
		globals.rawset("id_", new VarArgFunction() {
			@Override
			public Varargs invoke(LuaState state, Varargs args) {
				return args;
			}
		});
		TimeZone.setDefault(TimeZone.getTimeZone(ZoneOffset.UTC));
	}

	@Override
	public InputStream findResource(String filename) {
		InputStream stream = getClass().getResourceAsStream(subdir + filename);
		return stream == null ? super.findResource(filename) : stream;
	}

	/**
	 * Runs a test and compares the output
	 *
	 * @param testName The name of the test file to run
	 */
	public void runComparisonTest(String testName) throws Exception {
		// Override print()
		final ByteArrayOutputStream output = new ByteArrayOutputStream();
		final PrintStream oldps = state.stdout;
		final PrintStream ps = new PrintStream(output);
		state.stdout = ps;
		globals.load(state, new JseIoLib());

		// Run the script
		try {
			LuaThread.runMain(state, loadScript(testName));

			ps.flush();
			String actualOutput = new String(output.toByteArray());
			String expectedOutput = getExpectedOutput(testName);
			actualOutput = actualOutput.replaceAll("\r\n", "\n");
			expectedOutput = expectedOutput.replaceAll("\r\n", "\n");

			assertEquals(expectedOutput, actualOutput);
		} catch (LuaError e) {
			System.out.println(new String(output.toByteArray()));
			throw e;
		} finally {
			state.stdout = oldps;
			ps.close();
		}
	}

	/**
	 * Loads a script into the global table
	 *
	 * @param name The name of the file
	 * @return The loaded LuaFunction
	 * @throws IOException
	 */
	public LuaFunction loadScript(String name) throws IOException, CompileException {
		InputStream script = findResource(name + ".lua");
		if (script == null) fail("Could not load script for test case: " + name);
		try {
			return LoadState.load(state, script, "@" + name + ".lua", globals);
		} finally {
			script.close();
		}
	}

	public Varargs runWithDump(String script) throws InterruptedException, LuaError, IOException, CompileException {
		return runWithDump(loadScript(script));
	}

	public Varargs runWithDump(LuaFunction function) throws InterruptedException, LuaError {
		try {
			return LuaThread.runMain(state, function);
		} catch (LuaError e) {
			DebugState debug = state.getCurrentThread().getDebugState();
			int level = 0;
			while (true) {
				DebugFrame frame = debug.getFrame(level++);
				if (frame == null) break;

				System.out.printf("%d %s\n", level, frame.func.debugName());

				if (frame.closure == null || frame.stack == null) continue;
				Prototype proto = frame.closure.getPrototype();
				for (int local = 0; local < proto.maxstacksize; local++) {
					LuaString name = frame.getLocalName(local + 1);
					if (name != null) System.out.printf("  %02x | %10s = %s\n", local, name, frame.stack[local]);
				}
			}
			throw e;
		}
	}

	private String getExpectedOutput(final String name) throws IOException {
		InputStream output = this.findResource(name + ".out");
		if (output == null) fail("Failed to get comparison output for " + name);
		try {
			return readString(output);
		} finally {
			output.close();
		}
	}

	private String readString(InputStream is) throws IOException {
		ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
		byte[] buf = new byte[1024];
		int r;
		while ((r = is.read(buf)) >= 0) {
			outputStream.write(buf, 0, r);
		}
		return new String(outputStream.toByteArray());
	}

}

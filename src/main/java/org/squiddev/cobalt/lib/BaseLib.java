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
package org.squiddev.cobalt.lib;

import jdk.nashorn.internal.runtime.regexp.joni.ast.ConsAltNode;
import org.squiddev.cobalt.*;
import org.squiddev.cobalt.compiler.LoadState;
import org.squiddev.cobalt.compiler.LuaC;
import org.squiddev.cobalt.debug.DebugFrame;
import org.squiddev.cobalt.debug.DebugHandler;
import org.squiddev.cobalt.debug.DebugState;
import org.squiddev.cobalt.function.*;
import org.squiddev.cobalt.lib.jse.JsePlatform;
import org.squiddev.cobalt.lib.platform.ResourceManipulator;

import java.io.InputStream;

import static org.squiddev.cobalt.OperationHelper.noUnwind;
import static org.squiddev.cobalt.ValueFactory.valueOf;
import static org.squiddev.cobalt.ValueFactory.varargsOf;
import static org.squiddev.cobalt.debug.DebugFrame.FLAG_ERROR;
import static org.squiddev.cobalt.debug.DebugFrame.FLAG_YPCALL;

/**
 * Subclass of {@link LibFunction} which implements the lua basic library functions.
 *
 * This contains all library functions listed as "basic functions" in the lua documentation for JME.
 * The functions dofile and loadfile use the
 * {@link LuaState#resourceManipulator} instance to find resource files.
 * The default loader chain in {@link PackageLib} will use these as well.
 *
 * This is a direct port of the corresponding library in C.
 *
 * @see ResourceManipulator
 * @see LibFunction
 * @see JsePlatform
 * @see <a href="http://www.lua.org/manual/5.1/manual.html#5.1">http://www.lua.org/manual/5.1/manual.html#5.1</a>
 */
public class BaseLib implements LuaLibrary {
	private static final LuaString STDIN_STR = valueOf("=stdin");
	private static final LuaString FUNCTION_STR = valueOf("function");
	private static final LuaString LOAD_MODE = valueOf("bt");

	private LuaValue next;
	private LuaValue inext;

	private static final String[] LIB2_KEYS = {
		"collectgarbage", // ( opt [,arg] ) -> value
		"error", // ( message [,level] ) -> ERR
		"setfenv", // (f, table) -> void
	};
	private static final String[] LIBV_KEYS = {
		"assert", // ( v [,message] ) -> v, message | ERR
		"dofile", // ( filename ) -> result1, ...
		"getfenv", // ( [f] ) -> env
		"getmetatable", // ( object ) -> table
		"loadfile", // ( [filename] ) -> chunk | nil, msg
		"loadstring", // ( string [,chunkname] ) -> chunk | nil, msg
		"print", // (...) -> void
		"select", // (f, ...) -> value1, ...
		"unpack", // (list [,i [,j]]) -> result1, ...
		"type",  // (v) -> value
		"rawequal", // (v1, v2) -> boolean
		"rawget", // (table, index) -> value
		"rawset", // (table, index, value) -> table
		"setmetatable", // (table, metatable) -> table
		"tostring", // (e) -> value
		"tonumber", // (e [,base]) -> value
		"pairs", // "pairs" (t) -> iter-func, t, nil
		"ipairs", // "ipairs", // (t) -> iter-func, t, 0
		"next", // "next"  ( table, [index] ) -> next-index, next-value
		"__inext", // "inext" ( table, [int-index] ) -> next-index, next-value
		"rawlen", // "rawlen" ( table | string ) -> int
	};
	private static final String[] LIBR_KEYS = {
		"pcall", // (f, arg1, ...) -> status, result1, ...
		"xpcall", // (f, err) -> result1, ...
		"load", // ( func [,chunkname] ) -> chunk | nil, msg
	};

	@Override
	public LuaValue add(LuaState state, LuaTable env) {
		env.rawset("_G", env);
		env.rawset("_VERSION", valueOf(Lua._VERSION));
		LibFunction.bind(env, BaseLib2::new, LIB2_KEYS);
		LibFunction.bind(env, () -> new BaseLibV(this), LIBV_KEYS);
		LibFunction.bind(env, BaseLibR::new, LIBR_KEYS);

		// remember next, and inext for use in pairs and ipairs
		next = env.rawget("next");
		inext = env.rawget("__inext");

		env.rawset("_VERSION", valueOf("Lua 5.2"));

		return env;
	}

	private static final class BaseLib2 extends TwoArgFunction {
		@Override
		public LuaValue call(LuaState state, LuaValue arg1, LuaValue arg2) throws LuaError {
			switch (opcode) {
				case 0: // "collectgarbage", // ( opt [,arg] ) -> value
					String s = arg1.optString("collect");
					if ("collect".equals(s)) {
						System.gc();
						return Constants.ZERO;
					} else if ("count".equals(s)) {
						Runtime rt = Runtime.getRuntime();
						long used = rt.totalMemory() - rt.freeMemory();
						return valueOf(used / 1024.);
					} else if ("step".equals(s)) {
						System.gc();
						return Constants.TRUE;
					} else {
						throw ErrorFactory.argError(1, "invalid option");
					}
				case 1: // "error", // ( message [,level] ) -> ERR
					throw new LuaError(arg1.isNil() ? Constants.NIL : arg1, arg2.optInteger(1));
				case 2: { // "setfenv", // (f, table) -> void
					LuaTable t = arg2.checkTable();
					LuaValue f = getfenvobj(state, arg1);
					if (!f.isThread() && !f.isClosure()) {
						throw new LuaError("'setfenv' cannot change environment of given object");
					}
					f.setfenv(t);
					return f.isThread() ? Constants.NONE : f;
				}
			}
			return Constants.NIL;
		}
	}

	private static LuaValue getfenvobj(LuaState state, LuaValue arg) throws LuaError {
		if (arg.isFunction()) {
			return arg;
		}
		int level = arg.optInteger(1);
		Varargs.argCheck(level >= 0, 1, "level must be non-negative");
		if (level == 0) {
			return state.getCurrentThread();
		}
		LuaValue f = LuaThread.getCallstackFunction(state, level - 1);
		Varargs.argCheck(f != null, 1, "invalid level");
		return f;
	}

	private static final class BaseLibV extends VarArgFunction {
		private final BaseLib baselib;

		private BaseLibV(BaseLib baselib) {
			this.baselib = baselib;
		}

		@Override
		public Varargs invoke(LuaState state, Varargs args) throws LuaError, UnwindThrowable {
			// Note: dofile, tostring and pairs throw UnwindThrowable. This is safe, as it's a tail call.

			switch (opcode) {
				case 0: // "assert", // ( v [,message] ) -> v, message | ERR
					if (!args.first().toBoolean()) {
						throw new LuaError(args.count() > 1 ? args.arg(2).optString("assertion failed!") : "assertion failed!");
					}
					return args;
				case 1: // "dofile", // ( filename ) -> result1, ...
				{
					Varargs v = args.isNil(1) ?
						BaseLib.loadStream(state, state.stdin, STDIN_STR) :
						BaseLib.loadFile(state, args.arg(1).checkString());
					if (v.isNil(1)) {
						throw new LuaError(v.arg(2).toString());
					} else {
						return OperationHelper.invoke(state, v.first(), Constants.NONE);
					}
				}
				case 2: // "getfenv", // ( [f] ) -> env
				{
					LuaValue f = getfenvobj(state, args.first());
					LuaValue e = f.getfenv();
					return e != null ? e : Constants.NIL;
				}
				case 3: // "getmetatable", // ( object ) -> table
				{
					LuaTable mt = args.checkValue(1).getMetatable(state);
					return mt != null ? mt.rawget(Constants.METATABLE).optValue(mt) : Constants.NIL;
				}
				case 4: // "loadfile", // ( [filename] ) -> chunk | nil, msg
				{
					return args.isNil(1) ?
						BaseLib.loadStream(state, state.stdin, STDIN_STR) :
						BaseLib.loadFile(state, args.arg(1).checkString());
				}
				case 5: // "loadstring", // ( string [,chunkname] ) -> chunk | nil, msg
				{
					LuaString script = args.arg(1).checkLuaString();
					return BaseLib.loadStream(state, script.toInputStream(), args.arg(2).optLuaString(script));
				}
				case 6: // "print", // (...) -> void
				{
					return noUnwind(state, () -> {
						LuaValue tostring = OperationHelper.getTable(state, state.globalTable, valueOf("tostring"));
						for (int i = 1, n = args.count(); i <= n; i++) {
							if (i > 1) state.stdout.write('\t');
							LuaString s = OperationHelper.call(state, tostring, args.arg(i)).strvalue();
							int z = s.indexOf((byte) 0, 0);
							state.stdout.write(s.bytes, s.offset, z >= 0 ? z : s.length);
						}
						state.stdout.println();
						return Constants.NONE;
					});
				}
				case 7: // "select", // (f, ...) -> value1, ...
				{
					int n = args.count() - 1;
					if (args.first().equals(valueOf("#"))) {
						return valueOf(n);
					}
					int i = args.arg(1).checkInteger();
					if (i == 0 || i < -n) {
						throw ErrorFactory.argError(1, "index out of range");
					}
					return args.subargs(i < 0 ? n + i + 2 : i + 1);
				}
				case 8: // "unpack", // (list [,i [,j]]) -> result1, ...
				{
					int na = args.count();
					LuaTable t = args.arg(1).checkTable();
					int n = t.length();
					int i = na >= 2 ? args.arg(2).optInteger(1) : 1;
					int j = na >= 3 ? args.arg(3).optInteger(n) : n;
					n = j - i + 1;
					if (n < 0) return Constants.NONE;
					if (n == 1) return t.rawget(i);
					if (n == 2) return varargsOf(t.rawget(i), t.rawget(j));
					LuaValue[] v = new LuaValue[n];
					for (int k = 0; k < n; k++) {
						v[k] = t.rawget(i + k);
					}
					return varargsOf(v);
				}
				case 9: // "type",  // (v) -> value
					return valueOf(args.checkValue(1).typeName());
				case 10: // "rawequal", // (v1, v2) -> boolean
					return valueOf(args.checkValue(1) == args.checkValue(2));
				case 11: // "rawget", // (table, index) -> value
					return args.arg(1).checkTable().rawget(args.checkValue(2));
				case 12: { // "rawset", // (table, index, value) -> table
					LuaTable t = args.arg(1).checkTable();
					LuaValue k = args.checkValue(2);
					LuaValue v = args.checkValue(3);
					if (k.isNil()) throw new LuaError("table index is nil");
					t.rawset(k.checkValidKey(), v);
					return t;
				}
				case 13: { // "setmetatable", // (table, metatable) -> table
					final LuaValue t = args.first();
					final LuaTable mt0 = t.getMetatable(state);
					if (mt0 != null && !mt0.rawget(Constants.METATABLE).isNil()) {
						throw new LuaError("cannot change a protected metatable");
					}
					final LuaValue mt = args.checkValue(2);
					t.setMetatable(state, mt.isNil() ? null : mt.checkTable());
					return t;
				}
				case 14:  // "tostring", // (e) -> value
					return OperationHelper.toString(state, args.checkValue(1));
				case 15: { // "tonumber", // (e [,base]) -> value
					LuaValue arg1 = args.checkValue(1);
					final int base = args.arg(2).optInteger(10);
					if (base == 10) {  /* standard conversion */
						return arg1.toNumber();
					} else {
						if (base < 2 || base > 36) {
							throw ErrorFactory.argError(2, "base out of range");
						}
						return arg1.checkLuaString().tonumber(base);
					}
				}
				case 16: { // "pairs" (t) -> iter-func, t, nil
					LuaValue value = args.checkValue(1);
					LuaValue pairs = value.metatag(state, Constants.PAIRS);
					if (pairs.isNil()) {
						return varargsOf(baselib.next, value, Constants.NIL);
					} else {
						return OperationHelper.invoke(state, pairs, value);
					}
				}
				case 17: // "ipairs", // (t) -> iter-func, t, 0
					return varargsOf(baselib.inext, args.arg(1).checkTable(), Constants.ZERO);
				case 18: // "next"  ( table, [index] ) -> next-index, next-value
					return args.arg(1).checkTable().next(args.arg(2));
				case 19: // "inext" ( table, [int-index] ) -> next-index, next-value
					return args.arg(1).checkTable().inext(args.arg(2));
				case 20: {  // "rawlen" ( table | string ) -> int
					LuaValue v = args.arg(1);
					switch (v.type()) {
						case Constants.TTABLE:
							return ValueFactory.valueOf(v.checkTable().length());
						case Constants.TSTRING:
							return ValueFactory.valueOf(v.checkLuaString().length);
						default:
							throw ErrorFactory.argError(1, "table or string expected");
					}
				}
			}
			return Constants.NONE;
		}
	}

	private static class BaseLibR extends ResumableVarArgFunction<PCallState> {
		@Override
		protected Varargs invoke(LuaState state, DebugFrame di, Varargs args) throws LuaError, UnwindThrowable {
			switch (opcode) {
				case 0: // "pcall", // (f, arg1, ...) -> status, result1, ...
					return pcall(state, di, args.checkValue(1), args.subargs(2), null);
				case 1: // "xpcall", // (f, err) -> result1, ...
					return pcall(state, di, args.checkValue(1), Constants.NONE, args.checkValue(2));

				case 2: // "load", // ( func|str [,chunkname[, mode[, env]]] ) -> chunk | nil, msg
				{
					// We'd typically rely on the argument checks in LuaValue, but those don't give us argument numbers so we do arg checks ahead of time.
					Varargs.argCheck(args.arg(1).isFunction() || args.arg(1).isString(), 1, "expected function or string, got " + args.arg(1).typeName());
					Varargs.argCheck(args.isNoneOrNil(2) || args.arg(2).isString(), 2, "expected string, got " + args.arg(2).typeName());
					Varargs.argCheck(args.isNoneOrNil(3) || args.arg(3).isString(), 3, "expected string, got " + args.arg(3).typeName());
					Varargs.argCheck(args.isNoneOrNil(4) || args.arg(4).isTable(), 4, "expected table, got " + args.arg(4).typeName());

					LuaValue scriptGen = args.arg(1);
					LuaString chunkName = args.arg(2).optLuaString(null);
					LuaString mode = args.arg(3).optLuaString(LOAD_MODE);
					LuaTable funcEnv = args.arg(4).optTable(state.getCurrentThread().getfenv());

					// If we're a string, load as normal
					LuaValue script = scriptGen.toLuaString();
					if (!script.isNil()) {
						try {
							return LoadState.load(state, ((LuaString) script).toInputStream(), chunkName == null ? (LuaString) script : chunkName, mode, funcEnv);
						} catch (Exception e) {
							return varargsOf(Constants.NIL, LuaError.getMessage(e));
						}
					}

					LuaFunction function = scriptGen.checkFunction();
					Varargs result = pcall(state, di, new ZeroArgFunction() {
						@Override
						public LuaValue call(LuaState state) throws LuaError {
							try {
								InputStream stream = new StringInputStream(state, function);
								return LoadState.load(state, stream, chunkName == null ? FUNCTION_STR : chunkName, mode, funcEnv);
							} catch (Exception e) {
								throw LuaError.wrapMessage(e);
							}
						}
					}, Constants.NONE, state.getCurrentThread().getErrorFunc());

					if (result.first().toBoolean()) {
						return result.arg(2);
					} else {
						return varargsOf(Constants.NIL, result.arg(2));
					}
				}
				default:
					return Constants.NONE;
			}
		}

		@Override
		protected Varargs resumeThis(LuaState state, PCallState pState, Varargs value) {
			state.getCurrentThread().setErrorFunc(pState.oldErrorFunc);

			if (pState.errored) closeUntil(state, pState.frame);
			return finish(pState, value);
		}

		@Override
		public Varargs resumeErrorThis(LuaState state, PCallState pState, LuaError error) throws UnwindThrowable {
			LuaValue value;
			if (pState.errored) {
				value = valueOf("error in error handling");
			} else {
				// Mark this frame as errored, meaning it will not be resumed.
				DebugHandler.getDebugState(state).getStackUnsafe().flags |= FLAG_ERROR;
				// And mark us as being in the error handler.
				pState.errored = true;
				error.fillTraceback(state);
				value = error.value;
			}

			state.getCurrentThread().setErrorFunc(pState.oldErrorFunc);
			closeUntil(state, pState.frame);
			return finish(pState, value);
		}

		private Varargs finish(PCallState pState, Varargs value) {
			switch (opcode) {
				case 0:
				case 1:
					return pState.errored ? varargsOf(Constants.FALSE, value) : varargsOf(Constants.TRUE, value);
				case 2:
					return pState.errored ? varargsOf(Constants.NIL, value) : value;
				default:
					throw new NonResumableException("Cannot resume " + debugName());
			}
		}
	}

	private static final class PCallState {
		DebugFrame frame;
		LuaValue oldErrorFunc;
		boolean errored = false;
	}

	private static Varargs pcall(LuaState state, DebugFrame di, LuaValue func, Varargs args, LuaValue errFunc) throws UnwindThrowable {
		// Mark this frame as being an error handler
		PCallState pState = new PCallState();
		di.state = pState;
		di.flags |= FLAG_YPCALL;

		// Store this frame in the current state.
		pState.frame = di;

		LuaValue oldErr = pState.oldErrorFunc = state.getCurrentThread().setErrorFunc(errFunc);
		try {
			Varargs result = varargsOf(Constants.TRUE, OperationHelper.invoke(state, func, args));

			state.getCurrentThread().setErrorFunc(oldErr);
			return result;
		} catch (Exception | VirtualMachineError e) {
			// Mark this frame as errored, meaning it will not be resumed.
			DebugHandler.getDebugState(state).getStackUnsafe().flags |= FLAG_ERROR;
			// And mark us as being in the error handler.
			pState.errored = true;

			LuaError le = LuaError.wrap(e);
			le.fillTraceback(state);

			state.getCurrentThread().setErrorFunc(oldErr);
			closeUntil(state, di);
			return varargsOf(Constants.FALSE, le.value);
		}
	}

	private static void closeUntil(LuaState state, DebugFrame top) {
		DebugState ds = DebugHandler.getDebugState(state);
		DebugHandler handler = state.debug;

		DebugFrame current;
		while ((current = ds.getStackUnsafe()) != top) {
			current.cleanup();
			handler.onReturnError(ds);
		}
	}

	/**
	 * Load from a named file, returning the chunk or nil,error of can't load
	 *
	 * @param state    The current lua state
	 * @param filename Name of the file
	 * @return Varargs containing chunk, or NIL,error-text on error
	 */
	public static Varargs loadFile(LuaState state, String filename) {
		InputStream is = state.resourceManipulator.findResource(filename);
		if (is == null) {
			return varargsOf(Constants.NIL, valueOf("cannot open " + filename + ": No such file or directory"));
		}
		try {
			return loadStream(state, is, valueOf("@" + filename));
		} finally {
			try {
				is.close();
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}

	private static Varargs loadStream(LuaState state, InputStream is, LuaString chunkname) {
		try {
			if (is == null) {
				return varargsOf(Constants.NIL, valueOf("not found: " + chunkname));
			}
			return LoadState.load(state, is, chunkname, state.globalTable);
		} catch (Exception e) {
			return varargsOf(Constants.NIL, LuaError.getMessage(e));
		}
	}

	private static class StringInputStream extends InputStream {
		private final LuaState state;
		final LuaValue func;
		byte[] bytes;
		int offset, remaining = 0;

		StringInputStream(LuaState state, LuaValue func) {
			this.state = state;
			this.func = func;
		}

		@Override
		public int read() {
			if (remaining <= 0) {
				LuaValue s;
				try {
					s = OperationHelper.noUnwind(state, () -> OperationHelper.call(state, func));
				} catch (LuaError e) {
					throw new UncheckedLuaError(e);
				}

				if (s.isNil()) {
					return -1;
				}
				LuaString ls;
				try {
					ls = s.strvalue();
				} catch (LuaError e) {
					throw new UncheckedLuaError(e);
				}
				bytes = ls.bytes;
				offset = ls.offset;
				remaining = ls.length;
				if (remaining <= 0) {
					return -1;
				}
			}
			--remaining;
			return bytes[offset++];
		}
	}
}

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


import org.squiddev.cobalt.*;
import org.squiddev.cobalt.debug.DebugFrame;
import org.squiddev.cobalt.function.LibFunction;
import org.squiddev.cobalt.function.LuaFunction;
import org.squiddev.cobalt.function.ResumableVarArgFunction;
import org.squiddev.cobalt.lib.jse.JsePlatform;

import static org.squiddev.cobalt.ValueFactory.valueOf;
import static org.squiddev.cobalt.ValueFactory.varargsOf;
import static org.squiddev.cobalt.debug.DebugFrame.FLAG_YPCALL;

/**
 * Subclass of {@link LibFunction} which implements the lua standard {@code coroutine}
 * library.
 *
 * The coroutine library in luaj has the same behavior as the
 * coroutine library in C, but is implemented using Java Threads to maintain
 * the call state between invocations.  Therefore it can be yielded from anywhere,
 * similar to the "Coco" yield-from-anywhere patch available for C-based lua.
 * However, coroutines that are yielded but never resumed to complete their execution
 * may not be collected by the garbage collector.
 *
 * @see LibFunction
 * @see JsePlatform
 * @see <a href="http://www.lua.org/manual/5.1/manual.html#5.2">http://www.lua.org/manual/5.1/manual.html#5.2</a>
 */
public class CoroutineLib extends ResumableVarArgFunction<Object> implements LuaLibrary {
	private static final int CREATE = 0;
	private static final int RESUME = 1;
	private static final int RUNNING = 2;
	private static final int STATUS = 3;
	private static final int YIELD = 4;
	private static final int WRAP = 5;
	private static final int WRAPPED = 6;

	private final LuaThread thread;

	public CoroutineLib() {
		thread = null;
	}

	private CoroutineLib(LuaThread thread) {
		this.thread = thread;
	}

	@Override
	public LuaValue add(LuaState state, LuaTable env) {
		LuaTable t = new LuaTable();
		bind(t, CoroutineLib::new, new String[]{"create", "resume", "running", "status", "yield", "wrap"});
		env.rawset("coroutine", t);
		state.loadedPackages.rawset("coroutine", t);
		return t;
	}

	@Override
	public Varargs invoke(LuaState state, DebugFrame di, Varargs args) throws LuaError, UnwindThrowable {
		switch (opcode) {
			case CREATE: {
				final LuaFunction func = args.arg(1).checkFunction();
				return new LuaThread(state, func);
			}
			case RESUME: {
				di.flags |= FLAG_YPCALL;
				LuaThread thread = args.arg(1).checkThread();
				try {
					Varargs result = LuaThread.resume(state, thread, args.subargs(2));
					return varargsOf(Constants.TRUE, result);
				} catch (LuaError le) {
					return varargsOf(Constants.FALSE, le.value);
				}
			}
			case RUNNING: {
				final LuaThread r = state.getCurrentThread();
				return r.isMainThread() ? Constants.NIL : r;
			}
			case STATUS: {
				return valueOf(args.arg(1).checkThread().getStatus());
			}
			case YIELD:
				return LuaThread.yield(state, args);
			case WRAP: {
				final LuaFunction func = args.arg(1).checkFunction();
				final LuaThread thread = new LuaThread(state, func);
				CoroutineLib cl = new CoroutineLib(thread);
				cl.name = "wrapped";
				cl.opcode = WRAPPED;
				return cl;
			}
			case WRAPPED: {
				return LuaThread.resume(state, thread, args);
			}
			default:
				return Constants.NONE;
		}
	}

	@Override
	public Varargs resumeThis(LuaState state, Object object, Varargs value) {
		switch (opcode) {
			case YIELD:
			case WRAPPED:
				return value;
			case RESUME:
				return varargsOf(Constants.TRUE, value);
			default:
				throw new NonResumableException("Cannot resume " + debugName());
		}
	}

	@Override
	public Varargs resumeErrorThis(LuaState state, Object object, LuaError error) {
		switch (opcode) {
			case RESUME:
				return varargsOf(Constants.FALSE, error.value);
			default:
				throw new NonResumableException("Cannot resume " + debugName());
		}
	}
}

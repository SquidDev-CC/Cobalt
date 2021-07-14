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
package org.squiddev.cobalt.function;

import org.squiddev.cobalt.*;
import org.squiddev.cobalt.compiler.LexState;
import org.squiddev.cobalt.compiler.LoadState;
import org.squiddev.cobalt.compiler.LuaC;
import org.squiddev.cobalt.debug.DebugFrame;
import org.squiddev.cobalt.debug.DebugHandler;
import org.squiddev.cobalt.debug.DebugState;

import static org.squiddev.cobalt.debug.DebugFrame.*;
import static org.squiddev.cobalt.function.LuaInterpreter.*;

/**
 * Extension of {@link LuaFunction} which executes lua bytecode.
 *
 * A {@link LuaInterpretedFunction} is a combination of a {@link Prototype}
 * and a {@link LuaValue} to use as an environment for execution.
 *
 * There are three main ways {@link LuaInterpretedFunction} instances are created:
 * <ul>
 * <li>Construct an instance using {@link #LuaInterpretedFunction(Prototype, LuaTable)}</li>
 * <li>Construct it indirectly by loading a chunk via {@link LoadState.LuaCompiler#load(java.io.InputStream, LuaString, LuaString, LuaTable)}
 * <li>Execute the lua bytecode {@link Lua#OP_CLOSURE} as part of bytecode processing
 * </ul>
 *
 * To construct it directly, the {@link Prototype} is typically created via a compiler such as {@link LuaC}:
 * <pre> {@code
 * InputStream is = new ByteArrayInputStream("print('hello,world').getBytes());
 * Prototype p = LuaC.INSTANCE.compile(is, "script");
 * LuaValue _G = JsePlatform.standardGlobals()
 * LuaClosure f = new LuaClosure(p, _G);
 * }</pre>
 *
 * To construct it indirectly, the {@link LuaC} compiler may be used,
 * which implements the {@link LoadState.LuaCompiler} interface:
 * <pre> {@code
 * LuaFunction f = LuaC.INSTANCE.load(is, "script", _G);
 * }</pre>
 *
 * Typically, a closure that has just been loaded needs to be initialized by executing it,
 * and its return value can be saved if needed:
 * <pre> {@code
 * LuaValue r = f.call();
 * _G.set( "mypkg", r )
 * }</pre>
 *
 * In the preceding, the loaded value is typed as {@link LuaFunction}
 * to allow for the possibility of other compilers such as LuaJC
 * producing {@link LuaFunction} directly without
 * creating a {@link Prototype} or {@link LuaInterpretedFunction}.
 *
 * Since a {@link LuaInterpretedFunction} is a {@link LuaFunction} which is a {@link LuaValue},
 * all the value operations can be used directly such as:
 * <ul>
 * <li>{@link LuaValue#setfenv(LuaTable)}</li>
 * <li>{@link LuaFunction#call(LuaState)}</li>
 * <li>{@link LuaFunction#call(LuaState, LuaValue)}</li>
 * <li>{@link LuaFunction#invoke(LuaState, Varargs)}</li>
 * <li>{@link LuaFunction#invoke(LuaState, Varargs)}</li>
 * <li> ...</li>
 * </ul>
 *
 * @see LuaValue
 * @see LuaFunction
 * @see LuaValue#isClosure()
 * @see LuaValue#checkClosure()
 * @see LuaValue#optClosure(LuaClosure)
 * @see LoadState
 */
public final class LuaInterpretedFunction extends LuaClosure implements Resumable<Object> {
	private static final Upvalue[] NO_UPVALUES = new Upvalue[0];

	public final Prototype p;
	public final Upvalue[] upvalues;

	public LuaInterpretedFunction(Prototype p) {
		this.p = p;
		if (p.isLua52) this.upvalues = p.nups > 0 ? new Upvalue[p.nups] : NO_UPVALUES;
        else this.upvalues = new Upvalue[p.nups + 1];
	}

	/**
	 * Supply the initial environment
	 *
	 * @param p   The prototype to run
	 * @param env The environement to run in
	 */
	public LuaInterpretedFunction(Prototype p, LuaTable env) {
		super(env);
		this.p = p;
		if (p.isLua52) {
			this.upvalues = p.nups > 0 ? new Upvalue[p.nups] : NO_UPVALUES;
			if (env != null) {
				for (int i = 0; i < p.nups && i < p.upvalues.length; i++) {
					if (p.upvalues[i].equals(LuaString.valueOf("_ENV"))) {
						this.upvalues[i] = new Upvalue(env);
					}
				}
			}
		} else {
			this.upvalues = new Upvalue[p.nups + 1];
			this.upvalues[0] = new Upvalue(env);
		}
	}

	public void nilUpvalues() {
		int nups = p.nups;
		if (nups > 0) {
			Upvalue[] upvalues = this.upvalues;
			for (int i = p.isLua52 ? 0 : 1; i < nups; i++) {
				upvalues[i] = new Upvalue(Constants.NIL);
			}
		}
	}

	@Override
	public final LuaValue call(LuaState state) throws LuaError, UnwindThrowable {
		return execute(state, setupCall(state, this, FLAG_FRESH), this).first();
	}

	@Override
	public final LuaValue call(LuaState state, LuaValue arg) throws LuaError, UnwindThrowable {
		return execute(state, setupCall(state, this, arg, FLAG_FRESH), this).first();
	}

	@Override
	public final LuaValue call(LuaState state, LuaValue arg1, LuaValue arg2) throws LuaError, UnwindThrowable {
		return execute(state, setupCall(state, this, arg1, arg2, FLAG_FRESH), this).first();
	}

	@Override
	public final LuaValue call(LuaState state, LuaValue arg1, LuaValue arg2, LuaValue arg3) throws LuaError, UnwindThrowable {
		return execute(state, setupCall(state, this, arg1, arg2, arg3, FLAG_FRESH), this).first();
	}

	@Override
	public final Varargs invoke(LuaState state, Varargs varargs) throws LuaError, UnwindThrowable {
		return execute(state, setupCall(state, this, varargs, FLAG_FRESH), this);
	}

	@Override
	public LuaTable getfenv() {
		if (!p.isLua52) return (LuaTable)upvalues[0].getValue();
		else {
			for (int i = 0; i < p.nups && i < p.upvalues.length; i++) {
				if (p.upvalues[i].equals(LexState.ENV)) {
					return (LuaTable)upvalues[i].getValue();
				}
			}
			return null; // TODO: maybe this should return _G (state.globalTable) instead?
		}
	}

	@Override
	public void setfenv(LuaTable env) {
		if (env == null) throw new NullPointerException("environment must not be null");
		if (!p.isLua52) upvalues[0] = new Upvalue(env);
		else {
			for (int i = 0; i < p.nups && i < p.upvalues.length; i++) {
				if (p.upvalues[i].equals(LexState.ENV)) {
					upvalues[i] = new Upvalue(env);
				}
			}
		}
	}

	@Override
	public Upvalue getUpvalue(int i) {
		return upvalues[i+(p.isLua52 ? 0 : 1)];
	}

	@Override
	public void setUpvalue(int i, Upvalue upvalue) {
		upvalues[i+(p.isLua52 ? 0 : 1)] = upvalue;
	}

	@Override
	public Prototype getPrototype() {
		return p;
	}

	@Override
	public String debugName() {
		return getPrototype().sourceShort() + ":" + getPrototype().linedefined;
	}

	@Override
	public Varargs resume(LuaState state, Object object, Varargs value) throws LuaError, UnwindThrowable {
		DebugState ds = DebugHandler.getDebugState(state);
		DebugFrame di = ds.getStackUnsafe();

		if ((di.flags & FLAG_HOOKED) != 0) {
			// We're resuming in from a hook
			ds.inhook = false;
			di.flags ^= FLAG_HOOKED;

			if ((di.flags & FLAG_HOOKYIELD) != 0) {
				// Yielded within instruction hook, do nothing
			} else if (di.top != -1) {
				// Yielded while returning. This one's pretty simple, but verbose due to how returns are
				// implemented
				return resumeReturn(state, ds, di, this);
			} else {
				// Yielded while calling. Finish off setupCall
				di.pc = di.top = 0;
			}
		} else {
			LuaInterpreter.resume(state, di, this, value);
		}

		return execute(state, di, this);
	}
}

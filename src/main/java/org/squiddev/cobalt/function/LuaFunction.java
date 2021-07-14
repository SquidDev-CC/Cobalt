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
import org.squiddev.cobalt.debug.DebugFrame;
import org.squiddev.cobalt.debug.DebugHook;
import org.squiddev.cobalt.debug.DebugState;

/**
 * Base class for functions implemented in Java.
 *
 * Direct subclass include {@link LibFunction} which is the base class for
 * all built-in library functions coded in Java,
 * and {@link LuaInterpretedFunction}, which represents a lua closure
 * whose bytecode is interpreted when the function is invoked.
 *
 * @see LuaValue
 * @see LibFunction
 * @see LuaInterpretedFunction
 */
public abstract class LuaFunction extends LuaValue implements DebugHook {
	public LuaFunction() {
		super(Constants.TFUNCTION);
	}

	public LuaFunction(LuaTable env) {
		super(Constants.TFUNCTION);
	}

	@Override
	public LuaFunction checkFunction() {
		return this;
	}

	@Override
	public LuaFunction optFunction(LuaFunction defval) {
		return this;
	}

	@Override
	public LuaTable getMetatable(LuaState state) {
		return state.functionMetatable;
	}

	@Override @Deprecated
	public LuaTable getfenv() {
		return null;
	}

	@Override @Deprecated
	public void setfenv(LuaTable env) {

	}

	public String debugName() {
		return toString();
	}

	/**
	 * Call {@code this} with 0 arguments, including metatag processing,
	 * and return only the first return value.
	 *
	 * If {@code this} is a {@link LuaFunction}, call it,
	 * and return only its first return value, dropping any others.
	 * Otherwise, look for the {@link Constants#CALL} metatag and call that.
	 *
	 * If the return value is a {@link Varargs}, only the 1st value will be returned.
	 * To get multiple values, use {@link #invoke(LuaState, Varargs)} instead.
	 *
	 * @param state The current lua state
	 * @return First return value {@code (this())}, or {@link Constants#NIL} if there were none.
	 * @throws LuaError        If the invoked function throws an error.
	 * @throws UnwindThrowable If this function transfers control to another coroutine.
	 * @see #call(LuaState, LuaValue)
	 * @see #call(LuaState, LuaValue, LuaValue)
	 * @see #call(LuaState, LuaValue, LuaValue, LuaValue)
	 * @see #invoke(LuaState, Varargs)
	 */
	public abstract LuaValue call(LuaState state) throws LuaError, UnwindThrowable;

	/**
	 * Call {@code this} with 1 argument, including metatag processing,
	 * and return only the first return value.
	 *
	 * If {@code this} is a {@link LuaFunction}, call it,
	 * and return only its first return value, dropping any others.
	 * Otherwise, look for the {@link Constants#CALL} metatag and call that.
	 *
	 * If the return value is a {@link Varargs}, only the 1st value will be returned.
	 * To get multiple values, use {@link #invoke(LuaState, Varargs)} instead.
	 *
	 * @param state The current lua state
	 * @param arg   First argument to supply to the called function
	 * @return First return value {@code (this(arg))}, or {@link Constants#NIL} if there were none.
	 * @throws LuaError        If the invoked function throws an error.
	 * @throws UnwindThrowable If this function transfers control to another coroutine.
	 * @see #call(LuaState)
	 * @see #call(LuaState, LuaValue, LuaValue)
	 * @see #call(LuaState, LuaValue, LuaValue, LuaValue)
	 * @see #invoke(LuaState, Varargs)
	 */
	public abstract LuaValue call(LuaState state, LuaValue arg) throws LuaError, UnwindThrowable;

	/**
	 * Call {@code this} with 2 arguments, including metatag processing,
	 * and return only the first return value.
	 *
	 * If {@code this} is a {@link LuaFunction}, call it,
	 * and return only its first return value, dropping any others.
	 * Otherwise, look for the {@link Constants#CALL} metatag and call that.
	 *
	 * If the return value is a {@link Varargs}, only the 1st value will be returned.
	 * To get multiple values, use {@link #invoke(LuaState, Varargs)} instead.
	 *
	 * @param state The current lua state
	 * @param arg1  First argument to supply to the called function
	 * @param arg2  Second argument to supply to the called function
	 * @return First return value {@code (this(arg1, arg2))}, or {@link Constants#NIL} if there were none.
	 * @throws LuaError        If the invoked function throws an error.
	 * @throws UnwindThrowable If this function transfers control to another coroutine.
	 * @see #call(LuaState)
	 * @see #call(LuaState, LuaValue)
	 * @see #call(LuaState, LuaValue, LuaValue, LuaValue)
	 */
	public abstract LuaValue call(LuaState state, LuaValue arg1, LuaValue arg2) throws LuaError, UnwindThrowable;

	/**
	 * Call {@code this} with 3 arguments, including metatag processing,
	 * and return only the first return value.
	 *
	 * If {@code this} is a {@link LuaFunction}, call it,
	 * and return only its first return value, dropping any others.
	 * Otherwise, look for the {@link Constants#CALL} metatag and call that.
	 *
	 * If the return value is a {@link Varargs}, only the 1st value will be returned.
	 * To get multiple values, use {@link #invoke(LuaState, Varargs)} instead.
	 *
	 * @param state The current lua state
	 * @param arg1  First argument to supply to the called function
	 * @param arg2  Second argument to supply to the called function
	 * @param arg3  Second argument to supply to the called function
	 * @return First return value {@code (this(arg1, arg2, arg3))}, or {@link Constants#NIL} if there were none.
	 * @throws LuaError        If the invoked function throws an error.
	 * @throws UnwindThrowable If this function transfers control to another coroutine.
	 * @see #call(LuaState)
	 * @see #call(LuaState, LuaValue)
	 * @see #call(LuaState, LuaValue, LuaValue)
	 */
	public abstract LuaValue call(LuaState state, LuaValue arg1, LuaValue arg2, LuaValue arg3) throws LuaError, UnwindThrowable;

	/**
	 * Call {@code this} with variable arguments, including metatag processing,
	 * and retain all return values in a {@link Varargs}.
	 *
	 * If {@code this} is a {@link LuaFunction}, call it, and return all values.
	 * Otherwise, look for the {@link Constants#CALL} metatag and call that.
	 *
	 * To get a particular return value, us {@link Varargs#arg(int)}
	 *
	 * @param state The current lua state
	 * @param args  Varargs containing the arguments to supply to the called function
	 * @return All return values as a {@link Varargs} instance.
	 * @throws LuaError        If the invoked function throws an error.
	 * @throws UnwindThrowable If this function transfers control to another coroutine.
	 * @see ValueFactory#varargsOf(LuaValue[])
	 * @see #call(LuaState, LuaValue)
	 */
	public abstract Varargs invoke(LuaState state, Varargs args) throws LuaError, UnwindThrowable;

	@Override
	public void onCall(LuaState state, DebugState ds, DebugFrame frame) throws LuaError, UnwindThrowable {
		call(state, CALL);
	}

	@Override
	public void onTailCall(LuaState state, DebugState ds, DebugFrame frame) throws LuaError, UnwindThrowable {
		call(state, TAILCALL);
	}

	@Override
	public void onReturn(LuaState state, DebugState ds, DebugFrame frame) throws LuaError, UnwindThrowable {
		call(state, RETURN);
	}

	@Override
	public void onCount(LuaState state, DebugState ds, DebugFrame frame) throws LuaError, UnwindThrowable {
		call(state, COUNT);
	}

	@Override
	public void onLine(LuaState state, DebugState ds, DebugFrame frame, int newLine) throws LuaError, UnwindThrowable {
		call(state, LINE, ValueFactory.valueOf(newLine));
	}
}

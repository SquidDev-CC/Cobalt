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
package org.squiddev.cobalt.debug;

import org.squiddev.cobalt.LuaError;
import org.squiddev.cobalt.LuaState;
import org.squiddev.cobalt.LuaString;
import org.squiddev.cobalt.UnwindThrowable;

import static org.squiddev.cobalt.ValueFactory.valueOf;

/**
 * A basic method for registering for debug hooks
 */
public interface DebugHook {
	LuaString CALL = valueOf("call");
	LuaString TAILCALL = valueOf("tail call");
	LuaString LINE = valueOf("line");
	LuaString COUNT = valueOf("count");
	LuaString RETURN = valueOf("return");

	/**
	 * Called after entering a function
	 *
	 * @param state Current lua state
	 * @param ds    The current debug state
	 * @param frame The current frame
	 * @throws LuaError        On a runtime error.
	 * @throws UnwindThrowable If this hook transfers control to another coroutine.
	 */
	void onCall(LuaState state, DebugState ds, DebugFrame frame) throws LuaError, UnwindThrowable;

	/**
	 * Called after entering a function by means of tail call
	 *
	 * @param state Current lua state
	 * @param ds    The current debug state
	 * @param frame The current frame
	 * @throws LuaError On a runtime error.
	 * @throws UnwindThrowable If this hook transfers control to another coroutine.
	 */
	void onTailCall(LuaState state, DebugState ds, DebugFrame frame) throws LuaError, UnwindThrowable;

	/**
	 * Called before exiting a function
	 *
	 * @param state Current lua state
	 * @param ds    The current debug state
	 * @param frame The current frame
	 * @throws LuaError        On a runtime error.
	 * @throws UnwindThrowable If this hook transfers control to another coroutine.
	 */
	void onReturn(LuaState state, DebugState ds, DebugFrame frame) throws LuaError, UnwindThrowable;

	/**
	 * Called before ever 'n' instructions
	 *
	 * @param state Current lua state
	 * @param ds    The current debug state
	 * @param frame The current frame
	 * @throws LuaError        On a runtime error.
	 * @throws UnwindThrowable If this hook transfers control to another coroutine.
	 */
	void onCount(LuaState state, DebugState ds, DebugFrame frame) throws LuaError, UnwindThrowable;

	/**
	 * Called before each line changes
	 *
	 * @param state   Current lua state
	 * @param ds      The current debug state
	 * @param frame   The current frame
	 * @param newLine The new new line
	 * @throws LuaError        On a runtime error.
	 * @throws UnwindThrowable If this hook transfers control to another coroutine.
	 */
	void onLine(LuaState state, DebugState ds, DebugFrame frame, int newLine) throws LuaError, UnwindThrowable;
}

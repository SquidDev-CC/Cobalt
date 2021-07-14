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
import org.squiddev.cobalt.UnwindThrowable;
import org.squiddev.cobalt.function.LuaFunction;

import static org.squiddev.cobalt.debug.DebugFrame.*;

/**
 * DebugState is associated with a Thread
 */
public final class DebugState {
	/**
	 * The maximum size the Lua stack can be
	 */
	public static final int MAX_SIZE = 32767;

	/**
	 * The maximum number of "Java" calls.
	 */
	public static final int MAX_JAVA_SIZE = 200;

	public static final int DEFAULT_SIZE = 8;

	private static final DebugFrame[] EMPTY = new DebugFrame[0];

	/**
	 * The thread's lua state
	 */
	private final LuaState state;

	/**
	 * The top function.
	 *
	 * This is limited by {@link #MAX_SIZE}.
	 */
	int top = -1;

	/**
	 * The number of non-interpreter functions on the stack.
	 *
	 * This is limited by {@link #MAX_JAVA_SIZE}.
	 */
	private int javaCount = 0;

	/**
	 * The stack of debug info
	 */
	private DebugFrame[] stack = EMPTY;

	/**
	 * The hook function to call
	 */
	public DebugHook hookfunc;

	/**
	 * Which item hooks should be called on
	 */
	public boolean hookcall, hookline, hookrtrn, inhook;

	/**
	 * Number of instructions to execute
	 */
	public int hookcount;

	/**
	 * Number of instructions executed
	 */
	public int hookcodes;

	public DebugState(LuaState state) {
		this.state = state;
	}

	public LuaState getLuaState() {
		return state;
	}

	/**
	 * Push a new debug frame onto the stack, marking it as also consuming one or more Java stack frames.
	 *
	 * @return The created info. This should be marked with {@link DebugFrame#FLAG_JAVA} or {@link DebugFrame#FLAG_FRESH}
	 * by the calling function.
	 * @throws LuaError On a stack overflow
	 */
	public DebugFrame pushJavaInfo() throws LuaError {
		int javaTop = this.javaCount + 1;
		if (javaTop >= MAX_JAVA_SIZE) throw new LuaError("stack overflow");

		DebugFrame frame = pushInfo();
		this.javaCount = javaTop;
		return frame;
	}

	/**
	 * Push a new debug info onto the stack
	 *
	 * @return The created info
	 * @throws LuaError On a stack overflow.
	 */
	public DebugFrame pushInfo() throws LuaError {
		int top = this.top + 1;

		DebugFrame[] frames = stack;
		int length = frames.length;
		if (top >= length) {
			if (top >= MAX_SIZE) throw new LuaError("stack overflow");
			int newSize = length == 0 ? DEFAULT_SIZE : Math.min(MAX_SIZE, length + (length / 2));
			DebugFrame[] f = new DebugFrame[newSize];
			System.arraycopy(frames, 0, f, 0, length);
			for (int i = frames.length; i < newSize; ++i) {
				f[i] = new DebugFrame(i > 0 ? f[i - 1] : null);
			}
			frames = stack = f;
		}

		this.top = top;
		return frames[top];
	}

	/**
	 * Pop a debug info off the stack
	 */
	public void popInfo() {
		DebugFrame frame = stack[top--];
		if ((frame.flags & (FLAG_JAVA | FLAG_FRESH)) != 0) javaCount--;
		assert javaCount >= 0;
		frame.clear();
	}

	/**
	 * Setup the hook
	 *
	 * @param func  The function hook
	 * @param call  Hook on calls
	 * @param line  Hook on lines
	 * @param rtrn  Hook on returns
	 * @param count Number of bytecode operators to use
	 */
	public void setHook(DebugHook func, boolean call, boolean line, boolean rtrn, int count) {
		this.hookcount = count;
		this.hookcall = call;
		this.hookline = line;
		this.hookrtrn = rtrn;
		this.hookfunc = func;
	}

	/**
	 * Get the top debug info
	 *
	 * @return The top debug info or {@code null}
	 */
	public DebugFrame getStack() {
		return top >= 0 ? stack[top] : null;
	}

	/**
	 * Get the top debug info
	 *
	 * @return The top debug info or {@code null}
	 */
	public DebugFrame getStackUnsafe() {
		return stack[top];
	}

	/**
	 * Get the debug info at a particular level
	 *
	 * @param level The level to get at
	 * @return The debug info or {@code null}
	 */
	public DebugFrame getFrame(int level) {
		return level >= 0 && level <= top ? stack[top - level] : null;
	}

	/**
	 * Find the debug info for a function
	 *
	 * @param func The function to find
	 * @return The debug info for this function
	 */
	public DebugFrame findDebugInfo(LuaFunction func) {
		for (int i = top - 1; --i >= 0; ) {
			if (stack[i].func == func) {
				return stack[i];
			}
		}
		return new DebugFrame(func);
	}

	public void hookCall(DebugFrame frame) throws LuaError, UnwindThrowable {
		inhook = true;
		frame.flags |= FLAG_HOOKED;
		try {
			hookfunc.onCall(state, this, frame);
		} catch (LuaError | RuntimeException e) {
			inhook = false;
			throw e;
		}

		inhook = false;
		frame.flags &= ~FLAG_HOOKED;
	}

	public void hookTailCall(DebugFrame frame) throws LuaError, UnwindThrowable {
		inhook = true;
		frame.flags |= FLAG_HOOKED;
		try {
			hookfunc.onTailCall(state, this, frame);
		} catch (LuaError | RuntimeException e) {
			inhook = false;
			throw e;
		}

		inhook = false;
		frame.flags &= ~FLAG_HOOKED;
	}

	void hookReturn(DebugFrame frame) throws LuaError, UnwindThrowable {
		inhook = true;
		frame.flags |= FLAG_HOOKED;
		try {
			hookfunc.onReturn(state, this, frame);
		} catch (LuaError | RuntimeException e) {
			inhook = false;
			throw e;
		}

		inhook = false;
		frame.flags &= ~FLAG_HOOKED;
	}

	void hookInstruction(DebugFrame frame) throws LuaError, UnwindThrowable {
		inhook = true;
		frame.flags |= FLAG_HOOKED;
		try {
			hookfunc.onCount(state, this, frame);
		} catch (LuaError | RuntimeException e) {
			inhook = false;
			throw e;
		} catch (UnwindThrowable e) {
			frame.flags |= FLAG_HOOKYIELD;
			throw e;
		}
		inhook = false;
		frame.flags &= ~FLAG_HOOKED;
	}

	void hookLine(DebugFrame frame, int newLine) throws LuaError, UnwindThrowable {
		inhook = true;
		frame.flags |= FLAG_HOOKED;
		try {
			hookfunc.onLine(state, this, frame, newLine);
		} catch (LuaError | RuntimeException e) {
			inhook = false;
			throw e;
		} catch (UnwindThrowable e) {
			frame.flags |= FLAG_HOOKED | FLAG_HOOKYIELD | FLAG_HOOKYIELD_LINE;
			throw e;
		}
		inhook = false;
		frame.flags &= ~FLAG_HOOKED;
	}
}

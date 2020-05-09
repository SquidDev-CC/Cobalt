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
import org.squiddev.cobalt.LuaValue;
import org.squiddev.cobalt.NonResumableException;
import org.squiddev.cobalt.OperationHelper;
import org.squiddev.cobalt.Print;
import org.squiddev.cobalt.Resumable;
import org.squiddev.cobalt.UnwindThrowable;
import org.squiddev.cobalt.ValueFactory;
import org.squiddev.cobalt.Varargs;
import org.squiddev.cobalt.function.LuaClosure;
import org.squiddev.cobalt.function.LuaFunction;
import org.squiddev.cobalt.function.LuaInterpretedFunction;
import org.squiddev.cobalt.function.LuaInterpreter;
import org.squiddev.cobalt.function.Upvalue;

import java.util.stream.IntStream;

/**
 * Each thread will get a DebugState attached to it by the debug library
 * which will track function calls, hook functions, etc.
 */
public final class DebugFrame {

	/**
	 * Whether this function is currently within a debug hook.
	 *
	 * @see #flags
	 * @see DebugState#hookCall(DebugFrame) and other {@code hook*} functions.
	 * @see org.squiddev.cobalt.function.ResumableVarArgFunction
	 * @see org.squiddev.cobalt.function.LuaInterpretedFunction
	 */
	public static final int FLAG_HOOKED = 1 << 2;

	/**
	 * This is a fresh instance of a {@link LuaInterpreter}. The interpreter
	 * loop should not continue beyond functions marked with this flag.
	 */
	public static final int FLAG_FRESH = 1 << 3;

	/**
	 * If this function is a yielded, protected call. Namely, if one can
	 * {@link Resumable#resumeError(LuaState, Object, LuaError)} into it.
	 *
	 * @see #flags
	 * @see org.squiddev.cobalt.lib.CoroutineLib {@code coroutine.resume} sets this, in order to receive errors from the
	 * child coroutine.
	 * @see org.squiddev.cobalt.lib.BaseLib {@code pcall}/{@code xpcall} set this for obvious reasons.
	 */
	public static final int FLAG_YPCALL = 1 << 4;

	/**
	 * Whether this function is currently within a line/instruction debug hook.
	 *
	 * @see #flags
	 * @see DebugState#hookInstruction(DebugFrame)
	 * @see org.squiddev.cobalt.function.LuaInterpretedFunction#resume(LuaState, Object, Varargs)
	 */
	public static final int FLAG_HOOKYIELD = 1 << 6;

	/**
	 * If the result should be inverted  (due to using lt rather than le).
	 *
	 * @see #flags
	 * @see OperationHelper#le(LuaState, LuaValue, LuaValue)
	 * @see LuaInterpreter#resume(LuaState, DebugFrame, LuaInterpretedFunction, Varargs)
	 */
	public static final int FLAG_LEQ = 1 << 7;

	/**
	 * If this function errored. This is a really gross hack to ensure we don't resume into errored
	 * functions again.
	 *
	 * @see #flags
	 * @see org.squiddev.cobalt.lib.BaseLib and the xpcall implementation.
	 */
	public static final int FLAG_ERROR = 1 << 10;

	/**
	 * Whether this function is currently within line debug hook.
	 *
	 * @see #flags
	 * @see DebugState#hookInstruction(DebugFrame)
	 */
	public static final int FLAG_HOOKYIELD_LINE = 1 << 11;

	/**
	 * Whether this function contributes to the Java call stack
	 *
	 * @see #flags
	 * @see DebugState#pushJavaInfo()
	 */
	public static final int FLAG_JAVA = 1 << 12;

	/**
	 * Whether this function was tail called
	 *
	 * @see #flags
	 * @see LuaInterpreter
	 */
	public static final int FLAG_TAIL = 1 << 13;

	/**
	 * The debug info's function
	 */
	public LuaFunction func;

	/**
	 * The debug info's closure
	 */
	public LuaClosure closure;

	/**
	 * The stack for this info
	 */
	public LuaValue[] stack;

	/**
	 * The {@link Upvalue} equivalent of {@link #stack}
	 */
	public Upvalue[] stackUpvalues;

	public Object state;

	public final DebugFrame previous;

	private static final LuaString TEMPORARY = ValueFactory.valueOf("(*temporary)");

	public Varargs varargs, extras;
	public int pc = -1, oldPc = -1, top = -1;
	public int flags;

	public DebugFrame(DebugFrame previous) {
		this.previous = previous;
		func = null;
	}

	public DebugFrame(LuaFunction func) {
		previous = null;
		this.func = func;
		this.closure = func instanceof LuaClosure ? (LuaClosure) func : null;
	}

	/**
	 * Set this debug frame to hold some Lua closure
	 *
	 * @param closure       the function called
	 * @param varargs       The arguments to this function
	 * @param stack         The current lua stack
	 * @param stackUpvalues The upvalues on this stack
	 */
	public void setFunction(LuaClosure closure, Varargs varargs, LuaValue[] stack, Upvalue[] stackUpvalues) {
		this.func = closure;
		this.closure = closure;
		this.varargs = varargs;
		this.stack = stack;
		this.stackUpvalues = stackUpvalues;
	}

	/**
	 * Set this debug frame to hold some Java function.
	 *
	 * @param func  the function called
	 * @param state The state which will be used when resuming the function.
	 * @param <S>   The type of the state used when resuming the function.
	 * @param <T>   The type of the function
	 */
	public <S, T extends LuaFunction & Resumable<S>> void setFunction(T func, S state) {
		this.func = func;
		this.closure = func instanceof LuaClosure ? (LuaClosure) func : null;
		this.state = state;
	}

	public void cleanup() {
		LuaInterpreter.closeAll(stackUpvalues);
	}

	void clear() {
		func = null;
		closure = null;
		stack = null;
		stackUpvalues = null;
		state = null;
		varargs = extras = null;
		flags = 0;
		oldPc = pc = top = -1;
	}

	/**
	 * Get the current line
	 *
	 * @return The current line the function is on
	 */
	public int currentLine() {
		if (closure == null) return -1;
		int[] li = closure.getPrototype().lineinfo;
		return li == null || pc < 0 || pc >= li.length ? -1 : li[pc];
	}

	/**
	 * Get the kind for this function
	 *
	 * @return This function's kind
	 */
	public LuaString[] getFuncKind() {
		DebugFrame previous = this.previous;
		if ((flags & FLAG_TAIL) != 0) return null;

		if (previous == null || previous.closure == null || previous.pc < 0) return null;

		int stackpos = (previous.closure.getPrototype().code[previous.pc] >> 6) & 0xff;
		return DebugHelpers.getFuncName(previous, stackpos);
	}

	public String sourceLine() {
		if (closure == null) return func == null ? "nil" : func.debugName();
		return closure.getPrototype().sourceShort() + ":" + currentLine();
	}

	public LuaString getLocalName(int index) {
		if (closure == null) return null;
		LuaString name = closure.getPrototype().getlocalname(index, pc);
		if (name != null) return name;

		// FIXME: Use top rather than maxstacksize. Sadly it isn't currently updated.
		return index > 0 && index <= closure.getPrototype().maxstacksize ? TEMPORARY : null;
	}

	@SuppressWarnings("unchecked")
	public Varargs resume(LuaState state, Varargs args) throws LuaError, UnwindThrowable {
		if (func instanceof Resumable<?>) {
			return ((Resumable<Object>) func).resume(state, this.state, args);
		} else {
			throw new NonResumableException(func == null ? "null" : func.debugName());
		}
	}

	@SuppressWarnings("unchecked")
	public Varargs resumeError(LuaState state, LuaError error) throws LuaError, UnwindThrowable {
		if (func instanceof Resumable<?>) {
			return ((Resumable<Object>) func).resumeError(state, this.state, error);
		} else {
			throw new NonResumableException(func == null ? "null" : func.debugName());
		}
	}

	public Object[] showBytecode() {
		return IntStream
				.range(0, this.closure.getPrototype().code.length)
				.mapToObj(i -> (this.pc == i ? "->" : "  ") + Print.showOpCode(this.closure.getPrototype(), i))
				.toArray();
	}
}

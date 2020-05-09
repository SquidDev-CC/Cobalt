package org.squiddev.cobalt.function;

import org.squiddev.cobalt.Varargs;
import org.squiddev.cobalt.debug.DebugFrame;

/**
 * Continuation object for partial evaluation.
 * Used to pack results of compiled instruction executions.
 */
public final class EvalCont {
	/**
	 * Used as the new value for the program counter.
	 */
	public int programCounter;
	/**
	 * Used to pass vararg return results.
	 */
	public Varargs varargs;
	/**
	 * Used together with {@link #function} to switch the current frame in calls and returns.
	 */
	public DebugFrame debugFrame;
	/**
	 * @see #debugFrame
	 */
	public LuaInterpretedFunction function;
}

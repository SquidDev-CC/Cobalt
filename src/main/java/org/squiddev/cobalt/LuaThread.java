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

import org.squiddev.cobalt.debug.DebugFrame;
import org.squiddev.cobalt.debug.DebugHandler;
import org.squiddev.cobalt.debug.DebugState;
import org.squiddev.cobalt.function.LuaFunction;
import org.squiddev.cobalt.function.LuaInterpretedFunction;
import org.squiddev.cobalt.function.Upvalue;
import org.squiddev.cobalt.lib.CoroutineLib;
import org.squiddev.cobalt.lib.jse.JsePlatform;

import java.lang.ref.WeakReference;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;

import static org.squiddev.cobalt.debug.DebugFrame.FLAG_ERROR;
import static org.squiddev.cobalt.debug.DebugFrame.FLAG_YPCALL;

/**
 * Subclass of {@link LuaValue} that implements a lua coroutine thread.
 *
 * A LuaThread is typically created in response to a scripted call to
 * {@code coroutine.create()}
 *
 * The utility class {@link JsePlatform}
 * sees to it that this initialization is done properly.
 * For this reason it is highly recommended to use one of these classes
 * when initializing globals.
 *
 * The behavior of coroutine threads matches closely the behavior
 * of C coroutine library.  However, because of the use of Java threads
 * to manage call state, it is possible to yield from anywhere in luaj.
 *
 * @see LuaValue
 * @see JsePlatform
 * @see CoroutineLib
 */
public class LuaThread extends LuaValue {
	/**
	 * Interval in nanoseconds at which to check for lua threads that are no longer referenced.
	 * This can be changed by Java startup code if desired.
	 */
	public static long orphanCheckInterval = TimeUnit.SECONDS.toNanos(30);

	/**
	 * A coroutine which has been run at all
	 */
	private static final int STATUS_INITIAL = 0;

	/**
	 * A coroutine which has yielded
	 */
	private static final int STATUS_SUSPENDED = 1;

	/**
	 * A coroutine which is currently running
	 */
	private static final int STATUS_RUNNING = 2;

	/**
	 * A coroutine which has resumed another coroutine
	 */
	private static final int STATUS_NORMAL = 3;

	/**
	 * A coroutine which has finished executing
	 */
	private static final int STATUS_DEAD = 4;

	private static final String[] STATUS_NAMES = {
		"suspended",
		"suspended",
		"running",
		"normal",
		"dead",
	};

	final State state;

	/**
	 * The state that this thread lives in
	 */
	private final LuaState luaState;

	/**
	 * The function called when handling errors
	 */
	private LuaValue errFunc;

	/**
	 * Used by DebugLib to store debugging state.
	 */
	private final DebugState debugState;

	/**
	 * The main function for this thread
	 */
	final LuaFunction function;

	/**
	 * Constructor for main thread only
	 *
	 * @param state The current lua state
	 */
	public LuaThread(LuaState state) {
		super(Constants.TTHREAD);
		this.state = new State(this, STATUS_RUNNING);
		this.luaState = state;
		this.debugState = new DebugState(state);
		this.function = null;
	}

	/**
	 * Create a LuaThread around a function and environment
	 *
	 * @param state The current lua state
	 * @param func  The function to execute
	 */
	public LuaThread(LuaState state, LuaFunction func) {
		super(Constants.TTHREAD);
		if (func == null) throw new IllegalArgumentException("function cannot be null");
		this.state = new State(this, STATUS_INITIAL);
		this.luaState = state;
		this.debugState = new DebugState(state);
		this.function = func;
	}

	@Override
	public LuaThread optThread(LuaThread defval) {
		return this;
	}

	@Override
	public LuaThread checkThread() {
		return this;
	}

	@Override
	public LuaTable getMetatable(LuaState state) {
		return state.threadMetatable;
	}

	@Override @Deprecated
	public LuaTable getfenv() {
		return luaState.globalTable;
	}

	@Override @Deprecated
	public void setfenv(LuaTable env) { }

	public String getStatus() {
		return STATUS_NAMES[state.status];
	}

	/**
	 * Test if this is the main thread
	 *
	 * @return true if this is the main thread
	 */
	public boolean isMainThread() {
		return luaState.getMainThread() == this;
	}

	public boolean isAlive() {
		return state.status != STATUS_DEAD;
	}

	/**
	 * Get the function called as a specific location on the stack.
	 *
	 * @param state The current lua state
	 * @param level 1 for the function calling this one, 2 for the next one.
	 * @return LuaFunction on the call stack, or null if outside of range of active stack
	 */
	public static LuaFunction getCallstackFunction(LuaState state, int level) {
		DebugFrame info = DebugHandler.getDebugState(state).getFrame(level);
		return info == null ? null : info.func;
	}

	/**
	 * Get the debug state for this thread
	 *
	 * @return This thread's debug state
	 * @see DebugHandler#getDebugState(LuaState)
	 */
	public DebugState getDebugState() {
		return debugState;
	}

	/**
	 * Get this thread's error handling function
	 *
	 * @return The error handling function, or {@code null} if not defined.
	 * @see #setErrorFunc(LuaValue)
	 * @see LuaError
	 */
	public LuaValue getErrorFunc() {
		return errFunc;
	}

	/**
	 * Replace the error function of this thread.
	 *
	 * @param errFunc the new error function to use.
	 * @return the previous error function.
	 * @see #getErrorFunc()
	 */
	public LuaValue setErrorFunc(LuaValue errFunc) {
		LuaValue prev = this.errFunc;
		this.errFunc = errFunc;
		return prev;
	}

	/**
	 * Yield the current thread with arguments
	 *
	 * @param state The current lua state
	 * @param args  The arguments to send as return values to {@link #resume(LuaState, LuaThread, Varargs)}
	 * @return The arguments to resume with, if yielding is currently blocked.
	 * @throws LuaError         If attempting to yield the main thread.
	 * @throws UnwindThrowable  If we can yield this stack with an exception
	 * @throws InterruptedError If we had a blocking yield which threw.
	 */
	public static Varargs yield(LuaState state, Varargs args) throws LuaError, UnwindThrowable {
		Objects.requireNonNull(args, "args cannot be null");
		checkYield(state);

		if (state.currentThread.state.javaCount == 0) {
			throw UnwindThrowable.yield(args);
		} else {
			try {
				return yieldBlockingImpl(state, args);
			} catch (InterruptedException e) {
				throw new InterruptedError(e);
			}
		}
	}

	/**
	 * Yield the current thread and wait for a response
	 *
	 * @param state The current lua state
	 * @param args  The arguments to send as return values to {@link #resume(LuaState, LuaThread, Varargs)}
	 * @return The values this coroutine was resumed with
	 * @throws LuaError             If this thread cannot be yielded.
	 * @throws InterruptedException If this thread was terminated when yielding.
	 */
	public static Varargs yieldBlocking(LuaState state, Varargs args) throws LuaError, InterruptedException {
		Objects.requireNonNull(args, "args cannot be null");
		checkYield(state);
		return yieldBlockingImpl(state, args);
	}

	private static void checkYield(LuaState state) throws LuaError {
		LuaThread thread = state.currentThread;
		if (thread.state.status != STATUS_RUNNING) {
			throw new LuaError("cannot yield a " + STATUS_NAMES[thread.state.status] + " thread");
		}
		if (thread.isMainThread()) throw new LuaError("cannot yield main thread");
	}

	private static Varargs yieldBlockingImpl(LuaState state, Varargs args) throws InterruptedException, LuaError {
		State current = state.currentThread.state;

		// Mark the parent coroutine as "active" and transfer.
		state.currentThread = current.previousThread;
		current.status = STATUS_SUSPENDED;
		current.previousThread = null;
		return transferControl(state, current, args);
	}

	/**
	 * Resume a thread with arguments.
	 *
	 * @param state  The current lua state
	 * @param thread The thread to resume
	 * @param args   The arguments to resume with
	 * @return The arguments the parent coroutine yielded with, if yielding is currently blocked.
	 * @throws LuaError        If this coroutine cannot resume another.
	 * @throws UnwindThrowable If we can yield this stack with an exception
	 */
	public static Varargs resume(LuaState state, LuaThread thread, Varargs args) throws LuaError, UnwindThrowable {
		LuaThread current = state.currentThread;
		State currentState = current.state;
		if (currentState.status != STATUS_RUNNING) {
			throw new LuaError("cannot resume from a " + STATUS_NAMES[currentState.status] + " thread");
		}

		State threadState = thread.state;
		if (threadState.status > STATUS_SUSPENDED) {
			throw new LuaError("cannot resume " + STATUS_NAMES[threadState.status] + " coroutine");
		}

		if (currentState.javaCount == 0) {
			throw UnwindThrowable.resume(thread, args);
		} else {
			try {
				// Mark the child coroutine as "active", and transfer.
				state.currentThread = thread;
				currentState.status = STATUS_NORMAL;
				threadState.previousThread = current;

				// Null these out to ensure they do not hang around on the stack
				//noinspection UnusedAssignment
				thread = current = null;

				return transferControl(state, currentState, args);
			} catch (InterruptedException e) {
				throw new InterruptedError(e);
			}
		}
	}

	/**
	 * Suspend the current thread. Note, this may return or throw an exception, so you must handle both cases.
	 *
	 * @param state The current lua state
	 * @throws LuaError        If this coroutine cannot be suspended.
	 * @throws UnwindThrowable If we can yield this stack with an exception
	 */
	public static void suspend(LuaState state) throws LuaError, UnwindThrowable {
		State current = state.currentThread.state;
		if (current.status != STATUS_RUNNING) {
			throw new LuaError("cannot suspend a " + STATUS_NAMES[current.status] + " thread");
		}

		if (current.javaCount == 0) {
			throw UnwindThrowable.suspend();
		} else {
			suspendBlocking(state);
		}
	}

	/**
	 * Suspend the current thread and wait for it to be resumed.
	 *
	 * @param state The current lua state
	 * @throws LuaError If this coroutine cannot be suspended.
	 */
	public static void suspendBlocking(LuaState state) throws LuaError {
		State current = state.currentThread.state;
		if (current.status != STATUS_RUNNING) {
			throw new LuaError("cannot suspend a " + STATUS_NAMES[current.status] + " thread");
		}

		try {
			// Mark the child coroutine as "active", and transfer.
			current.status = STATUS_SUSPENDED;
			state.threader.running = false;
			transferControl(state, current, null);
		} catch (InterruptedException e) {
			throw new InterruptedError(e);
		}
	}

	private static Varargs transferControl(LuaState state, State thread, Varargs args) throws InterruptedException, LuaError {
		YieldThreader threader = state.threader;

		// Construct a lock to wait on.
		if (thread.resumeLock == null) thread.resumeLock = threader.lock.newCondition();

		threader.lock.lockInterruptibly();
		try {
			// Give the runner a signal, and start it off.
			thread.needsThreadedResume = true;
			threader.set(args);
			threader.loop.signal();

			// Wait for us to be resumed.
			while (thread.resumeLock.awaitNanos(orphanCheckInterval) <= 0) {
				if (state.abandoned) throw new InterruptedException("Abandoned state");
				if (thread.owner.get() == null) throw new OrphanedThreadException();
			}

			return threader.unpack();
		} finally {
			threader.lock.unlock();
			thread.needsThreadedResume = false;
		}
	}

	public static Varargs runMain(LuaState state, LuaFunction function) throws LuaError, InterruptedException {
		return run(state, state.getMainThread(), function, Constants.NONE);
	}

	public static Varargs runMain(LuaState state, LuaFunction function, Varargs args) throws LuaError, InterruptedException {
		return run(state, state.getMainThread(), function, args);
	}

	/**
	 * Start or resume this thread
	 *
	 * @param thread The thread to resume
	 * @param args   The arguments to send as return values to {@link #yield(LuaState, Varargs)}
	 * @return {@link Varargs} provided as arguments to {@link #yield(LuaState, Varargs)}
	 * @throws LuaError If the current function threw an exception.
	 */
	public static Varargs run(LuaThread thread, Varargs args) throws LuaError, InterruptedException {
		return run(thread.luaState, thread, null, args);
	}

	private static Varargs run(final LuaState state, LuaThread thread, LuaFunction function, Varargs args) throws LuaError, InterruptedException {
		YieldThreader threader = state.threader;
		threader.lock.lock();
		try {
			// First, set up the initial state
			state.currentThread = thread;
			threader.set(args);
			threader.running = true;

			Runnable task = new Runnable() {
				LuaFunction func = function;

				@Override
				public void run() {
					try {
						threader.lock.lockInterruptibly();
					} catch (InterruptedException ignored) {
						Thread.currentThread().interrupt();
						System.out.println("Interrupted");
						return;
					}

					try {
						// Clear the function after the first run
						LuaFunction function = func;
						func = null;
						if (function instanceof LuaInterpretedFunction) {
							((LuaInterpretedFunction)function).setUpvalue(((LuaInterpretedFunction) function).p.isLua52 ? 0 : -1, new Upvalue(state.globalTable));
						}

						Varargs res = loop(state, state.currentThread, function, threader.unpack());

						// Loop returned a value, which means the top-level coroutine yielded or terminated.
						threader.set(res);
						threader.running = false;
						threader.loop.signal();
					} catch (TransferredControlThrowable ignored) {
						// Just die here: someone else is running now, but the coroutines are still being executed.
					} catch (Throwable e) {
						// Loop threw a LuaError (the top-level coroutine errored) or threw an unknown exception
						// (terminate everything).
						threader.set(e);
						threader.running = false;
						threader.loop.signal();
					} finally {
						threader.lock.unlock();
					}
				}
			};

			while (threader.running && state.currentThread != null) {
				threader.execute(task);
				threader.loop.await();
			}

			return threader.unpack();
		} catch (InterruptedError e) {
			throw e.getCause();
		} finally {
			threader.lock.unlock();
		}
	}

	static Varargs loop(final LuaState state, LuaThread thread, LuaFunction function, Varargs args) throws LuaError, TransferredControlThrowable {
		YieldThreader threader = state.threader;

		LuaError le = null;
		do {
			final State threadState = thread.state;
			final DebugState ds = thread.debugState;
			state.currentThread = thread;
			if (threadState.status == STATUS_INITIAL && function == null) function = thread.function;

			// Null this out to ensure they do not hang around on the stack
			//noinspection UnusedAssignment
			thread = null;

			try {
				if (function != null) {
					threadState.status = STATUS_RUNNING;

					// We only want to execute the function the first time, so null it out
					LuaFunction toExecute = function;
					function = null;

					try {
						args = toExecute.invoke(state, args);
					} catch (Exception | VirtualMachineError e) {
						args = null;
						le = LuaError.wrap(e);
					}
				} else if (threadState.needsThreadedResume) {
					// We only ever resume coroutines which have yielded, never those which have
					// resumed other coroutines. Consequently, we know we will never have an error here.
					if (le != null) {
						throw new IllegalStateException("Cannot resume a threaded coroutine with an error.");
					}

					// Store the arguments in threader, and resume this thread.
					threader.set(args);
					threadState.status = STATUS_RUNNING;
					threadState.resumeLock.signal();

					throw TransferredControlThrowable.INSTANCE;
				} else {
					threadState.status = STATUS_RUNNING;

					outer:
					while (true) {
						try {
							if (le != null) {
								// If we've an error, walk up the stack until we find an error handler and resume.
								DebugFrame frame = findErrorHandler(ds);
								if (frame == null) break;

								// We need to set the error to null first so we don't count propagate errors across
								// yields
								LuaError err = le;
								le = null;
								args = frame.resumeError(state, err);
							}

							while (true) {
								DebugFrame frame = ds.getStack();
								if (frame == null) break outer;

								// If this frame errored, then we've just exited an error handler: look up the frame
								// for what to return into.
								if ((frame.flags & FLAG_ERROR) != 0) {
									frame = findErrorHandler(ds);
									if (frame == null) break;
								}

								args = frame.resume(state, args);
							}
						} catch (Exception | VirtualMachineError e) {
							args = null;
							le = LuaError.wrap(e);
						}
					}
				}

				// We've died, yield into the parent coroutine
				threadState.status = STATUS_DEAD;
				LuaThread previous = threadState.previousThread;
				threadState.previousThread = null;
				thread = previous;

				if (le != null) {
					// If we've an error, fill in the debug info and clean up the stack.
					le.fillTracebackNoHandler(state);

					DebugFrame di;
					for (int i = 0; (di = ds.getFrame(i)) != null; i++) di.cleanup();
				}
			} catch (UnwindThrowable e) {
				if (e.isSuspend()) {
					threadState.status = STATUS_SUSPENDED;
					return null;
				} else if (e.isYield()) {
					// Yield into the parent coroutine
					threadState.status = STATUS_SUSPENDED;
					LuaThread previous = threadState.previousThread;
					threadState.previousThread = null;
					thread = previous;
					args = e.getArgs();
				} else {
					// Resume into the next coroutine
					threadState.status = STATUS_NORMAL;
					LuaThread next = e.getThread();
					next.state.previousThread = state.currentThread;
					thread = next;
					args = e.getArgs();
				}
			}
		} while (thread != null);

		if (le != null) throw le;
		return args;
	}

	private static DebugFrame findErrorHandler(DebugState ds) {
		for (int i = 0; ; i++) {
			DebugFrame frame = ds.getFrame(i);
			if (frame == null || (frame.flags & FLAG_YPCALL) != 0) return frame;
		}
	}

	/**
	 * Holds the active state of a {@link LuaThread}.
	 *
	 * While {@link LuaThread} can be thought of as the Lua value representation of a coroutine, the {@link State} holds
	 * the underlying behaviour and important values.
	 *
	 * This distinction is important, as it allows us to detect when specific coroutines are no longer referenced, and
	 * so clean up after them. Therefore, any long-lasting memory structures of functions should aim to hold on to
	 * a {@link State} rather than its owning {@link LuaThread}.
	 *
	 * Note, this distinction is only important in the case where a yield is blocking, and so a new thread is spawned.
	 * Non-blocking yields do not need to be collected up, as they do not use any resources beyond the standard Lua
	 * frame, etc...
	 *
	 * @see OrphanedThreadException
	 */
	static class State {
		/**
		 * Weak reference to the owner, allowing us to detect abandoned threads
		 */
		final WeakReference<LuaThread> owner;

		/**
		 * The current status of this thread
		 */
		int status;

		/**
		 * The thread which resumed this one, and so should be resumed back into.
		 */
		LuaThread previousThread;

		/**
		 * The depth of the Java blocks. Yielding/resuming is only allowed when this is 0.
		 */
		int javaCount = 0;

		/**
		 * The lock to wait on while this coroutine is suspended as a thread
		 */
		Condition resumeLock;

		/**
		 * Whether we've yielded in a threaded manner.
		 */
		boolean needsThreadedResume;

		/**
		 * Constructor for main thread only
		 */
		State(LuaThread owner, int status) {
			this.owner = new WeakReference<>(owner);
			this.status = status;
		}
	}

	/**
	 * Used inside {@link #loop(LuaState, LuaThread, LuaFunction, Varargs)} when
	 * this particular thread has transferred control elsewhere.
	 */
	private static class TransferredControlThrowable extends Throwable {
		private static final long serialVersionUID = 6854182520592525282L;
		static final TransferredControlThrowable INSTANCE = new TransferredControlThrowable();

		private TransferredControlThrowable() {
			super(null, null, true, false);
		}
	}
}

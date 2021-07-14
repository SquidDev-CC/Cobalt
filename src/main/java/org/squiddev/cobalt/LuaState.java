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

import org.squiddev.cobalt.compiler.LoadState;
import org.squiddev.cobalt.compiler.LuaC;
import org.squiddev.cobalt.debug.DebugHandler;
import org.squiddev.cobalt.lib.platform.FileResourceManipulator;
import org.squiddev.cobalt.lib.platform.ResourceManipulator;

import java.io.InputStream;
import java.io.PrintStream;
import java.util.Random;
import java.util.TimeZone;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Global lua state
 */
public final class LuaState {
	/**
	 * The active input stream
	 */
	public InputStream stdin;

	/**
	 * The active output stream
	 */
	public PrintStream stdout;

	/**
	 * The metatable for all strings
	 */
	public LuaTable stringMetatable;

	/**
	 * The metatable for all booleans
	 */
	public LuaTable booleanMetatable;

	/**
	 * The metatable for all numbers
	 */
	public LuaTable numberMetatable;

	/**
	 * The metatable for all nil values
	 */
	public LuaTable nilMetatable;

	/**
	 * The metatable for all functions
	 */
	public LuaTable functionMetatable;

	/**
	 * The metatable for all threads
	 */
	public LuaTable threadMetatable;

	/**
	 * Lookup of loaded packages
	 */
	public final LuaTable loadedPackages = new LuaTable();

	/**
	 * The active resource manipulator
	 */
	public final ResourceManipulator resourceManipulator;

	/**
	 * The compiler for this threstate
	 */
	public final LoadState.LuaCompiler compiler;

	/**
	 * The handler for the debugger. Override this for custom debug actions.
	 */
	public final DebugHandler debug;

	/**
	 * The timezone for this state, as used by {@code os}.
	 */
	public final TimeZone timezone;

	/**
	 * The random instance for this state.
	 */
	public Random random;

	/**
	 * The currently executing thread
	 */
	LuaThread currentThread;

	/**
	 * The currently executing main thread
	 */
	private LuaThread mainThread;

	/**
	 * The currently active {@link YieldThreader}.
	 */
	final YieldThreader threader;

	/**
	 * If this state has been abandoned, and threads should be cleaned up.
	 *
	 * @see LuaThread#orphanCheckInterval
	 */
	boolean abandoned;

	/**
	 * The global table for the state
	 */
	public LuaTable globalTable;

	public LuaState() {
		this(new LuaState.Builder());
	}

	private LuaState(Builder builder) {
		this.stdin = builder.stdin;
		this.stdout = builder.stdout;
		this.stringMetatable = builder.stringMetatable;
		this.booleanMetatable = builder.booleanMetatable;
		this.numberMetatable = builder.numberMetatable;
		this.nilMetatable = builder.nilMetatable;
		this.functionMetatable = builder.functionMetatable;
		this.threadMetatable = builder.threadMetatable;
		this.resourceManipulator = builder.resourceManipulator;
		this.compiler = builder.compiler;
		this.random = builder.random;
		this.debug = builder.debug;
		this.timezone = builder.timezone;
		this.threader = new YieldThreader(builder.coroutineExecutor);
		this.globalTable = builder.globalTable;
	}

	/**
	 * Abandon this state, instructing any pending thread to terminate.
	 */
	public void abandon() {
		abandoned = true;
	}

	/**
	 * Get the main thread
	 *
	 * @return The main thread
	 * @see #getCurrentThread()
	 * @see #setupThread(LuaTable)
	 */
	public LuaThread getMainThread() {
		return mainThread;
	}

	/**
	 * The active thread
	 *
	 * @return The active thread
	 * @see #getMainThread()
	 * @see #setupThread(LuaTable)
	 */
	public LuaThread getCurrentThread() {
		return currentThread;
	}

	/**
	 * Setup the main thread
	 *
	 * @param globals The global table to use
	 * @see #getMainThread()
	 * @see #getCurrentThread()
	 */
	public void setupThread(LuaTable globals) {
		if (mainThread != null && mainThread.isAlive()) {
			throw new IllegalStateException("State already has main thread");
		}

		LuaThread thread = new LuaThread(this);
		mainThread = thread;
		currentThread = thread;
		globalTable = globals;
	}

	public static LuaState.Builder builder() {
		return new LuaState.Builder();
	}

	/**
	 * A mutable builder for {@link LuaState}s.
	 */
	public static class Builder {
		private static final AtomicInteger coroutineCount = new AtomicInteger();
		private static final Executor defaultCoroutineExecutor = Executors.newCachedThreadPool(command -> {
			Thread thread = new Thread(command, "Coroutine-" + coroutineCount.getAndIncrement());
			thread.setDaemon(true);
			return thread;
		});

		private InputStream stdin = System.in;
		private PrintStream stdout = System.out;
		private LuaTable stringMetatable;
		private LuaTable booleanMetatable;
		private LuaTable numberMetatable;
		private LuaTable nilMetatable;
		private LuaTable functionMetatable;
		private LuaTable threadMetatable;
		private ResourceManipulator resourceManipulator = new FileResourceManipulator();
		private LoadState.LuaCompiler compiler = LuaC.INSTANCE;
		private Random random = new Random();
		private DebugHandler debug = DebugHandler.INSTANCE;
		private TimeZone timezone = TimeZone.getDefault();
		private Executor coroutineExecutor = defaultCoroutineExecutor;
		private LuaTable globalTable = new LuaTable();

		/**
		 * Build a Lua state from this builder
		 *
		 * @return The constructed Lua state.
		 */
		public LuaState build() {
			return new LuaState(this);
		}

		/**
		 * Set the initial standard input for this Lua state. This defaults to {@link System#in}.
		 *
		 * @param stdin The new standard input
		 * @return This builder
		 * @see LuaState#stdin
		 */
		public Builder stdin(InputStream stdin) {
			if (stdin == null) throw new NullPointerException("stdin cannot be null");
			this.stdin = stdin;
			return this;
		}

		/**
		 * Set the initial standard output for this Lua state. This defaults to {@link System#out}.
		 *
		 * @param stdout The new standard output
		 * @return This builder
		 * @see LuaState#stdout
		 */
		public Builder stdout(PrintStream stdout) {
			if (stdout == null) throw new NullPointerException("stdout cannot be null");
			this.stdout = stdout;
			return this;
		}

		/**
		 * Set the initial metatable for string values within this Lua State. This defaults to {@code null}.
		 *
		 * @param metatable The initial metatable
		 * @return This builder
		 */
		public Builder stringMetatable(LuaTable metatable) {
			this.stringMetatable = metatable;
			return this;
		}

		/**
		 * Set the initial metatable for boolean values within this Lua State. This defaults to {@code null}.
		 *
		 * @param metatable The initial metatable
		 * @return This builder
		 */
		public Builder booleanMetatable(LuaTable metatable) {
			this.booleanMetatable = metatable;
			return this;
		}

		/**
		 * Set the initial metatable for numeric values within this Lua State. This defaults to {@code null}.
		 *
		 * @param metatable The initial metatable
		 * @return This builder
		 */
		public Builder numberMetatable(LuaTable metatable) {
			this.numberMetatable = metatable;
			return this;
		}

		/**
		 * Set the initial metatable for nil values within this Lua State. This defaults to {@code null}.
		 *
		 * @param metatable The initial metatable
		 * @return This builder
		 */
		public Builder nilMetatable(LuaTable metatable) {
			this.nilMetatable = metatable;
			return this;
		}

		/**
		 * Set the initial metatable for functions within this Lua State. This defaults to {@code null}.
		 *
		 * @param metatable The initial metatable
		 * @return This builder
		 */
		public Builder functionMetatable(LuaTable metatable) {
			this.functionMetatable = metatable;
			return this;
		}

		/**
		 * Set the initial metatable for threads within this Lua State. This defaults to {@code null}.
		 *
		 * @param metatable The initial metatable
		 * @return This builder
		 */
		public Builder threadMetatable(LuaTable metatable) {
			this.threadMetatable = metatable;
			return this;
		}

		/**
		 * Set the resource manipulator that the {@code os} and {@code io} libraries will use. This defaults to a
		 * {@link FileResourceManipulator}, which uses the default file system.
		 *
		 * @param resourceManipulator The new resource manipulator
		 * @return This builder
		 */
		public Builder resourceManipulator(ResourceManipulator resourceManipulator) {
			if (this.resourceManipulator == null) throw new NullPointerException("resourceManipulator cannot be null");
			this.resourceManipulator = resourceManipulator;
			return this;
		}

		/**
		 * Set the compiler for this Lua state. This defaults to using the {@link LuaC} compiler.
		 *
		 * @param compiler The new compiler to use
		 * @return This builder
		 */
		public Builder compiler(LoadState.LuaCompiler compiler) {
			if (compiler == null) throw new NullPointerException("compiler cannot be null");
			this.compiler = compiler;
			return this;
		}

		/**
		 * Set the initial random state for the Lua state. This will be used by {@code math.random}, but may
		 * be changed by {@code math.radomseed}
		 *
		 * @param random The new random state.
		 * @return This builder
		 */
		public Builder random(Random random) {
			if (random == null) throw new NullPointerException("random cannot be null");
			this.random = random;
			return this;
		}

		/**
		 * Set the debug handler for this Lua state.
		 *
		 * @param debug The new debug handler
		 * @return This builder
		 */
		public Builder debug(DebugHandler debug) {
			if (debug == null) throw new NullPointerException("debug cannot be null");
			this.debug = debug;
			return this;
		}

		/**
		 * Set the timezone for this Lua state.
		 *
		 * @param zone The new timezone
		 * @return This builder
		 */
		public Builder timezone(TimeZone zone) {
			if (zone == null) throw new NullPointerException("zone cannot be null");
			this.timezone = zone;
			return this;
		}


		/**
		 * Set the coroutine executor for this state.
		 *
		 * @param coroutineExecutor The new executor
		 * @return This builder
		 */
		public Builder coroutineExecutor(Executor coroutineExecutor) {
			if (coroutineExecutor == null) throw new NullPointerException("coroutineExecutor cannot be null");
			this.coroutineExecutor = coroutineExecutor;
			return this;
		}

		/**
		 * Set the global table for this state.
		 *
		 * @param table The new global table
		 * @return This builder
		 */
		public Builder globalTable(LuaTable table) {
			if (table == null) throw new NullPointerException("table cannot be null");
			this.globalTable = table;
			return this;
		}
	}
}

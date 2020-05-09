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

import org.squiddev.cobalt.function.LocalVariable;
import org.squiddev.cobalt.function.LuaInterpretedFunction;
import org.squiddev.cobalt.function.UnwindableCallable;

import static org.squiddev.cobalt.compiler.LoadState.getShortName;

/**
 * Prototype representing compiled lua code.
 *
 * This is both a straight translation of the corresponding C type,
 * and the main data structure for execution of compiled lua bytecode.
 *
 * See documentatation on {@link LuaInterpretedFunction} for information on how to load
 * and execute a {@link Prototype}.
 *
 * @see LuaInterpretedFunction
 */
public final class Prototype {
	/* constants used by the function */
	public LuaValue[] k;
	public int[] code;
	public UnwindableCallable[] compiledInstrs;
	/* functions defined inside the function */
	public Prototype[] p;
	/* map from opcodes to source lines */
	public int[] lineinfo;
	/* information about local variables */
	public LocalVariable[] locvars;
	/* upvalue names */
	public LuaString[] upvalues;
	public LuaString source;
	public int nups;
	public int linedefined;
	public int lastlinedefined;
	public int numparams;
	public int is_vararg;
	public int maxstacksize;

	public LuaString sourceShort() {
		return getShortName(source);
	}

	public String toString() {
		return source + ":" + linedefined + "-" + lastlinedefined;
	}

	/**
	 * Get the name of a local variable.
	 *
	 * @param number the local variable number to look up
	 * @param pc     the program counter
	 * @return the name, or null if not found
	 */
	public LuaString getlocalname(int number, int pc) {
		int i;
		for (i = 0; i < locvars.length && locvars[i].startpc <= pc; i++) {
			if (pc < locvars[i].endpc) {  /* is variable active? */
				number--;
				if (number == 0) return locvars[i].name;
			}
		}
		return null;  // not found
	}
}

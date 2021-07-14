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
import org.squiddev.cobalt.debug.DebugHelpers;
import org.squiddev.cobalt.debug.DebugHelpers52;

/**
 * Factory class for errors
 */
public class ErrorFactory {
	/**
	 * Throw a {@link LuaError} indicating an invalid argument was supplied to a function
	 *
	 * @param value    The current value
	 * @param expected String naming the type that was expected
	 * @return The created LuaError
	 */
	public static LuaError argError(LuaValue value, String expected) {
		return new LuaError("bad argument (" + expected + " expected, got " + value.typeName() + ")");
	}

	/**
	 * Throw a {@link LuaError} indicating an invalid argument was supplied to a function
	 *
	 * @param iarg index of the argument that was invalid, first index is 1
	 * @param msg  String providing information about the invalid argument
	 * @return The created LuaError
	 */
	public static LuaError argError(int iarg, String msg) {
		return new LuaError("bad argument #" + iarg + " (" + msg + ")");
	}

	/**
	 * Throw a {@link LuaError} indicating an invalid type was supplied to a function
	 *
	 * @param value    The current value
	 * @param expected String naming the type that was expected
	 * @return The created LuaError
	 */
	public static LuaError typeError(LuaValue value, String expected) {
		return new LuaError(expected + " expected, got " + value.typeName());
	}

	/**
	 * Throw a {@link LuaError} indicating an operation is not implemented
	 *
	 * @param value The current value
	 * @param fun   Function that hasn't been implemented
	 * @return The created LuaError
	 */
	public static LuaError unimplemented(LuaValue value, String fun) {
		return new LuaError("'" + fun + "' not implemented for " + value.typeName());
	}

	/**
	 * Throw a {@link LuaError} indicating an illegal operation occurred,
	 * typically involved in managing weak references
	 *
	 * @param op       Operation
	 * @param typename Current type
	 * @return The created LuaError
	 */
	public static LuaError illegal(String op, String typename) {
		return new LuaError("illegal operation '" + op + "' for " + typename);
	}

	/**
	 * Throw a {@link LuaError} based on an arithmetic error such as add, or pow,
	 * typically due to an invalid operand type
	 *
	 * @param value The value to perform arithmetic on
	 * @return The created LuaError
	 */
	public static LuaError arithError(LuaValue value) {
		return new LuaError("attempt to perform arithmetic on " + value.typeName());
	}

	public static LuaError operandError(LuaState state, LuaValue operand, String verb, int stack) {
		String type = operand.typeName();
		LuaString[] kind = null;
		if (stack >= 0) {
			DebugFrame info = DebugHandler.getDebugState(state).getStack();
			if (info != null && info.closure != null) {
				if (stack < info.closure.getPrototype().maxstacksize) {
					kind = info.closure.getPrototype().isLua52 ? DebugHelpers52.getObjectName(info, stack) : DebugHelpers.getObjectName(info, stack);
				}
			}
		}

		if (kind != null) {
			return new LuaError("attempt to " + verb + " " + kind[1] + " '" + kind[0] + "' (a " + type + " value)");
		} else {
			return new LuaError("attempt to " + verb + " a " + type + " value");
		}
	}

	/**
	 * Throw a {@link LuaError} based on a comparison error such as greater-than or less-than,
	 * typically due to an invalid operand type
	 *
	 * @param lhs Left-hand-side of the comparison that resulted in the error.
	 * @param rhs Right-hand-side of the comparison that resulted in the error.
	 * @return The created LuaError
	 */
	public static LuaError compareError(LuaValue lhs, LuaValue rhs) {
		return new LuaError("attempt to compare " + lhs.typeName() + " with " + rhs.typeName());
	}

	/**
	 * Throw {@link LuaError} indicating index was attempted on illegal type
	 *
	 * @param value The value to index
	 * @return The created LuaError
	 */
	public static LuaError indexError(LuaValue value) {
		return new LuaError("attempt to index ? (a " + value.typeName() + " value)");
	}
}

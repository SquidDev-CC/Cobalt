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
import org.squiddev.cobalt.function.LuaFunction;

import static org.squiddev.cobalt.Constants.*;
import static org.squiddev.cobalt.LuaDouble.NAN;
import static org.squiddev.cobalt.LuaDouble.valueOf;
import static org.squiddev.cobalt.LuaInteger.valueOf;
import static org.squiddev.cobalt.debug.DebugFrame.FLAG_LEQ;

/**
 * Handles arithmetic operations
 */
public final class OperationHelper {
	private OperationHelper() {
	}

	//region Binary
	public static LuaValue add(LuaState state, LuaValue left, LuaValue right) throws LuaError, UnwindThrowable {
		return add(state, left, right, -1, -1);
	}

	public static LuaValue add(LuaState state, LuaValue left, LuaValue right, int leftIdx, int rightIdx) throws LuaError, UnwindThrowable {
		if (left instanceof LuaInteger && right instanceof LuaInteger) {
			int x = ((LuaInteger) left).v, y = ((LuaInteger) right).v;
			int r = x + y;
			if (((x ^ r) & (y ^ r)) >= 0) return valueOf(r);
		}

		double dLeft, dRight;
		if (checkNumber(left, dLeft = left.toDouble()) && checkNumber(right, dRight = right.toDouble())) {
			return valueOf(dLeft + dRight);
		} else {
			return arithMetatable(state, ADD, left, right, leftIdx, rightIdx);
		}
	}

	public static LuaValue sub(LuaState state, LuaValue left, LuaValue right) throws LuaError, UnwindThrowable {
		return sub(state, left, right, -1, -1);
	}

	public static LuaValue sub(LuaState state, LuaValue left, LuaValue right, int leftIdx, int rightIdx) throws LuaError, UnwindThrowable {
		if (left instanceof LuaInteger && right instanceof LuaInteger) {
			int x = ((LuaInteger) left).v, y = ((LuaInteger) right).v;
			int r = x - y;
			if (((x ^ r) & (y ^ r)) >= 0) return valueOf(r);
		}

		double dLeft, dRight;
		if (checkNumber(left, dLeft = left.toDouble()) && checkNumber(right, dRight = right.toDouble())) {
			return valueOf(dLeft - dRight);
		} else {
			return arithMetatable(state, SUB, left, right, leftIdx, rightIdx);
		}
	}

	public static LuaValue mul(LuaState state, LuaValue left, LuaValue right) throws LuaError, UnwindThrowable {
		return mul(state, left, right, -1, -1);
	}

	public static LuaValue mul(LuaState state, LuaValue left, LuaValue right, int leftIdx, int rightIdx) throws LuaError, UnwindThrowable {
		if (left instanceof LuaInteger && right instanceof LuaInteger) {
			return valueOf((long) ((LuaInteger) left).v * (long) ((LuaInteger) right).v);
		}

		double dLeft, dRight;
		if (checkNumber(left, dLeft = left.toDouble()) && checkNumber(right, dRight = right.toDouble())) {
			return valueOf(dLeft * dRight);
		} else {
			return arithMetatable(state, MUL, left, right, leftIdx, rightIdx);
		}
	}

	public static LuaValue div(LuaState state, LuaValue left, LuaValue right) throws LuaError, UnwindThrowable {
		return div(state, left, right, -1, -1);
	}

	public static LuaValue div(LuaState state, LuaValue left, LuaValue right, int leftIdx, int rightIdx) throws LuaError, UnwindThrowable {
		double dLeft, dRight;
		if (checkNumber(left, dLeft = left.toDouble()) && checkNumber(right, dRight = right.toDouble())) {
			return valueOf(div(dLeft, dRight));
		} else {
			return arithMetatable(state, DIV, left, right, leftIdx, rightIdx);
		}
	}

	public static LuaValue mod(LuaState state, LuaValue left, LuaValue right) throws LuaError, UnwindThrowable {
		return mod(state, left, right, -1, -1);
	}

	public static LuaValue mod(LuaState state, LuaValue left, LuaValue right, int leftIdx, int rightIdx) throws LuaError, UnwindThrowable {
		double dLeft, dRight;
		if (checkNumber(left, dLeft = left.toDouble()) && checkNumber(right, dRight = right.toDouble())) {
			return valueOf(mod(dLeft, dRight));
		} else {
			return arithMetatable(state, MOD, left, right, leftIdx, rightIdx);
		}
	}

	public static LuaValue pow(LuaState state, LuaValue left, LuaValue right) throws LuaError, UnwindThrowable {
		return pow(state, left, right, -1, -1);
	}

	public static LuaValue pow(LuaState state, LuaValue left, LuaValue right, int leftIdx, int rightIdx) throws LuaError, UnwindThrowable {
		double dLeft, dRight;
		if (checkNumber(left, dLeft = left.toDouble()) && checkNumber(right, dRight = right.toDouble())) {
			return valueOf(Math.pow(dLeft, dRight));
		} else {
			return arithMetatable(state, POW, left, right, leftIdx, rightIdx);
		}
	}

	/**
	 * Divide two double numbers according to lua math, and return a double result.
	 *
	 * @param lhs Left-hand-side of the division.
	 * @param rhs Right-hand-side of the division.
	 * @return Value of the division, taking into account positive and negative infinity, and Nan
	 */
	public static double div(double lhs, double rhs) {
		return rhs != 0 ? lhs / rhs : lhs > 0 ? Double.POSITIVE_INFINITY : lhs == 0 ? Double.NaN : Double.NEGATIVE_INFINITY;
	}

	/**
	 * Take modulo for double numbers according to lua math, and return a double result.
	 *
	 * @param lhs Left-hand-side of the modulo.
	 * @param rhs Right-hand-side of the modulo.
	 * @return double value for the result of the modulo,
	 * using lua's rules for modulo
	 */
	public static double mod(double lhs, double rhs) {
		double mod = lhs % rhs;
		return mod * rhs < 0 ? mod + rhs : mod;
	}

	/**
	 * Perform metatag processing for arithmetic operations.
	 *
	 * Finds the supplied metatag value for {@code this} or {@code op2} and invokes it,
	 * or throws {@link LuaError} if neither is defined.
	 *
	 * @param state      The current lua state
	 * @param tag        The metatag to look up
	 * @param left       The left operand value to perform the operation with
	 * @param right      The other operand value to perform the operation with
	 * @param leftStack  Stack index of the LHS
	 * @param rightStack Stack index of the RHS
	 * @return {@link LuaValue} resulting from metatag processing
	 * @throws LuaError        if metatag was not defined for either operand or the underlying operator errored.
	 * @throws UnwindThrowable If calling the metatable function yielded.
	 */
	public static LuaValue arithMetatable(LuaState state, LuaValue tag, LuaValue left, LuaValue right, int leftStack, int rightStack) throws LuaError, UnwindThrowable {
		return call(state, getMetatable(state, tag, left, right, leftStack, rightStack), left, right);
	}

	/**
	 * Perform metatag processing for arithmetic operations.
	 *
	 * Finds the supplied metatag value for {@code this} or {@code op2} and invokes it,
	 * or throws {@link LuaError} if neither is defined.
	 *
	 * @param state      The current lua state
	 * @param tag        The metatag to look up
	 * @param left       The left operand value to perform the operation with
	 * @param right      The other operand value to perform the operation with
	 * @param leftStack  Stack index of the LHS
	 * @param rightStack Stack index of the RHS
	 * @return {@link LuaValue} resulting from metatag processing
	 * @throws LuaError if metatag was not defined for either operand
	 */
	public static LuaValue getMetatable(LuaState state, LuaValue tag, LuaValue left, LuaValue right, int leftStack, int rightStack) throws LuaError {
		LuaValue h = left.metatag(state, tag);
		if (h.isNil()) {
			h = right.metatag(state, tag);
			if (h.isNil()) {
				if (left.isNumber()) {
					left = right;
					leftStack = rightStack;
				}
				throw ErrorFactory.operandError(state, left, "perform arithmetic on", leftStack);
			}
		}
		return h;
	}

	/**
	 * Perform metatag processing for concatenation operations.
	 *
	 * Finds the {@link Constants#CONCAT} metatag value and invokes it,
	 * or throws {@link LuaError} if it doesn't exist.
	 *
	 * @param state The current lua state
	 * @param left  The right-hand-side value to perform the operation with
	 * @param right The right-hand-side value to perform the operation with
	 * @return {@link LuaValue} resulting from metatag processing for {@link Constants#CONCAT} metatag.
	 * @throws LuaError        If the {@code __concat} metatag was not defined for either operand
	 * @throws UnwindThrowable If the {@code __concat} metamethod yielded.
	 */
	public static LuaValue concat(LuaState state, LuaValue left, LuaValue right) throws LuaError, UnwindThrowable {
		return concat(state, left, right, -1, -1);
	}

	public static LuaValue concat(LuaState state, LuaValue left, LuaValue right, int leftStack, int rightStack) throws LuaError, UnwindThrowable {
		if (left.isString() && right.isString()) {
			return concat(left.checkLuaString(), right.checkLuaString());
		} else {
			return concatNonStrings(state, left, right, leftStack, rightStack);
		}
	}

	public static LuaValue concatNonStrings(LuaState state, LuaValue left, LuaValue right, int leftStack, int rightStack) throws LuaError, UnwindThrowable {
		LuaValue h = left.metatag(state, Constants.CONCAT);
		if (h.isNil() && (h = right.metatag(state, Constants.CONCAT)).isNil()) {
			if (left.isString()) {
				throw ErrorFactory.operandError(state, right, "concatenate", rightStack);
			} else {
				throw ErrorFactory.operandError(state, left, "concatenate", leftStack);
			}
		}

		return OperationHelper.call(state, h, left, right);
	}

	public static LuaString concat(LuaString left, LuaString right) {
		byte[] b = new byte[left.length + right.length];
		System.arraycopy(left.bytes, left.offset, b, 0, left.length);
		System.arraycopy(right.bytes, right.offset, b, left.length, right.length);
		return LuaString.valueOf(b);
	}
	//endregion

	//region Compare
	public static boolean lt(LuaState state, LuaValue left, LuaValue right) throws LuaError, UnwindThrowable {
		int tLeft = left.type();
		if (tLeft != right.type()) {
			throw ErrorFactory.compareError(left, right);
		}

		if (left == right && (tLeft == TNUMBER || tLeft == TSTRING)) return false;

		switch (tLeft) {
			case TNUMBER:
				return left.toDouble() < right.toDouble();
			case TSTRING:
				return left.strvalue().compare(right.strvalue()) < 0;
			default:
				LuaValue h = left.metatag(state, Constants.LT);
				if (!h.isNil() && h == right.metatag(state, Constants.LT)) {
					return OperationHelper.call(state, h, left, right).toBoolean();
				} else {
					throw new LuaError("attempt to compare two " + left.typeName() + " values");
				}
		}
	}

	public static boolean le(LuaState state, LuaValue left, LuaValue right) throws LuaError, UnwindThrowable {
		int tLeft = left.type();
		if (tLeft != right.type()) {
			throw ErrorFactory.compareError(left, right);
		}

		if (left == right && (tLeft == TNUMBER || tLeft == TSTRING)) return (tLeft != TNUMBER) || !Double.isNaN(left.toDouble());

		switch (tLeft) {
			case TNUMBER:
				return left.toDouble() <= right.toDouble();
			case TSTRING:
				return left.strvalue().compare(right.strvalue()) <= 0;
			default:
				LuaValue h = left.metatag(state, Constants.LE);
				if (h.isNil()) {
					h = left.metatag(state, Constants.LT);
					if (!h.isNil() && h == right.metatag(state, Constants.LT)) {
						DebugFrame frame = DebugHandler.getDebugState(state).getStackUnsafe();

						frame.flags |= FLAG_LEQ;
						boolean result = !OperationHelper.call(state, h, right, left).toBoolean();
						frame.flags ^= FLAG_LEQ;

						return result;
					}
				} else if (h == right.metatag(state, Constants.LE)) {
					return OperationHelper.call(state, h, left, right).toBoolean();
				}

				throw new LuaError("attempt to compare two " + left.typeName() + " values");
		}
	}

	public static boolean eq(LuaState state, LuaValue left, LuaValue right) throws LuaError, UnwindThrowable {
		int tLeft = left.type();
		if (left == right) return (tLeft != TNUMBER) || !Double.isNaN(left.toDouble());

		if (tLeft != right.type()) return false;

		switch (tLeft) {
			case TNIL:
				return true;
			case TNUMBER:
				return left.toDouble() == right.toDouble();
			case TBOOLEAN:
				return left.toBoolean() == right.toBoolean();
			case TUSERDATA:
			case TTABLE: {
				if (left.raweq(right)) return true;

				LuaTable leftMeta = left.getMetatable(state);
				if (leftMeta == null) return false;

				LuaTable rightMeta = right.getMetatable(state);
				if (rightMeta == null) return false;

				LuaValue h = leftMeta.rawget(CachedMetamethod.EQ);
				return !(h.isNil() || h != rightMeta.rawget(CachedMetamethod.EQ)) && OperationHelper.call(state, h, left, right).toBoolean();
			}
			case TSTRING:
			default:
				return left.raweq(right);
		}
	}
	//endregion

	//region Unary

	/**
	 * Length operator: return lua length of object including metatag processing
	 *
	 * @param state The current lua state
	 * @param value The value to ge the length of
	 * @return length as defined by the lua # operator or metatag processing result
	 * @throws LuaError        If {@code value} is not a table or string, and has no {@link Constants#LEN} metatag
	 * @throws UnwindThrowable If the {@code __len} metamethod yielded.
	 */
	public static LuaValue length(LuaState state, LuaValue value) throws LuaError, UnwindThrowable {
		return length(state, value, -1);
	}

	public static LuaValue length(LuaState state, LuaValue value, int stack) throws LuaError, UnwindThrowable {
		switch (value.type()) {
			case Constants.TTABLE: {
				LuaValue h = value.metatag(state, CachedMetamethod.LEN);
				if (h.isNil()) {
					return valueOf(((LuaTable) value).length());
				} else {
					return OperationHelper.call(state, h, value);
				}
			}
			case TSTRING:
				return valueOf(((LuaBaseString) value).length());
			default: {
				LuaValue h = value.metatag(state, CachedMetamethod.LEN);
				if (h.isNil()) {
					throw ErrorFactory.operandError(state, value, "get length of", stack);
				}
				return OperationHelper.call(state, h, value);
			}
		}
	}

	/**
	 * Unary minus: return negative value {@code (-this)} as defined by lua unary minus operator
	 *
	 * @param state The current lua state
	 * @param value Value to get the minus of
	 * @return numeric inverse as {@link LuaNumber} if numeric, or metatag processing result if {@link Constants#UNM} metatag is defined
	 * @throws LuaError        If {@code value} is not a table or string, and has no {@link Constants#UNM} metatag
	 * @throws UnwindThrowable If the {@code __unm} metamethod yielded.
	 */
	public static LuaValue neg(LuaState state, LuaValue value) throws LuaError, UnwindThrowable {
		return neg(state, value, -1);
	}

	public static LuaValue neg(LuaState state, LuaValue value, int stack) throws LuaError, UnwindThrowable {
		int type = value.type();
		if (type == TNUMBER) {
			if (value instanceof LuaInteger) {
				int x = ((LuaInteger) value).v;
				if (x != Integer.MIN_VALUE) return valueOf(-x);
			}

			return valueOf(-value.toDouble());
		} else if (type == TSTRING) {
			double res = value.toDouble();
			if (!Double.isNaN(res)) return valueOf(-res);
		}

		LuaValue meta = value.metatag(state, Constants.UNM);
		if (meta.isNil()) {
			throw ErrorFactory.operandError(state, value, "perform arithmetic on", stack);
		}

		return OperationHelper.call(state, meta, value);
	}

	private static boolean checkNumber(LuaValue lua, double value) {
		return lua.type() == TNUMBER || (lua.type() == TSTRING && !Double.isNaN(value));
	}
	//endregion

	//region Calling
	public static LuaValue call(LuaState state, LuaValue function) throws LuaError, UnwindThrowable {
		return call(state, function, -1);
	}

	public static LuaValue call(LuaState state, LuaValue function, int stack) throws LuaError, UnwindThrowable {
		if (function.isFunction()) {
			return ((LuaFunction) function).call(state);
		} else {
			LuaValue meta = function.metatag(state, Constants.CALL);
			if (!meta.isFunction()) throw ErrorFactory.operandError(state, function, "call", stack);

			return ((LuaFunction) meta).call(state, function);
		}
	}

	public static LuaValue call(LuaState state, LuaValue function, LuaValue arg) throws LuaError, UnwindThrowable {
		return call(state, function, arg, -1);
	}

	public static LuaValue call(LuaState state, LuaValue function, LuaValue arg, int stack) throws LuaError, UnwindThrowable {
		if (function.isFunction()) {
			return ((LuaFunction) function).call(state, arg);
		} else {
			LuaValue meta = function.metatag(state, Constants.CALL);
			if (!meta.isFunction()) throw ErrorFactory.operandError(state, function, "call", stack);

			return ((LuaFunction) meta).call(state, function, arg);
		}
	}

	public static LuaValue call(LuaState state, LuaValue function, LuaValue arg1, LuaValue arg2) throws LuaError, UnwindThrowable {
		return call(state, function, arg1, arg2, -1);
	}

	public static LuaValue call(LuaState state, LuaValue function, LuaValue arg1, LuaValue arg2, int stack) throws LuaError, UnwindThrowable {
		if (function.isFunction()) {
			return ((LuaFunction) function).call(state, arg1, arg2);
		} else {
			LuaValue meta = function.metatag(state, Constants.CALL);
			if (!meta.isFunction()) throw ErrorFactory.operandError(state, function, "call", stack);

			return ((LuaFunction) meta).call(state, function, arg1, arg2);
		}
	}

	public static LuaValue call(LuaState state, LuaValue function, LuaValue arg1, LuaValue arg2, LuaValue arg3) throws LuaError, UnwindThrowable {
		return call(state, function, arg1, arg2, arg3, -1);
	}

	public static LuaValue call(LuaState state, LuaValue function, LuaValue arg1, LuaValue arg2, LuaValue arg3, int stack) throws LuaError, UnwindThrowable {
		if (function.isFunction()) {
			return ((LuaFunction) function).call(state, arg1, arg2, arg3);
		} else {
			LuaValue meta = function.metatag(state, Constants.CALL);
			if (!meta.isFunction()) throw ErrorFactory.operandError(state, function, "call", stack);

			return ((LuaFunction) meta).invoke(state, ValueFactory.varargsOf(function, arg1, arg2, arg3)).first();
		}
	}

	public static Varargs invoke(LuaState state, LuaValue function, Varargs args) throws LuaError, UnwindThrowable {
		return invoke(state, function, args, -1);
	}

	public static Varargs invoke(LuaState state, LuaValue function, Varargs args, int stack) throws LuaError, UnwindThrowable {
		if (function.isFunction()) {
			return ((LuaFunction) function).invoke(state, args);
		} else {
			LuaValue meta = function.metatag(state, Constants.CALL);
			if (!meta.isFunction()) throw ErrorFactory.operandError(state, function, "call", stack);

			return ((LuaFunction) meta).invoke(state, ValueFactory.varargsOf(function, args));
		}
	}

	//endregion

	//region Tables

	/**
	 * Return value for field reference including metatag processing, or {@link Constants#NIL} if it doesn't exist.
	 *
	 * @param state The current lua state
	 * @param t     {@link LuaValue} on which field is being referenced, typically a table or something with the metatag {@link Constants#INDEX} defined
	 * @param key   {@link LuaValue} naming the field to reference
	 * @return {@link LuaValue} for the {@code key} if it exists, or {@link Constants#NIL}
	 * @throws LuaError        If there is a loop in metatag processing
	 * @throws UnwindThrowable If the {@code __get} metamethod yielded.
	 */
	public static LuaValue getTable(LuaState state, LuaValue t, LuaValue key) throws LuaError, UnwindThrowable {
		return getTable(state, t, key, -1);
	}

	public static LuaValue getTable(LuaState state, LuaValue t, LuaValue key, int stack) throws LuaError, UnwindThrowable {
		LuaValue tm;
		int loop = 0;
		do {
			if (t.isTable()) {
				LuaValue res = ((LuaTable) t).rawget(key);
				if (!res.isNil() || (tm = t.metatag(state, CachedMetamethod.INDEX)).isNil()) {
					return res;
				}
			} else if ((tm = t.metatag(state, CachedMetamethod.INDEX)).isNil()) {
				throw ErrorFactory.operandError(state, t, "index", stack);
			}
			if (tm.isFunction()) {
				return ((LuaFunction) tm).call(state, t, key);
			}
			t = tm;
			stack = -1;
		}
		while (++loop < Constants.MAXTAGLOOP);
		throw new LuaError("loop in gettable");
	}

	/**
	 * Perform field assignment including metatag processing.
	 *
	 * @param state The current lua state
	 * @param t     {@link LuaValue} on which value is being set, typically a table or something with the metatag {@link Constants#NEWINDEX} defined
	 * @param key   {@link LuaValue} naming the field to assign
	 * @param value {@link LuaValue} the new value to assign to {@code key}
	 * @throws LuaError        If there is a loop in metatag processing
	 * @throws UnwindThrowable If the {@code __set} metamethod yielded.
	 */
	public static void setTable(LuaState state, LuaValue t, LuaValue key, LuaValue value) throws LuaError, UnwindThrowable {
		setTable(state, t, key, value, -1);
	}

	public static void setTable(LuaState state, LuaValue t, LuaValue key, LuaValue value, int stack) throws LuaError, UnwindThrowable {
		LuaValue tm;
		int loop = 0;
		do {
			if (t.isTable()) {
				LuaTable table = (LuaTable) t;
				key.checkValidKey();
				if (!table.rawget(key).isNil() || (tm = t.metatag(state, CachedMetamethod.NEWINDEX)).isNil()) {
					table.rawset(key, value);
					return;
				}
			} else if ((tm = t.metatag(state, CachedMetamethod.NEWINDEX)).isNil()) {
				throw ErrorFactory.operandError(state, t, "index", stack);
			}
			if (tm.isFunction()) {
				((LuaFunction) tm).call(state, t, key, value);
				return;
			}
			t = tm;
			stack = -1;
		}
		while (++loop < Constants.MAXTAGLOOP);
		throw new LuaError("loop in settable");
	}
	//endregion

	public interface LuaRunnable<T> {
		T run() throws LuaError, UnwindThrowable;
	}

	public interface LuaTask {
		void run() throws LuaError, UnwindThrowable;
	}

	public static <T> T noUnwind(LuaState state, LuaRunnable<T> task) throws LuaError {
		LuaThread.State threadState = state.getCurrentThread().state;
		threadState.javaCount++;
		try {
			return task.run();
		} catch (UnwindThrowable e) {
			throw new NonResumableException("Cannot raise UnwindThrowable while disabled");
		} finally {
			threadState.javaCount--;
		}
	}

	public static void noUnwind(LuaState state, LuaTask task) throws LuaError {
		LuaThread.State threadState = state.getCurrentThread().state;
		threadState.javaCount++;
		try {
			task.run();
		} catch (UnwindThrowable e) {
			throw new NonResumableException("Cannot raise UnwindThrowable while disabled");
		} finally {
			threadState.javaCount--;
		}
	}

	public static LuaValue toString(LuaState state, LuaValue value) throws LuaError, UnwindThrowable {
		LuaValue h = value.metatag(state, Constants.TOSTRING);
		return h.isNil() ? toStringDirect(value) : OperationHelper.call(state, h, value);
	}

	public static LuaString checkToString(LuaValue value) throws LuaError {
		LuaValue asStr = value.toLuaString();
		if (asStr.isNil()) throw new LuaError("'__tostring' must return a string");
		return (LuaString) asStr;
	}

	/**
	 * A version of {@link #toString(LuaState, LuaValue)} which doesn't obey metamethods.
	 *
	 * Technically this is wrong, as <code>luaL_tolstring</code> should use metamethods, but sometimes it's easier
	 * to not worry about them yielding.
	 *
	 * @param value The value to convert to a string.
	 * @return This value as a string.
	 * @see <a href="https://www.lua.org/source/5.3/lauxlib.c.html#luaL_tolstring">luaL_tolstring</a>
	 */
	public static LuaString toStringDirect(LuaValue value) {
		LuaValue v = value.toLuaString();
		return v.isNil() ? LuaString.valueOf(value.toString()) : (LuaString) v;
	}
}

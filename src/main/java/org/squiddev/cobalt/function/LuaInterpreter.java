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
import org.squiddev.cobalt.debug.DebugHandler;
import org.squiddev.cobalt.debug.DebugState;

import java.util.function.Function;

import static org.squiddev.cobalt.Constants.FALSE;
import static org.squiddev.cobalt.Constants.NIL;
import static org.squiddev.cobalt.Constants.NILS;
import static org.squiddev.cobalt.Constants.NONE;
import static org.squiddev.cobalt.Constants.TRUE;
import static org.squiddev.cobalt.Lua.*;
import static org.squiddev.cobalt.LuaDouble.valueOf;
import static org.squiddev.cobalt.debug.DebugFrame.FLAG_FRESH;
import static org.squiddev.cobalt.debug.DebugFrame.FLAG_LEQ;
import static org.squiddev.cobalt.debug.DebugFrame.FLAG_TAIL;

/**
 * The main interpreter for {@link LuaInterpretedFunction}s.
 */
public final class LuaInterpreter {
	static DebugFrame setupCall(LuaState state, LuaInterpretedFunction function, int flags) throws LuaError, UnwindThrowable {
		Prototype p = function.p;
		LuaValue[] stack = new LuaValue[p.maxstacksize];
		System.arraycopy(NILS, 0, stack, 0, p.maxstacksize);

		return setupCall(state, function, NONE, stack, flags);
	}

	static DebugFrame setupCall(LuaState state, LuaInterpretedFunction function, LuaValue arg, int flags) throws LuaError, UnwindThrowable {
		Prototype p = function.p;
		LuaValue[] stack = new LuaValue[p.maxstacksize];
		System.arraycopy(NILS, 0, stack, 0, p.maxstacksize);

		switch (p.numparams) {
			case 0:
				return setupCall(state, function, arg, stack, flags);

			default:
				stack[0] = arg;
				return setupCall(state, function, NONE, stack, flags);
		}
	}

	static DebugFrame setupCall(LuaState state, LuaInterpretedFunction function, LuaValue arg1, LuaValue arg2, int flags) throws LuaError, UnwindThrowable {
		Prototype p = function.p;
		LuaValue[] stack = new LuaValue[p.maxstacksize];
		System.arraycopy(NILS, 0, stack, 0, p.maxstacksize);

		switch (p.numparams) {
			case 0:
				return setupCall(state, function, p.is_vararg != 0 ? ValueFactory.varargsOf(arg1, arg2) : NONE, stack, flags);

			case 1:
				stack[0] = arg1;
				return setupCall(state, function, arg2, stack, flags);

			default:
				stack[0] = arg1;
				stack[1] = arg2;
				return setupCall(state, function, NONE, stack, flags);
		}
	}

	static DebugFrame setupCall(LuaState state, LuaInterpretedFunction function, LuaValue arg1, LuaValue arg2, LuaValue arg3, int flags) throws LuaError, UnwindThrowable {
		Prototype p = function.p;
		LuaValue[] stack = new LuaValue[p.maxstacksize];
		System.arraycopy(NILS, 0, stack, 0, p.maxstacksize);

		switch (p.numparams) {
			case 0:
				return setupCall(state, function, p.is_vararg != 0 ? ValueFactory.varargsOf(arg1, arg2, arg3) : NONE, stack, flags);

			case 1:
				stack[0] = arg1;
				return setupCall(state, function, p.is_vararg != 0 ? ValueFactory.varargsOf(arg2, arg3) : NONE, stack, flags);

			case 2:
				stack[0] = arg1;
				stack[1] = arg2;
				return setupCall(state, function, arg3, stack, flags);

			default:
				stack[0] = arg1;
				stack[1] = arg2;
				stack[2] = arg3;
				return setupCall(state, function, NONE, stack, flags);
		}
	}

	static DebugFrame setupCall(LuaState state, LuaInterpretedFunction function, Varargs varargs, int flags) throws LuaError, UnwindThrowable {
		Prototype p = function.p;
		LuaValue[] stack = new LuaValue[p.maxstacksize];
		System.arraycopy(NILS, 0, stack, 0, p.maxstacksize);
		for (int i = 0; i < p.numparams; i++) stack[i] = varargs.arg(i + 1);

		return setupCall(state, function, p.is_vararg != 0 ? varargs.subargs(p.numparams + 1) : NONE, stack, flags);
	}

	private static DebugFrame setupCall(LuaState state, LuaInterpretedFunction function, LuaValue[] args, int argStart, int argSize, Varargs varargs, int flags) throws LuaError, UnwindThrowable {
		Prototype p = function.p;
		LuaValue[] stack = new LuaValue[p.maxstacksize];
		System.arraycopy(NILS, 0, stack, 0, p.maxstacksize);

		varargs = ValueFactory.varargsOf(args, argStart, argSize, varargs);
		for (int i = 0; i < p.numparams; i++) stack[i] = varargs.arg(i + 1);

		return setupCall(state, function, p.is_vararg != 0 ? varargs.subargs(p.numparams + 1) : NONE, stack, flags);
	}

	private static DebugFrame setupCall(LuaState state, LuaInterpretedFunction function, Varargs varargs, LuaValue[] stack, int flags) throws LuaError, UnwindThrowable {
		Prototype p = function.p;
		Upvalue[] upvalues = p.p.length > 0 ? new Upvalue[stack.length] : null;
		if (p.is_vararg >= VARARG_NEEDSARG) stack[p.numparams] = new LuaTable(varargs);

		DebugState ds = DebugHandler.getDebugState(state);
		DebugFrame di = (flags & FLAG_FRESH) != 0 ? ds.pushJavaInfo() : ds.pushInfo();
		di.setFunction(function, varargs.asImmutable(), stack, upvalues);
		di.flags |= flags;
		di.extras = NONE;
		di.pc = 0;

		if (!ds.inhook && ds.hookcall) {
			// Pretend we are at the first instruction for the hook.
			ds.hookCall(di);
		}

		di.top = 0;
		return di;
	}

	static Varargs execute(LuaState state, DebugFrame di, LuaInterpretedFunction function) throws LuaError, UnwindThrowable {
		if (true)
			return partialEval(state, di, function);
		else
			return interpret(state, di, function);
	}

	static Varargs interpret(final LuaState state, DebugFrame di, LuaInterpretedFunction function) throws LuaError, UnwindThrowable {
		final DebugState ds = DebugHandler.getDebugState(state);
		final DebugHandler handler = state.debug;

		newFrame:
		while (true) {
			// Fetch all info from the function
			final Prototype p = function.p;
			final Upvalue[] upvalues = function.upvalues;
			final int[] code = p.code;
			final LuaValue[] k = p.k;

			// And from the debug info
			final LuaValue[] stack = di.stack;
			final Upvalue[] openups = di.stackUpvalues;
			final Varargs varargs = di.varargs;

			int pc = di.pc;

			// process instructions
			while (true) {
				handler.onInstruction(ds, di, pc);

				// pull out instruction
				int i = code[pc++];
				int a = ((i >> POS_A) & MAXARG_A);

				// process the instruction
				switch (((i >> POS_OP) & MAX_OP)) {
					case OP_MOVE: // A B: R(A):= R(B)
						stack[a] = stack[(i >>> POS_B) & MAXARG_B];
						break;

					case OP_LOADK: // A Bx: R(A):= Kst(Bx)
						stack[a] = k[(i >>> POS_Bx) & MAXARG_Bx];
						break;

					case OP_LOADBOOL: // A B C: R(A):= (Bool)B: if (C) pc++
						stack[a] = ((i >>> POS_B) & MAXARG_B) != 0 ? TRUE : FALSE;
						if (((i >>> POS_C) & MAXARG_C) != 0) pc++; // skip next instruction (if C)
						break;

					case OP_LOADNIL: { // A B: R(A):= ...:= R(B):= nil
						int b = ((i >>> POS_B) & MAXARG_B);
						do {
							stack[b--] = NIL;
						} while (b >= a);
						break;
					}

					case OP_GETUPVAL: // A B: R(A):= UpValue[B]
						stack[a] = upvalues[((i >>> POS_B) & MAXARG_B)].getValue();
						break;

					case OP_GETGLOBAL: // A Bx	R(A):= Gbl[Kst(Bx)]
						stack[a] = OperationHelper.getTable(state, function.env, k[(i >>> POS_Bx) & MAXARG_Bx]);
						break;

					case OP_GETTABLE: { // A B C: R(A):= R(B)[RK(C)]
						int b = (i >>> POS_B) & MAXARG_B;
						int c = (i >>> POS_C) & MAXARG_C;
						stack[a] = OperationHelper.getTable(state, stack[b], c > 0xff ? k[c & 0x0ff] : stack[c], b);
						break;
					}

					case OP_SETGLOBAL: // A Bx: Gbl[Kst(Bx)]:= R(A)
						OperationHelper.setTable(state, function.env, k[(i >>> POS_Bx) & MAXARG_Bx], stack[a]);
						break;

					case OP_SETUPVAL: // A B: UpValue[B]:= R(A)
						upvalues[(i >>> POS_B) & MAXARG_B].setValue(stack[a]);
						break;

					case OP_SETTABLE: { // A B C: R(A)[RK(B)]:= RK(C)
						int b = (i >>> POS_B) & MAXARG_B;
						int c = (i >>> POS_C) & MAXARG_C;
						OperationHelper.setTable(state, stack[a], b > 0xff ? k[b & 0x0ff] : stack[b], c > 0xff ? k[c & 0x0ff] : stack[c], a);
						break;
					}

					case OP_NEWTABLE: // A B C: R(A):= {} (size = B,C)
						stack[a] = new LuaTable((i >>> POS_B) & MAXARG_B, (i >>> POS_C) & MAXARG_C);
						break;

					case OP_SELF: { // A B C: R(A+1):= R(B): R(A):= R(B)[RK(C)]
						int b = (i >>> POS_B) & MAXARG_B;
						int c = (i >> POS_C) & MAXARG_C;
						LuaValue o = stack[a + 1] = stack[b];
						stack[a] = OperationHelper.getTable(state, o, c > 0xff ? k[c & 0x0ff] : stack[c], b);
						break;
					}

					case OP_ADD: { // A B C: R(A):= RK(B) + RK(C)
						int b = (i >>> POS_B) & MAXARG_B;
						int c = (i >> POS_C) & MAXARG_C;
						stack[a] = OperationHelper.add(state, b > 0xff ? k[b & 0x0ff] : stack[b], c > 0xff ? k[c & 0x0ff] : stack[c], b, c);
						break;
					}

					case OP_SUB: { // A B C: R(A):= RK(B) - RK(C)
						int b = (i >>> POS_B) & MAXARG_B;
						int c = (i >> POS_C) & MAXARG_C;
						stack[a] = OperationHelper.sub(state, b > 0xff ? k[b & 0x0ff] : stack[b], c > 0xff ? k[c & 0x0ff] : stack[c], b, c);
						break;
					}

					case OP_MUL: { // A B C: R(A):= RK(B) * RK(C)
						int b = (i >>> POS_B) & MAXARG_B;
						int c = (i >> POS_C) & MAXARG_C;
						stack[a] = OperationHelper.mul(state, b > 0xff ? k[b & 0x0ff] : stack[b], c > 0xff ? k[c & 0x0ff] : stack[c], b, c);
						break;
					}

					case OP_DIV: { // A B C: R(A):= RK(B) / RK(C)
						int b = (i >>> POS_B) & MAXARG_B;
						int c = (i >> POS_C) & MAXARG_C;
						stack[a] = OperationHelper.div(state, b > 0xff ? k[b & 0x0ff] : stack[b], c > 0xff ? k[c & 0x0ff] : stack[c], b, c);
						break;
					}

					case OP_MOD: { // A B C: R(A):= RK(B) % RK(C)
						int b = (i >>> POS_B) & MAXARG_B;
						int c = (i >> POS_C) & MAXARG_C;
						stack[a] = OperationHelper.mod(state, b > 0xff ? k[b & 0x0ff] : stack[b], c > 0xff ? k[c & 0x0ff] : stack[c], b, c);
						break;
					}

					case OP_POW: { // A B C: R(A):= RK(B) ^ RK(C)
						int b = (i >>> POS_B) & MAXARG_B;
						int c = (i >> POS_C) & MAXARG_C;
						stack[a] = OperationHelper.pow(state, b > 0xff ? k[b & 0x0ff] : stack[b], c > 0xff ? k[c & 0x0ff] : stack[c], b, c);
						break;
					}

					case OP_UNM: { // A B: R(A):= -R(B)
						int b = (i >>> POS_B) & MAXARG_B;
						stack[a] = OperationHelper.neg(state, b > 0xff ? k[b & 0x0ff] : stack[b], b);
						break;
					}

					case OP_NOT: // A B: R(A):= not R(B)
						stack[a] = stack[(i >>> POS_B) & MAXARG_B].toBoolean() ? FALSE : TRUE;
						break;

					case OP_LEN: { // A B: R(A):= length of R(B)
						int b = (i >>> POS_B) & MAXARG_B;
						stack[a] = OperationHelper.length(state, stack[b], b);
						break;
					}

					case OP_CONCAT: { // A B C: R(A):= R(B).. ... ..R(C)
						int b = (i >>> POS_B) & MAXARG_B;
						int c = (i >> POS_C) & MAXARG_C;

						di.top = c + 1;
						concat(state, di, stack, di.top, c - b + 1);
						stack[a] = stack[b];
						di.top = b;
						break;
					}

					case OP_JMP: // sBx: pc+=sBx
						pc += ((i >>> POS_Bx) & MAXARG_Bx) - MAXARG_sBx;
						break;

					case OP_EQ: { // A B C: if ((RK(B) == RK(C)) ~= A) then pc++
						int b = (i >>> POS_B) & MAXARG_B;
						int c = (i >> POS_C) & MAXARG_C;
						if (OperationHelper.eq(state, b > 0xff ? k[b & 0x0ff] : stack[b], c > 0xff ? k[c & 0x0ff] : stack[c]) == (a != 0)) {
							// We assume the next instruction is a jump and read the branch from there.
							pc += ((code[pc] >> POS_Bx) & MAXARG_Bx) - MAXARG_sBx;
						}
						pc++;
						break;
					}

					case OP_LT: { // A B C: if ((RK(B) <  RK(C)) ~= A) then pc++
						int b = (i >>> POS_B) & MAXARG_B;
						int c = (i >> POS_C) & MAXARG_C;
						if (OperationHelper.lt(state, b > 0xff ? k[b & 0x0ff] : stack[b], c > 0xff ? k[c & 0x0ff] : stack[c]) == (a != 0)) {
							pc += ((code[pc] >> POS_Bx) & MAXARG_Bx) - MAXARG_sBx;
						}
						pc++;
						break;
					}

					case OP_LE: { // A B C: if ((RK(B) <= RK(C)) ~= A) then pc++
						int b = (i >>> POS_B) & MAXARG_B;
						int c = (i >> POS_C) & MAXARG_C;
						if (OperationHelper.le(state, b > 0xff ? k[b & 0x0ff] : stack[b], c > 0xff ? k[c & 0x0ff] : stack[c]) == (a != 0)) {
							pc += ((code[pc] >> POS_Bx) & MAXARG_Bx) - MAXARG_sBx;
						}
						pc++;
						break;
					}

					case OP_TEST: // A C: if not (R(A) <=> C) then pc++
						if (stack[a].toBoolean() == (((i >> POS_C) & MAXARG_C) != 0)) {
							pc += ((code[pc] >> POS_Bx) & MAXARG_Bx) - MAXARG_sBx;
						}
						pc++;
						break;

					case OP_TESTSET: { // A B C: if (R(B) <=> C) then R(A):= R(B) else pc++
						/* note: doc appears to be reversed */
						int b = (i >>> POS_B) & MAXARG_B;
						int c = (i >> POS_C) & MAXARG_C;
						LuaValue val = stack[b];
						if (val.toBoolean() == (c != 0)) {
							stack[a] = val;
							pc += ((code[pc] >> POS_Bx) & MAXARG_Bx) - MAXARG_sBx;
						}
						pc++;
						break;
					}

					case OP_CALL: { // A B C: R(A), ... ,R(A+C-2):= R(A)(R(A+1), ... ,R(A+B-1)) */
						int b = (i >>> POS_B) & MAXARG_B;
						int c = ((i >> POS_C) & MAXARG_C);

						LuaValue val = stack[a];
						if (val instanceof LuaInterpretedFunction) {
							function = (LuaInterpretedFunction) val;
							switch (b) {
								case 1:
									di = setupCall(state, function, 0);
									break;
								case 2:
									di = setupCall(state, function, stack[a + 1], 0);
									break;
								case 3:
									di = setupCall(state, function, stack[a + 1], stack[a + 2], 0);
									break;
								case 4:
									di = setupCall(state, function, stack[a + 1], stack[a + 2], stack[a + 3], 0);
									break;
								default:
									di = b > 0
										? setupCall(state, function, stack, a + 1, b - 1, NONE, 0) // exact arg count
										: setupCall(state, function, stack, a + 1, di.top - di.extras.count() - (a + 1), di.extras, 0); // from prev top
							}

							continue newFrame;
						}

						switch (i & (MASK_B | MASK_C)) {
							case (1 << POS_B) | (0 << POS_C): {
								Varargs v = di.extras = OperationHelper.invoke(state, val, NONE, a);
								di.top = a + v.count();
								break;
							}
							case (2 << POS_B) | (0 << POS_C): {
								Varargs v = di.extras = OperationHelper.invoke(state, val, stack[a + 1], a);
								di.top = a + v.count();
								break;
							}
							case (1 << POS_B) | (1 << POS_C):
								OperationHelper.call(state, val, a);
								break;
							case (2 << POS_B) | (1 << POS_C):
								OperationHelper.call(state, val, stack[a + 1], a);
								break;
							case (3 << POS_B) | (1 << POS_C):
								OperationHelper.call(state, val, stack[a + 1], stack[a + 2], a);
								break;
							case (4 << POS_B) | (1 << POS_C):
								OperationHelper.call(state, val, stack[a + 1], stack[a + 2], stack[a + 3], a);
								break;
							case (1 << POS_B) | (2 << POS_C):
								stack[a] = OperationHelper.call(state, val, a);
								break;
							case (2 << POS_B) | (2 << POS_C):
								stack[a] = OperationHelper.call(state, val, stack[a + 1], a);
								break;
							case (3 << POS_B) | (2 << POS_C):
								stack[a] = OperationHelper.call(state, val, stack[a + 1], stack[a + 2], a);
								break;
							case (4 << POS_B) | (2 << POS_C):
								stack[a] = OperationHelper.call(state, val, stack[a + 1], stack[a + 2], stack[a + 3], a);
								break;
							default: {
								Varargs args = b > 0 ?
									ValueFactory.varargsOf(stack, a + 1, b - 1) : // exact arg count
									ValueFactory.varargsOf(stack, a + 1, di.top - di.extras.count() - (a + 1), di.extras); // from prev top
								Varargs v = OperationHelper.invoke(state, val, args.asImmutable(), a);
								if (c > 0) {
									while (--c > 0) stack[a + c - 1] = v.arg(c);
									v = NONE;
								} else {
									di.top = a + v.count();
									di.extras = v;
								}
								break;
							}
						}
						break;
					}

					case OP_TAILCALL: { // A B C: return R(A)(R(A+1), ... ,R(A+B-1))
						int b = (i >>> POS_B) & MAXARG_B;

						LuaValue val = stack[a];
						Varargs args;
						switch (b) {
							case 1:
								args = NONE;
								break;
							case 2:
								args = stack[a + 1];
								break;
							default: {
								Varargs v = di.extras;
								args = b > 0 ?
									ValueFactory.varargsOf(stack, a + 1, b - 1) : // exact arg count
									ValueFactory.varargsOf(stack, a + 1, di.top - v.count() - (a + 1), v); // from prev top
							}
						}

						LuaFunction functionVal;
						if (val.isFunction()) {
							functionVal = (LuaFunction) val;
						} else {
							LuaValue meta = val.metatag(state, Constants.CALL);
							if (!meta.isFunction()) throw ErrorFactory.operandError(state, val, "call", a);

							functionVal = (LuaFunction) meta;
							args = ValueFactory.varargsOf(val, args);
						}

						if (functionVal instanceof LuaInterpretedFunction) {
							int flags = di.flags;
							closeAll(openups);
							ds.popInfo();

							// Replace the current frame with a new one.
							function = (LuaInterpretedFunction) functionVal;
							di = setupCall(state, function, args, (flags & FLAG_FRESH) | FLAG_TAIL);

							continue newFrame;
						} else {
							Varargs v = functionVal.invoke(state, args.asImmutable());
							di.top = a + v.count();
							di.extras = v;
							break;
						}
					}

					case OP_RETURN: { // A B: return R(A), ... ,R(A+B-2) (see note)
						int b = (i >>> POS_B) & MAXARG_B;

						int flags = di.flags, top = di.top;
						Varargs v = di.extras;
						closeAll(openups);
						handler.onReturn(ds, di);

						Varargs ret;
						switch (b) {
							case 0:
								ret = ValueFactory.varargsOf(stack, a, top - v.count() - a, v).asImmutable();
								break;
							case 1:
								ret = NONE;
								break;
							case 2:
								ret = stack[a];
								break;
							default:
								ret = ValueFactory.varargsOf(stack, a, b - 1).asImmutable();
								break;
						}

						if ((flags & FLAG_FRESH) != 0) {
							// If we're a fresh invocation then return to the parent.
							return ret;
						} else {
							di = ds.getStackUnsafe();
							function = (LuaInterpretedFunction) di.closure;
							resume(state, di, function, ret);
							continue newFrame;
						}
					}

					case OP_FORLOOP: { // A sBx: R(A)+=R(A+2): if R(A) <?= R(A+1) then { pc+=sBx: R(A+3)=R(A) }
						double limit = stack[a + 1].checkDouble();
						double step = stack[a + 2].checkDouble();
						double value = stack[a].checkDouble();
						double idx = step + value;
						if (0 < step ? idx <= limit : limit <= idx) {
							stack[a + 3] = stack[a] = valueOf(idx);
							pc += ((i >>> POS_Bx) & MAXARG_Bx) - MAXARG_sBx;
						}
					}
					break;

					case OP_FORPREP: { // A sBx: R(A)-=R(A+2): pc+=sBx
						LuaNumber init = stack[a].checkNumber("'for' initial value must be a number");
						LuaNumber limit = stack[a + 1].checkNumber("'for' limit must be a number");
						LuaNumber step = stack[a + 2].checkNumber("'for' step must be a number");
						stack[a] = valueOf(init.toDouble() - step.toDouble());
						stack[a + 1] = limit;
						stack[a + 2] = step;
						pc += ((i >>> POS_Bx) & MAXARG_Bx) - MAXARG_sBx;
					}
					break;

					case OP_TFORLOOP: {
							/*
								A C R(A+3), ... ,R(A+2+C):= R(A)(R(A+1),
								R(A+2)): if R(A+3) ~= nil then R(A+2)=R(A+3)
								else pc++
							*/
						Varargs v = di.extras = OperationHelper.invoke(state, stack[a], ValueFactory.varargsOf(stack[a + 1], stack[a + 2]), a);
						LuaValue val = v.first();
						if (val.isNil()) {
							pc++;
						} else {
							stack[a + 2] = stack[a + 3] = val;
							for (int c = (i >> POS_C) & MAXARG_C; c > 1; --c) {
								stack[a + 2 + c] = v.arg(c);
							}
							di.extras = NONE;
						}
						break;
					}

					case OP_SETLIST: { // A B C: R(A)[(C-1)*FPF+i]:= R(A+i), 1 <= i <= B
						int b = (i >>> POS_B) & MAXARG_B;
						int c = (i >> POS_C) & MAXARG_C;
						if (c == 0) c = code[pc++];

						int offset = (c - 1) * LFIELDS_PER_FLUSH;
						LuaTable tbl = stack[a].checkTable();
						if (b == 0) {
							b = di.top - a - 1;
							int m = b - di.extras.count();
							int j = 1;
							for (; j <= m; j++) {
								tbl.rawset(offset + j, stack[a + j]);
							}
							for (; j <= b; j++) {
								tbl.rawset(offset + j, di.extras.arg(j - m));
							}
						} else {
							tbl.presize(offset + b);
							for (int j = 1; j <= b; j++) {
								tbl.rawset(offset + j, stack[a + j]);
							}
						}
						break;
					}

					case OP_CLOSE: { // A : close all variables in the stack up to (>=) R(A)
						for (int x = openups.length; --x >= a; ) {
							Upvalue upvalue = openups[x];
							if (upvalue != null) {
								upvalue.close();
								openups[x] = null;
							}
						}
						break;
					}

					case OP_CLOSURE: { // A Bx: R(A):= closure(KPROTO[Bx], R(A), ... ,R(A+n))
						Prototype newp = p.p[(i >>> POS_Bx) & MAXARG_Bx];
						LuaInterpretedFunction newcl = new LuaInterpretedFunction(newp, function.env);
						for (int j = 0, nup = newp.nups; j < nup; ++j) {
							i = code[pc++];
							int b = (i >>> POS_B) & MAXARG_B;
							newcl.upvalues[j] = (i & 4) != 0
								? upvalues[b] // OP_GETUPVAL
								: openups[b] != null ? openups[b] : (openups[b] = new Upvalue(stack, b)); // OP_MOVE
						}
						stack[a] = newcl;
						break;
					}

					case OP_VARARG: { // A B: R(A), R(A+1), ..., R(A+B-1) = vararg
						int b = (i >>> POS_B) & MAXARG_B;
						if (b == 0) {
							di.top = a + varargs.count();
							di.extras = varargs;
						} else {
							for (int j = 1; j < b; ++j) {
								stack[a + j - 1] = varargs.arg(j);
							}
						}
					}
				}
			}
		}
	}

	private static Function<UnwindableRunnable, UnwindableCallable> continuation(LuaState state, int pc, Prototype proto) {
		return f -> {
			final UnwindableCallable callable = (di, c) -> {
				f.run(di);
				c.programCounter = di.pc + 1;
			};

			proto.compiledInstrs[pc] = callable;
			return callable;
		};
	}

	private static Function<UnwindableCallable, UnwindableCallable> rawCont(LuaState state, int pc, Prototype proto) {
		return raw -> {
			final UnwindableCallable f = (di, c) -> {
				raw.call(di, c);
			};

			proto.compiledInstrs[pc] = f;
			return f;
		};
	}

	static Varargs partialEval(final LuaState state, DebugFrame di, LuaInterpretedFunction function) throws LuaError, UnwindThrowable {
		Prototype proto = function.getPrototype();
		EvalCont cont = new EvalCont();
		final DebugHandler handler = state.debug;
		DebugState ds = DebugHandler.getDebugState(state);

		while (true) {
			handler.onInstruction(ds, di, di.pc);
			final UnwindableCallable compiledInstr = proto.compiledInstrs[di.pc];
			if (compiledInstr != null) {
				compiledInstr.call(di, cont);
			} else {
				partialEvalStep(state, di, function).call(di, cont);
			}

			if (cont.varargs != null) return cont.varargs;
			if (cont.debugFrame != null) {
				di = cont.debugFrame;
				cont.programCounter = di.pc;
				function = cont.function;
				proto = function.getPrototype();
				ds = DebugHandler.getDebugState(state);
				cont.debugFrame = null;
				cont.function = null;
			}

			di.pc = cont.programCounter;
		}
	}

	// FIXME beware: if a reference to initialFrame makes it into any of the lambdas it could introduce serious memory leaks
	static UnwindableCallable partialEvalStep(final LuaState state, final DebugFrame initialFrame, final LuaInterpretedFunction initialClosure) {
		final DebugHandler handler = state.debug;
		final Prototype p = initialClosure.p;
		final int pc = initialFrame.pc;

		// Fetch all info from the function
		final int[] code = p.code;
		final LuaValue[] k = p.k;

		// pull out instruction
		final int i = code[pc];
		final int a = (i >> POS_A) & MAXARG_A;

		final Function<UnwindableRunnable, UnwindableCallable> cont = continuation(state, pc, p);
		final Function<UnwindableCallable, UnwindableCallable> raw = rawCont(state, pc, p);

		// process the instruction
		switch (((i >> POS_OP) & MAX_OP)) {
			case OP_MOVE: { // A B: R(A):= R(B)
				final int b = (i >>> POS_B) & MAXARG_B;
				return cont.apply(di -> di.stack[a] = di.stack[b]);
			}

			case OP_LOADK: { // A Bx: R(A):= Kst(Bx)
				final LuaValue konst = k[(i >>> POS_Bx) & MAXARG_Bx];
				return cont.apply(di -> di.stack[a] = konst);
			}

			case OP_LOADBOOL: { // A B C: R(A):= (Bool)B: if (C) pc++
				LuaBoolean value = ((i >>> POS_B) & MAXARG_B) != 0 ? TRUE : FALSE;
				// skip next instruction (if C)
				if (((i >>> POS_C) & MAXARG_C) != 0) {
					return raw.apply((di, c) -> {
						di.stack[a] = value;
						c.programCounter = pc + 2;
					});
				}

				return cont.apply(di -> di.stack[a] = value);
			}

			case OP_LOADNIL: { // A B: R(A):= ...:= R(B):= nil
				final int init = ((i >>> POS_B) & MAXARG_B);
				return cont.apply(di -> {
					int b = init;
					do {
						di.stack[b--] = NIL;
					} while (b >= a);
				});
			}

			case OP_GETUPVAL: { // A B: R(A):= UpValue[B]
				final int b = (i >>> POS_B) & MAXARG_B;
				return cont.apply(di -> {
					LuaInterpretedFunction closure = (LuaInterpretedFunction) di.closure;
					di.stack[a] = closure.upvalues[b].getValue();
				});
			}

			case OP_GETGLOBAL: { // A Bx	R(A):= Gbl[Kst(Bx)]
				final LuaValue konstKey = k[(i >>> POS_Bx) & MAXARG_Bx];
				return cont.apply(di -> di.stack[a] = OperationHelper.getTable(state, di.func.env, konstKey));
			}

			case OP_GETTABLE: { // A B C: R(A):= R(B)[RK(C)]
				final int b = (i >>> POS_B) & MAXARG_B;
				final int c = (i >>> POS_C) & MAXARG_C;
				if (c > 0xff) {
					final LuaValue konst = k[c & 0x0ff];
					return cont.apply(di -> di.stack[a] = OperationHelper.getTable(state, di.stack[b], konst, b));
				}

				return cont.apply(di -> di.stack[a] = OperationHelper.getTable(state, di.stack[b], di.stack[c], b));
			}

			case OP_SETGLOBAL: { // A Bx: Gbl[Kst(Bx)]:= R(A)
				final LuaValue konst = k[(i >>> POS_Bx) & MAXARG_Bx];
				return cont.apply(di -> OperationHelper.setTable(state, di.func.env, konst, di.stack[a]));
			}

			case OP_SETUPVAL: { // A B: UpValue[B]:= R(A)
				final int b = (i >>> POS_B) & MAXARG_B;
				return cont.apply(di -> {
					LuaInterpretedFunction closure = (LuaInterpretedFunction) di.closure;
					closure.upvalues[b].setValue(di.stack[a]);
				});
			}

			case OP_SETTABLE: { // A B C: R(A)[RK(B)]:= RK(C)
				final int b = (i >>> POS_B) & MAXARG_B;
				final int c = (i >>> POS_C) & MAXARG_C;
				if (b > 0xff) {
					final LuaValue konstKey = k[b & 0x0ff];
					if (c > 0xff) {
						final LuaValue konstValue = k[c & 0x0ff];
						return cont.apply(di -> OperationHelper.setTable(state, di.stack[a], konstKey, konstValue, a));
					}

					return cont.apply(di -> OperationHelper.setTable(state, di.stack[a], konstKey, di.stack[c], a));
				}

				if (c > 0xff) {
					final LuaValue konstValue = k[c & 0x0ff];
					return cont.apply(di -> OperationHelper.setTable(state, di.stack[a], di.stack[b], konstValue, a));
				}

				return cont.apply(di -> OperationHelper.setTable(state, di.stack[a], di.stack[b], di.stack[c], a));
			}

			case OP_NEWTABLE: { // A B C: R(A):= {} (size = B,C)
				final int narray = (i >>> POS_B) & MAXARG_B;
				final int nhash = (i >>> POS_C) & MAXARG_C;

				return cont.apply(di -> di.stack[a] = new LuaTable(narray, nhash));
			}

			case OP_SELF: { // A B C: R(A+1):= R(B): R(A):= R(B)[RK(C)]
				final int b = (i >>> POS_B) & MAXARG_B;
				final int c = (i >> POS_C) & MAXARG_C;

				if (c > 0xff) {
					final LuaValue konstKey = k[c & 0x0ff];
					return cont.apply(di -> {
						LuaValue o = di.stack[a + 1] = di.stack[b];
						di.stack[a] = OperationHelper.getTable(state, o, konstKey, b);
					});
				}

				return cont.apply(di -> {
					LuaValue o = di.stack[a + 1] = di.stack[b];
					di.stack[a] = OperationHelper.getTable(state, o, di.stack[c], b);
				});
			}

			case OP_ADD: { // A B C: R(A):= RK(B) + RK(C)
				final int b = (i >>> POS_B) & MAXARG_B;
				final int c = (i >> POS_C) & MAXARG_C;
				if (b > 0xff) {
					final LuaValue konstLeft = k[b & 0x0ff];
					if (c > 0xff) {
						final LuaValue konstRight = k[c & 0x0ff];
						return cont.apply(di -> di.stack[a] = OperationHelper.add(state, konstLeft, konstRight, b, c));
					}

					return cont.apply(di -> di.stack[a] = OperationHelper.add(state, konstLeft, di.stack[c], b, c));
				}

				if (c > 0xff) {
					final LuaValue konstRight = k[c & 0x0ff];
					return cont.apply(di -> di.stack[a] = OperationHelper.add(state, di.stack[b], konstRight, b, c));
				}

				return cont.apply(di -> di.stack[a] = OperationHelper.add(state, di.stack[b], di.stack[c], b, c));
			}

			case OP_SUB: { // A B C: R(A):= RK(B) - RK(C)
				final int b = (i >>> POS_B) & MAXARG_B;
				final int c = (i >> POS_C) & MAXARG_C;
				if (b > 0xff) {
					final LuaValue konstLeft = k[b & 0x0ff];
					if (c > 0xff) {
						final LuaValue konstRight = k[c & 0x0ff];
						return cont.apply(di -> di.stack[a] = OperationHelper.sub(state, konstLeft, konstRight, b, c));
					}

					return cont.apply(di -> di.stack[a] = OperationHelper.sub(state, konstLeft, di.stack[c], b, c));
				}

				if (c > 0xff) {
					final LuaValue konstRight = k[c & 0x0ff];
					return cont.apply(di -> di.stack[a] = OperationHelper.sub(state, di.stack[b], konstRight, b, c));
				}

				return cont.apply(di -> di.stack[a] = OperationHelper.sub(state, di.stack[b], di.stack[c], b, c));
			}

			case OP_MUL: { // A B C: R(A):= RK(B) * RK(C)
				final int b = (i >>> POS_B) & MAXARG_B;
				final int c = (i >> POS_C) & MAXARG_C;
				if (b > 0xff) {
					final LuaValue konstLeft = k[b & 0x0ff];
					if (c > 0xff) {
						final LuaValue konstRight = k[c & 0x0ff];
						return cont.apply(di -> di.stack[a] = OperationHelper.mul(state, konstLeft, konstRight, b, c));
					}

					return cont.apply(di -> di.stack[a] = OperationHelper.mul(state, konstLeft, di.stack[c], b, c));
				}

				if (c > 0xff) {
					final LuaValue konstRight = k[c & 0x0ff];
					return cont.apply(di -> di.stack[a] = OperationHelper.mul(state, di.stack[b], konstRight, b, c));
				}

				return cont.apply(di -> di.stack[a] = OperationHelper.mul(state, di.stack[b], di.stack[c], b, c));
			}

			case OP_DIV: { // A B C: R(A):= RK(B) / RK(C)
				final int b = (i >>> POS_B) & MAXARG_B;
				final int c = (i >> POS_C) & MAXARG_C;
				if (b > 0xff) {
					final LuaValue konstLeft = k[b & 0x0ff];
					if (c > 0xff) {
						final LuaValue konstRight = k[c & 0x0ff];
						return cont.apply(di -> di.stack[a] = OperationHelper.div(state, konstLeft, konstRight, b, c));
					}

					return cont.apply(di -> di.stack[a] = OperationHelper.div(state, konstLeft, di.stack[c], b, c));
				}

				if (c > 0xff) {
					final LuaValue konstRight = k[c & 0x0ff];
					return cont.apply(di -> di.stack[a] = OperationHelper.div(state, di.stack[b], konstRight, b, c));
				}

				return cont.apply(di -> di.stack[a] = OperationHelper.div(state, di.stack[b], di.stack[c], b, c));
			}

			case OP_MOD: { // A B C: R(A):= RK(B) % RK(C)
				final int b = (i >>> POS_B) & MAXARG_B;
				final int c = (i >> POS_C) & MAXARG_C;
				if (b > 0xff) {
					final LuaValue konstLeft = k[b & 0x0ff];
					if (c > 0xff) {
						final LuaValue konstRight = k[c & 0x0ff];
						return cont.apply(di -> di.stack[a] = OperationHelper.mod(state, konstLeft, konstRight, b, c));
					}

					return cont.apply(di -> di.stack[a] = OperationHelper.mod(state, konstLeft, di.stack[c], b, c));
				}

				if (c > 0xff) {
					final LuaValue konstRight = k[c & 0x0ff];
					return cont.apply(di -> di.stack[a] = OperationHelper.mod(state, di.stack[b], konstRight, b, c));
				}

				return cont.apply(di -> di.stack[a] = OperationHelper.mod(state, di.stack[b], di.stack[c], b, c));
			}

			case OP_POW: { // A B C: R(A):= RK(B) ^ RK(C)
				final int b = (i >>> POS_B) & MAXARG_B;
				final int c = (i >> POS_C) & MAXARG_C;
				if (b > 0xff) {
					final LuaValue konstLeft = k[b & 0x0ff];
					if (c > 0xff) {
						final LuaValue konstRight = k[c & 0x0ff];
						return cont.apply(di -> di.stack[a] = OperationHelper.pow(state, konstLeft, konstRight, b, c));
					}

					return cont.apply(di -> di.stack[a] = OperationHelper.pow(state, konstLeft, di.stack[c], b, c));
				}

				if (c > 0xff) {
					final LuaValue konstRight = k[c & 0x0ff];
					return cont.apply(di -> di.stack[a] = OperationHelper.pow(state, di.stack[b], konstRight, b, c));
				}

				return cont.apply(di -> di.stack[a] = OperationHelper.pow(state, di.stack[b], di.stack[c], b, c));
			}

			case OP_UNM: { // A B: R(A):= -R(B)
				final int b = (i >>> POS_B) & MAXARG_B;

				if (b > 0xff) {
					final LuaValue konst = k[b & 0x0ff];
					return cont.apply(di -> di.stack[a] = OperationHelper.neg(state, konst, b));
				}

				return cont.apply(di -> di.stack[a] = OperationHelper.neg(state, di.stack[b], b));
			}

			case OP_NOT: { // A B: R(A):= not R(B)
				final int b = (i >>> POS_B) & MAXARG_B;
				return cont.apply(di -> di.stack[a] = di.stack[b].toBoolean() ? FALSE : TRUE);
			}

			case OP_LEN: { // A B: R(A):= length of R(B)
				final int b = (i >>> POS_B) & MAXARG_B;
				return cont.apply(di -> di.stack[a] = OperationHelper.length(state, di.stack[b], b));
			}

			case OP_CONCAT: { // A B C: R(A):= R(B).. ... ..R(C)
				final int b = (i >>> POS_B) & MAXARG_B;
				final int c = (i >> POS_C) & MAXARG_C;
				final int total = c - b + 1;

				return cont.apply(di -> {
					di.top = c + 1;
					concat(state, di, di.stack, di.top, total);
					di.stack[a] = di.stack[b];
					di.top = b;
				});
			}

			case OP_JMP: { // sBx: pc+=sBx
				final int offset = ((i >>> POS_Bx) & MAXARG_Bx) - MAXARG_sBx + 1;
				return raw.apply((di, c) -> c.programCounter = pc + offset);
			}

			// TODO precompute comparisons of constants
			case OP_EQ: { // A B C: if ((RK(B) == RK(C)) ~= A) then pc++
				final int b = (i >>> POS_B) & MAXARG_B;
				final int c = (i >> POS_C) & MAXARG_C;
				final boolean aNotZero = a != 0;

				// We assume the next instruction is a jump and read the branch from there.
				final int offset = ((code[pc + 1] >> POS_Bx) & MAXARG_Bx) - MAXARG_sBx + 2;

				if (b > 0xff) {
					final LuaValue konstLeft = k[b & 0x0ff];
					if (c > 0xff) {
						final LuaValue konstRight = k[c & 0x0ff];
						return raw.apply((di, continuation) -> continuation.programCounter = (
								OperationHelper.eq(state, konstLeft, konstRight) == aNotZero
										? pc + offset
										: pc + 2
						));
					}
					return raw.apply((di, continuation) -> continuation.programCounter = (
							OperationHelper.eq(state, konstLeft, di.stack[c]) == aNotZero
									? pc + offset
									: pc + 2
					));
				}
				if (c > 0xff) {
					final LuaValue konstRight = k[c & 0x0ff];
					return raw.apply((di, continuation) -> continuation.programCounter = (
							OperationHelper.eq(state, di.stack[b], konstRight) == aNotZero
									? pc + offset
									: pc + 2
					));
				}
				return raw.apply((di, continuation) -> continuation.programCounter = (
						OperationHelper.eq(state, di.stack[b], di.stack[c]) == aNotZero
								? pc + offset
								: pc + 2
				));
			}

			case OP_LT: { // A B C: if ((RK(B) <  RK(C)) ~= A) then pc++
				final int b = (i >>> POS_B) & MAXARG_B;
				final int c = (i >> POS_C) & MAXARG_C;
				final boolean aNotZero = a != 0;
				final int offset = ((code[pc + 1] >> POS_Bx) & MAXARG_Bx) - MAXARG_sBx + 2;

				if (b > 0xff) {
					final LuaValue konstLeft = k[b & 0x0ff];
					if (c > 0xff) {
						final LuaValue konstRight = k[c & 0x0ff];
						return raw.apply((di, continuation) -> continuation.programCounter = (
								OperationHelper.lt(state, konstLeft, konstRight) == aNotZero
										? pc + offset
										: pc + 2
						));
					}
					return raw.apply((di, continuation) -> continuation.programCounter = (
							OperationHelper.lt(state, konstLeft, di.stack[c]) == aNotZero
									? pc + offset
									: pc + 2
					));
				}
				if (c > 0xff) {
					final LuaValue konstRight = k[c & 0x0ff];
					return raw.apply((di, continuation) -> continuation.programCounter = (
							OperationHelper.lt(state, di.stack[b], konstRight) == aNotZero
									? pc + offset
									: pc + 2
					));
				}
				return raw.apply((di, continuation) -> continuation.programCounter = (
						OperationHelper.lt(state, di.stack[b], di.stack[c]) == aNotZero
								? pc + offset
								: pc + 2
				));
			}

			case OP_LE: { // A B C: if ((RK(B) <= RK(C)) ~= A) then pc++
				final int b = (i >>> POS_B) & MAXARG_B;
				final int c = (i >> POS_C) & MAXARG_C;
				final boolean aNotZero = a != 0;
				final int offset = ((code[pc + 1] >> POS_Bx) & MAXARG_Bx) - MAXARG_sBx + 2;

				if (b > 0xff) {
					final LuaValue konstLeft = k[b & 0x0ff];
					if (c > 0xff) {
						final LuaValue konstRight = k[c & 0x0ff];
						return raw.apply((di, continuation) -> continuation.programCounter = (
								OperationHelper.le(state, konstLeft, konstRight) == aNotZero
										? pc + offset
										: pc + 2
						));
					}
					return raw.apply((di, continuation) -> continuation.programCounter = (
							OperationHelper.le(state, konstLeft, di.stack[c]) == aNotZero
									? pc + offset
									: pc + 2
					));
				}
				if (c > 0xff) {
					final LuaValue konstRight = k[c & 0x0ff];
					return raw.apply((di, continuation) -> continuation.programCounter = (
							OperationHelper.le(state, di.stack[b], konstRight) == aNotZero
									? pc + offset
									: pc + 2
					));
				}
				return raw.apply((di, continuation) -> continuation.programCounter = (
						OperationHelper.le(state, di.stack[b], di.stack[c]) == aNotZero
								? pc + offset
								: pc + 2
				));
			}

			case OP_TEST: { // A C: if not (R(A) <=> C) then pc++
				final boolean c = (i >> POS_C & MAXARG_C) != 0;
				final int offset = ((code[pc + 1] >> POS_Bx) & MAXARG_Bx) - MAXARG_sBx + 2;

				return raw.apply((di, continuation) ->
						continuation.programCounter = di.stack[a].toBoolean() == c ? pc + offset : pc + 2
				);
			}

			case OP_TESTSET: { // A B C: if (R(B) <=> C) then R(A):= R(B) else pc++
				/* note: doc appears to be reversed */
				final int b = (i >>> POS_B) & MAXARG_B;
				final int c = (i >> POS_C) & MAXARG_C;
				final int offset = ((code[pc + 1] >> POS_Bx) & MAXARG_Bx) - MAXARG_sBx + 2;
				boolean nonZeroC = c != 0;

				return raw.apply((di, continuation) -> {
					LuaValue val = di.stack[b];
					if (val.toBoolean() == nonZeroC) {
						di.stack[a] = val;
						continuation.programCounter = pc + offset;
					} else {
						continuation.programCounter = pc + 2;
					}
				});
			}

			case OP_CALL: { // A B C: R(A), ... ,R(A+C-2):= R(A)(R(A+1), ... ,R(A+B-1)) */
				final int b = (i >>> POS_B) & MAXARG_B;
				final int c = (i >> POS_C) & MAXARG_C;
				final int bc = i & (MASK_B | MASK_C);

				return raw.apply((di, continuation) -> {
					LuaValue val = di.stack[a];

					if (val instanceof LuaInterpretedFunction) {
						final DebugFrame debugFrame;
						final LuaInterpretedFunction fn = (LuaInterpretedFunction) val;
						switch (b) {
							case 1:
								debugFrame = setupCall(state, fn, 0);
								break;
							case 2:
								debugFrame = setupCall(state, fn, di.stack[a + 1], 0);
								break;
							case 3:
								debugFrame = setupCall(state, fn, di.stack[a + 1], di.stack[a + 2], 0);
								break;
							case 4:
								debugFrame = setupCall(state, fn, di.stack[a + 1], di.stack[a + 2], di.stack[a + 3], 0);
								break;
							default:
								debugFrame = b > 0
										? setupCall(state, fn, di.stack, a + 1, b - 1, NONE, 0) // exact arg count
										: setupCall(state, fn, di.stack, a + 1, di.top - di.extras.count() - (a + 1), di.extras, 0); // from prev top
						}

						continuation.debugFrame = debugFrame;
						continuation.function = fn;
						return;
					}

					switch (bc) {
						case (1 << POS_B) | (0 << POS_C): {
							Varargs v = di.extras = OperationHelper.invoke(state, val, NONE, a);
							di.top = a + v.count();
							break;
						}
						case (2 << POS_B) | (0 << POS_C): {
							Varargs v = di.extras = OperationHelper.invoke(state, val, di.stack[a + 1], a);
							di.top = a + v.count();
							break;
						}
						case (1 << POS_B) | (1 << POS_C):
							OperationHelper.call(state, val, a);
							break;
						case (2 << POS_B) | (1 << POS_C):
							OperationHelper.call(state, val, di.stack[a + 1], a);
							break;
						case (3 << POS_B) | (1 << POS_C):
							OperationHelper.call(state, val, di.stack[a + 1], di.stack[a + 2], a);
							break;
						case (4 << POS_B) | (1 << POS_C):
							OperationHelper.call(state, val, di.stack[a + 1], di.stack[a + 2], di.stack[a + 3], a);
							break;
						case (1 << POS_B) | (2 << POS_C):
							di.stack[a] = OperationHelper.call(state, val, a);
							break;
						case (2 << POS_B) | (2 << POS_C):
							di.stack[a] = OperationHelper.call(state, val, di.stack[a + 1], a);
							break;
						case (3 << POS_B) | (2 << POS_C):
							di.stack[a] = OperationHelper.call(state, val, di.stack[a + 1], di.stack[a + 2], a);
							break;
						case (4 << POS_B) | (2 << POS_C):
							di.stack[a] = OperationHelper.call(state, val, di.stack[a + 1], di.stack[a + 2], di.stack[a + 3], a);
							break;
						default: {
							Varargs args = b > 0 ?
									ValueFactory.varargsOf(di.stack, a + 1, b - 1) : // exact arg count
									ValueFactory.varargsOf(di.stack, a + 1, di.top - di.extras.count() - (a + 1), di.extras); // from prev top
							Varargs v = OperationHelper.invoke(state, val, args.asImmutable(), a);
							if (c > 0) {
								int cnt = c;
								while (--cnt > 0) di.stack[a + cnt - 1] = v.arg(cnt);
								v = NONE;
							} else {
								di.top = a + v.count();
								di.extras = v;
							}
							break;
						}
					}

					// a native call is over, continue executing this function
					continuation.programCounter = pc + 1;
				});
			}

			case OP_TAILCALL: { // A B C: return R(A)(R(A+1), ... ,R(A+B-1))
				final int b = (i >>> POS_B) & MAXARG_B;

				return raw.apply((di, continuation) -> {
					LuaValue val = di.stack[a];
					Varargs args;
					switch (b) {
						case 1:
							args = NONE;
							break;
						case 2:
							args = di.stack[a + 1];
							break;
						default: {
							Varargs v = di.extras;
							args = b > 0 ?
									ValueFactory.varargsOf(di.stack, a + 1, b - 1) : // exact arg count
									ValueFactory.varargsOf(di.stack, a + 1, di.top - v.count() - (a + 1), v); // from prev top
						}
					}

					final LuaFunction functionVal;
					if (val.isFunction()) {
						functionVal = (LuaFunction) val;
					} else {
						LuaValue meta = val.metatag(state, Constants.CALL);
						if (!meta.isFunction()) throw ErrorFactory.operandError(state, val, "call", a);

						functionVal = (LuaFunction) meta;
						args = ValueFactory.varargsOf(val, args);
					}

					if (functionVal instanceof LuaInterpretedFunction) {
						int flags = di.flags;
						closeAll(di.stackUpvalues);
						DebugHandler.getDebugState(state).popInfo();

						// Replace the current frame with a new one.
						final LuaInterpretedFunction fn = (LuaInterpretedFunction) functionVal;
						continuation.debugFrame = setupCall(state, fn, args, (flags & FLAG_FRESH) | FLAG_TAIL);
						continuation.function = fn;
					} else {
						Varargs v = functionVal.invoke(state, args.asImmutable());
						di.top = a + v.count();
						di.extras = v;
						continuation.programCounter = pc + 1;
					}
				});
			}

			case OP_RETURN: { // A B: return R(A), ... ,R(A+B-2) (see note)
				final int b = (i >>> POS_B) & MAXARG_B;
				return raw.apply((di, continuation) -> {
					final int flags = di.flags, top = di.top;
					final boolean fresh = (flags & FLAG_FRESH) != 0;
					final Varargs v = di.extras;

					final LuaValue[] stack = di.stack;
					closeAll(di.stackUpvalues);
					final DebugState ds = DebugHandler.getDebugState(state);
					handler.onReturn(ds, di);

					Varargs ret;
					// TODO lift conditional out of λ
					switch (b) {
						case 0:
							ret = ValueFactory.varargsOf(stack, a, top - v.count() - a, v).asImmutable();
							break;
						case 1:
							ret = NONE;
							break;
						case 2:
							ret = stack[a];
							break;
						default:
							ret = ValueFactory.varargsOf(stack, a, b - 1).asImmutable();
							break;
					}

					if (fresh) {
						// If we're a fresh invocation then return to the parent.
						continuation.varargs = ret;
					} else {
						DebugFrame debugFrame = ds.getStackUnsafe();
						LuaInterpretedFunction fn = (LuaInterpretedFunction) debugFrame.closure;
						resume(state, debugFrame, fn, ret);
						continuation.debugFrame = debugFrame;
						continuation.function = fn;
					}
				});
			}

			case OP_FORLOOP: { // A sBx: R(A)+=R(A+2): if R(A) <?= R(A+1) then { pc+=sBx: R(A+3)=R(A) }
				final int offset = ((i >>> POS_Bx) & MAXARG_Bx) - MAXARG_sBx + 1;

				return raw.apply((di, continuation) -> {
					double limit = di.stack[a + 1].checkDouble();
					double step = di.stack[a + 2].checkDouble();
					double value = di.stack[a].checkDouble();
					double idx = step + value;
					if (0 < step ? idx <= limit : limit <= idx) {
						di.stack[a + 3] = di.stack[a] = valueOf(idx);
						continuation.programCounter = pc + offset;
					} else {
						continuation.programCounter = pc + 1;
					}
				});
			}

			case OP_FORPREP: { // A sBx: R(A)-=R(A+2): pc+=sBx
				final int offset = ((i >>> POS_Bx) & MAXARG_Bx) - MAXARG_sBx + 1;
				return raw.apply((di, continuation) -> {
					LuaNumber init = di.stack[a].checkNumber("'for' initial value must be a number");
					LuaNumber limit = di.stack[a + 1].checkNumber("'for' limit must be a number");
					LuaNumber step = di.stack[a + 2].checkNumber("'for' step must be a number");
					di.stack[a] = valueOf(init.toDouble() - step.toDouble());
					di.stack[a + 1] = limit;
					di.stack[a + 2] = step;
					continuation.programCounter = pc + offset;
				});
			}

			case OP_TFORLOOP: {
							/*
								A C R(A+3), ... ,R(A+2+C):= R(A)(R(A+1),
								R(A+2)): if R(A+3) ~= nil then R(A+2)=R(A+3)
								else pc++
							*/
				final int initC = (i >> POS_C) & MAXARG_C;
				return raw.apply((di, continuation) -> {
					Varargs v = di.extras = OperationHelper.invoke(state, di.stack[a], ValueFactory.varargsOf(di.stack[a + 1], di.stack[a + 2]), a);
					LuaValue val = v.first();
					if (val.isNil()) {
						continuation.programCounter = pc + 2;
						return;
					}

					di.stack[a + 2] = di.stack[a + 3] = val;
					for (int c = initC; c > 1; --c) {
						di.stack[a + 2 + c] = v.arg(c);
					}
					di.extras = NONE;
					continuation.programCounter = pc + 1;
				});
			}

			case OP_SETLIST: { // A B C: R(A)[(C-1)*FPF+i]:= R(A+i), 1 <= i <= B
				final int _b = (i >>> POS_B) & MAXARG_B;
				final int _c = (i >> POS_C) & MAXARG_C;
				final int c;
				if (_c == 0) c = code[pc + 1]; else c = _c;
				final int offset = (c - 1) * LFIELDS_PER_FLUSH;

				return raw.apply((di, continuation) -> {
					// TODO lift conditional out of λ
					int newPc = pc;
					if (_c == 0) newPc++;
					LuaTable tbl = di.stack[a].checkTable();
					// TODO lift conditional out of λ
					if (_b == 0) {
						int b = di.top - a - 1;
						int m = b - di.extras.count();
						int j = 1;
						for (; j <= m; j++) {
							tbl.rawset(offset + j, di.stack[a + j]);
						}
						for (; j <= b; j++) {
							tbl.rawset(offset + j, di.extras.arg(j - m));
						}
					} else {
						tbl.presize(offset + _b);
						for (int j = 1; j <= _b; j++) {
							tbl.rawset(offset + j, di.stack[a + j]);
						}
					}
					continuation.programCounter = newPc + 1;
				});
			}

			case OP_CLOSE: // A : close all variables in the stack up to (>=) R(A)
				return cont.apply(di -> {
					final Upvalue[] openups = di.stackUpvalues;
					for (int x = openups.length; --x >= a; ) {
						Upvalue upvalue = openups[x];
						if (upvalue != null) {
							upvalue.close();
							openups[x] = null;
						}
					}
				});

			case OP_CLOSURE: { // A Bx: R(A):= closure(KPROTO[Bx], R(A), ... ,R(A+n))
				Prototype newp = p.p[(i >>> POS_Bx) & MAXARG_Bx];
				return raw.apply((di, continuation) -> {
					final Upvalue[] openups = di.stackUpvalues;
					final LuaInterpretedFunction closure = (LuaInterpretedFunction) di.closure;
					LuaInterpretedFunction newcl = new LuaInterpretedFunction(newp, closure.env);

					for (int j = 0, nup = newp.nups; j < nup; ++j) {
						int instr = code[pc + j + 1];
						int b = (instr >>> POS_B) & MAXARG_B;
						newcl.upvalues[j] = (instr & 4) != 0
								? closure.upvalues[b] // OP_GETUPVAL
								: openups[b] != null ? openups[b] : (openups[b] = new Upvalue(di.stack, b)); // OP_MOVE
					}
					di.stack[a] = newcl;
					continuation.programCounter = pc + newp.nups + 1;
				});
			}

			case OP_VARARG: { // A B: R(A), R(A+1), ..., R(A+B-1) = vararg
				final int b = (i >>> POS_B) & MAXARG_B;
				if (b == 0) {
					return cont.apply(di -> {
						di.top = a + di.varargs.count();
						di.extras = di.varargs;
					});
				} else {
					return cont.apply(di -> {
						for (int j = 1; j < b; ++j) {
							di.stack[a + j - 1] = di.varargs.arg(j);
						}
					});
				}
			}
		}

		throw new IllegalStateException();
	}

	private static void concat(LuaState state, DebugFrame frame, LuaValue[] stack, int top, int total) throws LuaError, UnwindThrowable {
		try {
			do {
				LuaValue left = stack[top - 2];
				LuaValue right = stack[top - 1];

				LuaString lString, rString;

				int n = 2;

				if (!left.isString() || !right.isString()) {
					// If one of these isn't convertible to a string then use the metamethod
					stack[top - 2] = OperationHelper.concatNonStrings(state, left, right, top - 2, top - 1);
				} else if ((rString = right.checkLuaString()).length == 0) {
					stack[top - 2] = left.checkLuaString();
				} else if ((lString = left.checkLuaString()).length == 0) {
					stack[top - 2] = rString;
				} else {
					int length = rString.length + lString.length;
					stack[top - 2] = lString;
					stack[top - 1] = rString;

					for (; n < total; n++) {
						LuaValue value = stack[top - n - 1];
						if (!value.isString()) break;

						LuaString string = value.checkLuaString();

						// Ensure we don't get a string which is too long
						if (string.length > Integer.MAX_VALUE - length) throw new LuaError("string length overflow");

						// Otherwise increment the length and store this converted string
						stack[top - n - 1] = string;
						length += string.length;
					}

					byte[] buffer = new byte[length];
					length = 0;
					for (int j = n; j > 0; j--) {
						LuaString string = (LuaString) stack[top - j];
						System.arraycopy(string.bytes, string.offset, buffer, length, string.length);
						length += string.length;
					}

					stack[top - n] = LuaString.valueOf(buffer);

				}

				// Got "n" strings and created one new one
				total -= n - 1;
				top -= n - 1;
			} while (total > 1);
		} catch (UnwindThrowable e) {
			frame.top = top;
			throw e;
		}
	}

	public static void closeAll(Upvalue[] upvalues) {
		if (upvalues == null) return;
		for (Upvalue upvalue : upvalues) if (upvalue != null) upvalue.close();
	}

	public static void resume(LuaState state, DebugFrame di, LuaInterpretedFunction function, Varargs varargs) throws LuaError, UnwindThrowable {
		Prototype p = function.p;
		final int i = p.code[di.pc++];
		final int opcode = (i >> POS_OP) & MAX_OP;

		switch (opcode) {
			case OP_ADD: case OP_SUB: case OP_MUL: case OP_DIV: case OP_MOD: case OP_POW: case OP_UNM:
			case OP_GETTABLE: case OP_GETGLOBAL: case OP_SELF: {
				di.stack[(i >> POS_A) & MAXARG_A] = varargs.first();
				break;
			}

			case OP_LE: case OP_LT: case OP_EQ: {
				boolean res = varargs.first().toBoolean();

				// If we should negate this result (due to using lt rather than le)
				if ((di.flags & FLAG_LEQ) != 0) {
					res = !res;
					di.flags ^= FLAG_LEQ;
				}

				if (res == (((i >> POS_A) & MAXARG_A) != 0)) {
					// We assume the next instruction is a jump and read the branch from there.
					di.pc += ((p.code[di.pc] >> POS_Bx) & MAXARG_Bx) - MAXARG_sBx;
				}
				di.pc++;
				break;
			}

			case OP_CALL: case OP_TAILCALL: {
				int a = (i >>> POS_A) & MAXARG_A;
				int c = (i >>> POS_C) & MAXARG_C;
				if (c > 0) {
					LuaValue[] stack = di.stack;
					while (--c > 0) stack[a + c - 1] = varargs.arg(c);
					di.extras = NONE;
				} else {
					di.extras = varargs;
					di.top = a + varargs.count();
				}
				break;
			}

			case OP_SETTABLE: case OP_SETGLOBAL:
				// Nothing to be done here
				break;

			case OP_TFORLOOP: {
				LuaValue o = varargs.first();
				if (o.isNil()) {
					di.pc++;
				} else {
					int a = (i >>> POS_A) & MAXARG_A;
					LuaValue[] stack = di.stack;
					stack[a + 2] = stack[a + 3] = o;
					for (int c = (i >>> POS_C) & MAXARG_C; c > 1; --c) {
						stack[a + 2 + c] = varargs.arg(c);
					}
					di.extras = Constants.NONE;
				}
				break;
			}
			case OP_CONCAT: {
				int a = (i >>> POS_A) & MAXARG_A;
				int b = (i >>> POS_B) & MAXARG_B;

				LuaValue[] stack = di.stack;
				int top = di.top - 1;

				stack[top - 1] = varargs.first();
				int total = top - b;
				if (total > 1) {
					// Rewind time, we may end up executing this instruction multiple times.
					di.pc--;
					concat(state, di, stack, top, total);
					di.pc++;
				}
				stack[a] = stack[b];
				di.top = top;

				break;
			}

			default:
				throw new IllegalStateException("Resuming from unknown instruction (" + Print.OPNAMES[opcode] + ")");
		}
	}

	public static Varargs resumeReturn(LuaState state, DebugState ds, DebugFrame di, LuaInterpretedFunction function) throws LuaError, UnwindThrowable {
		int i = function.p.code[di.pc];
		DebugHandler handler = state.debug;

		switch (((i >> POS_OP) & MAX_OP)) {
			case OP_RETURN: {
				int a = (i >>> POS_B) & MAXARG_B;
				int b = (i >>> POS_B) & MAXARG_B;

				Varargs ret;
				switch (b) {
					case 0:
						ret = ValueFactory.varargsOf(di.stack, a, di.top - di.extras.count() - a, di.extras).asImmutable();
						break;
					case 1:
						ret = NONE;
						break;
					case 2:
						ret = di.stack[a];
						break;
					default:
						ret = ValueFactory.varargsOf(di.stack, a, b - 1).asImmutable();
						break;
				}

				int flags = di.flags;
				handler.onReturnError(ds);

				if ((flags & FLAG_FRESH) != 0) {
					// If we're a fresh invocation then return to the parent.
					return ret;
				} else {
					di = ds.getStackUnsafe();
					function = (LuaInterpretedFunction) di.closure;
					resume(state, di, function, ret);
					return execute(state, di, function);
				}
			}

			default:
				Print.printCode(function.p);
				throw new IllegalStateException("Cannot resume on this opcode (pc=" + di.pc + ")");
		}
	}
}

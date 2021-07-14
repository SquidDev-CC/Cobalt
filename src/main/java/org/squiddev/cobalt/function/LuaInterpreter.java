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

import static org.squiddev.cobalt.Constants.*;
import static org.squiddev.cobalt.Lua52.*;
import static org.squiddev.cobalt.LuaDouble.valueOf;
import static org.squiddev.cobalt.debug.DebugFrame.*;

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
			if ((flags & FLAG_TAIL) != 0) ds.hookTailCall(di);
			else ds.hookCall(di);
		}

		di.top = 0;
		return di;
	}

	private static int jumpStatement(Prototype p, Upvalue[] openups, int pc, int a, int b) {
		if (p.isLua52 && a > 0) {
			for (int x = openups.length; --x >= a - 1;) {
				Upvalue upvalue = openups[x];
				if (upvalue != null) {
					upvalue.close();
					openups[x] = null;
				}
			}
		}
		return pc + b;
	}

	static Varargs execute(final LuaState state, DebugFrame di, LuaInterpretedFunction function) throws LuaError, UnwindThrowable {
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

				int op = ((i >> POS_OP) & MAX_OP);

				if (!p.isLua52) op = lua51opcodes[op];

				// process the instruction
				switch (op) {
					case OP_MOVE: // A B: R(A):= R(B)
						stack[a] = stack[(i >>> POS_B) & MAXARG_B];
						break;

					case OP_LOADK: // A Bx: R(A):= Kst(Bx)
						stack[a] = k[(i >>> POS_Bx) & MAXARG_Bx];
						break;

					case OP_LOADKX: // A: R(A) := Kst(extra arg)
						// assert(((code[pc] >> POS_OP) & MAX_OP) == OP_EXTRAARG);
						stack[a] = k[GETARG_Ax(code[pc++])];
						break;

					case OP_LOADBOOL: // A B C: R(A):= (Bool)B: if (C) pc++
						stack[a] = ((i >>> POS_B) & MAXARG_B) != 0 ? TRUE : FALSE;
						if (((i >>> POS_C) & MAXARG_C) != 0) pc++; // skip next instruction (if C)
						break;

					case OP_LOADNIL: { // A B: R(A):= ...:= R([A+]B):= nil
						int b = ((i >>> POS_B) & MAXARG_B);
						if (p.isLua52) {
							while (b >= 0) {
								stack[a+(b--)] = NIL;
							}
						} else {
							do {
								stack[b--] = NIL;
							} while (b >= a);
						}
						break;
					}

					case OP_GETUPVAL: // A B: R(A):= UpValue[B]
						stack[a] = upvalues[((i >>> POS_B) & MAXARG_B) + (p.isLua52 ? 0 : 1)].getValue();
						break;

					case OP_GETTABUP: { // A B C: R(A) := UpValue[B][RK(C)]
						int b = (i >>> POS_B) & MAXARG_B;
						int c = (i >>> POS_C) & MAXARG_C;
						stack[a] = OperationHelper.getTable(state, upvalues[b].getValue(), c > 0xff ? k[c & 0x0ff] : stack[c], b);
						break;
					}

					case OP_GETGLOBAL: // A Bx	R(A):= Gbl[Kst(Bx)]
						stack[a] = OperationHelper.getTable(state, upvalues[0].getValue(), k[(i >>> POS_Bx) & MAXARG_Bx]);
						break;

					case OP_GETTABLE: { // A B C: R(A):= R(B)[RK(C)]
						int b = (i >>> POS_B) & MAXARG_B;
						int c = (i >>> POS_C) & MAXARG_C;
						stack[a] = OperationHelper.getTable(state, stack[b], c > 0xff ? k[c & 0x0ff] : stack[c], b);
						break;
					}

					case OP_SETGLOBAL: // A Bx: Gbl[Kst(Bx)]:= R(A)
						OperationHelper.setTable(state, upvalues[0].getValue(), k[(i >>> POS_Bx) & MAXARG_Bx], stack[a]);
						break;

					case OP_SETTABUP: { // A B C: UpValue[A][RK(B)] := RK(C)
						int b = (i >>> POS_B) & MAXARG_B;
						int c = (i >>> POS_C) & MAXARG_C;
						OperationHelper.setTable(state, upvalues[a].getValue(), b > 0xff ? k[b & 0x0ff] : stack[b], c > 0xff ? k[c & 0x0ff] : stack[c], a);
						break;
					}

					case OP_SETUPVAL: // A B: UpValue[B]:= R(A)
						upvalues[((i >>> POS_B) & MAXARG_B) + (p.isLua52 ? 0 : 1)].setValue(stack[a]);
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

					case OP_JMP: // A sBx: pc+=sBx; if (A) close all upvalues >= R(A - 1)
						pc = jumpStatement(p, openups, pc, a, GETARG_sBx(i));
						break;

					case OP_EQ: { // A B C: if ((RK(B) == RK(C)) ~= A) then pc++
						int b = (i >>> POS_B) & MAXARG_B;
						int c = (i >> POS_C) & MAXARG_C;
						if (OperationHelper.eq(state, b > 0xff ? k[b & 0x0ff] : stack[b], c > 0xff ? k[c & 0x0ff] : stack[c]) == (a != 0)) {
							// We assume the next instruction is a jump and read the branch from there.
							pc = jumpStatement(p, openups, pc, GETARG_A(code[pc]), GETARG_sBx(code[pc]));
						}
						pc++;
						break;
					}

					case OP_LT: { // A B C: if ((RK(B) <  RK(C)) ~= A) then pc++
						int b = (i >>> POS_B) & MAXARG_B;
						int c = (i >> POS_C) & MAXARG_C;
						if (OperationHelper.lt(state, b > 0xff ? k[b & 0x0ff] : stack[b], c > 0xff ? k[c & 0x0ff] : stack[c]) == (a != 0)) {
							pc = jumpStatement(p, openups, pc, GETARG_A(code[pc]), GETARG_sBx(code[pc]));
						}
						pc++;
						break;
					}

					case OP_LE: { // A B C: if ((RK(B) <= RK(C)) ~= A) then pc++
						int b = (i >>> POS_B) & MAXARG_B;
						int c = (i >> POS_C) & MAXARG_C;
						if (OperationHelper.le(state, b > 0xff ? k[b & 0x0ff] : stack[b], c > 0xff ? k[c & 0x0ff] : stack[c]) == (a != 0)) {
							pc = jumpStatement(p, openups, pc, GETARG_A(code[pc]), GETARG_sBx(code[pc]));
						}
						pc++;
						break;
					}

					case OP_TEST: // A C: if not (R(A) <=> C) then pc++
						if (stack[a].toBoolean() == (((i >> POS_C) & MAXARG_C) != 0)) {
							pc = jumpStatement(p, openups, pc, GETARG_A(code[pc]), GETARG_sBx(code[pc]));
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
							pc = jumpStatement(p, openups, pc, GETARG_A(code[pc]), GETARG_sBx(code[pc]));
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

					case OP_TFORCALL: { // A C: R(A+3), ..., R(A+2+C) := R(A)(R(A+1), R(A+2));
						di.extras = OperationHelper.invoke(state, stack[a], ValueFactory.varargsOf(stack[a + 1], stack[a + 2]), a);
						for (int c = (i >> POS_C) & MAXARG_C; c > 1; --c) {
							stack[a + 2 + c] = di.extras.arg(c);
						}
						i = code[pc++];
						a = ((i >> POS_A) & MAXARG_A);
					}

					case OP_TFORLOOP: {
							/*
								A C R(A+3), ... ,R(A+2+C):= R(A)(R(A+1),
								R(A+2)): if R(A+3) ~= nil then R(A+2)=R(A+3)
								else pc++
							*/
						if (p.isLua52) {
							Varargs v = di.extras;
							LuaValue val = v.first();
							if (!val.isNil()) {
								stack[a] = stack[a + 1] = val;
								di.extras = NONE;
								pc += ((i >>> POS_Bx) & MAXARG_Bx) - MAXARG_sBx;
							}
						} else {
							di.extras = OperationHelper.invoke(state, stack[a], ValueFactory.varargsOf(stack[a + 1], stack[a + 2]), a);
							Varargs v = di.extras;
							LuaValue val = v.first();
							if (val.isNil()) {
								pc++;
							} else {
								for (int c = (i >> POS_C) & MAXARG_C; c > 1; --c) {
									stack[a + 2 + c] = v.arg(c);
								}
								if (p.isLua52) a = ((i >> POS_A) & MAXARG_A);
								stack[a + 2] = stack[a + 3] = val;
								di.extras = NONE;
							}
						}
						break;
					}

					case OP_SETLIST: { // A B C: R(A)[(C-1)*FPF+i]:= R(A+i), 1 <= i <= B
						int b = (i >>> POS_B) & MAXARG_B;
						int c = (i >> POS_C) & MAXARG_C;
						if (c == 0) c = p.isLua52 ? GETARG_Ax(code[pc++]) : code[pc++];

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
						LuaInterpretedFunction newcl = new LuaInterpretedFunction(newp, newp.isLua52 ? null : (LuaTable)upvalues[0].getValue());
						if (newp.isLua52) {
							for (int j = 0; j < newp.nups; ++j) {
								int b = newp.upvalue_info[j];
								if ((b >> 8) != 0) {
									newcl.upvalues[j] = openups[b & 0xFF] != null ? openups[b & 0xFF] : (openups[b & 0xFF] = new Upvalue(stack, b & 0xFF));
								} else {
									newcl.upvalues[j] = upvalues[b & 0xFF];
								}
							}
						} else {
							for (int j = 1, nup = newp.nups; j <= nup; ++j) {
								i = code[pc++];
								int b = (i >>> POS_B) & MAXARG_B;
								newcl.upvalues[j] = (i & 4) != 0
									? upvalues[b+1] // OP_GETUPVAL
									: openups[b] != null ? openups[b] : (openups[b] = new Upvalue(stack, b)); // OP_MOVE
							}
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
						break;
					}

					case OP_EXTRAARG:
						// throw exception?
				}
			}
		}
	}

	private static void concat(LuaState state, DebugFrame frame, LuaValue[] stack, int top, int total) throws LuaError, UnwindThrowable {
		try {
			do {
				LuaValue left = stack[top - 2];
				LuaValue right = stack[top - 1];

				LuaBaseString lString, rString;

				int n = 2;

				if (!left.isString() || !right.isString()) {
					// If one of these isn't convertible to a string then use the metamethod
					stack[top - 2] = OperationHelper.concatNonStrings(state, left, right, top - 2, top - 1);
				} else if ((rString = right.checkLuaBaseString()).length() == 0) {
					stack[top - 2] = left.checkLuaString();
				} else if ((lString = left.checkLuaBaseString()).length() == 0) {
					stack[top - 2] = rString;
				} else {
					int length = rString.length() + lString.length();
					stack[top - 2] = lString;
					stack[top - 1] = rString;

					for (; n < total; n++) {
						LuaValue value = stack[top - n - 1];
						if (!value.isString()) break;

						LuaBaseString string = value.checkLuaBaseString();

						// Ensure we don't get a string which is too long
						int strLen = string.length();
						if (strLen > Integer.MAX_VALUE - length) throw new LuaError("string length overflow");

						// Otherwise increment the length and store this converted string
						stack[top - n - 1] = string;
						length += strLen;
					}

					stack[top - n] = LuaRope.valueOf(stack, top - n, n, length);
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
		int i = p.code[di.pc++];
		int op = ((i >> POS_OP) & MAX_OP);
		if (!p.isLua52) op = lua51opcodes[op];

		switch (op) {
			case OP_ADD: case OP_SUB: case OP_MUL: case OP_DIV: case OP_MOD: case OP_POW: case OP_UNM:
			case OP_GETTABLE: case OP_GETTABUP: case OP_GETGLOBAL: case OP_SELF: {
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

			case OP_TFORCALL: {
				int a = (i >>> POS_A) & MAXARG_A;
				di.extras = varargs;
				for (int c = (i >> POS_C) & MAXARG_C; c > 1; --c) {
					di.stack[a + 2 + c] = varargs.arg(c);
				}
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

			case OP_SETTABLE: case OP_SETGLOBAL: case OP_SETTABUP:
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
				throw new IllegalStateException("Resuming from unknown instruction");
		}
	}

	public static Varargs resumeReturn(LuaState state, DebugState ds, DebugFrame di, LuaInterpretedFunction function) throws LuaError, UnwindThrowable {
		int i = function.p.code[di.pc];
		DebugHandler handler = state.debug;

		switch (((i >> POS_OP) & MAX_OP)) {
			case OP_RETURN: {
				int a = (i >>> POS_A) & MAXARG_A;
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
				Print.printCode(System.out, function.p);
				throw new IllegalStateException("Cannot resume on this opcode (pc=" + di.pc + ")");
		}
	}
}

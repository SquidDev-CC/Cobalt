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

import org.squiddev.cobalt.*;
import org.squiddev.cobalt.compiler.LuaC;
import org.squiddev.cobalt.lib.DebugLib;

import static org.squiddev.cobalt.Lua52.*;
import static org.squiddev.cobalt.ValueFactory.valueOf;
import static org.squiddev.cobalt.debug.DebugFrame.FLAG_HOOKED;
import static org.squiddev.cobalt.debug.DebugFrame.FLAG_TAIL;

/**
 * Helper methods for the debug library
 */
public final class DebugHelpers52 {
	private static final LuaString GLOBAL = valueOf("global");
	private static final LuaString LOCAL = valueOf("local");
	private static final LuaString METHOD = valueOf("method");
	private static final LuaString UPVALUE = valueOf("upvalue");
	private static final LuaString FIELD = valueOf("field");
	private static final LuaString QUESTION = valueOf("?");
	private static final LuaString HOOK = valueOf("hook");
	private static final LuaString METAMETHOD = valueOf("metamethod");
	private static final LuaString CONSTANT = valueOf("constant");

	/**
	 * Size of the first part of the stack
	 */
	private static final int LEVELS1 = 10;

	/**
	 * Size of the second part of the stack
	 */
	private static final int LEVELS2 = 11;

	private DebugHelpers52() {
	}

	private static LuaString[] fromMetamethod(String name) {
		return new LuaString[]{valueOf("__" + name), METAMETHOD};
	}

	static LuaString[] getFuncName(DebugFrame di, int stackpos) {
		if (di.closure == null) return null;
		if ((di.flags & FLAG_HOOKED) != 0) return new LuaString[]{QUESTION, HOOK};

		Prototype p = di.closure.getPrototype();
		int pc = di.pc; // currentpc(L, ci);
		int i = p.code[pc];
		switch (GET_OPCODE(i)) {
			case OP_CALL: case OP_TAILCALL: return getObjectName(di, GETARG_A(i));
			case OP_SELF: case OP_GETTABLE: return fromMetamethod("index");
			case OP_SETTABLE: return fromMetamethod("newindex");
			case OP_ADD: return fromMetamethod("add");
			case OP_SUB: return fromMetamethod("sub");
			case OP_MUL: return fromMetamethod("mul");
			case OP_DIV: return fromMetamethod("div");
			case OP_POW: return fromMetamethod("pow");
			case OP_MOD: return fromMetamethod("mod");
			case OP_UNM: return fromMetamethod("unm");
			case OP_EQ: return fromMetamethod("eq");
			case OP_LE: return fromMetamethod("le");
			case OP_LT: return fromMetamethod("lt");
			case OP_LEN: return fromMetamethod("len");
			case OP_CONCAT: return fromMetamethod("concat");
			default: return null;
		}
	}

	// return StrValue[] { name, namewhat } if found, null if not
	public static LuaString[] getObjectName(DebugFrame di, int stackpos) {
		if (di.closure == null) return null;
		if ((di.flags & FLAG_HOOKED) != 0) return new LuaString[]{QUESTION, HOOK};

		Prototype p = di.closure.getPrototype();
		int pc = di.pc; // currentpc(L, ci);
		LuaString name = p.getlocalname(stackpos + 1, pc);

		// is a local?
		if (name != null) return new LuaString[]{name, LOCAL};

		pc = findsetreg(p, di.pc, stackpos);
		if (pc != -1) {
			int i = p.code[pc];
			int op = GET_OPCODE(i);
			switch (op) {
				case OP_MOVE: {
					int a = GETARG_A(i);
					int b = GETARG_B(i); /* move from `b' to `a' */
					if (b < a) return getObjectName(di, b); /* get name for `b' */
					break;
				}
				case OP_GETTABUP:
				case OP_GETTABLE: {
					int k = GETARG_C(i); /* key index */
					int t = GETARG_B(i); /* table index */
					LuaString vn = op == OP_GETTABLE ? constantName(p, k) : p.upvalues[t];
					return new LuaString[]{constantName(p, k), vn.equals(LuaString.valueOf("_ENV")) ? GLOBAL : FIELD};
				}
				case OP_GETUPVAL: {
					int u = GETARG_B(i); /* upvalue index */
					return new LuaString[]{u < p.upvalues.length ? p.upvalues[u] : DebugLib.QMARK, UPVALUE};
				}
				case OP_LOADK:
				case OP_LOADKX: {
					int b = op == OP_LOADK ? GETARG_Bx(i) : GETARG_Ax(i);
					if (p.k[b].isString()) return new LuaString[]{(LuaString)p.k[b], CONSTANT};
					break;
				}
				case OP_SELF: {
					int k = GETARG_C(i); /* key index */
					return new LuaString[]{constantName(p, k), METHOD};
				}
			}
		}

		return null; // no useful name found
	}

	private static int filterpc(int pc, int jmptarget) {
		if (pc < jmptarget) return -1;
		else return pc;
	}

	// return last instruction, or 0 if error
	private static int findsetreg(Prototype p, int lastpc, int reg) {
		int setreg = -1;
		int jmptarget = 0;
		for (int pc = 0; pc < lastpc; pc++) {
			int i = p.code[pc];
			int op = GET_OPCODE(i);
			int a = GETARG_A(i);
			switch (op) {
				case OP_LOADNIL: {
					int b = GETARG_B(i);
					if (a <= reg && reg <= a + b) {
						setreg = filterpc(pc, jmptarget);
					}
					break;
				}
				case OP_TFORCALL: {
					if (reg >= a + 2) {
						setreg = filterpc(pc, jmptarget);
					}
					break;
				}
				case OP_CALL:
				case OP_TAILCALL: {
					if (reg >= a) {
						setreg = filterpc(pc, jmptarget);
					}
					break;
				}
				case OP_JMP: {
					int b = GETARG_sBx(i);
					int dest = pc + 1 + b;
					/* jump is forward and do not skip `lastpc'? */
					if (pc < dest && dest <= lastpc) {
						if (dest > jmptarget)
							jmptarget = dest;  /* update 'jmptarget' */
					}
					break;
				}
				case OP_TEST: {
					if (reg == a) {
						setreg = filterpc(pc, jmptarget);
					}
					break;
				}
				default: {
					if (testAMode(op) && reg == a) {
						setreg = filterpc(pc, jmptarget);
					}
					break;
				}
			}
		}
		return setreg;
	}

	private static boolean precheck(Prototype pt) {
		if (!(pt.maxstacksize <= LuaC.MAXSTACK)) return false;
		lua_assert(pt.numparams + (pt.is_vararg & VARARG_HASARG) <= pt.maxstacksize);
		lua_assert((pt.is_vararg & VARARG_NEEDSARG) == 0
			|| (pt.is_vararg & VARARG_HASARG) != 0);
		return pt.upvalues.length <= pt.nups && (pt.lineinfo.length == pt.code.length || pt.lineinfo.length == 0) && GET_OPCODE(pt.code[pt.code.length - 1]) == OP_RETURN;
	}

	private static boolean checkArgMode(Prototype pt, int val, int mode) {
		switch (mode) {
			case OpArgN:
				if (!(val == 0)) return false;
				break;
			case OpArgU:
				break;
			case OpArgR:
				checkRegister(pt, val);
				break;
			case OpArgK:
				if (!(ISK(val) ? INDEXK(val) < pt.k.length : val < pt.maxstacksize)) return false;
				break;
		}
		return true;
	}

	private static boolean checkRegister(Prototype proto, int reg) {
		return (reg < proto.maxstacksize);
	}

	private static boolean checkOpenUp(Prototype proto, int pc) {
		int i = proto.code[(pc) + 1];
		switch (GET_OPCODE(i)) {
			case OP_CALL:
			case OP_TAILCALL:
			case OP_RETURN:
			case OP_SETLIST: {
				return GETARG_B(i) == 0;
			}
			default:
				return false; /* invalid instruction after an open call */
		}
	}

	private static LuaString constantName(Prototype proto, int index) {
		if (ISK(index) && proto.k[INDEXK(index)].isString()) {
			return (LuaString) proto.k[INDEXK(index)].toLuaString();
		} else {
			return DebugLib.QMARK;
		}
	}

	private static void lua_assert(boolean x) {
		if (!x) throw new RuntimeException("lua_assert failed");
	}
}

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

import org.squiddev.cobalt.function.LuaClosure;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;

import static org.squiddev.cobalt.Lua52.*;

/**
 * Debug helper class to pretty-print lua bytecodes.
 *
 * @see Prototype
 * @see LuaClosure
 */
public class Print52 {
	public static final String[] OPNAMES = {
		"MOVE",
		"LOADK",
		"LOADKX",
		"LOADBOOL",
		"LOADNIL",
		"GETUPVAL",
		"GETTABUP",
		"GETTABLE",
		"SETTABUP",
		"SETUPVAL",
		"SETTABLE",
		"NEWTABLE",
		"SELF",
		"ADD",
		"SUB",
		"MUL",
		"DIV",
		"MOD",
		"POW",
		"UNM",
		"NOT",
		"LEN",
		"CONCAT",
		"JMP",
		"EQ",
		"LT",
		"LE",
		"TEST",
		"TESTSET",
		"CALL",
		"TAILCALL",
		"RETURN",
		"FORLOOP",
		"FORPREP",
		"TFORCALL",
		"TFORLOOP",
		"SETLIST",
		"CLOSURE",
		"VARARG",
		"EXTRAARG",
		null,
	};


	static void printString(PrintStream ps, final LuaString s) {

		ps.print('"');
		for (int i = 0, n = s.length; i < n; i++) {
			int c = s.bytes[s.offset + i];
			if (c >= ' ' && c <= '~' && c != '\"' && c != '\\') {
				ps.print((char) c);
			} else {
				switch (c) {
					case '"':
						ps.print("\\\"");
						break;
					case '\\':
						ps.print("\\\\");
						break;
					case 0x0007: /* bell */
						ps.print("\\a");
						break;
					case '\b': /* backspace */
						ps.print("\\b");
						break;
					case '\f':  /* form feed */
						ps.print("\\f");
						break;
					case '\t':  /* tab */
						ps.print("\\t");
						break;
					case '\r': /* carriage return */
						ps.print("\\r");
						break;
					case '\n': /* newline */
						ps.print("\\n");
						break;
					case 0x000B: /* vertical tab */
						ps.print("\\v");
						break;
					default:
						ps.print('\\');
						ps.print(Integer.toString(1000 + 0xff & c).substring(1));
						break;
				}
			}
		}
		ps.print('"');
	}

	private static void printValue(PrintStream ps, LuaValue v) {
		switch (v.type()) {
			case Constants.TSTRING:
				printString(ps, (LuaString) v);
				break;
			default:
				ps.print(v.toString());

		}
	}

	private static void printConstant(PrintStream ps, Prototype f, int i) {
		printValue(ps, f.k[i]);
	}

	/**
	 * Print an opcode in a prototype
	 *
	 * @param ps the {@link PrintStream} to print to
	 * @param f  the {@link Prototype}
	 * @param pc the program counter to look up and print
	 */
	public static void printOpcode(PrintStream ps, Prototype f, int pc) {
		int[] code = f.code;
		int i = code[pc];
		int o = GET_OPCODE(i);
		int a = GETARG_A(i);
		int b = GETARG_B(i);
		int c = GETARG_C(i);
		int bx = GETARG_Bx(i);
		int sbx = GETARG_sBx(i);
		int line = getline(f, pc);
		ps.print("  " + (pc + 1) + "  ");
		if (line > 0) {
			ps.print("[" + line + "]  ");
		} else {
			ps.print("[-]  ");
		}
		ps.print(OPNAMES[o] + "  ");
		switch (getOpMode(o)) {
			case iABC:
				ps.print(a);
				if (getBMode(o) != OpArgN) {
					ps.print(" " + (ISK(b) ? (-1 - INDEXK(b)) : b));
				}
				if (getCMode(o) != OpArgN) {
					ps.print(" " + (ISK(c) ? (-1 - INDEXK(c)) : c));
				}
				break;
			case iABx:
				if (getBMode(o) == OpArgK) {
					ps.print(a + " " + (-1 - bx));
				} else {
					ps.print(a + " " + (bx));
				}
				break;
			case iAsBx:
				ps.print(a + " " + sbx);
				break;
		}
		switch (o) {
			case OP_LOADK:
				ps.print("  ; ");
				printConstant(ps, f, bx);
				break;
			case OP_LOADKX:
				ps.print("  ; ");
				printConstant(ps, f, GETARG_Ax(code[pc+1]));
				break;
			case OP_GETUPVAL:
			case OP_SETUPVAL:
				ps.print("  ; ");
				if (f.upvalues.length > b) {
					printValue(ps, f.upvalues[b]);
				} else {
					ps.print("-");
				}
				break;
			case OP_GETTABUP:
				ps.print("  ; ");
				if (f.upvalues.length > b) {
					printValue(ps, f.upvalues[b]);
				} else {
					ps.print("-");
				}
				ps.print("[");
				if (c > 0xff) printConstant(ps, f, c & 0x0ff);
				else ps.print("-");
				ps.print("]");
				break;
			case OP_SETTABUP:
				ps.print("  ; ");
				if (f.upvalues.length > a) {
					printValue(ps, f.upvalues[a]);
				} else {
					ps.print("-");
				}
				ps.print("[");
				if (b > 0xff) printConstant(ps, f, b & 0x0ff);
				else ps.print("-");
				ps.print("] = ");
				if (c > 0xff) printConstant(ps, f, c & 0x0ff);
				else ps.print("-");
				break;
			case OP_GETTABLE:
			case OP_SELF:
				if (ISK(c)) {
					ps.print("  ; ");
					printConstant(ps, f, INDEXK(c));
				}
				break;
			case OP_SETTABLE:
			case OP_ADD:
			case OP_SUB:
			case OP_MUL:
			case OP_DIV:
			case OP_POW:
			case OP_EQ:
			case OP_LT:
			case OP_LE:
				if (ISK(b) || ISK(c)) {
					ps.print("  ; ");
					if (ISK(b)) {
						printConstant(ps, f, INDEXK(b));
					} else {
						ps.print("-");
					}
					ps.print(" ");
					if (ISK(c)) {
						printConstant(ps, f, INDEXK(c));
					} else {
						ps.print("-");
					}
				}
				break;
			case OP_JMP:
			case OP_FORLOOP:
			case OP_FORPREP:
			case OP_TFORLOOP:
				ps.print("  ; to " + (sbx + pc + 2));
				break;
			case OP_CLOSURE:
				ps.print("  ; " + f.p[bx].getClass().getName());
				break;
			case OP_SETLIST:
				if (c == 0) {
					ps.print("  ; " + code[++pc]);
				} else {
					ps.print("  ; " + c);
				}
				break;
			case OP_VARARG:
				ps.print("  ; is_vararg=" + f.is_vararg);
				break;
			default:
				break;
		}
	}

	private static int getline(Prototype f, int pc) {
		return pc > 0 && f.lineinfo != null && pc < f.lineinfo.length ? f.lineinfo[pc] : -1;
	}
}

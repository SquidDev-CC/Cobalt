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

import static org.squiddev.cobalt.Lua.*;

/**
 * Debug helper class to pretty-print lua bytecodes.
 *
 * @see Prototype
 * @see LuaClosure
 */
public class Print {

	/**
	 * opcode names
	 */
	private static final String STRING_FOR_NULL = "null";
	public static PrintStream ps = System.out;

	public static final String[] OPNAMES = {
		"MOVE",
		"LOADK",
		"LOADBOOL",
		"LOADNIL",
		"GETUPVAL",
		"GETGLOBAL",
		"GETTABLE",
		"SETGLOBAL",
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
		"TFORLOOP",
		"SETLIST",
		"CLOSE",
		"CLOSURE",
		"VARARG",
		null,
	};


	static void showString(StringBuilder sb, final LuaString s) {
		sb.append('"');
		for (int i = 0, n = s.length; i < n; i++) {
			int c = s.bytes[s.offset + i];
			if (c >= ' ' && c <= '~' && c != '\"' && c != '\\') {
				sb.append((char) c);
			} else {
				switch (c) {
					case '"':
						sb.append("\\\"");
						break;
					case '\\':
						sb.append("\\\\");
						break;
					case 0x0007: /* bell */
						sb.append("\\a");
						break;
					case '\b': /* backspace */
						sb.append("\\b");
						break;
					case '\f':  /* form feed */
						sb.append("\\f");
						break;
					case '\t':  /* tab */
						sb.append("\\t");
						break;
					case '\r': /* carriage return */
						sb.append("\\r");
						break;
					case '\n': /* newline */
						sb.append("\\n");
						break;
					case 0x000B: /* vertical tab */
						sb.append("\\v");
						break;
					default:
						sb.append('\\');
						sb.append(Integer.toString(1000 + 0xff & c).substring(1));
						break;
				}
			}
		}
		sb.append('"');
	}

	static void showValue(StringBuilder sb, LuaValue v) {
		if (v.type() == Constants.TSTRING) {
			showString(sb, (LuaString) v);
		} else {
			sb.append(v.toString());
		}
	}

	static void showConstant(StringBuilder sb, Prototype f, int i) {
		showValue(sb, f.k[i]);
	}

	/**
	 * Print the code in a prototype
	 *
	 * @param f the {@link Prototype}
	 */
	public static void printCode(Prototype f) {
		ps.print(showCode(f));
	}

	public static String showCode(Prototype f) {
		final StringBuilder sb = new StringBuilder();
		showCode(sb, f);
		return sb.toString();
	}

	public static void showCode(StringBuilder sb, Prototype f) {
		int[] code = f.code;
		int pc, n = code.length;
		for (pc = 0; pc < n; pc++) {
			showOpCode(sb, f, pc);
			sb.append(System.lineSeparator());
		}
	}

	/**
	 * Print an opcode in a prototype
	 *
	 * @param f  the {@link Prototype}
	 * @param pc the program counter to look up and print
	 */
	public static void printOpCode(Prototype f, int pc) {
		final StringBuilder sb = new StringBuilder();
		showOpCode(sb, f, pc);
		ps.print(sb);
	}

	/**
	 * Print an opcode in a prototype
	 *
	 * @param sb the {@link StringBuilder} to print to
	 * @param f  the {@link Prototype}
	 * @param pc the program counter to look up and print
	 */
	public static void showOpCode(StringBuilder sb, Prototype f, int pc) {
		int[] code = f.code;
		int i = code[pc];
		int o = GET_OPCODE(i);
		int a = GETARG_A(i);
		int b = GETARG_B(i);
		int c = GETARG_C(i);
		int bx = GETARG_Bx(i);
		int sbx = GETARG_sBx(i);
		int line = getline(f, pc);
		sb.append(' ');
		sb.append(f.compiledInstrs[pc] != null ? 'c' : ' ');
		sb.append(pc + 1).append("  ");
		if (line > 0) {
			sb.append("[").append(line).append("]  ");
		} else {
			sb.append("[-]  ");
		}
		sb.append(OPNAMES[o]).append("  ");
		switch (getOpMode(o)) {
			case iABC:
				sb.append(a);
				if (getBMode(o) != OpArgN) {
					sb.append(" ").append(ISK(b) ? (-1 - INDEXK(b)) : b);
				}
				if (getCMode(o) != OpArgN) {
					sb.append(" ").append(ISK(c) ? (-1 - INDEXK(c)) : c);
				}
				break;
			case iABx:
				if (getBMode(o) == OpArgK) {
					sb.append(a).append(" ").append(-1 - bx);
				} else {
					sb.append(a).append(" ").append(bx);
				}
				break;
			case iAsBx:
				if (o == OP_JMP) {
					sb.append(sbx);
				} else {
					sb.append(a).append(" ").append(sbx);
				}
				break;
		}
		switch (o) {
			case OP_LOADK:
			case OP_GETGLOBAL:
			case OP_SETGLOBAL:
				sb.append("  ; ");
				showConstant(sb, f, bx);
				break;
			case OP_GETUPVAL:
			case OP_SETUPVAL:
				sb.append("  ; ");
				if (f.upvalues.length > b) {
					showValue(sb, f.upvalues[b]);
				} else {
					sb.append("-");
				}
				break;
			case OP_GETTABLE:
			case OP_SELF:
				if (ISK(c)) {
					sb.append("  ; ");
					showConstant(sb, f, INDEXK(c));
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
					sb.append("  ; ");
					if (ISK(b)) {
						showConstant(sb, f, INDEXK(b));
					} else {
						sb.append("-");
					}
					sb.append(" ");
					if (ISK(c)) {
						showConstant(sb, f, INDEXK(c));
					} else {
						sb.append("-");
					}
				}
				break;
			case OP_JMP:
			case OP_FORLOOP:
			case OP_FORPREP:
				sb.append("  ; to ").append(sbx + pc + 2);
				break;
			case OP_CLOSURE:
				sb.append("  ; ").append(f.p[bx].getClass().getName());
				break;
			case OP_SETLIST:
				if (c == 0) {
					sb.append("  ; ").append(code[++pc]);
				} else {
					sb.append("  ; ").append(c);
				}
				break;
			case OP_VARARG:
				sb.append("  ; is_vararg=").append(f.is_vararg);
				break;
			default:
				break;
		}
	}

	public static String showOpCode(Prototype f, int pc) {
		final StringBuilder sb = new StringBuilder();
		showOpCode(sb, f, pc);
		return sb.toString();
	}

	private static int getline(Prototype f, int pc) {
		return pc >= 0 && f.lineinfo != null && pc < f.lineinfo.length ? f.lineinfo[pc] : -1;
	}

	static void printHeader(Prototype f) {
		String s = String.valueOf(f.source);
		if (s.startsWith("@") || s.startsWith("=")) {
			s = s.substring(1);
		} else if ("\033Lua".equals(s)) {
			s = "(bstring)";
		} else {
			s = "(string)";
		}
		String a = (f.linedefined == 0) ? "main" : "function";
		ps.print("\n%" + a + " <" + s + ":" + f.linedefined + ","
			+ f.lastlinedefined + "> (" + f.code.length + " instructions, "
			+ f.code.length * 4 + " bytes at " + id(f) + ")\n");
		ps.print(f.numparams + " param, " + f.maxstacksize + " slot, "
			+ f.upvalues.length + " upvalue, ");
		ps.print(f.locvars.length + " local, " + f.k.length
			+ " constant, " + f.p.length + " function\n");
	}

	static void printConstants(Prototype f) {
		int i, n = f.k.length;
		final StringBuilder sb = new StringBuilder();
		sb.append("constants (").append(n).append(") for ").append(id(f)).append(":\n");
		for (i = 0; i < n; i++) {
			sb.append("  ").append(i + 1).append("  ");
			showValue(sb, f.k[i]);
			sb.append("\n");
		}
		ps.print(sb.toString());
	}

	static void printLocals(Prototype f) {
		int i, n = f.locvars.length;
		ps.print("locals (" + n + ") for " + id(f) + ":\n");
		for (i = 0; i < n; i++) {
			ps.println("  " + i + "  " + f.locvars[i].name + " " + (f.locvars[i].startpc + 1) + " " + (f.locvars[i].endpc + 1));
		}
	}

	static void printUpValues(Prototype f) {
		int i, n = f.upvalues.length;
		ps.print("upvalues (" + n + ") for " + id(f) + ":\n");
		for (i = 0; i < n; i++) {
			ps.print("  " + i + "  " + f.upvalues[i] + "\n");
		}
	}

	public static void print(Prototype p) {
		printFunction(p, true);
	}

	public static void printFunction(Prototype f, boolean full) {
		int i, n = f.p.length;
		printHeader(f);
		printCode(f);
		if (full) {
			printConstants(f);
			printLocals(f);
			printUpValues(f);
		}
		for (i = 0; i < n; i++) {
			printFunction(f.p[i], full);
		}
	}

	private static void format(String s, int maxcols) {
		int n = s.length();
		if (n > maxcols) {
			ps.print(s.substring(0, maxcols));
		} else {
			ps.print(s);
			for (int i = maxcols - n; --i >= 0; ) {
				ps.print(' ');
			}
		}
	}

	private static String id(Prototype f) {
		return "Proto";
	}

	private void _assert(boolean b) {
		if (!b) {
			throw new NullPointerException("_assert failed");
		}
	}

	/**
	 * Print the state of a {@link LuaClosure} that is being executed
	 *
	 * @param cl      the {@link LuaClosure}
	 * @param pc      the program counter
	 * @param stack   the stack of {@link LuaValue}
	 * @param top     the top of the stack
	 * @param varargs any {@link Varargs} value that may apply
	 */
	public static void printState(LuaClosure cl, int pc, LuaValue[] stack, int top, Varargs varargs) {
		// print opcode into buffer
		PrintStream previous = ps;
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		ps = new PrintStream(baos);
		printOpCode(cl.getPrototype(), pc);
		ps.flush();
		ps.close();
		ps = previous;
		format(baos.toString(), 50);

		// print stack
		ps.print('[');
		for (int i = 0; i < stack.length; i++) {
			LuaValue v = stack[i];
			if (v == null) {
				ps.print(STRING_FOR_NULL);
			} else {
				switch (v.type()) {
					case Constants.TSTRING:
						LuaString s = (LuaString) v;
						ps.print(s.length() < 48 ?
							s.toString() :
							s.substring(0, 32).toString() + "...+" + (s.length() - 32) + "b");
						break;
					case Constants.TFUNCTION:
						ps.print((v instanceof LuaClosure) ?
							((LuaClosure) v).getPrototype().toString() : v.toString());
						break;
					case Constants.TUSERDATA:
						Object o = v.toUserdata();
						if (o != null) {
							String n = o.getClass().getName();
							n = n.substring(n.lastIndexOf('.') + 1);
							ps.print(n + ": " + Integer.toHexString(o.hashCode()));
						} else {
							ps.print(v.toString());
						}
						break;
					default:
						ps.print(v.toString());
				}
			}
			if (i + 1 == top) {
				ps.print(']');
			}
			ps.print(" | ");
		}
		ps.print(varargs);
		ps.println();
	}


}

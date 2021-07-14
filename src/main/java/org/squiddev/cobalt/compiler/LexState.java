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
package org.squiddev.cobalt.compiler;

import org.squiddev.cobalt.*;
import org.squiddev.cobalt.function.LocalVariable;
import org.squiddev.cobalt.lib.Utf8Lib;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Hashtable;

public class LexState {

	private static final String RESERVED_LOCAL_VAR_FOR_CONTROL = "(for control)";
	private static final String RESERVED_LOCAL_VAR_FOR_STATE = "(for state)";
	private static final String RESERVED_LOCAL_VAR_FOR_GENERATOR = "(for generator)";
	private static final String RESERVED_LOCAL_VAR_FOR_STEP = "(for step)";
	private static final String RESERVED_LOCAL_VAR_FOR_LIMIT = "(for limit)";
	private static final String RESERVED_LOCAL_VAR_FOR_INDEX = "(for index)";

	// keywords array
	private static final String[] RESERVED_LOCAL_VAR_KEYWORDS = new String[]{
		RESERVED_LOCAL_VAR_FOR_CONTROL,
		RESERVED_LOCAL_VAR_FOR_GENERATOR,
		RESERVED_LOCAL_VAR_FOR_INDEX,
		RESERVED_LOCAL_VAR_FOR_LIMIT,
		RESERVED_LOCAL_VAR_FOR_STATE,
		RESERVED_LOCAL_VAR_FOR_STEP
	};
	private static final Hashtable<String, Boolean> RESERVED_LOCAL_VAR_KEYWORDS_TABLE = new Hashtable<>();

	static {
		for (String RESERVED_LOCAL_VAR_KEYWORD : RESERVED_LOCAL_VAR_KEYWORDS) {
			RESERVED_LOCAL_VAR_KEYWORDS_TABLE.put(RESERVED_LOCAL_VAR_KEYWORD, Boolean.TRUE);
		}
	}

	public static final LuaString ENV = LuaString.valueOf("_ENV");

	private static final int EOZ = -1;
	private static final int MAXSRC = 80;
	private static final int MAX_INT = Integer.MAX_VALUE - 2;
	private static final int UCHAR_MAX = 255; // TODO, convert to unicode CHAR_MAX?
	private static final int LUAI_MAXCCALLS = 200;

	private static String LUA_QS(String s) {
		return "'" + s + "'";
	}

	private static String LUA_QL(Object o) {
		return LUA_QS(String.valueOf(o));
	}

	private static final int LUA_COMPAT_LSTR = 1; // 1 for compatibility, 2 for old behavior
	private static final boolean LUA_COMPAT_VARARG = true;

	public static boolean isReservedKeyword(String varName) {
		return RESERVED_LOCAL_VAR_KEYWORDS_TABLE.containsKey(varName);
	}

	/*
	 ** Marks the end of a patch list. It is an invalid value both as an absolute
	 ** address, and as a list link (would link an element to itself).
	 */
	static final int NO_JUMP = -1;

	/*
	 ** grep "ORDER OPR" if you change these enums
	 */
	static final int
		OPR_ADD = 0, OPR_SUB = 1, OPR_MUL = 2, OPR_DIV = 3, OPR_MOD = 4, OPR_POW = 5,
		OPR_CONCAT = 6,
		OPR_NE = 7, OPR_EQ = 8,
		OPR_LT = 9, OPR_LE = 10, OPR_GT = 11, OPR_GE = 12,
		OPR_AND = 13, OPR_OR = 14,
		OPR_NOBINOPR = 15;

	static final int
		OPR_MINUS = 0, OPR_NOT = 1, OPR_LEN = 2, OPR_NOUNOPR = 3;

	/* exp kind */
	static final int
		VVOID = 0,    /* no value */
		VNIL = 1,
		VTRUE = 2,
		VFALSE = 3,
		VK = 4,        /* info = index of constant in `k' */
		VKNUM = 5,    /* nval = numerical value */
		VLOCAL = 6,    /* info = local register */
		VUPVAL = 7,       /* info = index of upvalue in `upvalues' */
		VINDEXED = 9,    /* info = table register, aux = index register (or `k') */
		VJMP = 10,        /* info = instruction pc */
		VRELOCABLE = 11,    /* info = instruction pc */
		VNONRELOC = 12,    /* info = result register */
		VCALL = 13,    /* info = instruction pc */
		VVARARG = 14;    /* info = instruction pc */

	/* semantics information */
	private static class SemInfo {
		LuaValue r;
		LuaString ts;
	}

	private static class Token {
		int token;
		final SemInfo seminfo = new SemInfo();

		public void set(Token other) {
			this.token = other.token;
			this.seminfo.r = other.seminfo.r;
			this.seminfo.ts = other.seminfo.ts;
		}
	}

	private int current;  /* current character (charint) */
	private int linenumber;  /* input line counter */
	int lastline;  /* line of last token `consumed' */
	private final Token t = new Token();  /* current token */
	private final Token lookahead = new Token();  /* look ahead token */
	FuncState fs;  /* `FuncState' is private to the parser */
	private InputStream z;  /* input stream */
	private byte[] buff;  /* buffer for tokens */
	private int nbuff; /* length of buffer */
	private LuaString source;  /* current source name */
	private byte decpoint;  /* locale decimal point */
	public int nCcalls;
	private final HashMap<LuaString, LuaString> strings = new HashMap<>();
	DynamicData dyd;

	/* ORDER RESERVED */
	private final static String[] luaX_tokens = {
		"and", "break", "do", "else", "elseif",
		"end", "false", "for", "function", "goto", "if",
		"in", "local", "nil", "not", "or", "repeat",
		"return", "then", "true", "until", "while",
		"..", "...", "==", ">=", "<=", "~=", "::",
		"<eof>",
		"<number>", "<name>", "<string>",
	};

	final static int
		// terminal symbols denoted by reserved words
		TK_AND = 257, TK_BREAK = 258, TK_DO = 259, TK_ELSE = 260, TK_ELSEIF = 261,
		TK_END = 262, TK_FALSE = 263, TK_FOR = 264, TK_FUNCTION = 265, TK_GOTO = 266, TK_IF = 267,
		TK_IN = 268, TK_LOCAL = 269, TK_NIL = 270, TK_NOT = 271, TK_OR = 272, TK_REPEAT = 273,
		TK_RETURN = 274, TK_THEN = 275, TK_TRUE = 276, TK_UNTIL = 277, TK_WHILE = 278,
	// other terminal symbols
	TK_CONCAT = 279, TK_DOTS = 280, TK_EQ = 281, TK_GE = 282, TK_LE = 283, TK_NE = 284, TK_DBCOLON = 285,
		TK_EOS = 286,
		TK_NUMBER = 287, TK_NAME = 288, TK_STRING = 289;

	private final static int FIRST_RESERVED = TK_AND;
	private final static int NUM_RESERVED = TK_WHILE + 1 - FIRST_RESERVED;

	private final static Hashtable<LuaString, Integer> RESERVED = new Hashtable<>();

	static {
		for (int i = 0; i < NUM_RESERVED; i++) {
			LuaString ts = ValueFactory.valueOf(luaX_tokens[i]);
			RESERVED.put(ts, FIRST_RESERVED + i);
		}
	}

	private static boolean isAlphaNum(int c) {
		return c >= '0' && c <= '9'
			|| c >= 'a' && c <= 'z'
			|| c >= 'A' && c <= 'Z'
			|| c == '_';
	}

	private static boolean isAlpha(int c) {
		return c >= 'a' && c <= 'z' || c >= 'A' && c <= 'Z';
	}

	private static boolean isDigit(int c) {
		return c >= '0' && c <= '9';
	}

	private static boolean isSpace(int c) {
		return c <= ' ';
	}

	private static boolean isHex(int c) {
		return c >= '0' && c <= '9' || c >= 'a' && c <= 'f' || c >= 'A' && c <= 'F';
	}

	public LexState(InputStream stream) {
		this.z = stream;
		this.buff = new byte[32];
	}

	private void nextChar() {
		try {
			current = z.read();
		} catch (IOException e) {
			e.printStackTrace();
			current = EOZ;
		}
	}

	private boolean currIsNewline() {
		return current == '\n' || current == '\r';
	}

	private void save_and_next() {
		save(current);
		nextChar();
	}

	private void save(int c) {
		if (buff == null || nbuff + 1 > buff.length) {
			buff = LuaC.realloc(buff, nbuff * 2 + 1);
		}
		buff[nbuff++] = (byte) c;
	}


	private String token2str(int token) {
		if (token < FIRST_RESERVED) {
			return LUA_QS(iscntrl(token) ? "char(" + token + ")" : String.valueOf((char) token));
		} else {
			String name = luaX_tokens[token - FIRST_RESERVED];
			return token < TK_EOS ? LUA_QS(name) : name;
		}
	}

	private static boolean iscntrl(int token) {
		return token < ' ';
	}

	private String txtToken(int token) {
		switch (token) {
			case TK_NAME:
			case TK_STRING:
			case TK_NUMBER:
				return LUA_QS(new String(buff, 0, nbuff));
			default:
				return token2str(token);
		}
	}

	CompileException lexError(String msg, int token) {
		String cid = chunkId(source.toString()); // TODO: get source name from source
		String message = cid + ":" + linenumber + ": " + msg;
		if (token != 0) message += " near " + txtToken(token);
		return new CompileException(message);
	}

	String chunkId(String source) {
		if (source.startsWith("=")) {
			return source.substring(1);
		}
		String end = "";
		if (source.startsWith("@")) {
			source = source.substring(1);
		} else {
			source = "[string \"" + source;
			end = "\"]";
		}
		int n = source.length() + end.length();
		if (n > MAXSRC) {
			source = source.substring(0, MAXSRC - end.length() - 3) + "...";
		}
		return source + end;
	}

	CompileException syntaxError(String msg) {
		return lexError(msg, t.token);
	}

	// look up and keep at most one copy of each string
	public LuaString newString(byte[] bytes, int offset, int len) {
		LuaString tmp = LuaString.valueOf(bytes, offset, len);
		LuaString v = strings.get(tmp);
		if (v == null) {
			// must copy bytes, since bytes could be from reusable buffer
			byte[] copy = new byte[len];
			System.arraycopy(bytes, offset, copy, 0, len);
			v = LuaString.valueOf(copy);
			strings.put(v, v);
		}
		return v;
	}

	// only called by new_localvarliteral() for var names.
	private LuaString newString(String s) {
		byte[] b = s.getBytes();
		return newString(b, 0, b.length);
	}

	private void inclineNumber() throws CompileException {
		int old = current;
		LuaC._assert(currIsNewline(), linenumber);
		nextChar(); /* skip '\n' or '\r' */
		if (currIsNewline() && current != old) {
			nextChar(); /* skip '\n\r' or '\r\n' */
		}
		if (++linenumber >= MAX_INT) {
			throw syntaxError("chunk has too many lines");
		}
	}

	void setinput(int firstByte, InputStream z, LuaString source) {
		this.decpoint = '.';
		this.lookahead.token = TK_EOS; /* no look-ahead token */
		this.z = z;
		this.fs = null;
		this.linenumber = 1;
		this.lastline = 1;
		this.source = source;
		this.nbuff = 0;   /* initialize buffer */
		this.current = firstByte; /* read first char */
		this.skipShebang();
	}

	private void skipShebang() {
		if (current == '#') {
			while (!currIsNewline() && current != EOZ) {
				nextChar();
			}
		}
	}



	/*
	 ** =======================================================
	 ** LEXICAL ANALYZER
	 ** =======================================================
	 */


	private boolean check_next(String set) {
		if (set.indexOf(current) < 0) {
			return false;
		}
		save_and_next();
		return true;
	}

	private void buffreplace(byte from, byte to) {
		int n = nbuff;
		byte[] p = buff;
		while (--n >= 0) {
			if (p[n] == from) {
				p[n] = to;
			}
		}
	}

	private void str2d(String str, SemInfo seminfo) throws CompileException {
		// If we're a hex string, try to parse as a long. Java's handling of hex floats
		// is much more limited than C's version.
		if (str.startsWith("0x") || str.startsWith("0X")) {
			try {
				seminfo.r = ValueFactory.valueOf(Long.valueOf(str.substring(2), 16));
				return;
			} catch (NumberFormatException ignored) {
			}
		}

		try {
			seminfo.r = ValueFactory.valueOf(Double.parseDouble(str));
			return;
		} catch (NumberFormatException ignored) {
		}

		throw lexError("malformed number", TK_NUMBER);
	}

	private void read_numeral(SemInfo seminfo) throws CompileException {
		LuaC._assert(isDigit(current), linenumber);

		int first = current;
		save_and_next();
		String exp = "Ee";
		if (first == '0' && check_next("xX")) exp = "Pp";

		while (true) {
			// Exponential and sign
			if (check_next(exp)) check_next("+-");

			if (isHex(current) || current == '.') {
				save_and_next();
			} else {
				break;
			}
		}

		String str = new String(buff, 0, nbuff);
		str2d(str, seminfo);
	}

	private int skip_sep() throws CompileException {
		int count = 0;
		int s = current;
		LuaC._assert(s == '[' || s == ']', linenumber);
		save_and_next();
		while (current == '=') {
			save_and_next();
			count++;
		}
		return current == s ? count : -count - 1;
	}

	private void read_long_string(SemInfo seminfo, int sep) throws CompileException {
		int cont = 0;
		save_and_next(); /* skip 2nd `[' */
		if (currIsNewline()) /* string starts with a newline? */ {
			inclineNumber(); /* skip it */
		}
		for (boolean endloop = false; !endloop; ) {
			switch (current) {
				case EOZ:
					String msg = seminfo != null ? "unfinished long string"
						: "unfinished long comment";
					throw lexError(msg, TK_EOS);
				case '[': {
					if (skip_sep() == sep) {
						save_and_next(); /* skip 2nd `[' */
						cont++;
						if (LUA_COMPAT_LSTR == 1) {
							if (sep == 0) {
								throw lexError("nesting of [[...]] is deprecated", (int) '[');
							}
						}
					}
					break;
				}
				case ']': {
					if (skip_sep() == sep) {
						save_and_next(); /* skip 2nd `]' */
						if (LUA_COMPAT_LSTR == 2) {
							cont--;
							if (sep == 0 && cont >= 0) {
								break;
							}
						}
						endloop = true;
					}
					break;
				}
				case '\n':
				case '\r': {
					save('\n');
					inclineNumber();
					if (seminfo == null) {
						nbuff = 0; /* avoid wasting space */
					}
					break;
				}
				default: {
					if (seminfo != null) {
						save_and_next();
					} else {
						nextChar();
					}
				}
			}
		}
		if (seminfo != null) {
			seminfo.ts = newString(buff, 2 + sep, nbuff - 2 * (2 + sep));
		}
	}

	private void read_string(int del, SemInfo seminfo) throws CompileException {
		save_and_next();
		while (current != del) {
			switch (current) {
				case EOZ:
					throw lexError("unfinished string", TK_EOS);
				case '\n':
				case '\r':
					throw lexError("unfinished string", TK_STRING);
				case '\\': {
					save_and_next(); /* do not save the `\' */
					switch (current) {
						case 'a': /* bell */
							saveEscape('\u0007');
							break;
						case 'b': /* backspace */
							saveEscape('\b');
							break;
						case 'f': /* form feed */
							saveEscape('\f');
							break;
						case 'n': /* newline */
							saveEscape('\n');
							break;
						case 'r': /* carriage return */
							saveEscape('\r');
							break;
						case 't': /* tab */
							saveEscape('\t');
							break;
						case 'v': /* vertical tab */
							saveEscape('\u000B');
							break;
						case 'x':
							saveEscape(readHexEsc());
							break;
						case 'u':
							readUtf8Esc();
							break;
						case '\n': /* go through */
						case '\r':
							nbuff--;
							save('\n');
							inclineNumber();
							break;
						case EOZ:
							break; /* will raise an error next loop */
						case 'z': { // "zap" following span of spaces
							nbuff--;
							nextChar(); // Skip z and remove '\\'
							while (isSpace(current)) {
								if (currIsNewline()) inclineNumber();
								nextChar();
							}
							break;
						}
						default: {
							if (!isDigit(current)) {
								nbuff--;
								save_and_next(); /* handles \\, \", \', and \? */
							} else { /* \xxx */
								int c = readDecEsc();
								nbuff--;
								save(c);
							}
							break;
						}
					}
					break;
				}
				default:
					save_and_next();
			}
		}
		save_and_next(); /* skip delimiter */
		seminfo.ts = newString(buff, 1, nbuff - 2);
	}

	private void saveEscape(int character) {
		nextChar();
		nbuff--;
		save(character);
	}

	private int readHexEsc() throws CompileException {
		int r = (readHex() << 4) | readHex();
		nbuff -= 2;
		return r;
	}

	private int readDecEsc() throws CompileException {
		int i = 0;
		int result = 0;
		for (; i < 3 && isDigit(current); i++) {
			result = 10 * result + current - '0';
			save_and_next();
		}

		if (result > UCHAR_MAX) throw escapeError("escape sequence too large");
		nbuff -= i;

		return result;
	}

	private void readUtf8Esc() throws CompileException {
		save_and_next();
		if (current != '{') throw escapeError("mising '{'");

		int i = 4;
		long codepoint = readHex();
		while (true) {
			save_and_next();
			if (!isHex(current)) break;

			i++;
			codepoint = (codepoint << 4) | hexValue(current);
			if (codepoint > Utf8Lib.MAX_UNICODE) throw escapeError("UTF-8 value too large");
		}
		if (current != '}') throw escapeError("missing '}'");
		nextChar();

		nbuff -= i;
		if (codepoint < 0x80) {
			save((int) codepoint);
		} else {
			byte[] buffer = new byte[8];
			int j = Utf8Lib.buildCharacter(buffer, codepoint);
			for (; j > 0; j--) save(buffer[8 - j]);
		}
	}

	private int readHex() throws CompileException {
		save_and_next();
		if (!isHex(current)) throw escapeError("hexadecimal digit expected");
		return hexValue(current);
	}

	private static int hexValue(int c) {
		// Terrible bit twiddling right here:
		// 'A'..'F' corresponds to 0x41..0x46, and 'a'..'f' to 0x61..0x66. So bitwise and with 0xf
		// gives us the last digit, +9 to map from 1..6 to 10..15.
		return c <= '9' ? c - '0' : (c & 0xf) + 9;
	}

	private CompileException escapeError(String message) {
		if (current != EOZ) save_and_next();
		return lexError(message, TK_STRING);
	}

	private int llex(SemInfo seminfo) throws CompileException {
		nbuff = 0;
		while (true) {
			switch (current) {
				case '\n':
				case '\r': {
					inclineNumber();
					continue;
				}
				case '-': {
					nextChar();
					if (current != '-') {
						return '-';
					}
					/* else is a comment */
					nextChar();
					if (current == '[') {
						int sep = skip_sep();
						nbuff = 0; /* `skip_sep' may dirty the buffer */
						if (sep >= 0) {
							read_long_string(null, sep); /* long comment */
							nbuff = 0;
							continue;
						}
					}
					/* else short comment */
					while (!currIsNewline() && current != EOZ) {
						nextChar();
					}
					continue;
				}
				case '[': {
					int sep = skip_sep();
					if (sep >= 0) {
						read_long_string(seminfo, sep);
						return TK_STRING;
					} else if (sep == -1) {
						return '[';
					} else {
						throw lexError("invalid long string delimiter", TK_STRING);
					}
				}
				case '=': {
					nextChar();
					if (current != '=') {
						return '=';
					} else {
						nextChar();
						return TK_EQ;
					}
				}
				case '<': {
					nextChar();
					if (current != '=') {
						return '<';
					} else {
						nextChar();
						return TK_LE;
					}
				}
				case '>': {
					nextChar();
					if (current != '=') {
						return '>';
					} else {
						nextChar();
						return TK_GE;
					}
				}
				case '~': {
					nextChar();
					if (current != '=') {
						return '~';
					} else {
						nextChar();
						return TK_NE;
					}
				}
				case ':': {
					nextChar();
					if (current != ':') {
						return ':';
					} else {
						nextChar();
						return TK_DBCOLON;
					}
				}
				case '"':
				case '\'': {
					read_string(current, seminfo);
					return TK_STRING;
				}
				case '.': {
					save_and_next();
					if (check_next(".")) {
						if (check_next(".")) {
							return TK_DOTS; /* ... */
						} else {
							return TK_CONCAT; /* .. */
						}
					} else if (!isDigit(current)) {
						return '.';
					} else {
						read_numeral(seminfo);
						return TK_NUMBER;
					}
				}
				case EOZ: {
					return TK_EOS;
				}
				default: {
					if (isSpace(current)) {
						LuaC._assert(!currIsNewline(), linenumber);
						nextChar();
					} else if (isDigit(current)) {
						read_numeral(seminfo);
						return TK_NUMBER;
					} else if (isAlpha(current) || current == '_') {
						/* identifier or reserved word */
						LuaString ts;
						do {
							save_and_next();
						} while (isAlphaNum(current) || current == '_');
						ts = newString(buff, 0, nbuff);
						if (RESERVED.containsKey(ts) && !ts.equals(LuaString.valueOf("goto"))) {
							return RESERVED.get(ts);
						} else {
							seminfo.ts = ts;
							return TK_NAME;
						}
					} else {
						int c = current;
						nextChar();
						return c; /* single-char tokens (+ - / ...) */
					}
				}
			}
		}
	}

	void nextToken() throws CompileException {
		lastline = linenumber;
		if (lookahead.token != TK_EOS) { /* is there a look-ahead token? */
			t.set(lookahead); /* use this one */
			lookahead.token = TK_EOS; /* and discharge it */
		} else {
			t.token = llex(t.seminfo); /* read next token */
		}
	}

	private void lookahead() throws CompileException {
		LuaC._assert(lookahead.token == TK_EOS, linenumber);
		lookahead.token = llex(lookahead.seminfo);
	}

	// =============================================================
	// from lcode.h
	// =============================================================


	// =============================================================
	// from lparser.c
	// =============================================================

	static class expdesc {
		int k; // expkind, from enumerated list, above

		static class U { // originally a union
			static class S {
				int idx;
				int t;
				int vt;
			}

			final S ind = new S();
			int info;
			private LuaValue _nval;

			public void setNval(LuaValue r) {
				_nval = r;
			}

			public LuaValue nval() {
				return _nval == null ? LuaInteger.valueOf(info) : _nval;
			}
		}

		final U u = new U();
		final IntPtr t = new IntPtr(); /* patch list of `exit when true' */
		final IntPtr f = new IntPtr(); /* patch list of `exit when false' */

		void init(int k, int i) {
			this.f.i = NO_JUMP;
			this.t.i = NO_JUMP;
			this.k = k;
			this.u.info = i;
		}

		boolean hasjumps() {
			return t.i != f.i;
		}

		boolean isnumeral() {
			return k == VKNUM && t.i == NO_JUMP && f.i == NO_JUMP;
		}

		public void setvalue(expdesc other) {
			this.k = other.k;
			this.u._nval = other.u._nval;
			this.u.info = other.u.info;
			this.u.ind.idx = other.u.ind.idx;
			this.u.ind.t = other.u.ind.t;
			this.u.ind.vt = other.u.ind.vt;
			this.t.i = other.t.i;
			this.f.i = other.f.i;
		}
	}

	static class LabelDescription {
		LuaString name; /* label identifier */
		int pc; /* position in code */
		int line; /* line where it appeared */
		short nactvar; /* local level where it appears in current block */
	}

	static class DynamicData {
		short[] actvar;
		int nactvar;
		LabelDescription[] gt;
		short ngt;
		LabelDescription[] label;
		short nlabel;

		DynamicData() {
			actvar = null; nactvar = 0;
			gt = null; ngt = 0;
			label = null; nlabel = 0;
		}
	}

	private boolean hasmultret(int k) {
		return k == VCALL || k == VVARARG;
	}

	/*----------------------------------------------------------------------
	name		args	description
	------------------------------------------------------------------------*/

	/*
	 * * prototypes for recursive non-terminal functions
	 */

	private void error_expected(int token) throws CompileException {
		throw syntaxError(token2str(token) + " expected");
	}

	private boolean testnext(int c) throws CompileException {
		if (t.token == c) {
			nextToken();
			return true;
		} else {
			return false;
		}
	}

	void check(int c) throws CompileException {
		if (t.token != c) {
			error_expected(c);
		}
	}

	private void checknext(int c) throws CompileException {
		check(c);
		nextToken();
	}

	private void check_condition(boolean c, String msg) throws CompileException {
		if (!c) {
			throw syntaxError(msg);
		}
	}


	private void check_match(int what, int who, int where) throws CompileException {
		if (!testnext(what)) {
			if (where == linenumber) {
				error_expected(what);
			} else {
				throw syntaxError(LUA_QS(token2str(what))
					+ " expected " + "(to close " + LUA_QS(token2str(who))
					+ " at line " + where + ")");
			}
		}
	}

	private LuaString str_checkname() throws CompileException {
		LuaString ts;
		check(TK_NAME);
		ts = t.seminfo.ts;
		nextToken();
		return ts;
	}

	private void codestring(expdesc e, LuaString s) {
		e.init(VK, fs.stringK(s));
	}

	private void checkname(expdesc e) throws CompileException {
		codestring(e, str_checkname());
	}


	private int registerlocalvar(LuaString varname) {
		FuncState fs = this.fs;
		Prototype f = fs.f;
		if (f.locvars == null || fs.nlocvars + 1 > f.locvars.length) {
			f.locvars = LuaC.realloc(f.locvars, fs.nlocvars * 2 + 1);
		}
		f.locvars[fs.nlocvars] = new LocalVariable(varname, 0, 0);
		return fs.nlocvars++;
	}


	//
//	#define new_localvarliteral(ls,v,n) \
//	  this.new_localvar(luaX_newstring(ls, "" v, (sizeof(v)/sizeof(char))-1), n)
//
	private void new_localvarliteral(String v, int n) throws CompileException {
		LuaString ts = newString(v);
		new_localvar(ts, n);
	}

	private void new_localvar(LuaString name, int n) throws CompileException {
		FuncState fs = this.fs;
		fs.checklimit(fs.nactvar + n + 1, LuaC.LUAI_MAXVARS, "local variables");
		fs.actvar[fs.nactvar + n] = (short) registerlocalvar(name);
	}

	private void adjustlocalvars(int nvars) {
		FuncState fs = this.fs;
		fs.nactvar = (short) (fs.nactvar + nvars);
		for (; nvars > 0; nvars--) {
			fs.getlocvar(fs.nactvar - nvars).startpc = fs.pc;
		}
	}

	void removevars(int tolevel) {
		FuncState fs = this.fs;
		fs.ls.dyd.nactvar -= (fs.nactvar - tolevel);
		while (fs.nactvar > tolevel) {
			fs.getlocvar(--fs.nactvar).endpc = fs.pc;
		}
	}

	private void singlevar(expdesc var) throws CompileException {
		LuaString varname = str_checkname();
		FuncState fs = this.fs;
		if (fs.singlevaraux(varname, var, 1) == VVOID) {
			expdesc key = new expdesc();
			fs.singlevaraux(LexState.ENV, var, 1);
			LuaC._assert(var.k == LexState.VLOCAL || var.k == LexState.VUPVAL, linenumber);
			codestring(key, varname);
			fs.indexed(var, key);
		}
	}

	private void adjust_assign(int nvars, int nexps, expdesc e) throws CompileException {
		FuncState fs = this.fs;
		int extra = nvars - nexps;
		if (hasmultret(e.k)) {
			/* includes call itself */
			extra++;
			if (extra < 0) {
				extra = 0;
			}
			/* last exp. provides the difference */
			fs.setreturns(e, extra);
			if (extra > 1) {
				fs.reserveregs(extra - 1);
			}
		} else {
			/* close last expression */
			if (e.k != VVOID) {
				fs.exp2nextreg(e);
			}
			if (extra > 0) {
				int reg = fs.freereg;
				fs.reserveregs(extra);
				fs.nil(reg, extra);
			}
		}
	}

	private void enterlevel() throws CompileException {
		if (++nCcalls > LUAI_MAXCCALLS) {
			throw lexError("chunk has too many syntax levels", 0);
		}
	}

	private void leavelevel() {
		nCcalls--;
	}

	private void closegoto(int g, LabelDescription label) throws CompileException {
		LabelDescription gt = dyd.gt[g];
		LuaC._assert(gt.name.equals(label.name), linenumber);
		if (gt.nactvar < label.nactvar) {
			throw new CompileException(String.format("<goto %s> at line %d jumps into the scope of local '%s'",
				gt.name.toString(), gt.line, fs.getlocvar(gt.nactvar).name.toString()));
		}
		fs.patchlist(gt.pc, label.pc);
		for (int i = g; i < dyd.ngt - 1; i++) {
			dyd.gt[i] = dyd.gt[i+1];
		}
		dyd.ngt--;
	}

	boolean findlabel(int g) throws CompileException {
		LabelDescription gt = dyd.gt[g];
		for (int i = fs.bl.firstlabel; i < dyd.nlabel; i++) {
			LabelDescription lb = dyd.label[i];
			if (lb.name.equals(gt.name)) {
				if (gt.nactvar > lb.nactvar && (fs.bl.upval || dyd.nlabel > fs.bl.firstlabel)) {
					fs.patchclose(gt.pc, lb.nactvar);
				}
				closegoto(g, lb);
				return true;
			}
		}
		return false;
	}

	private int newlabelentry(DynamicData dyd, boolean islabel, LuaString name, int line, int pc) {
		if (islabel) {
			if (dyd.label == null || dyd.label.length < dyd.nlabel + 1) {
				dyd.label = LuaC.realloc(dyd.label, dyd.nlabel * 2 + 1);
			}
			dyd.label[dyd.nlabel] = new LabelDescription();
			dyd.label[dyd.nlabel].name = name;
			dyd.label[dyd.nlabel].line = line;
			dyd.label[dyd.nlabel].nactvar = fs.nactvar;
			dyd.label[dyd.nlabel].pc = pc;
			return dyd.nlabel++;
		} else {
			if (dyd.gt == null || dyd.gt.length < dyd.ngt + 1) {
				dyd.gt = LuaC.realloc(dyd.gt, dyd.ngt * 2 + 1);
			}
			dyd.gt[dyd.ngt] = new LabelDescription();
			dyd.gt[dyd.ngt].name = name;
			dyd.gt[dyd.ngt].line = line;
			dyd.gt[dyd.ngt].nactvar = fs.nactvar;
			dyd.gt[dyd.ngt].pc = pc;
			return dyd.ngt++;
		}
	}

	private void findgotos(LabelDescription lb) throws CompileException {
		int i = fs.bl.firstgoto;
		while (i < dyd.ngt) {
			if (dyd.gt[i].name.equals(lb.name)) {
				closegoto(i, lb);
			} else {
				i++;
			}
		}
	}

	/*
    ** create a label named "break" to resolve break statements
    */
	void breaklabel() throws CompileException {
		int l = newlabelentry(dyd, true, LuaString.valueOf("break"), 0, fs.pc);
		findgotos(dyd.label[l]);
	}

	/*
	** generates an error for an undefined 'goto'; choose appropriate
	** message when label name is a reserved word (which can only be 'break')
	*/
	void /* no return */ undefgoto(LabelDescription gt) throws CompileException {
		String msg = String.format(isReservedKeyword(gt.name.toString()) ? "<%s> at line %d not inside a loop" : "no visible label '%s' for <goto> at line %d",
			gt.name.toString(), gt.line);
		throw new CompileException(msg);
	}

	Prototype addprototype() {
		Prototype clp;
		Prototype f = fs.f;
		if (f.p == null || fs.np >= f.p.length) {
			f.p = LuaC.realloc(f.p, fs.np * 2 + 1);
		}
		f.p[fs.np++] = clp = new Prototype();
		return clp;
	}

	private void codeclosure(expdesc v) throws CompileException {
		FuncState fs = this.fs.prev;
		v.init(LexState.VRELOCABLE, fs.codeABx(Lua52.OP_CLOSURE, 0, fs.np - 1));
		fs.exp2nextreg(v);
	}

	void open_func(FuncState fs, FuncState.BlockCnt bl) throws CompileException {
		Prototype f = fs.f;
		if (this.fs != null) {
			f.source = this.fs.f.source;
		}
		f.isLua52 = true;
		fs.prev = this.fs;  /* linked list of FuncStates */
		fs.ls = this;
		this.fs = fs;
		fs.pc = 0;
		fs.lasttarget = 0;
		fs.jpc = new IntPtr(NO_JUMP);
		fs.freereg = 0;
		fs.nk = 0;
		fs.np = 0;
		fs.nlocvars = 0;
		fs.nactvar = 0;
		fs.bl = null;
		f.maxstacksize = 2;  /* registers 0/1 are always valid */
		//fs.h = new LTable();
		fs.htable = new Hashtable<>();
		fs.enterblock(bl, false);
	}

	void close_func() throws CompileException {
		FuncState fs = this.fs;
		Prototype f = fs.f;
		fs.ret(0, 0); /* final return */
		fs.leaveblock();
		f.code = LuaC.realloc(f.code, fs.pc);
		f.lineinfo = LuaC.realloc(f.lineinfo, fs.pc);
		// f.sizelineinfo = fs.pc;
		f.k = LuaC.realloc(f.k, fs.nk);
		f.p = LuaC.realloc(f.p, fs.np);
		f.locvars = LuaC.realloc(f.locvars, fs.nlocvars);
		// f.sizelocvars = fs.nlocvars;
		f.upvalues = LuaC.realloc(f.upvalues, f.nups);
		// LuaC._assert (CheckCode.checkcode(f));
		LuaC._assert(fs.bl == null, linenumber);
		this.fs = fs.prev;
//		L.top -= 2; /* remove table and prototype from the stack */
		// /* last token read was anchored in defunct function; must reanchor it
		// */
		// if (fs!=null) ls.anchor_token();
	}

	/*============================================================*/
	/* GRAMMAR RULES */
	/*============================================================*/

	private void field(expdesc v) throws CompileException {
		/* field -> ['.' | ':'] NAME */
		FuncState fs = this.fs;
		expdesc key = new expdesc();
		fs.exp2anyregup(v);
		this.nextToken(); /* skip the dot or colon */
		this.checkname(key);
		fs.indexed(v, key);
	}

	private void yindex(expdesc v) throws CompileException {
		/* index -> '[' expr ']' */
		this.nextToken(); /* skip the '[' */
		this.expr(v);
		this.fs.exp2val(v);
		this.checknext(']');
	}


	/*
	 ** {======================================================================
	 ** Rules for Constructors
	 ** =======================================================================
	 */


	static class ConsControl {
		expdesc v = new expdesc(); /* last list item read */
		expdesc t; /* table descriptor */
		int nh; /* total number of `record' elements */
		int na; /* total number of array elements */
		int tostore; /* number of array elements pending to be stored */
	}


	private void recfield(ConsControl cc) throws CompileException {
		/* recfield -> (NAME | `['exp1`]') = exp1 */
		FuncState fs = this.fs;
		int reg = this.fs.freereg;
		expdesc key = new expdesc();
		expdesc val = new expdesc();
		int rkkey;
		if (this.t.token == TK_NAME) {
			fs.checklimit(cc.nh, MAX_INT, "items in a constructor");
			this.checkname(key);
		} else
			/* this.t.token == '[' */ {
			this.yindex(key);
		}
		cc.nh++;
		this.checknext('=');
		rkkey = fs.exp2RK(key);
		this.expr(val);
		fs.codeABC(Lua52.OP_SETTABLE, cc.t.u.info, rkkey, fs.exp2RK(val));
		fs.freereg = reg; /* free registers */
	}

	private void listfield(ConsControl cc) throws CompileException {
		this.expr(cc.v);
		fs.checklimit(cc.na, MAX_INT, "items in a constructor");
		cc.na++;
		cc.tostore++;
	}


	private void constructor(expdesc t) throws CompileException {
		/* constructor -> ?? */
		FuncState fs = this.fs;
		int line = this.linenumber;
		int pc = fs.codeABC(Lua52.OP_NEWTABLE, 0, 0, 0);
		ConsControl cc = new ConsControl();
		cc.na = cc.nh = cc.tostore = 0;
		cc.t = t;
		t.init(VRELOCABLE, pc);
		cc.v.init(VVOID, 0); /* no value (yet) */
		fs.exp2nextreg(t); /* fix it at stack top (for gc) */
		this.checknext('{');
		do {
			LuaC._assert(cc.v.k == VVOID || cc.tostore > 0, linenumber);
			if (this.t.token == '}') {
				break;
			}
			fs.closelistfield(cc);
			switch (this.t.token) {
				case TK_NAME: { /* may be listfields or recfields */
					this.lookahead();
					if (this.lookahead.token != '=') /* expression? */ {
						this.listfield(cc);
					} else {
						this.recfield(cc);
					}
					break;
				}
				case '[': { /* constructor_item -> recfield */
					this.recfield(cc);
					break;
				}
				default: { /* constructor_part -> listfield */
					this.listfield(cc);
					break;
				}
			}
		} while (this.testnext(',') || this.testnext(';'));
		this.check_match('}', '{', line);
		fs.lastlistfield(cc);
		InstructionPtr i = new InstructionPtr(fs.f.code, pc);
		LuaC.SETARG_B(i, luaO_int2fb(cc.na)); /* set initial array size */
		LuaC.SETARG_C(i, luaO_int2fb(cc.nh));  /* set initial table size */
	}

	/*
	 ** converts an integer to a "floating point byte", represented as
	 ** (eeeeexxx), where the real value is (1xxx) * 2^(eeeee - 1) if
	 ** eeeee != 0 and (xxx) otherwise.
	 */
	private static int luaO_int2fb(int x) {
		int e = 0;  /* expoent */
		while (x >= 16) {
			x = x + 1 >> 1;
			e++;
		}
		if (x < 8) {
			return x;
		} else {
			return e + 1 << 3 | x - 8;
		}
	}


	/* }====================================================================== */

	private void parlist() throws CompileException {
		/* parlist -> [ param { `,' param } ] */
		FuncState fs = this.fs;
		Prototype f = fs.f;
		int nparams = 0;
		f.is_vararg = 0;
		if (this.t.token != ')') {  /* is `parlist' not empty? */
			do {
				switch (this.t.token) {
					case TK_NAME: {  /* param . NAME */
						this.new_localvar(this.str_checkname(), nparams++);
						break;
					}
					case TK_DOTS: {  /* param . `...' */
						this.nextToken();
						if (LUA_COMPAT_VARARG) {
							/* use `arg' as default name */
							this.new_localvarliteral("arg", nparams++);
							f.is_vararg = Lua52.VARARG_HASARG | Lua52.VARARG_NEEDSARG;
						}
						f.is_vararg |= Lua52.VARARG_ISVARARG;
						break;
					}
					default:
						throw syntaxError("<name> or " + LUA_QL("...") + " expected");
				}
			} while (f.is_vararg == 0 && this.testnext(','));
		}
		this.adjustlocalvars(nparams);
		f.numparams = fs.nactvar - (f.is_vararg & Lua52.VARARG_HASARG);
		fs.reserveregs(fs.nactvar);  /* reserve register for parameters */
	}


	private void body(expdesc e, boolean needself, int line) throws CompileException {
		/* body -> `(' parlist `)' chunk END */
		FuncState new_fs = new FuncState();
		FuncState.BlockCnt bl = new FuncState.BlockCnt();
		new_fs.f = this.addprototype();
		new_fs.f.linedefined = line;
		open_func(new_fs, bl);
		this.checknext('(');
		if (needself) {
			new_localvarliteral("self", 0);
			adjustlocalvars(1);
		}
		this.parlist();
		this.checknext(')');
		this.statlist();
		new_fs.f.lastlinedefined = this.linenumber;
		this.check_match(TK_END, TK_FUNCTION, line);
		this.codeclosure(e);
		this.close_func();
	}

	private int explist1(expdesc v) throws CompileException {
		/* explist1 -> expr { `,' expr } */
		int n = 1; /* at least one expression */
		this.expr(v);
		while (this.testnext(',')) {
			fs.exp2nextreg(v);
			this.expr(v);
			n++;
		}
		return n;
	}


	private void funcargs(expdesc f, int line) throws CompileException {
		FuncState fs = this.fs;
		expdesc args = new expdesc();
		int base, nparams;
		switch (this.t.token) {
			case '(': { /* funcargs -> `(' [ explist1 ] `)' */
				this.nextToken();
				if (this.t.token == ')') /* arg list is empty? */ {
					args.k = VVOID;
				} else {
					this.explist1(args);
					fs.setmultret(args);
				}
				this.check_match(')', '(', line);
				break;
			}
			case '{': { /* funcargs -> constructor */
				this.constructor(args);
				break;
			}
			case TK_STRING: { /* funcargs -> STRING */
				this.codestring(args, this.t.seminfo.ts);
				this.nextToken(); /* must use `seminfo' before `next' */
				break;
			}
			default: {
				throw syntaxError("function arguments expected");
			}
		}
		LuaC._assert(f.k == VNONRELOC, linenumber);
		base = f.u.info; /* base register for call */
		if (hasmultret(args.k)) {
			nparams = Lua52.LUA_MULTRET; /* open call */
		} else {
			if (args.k != VVOID) {
				fs.exp2nextreg(args); /* close last argument */
			}
			nparams = fs.freereg - (base + 1);
		}
		f.init(VCALL, fs.codeABC(Lua52.OP_CALL, base, nparams + 1, 2));
		fs.fixline(line);
		fs.freereg = base + 1;  /* call remove function and arguments and leaves
		 * (unless changed) one result */
	}


	/*
	 ** {======================================================================
	 ** Expression parsing
	 ** =======================================================================
	 */

	private void primaryexp(expdesc v) throws CompileException {
		/* primaryexp -> NAME | '(' expr ')' */
		switch (this.t.token) {
			case '(': {
				int line = this.linenumber;
				this.nextToken();
				this.expr(v);
				this.check_match(')', '(', line);
				fs.dischargevars(v);
				return;
			}
			case TK_NAME: {
				this.singlevar(v);
				return;
			}
			default: {
				throw syntaxError("unexpected symbol");
			}
		}
	}


	private void suffixedexp(expdesc v) throws CompileException {
		/*
		 * suffixedexp -> prefixexp { `.' NAME | `[' exp `]' | `:' NAME funcargs |
		 * funcargs }
		 */
		FuncState fs = this.fs;
		int line = linenumber;
		this.primaryexp(v);
		for (; ; ) {
			switch (this.t.token) {
				case '.': { /* field */
					this.field(v);
					break;
				}
				case '[': { /* `[' exp1 `]' */
					expdesc key = new expdesc();
					fs.exp2anyregup(v);
					this.yindex(key);
					fs.indexed(v, key);
					break;
				}
				case ':': { /* `:' NAME funcargs */
					expdesc key = new expdesc();
					this.nextToken();
					this.checkname(key);
					fs.self(v, key);
					this.funcargs(v, line);
					break;
				}
				case '(':
				case TK_STRING:
				case '{': { /* funcargs */
					fs.exp2nextreg(v);
					this.funcargs(v, line);
					break;
				}
				default:
					return;
			}
		}
	}


	private void simpleexp(expdesc v) throws CompileException {
		/*
		 * simpleexp -> NUMBER | STRING | NIL | true | false | ... | constructor |
		 * FUNCTION body | suffixedexp
		 */
		switch (this.t.token) {
			case TK_NUMBER: {
				v.init(VKNUM, 0);
				v.u.setNval(this.t.seminfo.r);
				break;
			}
			case TK_STRING: {
				this.codestring(v, this.t.seminfo.ts);
				break;
			}
			case TK_NIL: {
				v.init(VNIL, 0);
				break;
			}
			case TK_TRUE: {
				v.init(VTRUE, 0);
				break;
			}
			case TK_FALSE: {
				v.init(VFALSE, 0);
				break;
			}
			case TK_DOTS: { /* vararg */
				FuncState fs = this.fs;
				this.check_condition(fs.f.is_vararg != 0, "cannot use " + LUA_QL("...")
					+ " outside a vararg function");
				fs.f.is_vararg &= ~Lua52.VARARG_NEEDSARG; /* don't need 'arg' */
				v.init(VVARARG, fs.codeABC(Lua52.OP_VARARG, 0, 1, 0));
				break;
			}
			case '{': { /* constructor */
				this.constructor(v);
				return;
			}
			case TK_FUNCTION: {
				this.nextToken();
				this.body(v, false, this.linenumber);
				return;
			}
			default: {
				this.suffixedexp(v);
				return;
			}
		}
		this.nextToken();
	}


	private int getunopr(int op) {
		switch (op) {
			case TK_NOT:
				return OPR_NOT;
			case '-':
				return OPR_MINUS;
			case '#':
				return OPR_LEN;
			default:
				return OPR_NOUNOPR;
		}
	}


	private int getbinopr(int op) {
		switch (op) {
			case '+':
				return OPR_ADD;
			case '-':
				return OPR_SUB;
			case '*':
				return OPR_MUL;
			case '/':
				return OPR_DIV;
			case '%':
				return OPR_MOD;
			case '^':
				return OPR_POW;
			case TK_CONCAT:
				return OPR_CONCAT;
			case TK_NE:
				return OPR_NE;
			case TK_EQ:
				return OPR_EQ;
			case '<':
				return OPR_LT;
			case TK_LE:
				return OPR_LE;
			case '>':
				return OPR_GT;
			case TK_GE:
				return OPR_GE;
			case TK_AND:
				return OPR_AND;
			case TK_OR:
				return OPR_OR;
			default:
				return OPR_NOBINOPR;
		}
	}

	static class Priority {
		final byte left; /* left priority for each binary operator */

		final byte right; /* right priority */

		public Priority(int i, int j) {
			left = (byte) i;
			right = (byte) j;
		}
	}

	private static Priority[] priority = {  /* ORDER OPR */
		new Priority(6, 6), new Priority(6, 6), new Priority(7, 7), new Priority(7, 7), new Priority(7, 7),  /* `+' `-' `/' `%' */
		new Priority(10, 9), new Priority(5, 4),                 /* power and concat (right associative) */
		new Priority(3, 3), new Priority(3, 3),                  /* equality and inequality */
		new Priority(3, 3), new Priority(3, 3), new Priority(3, 3), new Priority(3, 3),  /* order */
		new Priority(2, 2), new Priority(1, 1)                   /* logical (and/or) */
	};

	private static final int UNARY_PRIORITY = 8;  /* priority for unary operators */


	/*
	 ** subexpr -> (simpleexp | unop subexpr) { binop subexpr }
	 ** where `binop' is any binary operator with a priority higher than `limit'
	 */
	private int subexpr(expdesc v, int limit) throws CompileException {
		int op;
		int uop;
		this.enterlevel();
		uop = getunopr(this.t.token);
		if (uop != OPR_NOUNOPR) {
			int line = linenumber;
			this.nextToken();
			this.subexpr(v, UNARY_PRIORITY);
			fs.prefix(uop, v, line);
		} else {
			this.simpleexp(v);
		}
		/* expand while operators have priorities higher than `limit' */
		op = getbinopr(this.t.token);
		while (op != OPR_NOBINOPR && priority[op].left > limit) {
			expdesc v2 = new expdesc();
			int nextop;
			int line = linenumber;
			this.nextToken();
			fs.infix(op, v);
			/* read sub-expression with higher priority */
			nextop = this.subexpr(v2, priority[op].right);
			fs.posfix(op, v, v2, line);
			op = nextop;
		}
		this.leavelevel();
		return op; /* return first untreated operator */
	}

	private void expr(expdesc v) throws CompileException {
		this.subexpr(v, 0);
	}

	/* }==================================================================== */



	/*
	 ** {======================================================================
	 ** Rules for Statements
	 ** =======================================================================
	 */


	private boolean block_follow(boolean withUntil) {
		switch (t.token) {
			case TK_ELSE:
			case TK_ELSEIF:
			case TK_END:
			case TK_EOS:
				return true;
			case TK_UNTIL:
				return withUntil;
			default:
				return false;
		}
	}


	private void block() throws CompileException {
		/* block -> chunk */
		FuncState fs = this.fs;
		FuncState.BlockCnt bl = new FuncState.BlockCnt();
		fs.enterblock(bl, false);
		this.statlist();
		//LuaC._assert(bl.breaklist.i == NO_JUMP, linenumber);
		fs.leaveblock();
	}


	/*
	 ** structure to chain all variables in the left-hand side of an
	 ** assignment
	 */
	static class LHS_assign {
		LHS_assign prev;
		/* variable (global, local, upvalue, or indexed) */
		expdesc v = new expdesc();
	}


	/*
	 ** check whether, in an assignment to a local variable, the local variable
	 ** is needed in a previous assignment (to a table). If so, save original
	 ** local value in a safe place and use this safe copy in the previous
	 ** assignment.
	 */
	private void check_conflict(LHS_assign lh, expdesc v) throws CompileException {
		FuncState fs = this.fs;
		int extra = fs.freereg;  /* eventual position to save local variable */
		boolean conflict = false;
		for (; lh != null; lh = lh.prev) {
			if (lh.v.k == VINDEXED) {
				if (lh.v.u.ind.vt == v.k && lh.v.u.ind.t == v.u.info) {  /* conflict? */
					conflict = true;
					lh.v.u.ind.vt = VLOCAL;
					lh.v.u.ind.t = extra;  /* previous assignment will use safe copy */
				}
				if (v.k == VLOCAL && lh.v.u.ind.idx == v.u.info) {  /* conflict? */
					conflict = true;
					lh.v.u.ind.idx = extra;  /* previous assignment will use safe copy */
				}
			}
		}
		if (conflict) {
			fs.codeABC(Lua52.OP_MOVE, fs.freereg, v.u.info, 0); /* make copy */
			fs.reserveregs(1);
		}
	}


	private void assignment(LHS_assign lh, int nvars) throws CompileException {
		expdesc e = new expdesc();
		this.check_condition(VLOCAL <= lh.v.k && lh.v.k <= VINDEXED,
			"syntax error");
		if (this.testnext(',')) {  /* assignment -> `,' suffixedexp assignment */
			LHS_assign nv = new LHS_assign();
			nv.prev = lh;
			this.suffixedexp(nv.v);
			if (nv.v.k == VLOCAL) {
				this.check_conflict(lh, nv.v);
			}
			this.assignment(nv, nvars + 1);
		} else {  /* assignment . `=' explist1 */
			int nexps;
			this.checknext('=');
			nexps = this.explist1(e);
			if (nexps != nvars) {
				this.adjust_assign(nvars, nexps, e);
				if (nexps > nvars) {
					this.fs.freereg -= nexps - nvars;  /* remove extra values */
				}
			} else {
				fs.setoneret(e);  /* close last expression */
				fs.storevar(lh.v, e);
				return;  /* avoid default */
			}
		}
		e.init(VNONRELOC, this.fs.freereg - 1);  /* default assignment */
		fs.storevar(lh.v, e);
	}


	private int cond() throws CompileException {
		/* cond -> exp */
		expdesc v = new expdesc();
		/* read condition */
		this.expr(v);
		/* `falses' are all equal here */
		if (v.k == VNIL) {
			v.k = VFALSE;
		}
		fs.goiftrue(v);
		return v.f.i;
	}

	private void gotostat(int pc) throws CompileException {
		LuaString label;
		if (testnext(TK_GOTO)) {
			label = str_checkname();
		} else {
			this.nextToken();
			label = LuaString.valueOf("break");
		}
		int g = newlabelentry(dyd, false, label, linenumber, pc);
		findlabel(g);
	}



	private void labelstat(LuaString label, int line) throws CompileException {
		/* label -> '::' NAME '::' */
		fs.checkrepeated(dyd.label, dyd.nlabel, label); /* check for repeated labels */
		checknext(TK_DBCOLON); /* skip double colon */
		/* create new entry for this label */
		int l = newlabelentry(dyd, true, label, line, fs.pc); /* index of new label being created */
		while (t.token == ';' || t.token == TK_DBCOLON) { /* skip other no-op statements */
			statement();
		}
		if (block_follow(false)) { /* label is last no-op statement in the block? */
			/* assume that locals are already out of scope */
			dyd.label[l].nactvar = fs.bl.nactvar;
		}
		findgotos(dyd.label[l]);
	}

	private void whilestat(int line) throws CompileException {
		/* whilestat -> WHILE cond DO block END */
		FuncState fs = this.fs;
		int whileinit;
		int condexit;
		FuncState.BlockCnt bl = new FuncState.BlockCnt();
		this.nextToken();  /* skip WHILE */
		whileinit = fs.getlabel();
		condexit = this.cond();
		fs.enterblock(bl, true);
		this.checknext(TK_DO);
		this.block();
		fs.patchlist(fs.jump(), whileinit);
		this.check_match(TK_END, TK_WHILE, line);
		fs.leaveblock();
		fs.patchtohere(condexit);  /* false conditions finish the loop */
	}

	private void repeatstat(int line) throws CompileException {
		/* repeatstat -> REPEAT block UNTIL cond */
		int condexit;
		FuncState fs = this.fs;
		int repeat_init = fs.getlabel();
		FuncState.BlockCnt bl1 = new FuncState.BlockCnt();
		FuncState.BlockCnt bl2 = new FuncState.BlockCnt();
		fs.enterblock(bl1, true); /* loop block */
		fs.enterblock(bl2, false); /* scope block */
		this.nextToken(); /* skip REPEAT */
		this.statlist();
		this.check_match(TK_UNTIL, TK_REPEAT, line);
		condexit = this.cond(); /* read condition (inside scope block) */
		if (bl2.upval) { /* upvalues? */
			fs.patchclose(condexit, bl2.nactvar);
		}
		fs.leaveblock(); /* finish scope */
		fs.patchlist(condexit, repeat_init); /* close the loop */
		fs.leaveblock(); /* finish loop */
	}


	private int exp1() throws CompileException {
		expdesc e = new expdesc();
		this.expr(e);
		fs.exp2nextreg(e);
		return e.u.info;
	}


	private void forbody(int base, int line, int nvars, boolean isnum) throws CompileException {
		/* forbody -> DO block */
		FuncState.BlockCnt bl = new FuncState.BlockCnt();
		FuncState fs = this.fs;
		int prep, endfor;
		this.adjustlocalvars(3); /* control variables */
		this.checknext(TK_DO);
		prep = isnum ? fs.codeAsBx(Lua52.OP_FORPREP, base, NO_JUMP) : fs.jump();
		fs.enterblock(bl, false); /* scope for declared variables */
		this.adjustlocalvars(nvars);
		fs.reserveregs(nvars);
		this.block();
		fs.leaveblock(); /* end of scope for declared variables */
		fs.patchtohere(prep);
		if (isnum)  /* numeric for? */
			endfor = fs.codeAsBx(Lua52.OP_FORLOOP, base, NO_JUMP);
		else {  /* generic for */
			fs.codeABC(Lua52.OP_TFORCALL, base, 0, nvars);
			fs.fixline(line);
			endfor = fs.codeAsBx(Lua52.OP_TFORLOOP, base + 2, NO_JUMP);
		}
		fs.patchlist(endfor, prep + 1);
		fs.fixline(line); /* pretend that `Lua52.OP_FOR' starts the loop */
	}


	private void fornum(LuaString varname, int line) throws CompileException {
		/* fornum -> NAME = exp1,exp1[,exp1] forbody */
		FuncState fs = this.fs;
		int base = fs.freereg;
		this.new_localvarliteral(RESERVED_LOCAL_VAR_FOR_INDEX, 0);
		this.new_localvarliteral(RESERVED_LOCAL_VAR_FOR_LIMIT, 1);
		this.new_localvarliteral(RESERVED_LOCAL_VAR_FOR_STEP, 2);
		this.new_localvar(varname, 3);
		this.checknext('=');
		this.exp1(); /* initial value */
		this.checknext(',');
		this.exp1(); /* limit */
		if (this.testnext(',')) {
			this.exp1(); /* optional step */
		} else { /* default step = 1 */
			fs.codeABx(Lua52.OP_LOADK, fs.freereg, fs.numberK(LuaInteger.valueOf(1)));
			fs.reserveregs(1);
		}
		this.forbody(base, line, 1, true);
	}


	private void forlist(LuaString indexname) throws CompileException {
		/* forlist -> NAME {,NAME} IN explist1 forbody */
		FuncState fs = this.fs;
		expdesc e = new expdesc();
		int nvars = 0;
		int line;
		int base = fs.freereg;
		/* create control variables */
		this.new_localvarliteral(RESERVED_LOCAL_VAR_FOR_GENERATOR, nvars++);
		this.new_localvarliteral(RESERVED_LOCAL_VAR_FOR_STATE, nvars++);
		this.new_localvarliteral(RESERVED_LOCAL_VAR_FOR_CONTROL, nvars++);
		/* create declared variables */
		this.new_localvar(indexname, nvars++);
		while (this.testnext(',')) {
			this.new_localvar(this.str_checkname(), nvars++);
		}
		this.checknext(TK_IN);
		line = this.linenumber;
		this.adjust_assign(3, this.explist1(e), e);
		fs.checkstack(3); /* extra space to call generator */
		this.forbody(base, line, nvars - 3, false);
	}


	private void forstat(int line) throws CompileException {
		/* forstat -> FOR (fornum | forlist) END */
		FuncState fs = this.fs;
		LuaString varname;
		FuncState.BlockCnt bl = new FuncState.BlockCnt();
		fs.enterblock(bl, true); /* scope for loop and control variables */
		this.nextToken(); /* skip `for' */
		varname = this.str_checkname(); /* first variable name */
		switch (this.t.token) {
			case '=':
				this.fornum(varname, line);
				break;
			case ',':
			case TK_IN:
				this.forlist(varname);
				break;
			default:
				throw syntaxError(LUA_QL("=") + " or " + LUA_QL("in") + " expected");
		}
		this.check_match(TK_END, TK_FOR, line);
		fs.leaveblock(); /* loop scope (`break' jumps to this point) */
	}


	private void test_then_block(IntPtr escapelist) throws CompileException {
		/* test_then_block -> [IF | ELSEIF] cond THEN block */
		FuncState.BlockCnt bl = new FuncState.BlockCnt();
		int jf;
		this.nextToken(); /* skip IF or ELSEIF */
		expdesc v = new expdesc();
		expr(v);
		this.checknext(TK_THEN);
		if (t.token == TK_GOTO || t.token == TK_BREAK) {
			fs.goiffalse(v);
			fs.enterblock(bl, false);
			gotostat(v.t.i);
			while (t.token == ';' || t.token == TK_DBCOLON) { /* skip other no-op statements */
				statement();
			}
			if (block_follow(false)) { /* 'goto' is the entire block? */
				fs.leaveblock();
				return;
			} else {
				jf = fs.jump();
			}
		} else {
			fs.goiftrue(v);
			fs.enterblock(bl, false);
			jf = v.f.i;
		}
		this.statlist(); /* `then' part */
		fs.leaveblock();
		if (t.token == TK_ELSE || t.token == TK_ELSEIF) {
			fs.concat(escapelist, fs.jump());
		}
		fs.patchtohere(jf);
	}


	private void ifstat(int line) throws CompileException {
		/* ifstat -> IF cond THEN block {ELSEIF cond THEN block} [ELSE block]
		 * END */
		FuncState fs = this.fs;
		IntPtr escapelist = new IntPtr(NO_JUMP);
		test_then_block(escapelist); /* IF cond THEN block */
		while (this.t.token == TK_ELSEIF) {
			test_then_block(escapelist); /* ELSEIF cond THEN block */
		}
		if (testnext(TK_ELSE)) {
			this.block(); /* `else' part */
		}
		this.check_match(TK_END, TK_IF, line);
		fs.patchtohere(escapelist.i);
	}

	private void localfunc() throws CompileException {
		expdesc b = new expdesc();
		FuncState fs = this.fs;
		this.new_localvar(this.str_checkname(), 0);
		this.adjustlocalvars(1);
		this.body(b, false, this.linenumber);
		/* debug information will only see the variable after this point! */
		fs.getlocvar(fs.nactvar - 1).startpc = fs.pc;
	}


	private void localstat() throws CompileException {
		/* stat -> LOCAL NAME {`,' NAME} [`=' explist1] */
		int nvars = 0;
		int nexps;
		expdesc e = new expdesc();
		do {
			this.new_localvar(this.str_checkname(), nvars++);
		} while (this.testnext(','));
		if (this.testnext('=')) {
			nexps = this.explist1(e);
		} else {
			e.k = VVOID;
			nexps = 0;
		}
		this.adjust_assign(nvars, nexps, e);
		this.adjustlocalvars(nvars);
	}


	private boolean funcname(expdesc v) throws CompileException {
		/* funcname -> NAME {field} [`:' NAME] */
		boolean needself = false;
		this.singlevar(v);
		while (this.t.token == '.') {
			this.field(v);
		}
		if (this.t.token == ':') {
			needself = true;
			this.field(v);
		}
		return needself;
	}


	private void funcstat(int line) throws CompileException {
		/* funcstat -> FUNCTION funcname body */
		boolean needself;
		expdesc v = new expdesc();
		expdesc b = new expdesc();
		this.nextToken(); /* skip FUNCTION */
		needself = this.funcname(v);
		this.body(b, needself, line);
		fs.storevar(v, b);
		fs.fixline(line); /* definition `happens' in the first line */
	}


	private void exprstat() throws CompileException {
		/* stat -> func | assignment */
		FuncState fs = this.fs;
		LHS_assign v = new LHS_assign();
		this.suffixedexp(v.v);
		if (v.v.k == VCALL) /* stat -> func */ {
			LuaC.SETARG_C(fs.getcodePtr(v.v), 1); /* call statement uses no results */
		} else { /* stat -> assignment */
			v.prev = null;
			this.assignment(v, 1);
		}
	}

	private void retstat() throws CompileException {
		/* stat -> RETURN explist */
		FuncState fs = this.fs;
		expdesc e = new expdesc();
		int first, nret; /* registers with returned values */
		this.nextToken(); /* skip RETURN */
		if (block_follow(true) || this.t.token == ';') {
			first = nret = 0; /* return no values */
		} else {
			nret = this.explist1(e); /* optional return values */
			if (hasmultret(e.k)) {
				fs.setmultret(e);
				if (e.k == VCALL && nret == 1) { /* tail call? */
					LuaC.SET_OPCODE(fs.getcodePtr(e), Lua52.OP_TAILCALL);
					LuaC._assert(Lua52.GETARG_A(fs.getcode(e)) == fs.nactvar, linenumber);
				}
				first = fs.nactvar;
				nret = Lua52.LUA_MULTRET; /* return all values */
			} else {
				if (nret == 1) /* only one single value? */ {
					first = fs.exp2anyreg(e);
				} else {
					fs.exp2nextreg(e); /* values must go to the `stack' */
					first = fs.nactvar; /* return all `active' values */
					LuaC._assert(nret == fs.freereg - first, linenumber);
				}
			}
		}
		fs.ret(first, nret);
		testnext(';');
	}


	private void statement() throws CompileException {
		int line = this.linenumber; /* may be needed for error messages */
		enterlevel();
		if (this.t.token == TK_NAME && this.t.seminfo.ts.equals(LuaString.valueOf("goto"))) {
			lookahead();
			if (lookahead.token == TK_NAME) {
				this.t.token = TK_GOTO;
			}
		}
		switch (this.t.token) {
			case ';': { /* stat -> ';' (empty statement) */
				nextToken(); /* skip ';' */
				break;
			}
			case TK_IF: { /* stat -> ifstat */
				this.ifstat(line);
				break;
			}
			case TK_WHILE: { /* stat -> whilestat */
				this.whilestat(line);
				break;
			}
			case TK_DO: { /* stat -> DO block END */
				this.nextToken(); /* skip DO */
				this.block();
				this.check_match(TK_END, TK_DO, line);
				break;
			}
			case TK_FOR: { /* stat -> forstat */
				this.forstat(line);
				break;
			}
			case TK_REPEAT: { /* stat -> repeatstat */
				this.repeatstat(line);
				break;
			}
			case TK_FUNCTION: {
				this.funcstat(line); /* stat -> funcstat */
				break;
			}
			case TK_LOCAL: { /* stat -> localstat */
				this.nextToken(); /* skip LOCAL */
				if (this.testnext(TK_FUNCTION)) /* local function? */ {
					this.localfunc();
				} else {
					this.localstat();
				}
				break;
			}
			case TK_DBCOLON: { /* stat -> label */
				this.nextToken();
				this.labelstat(str_checkname(), line);
				break;
			}
			case TK_RETURN: { /* stat -> retstat */
				this.retstat();
				break;
			}
			case TK_BREAK:  /* stat -> breakstat */
			case TK_GOTO: { /* stat -> 'goto' NAME */
				if (this.t.token == TK_GOTO && LuaC.blockGoto) {
					throw lexError("illegal jump statement", this.t.token);
				}
				this.gotostat(fs.jump());
				break;
			}
			default: {
				this.exprstat();
				break;
			}
		}
		LuaC._assert(fs.f.maxstacksize >= fs.freereg && fs.freereg >= fs.nactvar, linenumber);
		fs.freereg = fs.nactvar; /* free registers */
		leavelevel();
	}

	void statlist() throws CompileException {
		/* chunk -> { stat [`;'] } */
		while (!block_follow(true)) {
			if (this.t.token == TK_RETURN) {
				statement();
				return;  /* 'return' must be last statement */
			}
			this.statement();
		}
	}

	/* }====================================================================== */
}

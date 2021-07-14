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

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.squiddev.cobalt.compiler.CompileException;
import org.squiddev.cobalt.function.OneArgFunction;
import org.squiddev.cobalt.function.ZeroArgFunction;
import org.squiddev.cobalt.lib.Bit32Lib;
import org.squiddev.cobalt.lib.Utf8Lib;

import java.io.File;
import java.io.IOException;

import static org.squiddev.cobalt.ValueFactory.valueOf;

/**
 * Just runs various libraries in the main test suite
 */
public class AssertTests {
	@ParameterizedTest(name = ParameterizedTest.ARGUMENTS_WITH_NAMES_PLACEHOLDER)
	@ValueSource(strings = {"table-hash-01", "table-hash-02", "table-hash-03"})
	public void tables(String name) throws IOException, CompileException, LuaError, InterruptedException {
		ScriptHelper helpers = new ScriptHelper("/assert/table/");
		helpers.setup();
		LuaThread.runMain(helpers.state, helpers.loadScript(name));
	}

	@ParameterizedTest(name = ParameterizedTest.ARGUMENTS_WITH_NAMES_PLACEHOLDER)
	@ValueSource(strings = {
		"base-issues",
		"debug",
		"debug-coroutine-hook",
		"debug-getinfo",
		"debug-upvalue",
		"gc",
		"immutable",
		"invalid-tailcall",
		"lex-context",
		"lex-number",
		"load-error",
		"modulo-large",
		"no-unwind",
		"number-format",
		"string-compare",
		"string-issues",
		"string-format",
		"table",
		"time",
		"tonumber",
		"traceback",
	})
	public void main(String name) throws IOException, CompileException, LuaError, InterruptedException {
		ScriptHelper helpers = new ScriptHelper("/assert/");
		helpers.setup();
		helpers.runWithDump(name);
	}

	@ParameterizedTest(name = ParameterizedTest.ARGUMENTS_WITH_NAMES_PLACEHOLDER)
	@ValueSource(strings = {
		// Skip all, api, big and main
		"attrib",
		"calls",
		"checktable",
		"closure",
		"code",
		"constructs",
		"db",
		"errors",
		"events",
		"files",
		"gc",
		"literals",
		"locals",
		"math",
		"nextvar",
		"pm",
		"sort",
		"strings",
		"vararg",
		"verybig",
	})
	public void lua51(String name) throws Exception {
		new File("libs").mkdirs();
		new File("libs/P1").mkdirs();

		ScriptHelper helpers = new ScriptHelper("/assert/lua5.1/");
		helpers.setup();
		helpers.globals.rawset("mkdir", new OneArgFunction() {
			@Override
			public LuaValue call(LuaState state, LuaValue arg) throws LuaError {
				return valueOf(new File(arg.checkString()).mkdirs());
			}
		});

		// TODO: Move this into the debug library
		((LuaTable) helpers.globals.rawget("debug")).rawset("debug", new ZeroArgFunction() {
			@Override
			public LuaValue call(LuaState state) {
				return Constants.NONE;
			}
		});

		helpers.runWithDump(name);
	}

	@ParameterizedTest(name = ParameterizedTest.ARGUMENTS_WITH_NAMES_PLACEHOLDER)
	@ValueSource(strings = {
		"bitwise",
		"goto",
	})
	public void lua52(String name) throws Exception {
		ScriptHelper helpers = new ScriptHelper("/assert/lua5.2/");
		helpers.setup();
		helpers.globals.load(helpers.state, new Bit32Lib());

		helpers.runWithDump(name);
	}

	@ParameterizedTest(name = ParameterizedTest.ARGUMENTS_WITH_NAMES_PLACEHOLDER)
	@ValueSource(strings = {
		"db",
		"nextvar",
		"tpack",
		"utf8",
	})
	public void lua53(String name) throws Exception {
		ScriptHelper helpers = new ScriptHelper("/assert/lua5.3/");
		helpers.setup();
		helpers.globals.load(helpers.state, new Bit32Lib());
		helpers.globals.load(helpers.state, new Utf8Lib());

		helpers.runWithDump(name);
	}
}

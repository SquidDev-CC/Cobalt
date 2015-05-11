/**
 * ****************************************************************************
 * Copyright (c) 2009-2011 Luaj.org. All rights reserved.
 * <p>
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * <p>
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * <p>
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 * ****************************************************************************
 */
package org.luaj.vm2.lib.jse;

import org.luaj.vm2.LuaValue;
import org.luaj.vm2.lib.LibFunction;
import org.luaj.vm2.lib.OneArgFunction;
import org.luaj.vm2.lib.TwoArgFunction;

/**
 * Subclass of {@link LibFunction} which implements the lua standard {@code math}
 * library.
 * <p>
 * It contains all lua math functions, including those not available on the JME platform.
 * See {@link org.luaj.vm2.lib.MathLib} for the exception list.
 * <p>
 * This has been implemented to match as closely as possible the behavior in the corresponding library in C.
 *
 * @see LibFunction
 * @see JsePlatform
 * @see JseMathLib
 * @see <a href="http://www.lua.org/manual/5.1/manual.html#5.6">http://www.lua.org/manual/5.1/manual.html#5.6</a>
 */
public class JseMathLib extends org.luaj.vm2.lib.MathLib {

	public JseMathLib() {
	}

	@Override
	public LuaValue call(LuaValue arg) {
		LuaValue t = super.call(arg);
		bind(t, JseMathLib1.class, new String[]{
			"acos", "asin", "atan", "cosh",
			"exp", "log", "log10", "sinh",
			"tanh"});
		bind(t, JseMathLib2.class, new String[]{
			"atan2", "pow",});
		return t;
	}

	public static final class JseMathLib1 extends OneArgFunction {
		@Override
		public LuaValue call(LuaValue arg) {
			switch (opcode) {
				case 0:
					return valueOf(Math.acos(arg.checkdouble()));
				case 1:
					return valueOf(Math.asin(arg.checkdouble()));
				case 2:
					return valueOf(Math.atan(arg.checkdouble()));
				case 3:
					return valueOf(Math.cosh(arg.checkdouble()));
				case 4:
					return valueOf(Math.exp(arg.checkdouble()));
				case 5:
					return valueOf(Math.log(arg.checkdouble()));
				case 6:
					return valueOf(Math.log10(arg.checkdouble()));
				case 7:
					return valueOf(Math.sinh(arg.checkdouble()));
				case 8:
					return valueOf(Math.tanh(arg.checkdouble()));
			}
			return NIL;
		}
	}

	public static final class JseMathLib2 extends TwoArgFunction {
		@Override
		public LuaValue call(LuaValue arg1, LuaValue arg2) {
			switch (opcode) {
				case 0:
					return valueOf(Math.atan2(arg1.checkdouble(), arg2.checkdouble()));
				case 1:
					return valueOf(Math.pow(arg1.checkdouble(), arg2.checkdouble()));
			}
			return NIL;
		}
	}

	/**
	 * Faster, better version of pow() used by arithmetic operator ^
	 */
	@Override
	public double dpow_lib(double a, double b) {
		return Math.pow(a, b);
	}


}

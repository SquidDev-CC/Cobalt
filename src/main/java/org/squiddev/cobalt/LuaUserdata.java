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

public class LuaUserdata extends LuaValue {

	public final Object instance;
	public LuaTable metatable;
	public LuaTable uservalue;

	public LuaUserdata(Object obj) {
		super(Constants.TUSERDATA);
		instance = obj;
	}

	public LuaUserdata(Object obj, LuaTable metatable) {
		super(Constants.TUSERDATA);
		instance = obj;
		this.metatable = metatable;
	}

	@Override
	public String toString() {
		return String.valueOf(instance);
	}

	public int hashCode() {
		return instance.hashCode();
	}

	public Object userdata() {
		return instance;
	}

	@Override
	public boolean isUserdata(Class<?> c) {
		return c.isAssignableFrom(instance.getClass());
	}

	@Override
	public Object toUserdata() {
		return instance;
	}

	@Override
	public <T> T toUserdata(Class<T> c) {
		return c.isAssignableFrom(instance.getClass()) ? c.cast(instance) : null;
	}

	@Override
	public Object optUserdata(Object defval) {
		return instance;
	}

	@Override
	public <T> T optUserdata(Class<T> c, T defval) throws LuaError {
		if (!c.isAssignableFrom(instance.getClass())) throw ErrorFactory.typeError(this, c.getName());
		return c.cast(instance);
	}

	@Override
	public LuaTable getMetatable(LuaState state) {
		return metatable;
	}

	@Override
	public void setMetatable(LuaState state, LuaTable metatable) {
		this.metatable = metatable;
	}

	@Override
	public Object checkUserdata() {
		return instance;
	}

	@Override
	public <T> T checkUserdata(Class<T> c) throws LuaError {
		if (!c.isAssignableFrom(instance.getClass())) throw ErrorFactory.typeError(this, c.getName());
		return c.cast(instance);
	}

	public boolean equals(Object val) {
		return this == val || val instanceof LuaUserdata && instance.equals(((LuaUserdata) val).instance);
	}

	@Override
	public boolean raweq(LuaValue val) {
		return val.raweq(this);
	}

	@Override
	public boolean raweq(LuaUserdata val) {
		return this == val || (metatable == val.metatable && instance.equals(val.instance));
	}
}

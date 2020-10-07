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
package org.squiddev.cobalt.table;

import org.junit.jupiter.api.Test;
import org.squiddev.cobalt.*;

import java.util.Vector;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.lessThanOrEqualTo;
import static org.junit.jupiter.api.Assertions.*;
import static org.squiddev.cobalt.support.Matchers.between;

/**
 * Tests for tables used as lists.
 */
public class TableArrayTest {
	private final LuaState state = new LuaState();

	@Test
	public void testInOrderIntegerKeyInsertion() throws LuaError, UnwindThrowable {
		LuaTable t = new LuaTable();

		for (int i = 1; i <= 32; ++i) {
			OperationHelper.setTable(state, t, ValueFactory.valueOf(i), LuaString.valueOf("Test Value! " + i));
		}

		// Ensure all keys are still there.
		for (int i = 1; i <= 32; ++i) {
			assertEquals("Test Value! " + i, OperationHelper.getTable(state, t, ValueFactory.valueOf(i)).toString());
		}

		// Ensure capacities make sense
		assertEquals(0, t.getHashLength());

		assertThat(t.getArrayLength(), between(32, 64));
	}

	@Test
	public void testResize() throws LuaError, UnwindThrowable {
		LuaTable t = new LuaTable();

		// NOTE: This order of insertion is important.
		OperationHelper.setTable(state, t, ValueFactory.valueOf(3), LuaInteger.valueOf(3));
		OperationHelper.setTable(state, t, ValueFactory.valueOf(1), LuaInteger.valueOf(1));
		OperationHelper.setTable(state, t, ValueFactory.valueOf(5), LuaInteger.valueOf(5));
		OperationHelper.setTable(state, t, ValueFactory.valueOf(4), LuaInteger.valueOf(4));
		OperationHelper.setTable(state, t, ValueFactory.valueOf(6), LuaInteger.valueOf(6));
		OperationHelper.setTable(state, t, ValueFactory.valueOf(2), LuaInteger.valueOf(2));

		for (int i = 1; i < 6; ++i) {
			assertEquals(LuaInteger.valueOf(i), OperationHelper.getTable(state, t, ValueFactory.valueOf(i)));
		}

		assertThat(t.getArrayLength(), between(3, 12));
		assertThat(t.getHashLength(), lessThanOrEqualTo(3));
	}

	@Test
	public void testOutOfOrderIntegerKeyInsertion() throws LuaError, UnwindThrowable {
		LuaTable t = new LuaTable();

		for (int i = 32; i > 0; --i) {
			OperationHelper.setTable(state, t, ValueFactory.valueOf(i), LuaString.valueOf("Test Value! " + i));
		}

		// Ensure all keys are still there.
		for (int i = 1; i <= 32; ++i) {
			assertEquals("Test Value! " + i, OperationHelper.getTable(state, t, ValueFactory.valueOf(i)).toString());
		}

		// Ensure capacities make sense
		assertEquals(32, t.getArrayLength());
		assertEquals(0, t.getHashLength());

	}

	@Test
	public void testStringAndIntegerKeys() throws LuaError, UnwindThrowable {
		LuaTable t = new LuaTable();

		for (int i = 0; i < 10; ++i) {
			LuaString str = LuaString.valueOf(String.valueOf(i));
			OperationHelper.setTable(state, t, ValueFactory.valueOf(i), str);
			OperationHelper.setTable(state, t, str, LuaInteger.valueOf(i));
		}

		assertThat(t.getArrayLength(), between(8, 18)); // 1, 2, ..., 9
		assertThat(t.getHashLength(), between(11, 33)); // 0, "0", "1", ..., "9"

		LuaValue[] keys = t.keys();

		int intKeys = 0;
		int stringKeys = 0;

		assertEquals(20, keys.length);
		for (LuaValue k : keys) {
			if (k instanceof LuaInteger) {
				final int ik = k.toInteger();
				assertTrue(ik >= 0 && ik < 10);
				final int mask = 1 << ik;
				assertEquals(0, (intKeys & mask));
				intKeys |= mask;
			} else if (k instanceof LuaString) {
				final int ik = Integer.parseInt(k.toString());
				assertEquals(String.valueOf(ik), k.toString());
				assertTrue(ik >= 0 && ik < 10);
				final int mask = 1 << ik;
				assertEquals(0, (stringKeys & mask), "Key \"" + ik + "\" found more than once");
				stringKeys |= mask;
			} else {
				fail("Unexpected type of key found");
			}
		}

		assertEquals(0x03FF, intKeys);
		assertEquals(0x03FF, stringKeys);
	}

	@Test
	public void testBadInitialCapacity() throws LuaError, UnwindThrowable {
		LuaTable t = new LuaTable(0, 1);

		OperationHelper.setTable(state, t, ValueFactory.valueOf("test"), LuaString.valueOf("foo"));
		OperationHelper.setTable(state, t, ValueFactory.valueOf("explode"), LuaString.valueOf("explode"));
		assertEquals(2, t.keyCount());
	}

	@Test
	public void testRemove0() throws LuaError, UnwindThrowable {
		LuaTable t = new LuaTable(2, 0);

		OperationHelper.setTable(state, t, ValueFactory.valueOf(1), LuaString.valueOf("foo"));
		OperationHelper.setTable(state, t, ValueFactory.valueOf(2), LuaString.valueOf("bah"));
		assertNotSame(Constants.NIL, OperationHelper.getTable(state, t, ValueFactory.valueOf(1)));
		assertNotSame(Constants.NIL, OperationHelper.getTable(state, t, ValueFactory.valueOf(2)));
		assertEquals(Constants.NIL, OperationHelper.getTable(state, t, ValueFactory.valueOf(3)));

		OperationHelper.setTable(state, t, ValueFactory.valueOf(1), Constants.NIL);
		OperationHelper.setTable(state, t, ValueFactory.valueOf(2), Constants.NIL);
		OperationHelper.setTable(state, t, ValueFactory.valueOf(3), Constants.NIL);
		assertEquals(Constants.NIL, OperationHelper.getTable(state, t, ValueFactory.valueOf(1)));
		assertEquals(Constants.NIL, OperationHelper.getTable(state, t, ValueFactory.valueOf(2)));
		assertEquals(Constants.NIL, OperationHelper.getTable(state, t, ValueFactory.valueOf(3)));
	}

	@Test
	public void testRemove1() throws LuaError, UnwindThrowable {
		LuaTable t = new LuaTable(0, 1);

		assertEquals(0, t.keyCount());

		OperationHelper.setTable(state, t, ValueFactory.valueOf("test"), LuaString.valueOf("foo"));
		assertEquals(1, t.keyCount());
		OperationHelper.setTable(state, t, ValueFactory.valueOf("explode"), Constants.NIL);
		assertEquals(1, t.keyCount());
		OperationHelper.setTable(state, t, ValueFactory.valueOf(42), Constants.NIL);
		assertEquals(1, t.keyCount());
		OperationHelper.setTable(state, t, new LuaTable(), Constants.NIL);
		assertEquals(1, t.keyCount());
		OperationHelper.setTable(state, t, ValueFactory.valueOf("test"), Constants.NIL);
		assertEquals(0, t.keyCount());

		OperationHelper.setTable(state, t, ValueFactory.valueOf(10), LuaInteger.valueOf(5));
		OperationHelper.setTable(state, t, ValueFactory.valueOf(10), Constants.NIL);
		assertEquals(0, t.keyCount());
	}

	@Test
	public void testRemove2() throws LuaError, UnwindThrowable {
		LuaTable t = new LuaTable(0, 1);

		OperationHelper.setTable(state, t, ValueFactory.valueOf("test"), LuaString.valueOf("foo"));
		OperationHelper.setTable(state, t, ValueFactory.valueOf("string"), LuaInteger.valueOf(10));
		assertEquals(2, t.keyCount());

		OperationHelper.setTable(state, t, ValueFactory.valueOf("string"), Constants.NIL);
		OperationHelper.setTable(state, t, ValueFactory.valueOf("three"), LuaDouble.valueOf(3.14));
		assertEquals(2, t.keyCount());

		OperationHelper.setTable(state, t, ValueFactory.valueOf("test"), Constants.NIL);
		assertEquals(1, t.keyCount());

		OperationHelper.setTable(state, t, ValueFactory.valueOf(10), LuaInteger.valueOf(5));
		assertEquals(2, t.keyCount());

		OperationHelper.setTable(state, t, ValueFactory.valueOf(10), Constants.NIL);
		assertEquals(1, t.keyCount());

		OperationHelper.setTable(state, t, ValueFactory.valueOf("three"), Constants.NIL);
		assertEquals(0, t.keyCount());
	}

	@Test
	public void testInOrderlen() throws LuaError, UnwindThrowable {
		LuaTable t = new LuaTable();

		for (int i = 1; i <= 32; ++i) {
			LuaValue v = LuaString.valueOf("Test Value! " + i);
			OperationHelper.setTable(state, t, ValueFactory.valueOf(i), v);
			assertEquals(i, OperationHelper.length(state, t).toInteger());
			assertEquals(i, t.maxn(), 1e-10);
		}
	}

	@Test
	public void testOutOfOrderlen() throws LuaError, UnwindThrowable {
		LuaTable t = new LuaTable();

		for (int j = 8; j < 32; j += 8) {
			for (int i = j; i > 0; --i) {
				OperationHelper.setTable(state, t, ValueFactory.valueOf(i), LuaString.valueOf("Test Value! " + i));
			}
			assertEquals(j, OperationHelper.length(state, t).toInteger());
			assertEquals(j, t.maxn(), 1e-10);
		}
	}

	@Test
	public void testStringKeyslen() throws LuaError, UnwindThrowable {
		LuaTable t = new LuaTable();

		for (int i = 1; i <= 32; ++i) {
			OperationHelper.setTable(state, t, ValueFactory.valueOf("str-" + i), LuaString.valueOf("String Key Test Value! " + i));
			assertEquals(0, OperationHelper.length(state, t).toInteger());
			assertEquals(0, t.maxn(), 1e-10);
		}
	}

	@Test
	public void testMixedKeyslen() throws LuaError, UnwindThrowable {
		LuaTable t = new LuaTable();

		for (int i = 1; i <= 32; ++i) {
			OperationHelper.setTable(state, t, ValueFactory.valueOf("str-" + i), LuaString.valueOf("String Key Test Value! " + i));
			OperationHelper.setTable(state, t, ValueFactory.valueOf(i), LuaString.valueOf("Int Key Test Value! " + i));
			assertEquals(i, OperationHelper.length(state, t).toInteger());
			assertEquals(i, t.maxn(), 1e-10);
		}
	}

	private void compareLists(LuaTable t, Vector<LuaString> v) throws LuaError, UnwindThrowable {
		int n = v.size();
		assertEquals(v.size(), OperationHelper.length(state, t).toInteger());
		for (int j = 0; j < n; j++) {
			Object vj = v.elementAt(j);
			Object tj = OperationHelper.getTable(state, t, ValueFactory.valueOf(j + 1)).toString();
			vj = vj.toString();
			assertEquals(vj, tj);
		}
	}

	@Test
	public void testInsertBeginningOfList() throws LuaError, UnwindThrowable {
		LuaTable t = new LuaTable();
		Vector<LuaString> v = new Vector<>();

		for (int i = 1; i <= 32; ++i) {
			LuaString test = LuaString.valueOf("Test Value! " + i);
			t.insert(1, test);
			v.insertElementAt(test, 0);
			compareLists(t, v);
		}
	}

	@Test
	public void testInsertEndOfList() throws LuaError, UnwindThrowable {
		LuaTable t = new LuaTable();
		Vector<LuaString> v = new Vector<>();

		for (int i = 1; i <= 32; ++i) {
			LuaString test = LuaString.valueOf("Test Value! " + i);
			t.insert(0, test);
			v.insertElementAt(test, v.size());
			compareLists(t, v);
		}
	}

	@Test
	public void testInsertMiddleOfList() throws LuaError, UnwindThrowable {
		LuaTable t = new LuaTable();
		Vector<LuaString> v = new Vector<>();

		for (int i = 1; i <= 32; ++i) {
			LuaString test = LuaString.valueOf("Test Value! " + i);
			int m = i / 2;
			t.insert(m + 1, test);
			v.insertElementAt(test, m);
			compareLists(t, v);
		}
	}

	private static void prefillLists(LuaTable t, Vector<LuaString> v) {
		for (int i = 1; i <= 32; ++i) {
			LuaString test = LuaString.valueOf("Test Value! " + i);
			t.insert(0, test);
			v.insertElementAt(test, v.size());
		}
	}

	@Test
	public void testRemoveBeginningOfList() throws LuaError, UnwindThrowable {
		LuaTable t = new LuaTable();
		Vector<LuaString> v = new Vector<>();
		prefillLists(t, v);
		for (int i = 1; i <= 32; ++i) {
			t.remove(1);
			v.removeElementAt(0);
			compareLists(t, v);
		}
	}

	@Test
	public void testRemoveEndOfList() throws LuaError, UnwindThrowable {
		LuaTable t = new LuaTable();
		Vector<LuaString> v = new Vector<>();
		prefillLists(t, v);
		for (int i = 1; i <= 32; ++i) {
			t.remove(0);
			v.removeElementAt(v.size() - 1);
			compareLists(t, v);
		}
	}

	@Test
	public void testRemoveMiddleOfList() throws LuaError, UnwindThrowable {
		LuaTable t = new LuaTable();
		Vector<LuaString> v = new Vector<>();
		prefillLists(t, v);
		for (int i = 1; i <= 32; ++i) {
			int m = v.size() / 2;
			t.remove(m + 1);
			v.removeElementAt(m);
			compareLists(t, v);
		}
	}

}

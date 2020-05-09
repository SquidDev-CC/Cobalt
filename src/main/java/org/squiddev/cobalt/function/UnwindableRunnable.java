package org.squiddev.cobalt.function;

import org.squiddev.cobalt.LuaError;
import org.squiddev.cobalt.UnwindThrowable;
import org.squiddev.cobalt.debug.DebugFrame;

@FunctionalInterface
public interface UnwindableRunnable {
    void run(DebugFrame di) throws LuaError, UnwindThrowable;
}

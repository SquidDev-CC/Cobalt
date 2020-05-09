package org.squiddev.cobalt.function;

import org.squiddev.cobalt.LuaError;
import org.squiddev.cobalt.UnwindThrowable;
import org.squiddev.cobalt.debug.DebugFrame;

@FunctionalInterface
public interface UnwindableCallable {
    void call(DebugFrame di, EvalCont cont) throws LuaError, UnwindThrowable;
}

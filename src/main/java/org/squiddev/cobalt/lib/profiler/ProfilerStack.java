package org.squiddev.cobalt.lib.profiler;

import org.squiddev.cobalt.LuaError;
import org.squiddev.cobalt.debug.DebugFrame;

public final class ProfilerStack {
	public static final int MAX_SIZE = 512;

	public static final int DEFAULT_SIZE = 8;

	private static final ProfilerFrame[] EMPTY = new ProfilerFrame[0];

	/**
	 * The top function
	 */
	public int top = -1;

	private ProfilerFrame[] stack = EMPTY;

	/**
	 * Push a new debug info onto the stack
	 *
	 * @return The created info
	 */
	private ProfilerFrame nextFrame() {
		int top = this.top + 1;

		ProfilerFrame[] frames = stack;
		int length = frames.length;
		if (top >= length) {
			if (top >= MAX_SIZE) throw new LuaError("stack overflow");

			int newSize = length == 0 ? DEFAULT_SIZE : length + (length / 2);
			ProfilerFrame[] f = new ProfilerFrame[newSize];
			System.arraycopy(frames, 0, f, 0, frames.length);
			frames = stack = f;
		}

		this.top = top;
		ProfilerFrame frame = frames[top];
		if (frame == null) frame = frames[top] = new ProfilerFrame((short) (top + 1));
		return frame;
	}

	private void pause() {
		ProfilerFrame[] stack = this.stack;
		int top = this.top;
		if (top < 0) return;
		long time = System.nanoTime();

		for (int i = top; i >= 0; i--) {
			stack[i].computeTotalTime(time);
		}
	}

	public void resume() {
		ProfilerFrame[] stack = this.stack;
		int top = this.top;
		if (top < 0) return;

		long time = System.nanoTime();

		stack[top].functionLocalTimeMarker = time;
		for (int i = top; i >= 0; i--) {
			stack[i].functionTotalTimeMarker = time;
		}
	}

	public void enter(DebugFrame dFrame) {
		long time = System.nanoTime();
		int top = this.top;
		if (top >= 0) {
			stack[top].computeLocalTime(time);
		}

		ProfilerFrame frame = nextFrame();
		frame.setup(dFrame);
		frame.functionLocalTimeMarker = time;
		frame.functionTotalTimeMarker = time;
	}

	public ProfilerFrame leave(boolean resume) {
		ProfilerFrame[] stack = this.stack;
		int top = this.top;
		if (top < 0) return null;
		long time = System.nanoTime();

		ProfilerFrame topFrame = stack[top];
		this.top = top - 1;

		pause();
		topFrame.computeLocalTime(time);
		topFrame.computeTotalTime(time);

		if (resume && top > 0) resume();

		stack[top] = null;
		return topFrame;
	}
}

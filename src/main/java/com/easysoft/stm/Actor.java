package com.easysoft.stm;

/**
 * Code run by a transition. Implement this on the classes referenced from configuration; the
 * builder binds the configured {@code methodName} on it, or calls {@link #handle} when no method is
 * named. The return value becomes the context's last result and drives {@code <Case>} routing.
 * Conditions must return {@code Boolean}.
 *
 * @param <T> the application context type
 */
public interface Actor<T extends STMContext> {
	public default Object handle(T request) {
		return null;
	}
}

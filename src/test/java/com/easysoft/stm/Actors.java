package com.easysoft.stm;

import java.util.function.Function;

/**
 * Factory for small anonymous {@link Actor}s used by the engine tests. ({@code Actor} has no
 * abstract method, so it cannot be implemented with a lambda directly.)
 */
public final class Actors {

	private Actors() {
	}

	/** Actor that records {@code name} in the context log and returns {@code result}. */
	public static Actor<STMContext> logging(String name, Object result) {
		return of(name, ctx -> {
			((TestContext) ctx).log.add(name);
			return result;
		});
	}

	/** Actor that records {@code name} and then throws. */
	public static Actor<STMContext> throwing(String name, RuntimeException ex) {
		return of(name, ctx -> {
			((TestContext) ctx).log.add(name);
			throw ex;
		});
	}

	public static Actor<STMContext> of(String name, Function<STMContext, Object> body) {
		return new Actor<STMContext>() {
			@Override
			public Object handle(STMContext context) {
				return body.apply(context);
			}

			@Override
			public String toString() {
				return name;
			}
		};
	}
}

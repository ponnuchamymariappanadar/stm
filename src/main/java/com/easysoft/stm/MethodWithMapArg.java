package com.easysoft.stm;

import java.lang.invoke.CallSite;
import java.util.Map;

/**
 * Calls {@code method(ctx, Map<String, String> input)} with the map written as {@code input} in the
 * flow YAML, for example {@code input: { limit: 10000, currency: USD }}. Every value is text, as
 * written ({@code "10000"}, {@code "USD"}), and the map is read-only.
 */
public class MethodWithMapArg implements Actor<STMContext> {

	protected final TriFunction<Actor<? extends STMContext>, STMContext, Object, Object> function;
	protected final Actor<? extends STMContext> actor;
	protected final Map<String, String> argument;
	protected final String toString;

	@SuppressWarnings("unchecked")
	public MethodWithMapArg(Actor<? extends STMContext> actor, CallSite callSite, String methodName, Map<String, String> argument) throws Throwable {
		this.function = (TriFunction<Actor<? extends STMContext>, STMContext, Object, Object>) callSite.getTarget().invokeExact();
		this.actor = actor;
		this.argument = argument;
		this.toString = actor + MethodActor.METHOD_SEPERATOR + methodName + "@" + argument;
	}

	@Override
	public Object handle(STMContext context) {
		return function.apply(actor, context, argument);
	}

	/** The map passed to the method. */
	public Map<String, String> getArgument() {
		return argument;
	}

	@SuppressWarnings("unchecked")
	public <T extends STMContext> Actor<T> getActualActor() {
		return (Actor<T>) actor;
	}

	@Override
	public String toString() {
		return toString;
	}
}

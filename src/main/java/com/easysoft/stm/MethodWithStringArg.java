package com.easysoft.stm;

import java.lang.invoke.CallSite;

public class MethodWithStringArg implements Actor<STMContext> {

	protected final TriFunction<Actor<? extends STMContext>, STMContext, Object, Object> function;
	protected final Actor<? extends STMContext> actor;
	protected final String argument;
	protected final String toString;

	public MethodWithStringArg(Actor<? extends STMContext> actor, CallSite callSite, String methodName, String argument) throws Throwable {
		this.function = (TriFunction<Actor<? extends STMContext>, STMContext, Object, Object>) callSite.getTarget().invokeExact();
		this.actor = actor;
		this.argument = argument;
		this.toString = actor + MethodActor.METHOD_SEPERATOR + methodName + "@" + argument;
	}

	@Override
	public Object handle(STMContext context) {
		return function.apply(actor, context, argument);
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

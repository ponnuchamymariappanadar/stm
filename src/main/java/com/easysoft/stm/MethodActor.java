package com.easysoft.stm;

import java.lang.invoke.CallSite;
import java.util.function.BiFunction;

public class MethodActor implements Actor<STMContext> {
    public static final String METHOD_SEPERATOR = "#";
	protected final BiFunction<Actor<? extends STMContext>, STMContext, Object> function;
	protected final Actor<? extends STMContext> actor;
	protected final String toString;
	
	public MethodActor(Actor<? extends STMContext> transition, CallSite callSite, String methodName) throws Throwable {
		this.function = (BiFunction<Actor<? extends STMContext>, STMContext, Object>) callSite.getTarget().invokeExact();
		this.actor = transition;
		this.toString = transition.toString() + METHOD_SEPERATOR + methodName;
	}

	@Override
	public Object handle(STMContext context) {
		return function.apply(actor, context);
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

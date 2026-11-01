package com.easysoft.stm;

import java.lang.invoke.CallSite;
import java.util.function.BiFunction;

public class MethodWithLookupArg implements Actor<STMContext> {

	protected final TriFunction<Actor<? extends STMContext>, STMContext, Object, Object> function;
	protected final BiFunction<Actor<? extends STMContext>, STMContext, Object> lookUpArgFunction;
	protected final Actor<? extends STMContext> actor;
	protected final Actor<? extends STMContext> lookupObject;
	protected final String toString;
	
	public MethodWithLookupArg(Actor<? extends STMContext> actor, CallSite callSite, Actor<? extends STMContext> lookupObject,
			CallSite lookupArgCallSite, String methodName, String lookupMethod) throws Throwable {
		this.function = (TriFunction<Actor<? extends STMContext>, STMContext, Object, Object>) callSite.getTarget().invokeExact();
		this.lookUpArgFunction = (BiFunction<Actor<? extends STMContext>, STMContext, Object>) lookupArgCallSite.getTarget().invokeExact();
		this.actor = actor;
		this.lookupObject = lookupObject;
		this.toString = actor + MethodActor.METHOD_SEPERATOR + methodName + "(" + lookupMethod + ")";
	}

	@Override
	public Object handle(STMContext context) {
		return function.apply(actor, context, lookUpArgFunction.apply(lookupObject, context));
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

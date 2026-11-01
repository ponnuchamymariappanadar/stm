package com.easysoft.stm;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.easysoft.stm.STM.STMEvent;

/**
 * One step of a flow: <em>in {@code fromState}, when {@code fromEvent}</em>, run the actor, then
 * continue with {@code fire} ({@code toEvent}) in {@code to} ({@code toState}).
 * <p>
 * Subclasses: {@link PlaceHolderTransition} (no actor), {@link ConditionOnly},
 * {@link ConditionAndActor} and {@link StmEventBasedTransition}. Exceptions thrown by the actor
 * route to the {@linkplain #getOnErrorTransition() error transition}.
 */
public class Transition {
	public Logger logger = LoggerFactory.getLogger(Transition.class);
	State fromState;
	Event fromEvent;

	State toState;
	Event toEvent;

	State configuredFromState;
	Event configuredFromEvent;

	State configuredToState;
	Event configuredToEvent;

	int hashCode;

	STMEvent nextStmEvent = STMEvent.NEXT;
	Actor<STMContext> actor;
	Transition onErrorTransition;
	Transition onResultTransition;
	String toStringValue;
	String toLongStringValue;
	boolean excludeInfo = false;

	public Transition(State fromState, Event fromEvent, State toState, Event toEvent, STMEvent next, Transition errorTransition) {

		this.fromState = fromState;
		this.toState = toState;
		this.fromEvent = fromEvent;
		this.toEvent = toEvent;
		this.configuredFromState = fromState;
		this.configuredFromEvent = fromEvent;
		this.configuredToState = toState;
		this.configuredToEvent = toEvent;
		this.onErrorTransition = errorTransition;
		if (next != null) {
			this.nextStmEvent = next;
		}
		StringBuilder builder = new StringBuilder();
		builder.append(fromState.getId());
		builder.append(":");
		builder.append(fromEvent.getId());
		hashCode = builder.toString().hashCode();
		toStringValue = formatShortString().toString();
		toLongStringValue = formatString().toString();
	}

	public StringBuilder formatShortString() {
		return formatShortString(actor, fromState, fromEvent);
	}

	public static StringBuilder formatShortString(Object actor, State fromState, Event fromEvent) {
		StringBuilder sBuilder = new StringBuilder();
		sBuilder.append(fromEvent);
		sBuilder.append("<-");
		sBuilder.append(fromState);
		sBuilder.append("(");
		abbreviateHandlerName(actor, sBuilder);
		sBuilder.append(")");
		return sBuilder;
	}

	public static void abbreviateHandlerName(Object actor, StringBuilder sBuilder) {
		if (actor != null) {
			String className = abbreivateHandlerName(actor);
			sBuilder.append(className);
		}
	}

	public static String abbreivateHandlerName(Object actor) {
		String className = actor.toString();
		String methodName = "";
		if (className.contains(MethodActor.METHOD_SEPERATOR)) {
		   methodName = MethodActor.METHOD_SEPERATOR + StringUtils.substringAfter(className, MethodActor.METHOD_SEPERATOR);
		   className = StringUtils.substringBefore(className, MethodActor.METHOD_SEPERATOR);
		}
		if (className.contains(".")) {
			className = StringUtils.substringAfterLast(className, ".") + methodName;
		}
		return className;
	}

	public StringBuilder formatString() {
		StringBuilder sBuilder = formatString("' handler='", actor, fromState.toString(), 
				fromEvent.toString(), toState.toString(), toEvent.toString(), nextStmEvent, onErrorTransition, onResultTransition);
		sBuilder.append("'/>");
		return sBuilder;
	}

	public static StringBuilder formatString(String type, Object object, String fromState, String fromEvent, String toState, String toEvent, STMEvent nextStmEvent,
			Transition onErrorTransition, Transition onResultTransition) {
		StringBuilder sBuilder = new StringBuilder();
		sBuilder.append("<transition state='");
		sBuilder.append(fromState);
		sBuilder.append("' when='");
		sBuilder.append(fromEvent);
		sBuilder.append("' to='");
		sBuilder.append(toState);

		sBuilder.append("' fire='");
		sBuilder.append(toEvent);
		sBuilder.append("' stmevent='");
		sBuilder.append(nextStmEvent);
		if (type != null) {
		  sBuilder.append(type);
		}
		if (object != null) {
		  sBuilder.append(object);
		}
		if (onErrorTransition != null) {
			sBuilder.append("' OnError to='");
			sBuilder.append(onErrorTransition.getToState());
			sBuilder.append("' fire='");
			sBuilder.append(onErrorTransition.getToEvent());
		}
		if (onResultTransition != null) {
			sBuilder.append("' OnResult to='");
			sBuilder.append(onResultTransition.getToState());
			sBuilder.append("' fire='");
			sBuilder.append(onResultTransition.getToEvent());
		}
		return sBuilder;
	}

	public State getFromState() {
		return fromState;
	}

	public State getToState() {
		return toState;
	}

	public Event getFromEvent() {
		return fromEvent;
	}

	@Override
	public int hashCode() {
		return hashCode;
	}

	public void setActor(Actor<STMContext> handler) {
		actor = handler;
		toStringValue = formatShortString().toString();
		toLongStringValue = formatString().toString();
		logger = getLogger(logger, actor);
	}

	public static <T> Logger getLogger(Logger logger, Actor<?> actor) {
		if (actor instanceof MethodActor) {
			logger = LoggerFactory.getLogger(((MethodActor) actor).getActualActor().getClass());
		} else if (actor instanceof MethodWithLookupArg) {
			logger = LoggerFactory.getLogger(((MethodWithLookupArg) actor).getActualActor().getClass());
		} else if (actor instanceof MethodWithStringArg) {
			logger = LoggerFactory.getLogger(((MethodWithStringArg) actor).getActualActor().getClass());
		}else if (actor instanceof Choice) {
			logger = LoggerFactory.getLogger(((Choice<?>) actor).getActualActor().getClass());
		} 
		else {
			if (actor != null) {
				logger = LoggerFactory.getLogger(actor.getClass());
			}
		}
		return logger;
	}

	public Actor<STMContext> getTransitionHandle() {
		return actor;
	}

	@Override
	public boolean equals(Object obj) {
		if (!(obj instanceof Transition)) {
			return false;
		}
		Transition fObj = (Transition) obj;
		return fObj.fromState.getId() == fromState.getId() && fObj.fromEvent.getId() == fromEvent.getId();
	}

	@Override
	public String toString() {
		return toStringValue;
	}

	public static String formatTranstion(String fromState, String fromEvent, String toState, String toEvent, STMEvent nextStmEvent) {
		StringBuilder sBuilder = formatString(null, null, fromState, fromEvent, toState, toEvent, nextStmEvent, null, null);
		sBuilder.append("'/>");
		return sBuilder.toString();
	}

	public Event getToEvent() {
		return toEvent;
	}

	public STMEvent getNextSTMEvent() {
		return nextStmEvent;
	}

	public Transition getOnErrorTransition() {
		return onErrorTransition;
	}

	public Transition getOnResultTransition() {
		return onResultTransition;
	}

	public STMEvent execute(STMContext fsmContext, StatefulContext statefulContext) {
		try {
			Object result = actor.handle(fsmContext);
			if (result instanceof STMEvent) {
				return (STMEvent) result;
			}
			statefulContext.setLastResult(result);
		} catch (Exception e) {
			return error(statefulContext, e);
		}
		return statefulContext.getNextSTMEvent();
	}

	STMEvent error(StatefulContext context, Exception ex) {
		context.setNextTransition(onErrorTransition);
		STMEvent event = STMEvent.NEXT;
		if (context.isError()) {
			logger.info("Already running on error context and thorwing exception again, so breaking away");
			event = STMEvent.STOP;
		}
		context.setException(ex);
		logger.info(toStringValue, ex);
		return event;
	}

	public void setOnErrorTransition(Transition onErrorTransition) {
		this.onErrorTransition = onErrorTransition;
	}

	public void setOnResultTransition(Transition onResultTransition) {
		this.onResultTransition = onResultTransition;
	}

	public State getConfiguredFromState() {
		return configuredFromState;
	}

	public void setConfiguredFromState(State configuredFromState) {
		this.configuredFromState = configuredFromState;
	}

	public Event getConfiguredFromEvent() {
		return configuredFromEvent;
	}

	public void setConfiguredFromEvent(Event configuredFromEvent) {
		this.configuredFromEvent = configuredFromEvent;
	}

	public State getConfiguredToState() {
		return configuredToState;
	}

	public void setConfiguredToState(State configuredToState) {
		this.configuredToState = configuredToState;
	}

	public Event getConfiguredToEvent() {
		return configuredToEvent;
	}

	public void setConfiguredToEvent(Event configuredToEvent) {
		this.configuredToEvent = configuredToEvent;
	}

	public Actor<STMContext> getActor() {
		return actor;
	}

	public String getToLongStringValue() {
		return toLongStringValue;
	}

	public boolean isExcludeInfo() {
		return excludeInfo;
	}

	public void setExcludeInfo(boolean excludeInfo) {
		this.excludeInfo = excludeInfo;
	}
}

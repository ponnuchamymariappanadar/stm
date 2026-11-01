package com.easysoft.stm;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The state-machine engine. Runs transitions one after another, following each transition's
 * {@code to}/{@code fire} (or a route chosen by a condition, case, call/return or error), until a
 * transition reports {@link STMEvent#STOP}.
 * <p>
 * One instance can run many flows concurrently, each with its own context from
 * {@link #createContext()} or {@link #createContextWithTrace()}.
 *
 * @param <T> the application context type
 */
public class STMStateful<T extends STMContext> implements STM<T> {

	private static Logger logger = LoggerFactory.getLogger(STMStateful.class);

	final StateCollection states;
	final EventCollection events;
	final State defaultStartState;
	final Event defaultStartEvent;

	private STMStateful<STMContext> me;
	private STMStateful<STMContext> parent;
	boolean debug = false;

	final int id;
	final String name;
	int maxStateChangeAllowed = 100;

	@SuppressWarnings("unchecked")
	public STMStateful(int id, String name, State defaultStartState, Event defaultStartEvent, StateCollection states, EventCollection events) {
		this.states = states;
		this.events = events;
		this.id = id;
		this.name = name;
		this.defaultStartState = defaultStartState;
		this.defaultStartEvent = defaultStartEvent;
		this.me = (STMStateful<STMContext>) this;
	}

	public STMStateful(STMStateful<STMContext> parent) {
		this(parent.id, parent.name, parent.defaultStartState, parent.defaultStartEvent, parent.states, parent.events);
		this.parent = parent;
	}

	@Override
	public State getDefaultStartState() {
		return defaultStartState;
	}

	@Override
	public Event getDefaultStartEvent() {
		return defaultStartEvent;
	}

	@Override
	public int getId() {
		return id;
	}

	@Override
	public Event getEvent(String event) {
		return events.getEvent(event);
	}

	@Override
	public State getState(String state) {
		return states.getState(state);
	}

	@Override
	public STMEvent start(T fsmContext, Transition transition) {
		StatefulContext context = fsmContext.getSTMContext();
		synchronized (context) {
			context.setNextTransition(null);
			Throwable exception = null;
			STMEvent stmEvent = null;
			try  {
			   stmEvent = callSTMStates(fsmContext, transition, context, STMEvent.START);
			}
			catch(Throwable e) {
				logger.info("unhandled exception", e);
				exception = e;
			}
			if (stmEvent == STMEvent.CHANGE_STM) {
				STMStateful<STMContext> newStm = context.getNextStm();
				// the transition passed to startWithNewSTM(), where the new machine starts
				Transition newStmTransition = context.getNextTransition();
				context.setNextSTM(null);
				context.setNextTransition(null);
				StatefulContext newContext = null;
				if (context.getDebug()) {
					newContext = newStm.createContextWithTrace();
				} else {
					newContext = newStm.createContext();
				}
				newContext.setLastResult(context.getLastResult());
				fsmContext.setSTMContext(newContext);
			    context.setLastResult(null);
				stmEvent = newStm.callSTMStates(fsmContext, newStmTransition, newContext, STMEvent.START);
				context.setLastResult(newContext.getLastResult());
				stmEvent = callEndTransition(fsmContext, transition, context, stmEvent, exception);
			} else {
				context.reset(transition);
				stmEvent = callEndTransition(fsmContext, transition, context, stmEvent, exception);
			}
			return stmEvent;
		}
	}

	private STMEvent callEndTransition(T fsmContext, Transition transition, StatefulContext context, STMEvent stmEvent, Throwable exception) {
		if (context.getEndTransition() != null) {
			context.setNextTransition(null);
			if (exception != null) {
				context.setException(new Exception(exception));
				context.clearError();
			}
			stmEvent = callSTMStates(fsmContext, context.getEndTransition(), context, STMEvent.START);
			context.reset(transition);
		}
		return stmEvent;
	}

	@Override
	public STMEvent startWithNewContext(T fsmContext, T newFsmContext, Transition transition) {
		StatefulContext context = fsmContext.getSTMContext();
		context.setNextTransition(transition);
		context.setNextContext(newFsmContext);
		context.setNextSTMEvent(STMEvent.NEXT);
		return STMEvent.NEXT;
	}

	@Override
	public STMEvent startWithNewSTM(STMStateful<STMContext> newStm, T fsmContext, Transition transition) {
		StatefulContext context = fsmContext.getSTMContext();
		context.setNextTransition(transition);
		context.setNextSTM(newStm);
		context.setNextSTMEvent(STMEvent.CHANGE_STM);
		return STMEvent.CHANGE_STM;
	}

	@Override
	public STMEvent next(T fsmContext, Transition transition) {
		StatefulContext context = fsmContext.getSTMContext();
		context.setNextTransition(transition);
		context.setNextSTMEvent(STMEvent.NEXT);
		return STMEvent.NEXT;
	}

	@SuppressWarnings("unchecked")
	private STMEvent callSTMStates(T fsmContext, Transition transition, StatefulContext context, STMEvent initResult) {

		Transition currentTransition = transition;

		Transition nextTransition = null;
		STMEvent nextEvent = initResult;
		int stateChangeCount = 0;

		while (currentTransition != null) {

			context.setCurrentTransition(currentTransition);
			nextEvent = currentTransition.execute(fsmContext, context);
			if (nextEvent == STMEvent.NEXT) {
				nextTransition = context.getNextTransition();
				if (nextTransition == null) {
					nextTransition = currentTransition.getToState().getTransition(currentTransition.getToEvent());
				} else {
					context.setNextTransition(null);
				}
			} else if (nextEvent == STMEvent.STOP) {
				logger.info("STOP event");
				break;
			} else if (nextEvent == STMEvent.START) {
				STMContext nextContext = context.getNextContext();
				if (nextContext != null) {
					fsmContext = (T) nextContext;
					context.setNextContext(null);
				} else {
					nextTransition = currentTransition.getToState().getTransition(currentTransition.getToEvent());
					context.reset();
				}
			} else if (nextEvent == STMEvent.CHANGE_STM) {
				break;
			}
			if (stateChangeCount > maxStateChangeAllowed) {
				logger.warn("number of state changed is more than allowed, possiblly looping " + currentTransition + ",next transition=" + nextTransition, new Exception());
				context.reset();
				break;
			}
			currentTransition = nextTransition;
			stateChangeCount++;
		}
		context.endState(stateChangeCount, nextEvent);

		return nextEvent;
	}

	@Override
	public int hashCode() {
		return id;
	}

	@SuppressWarnings("rawtypes")
	@Override
	public boolean equals(Object obj) {
		if (!(obj instanceof STMStateful))
			return false;
		return ((STMStateful) obj).id == id;
	}

	public StatefulContext createContext() {
		return me.createContextLocal();
	}

	protected StatefulContext createContextLocal() {
		return new StatefulContext(me);
	}

	public StatefulContext createContextWithTrace() {
		return new StatefulContextWithTrace(me);
	}

	public StatefulContext createContextWithTrace(STMTraceWriter writer) {
		return new StatefulContextWithTrace(me, writer);
	}

	public void setDebug(boolean debug) {
		if (this.debug != debug) {
			if (debug) {
				this.debug = debug;
				me = new STMStateful<STMContext>(me) {
					public StatefulContext createContextLocal() {
						return new StatefulContextWithTrace(me);
					}
				};
			} else {
				this.debug = debug;
				me = me.parent;
			}
		}
	}

	public StateCollection getStates() {
		return states;
	}

	public boolean getDebug() {
		return debug;
	}

	public int getMaxStateChangeAllowed() {
		return maxStateChangeAllowed;
	}

	public void setMaxStateChangeAllowed(int maxStateChangeAllowed) {
		this.maxStateChangeAllowed = maxStateChangeAllowed;
	}

	public String getName() {
		return name;
	}

}

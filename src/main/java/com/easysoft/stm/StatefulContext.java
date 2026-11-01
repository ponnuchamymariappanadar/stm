package com.easysoft.stm;

import java.util.ArrayDeque;
import java.util.Deque;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.easysoft.stm.STM.STMEvent;

/**
 * Per-run engine state: current and next transition, last actor result, error state and the
 * call stack used by {@code <Call>}/{@code <Return>}. Obtain one from
 * {@link STMStateful#createContext()} and keep it in your {@link STMContext}.
 * <p>
 * Use {@link #start(STMContext)} to run a flow, and {@link #next(STMContext, String, String)} from
 * inside an actor to jump to a different transition.
 */
public class StatefulContext {
	static Logger logger = LoggerFactory.getLogger(StatefulContext.class);
	Transition currentTransition;
	STMStateful<STMContext> stm;

	Transition nextTransition;
	Transition endTransition;
	Deque<Transition> stateCallStacks = new ArrayDeque<Transition>(1);
	boolean isError;
	Object lastResult;
	STMEvent nextSTMEvent;
	Exception exception;
	boolean debug;
	STMContext nextContext;
	STMStateful<STMContext> nextStm;

	public StatefulContext(STMStateful<STMContext> stm) {
		this.stm = stm;
	}

	public State getCurrentState() {
		return currentTransition.getFromState();
	}

	void setCurrentTransition(Transition transition) {
		this.currentTransition = transition;
		this.nextSTMEvent = transition.getNextSTMEvent();
	}

	public STMEvent getNextSTMEvent() {
		return nextSTMEvent;
	}

	public void addCallStateTransition(Transition transition) {
		stateCallStacks.push(transition);
	}

	public Transition popCallStateTransition() {
		if (stateCallStacks.isEmpty()) {
			return null;
		}
		return stateCallStacks.pop();
	}

	void setNextSTMEvent(STMEvent nextSTMEvent) {
		this.nextSTMEvent = nextSTMEvent;
	}

	public STMEvent start(STMContext stmContext) {
		return start(stmContext, stmContext.getSTMContext().getStm().getDefaultStartState(), stmContext.getSTMContext().getStm().getDefaultStartEvent());
	}

	public STMEvent start(STMContext stmContext, String state, String event) {
		State s = stm.getState(state);
		Event e = stm.getEvent(event);
		validateStateAndEvent(s, e);
		return startLocal(stmContext, s, e);
	}

	public STMEvent start(STMContext stmContext, State state, Event event) {
		validateStateAndEvent(state, event);
		return startLocal(stmContext, state, event);
	}

	private void validateStateAndEvent(State state, Event event) {
		if (state == null) {
			throw new InvalidStateException("State null");
		}
		if (event == null) {
			throw new InvalidEventException("event nullt");
		}
	}

	private STMEvent startLocal(STMContext stmContext, State state, Event event) {
		Transition transition = state.getTransition(event);
		if (transition == null) {
			throw new InvalidTransitionException("state and event combination not exist for start " + state + " " + event);
		}
		return stm.start(stmContext, transition);
	}

	public STMEvent start(STMContext stmContext, Transition transition) {
		return stm.start(stmContext, transition);
	}

	public STMEvent next(STMContext stmContext, String event) {
		Event e = stm.getEvent(event);
		if (e == null) {
			throw new InvalidEventException("event not exist for next " + event);
		}
		return next(stmContext, e);
	}

	public STMEvent next(STMContext stmContext, Event event) {
		if (currentTransition.getFromState() == null) {
			throw new InvalidStateException("current state null for next");
		}
		if (event == null) {
			throw new InvalidEventException("event null for next");
		}
		return nextLocal(stmContext, currentTransition.getFromState(), event);
	}

	public STMEvent next(STMContext stmContext, String state, String event) {
		State s = stm.getState(state);
		Event e = stm.getEvent(event);
		validateStateAndEvent(s, e);
		return nextLocal(stmContext, s, e);
	}

	public STMEvent next(STMContext stmContext, State state, Event event) {
		validateStateAndEvent(state, event);
		return nextLocal(stmContext, state, event);
	}

	private STMEvent nextLocal(STMContext stmContext, State state, Event event) {
		Transition transition = state.getTransition(event);
		if (transition == null) {
			throw new InvalidTransitionException("state and event combination not exist for next " + state + " " + event);
		}
		return stm.next(stmContext, transition);
	}

	public boolean isError() {
		return isError;
	}
	
	public void clearError() {
		isError = false;
	}

	public void reset() {
		isError = false;
		exception = null;
		nextTransition = null;
	}

	public void reset(Transition transition) {
		reset();
		nextTransition = transition;
	}

	public Transition getNextTransition() {
		return nextTransition;
	}

	public void setNextTransition(Transition transition) {
		nextTransition = transition;
	}

	public Transition getCurrentTransition() {
		return this.currentTransition;
	}

	public Exception getException() {
		return exception;
	}

	public void setStm(STMStateful<STMContext> flow) {
		this.stm = flow;
	}

	public STM<STMContext> getStm() {
		return stm;
	}

	public void setException(Exception exception) {
		this.exception = exception;
		this.isError = true;
	}

	@SuppressWarnings("unchecked")
	public <T> T getLastResult() {
		return (T) lastResult;
	}

	public void setLastResult(Object result) {
		this.lastResult = result;
	}

	public boolean getDebug() {
		return debug;
	}

	public void endState(int stateChangeCount, STMEvent nextEvent) {

	}

	public void setNextContext(STMContext newFsmContext) {
		this.nextContext = newFsmContext;
	}

	public STMContext getNextContext() {
		return this.nextContext;
	}

	public void setNextSTM(STMStateful<STMContext> nextStm) {
		this.nextStm = nextStm;
	}

	public STMStateful<STMContext> getNextStm() {
		return nextStm;
	}

	public STMTraceWriter getTraceWriter() {
		return null;
	}

	public void setTraceWriter(STMTraceWriter traceWriter) {

	}

	public Transition getEndTransition() {
		return endTransition;
	}

	public void setEndTransition(Transition endTransition) {
		this.endTransition = endTransition;
	}
}

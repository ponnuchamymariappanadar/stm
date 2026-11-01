package com.easysoft.stm.config;

import com.easysoft.stm.Event;
import com.easysoft.stm.STMContext;
import com.easysoft.stm.STMStateful;
import com.easysoft.stm.STMTraceWriter;
import com.easysoft.stm.State;
import com.easysoft.stm.StatefulContext;
import com.easysoft.stm.Transition;

import jakarta.annotation.PostConstruct;

/**
 * Convenience bean that starts a named machine from a fixed state and event:
 * {@code new StmStarter("order.flow", "order.s", "order.received")}.
 */
public class StmStarter {

	protected STMStateful<STMContext> stmStateFul;

	protected String fsmName;
	protected Event startEventObj;
	protected State startStateObj;

	protected String startState;
	protected String startEvent;

	protected Transition startTransition;

	@PostConstruct
	public void init() {
		try {
			STMFactory.addConsumer(fsmName, this::setStmStateFul);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}

	}

	public StmStarter(String fsmName, String startState, String startEvent) {
		this.fsmName = fsmName;
		this.startState = startState;
		this.startEvent = startEvent;
	}

	public void start(STMContext context) {
		stmStateFul.start(context, startTransition);
	}
	
	public void switchContext(STMContext current, STMContext context, StmStarter starter) {
		current.getSTMContext().getStm().startWithNewContext(current, context, starter.getStartTransition());
	}

	public StatefulContext createStmStateful() {
		return stmStateFul.createContext();
	}
	
	public StatefulContext createStmStatefulWithDebug(STMTraceWriter traceWriter) {
		return stmStateFul.createContextWithTrace(traceWriter);
	}
	
	public StatefulContext createStmStatefulWithDebug() {
		return stmStateFul.createContextWithTrace();
	}

	public String getStartState() {
		return startState;
	}

	public void setStartState(String startState) {
		this.startState = startState;
	}

	public String getStartEvent() {
		return startEvent;
	}

	public void setStartEvent(String startEvent) {
		this.startEvent = startEvent;
	}

	public Event getStartEventObj() {
		return startEventObj;
	}

	public State getStartStateObj() {
		return startStateObj;
	}

	public STMStateful<STMContext> getStmStateFul() {
		return stmStateFul;
	}

	public void setStmStateFul(STMStateful<STMContext> stmStateFul) {
		this.stmStateFul = stmStateFul;
		startStateObj = stmStateFul.getState(startState);
		startEventObj = stmStateFul.getEvent(startEvent);
		startTransition = startStateObj.getTransition(startEventObj);
	}

	public String getFsmName() {
		return fsmName;
	}

	public void setFsmName(String fsmName) {
		this.fsmName = fsmName;
	}

	public Transition getStartTransition() {
		return startTransition;
	}
}

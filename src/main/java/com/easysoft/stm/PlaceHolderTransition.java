package com.easysoft.stm;

import com.easysoft.stm.STM.STMEvent;

/**
 * A transition without an actor or condition that only forwards to its {@code to}/{@code fire}.
 * The builder collapses chains of these at build time.
 */
public class PlaceHolderTransition extends Transition {

	public PlaceHolderTransition(State fromState, Event fromEvent, State toState, Event toEvent, STMEvent next) {
		super(fromState, fromEvent, toState, toEvent, next, null);
	}
	
	@Override
	public STMEvent execute(STMContext fsmContext, StatefulContext statefulContext) {
		return nextStmEvent;
	}
	
	@Override
	public StringBuilder formatShortString() {
		return Transition.formatShortString("PlaceHolder", configuredFromState, configuredFromEvent);
	}
	
	@Override
	public StringBuilder formatString()
	{
		StringBuilder sBuilder = formatString(null, "PlaceHolder", fromState.toString(), 
				fromEvent.toString(), toState.toString(), toEvent.toString(), nextStmEvent, null, null);
		sBuilder.append("'/>");
		return sBuilder;
	}
}

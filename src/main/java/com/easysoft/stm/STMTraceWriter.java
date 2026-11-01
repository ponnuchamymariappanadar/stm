package com.easysoft.stm;

import com.easysoft.stm.STM.STMEvent;

/**
 * Receives trace events from a {@link StatefulContextWithTrace}. Implement it to send traces
 * somewhere other than the log.
 */
public interface STMTraceWriter {
	public default void endState(Transition currentTransition, long startTime, int stateChangeCount, STMEvent nextEvent, Object lastResult) {
		
	}

	public StringBuilder getTracerStringBuilder();

	public void nextContext(STMContext newFsmContext);

	public void printTiming(Transition currentTransition, long startTime, Object lastResult);

	public void endState();

	void addCustomMessage(String message);
}

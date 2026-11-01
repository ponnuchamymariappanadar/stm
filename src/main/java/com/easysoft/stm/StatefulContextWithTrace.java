package com.easysoft.stm;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.easysoft.stm.STM.STMEvent;

/**
 * A {@link StatefulContext} that records every step, its result and its duration through an
 * {@link STMTraceWriter} (by default {@link STMTraceLogWriter}, one log line per run).
 */
public class StatefulContextWithTrace extends StatefulContext {
	public static final String PROP_RECORD_SEPERATOR = "stm.trace.record.seperator";
	private static Logger logger = LoggerFactory.getLogger(StatefulContextWithTrace.class);
	private STMTraceWriter traceWriter;
	private long startTime;

	public StatefulContextWithTrace(STMStateful<STMContext> fsm) {
		this(fsm, new STMTraceLogWriter(logger));
	}

	public StatefulContextWithTrace(STMStateful<STMContext> fsm, STMTraceWriter traceWriter) {
		super(fsm);
		debug = true;
		this.traceWriter = traceWriter;
	}

	@Override
	void setCurrentTransition(Transition transition) {
		traceWriter.printTiming(currentTransition, startTime, lastResult);
		super.setCurrentTransition(transition);
		startTime = System.currentTimeMillis();
	}

	@Override
	public void setNextContext(STMContext newFsmContext) {
		super.setNextContext(newFsmContext);
		traceWriter.nextContext(newFsmContext);
	}

	@Override
	public void endState(int stateChangeCount, STMEvent nextEvent) {
		traceWriter.endState(currentTransition, startTime, stateChangeCount, nextEvent, lastResult);
		startTime = 0;
	}

	@Override
	public void reset() {
		traceWriter.printTiming(currentTransition, startTime, lastResult);
		super.reset();
		traceWriter.endState();
		startTime = 0;
	}

	@Override
	public void reset(Transition transition) {
		super.reset(transition);
	}

	@Override
	public STMTraceWriter getTraceWriter() {
		return traceWriter;
	}

	@Override
	public void setTraceWriter(STMTraceWriter traceWriter) {
		this.traceWriter = traceWriter;
	}
}

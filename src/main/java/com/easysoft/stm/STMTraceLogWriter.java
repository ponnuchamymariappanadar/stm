package com.easysoft.stm;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;

import com.easysoft.stm.STM.STMEvent;

public class STMTraceLogWriter implements STMTraceWriter {
	public static final String PROP_RECORD_SEPERATOR = "stm.trace.record.seperator";
	private static String recordSeperator = "|";
	private static String stepInfoSeperator = "->";
	final Logger logger;
	final StringBuilder tracer = new StringBuilder();
	StringBuilder contextSwitch;

	public STMTraceLogWriter(Logger logger) {
		this.logger = logger;
	}

	@Override
	public void endState() {
		if (tracer.length() > 0) {
			logger.info(tracer.toString());
			tracer.setLength(0);
		}
	}

	@Override
	public StringBuilder getTracerStringBuilder() {
		return tracer;
	}

	@Override
	public void nextContext(STMContext newFsmContext) {
		contextSwitch = new StringBuilder();
		contextSwitch.append(recordSeperator);
		contextSwitch.append("Context Switched");
	}

	@Override
	public void endState(Transition currentTransition, long startTime, int stateChangeCount, STMEvent nextEvent, Object lastResult) {
		printTiming(currentTransition, startTime, lastResult);
		tracer.append(recordSeperator);
		tracer.append("Process is finished ");
		tracer.append(nextEvent);
		endState();
	}
	
	@Override
	public void addCustomMessage(String message) {
		tracer.append(recordSeperator);
		tracer.append(message);
	}
	
	@Override
	public void printTiming(Transition currentTransition, long startTime, Object lastResult) {
		if (currentTransition != null && startTime != 0 && !currentTransition.isExcludeInfo()) {
			tracer.append(recordSeperator);
			tracer.append(currentTransition);
			tracer.append(stepInfoSeperator);
			if (lastResult == null) {
				tracer.append("null");
			} else {
				tracer.append(StringUtils.abbreviate(lastResult.toString(), 10));
			}
			tracer.append("(");
			tracer.append(System.currentTimeMillis() - startTime);
			tracer.append(")ms");
			if (contextSwitch != null) {
				tracer.append(contextSwitch);
				contextSwitch = null;
			}
		}
	}
	
	public static String getRecordSeperator() {
		return recordSeperator;
	}

	public static void setRecordSeperator(String recordSeperator) {
		STMTraceLogWriter.recordSeperator = recordSeperator;
	}

	public static String getStepInfoSeperator() {
		return stepInfoSeperator;
	}

	public static void setStepInfoSeperator(String stepInfoSeperator) {
		STMTraceLogWriter.stepInfoSeperator = stepInfoSeperator;
	}
}

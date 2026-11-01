package com.easysoft.stm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import com.easysoft.stm.STM.STMEvent;

/** Tests for debug tracing: {@link StatefulContextWithTrace} and {@link STMTraceLogWriter}. */
class TraceTest {

	/** Captures each finished trace line instead of only logging it. */
	static class CapturingWriter extends STMTraceLogWriter {
		final List<String> lines = new ArrayList<>();

		CapturingWriter() {
			super(LoggerFactory.getLogger(TraceTest.class));
		}

		@Override
		public void endState() {
			if (getTracerStringBuilder().length() > 0) {
				lines.add(getTracerStringBuilder().toString());
			}
			super.endState();
		}
	}

	@AfterEach
	void restoreSeparators() {
		STMTraceLogWriter.setRecordSeperator("|");
		STMTraceLogWriter.setStepInfoSeperator("->");
	}

	private STMStateful<STMContext> twoStepMachine(Object secondResult) {
		StateCollection states = new StateCollection();
		EventCollection events = new EventCollection();
		State s = new State(0, "s");
		State end = new State(1, "end");
		states.addState(s);
		states.addState(end);
		Event go = new Event(0, "go");
		Event then = new Event(1, "then");
		Event done = new Event(2, "done");
		events.addEvent(go);
		events.addEvent(then);
		events.addEvent(done);
		Transition t1 = new Transition(s, go, s, then, null, null);
		t1.setActor(Actors.logging("com.acme.First", "one"));
		Transition t2 = new Transition(s, then, end, done, STMEvent.STOP, null);
		t2.setActor(Actors.logging("com.acme.Second", secondResult));
		s.addTransition(t1);
		s.addTransition(t2);
		return new STMStateful<>(0, "trace", s, go, states, events);
	}

	@Test
	void traceRecordsEachTransitionAndTheFinalEvent() {
		CapturingWriter writer = new CapturingWriter();
		STMStateful<STMContext> stm = twoStepMachine("two");
		TestContext ctx = new TestContext(stm.createContextWithTrace(writer));

		ctx.getSTMContext().start(ctx);

		assertEquals(1, writer.lines.size(), writer.lines.toString());
		String line = writer.lines.get(0);
		assertTrue(line.contains("|go<-s(First)->one("), line);
		assertTrue(line.contains("|then<-s(Second)->two("), line);
		assertTrue(line.endsWith("|Process is finished STOP"), line);
	}

	@Test
	void longResultsAreAbbreviated() {
		CapturingWriter writer = new CapturingWriter();
		STMStateful<STMContext> stm = twoStepMachine("a-very-long-result-value");
		TestContext ctx = new TestContext(stm.createContextWithTrace(writer));
		ctx.getSTMContext().start(ctx);
		assertTrue(writer.lines.get(0).contains("->a-very-...("), writer.lines.get(0));
	}

	@Test
	void separatorsAreConfigurable() {
		STMTraceLogWriter.setRecordSeperator(" ; ");
		STMTraceLogWriter.setStepInfoSeperator(" => ");
		CapturingWriter writer = new CapturingWriter();
		STMStateful<STMContext> stm = twoStepMachine("two");
		TestContext ctx = new TestContext(stm.createContextWithTrace(writer));
		ctx.getSTMContext().start(ctx);

		String line = writer.lines.get(0);
		assertTrue(line.contains(" ; go<-s(First) => one("), line);
		assertEquals(" ; ", STMTraceLogWriter.getRecordSeperator());
		assertEquals(" => ", STMTraceLogWriter.getStepInfoSeperator());
	}

	@Test
	void customMessagesAreAppended() {
		CapturingWriter writer = new CapturingWriter();
		writer.addCustomMessage("hello");
		assertEquals("|hello", writer.getTracerStringBuilder().toString());
		writer.endState();
		assertEquals(List.of("|hello"), writer.lines);
		assertEquals(0, writer.getTracerStringBuilder().length());
	}

	@Test
	void traceContextExposesWriterAndDebugFlag() {
		CapturingWriter writer = new CapturingWriter();
		StatefulContext sc = twoStepMachine("x").createContextWithTrace(writer);
		assertSame(writer, sc.getTraceWriter());
		assertTrue(sc.getDebug());

		CapturingWriter other = new CapturingWriter();
		sc.setTraceWriter(other);
		assertSame(other, sc.getTraceWriter());
	}

	@Test
	void defaultTraceContextLogsWithoutFailing() {
		STMStateful<STMContext> stm = twoStepMachine("two");
		TestContext ctx = new TestContext(stm.createContextWithTrace());
		assertEquals(STMEvent.STOP, ctx.getSTMContext().start(ctx));
		assertEquals(List.of("com.acme.First", "com.acme.Second"), ctx.log);
	}
}

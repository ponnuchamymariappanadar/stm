package com.easysoft.stm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.easysoft.stm.STM.STMEvent;

/** Regression tests for engine bugs fixed after the first test pass (see CHANGELOG.md). */
class RegressionTest {

	private static final class Machine {
		final StateCollection states = new StateCollection();
		final EventCollection events = new EventCollection();
		final STMStateful<STMContext> stm;
		final Transition first;
		final Transition extra;

		/** s --go--> (firstActor) --> s --end (STOP) ; s --extra--> (extraActor, STOP) */
		Machine(int id, String prefix, Actor<STMContext> firstActor, Actor<STMContext> extraActor) {
			State s = new State(0, prefix + ".s");
			State end = new State(1, prefix + ".end");
			states.addState(s);
			states.addState(end);
			Event go = new Event(0, prefix + ".go");
			Event done = new Event(1, prefix + ".end");
			Event extraEvent = new Event(2, prefix + ".extra");
			events.addEvent(go);
			events.addEvent(done);
			events.addEvent(extraEvent);
			first = new Transition(s, go, s, done, null, null);
			first.setActor(firstActor);
			Transition stop = new PlaceHolderTransition(s, done, end, done, STMEvent.STOP);
			extra = new Transition(s, extraEvent, end, done, STMEvent.STOP, null);
			extra.setActor(extraActor);
			s.addTransition(first);
			s.addTransition(stop);
			s.addTransition(extra);
			stm = new STMStateful<>(id, prefix, s, go, states, events);
		}
	}

	@Test
	void setDebugFalseTurnsDebugModeOff() {
		Machine m = new Machine(0, "m", Actors.logging("a", null), Actors.logging("x", null));
		m.stm.setDebug(true);
		m.stm.setDebug(false);
		assertFalse(m.stm.getDebug());

		m.stm.setDebug(true);
		assertInstanceOf(StatefulContextWithTrace.class, m.stm.createContext(), "debug can be switched on again");
	}

	@Test
	void endTransitionRunsOnceAfterTheFlowFinishes() {
		Machine m = new Machine(0, "m", Actors.logging("work", null), Actors.logging("cleanup", null));
		TestContext ctx = new TestContext(m.stm.createContext());
		ctx.getSTMContext().setEndTransition(m.extra);

		ctx.getSTMContext().start(ctx);

		assertEquals(List.of("work", "cleanup"), ctx.log);
	}

	@Test
	void startWithNewStmContinuesInTheOtherMachine() {
		Machine second = new Machine(2, "second", Actors.logging("second-machine", null), Actors.logging("x", null));
		Actor<STMContext> switcher = Actors.of("switch", c -> {
			((TestContext) c).log.add("switch");
			return c.getSTMContext().getStm().startWithNewSTM(second.stm, c, second.first);
		});
		Machine first = new Machine(1, "first", switcher, Actors.logging("x", null));
		TestContext ctx = new TestContext(first.stm.createContext());

		STMEvent result = ctx.getSTMContext().start(ctx);

		assertEquals(List.of("switch", "second-machine"), ctx.log);
		assertEquals(STMEvent.STOP, result);
	}
}

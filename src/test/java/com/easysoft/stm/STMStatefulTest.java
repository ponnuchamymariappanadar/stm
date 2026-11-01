package com.easysoft.stm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.easysoft.stm.STM.STMEvent;

/**
 * Tests for the execution loop in {@link STMStateful} and the public API of
 * {@link StatefulContext}, using a machine built by hand (no XML).
 *
 * <pre>
 *   a --start--> (step1) --> a --middle--> (step2) --> b --finish--> (step3, STOP)
 *   error --error.e--> (handler, STOP)
 * </pre>
 */
class STMStatefulTest {

	private StateCollection states;
	private EventCollection events;
	private State a, b, errorState, end;
	private Event start, middle, finish, errorEvent, endEvent;
	private Transition step1, step2, step3, onError;
	private STMStateful<STMContext> stm;

	@BeforeEach
	void setUp() {
		states = new StateCollection();
		events = new EventCollection();
		a = state(0, "a");
		b = state(1, "b");
		errorState = state(2, "error");
		end = state(3, "end");
		start = event(0, "start");
		middle = event(1, "middle");
		finish = event(2, "finish");
		errorEvent = event(3, "error.e");
		endEvent = event(4, "end.e");

		onError = new Transition(errorState, errorEvent, end, endEvent, STMEvent.STOP, null);
		onError.setActor(Actors.of("handler", c -> {
			((TestContext) c).log.add("handler:" + c.getSTMContext().getException().getMessage());
			return null;
		}));
		errorState.addTransition(onError);

		step1 = add(a, start, a, middle, null, Actors.logging("step1", "r1"));
		step2 = add(a, middle, b, finish, null, Actors.logging("step2", "r2"));
		step3 = add(b, finish, end, endEvent, STMEvent.STOP, Actors.logging("step3", "r3"));

		stm = new STMStateful<>(1, "test.stm", a, start, states, events);
	}

	private State state(int id, String name) {
		State s = new State(id, name);
		states.addState(s);
		return s;
	}

	private Event event(int id, String name) {
		Event e = new Event(id, name);
		events.addEvent(e);
		return e;
	}

	private Transition add(State from, Event when, State to, Event fire, STMEvent next, Actor<STMContext> actor) {
		Transition t = new Transition(from, when, to, fire, next, onError);
		t.setActor(actor);
		from.addTransition(t);
		return t;
	}

	private TestContext newContext() {
		return new TestContext(stm.createContext());
	}

	@Test
	void runsTransitionsInOrderUntilStop() {
		TestContext ctx = newContext();
		STMEvent result = ctx.getSTMContext().start(ctx);

		assertEquals(STMEvent.STOP, result);
		assertEquals(List.of("step1", "step2", "step3"), ctx.log);
		assertEquals("r3", ctx.<String>getLastSTMResult());
	}

	@Test
	void startByStateAndEventName() {
		TestContext ctx = newContext();
		ctx.getSTMContext().start(ctx, "a", "middle");
		assertEquals(List.of("step2", "step3"), ctx.log);
	}

	@Test
	void startByStateAndEventObjects() {
		TestContext ctx = newContext();
		ctx.getSTMContext().start(ctx, b, finish);
		assertEquals(List.of("step3"), ctx.log);
	}

	@Test
	void startWithUnknownNamesFails() {
		TestContext ctx = newContext();
		StatefulContext sc = ctx.getSTMContext();
		assertThrows(InvalidStateException.class, () -> sc.start(ctx, "nope", "start"));
		assertThrows(InvalidEventException.class, () -> sc.start(ctx, "a", "nope"));
		assertThrows(InvalidTransitionException.class, () -> sc.start(ctx, "b", "start"));
	}

	@Test
	void accessorsExposeConfiguration() {
		assertSame(a, stm.getDefaultStartState());
		assertSame(start, stm.getDefaultStartEvent());
		assertSame(b, stm.getState("b"));
		assertSame(finish, stm.getEvent("finish"));
		assertEquals(1, stm.getId());
		assertEquals("test.stm", stm.getName());
		assertSame(states, stm.getStates());
	}

	@Test
	void exceptionRunsOnErrorTransitionAndStops() {
		step2.setActor(Actors.throwing("step2", new IllegalStateException("broken")));
		TestContext ctx = newContext();
		STMEvent result = ctx.getSTMContext().start(ctx);

		assertEquals(STMEvent.STOP, result);
		assertEquals(List.of("step1", "step2", "handler:broken"), ctx.log);
	}

	@Test
	void contextErrorStateIsClearedAfterRun() {
		step2.setActor(Actors.throwing("step2", new IllegalStateException("broken")));
		TestContext ctx = newContext();
		ctx.getSTMContext().start(ctx);
		assertFalse(ctx.getSTMContext().isError());
		assertNull(ctx.getSTMContext().getException());
	}

	@Test
	void actorCanRedirectWithNext() {
		// step1 jumps straight to b/finish instead of going to a/middle.
		step1.setActor(Actors.of("jump", c -> {
			((TestContext) c).log.add("jump");
			return c.getSTMContext().next(c, "b", "finish");
		}));
		TestContext ctx = newContext();
		ctx.getSTMContext().start(ctx);
		assertEquals(List.of("jump", "step3"), ctx.log);
	}

	@Test
	void nextFromCurrentStateByEventName() {
		// step1 fires "middle" on its own state explicitly.
		step1.setActor(Actors.of("jump", c -> c.getSTMContext().next(c, "middle")));
		TestContext ctx = newContext();
		ctx.getSTMContext().start(ctx);
		assertEquals(List.of("step2", "step3"), ctx.log);
	}

	@Test
	void nextWithUnknownTargetFails() {
		TestContext ctx = newContext();
		StatefulContext sc = ctx.getSTMContext();
		assertThrows(InvalidEventException.class, () -> sc.next(ctx, "a", "nope"));
		assertThrows(InvalidStateException.class, () -> sc.next(ctx, "nope", "start"));
		assertThrows(InvalidTransitionException.class, () -> sc.next(ctx, "a", "finish"));
	}

	@Test
	void loopGuardStopsRunawayMachines() {
		// b/finish loops back to a/start forever.
		Transition loopBack = new Transition(b, finish, a, start, null, onError);
		loopBack.setActor(Actors.logging("loop", null));
		b.addTransition(loopBack);
		stm.setMaxStateChangeAllowed(10);

		TestContext ctx = newContext();
		STMEvent result = ctx.getSTMContext().start(ctx);

		assertEquals(STMEvent.NEXT, result, "the guard breaks out of the loop rather than stopping normally");
		assertEquals(12, ctx.log.size(), "max + 2 transitions run before the guard trips");
		assertEquals(10, stm.getMaxStateChangeAllowed());
	}

	@Test
	void sameMachineCanRunManyContexts() {
		TestContext first = newContext();
		TestContext second = newContext();
		first.getSTMContext().start(first);
		second.getSTMContext().start(second);
		assertEquals(first.log, second.log);
		assertNotSame(first.getSTMContext(), second.getSTMContext());
	}

	@Test
	void debugModeCreatesTracingContexts() {
		assertFalse(stm.getDebug());
		stm.setDebug(true);
		assertTrue(stm.getDebug());
		assertInstanceOf(StatefulContextWithTrace.class, stm.createContext());

		stm.setDebug(false);
		assertFalse(stm.getDebug());
		assertFalse(stm.createContext() instanceof StatefulContextWithTrace);
	}

	@Test
	void callStackIsLastInFirstOut() {
		StatefulContext sc = stm.createContext();
		assertNull(sc.popCallStateTransition());
		sc.addCallStateTransition(step1);
		sc.addCallStateTransition(step2);
		assertSame(step2, sc.popCallStateTransition());
		assertSame(step1, sc.popCallStateTransition());
		assertNull(sc.popCallStateTransition());
	}

	@Test
	void resetClearsErrorAndOptionallyQueuesTransition() {
		StatefulContext sc = stm.createContext();
		sc.setException(new RuntimeException("x"));
		sc.setNextTransition(step2);
		assertTrue(sc.isError());

		sc.reset();
		assertFalse(sc.isError());
		assertNull(sc.getException());
		assertNull(sc.getNextTransition());

		sc.reset(step3);
		assertSame(step3, sc.getNextTransition());
	}

	@Test
	void clearErrorKeepsException() {
		StatefulContext sc = stm.createContext();
		RuntimeException ex = new RuntimeException("x");
		sc.setException(ex);
		sc.clearError();
		assertFalse(sc.isError());
		assertSame(ex, sc.getException());
	}

	@Test
	void machinesAreEqualById() {
		STMStateful<STMContext> same = new STMStateful<>(1, "other-name", b, finish, states, events);
		STMStateful<STMContext> different = new STMStateful<>(2, "test.stm", a, start, states, events);
		assertEquals(stm, same);
		assertEquals(stm.hashCode(), same.hashCode());
		assertFalse(stm.equals(different));
	}
}

package com.easysoft.stm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.easysoft.stm.STM.STMEvent;

/**
 * Tests for the individual transition types and how each one reports the next
 * {@link STMEvent} to the engine.
 */
class TransitionTest {

	private State from;
	private State to;
	private Event when;
	private Event fire;
	private Transition errorTransition;
	private StatefulContext stateful;
	private TestContext ctx;

	@BeforeEach
	void setUp() {
		from = new State(0, "from");
		to = new State(1, "to");
		when = new Event(0, "when");
		fire = new Event(1, "fire");
		errorTransition = new PlaceHolderTransition(new State(2, "error"), new Event(2, "error.e"), new State(3, "end"), new Event(3, "end.e"),
				STMEvent.STOP);
		STMStateful<STMContext> stm = new STMStateful<>(0, "t", from, when, new StateCollection(), new EventCollection());
		stateful = stm.createContext();
		ctx = new TestContext(stateful);
	}

	private STMEvent run(Transition t) {
		stateful.setCurrentTransition(t);
		return t.execute(ctx, stateful);
	}

	@Test
	void defaultsToNextWhenNoStmEventGiven() {
		Transition t = new Transition(from, when, to, fire, null, null);
		assertEquals(STMEvent.NEXT, t.getNextSTMEvent());
	}

	@Test
	void executeStoresActorResultAndReturnsConfiguredEvent() {
		Transition t = new Transition(from, when, to, fire, STMEvent.STOP, null);
		t.setActor(Actors.logging("work", "result"));

		assertEquals(STMEvent.STOP, run(t));
		assertEquals("result", stateful.getLastResult());
		assertEquals(List.of("work"), ctx.log);
	}

	@Test
	void actorCanReturnAnStmEventDirectly() {
		Transition t = new Transition(from, when, to, fire, STMEvent.NEXT, null);
		t.setActor(Actors.logging("stop-now", STMEvent.STOP));
		assertEquals(STMEvent.STOP, run(t));
		assertNull(stateful.getLastResult(), "an STMEvent result is not stored as the last result");
	}

	@Test
	void exceptionRoutesToOnErrorTransition() {
		Transition t = new Transition(from, when, to, fire, null, errorTransition);
		IllegalStateException boom = new IllegalStateException("boom");
		t.setActor(Actors.throwing("work", boom));

		assertEquals(STMEvent.NEXT, run(t));
		assertTrue(stateful.isError());
		assertSame(boom, stateful.getException());
		assertSame(errorTransition, stateful.getNextTransition());
	}

	@Test
	void secondExceptionWhileAlreadyInErrorStops() {
		Transition t = new Transition(from, when, to, fire, null, errorTransition);
		t.setActor(Actors.throwing("work", new IllegalStateException("first")));
		run(t);

		Transition handler = new Transition(from, fire, to, when, null, errorTransition);
		handler.setActor(Actors.throwing("handler", new IllegalStateException("second")));
		assertEquals(STMEvent.STOP, run(handler));
		assertEquals("second", stateful.getException().getMessage());
	}

	@Test
	void placeHolderNeverCallsAnActor() {
		PlaceHolderTransition t = new PlaceHolderTransition(from, when, to, fire, STMEvent.NEXT);
		t.setActor(Actors.logging("should-not-run", null));
		assertEquals(STMEvent.NEXT, run(t));
		assertTrue(ctx.log.isEmpty());
	}

	@Test
	void stmEventBasedTransitionIgnoresEventChangesMadeByTheActor() {
		StmEventBasedTransition t = new StmEventBasedTransition(from, when, to, fire, STMEvent.STOP, null);
		// The actor asks for NEXT via the context, but this transition type always reports its own configured event.
		t.setActor(Actors.of("redirect", c -> {
			c.getSTMContext().setNextSTMEvent(STMEvent.NEXT);
			return "done";
		}));
		assertEquals(STMEvent.STOP, run(t));
		assertEquals("done", stateful.getLastResult());
	}

	@Test
	void transitionsAreEqualByFromStateAndFromEvent() {
		Transition a = new Transition(from, when, to, fire, null, null);
		Transition b = new Transition(from, when, from, when, STMEvent.STOP, null);
		Transition c = new Transition(from, fire, to, when, null, null);
		assertEquals(a, b);
		assertEquals(a.hashCode(), b.hashCode());
		assertFalse(a.equals(c));
	}

	@Test
	void toStringShowsEventStateAndShortActorName() {
		Transition t = new Transition(from, when, to, fire, null, null);
		t.setActor(Actors.logging("com.example.MyActor#doWork", null));
		assertEquals("when<-from(MyActor#doWork)", t.toString());
	}

	@Test
	void longStringDescribesTheWholeTransition() {
		Transition t = new Transition(from, when, to, fire, STMEvent.STOP, errorTransition);
		String s = t.getToLongStringValue();
		assertTrue(s.startsWith("<transition state='from' when='when' to='to' fire='fire' stmevent='STOP'"), s);
		assertTrue(s.contains("OnError to='end' fire='end.e'"), s);
	}

	@Test
	void abbreviateHandlerNameKeepsSimpleClassAndMethod() {
		assertEquals("Foo#bar", Transition.abbreivateHandlerName("com.acme.Foo#bar"));
		assertEquals("Foo", Transition.abbreivateHandlerName("com.acme.Foo"));
		assertEquals("Plain", Transition.abbreivateHandlerName("Plain"));
	}

	@Test
	void formatTransitionProducesXmlLikeDescription() {
		assertEquals("<transition state='s' when='w' to='t' fire='f' stmevent='NEXT'/>",
				Transition.formatTranstion("s", "w", "t", "f", STMEvent.NEXT));
	}
}

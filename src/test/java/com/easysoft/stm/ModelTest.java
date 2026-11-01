package com.easysoft.stm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Tests for the basic value types: {@link State}, {@link Event} and their collections. */
class ModelTest {

	@Test
	void statesAreEqualByIdNotByName() {
		State a = new State(1, "a");
		assertEquals(a, new State(1, "renamed"));
		assertNotEquals(a, new State(2, "a"));
		assertEquals(1, a.hashCode());
		assertEquals("a", a.toString());
		assertEquals("a", a.getName());
		assertFalse(a.equals("a"));
	}

	@Test
	void eventsAreEqualByIdNotByName() {
		Event e = new Event(7, "go");
		assertEquals(e, new Event(7, "other"));
		assertNotEquals(e, new Event(8, "go"));
		assertEquals(7, e.hashCode());
		assertEquals("go", e.toString());
	}

	@Test
	void stateCollectionLooksUpByNameAndId() {
		StateCollection states = new StateCollection();
		State s = new State(3, "s");
		states.addState(s);
		assertSame(s, states.getState("s"));
		assertSame(s, states.getState(3));
		assertNull(states.getState("missing"));
		assertTrue(states.getStates().contains(s));
	}

	@Test
	void eventCollectionLooksUpByNameAndId() {
		EventCollection events = new EventCollection();
		Event e = new Event(4, "e");
		events.addEvent(e);
		assertSame(e, events.getEvent("e"));
		assertSame(e, events.getEvent(4));
		assertNull(events.getEvent(99));
	}

	@Test
	void stateIndexesTransitionsByFromEvent() {
		State from = new State(0, "from");
		State to = new State(1, "to");
		Event go = new Event(0, "go");
		Event next = new Event(1, "next");
		Transition t = new Transition(from, go, to, next, null, null);
		from.addTransition(t);

		assertSame(t, from.getTransition(go));
		assertNull(from.getTransition(next));
		assertEquals(1, from.getTransitons().size());
	}

	@Test
	void stateCanRegisterATransitionUnderADifferentEvent() {
		State from = new State(0, "from");
		Event go = new Event(0, "go");
		Event alias = new Event(1, "alias");
		Transition t = new Transition(from, go, from, alias, null, null);
		from.addTransition(alias, t);
		assertSame(t, from.getTransition(alias));
	}
}

package com.easysoft.stm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.easysoft.stm.STM.STMEvent;

/** Tests for {@link ConditionAndActor}, {@link ConditionOnly} and {@link Choice}. */
class ConditionAndChoiceTest {

	private State s;
	private Event when;
	private Event fire;
	private Transition failed;
	private Transition error;
	private StatefulContext stateful;
	private TestContext ctx;

	@BeforeEach
	void setUp() {
		s = new State(0, "s");
		when = new Event(0, "when");
		fire = new Event(1, "fire");
		failed = new PlaceHolderTransition(s, new Event(2, "failed"), s, fire, STMEvent.STOP);
		error = new PlaceHolderTransition(s, new Event(3, "error"), s, fire, STMEvent.STOP);
		stateful = new STMStateful<>(0, "t", s, when, new StateCollection(), new EventCollection()).createContext();
		ctx = new TestContext(stateful);
	}

	private STMEvent run(Transition t) {
		stateful.setCurrentTransition(t);
		return t.execute(ctx, stateful);
	}

	private static Actor<STMContext> condition(Object value) {
		return Actors.of("cond", c -> {
			((TestContext) c).log.add("cond");
			return value;
		});
	}

	@Test
	void conditionAndActorRunsActorWhenConditionIsTrue() {
		ConditionAndActor t = new ConditionAndActor(s, when, s, fire, null, error, condition(true), failed);
		t.setActor(Actors.logging("actor", "ok"));

		assertEquals(STMEvent.NEXT, run(t));
		assertEquals(List.of("cond", "actor"), ctx.log);
		assertEquals("ok", stateful.getLastResult());
		assertNull(stateful.getNextTransition());
	}

	@Test
	void conditionAndActorSkipsActorAndRoutesToFailedTransitionWhenFalse() {
		ConditionAndActor t = new ConditionAndActor(s, when, s, fire, null, error, condition(false), failed);
		t.setActor(Actors.logging("actor", "ok"));

		assertEquals(STMEvent.NEXT, run(t));
		assertEquals(List.of("cond"), ctx.log);
		assertSame(failed, stateful.getNextTransition());
	}

	@Test
	void onlyBooleanTrueCountsAsPassing() {
		ConditionAndActor t = new ConditionAndActor(s, when, s, fire, null, error, condition("true"), failed);
		t.setActor(Actors.logging("actor", null));
		run(t);
		assertSame(failed, stateful.getNextTransition(), "a non-Boolean result is treated as a failed condition");
	}

	@Test
	void exceptionInConditionRoutesToOnError() {
		ConditionAndActor t = new ConditionAndActor(s, when, s, fire, null, error,
				Actors.throwing("cond", new IllegalArgumentException("bad")), failed);
		run(t);
		assertTrue(stateful.isError());
		assertSame(error, stateful.getNextTransition());
	}

	@Test
	void conditionOnlyStoresBooleanResult() {
		ConditionOnly pass = new ConditionOnly(s, when, s, fire, null, error, condition(true), failed);
		assertEquals(STMEvent.NEXT, run(pass));
		assertEquals(Boolean.TRUE, stateful.getLastResult());
		assertNull(stateful.getNextTransition());

		ConditionOnly fail = new ConditionOnly(s, when, s, fire, null, error, condition(false), failed);
		assertEquals(STMEvent.NEXT, run(fail));
		assertEquals(Boolean.FALSE, stateful.getLastResult());
		assertSame(failed, stateful.getNextTransition());
	}

	@Test
	void conditionOnlyHonoursConfiguredStopEvent() {
		ConditionOnly t = new ConditionOnly(s, when, s, fire, STMEvent.STOP, error, condition(true), failed);
		assertEquals(STMEvent.STOP, run(t));
	}

	@Test
	void conditionOnlyRoutesExceptionsToOnError() {
		ConditionOnly t = new ConditionOnly(s, when, s, fire, null, error, Actors.throwing("cond", new IllegalStateException()), failed);
		run(t);
		assertSame(error, stateful.getNextTransition());
	}

	@Test
	void conditionToStringsUseTheConditionName() {
		ConditionOnly t = new ConditionOnly(s, when, s, fire, null, error, condition(true), failed);
		assertEquals("when<-s(cond)", t.toString());
		assertTrue(t.getToLongStringValue().contains("condition='cond'"), t.getToLongStringValue());
		assertTrue(t.getToLongStringValue().contains("onFailedCondition to='s' fire='fire'"), t.getToLongStringValue());
	}

	// ---- Choice ------------------------------------------------------------

	private Choice<STMContext> choice(Object returned, Map<String, Transition> cases) {
		return new Choice<>(Actors.logging("chooser", returned), cases);
	}

	@Test
	void choiceRoutesByTheStringValueOfTheResult() {
		Transition express = new PlaceHolderTransition(s, new Event(10, "express"), s, fire, STMEvent.STOP);
		Map<String, Transition> cases = new HashMap<>();
		cases.put("EXPRESS", express);

		Object result = choice(Shipping.EXPRESS, cases).handle(ctx);

		assertEquals(STMEvent.NEXT, result);
		assertSame(express, stateful.getNextTransition());
		assertEquals(Shipping.EXPRESS, stateful.getLastResult());
	}

	@Test
	void choiceFallsBackToDefaultCase() {
		Transition fallback = new PlaceHolderTransition(s, new Event(11, "fallback"), s, fire, STMEvent.STOP);
		Map<String, Transition> cases = new HashMap<>();
		cases.put("default", fallback);

		assertEquals(STMEvent.NEXT, choice("anything", cases).handle(ctx));
		assertSame(fallback, stateful.getNextTransition());
	}

	@Test
	void choiceWithoutMatchOrDefaultReturnsTheValue() {
		assertEquals("unmatched", choice("unmatched", new HashMap<>()).handle(ctx));
		assertNull(stateful.getNextTransition());
	}

	@Test
	void choiceWithNullResultDoesNotRoute() {
		Map<String, Transition> cases = new HashMap<>();
		cases.put("default", failed);
		assertNull(choice(null, cases).handle(ctx));
		assertNull(stateful.getNextTransition());
	}

	@Test
	void choiceExposesUnderlyingActor() {
		Actor<STMContext> inner = Actors.logging("inner", null);
		Choice<STMContext> c = new Choice<>(inner, new HashMap<>());
		assertSame(inner, c.getActualActor());
		assertEquals("inner", c.toString());
	}

	enum Shipping {
		EXPRESS, STANDARD
	}
}

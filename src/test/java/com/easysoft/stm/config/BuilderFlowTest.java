package com.easysoft.stm.config;

import static com.easysoft.stm.config.Stm.actor;
import static com.easysoft.stm.config.Stm.build;
import static com.easysoft.stm.config.Stm.builder;
import static com.easysoft.stm.config.Stm.condition;
import static com.easysoft.stm.config.Stm.machine;
import static com.easysoft.stm.config.Stm.run;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.easysoft.stm.ConditionAndActor;
import com.easysoft.stm.ConditionOnly;
import com.easysoft.stm.MethodActor;
import com.easysoft.stm.MethodWithLookupArg;
import com.easysoft.stm.MethodWithMapArg;
import com.easysoft.stm.STMException;
import com.easysoft.stm.MethodWithStringArg;
import com.easysoft.stm.PlaceHolderTransition;
import com.easysoft.stm.STM.STMEvent;
import com.easysoft.stm.STMContext;
import com.easysoft.stm.STMStateful;
import com.easysoft.stm.StmEventBasedTransition;
import com.easysoft.stm.TestContext;
import com.easysoft.stm.Transition;

/** End-to-end tests: build a machine from XML with {@link STMOptimizedBuilder} and run it. */
class BuilderFlowTest {

	private static Transition transition(STMStateful<STMContext> stm, String state, String event) {
		return stm.getState(state).getTransition(stm.getEvent(event));
	}

	@Test
	void linearFlowRunsActorsInOrderAndStops() {
		STMStateful<STMContext> stm = build(machine(
				"{ name: s, steps: [ "
						+ "{ step: start, nextStage: s, then: two, action: " + actor("a") + " }, "
						+ "{ step: two, nextStage: this, then: three, action: " + actor("b") + " }, "
						+ "{ step: three, then: done, action: " + actor("c") + " }, "
						+ "{ step: done, control: stop } ] }"));

		TestContext ctx = new TestContext(stm.createContext());
		STMEvent result = ctx.getSTMContext().start(ctx);

		assertEquals(STMEvent.STOP, result);
		assertEquals(List.of("a", "b", "c"), ctx.log);
		assertEquals("C", ctx.<String>getLastSTMResult());
		assertEquals("test.flow", stm.getName());
	}

	@Test
	void firstStateAndFirstTransitionAreTheDefaultStart() {
		STMStateful<STMContext> stm = build(machine(
				"{ name: first, steps: [ "
						+ "{ step: go, then: end, action: " + actor("a") + " }, "
						+ "{ step: end, control: stop } ] }, "
						+ "{ name: second, steps: [ "
						+ "{ step: go2, then: end2, action: " + actor("b") + " }, "
						+ "{ step: end2, control: stop } ] }"));
		assertEquals("first", stm.getDefaultStartState().getName());
		assertEquals("go", stm.getDefaultStartEvent().getName());
	}

	@Test
	void startAndFireAttributesOverrideTheDefaultStart() {
		STMStateful<STMContext> stm = build(machine("startStage: second, startStep: go2",
				"{ name: first, steps: [ "
						+ "{ step: go, then: end, action: " + actor("a") + " }, "
						+ "{ step: end, control: stop } ] }, "
						+ "{ name: second, steps: [ "
						+ "{ step: go2, then: end2, action: " + actor("b") + " }, "
						+ "{ step: end2, control: stop } ] }"));

		assertEquals(List.of("b"), run(stm).log);
	}

	@Test
	void defaultStartEventIgnoresCallRules() {
		STMStateful<STMContext> stm = build(machine("{ name: main, steps: [ "
				+ "{ step: first, runStage: sub, then: sub.go, otherwise: { then: end } }, "
				+ "{ step: end, control: stop, action: " + actor("c") + " } ] }, "
				+ "{ name: sub, steps: [ "
				+ "{ step: sub.go, then: sub.done, action: " + actor("a") + " } ], returns: [ "
				+ "{ step: sub.done } ] }"));
		assertEquals("end", stm.getDefaultStartEvent().getName());
	}

	@Test
	void transitionsCanMoveBetweenStates() {
		STMStateful<STMContext> stm = build(machine(
				"{ name: cart, steps: [ "
						+ "{ step: checkout, nextStage: payment, then: pay, action: " + actor("a") + " } ] }, "
						+ "{ name: payment, steps: [ "
						+ "{ step: pay, nextStage: done, then: finish, action: " + actor("b") + " } ] }, "
						+ "{ name: done, steps: [ "
						+ "{ step: finish, control: stop, action: " + actor("c") + " } ] }"));

		assertEquals(List.of("a", "b", "c"), run(stm).log);
	}

	@Test
	void conditionTrueContinuesAndFalseGoesToGlobalOnFailedCondition() {
		String yaml = machine("{ name: s, steps: [ "
				+ "{ step: start, then: work, check: " + condition("flag") + " }, "
				+ "{ step: work, then: end, action: " + actor("a") + " }, "
				+ "{ step: end, control: stop } ] }");
		STMStateful<STMContext> stm = build(yaml);

		assertEquals(List.of("flag", "a"), run(stm, "flag", true).log);
		assertEquals(List.of("flag", "onRejected"), run(stm, "flag", false).log);
		assertInstanceOf(ConditionOnly.class, transition(stm, "s", "start"));
	}

	@Test
	void localOnFailedConditionOverridesGlobal() {
		STMStateful<STMContext> stm = build(machine("{ name: s, steps: [ "
				+ "{ step: start, then: work, check: " + condition("flag") + ", onFalse: { nextStage: this, then: rejected } }, "
				+ "{ step: work, then: end, action: " + actor("a") + " }, "
				+ "{ step: rejected, then: end, action: " + actor("localRejected") + " }, "
				+ "{ step: end, control: stop } ] }"));

		assertEquals(List.of("flag", "localRejected"), run(stm, "flag", false).log);
	}

	@Test
	void conditionAndActorOnTheSameTransition() {
		STMStateful<STMContext> stm = build(machine("{ name: s, steps: [ "
				+ "{ step: start, then: end, check: " + condition("flag") + ", action: " + actor("a") + " }, "
				+ "{ step: end, control: stop } ] }"));

		assertInstanceOf(ConditionAndActor.class, transition(stm, "s", "start"));
		assertEquals(List.of("flag", "a"), run(stm, "flag", true).log);
		assertEquals(List.of("flag", "onRejected"), run(stm, "flag", false).log);
	}

	@Test
	void exceptionGoesToGlobalOnErrorWithTheException() {
		STMStateful<STMContext> stm = build(machine("{ name: s, steps: [ "
				+ "{ step: start, then: next, action: " + actor("a") + " }, "
				+ "{ step: next, then: end, action: " + actor("boom") + " }, "
				+ "{ step: end, control: stop } ] }"));

		assertEquals(List.of("a", "boom", "onError:boom"), run(stm).log);
	}

	@Test
	void localOnErrorOverridesGlobal() {
		STMStateful<STMContext> stm = build(machine("{ name: s, steps: [ "
				+ "{ step: start, then: end, action: " + actor("boom") + ", onError: { nextStage: this, then: oops } }, "
				+ "{ step: oops, then: end, action: " + actor("localError") + " }, "
				+ "{ step: end, control: stop } ] }"));

		assertEquals(List.of("boom", "localError"), run(stm).log);
	}

	@Test
	void casesRouteOnTheActorResult() {
		STMStateful<STMContext> stm = build(machine("{ name: s, steps: [ "
				+ "{ step: start, then: standard, action: " + actor("route") + ", outcomes: [ { when: EXPRESS, nextStage: this, then: express }, { when: PICKUP, then: pickup } ] }, "
				+ "{ step: express, then: end, action: " + actor("a") + " }, "
				+ "{ step: pickup, then: end, action: " + actor("b") + " }, "
				+ "{ step: standard, then: end, action: " + actor("c") + " }, "
				+ "{ step: end, control: stop } ] }"));

		assertEquals(List.of("route", "a"), run(stm, "route", "EXPRESS").log);
		assertEquals(List.of("route", "b"), run(stm, "route", "PICKUP").log);
		assertEquals(List.of("route", "c"), run(stm, "route", "UNKNOWN").log, "no match and no default: follow fire");
	}

	@Test
	void defaultCaseCatchesUnmatchedResults() {
		STMStateful<STMContext> stm = build(machine("{ name: s, steps: [ "
				+ "{ step: start, then: standard, action: " + actor("route") + ", outcomes: [ { when: EXPRESS, then: express } ], otherwise: { then: fallback } }, "
				+ "{ step: express, then: end, action: " + actor("a") + " }, "
				+ "{ step: fallback, then: end, action: " + actor("b") + " }, "
				+ "{ step: standard, then: end, action: " + actor("c") + " }, "
				+ "{ step: end, control: stop } ] }"));

		assertEquals(List.of("route", "b"), run(stm, "route", "UNKNOWN").log);
	}

	@Test
	void callRunsASubFlowAndReturnRoutesOnTheReturnEvent() {
		STMStateful<STMContext> stm = build(machine("{ name: main, steps: [ "
				+ "{ step: start, then: verify, action: " + actor("a") + " }, "
				+ "{ step: approved, then: end, action: " + actor("b") + " }, "
				+ "{ step: declined, then: end, action: " + actor("c") + " }, "
				+ "{ step: end, control: stop }, "
				+ "{ step: verify, runStage: check.s, then: check.start, outcomes: [ { when: check.ok, nextStage: this, then: approved } ], otherwise: { nextStage: this, then: declined } } ] }, "
				+ "{ name: check.s, steps: [ "
				+ "{ step: check.start, then: check.ok, check: " + condition("flag") + ", onFalse: { nextStage: this, then: check.failed } } ], returns: [ "
				+ "{ step: check.ok }, "
				+ "{ step: check.failed } ] }"));

		assertEquals(List.of("a", "flag", "b"), run(stm, "flag", true).log);
		assertEquals(List.of("a", "flag", "c"), run(stm, "flag", false).log);
	}

	@Test
	void callCanBeReusedFromTwoPlaces() {
		// startStage/then are required here: the default start event is the first rule that is not a call.
		STMStateful<STMContext> stm = build(machine("startStage: main, startStep: start", "{ name: main, steps: [ "
				+ "{ step: start, runStage: sub, then: sub.go, otherwise: { then: second } }, "
				+ "{ step: second, runStage: sub, then: sub.go, otherwise: { then: end } }, "
				+ "{ step: end, control: stop, action: " + actor("c") + " } ] }, "
				+ "{ name: sub, steps: [ "
				+ "{ step: sub.go, then: sub.done, action: " + actor("a") + " } ], returns: [ "
				+ "{ step: sub.done } ] }"));

		assertEquals(List.of("a", "a", "c"), run(stm).log);
	}

	@Test
	void returnWithoutAMatchingCallIsAnError() {
		STMStateful<STMContext> stm = build(machine("{ name: s, returns: [ "
				+ "{ step: start } ] }"));
		List<String> log = run(stm).log;
		assertEquals(1, log.size());
		assertTrue(log.get(0).startsWith("onError:Returned from state without any call"), log.get(0));
	}

	@Test
	void mapInputPassesNamedTextsToTheMethod() {
		STMStateful<STMContext> stm = build(machine("{ name: s, steps: [ "
				+ "{ step: start, then: end, action: { class: '" + Stm.A + "', method: withSettings, "
				+ "input: { mode: fast, limit: 500, rate: 1.50, express: true, note: '' } } }, "
				+ "{ step: end, control: stop } ] }"));

		assertEquals(List.of("settings:{mode=fast, limit=500, rate=1.50, express=true, note=}"), run(stm).log);
		MethodWithMapArg actor = assertInstanceOf(MethodWithMapArg.class, transition(stm, "s", "start").getActor());
		Map<String, String> input = actor.getArgument();
		assertEquals("500", input.get("limit"), "every value is text, as written");
		assertEquals("1.50", input.get("rate"), "1.50 stays 1.50, not 1.5");
		assertEquals("true", input.get("express"));
		assertThrows(UnsupportedOperationException.class, () -> input.put("x", "1"), "the map is read-only");
	}

	@Test
	void mapInputValuesMustBeSingleTexts() {
		String[][] cases = { { "{ a: { b: c } }", "input 'a' must be a single text, not a map" }, { "{ a: [ b, c ] }", "input 'a' must be a single text, not a list" },
				{ "{ a: }", "input 'a' has no value; write '' for an empty text" } };
		for (String[] c : cases) {
			STMException e = assertThrows(STMException.class, () -> build(machine("{ name: s, steps: [ "
					+ "{ step: start, then: end, action: { class: '" + Stm.A + "', method: withSettings, input: " + c[0] + " } }, "
					+ "{ step: end, control: stop } ] }")), c[0]);
			assertTrue(e.getMessage().contains(c[1]), e.getMessage());
		}
	}

	@Test
	void mapInputWorksOnChecks() {
		STMStateful<STMContext> pass = build(machine("{ name: s, steps: [ "
				+ "{ step: start, then: ok, check: { class: '" + Stm.A + "', method: underLimit, input: { amount: 50, limit: 100 } } }, "
				+ "{ step: ok, then: end, action: " + actor("a") + " }, { step: end, control: stop } ] }"));
		assertEquals(List.of("a"), run(pass).log);
		STMStateful<STMContext> fail = build(machine("{ name: s, steps: [ "
				+ "{ step: start, then: ok, check: { class: '" + Stm.A + "', method: underLimit, input: { amount: 500, limit: 100 } } }, "
				+ "{ step: ok, then: end, action: " + actor("a") + " }, { step: end, control: stop } ] }"));
		assertEquals(List.of("onRejected"), run(fail).log, "false goes to onFalse under defaults");
	}

	@Test
	void inputNeedsAMethodWhoseSecondParameterFits() {
		// withArg takes a String, not a Map: the build fails instead of the run
		STMException e = assertThrows(STMException.class, () -> build(machine("{ name: s, steps: [ "
				+ "{ step: start, then: end, action: { class: '" + Stm.A + "', method: withArg, input: { a: 1 } } }, "
				+ "{ step: end, control: stop } ] }")));
		assertTrue(e.getMessage().contains("input is a map, but com.easysoft.stm.config.FlowActions has no public method withArg(context, Map<String, String>)"),
				e.getMessage());
		e = assertThrows(STMException.class, () -> build(machine("{ name: s, steps: [ "
				+ "{ step: start, then: end, action: { class: '" + Stm.A + "', method: withSettings, input: hello } }, "
				+ "{ step: end, control: stop } ] }")));
		assertTrue(e.getMessage().contains("input is a text, but com.easysoft.stm.config.FlowActions has no public method withSettings(context, String)"),
				e.getMessage());
	}

	@Test
	void inputCannotBeAList() {
		STMException e = assertThrows(STMException.class, () -> build(machine("{ name: s, steps: [ "
				+ "{ step: start, then: end, action: { class: '" + Stm.A + "', method: withArg, input: [ a, b ] } }, "
				+ "{ step: end, control: stop } ] }")));
		assertTrue(e.getMessage().contains("input takes a text or a map of names to texts, not a list"), e.getMessage());
	}

	@Test
	void argParameterPassesAFixedStringToTheMethod() {
		STMStateful<STMContext> stm = build(machine("{ name: s, steps: [ "
				+ "{ step: start, then: end, action: { class: '" + Stm.A + "', method: withArg, input: hello } }, "
				+ "{ step: end, control: stop } ] }"));

		assertEquals(List.of("arg:hello"), run(stm).log);
		assertInstanceOf(MethodWithStringArg.class, transition(stm, "s", "start").getActor());
	}

	@Test
	void lookupMethodSuppliesTheSecondArgument() {
		STMStateful<STMContext> stm = build(machine("{ name: s, steps: [ "
				+ "{ step: start, then: end, action: { class: '" + Stm.A + "', method: useLookup, lookupMethod: lookupSource } }, "
				+ "{ step: end, control: stop } ] }"));

		assertEquals(List.of("lookup:looked-up"), run(stm).log);
		assertInstanceOf(MethodWithLookupArg.class, transition(stm, "s", "start").getActor());
	}

	@Test
	void lastResultIsAvailableToTheNextActor() {
		STMStateful<STMContext> stm = build(machine("{ name: s, steps: [ "
				+ "{ step: start, then: next, action: " + actor("a") + " }, "
				+ "{ step: next, then: end, action: " + actor("lastResult") + " }, "
				+ "{ step: end, control: stop } ] }"));

		assertEquals(List.of("a", "last:A"), run(stm).log);
	}

	@Test
	void stmEventOnAnActorTransitionStopsAfterIt() {
		STMStateful<STMContext> stm = build(machine("{ name: s, steps: [ "
				+ "{ step: start, then: next, control: stop, action: " + actor("a") + " }, "
				+ "{ step: next, then: end, action: " + actor("b") + " }, "
				+ "{ step: end, control: stop } ] }"));

		assertEquals(List.of("a"), run(stm).log);
		assertInstanceOf(StmEventBasedTransition.class, transition(stm, "s", "start"));
	}

	@Test
	void placeHolderChainsAreCollapsedByReduction() {
		String yaml = machine("{ name: s, steps: [ "
				+ "{ step: start, then: hop1 }, "
				+ "{ step: hop1, then: hop2 }, "
				+ "{ step: hop2, then: end, action: " + actor("a") + " }, "
				+ "{ step: end, control: stop } ] }");

		STMStateful<STMContext> reduced = builder(yaml, true).build();
		Transition start = transition(reduced, "s", "start");
		assertFalse(start instanceof PlaceHolderTransition, "start is replaced by the first real transition");
		assertInstanceOf(MethodActor.class, start.getActor());
		assertEquals(List.of("a"), run(reduced).log);

		STMStateful<STMContext> unreduced = builder(yaml, false).build();
		assertInstanceOf(PlaceHolderTransition.class, transition(unreduced, "s", "start"));
		assertEquals(List.of("a"), run(unreduced).log);
	}

	@Test
	void startFromANamedStateAndEvent() {
		STMStateful<STMContext> stm = build(machine("{ name: s, steps: [ "
				+ "{ step: start, then: next, action: " + actor("a") + " }, "
				+ "{ step: next, then: end, action: " + actor("b") + " }, "
				+ "{ step: end, control: stop } ] }"));
		TestContext ctx = new TestContext(stm.createContext());
		ctx.getSTMContext().start(ctx, "s", "next");
		assertEquals(List.of("b"), ctx.log);
	}

	@Test
	void stringValueResolverResolvesPlaceholders() {
		String yaml = machine("{ name: '${state}', steps: [ "
				+ "{ step: start, then: '${next}', action: " + actor("a") + " }, "
				+ "{ step: go, then: end, action: " + actor("b") + " }, "
				+ "{ step: end, control: stop } ] }");
		STMOptimizedBuilder<STMContext> b = new STMOptimizedBuilder<>(null, false,
				value -> value.replace("${state}", "resolved").replace("${next}", "go"));
		b.add(new ConfigYAMLReader(), yaml, "resolver-test.yaml");
		STMStateful<STMContext> stm = b.build();

		assertNotNull(stm.getState("resolved"));
		assertEquals(List.of("a", "b"), run(stm).log);
	}

	@Test
	void enumPlaceholdersResolveToTheConstantName() {
		String e = OrderEvent.class.getName();
		STMStateful<STMContext> stm = build(machine("{ name: s, steps: [ "
				+ "{ step: 'e:" + e + ".CREATED', then: 'e:" + e + ".SHIPPED', action: " + actor("a") + " }, "
				+ "{ step: SHIPPED, control: stop, action: " + actor("b") + " } ] }"));

		assertNotNull(stm.getEvent("CREATED"));
		assertEquals(List.of("a", "b"), run(stm).log);
	}

	@Test
	void builderExposesNameAndDebugFlag() {
		STMOptimizedBuilder<STMContext> b = builder(machine("{ name: s, steps: [ "
				+ "{ step: go, control: stop, action: " + actor("a") + " } ] }"));
		assertEquals("test.flow", b.getFsmName());
		assertFalse(b.isDebug());
		b.setDebug(true);
		assertTrue(b.isDebug());
		assertEquals(List.of("a"), run(b.build()).log);
	}

	@Test
	void buildCanBeCalledMoreThanOnce() {
		STMOptimizedBuilder<STMContext> b = builder(machine("{ name: s, steps: [ "
				+ "{ step: go, control: stop, action: " + actor("a") + " } ] }"));
		STMStateful<STMContext> first = b.build();
		STMStateful<STMContext> second = b.build();
		assertFalse(first.equals(second), "each build gets a new id");
		assertEquals(List.of("a"), run(second).log);
	}
}

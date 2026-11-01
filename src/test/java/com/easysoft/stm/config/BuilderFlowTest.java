package com.easysoft.stm.config;

import static com.easysoft.stm.config.Stm.actor;
import static com.easysoft.stm.config.Stm.build;
import static com.easysoft.stm.config.Stm.builder;
import static com.easysoft.stm.config.Stm.condition;
import static com.easysoft.stm.config.Stm.machine;
import static com.easysoft.stm.config.Stm.run;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.easysoft.stm.ConditionAndActor;
import com.easysoft.stm.ConditionOnly;
import com.easysoft.stm.MethodActor;
import com.easysoft.stm.MethodWithLookupArg;
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
				"<State name='s'>"
						+ "<Transition when='start' to='s' fire='two'>" + actor("a") + "</Transition>"
						+ "<Transition when='two' to='this' fire='three'>" + actor("b") + "</Transition>"
						+ "<Transition when='three' fire='done'>" + actor("c") + "</Transition>"
						+ "<Transition when='done' stmevent='stop'/>"
						+ "</State>"));

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
				"<State name='first'><Transition when='go' fire='end'>" + actor("a") + "</Transition><Transition when='end' stmevent='stop'/></State>"
						+ "<State name='second'><Transition when='go2' fire='end2'>" + actor("b") + "</Transition><Transition when='end2' stmevent='stop'/></State>"));
		assertEquals("first", stm.getDefaultStartState().getName());
		assertEquals("go", stm.getDefaultStartEvent().getName());
	}

	@Test
	void startAndFireAttributesOverrideTheDefaultStart() {
		STMStateful<STMContext> stm = build(machine("start='second' fire='go2'",
				"<State name='first'><Transition when='go' fire='end'>" + actor("a") + "</Transition><Transition when='end' stmevent='stop'/></State>"
						+ "<State name='second'><Transition when='go2' fire='end2'>" + actor("b") + "</Transition><Transition when='end2' stmevent='stop'/></State>"));

		assertEquals(List.of("b"), run(stm).log);
	}

	@Test
	void defaultStartEventIgnoresCallElements() {
		STMStateful<STMContext> stm = build(machine("<State name='main'>"
				+ "<Call when='first' to='sub' fire='sub.go'><Case when='default' fire='end'/></Call>"
				+ "<Transition when='end' stmevent='stop'>" + actor("c") + "</Transition>"
				+ "</State>"
				+ "<State name='sub'><Transition when='sub.go' fire='sub.done'>" + actor("a") + "</Transition><Return when='sub.done'/></State>"));
		assertEquals("end", stm.getDefaultStartEvent().getName());
	}

	@Test
	void transitionsCanMoveBetweenStates() {
		STMStateful<STMContext> stm = build(machine(
				"<State name='cart'><Transition when='checkout' to='payment' fire='pay'>" + actor("a") + "</Transition></State>"
						+ "<State name='payment'><Transition when='pay' to='done' fire='finish'>" + actor("b") + "</Transition></State>"
						+ "<State name='done'><Transition when='finish' stmevent='stop'>" + actor("c") + "</Transition></State>"));

		assertEquals(List.of("a", "b", "c"), run(stm).log);
	}

	@Test
	void conditionTrueContinuesAndFalseGoesToGlobalOnFailedCondition() {
		String xml = machine("<State name='s'>"
				+ "<Transition when='start' fire='work'>" + condition("flag") + "</Transition>"
				+ "<Transition when='work' fire='end'>" + actor("a") + "</Transition>"
				+ "<Transition when='end' stmevent='stop'/>"
				+ "</State>");
		STMStateful<STMContext> stm = build(xml);

		assertEquals(List.of("flag", "a"), run(stm, "flag", true).log);
		assertEquals(List.of("flag", "onRejected"), run(stm, "flag", false).log);
		assertInstanceOf(ConditionOnly.class, transition(stm, "s", "start"));
	}

	@Test
	void localOnFailedConditionOverridesGlobal() {
		STMStateful<STMContext> stm = build(machine("<State name='s'>"
				+ "<Transition when='start' fire='work'>" + condition("flag")
				+ "<OnFailedCondition to='this' fire='rejected'/></Transition>"
				+ "<Transition when='work' fire='end'>" + actor("a") + "</Transition>"
				+ "<Transition when='rejected' fire='end'>" + actor("localRejected") + "</Transition>"
				+ "<Transition when='end' stmevent='stop'/>"
				+ "</State>"));

		assertEquals(List.of("flag", "localRejected"), run(stm, "flag", false).log);
	}

	@Test
	void conditionAndActorOnTheSameTransition() {
		STMStateful<STMContext> stm = build(machine("<State name='s'>"
				+ "<Transition when='start' fire='end'>" + condition("flag") + actor("a") + "</Transition>"
				+ "<Transition when='end' stmevent='stop'/>"
				+ "</State>"));

		assertInstanceOf(ConditionAndActor.class, transition(stm, "s", "start"));
		assertEquals(List.of("flag", "a"), run(stm, "flag", true).log);
		assertEquals(List.of("flag", "onRejected"), run(stm, "flag", false).log);
	}

	@Test
	void exceptionGoesToGlobalOnErrorWithTheException() {
		STMStateful<STMContext> stm = build(machine("<State name='s'>"
				+ "<Transition when='start' fire='next'>" + actor("a") + "</Transition>"
				+ "<Transition when='next' fire='end'>" + actor("boom") + "</Transition>"
				+ "<Transition when='end' stmevent='stop'/>"
				+ "</State>"));

		assertEquals(List.of("a", "boom", "onError:boom"), run(stm).log);
	}

	@Test
	void localOnErrorOverridesGlobal() {
		STMStateful<STMContext> stm = build(machine("<State name='s'>"
				+ "<Transition when='start' fire='end'>" + actor("boom") + "<OnError to='this' fire='oops'/></Transition>"
				+ "<Transition when='oops' fire='end'>" + actor("localError") + "</Transition>"
				+ "<Transition when='end' stmevent='stop'/>"
				+ "</State>"));

		assertEquals(List.of("boom", "localError"), run(stm).log);
	}

	@Test
	void casesRouteOnTheActorResult() {
		STMStateful<STMContext> stm = build(machine("<State name='s'>"
				+ "<Transition when='start' fire='standard'>" + actor("route")
				+ "  <Case when='EXPRESS' to='this' fire='express'/>"
				+ "  <Case when='PICKUP' fire='pickup'/>"
				+ "</Transition>"
				+ "<Transition when='express' fire='end'>" + actor("a") + "</Transition>"
				+ "<Transition when='pickup' fire='end'>" + actor("b") + "</Transition>"
				+ "<Transition when='standard' fire='end'>" + actor("c") + "</Transition>"
				+ "<Transition when='end' stmevent='stop'/>"
				+ "</State>"));

		assertEquals(List.of("route", "a"), run(stm, "route", "EXPRESS").log);
		assertEquals(List.of("route", "b"), run(stm, "route", "PICKUP").log);
		assertEquals(List.of("route", "c"), run(stm, "route", "UNKNOWN").log, "no match and no default: follow fire");
	}

	@Test
	void defaultCaseCatchesUnmatchedResults() {
		STMStateful<STMContext> stm = build(machine("<State name='s'>"
				+ "<Transition when='start' fire='standard'>" + actor("route")
				+ "  <Case when='EXPRESS' fire='express'/>"
				+ "  <Case when='default' fire='fallback'/>"
				+ "</Transition>"
				+ "<Transition when='express' fire='end'>" + actor("a") + "</Transition>"
				+ "<Transition when='fallback' fire='end'>" + actor("b") + "</Transition>"
				+ "<Transition when='standard' fire='end'>" + actor("c") + "</Transition>"
				+ "<Transition when='end' stmevent='stop'/>"
				+ "</State>"));

		assertEquals(List.of("route", "b"), run(stm, "route", "UNKNOWN").log);
	}

	@Test
	void callRunsASubFlowAndReturnRoutesOnTheReturnEvent() {
		STMStateful<STMContext> stm = build(machine("<State name='main'>"
				+ "<Transition when='start' fire='verify'>" + actor("a") + "</Transition>"
				+ "<Call when='verify' to='check.s' fire='check.start'>"
				+ "  <Case when='check.ok' to='this' fire='approved'/>"
				+ "  <Case when='default' to='this' fire='declined'/>"
				+ "</Call>"
				+ "<Transition when='approved' fire='end'>" + actor("b") + "</Transition>"
				+ "<Transition when='declined' fire='end'>" + actor("c") + "</Transition>"
				+ "<Transition when='end' stmevent='stop'/>"
				+ "</State>"
				+ "<State name='check.s'>"
				+ "<Transition when='check.start' fire='check.ok'>" + condition("flag")
				+ "  <OnFailedCondition to='this' fire='check.failed'/></Transition>"
				+ "<Return when='check.ok'/>"
				+ "<Return when='check.failed'/>"
				+ "</State>"));

		assertEquals(List.of("a", "flag", "b"), run(stm, "flag", true).log);
		assertEquals(List.of("a", "flag", "c"), run(stm, "flag", false).log);
	}

	@Test
	void callCanBeReusedFromTwoPlaces() {
		// start/fire are required here: the default start event is the first <Transition>, and <Call> elements are not considered.
		STMStateful<STMContext> stm = build(machine("start='main' fire='start'", "<State name='main'>"
				+ "<Call when='start' to='sub' fire='sub.go'><Case when='default' fire='second'/></Call>"
				+ "<Call when='second' to='sub' fire='sub.go'><Case when='default' fire='end'/></Call>"
				+ "<Transition when='end' stmevent='stop'>" + actor("c") + "</Transition>"
				+ "</State>"
				+ "<State name='sub'>"
				+ "<Transition when='sub.go' fire='sub.done'>" + actor("a") + "</Transition>"
				+ "<Return when='sub.done'/>"
				+ "</State>"));

		assertEquals(List.of("a", "a", "c"), run(stm).log);
	}

	@Test
	void returnWithoutAMatchingCallIsAnError() {
		STMStateful<STMContext> stm = build(machine("<State name='s'><Return when='start'/></State>"));
		List<String> log = run(stm).log;
		assertEquals(1, log.size());
		assertTrue(log.get(0).startsWith("onError:Returned from state without any call"), log.get(0));
	}

	@Test
	void argParameterPassesAFixedStringToTheMethod() {
		STMStateful<STMContext> stm = build(machine("<State name='s'>"
				+ "<Transition when='start' fire='end'><Actor className='" + Stm.A + "' methodName='withArg' argParameter='hello'/></Transition>"
				+ "<Transition when='end' stmevent='stop'/>"
				+ "</State>"));

		assertEquals(List.of("arg:hello"), run(stm).log);
		assertInstanceOf(MethodWithStringArg.class, transition(stm, "s", "start").getActor());
	}

	@Test
	void lookupMethodSuppliesTheSecondArgument() {
		STMStateful<STMContext> stm = build(machine("<State name='s'>"
				+ "<Transition when='start' fire='end'><Actor className='" + Stm.A + "' methodName='useLookup' lookupMethod='lookupSource'/></Transition>"
				+ "<Transition when='end' stmevent='stop'/>"
				+ "</State>"));

		assertEquals(List.of("lookup:looked-up"), run(stm).log);
		assertInstanceOf(MethodWithLookupArg.class, transition(stm, "s", "start").getActor());
	}

	@Test
	void lastResultIsAvailableToTheNextActor() {
		STMStateful<STMContext> stm = build(machine("<State name='s'>"
				+ "<Transition when='start' fire='next'>" + actor("a") + "</Transition>"
				+ "<Transition when='next' fire='end'>" + actor("lastResult") + "</Transition>"
				+ "<Transition when='end' stmevent='stop'/>"
				+ "</State>"));

		assertEquals(List.of("a", "last:A"), run(stm).log);
	}

	@Test
	void stmEventOnAnActorTransitionStopsAfterIt() {
		STMStateful<STMContext> stm = build(machine("<State name='s'>"
				+ "<Transition when='start' fire='next' stmevent='stop'>" + actor("a") + "</Transition>"
				+ "<Transition when='next' fire='end'>" + actor("b") + "</Transition>"
				+ "<Transition when='end' stmevent='stop'/>"
				+ "</State>"));

		assertEquals(List.of("a"), run(stm).log);
		assertInstanceOf(StmEventBasedTransition.class, transition(stm, "s", "start"));
	}

	@Test
	void placeHolderChainsAreCollapsedByReduction() {
		String xml = machine("<State name='s'>"
				+ "<Transition when='start' fire='hop1'/>"
				+ "<Transition when='hop1' fire='hop2'/>"
				+ "<Transition when='hop2' fire='end'>" + actor("a") + "</Transition>"
				+ "<Transition when='end' stmevent='stop'/>"
				+ "</State>");

		STMStateful<STMContext> reduced = builder(xml, true).build();
		Transition start = transition(reduced, "s", "start");
		assertFalse(start instanceof PlaceHolderTransition, "start is replaced by the first real transition");
		assertInstanceOf(MethodActor.class, start.getActor());
		assertEquals(List.of("a"), run(reduced).log);

		STMStateful<STMContext> unreduced = builder(xml, false).build();
		assertInstanceOf(PlaceHolderTransition.class, transition(unreduced, "s", "start"));
		assertEquals(List.of("a"), run(unreduced).log);
	}

	@Test
	void startFromANamedStateAndEvent() {
		STMStateful<STMContext> stm = build(machine("<State name='s'>"
				+ "<Transition when='start' fire='next'>" + actor("a") + "</Transition>"
				+ "<Transition when='next' fire='end'>" + actor("b") + "</Transition>"
				+ "<Transition when='end' stmevent='stop'/>"
				+ "</State>"));
		TestContext ctx = new TestContext(stm.createContext());
		ctx.getSTMContext().start(ctx, "s", "next");
		assertEquals(List.of("b"), ctx.log);
	}

	@Test
	void stringValueResolverResolvesPlaceholders() {
		String xml = machine("<State name='${state}'>"
				+ "<Transition when='start' fire='${next}'>" + actor("a") + "</Transition>"
				+ "<Transition when='go' fire='end'>" + actor("b") + "</Transition>"
				+ "<Transition when='end' stmevent='stop'/>"
				+ "</State>");
		STMOptimizedBuilder<STMContext> b = new STMOptimizedBuilder<>(null, false,
				value -> value.replace("${state}", "resolved").replace("${next}", "go"));
		b.add(new ConfigXMLReader(), xml, "resolver-test.xml");
		STMStateful<STMContext> stm = b.build();

		assertNotNull(stm.getState("resolved"));
		assertEquals(List.of("a", "b"), run(stm).log);
	}

	@Test
	void enumPlaceholdersResolveToTheConstantName() {
		String e = OrderEvent.class.getName();
		STMStateful<STMContext> stm = build(machine("<State name='s'>"
				+ "<Transition when='e:" + e + ".CREATED' fire='e:" + e + ".SHIPPED'>" + actor("a") + "</Transition>"
				+ "<Transition when='SHIPPED' stmevent='stop'>" + actor("b") + "</Transition>"
				+ "</State>"));

		assertNotNull(stm.getEvent("CREATED"));
		assertEquals(List.of("a", "b"), run(stm).log);
	}

	@Test
	void builderExposesNameAndDebugFlag() {
		STMOptimizedBuilder<STMContext> b = builder(machine("<State name='s'><Transition when='go' stmevent='stop'>" + actor("a") + "</Transition></State>"));
		assertEquals("test.flow", b.getFsmName());
		assertFalse(b.isDebug());
		b.setDebug(true);
		assertTrue(b.isDebug());
		assertEquals(List.of("a"), run(b.build()).log);
	}

	@Test
	void buildCanBeCalledMoreThanOnce() {
		STMOptimizedBuilder<STMContext> b = builder(machine("<State name='s'><Transition when='go' stmevent='stop'>" + actor("a") + "</Transition></State>"));
		STMStateful<STMContext> first = b.build();
		STMStateful<STMContext> second = b.build();
		assertFalse(first.equals(second), "each build gets a new id");
		assertEquals(List.of("a"), run(second).log);
	}
}

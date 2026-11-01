package com.easysoft.stm.config;

import static com.easysoft.stm.config.Stm.actor;
import static com.easysoft.stm.config.Stm.build;
import static com.easysoft.stm.config.Stm.builder;
import static com.easysoft.stm.config.Stm.condition;
import static com.easysoft.stm.config.Stm.machine;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.easysoft.stm.STMContext;
import com.easysoft.stm.STMException;

/** The builder must reject invalid configurations with a clear {@link STMException}. */
class BuilderValidationTest {

	private static final String END = "<Transition when='end' stmevent='stop'/>";

	private static void assertRejected(String xml, String expectedMessagePart) {
		assertRejected(xml, true, expectedMessagePart);
	}

	private static void assertRejected(String xml, boolean reduce, String expectedMessagePart) {
		STMException e = assertThrows(STMException.class, () -> builder(xml, reduce).build());
		assertTrue(e.getMessage().contains(expectedMessagePart), "expected message containing '" + expectedMessagePart + "' but was: " + e.getMessage());
	}

	@Test
	void emptyMachineHasNoStartState() {
		assertRejected("<STM name='empty'/>", "Default state for stmStateful missing");
	}

	@Test
	void duplicateWhenInTheSameState() {
		assertRejected(machine("<State name='s'>"
				+ "<Transition when='start' fire='end'>" + actor("a") + "</Transition>"
				+ "<Transition when='start' fire='end'>" + actor("b") + "</Transition>"
				+ END + "</State>"), "duplicated in the same state s when=start");
	}

	@Test
	void sameStateDefinedTwice() {
		assertRejected(machine("<State name='s'><Transition when='start' fire='end'>" + actor("a") + "</Transition>" + END + "</State>"
				+ "<State name='s'><Transition when='other' fire='end'>" + actor("a") + "</Transition></State>"), "State s already defined");
	}

	@Test
	void stateWithoutName() {
		assertRejected(machine("<State><Transition when='start' stmevent='stop'>" + actor("a") + "</Transition></State>"), "cannot be null");
	}

	@Test
	void transitionWithoutWhen() {
		assertRejected(machine("<State name='s'><Transition fire='end'>" + actor("a") + "</Transition>" + END + "</State>"), "<when> missing");
	}

	@Test
	void toWithoutFire() {
		assertRejected(machine("<State name='s'><Transition when='start' to='s'>" + actor("a") + "</Transition></State>"), "both <fire><to> should be defined");
	}

	@Test
	void fireThatGoesNowhere() {
		assertRejected(machine("<State name='s'><Transition when='start' fire='missing'>" + actor("a") + "</Transition></State>"), "Transition missing");
	}

	@Test
	void missingOnErrorWhenNoGlobalHandler() {
		assertRejected("<STM name='x'><State name='s'><Transition when='start' fire='end'>" + actor("a") + "</Transition>" + END + "</State></STM>",
				"Missing local <OnError> and global <OnError>");
	}

	@Test
	void conditionWithoutAnyOnFailedCondition() {
		assertRejected("<STM name='x'><OnError to='s' fire='end'/><State name='s'>"
				+ "<Transition when='start' fire='end'>" + condition("flag") + "</Transition>"
				+ "<Transition when='end' stmevent='stop'>" + actor("a") + "</Transition></State></STM>",
				"Missing local <OnFailedCondition> and global <onFailedCondition>");
	}

	@Test
	void onErrorPointingToUnknownState() {
		assertRejected(machine("<State name='s'><Transition when='start' fire='end'>" + actor("a")
				+ "<OnError to='nowhere' fire='x'/></Transition>" + END + "</State>"), "state not exist for <state> nowhere");
	}

	@Test
	void onErrorPointingToUnknownEvent() {
		assertRejected(machine("<State name='s'><Transition when='start' fire='end'>" + actor("a")
				+ "<OnError to='this' fire='nope'/></Transition>" + END + "</State>"), "transition not exist for <fire> nope");
	}

	@Test
	void callWithoutReturn() {
		assertRejected(machine("start='main' fire='start'", "<State name='main'>"
				+ "<Call when='start' to='sub' fire='go'><Case when='default' fire='end'/></Call>" + END + "</State>"
				+ "<State name='sub'><Transition when='go' stmevent='stop'>" + actor("a") + "</Transition></State>"),
				"Call state dont have return defined sub");
	}

	@Test
	void callWithoutDefaultCase() {
		assertRejected(machine("start='main' fire='start'", "<State name='main'>"
				+ "<Call when='start' to='sub' fire='go'><Case when='ok' fire='end'/></Call>" + END + "</State>"
				+ "<State name='sub'><Transition when='go' fire='ok'>" + actor("a") + "</Transition><Return when='ok'/></State>"),
				"default as it is mandatory for Call");
	}

	@Test
	void plainTransitionIntoAReturnState() {
		assertRejected(machine("start='main' fire='start'", "<State name='main'>"
				+ "<Call when='start' to='sub' fire='go'><Case when='default' fire='end'/></Call>"
				+ "<Transition when='sneak' to='sub' fire='go'>" + actor("b") + "</Transition>"
				+ END + "</State>"
				+ "<State name='sub'><Transition when='go' fire='ok'>" + actor("a") + "</Transition><Return when='ok'/></State>"),
				"To State dont have any call or Return node");
	}

	@Test
	void caseOnATransitionWithoutActor() {
		assertRejected(machine("<State name='s'><Transition when='start' fire='end'><Case when='x' fire='end'/></Transition>" + END + "</State>"),
				"cases not allowed for place holder transition");
	}

	@Test
	void caseOnAConditionOnlyTransition() {
		assertRejected(machine("<State name='s'><Transition when='start' fire='end'>" + condition("flag")
				+ "<Case when='x' fire='end'/></Transition>" + END + "</State>"), "condition with out any handler");
	}

	@Test
	void caseWithoutFire() {
		assertRejected(machine("<State name='s'><Transition when='start' fire='end'>" + actor("route")
				+ "<Case when='x'/></Transition>" + END + "</State>"), "Missing <case> <when> x");
	}

	@Test
	void caseToUnknownTransition() {
		assertRejected(machine("<State name='s'><Transition when='start' fire='end'>" + actor("route")
				+ "<Case when='x' fire='start' to='error.s'/></Transition>" + END + "</State>"), "Missing transition for <case> <when> x");
	}

	@Test
	void placeHolderThatLoopsToItself() {
		String xml = machine("<State name='s'><Transition when='start' fire='start'/></State>");
		assertRejected(xml, true, "Loop on the transiton");
		assertRejected(xml, false, "Transition is looping");
	}

	@Test
	void placeHolderCycleIsDetected() {
		assertRejected(machine("<State name='s'><Transition when='start' fire='p1'/><Transition when='p1' fire='start'/></State>"),
				"Loop on the transiton");
	}

	@Test
	void unknownActorMethod() {
		assertRejected(machine("<State name='s'><Transition when='start' fire='end'>" + actor("noSuchMethod") + "</Transition>" + END + "</State>"),
				"handler is null");
	}

	@Test
	void conditionMethodMustReturnBoolean() {
		assertRejected(machine("<State name='s'><Transition when='start' fire='end'>" + condition("notABoolean") + "</Transition>" + END + "</State>"),
				"handler is null");
	}

	@Test
	void unknownActorClass() {
		assertRejected(machine("<State name='s'><Transition when='start' fire='end'><Actor className='com.example.DoesNotExist'/></Transition>" + END + "</State>"),
				"create handler instance failed");
	}

	@Test
	void reactiveConfigurationIsNotSupported() {
		assertThrows(STMException.class, () -> new STMOptimizedBuilder<STMContext>(null, false).add(new ConfigXMLReader(),
				machine("<State name='s'><Transition when='start' fire='end' reactive='true'>" + actor("a") + "</Transition>" + END + "</State>"),
				"reactive.xml"));
	}

	@Test
	void unknownEnumPlaceholder() {
		assertThrows(STMException.class, () -> build(machine("<State name='s'><Transition when='e:com.example.Nope.X' stmevent='stop'>"
				+ actor("a") + "</Transition></State>")));
	}

	@Test
	void unknownStmEventValue() {
		assertThrows(IllegalArgumentException.class,
				() -> build(machine("<State name='s'><Transition when='start' fire='end' stmevent='pause'>" + actor("a") + "</Transition>" + END + "</State>")));
	}

	@Test
	void malformedXml() {
		assertThrows(STMException.class, () -> new STMOptimizedBuilder<STMContext>(null, false).add(new ConfigXMLReader(), "<STM name='x'><State>", "bad.xml"));
	}

	@Test
	void validateOnlyModeChecksStructureWithoutCreatingActors() {
		// Actor classes are not loaded in validate-only mode, so a missing class is not an error...
		STMOptimizedBuilder<STMContext> b = new STMOptimizedBuilder<>(null, true);
		b.add(new ConfigXMLReader(), machine("<State name='s'><Transition when='start' fire='end'><Actor className='com.example.DoesNotExist'/></Transition>" + END
				+ "</State>"), "validate-only-ok.xml");
		b.build();

		// ...but structural problems still are.
		STMOptimizedBuilder<STMContext> bad = new STMOptimizedBuilder<>(null, true);
		bad.add(new ConfigXMLReader(), machine("<State name='s'><Transition when='start' fire='missing'>" + actor("a") + "</Transition></State>"),
				"validate-only-bad.xml");
		assertThrows(STMException.class, bad::build);
	}
}

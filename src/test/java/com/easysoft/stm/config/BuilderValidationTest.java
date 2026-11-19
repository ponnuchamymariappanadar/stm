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

	private static final String END = "{ step: end, control: stop }";

	private static void assertRejected(String yaml, String expectedMessagePart) {
		assertRejected(yaml, true, expectedMessagePart);
	}

	private static void assertRejected(String yaml, boolean reduce, String expectedMessagePart) {
		STMException e = assertThrows(STMException.class, () -> builder(yaml, reduce).build());
		assertTrue(e.getMessage().contains(expectedMessagePart), "expected message containing '" + expectedMessagePart + "' but was: " + e.getMessage());
	}

	@Test
	void emptyMachineHasNoStartState() {
		assertRejected("{ name: empty }", "Default state for stmStateful missing");
	}

	@Test
	void duplicateWhenInTheSameState() {
		assertRejected(machine("{ name: s, steps: [ "
				+ "{ step: start, then: end, action: " + actor("a") + " }, "
				+ "{ step: start, then: end, action: " + actor("b") + " }, "
				+ END + " ] }"), "duplicated in the same state s step=start");
	}

	@Test
	void sameStateDefinedTwice() {
		assertRejected(machine("{ name: s, steps: [ "
				+ "{ step: start, then: end, action: " + actor("a") + " }, "
				+ END + " ] }, "
				+ "{ name: s, steps: [ "
				+ "{ step: other, then: end, action: " + actor("a") + " } ] }"), "State s already defined");
	}

	@Test
	void stateWithoutName() {
		assertRejected(machine("{ steps: [ "
				+ "{ step: start, control: stop, action: " + actor("a") + " } ] }"), "cannot be null");
	}

	@Test
	void transitionWithoutWhen() {
		assertRejected(machine("{ name: s, steps: [ "
				+ "{ then: end, action: " + actor("a") + " }, "
				+ END + " ] }"), "step missing");
	}

	@Test
	void toWithoutFire() {
		assertRejected(machine("{ name: s, steps: [ "
				+ "{ step: start, nextStage: s, action: " + actor("a") + " } ] }"), "both then and nextStage should be defined");
	}

	@Test
	void fireThatGoesNowhere() {
		assertRejected(machine("{ name: s, steps: [ "
				+ "{ step: start, then: missing, action: " + actor("a") + " } ] }"), "Transition missing");
	}

	@Test
	void missingOnErrorWhenNoGlobalHandler() {
		assertRejected("{ name: x, stages: [ "
				+ "{ name: s, steps: [ "
				+ "{ step: start, then: end, action: " + actor("a") + " }, "
				+ END + " ] } ] }",
				"Missing onError on the step and under defaults");
	}

	@Test
	void conditionWithoutAnyOnFailedCondition() {
		assertRejected("{ name: x, defaults: { onError: { nextStage: s, then: end } }, stages: [ "
				+ "{ name: s, steps: [ "
				+ "{ step: start, then: end, check: " + condition("flag") + " }, "
				+ "{ step: end, control: stop, action: " + actor("a") + " } ] } ] }",
				"Missing onFalse on the step and under defaults");
	}

	@Test
	void onErrorPointingToUnknownState() {
		assertRejected(machine("{ name: s, steps: [ "
				+ "{ step: start, then: end, action: " + actor("a") + ", onError: { nextStage: nowhere, then: x } }, "
				+ END + " ] }"), "state not exist for stage nowhere");
	}

	@Test
	void onErrorPointingToUnknownEvent() {
		assertRejected(machine("{ name: s, steps: [ "
				+ "{ step: start, then: end, action: " + actor("a") + ", onError: { nextStage: this, then: nope } }, "
				+ END + " ] }"), "transition not exist for then nope");
	}

	@Test
	void callWithoutReturn() {
		assertRejected(machine("startStage: main, startStep: start", "{ name: main, steps: [ "
				+ "{ step: start, runStage: sub, then: go, otherwise: { then: end } }, "
				+ END + " ] }, "
				+ "{ name: sub, steps: [ "
				+ "{ step: go, control: stop, action: " + actor("a") + " } ] }"),
				"Call state dont have return defined sub");
	}

	@Test
	void callWithoutDefaultCase() {
		assertRejected(machine("startStage: main, startStep: start", "{ name: main, steps: [ "
				+ "{ step: start, runStage: sub, then: go, outcomes: [ { when: ok, then: end } ] }, "
				+ END + " ] }, "
				+ "{ name: sub, steps: [ "
				+ "{ step: go, then: ok, action: " + actor("a") + " } ], returns: [ "
				+ "{ step: ok } ] }"),
				"Missing otherwise, which is mandatory for a runStage step");
	}

	@Test
	void plainTransitionIntoAReturnState() {
		assertRejected(machine("startStage: main, startStep: start", "{ name: main, steps: [ "
				+ "{ step: start, runStage: sub, then: go, otherwise: { then: end } }, "
				+ "{ step: sneak, nextStage: sub, then: go, action: " + actor("b") + " }, "
				+ END + " ] }, "
				+ "{ name: sub, steps: [ "
				+ "{ step: go, then: ok, action: " + actor("a") + " } ], returns: [ "
				+ "{ step: ok } ] }"),
				"To State dont have any runStage step or returns entry");
	}

	@Test
	void caseOnATransitionWithoutActor() {
		assertRejected(machine("{ name: s, steps: [ "
				+ "{ step: start, then: end, outcomes: [ { when: x, then: end } ] }, "
				+ END + " ] }"),
				"outcomes not allowed for place holder transition");
	}

	@Test
	void caseOnAConditionOnlyTransition() {
		assertRejected(machine("{ name: s, steps: [ "
				+ "{ step: start, then: end, check: " + condition("flag") + ", outcomes: [ { when: x, then: end } ] }, "
				+ END + " ] }"), "condition with out any handler");
	}

	@Test
	void caseWithoutFire() {
		assertRejected(machine("{ name: s, steps: [ "
				+ "{ step: start, then: end, action: " + actor("route") + ", outcomes: [ { when: x } ] }, "
				+ END + " ] }"), "Missing outcome when x");
	}

	@Test
	void caseToUnknownTransition() {
		assertRejected(machine("{ name: s, steps: [ "
				+ "{ step: start, then: end, action: " + actor("route") + ", outcomes: [ { when: x, then: start, nextStage: error.s } ] }, "
				+ END + " ] }"), "Missing transition for outcome when x");
	}

	@Test
	void placeHolderThatLoopsToItself() {
		String yaml = machine("{ name: s, steps: [ "
				+ "{ step: start, then: start } ] }");
		assertRejected(yaml, true, "Loop on the transiton");
		assertRejected(yaml, false, "Transition is looping");
	}

	@Test
	void placeHolderCycleIsDetected() {
		assertRejected(machine("{ name: s, steps: [ "
				+ "{ step: start, then: p1 }, "
				+ "{ step: p1, then: start } ] }"),
				"Loop on the transiton");
	}

	@Test
	void unknownActorMethod() {
		assertRejected(machine("{ name: s, steps: [ "
				+ "{ step: start, then: end, action: " + actor("noSuchMethod") + " }, "
				+ END + " ] }"),
				"handler is null");
	}

	@Test
	void conditionMethodMustReturnBoolean() {
		assertRejected(machine("{ name: s, steps: [ "
				+ "{ step: start, then: end, check: " + condition("notABoolean") + " }, "
				+ END + " ] }"),
				"handler is null");
	}

	@Test
	void unknownActorClass() {
		assertRejected(machine("{ name: s, steps: [ "
				+ "{ step: start, then: end, action: { class: com.example.DoesNotExist } }, "
				+ END + " ] }"),
				"create handler instance failed");
	}

	@Test
	void reactiveConfigurationIsNotSupported() {
		assertThrows(STMException.class, () -> new STMOptimizedBuilder<STMContext>(null, false).add(new ConfigYAMLReader(),
				machine("{ name: s, steps: [ "
						+ "{ step: start, then: end, reactive: 'true', action: " + actor("a") + " }, "
						+ END + " ] }"),
				"reactive.yaml"));
	}

	@Test
	void unknownEnumPlaceholder() {
		assertThrows(STMException.class, () -> build(machine("{ name: s, steps: [ "
				+ "{ step: 'e:com.example.Nope.X', control: stop, action: " + actor("a") + " } ] }")));
	}

	@Test
	void unknownStmEventValue() {
		assertThrows(IllegalArgumentException.class,
				() -> build(machine("{ name: s, steps: [ "
						+ "{ step: start, then: end, control: pause, action: " + actor("a") + " }, "
						+ END + " ] }")));
	}

	@Test
	void malformedYaml() {
		assertThrows(STMException.class, () -> new STMOptimizedBuilder<STMContext>(null, false).add(new ConfigYAMLReader(), "{ name: x, stages: [ { name: s", "bad.yaml"));
	}

	@Test
	void validateOnlyModeChecksStructureWithoutCreatingActors() {
		// Actor classes are not loaded in validate-only mode, so a missing class is not an error...
		STMOptimizedBuilder<STMContext> b = new STMOptimizedBuilder<>(null, true);
		b.add(new ConfigYAMLReader(), machine("{ name: s, steps: [ "
				+ "{ step: start, then: end, action: { class: com.example.DoesNotExist } }, "
				+ END + " ] }"), "validate-only-ok.yaml");
		b.build();

		// ...but structural problems still are.
		STMOptimizedBuilder<STMContext> bad = new STMOptimizedBuilder<>(null, true);
		bad.add(new ConfigYAMLReader(), machine("{ name: s, steps: [ "
				+ "{ step: start, then: missing, action: " + actor("a") + " } ] }"),
				"validate-only-bad.yaml");
		assertThrows(STMException.class, bad::build);
	}
}

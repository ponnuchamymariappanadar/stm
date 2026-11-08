package com.easysoft.decision;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

/** Checking rule files against the context class when they are loaded. */
class ValidationTest {

	private static final String TYPE = "type: com.easysoft.decision.LoanContext\n";

	private static DecisionRegistry load(String body) {
		DecisionRegistry r = new DecisionRegistry();
		r.loadYaml("name: t\n" + body, "test");
		return r;
	}

	private static String failure(String body) {
		return assertThrows(DecisionException.class, () -> load(TYPE + body), body).getMessage();
	}

	private static void assertProblem(String body, String expected) {
		String message = failure(body);
		assertTrue(message.contains(expected), "expected '" + expected + "' in: " + message);
	}

	private static String condition(String path, String operatorAndValue) {
		return "conditions:\n  - { name: c, path: '" + path + "', " + operatorAndValue + " }\n";
	}

	private static String when(String expression) {
		return "decisions:\n  - name: d\n    when: \"" + expression + "\"\n";
	}

	@Test
	void theExampleFileFitsItsType() {
		DecisionRegistry r = new DecisionRegistry();
		r.load("classpath:decision/loan-rules.yaml");
		assertEquals(LoanContext.class, r.getType("loan.rules"));
		assertDoesNotThrow(() -> r.validate("loan.rules", LoanContext.class));
	}

	@Test
	void reportsPathsTheClassDoesNotHave() {
		assertProblem(condition("customer.vipp", "equal: true"), "condition 'c': path 'customer.vipp': com.easysoft.decision.LoanContext$Customer has no property 'vipp'");
		assertProblem(condition("amountt", "greaterThan: 1"), "has no property 'amountt'");
		assertProblem(when("customer.adress.country == US"), "decision 'd': 'customer.adress.country == US': path 'customer.adress.country'");
		assertProblem(condition("amount[0]", "equal: 1"), "[0] used on long, which is not a list, array or map");
		assertProblem(condition("tags[first]", "equal: x"), "[first] needs a number for java.util.List");
		assertProblem(condition("amount.cents", "equal: 1"), "long has no property 'cents'");
	}

	@Test
	void reportsValuesThatDoNotFitTheType() {
		assertProblem(when("customer.tier == GOLDD"), "path 'customer.tier' (Tier): 'GOLDD' is not one of SILVER, GOLD");
		assertProblem(condition("customer.status", "in: [ACTIVE, CLOSED]"), "'CLOSED' is not one of ACTIVE, BLOCKED");
		assertProblem(when("customer.vip == yes"), "path 'customer.vip' (boolean): 'yes' is not true or false");
		assertProblem(when("amount > lots"), "path 'amount' (long): 'lots' is not a number");
		assertProblem(when("customer.tier > SILVER"), "is an enum and cannot be compared with greaterThan");
		assertProblem(when("customer.vip >= true"), "is a boolean and cannot be compared with greaterOrEqual");
		assertProblem(when("amount is null"), "path 'amount' is a long, which is never null");
		assertProblem(when("amount == null"), "path 'amount' (long) is never null");
		assertProblem(when("customer.since < 2020-13-45"), "'2020-13-45' is not a valid LocalDate");
		assertProblem(when("customer.address < x"), "is not Comparable and cannot be compared with lessThan");
	}

	@Test
	void outcomesAreCheckedToo() {
		assertProblem("outcomes:\n  - name: o\n    rules:\n      - { when: customer.tier == PLATINUM, result: X }\n",
				"outcome 'o' rule 1: 'customer.tier == PLATINUM': path 'customer.tier' (Tier): 'PLATINUM' is not one of SILVER, GOLD");
	}

	@Test
	void everyProblemIsListedAndNothingIsRegistered() {
		DecisionRegistry r = new DecisionRegistry();
		DecisionException e = assertThrows(DecisionException.class, () -> r.loadYaml("name: t\n" + TYPE //
				+ "conditions:\n  - { name: a, path: customer.vipp, equal: true }\n" //
				+ "decisions:\n  - { name: d, when: amount > lots or customer.tier == GOLDD }\n", "test"));
		String message = e.getMessage();
		assertTrue(message.contains("rule set 't' does not fit com.easysoft.decision.LoanContext"), message);
		assertTrue(message.contains("'vipp'") && message.contains("'lots'") && message.contains("'GOLDD'"), message);
		assertEquals(List.of(), r.getRuleSetNames());
		assertNull(r.find("t/a"));
	}

	@Test
	void valuesThatCannotBeKnownStaticallyAreLeftToRunTime() {
		// attributes is Map<String, Object>: anything below it is unknown until the flow runs
		DecisionRegistry r = load(TYPE + when("attributes[risk] == low and attributes.limit.max > 5 and tags[0] == anything and score >= 2.5"));
		assertEquals(LoanContext.class, r.getType("t"));
	}

	@Test
	void withoutATypeNothingIsCheckedAtLoad() {
		DecisionRegistry r = load(condition("customer.vipp", "equal: true"));
		assertNull(r.getType("t"));
		DecisionException e = assertThrows(DecisionException.class, () -> r.validate("t", LoanContext.class));
		assertTrue(e.getMessage().contains("'vipp'"), e.getMessage());
	}

	@Test
	void theTypeCanBeGivenWhenLoading() {
		DecisionRegistry r = new DecisionRegistry();
		DecisionException e = assertThrows(DecisionException.class,
				() -> r.loadYaml("name: t\n" + condition("customer.vipp", "equal: true"), "test", LoanContext.class));
		assertTrue(e.getMessage().contains("'vipp'"), e.getMessage());
		// the argument overrides the file's type
		DecisionRegistry other = new DecisionRegistry();
		assertDoesNotThrow(() -> other.loadYaml("name: t\ntype: java.lang.String\n" + condition("amount", "equal: 1"), "test", LoanContext.class));
		assertEquals(LoanContext.class, other.getType("t"));
	}

	@Test
	void unknownTypeAndUnknownRuleSet() {
		DecisionException e = assertThrows(DecisionException.class, () -> load("type: com.example.Missing\n" + condition("amount", "equal: 1")));
		assertTrue(e.getMessage().contains("type com.example.Missing is not on the classpath"), e.getMessage());
		e = assertThrows(DecisionException.class, () -> new DecisionRegistry().validate("nope", LoanContext.class));
		assertTrue(e.getMessage().contains("rule set 'nope' is not loaded"), e.getMessage());
	}
}

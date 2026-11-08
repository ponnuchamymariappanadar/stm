package com.easysoft.decision;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.easysoft.decision.LoanContext.Address;
import com.easysoft.decision.LoanContext.Customer;
import com.easysoft.decision.LoanContext.Status;
import com.easysoft.stm.STM.STMEvent;
import com.easysoft.stm.STMContext;
import com.easysoft.stm.STMStateful;
import com.easysoft.stm.config.STMOptimizedBuilder;

/** Outcomes: decisions with a named result, on their own and routing a flow through cases. */
class OutcomeTest {

	private static DecisionRegistry previous;
	private static STMStateful<STMContext> stm;
	private DecisionRegistry registry;

	@BeforeAll
	static void buildFlow() throws Exception {
		previous = DecisionRegistry.getDefault();
		STMOptimizedBuilder<STMContext> builder = new STMOptimizedBuilder<>(null, false);
		builder.addFile("classpath:stm/loan-outcome-flow.yaml");
		stm = builder.build();
	}

	@AfterAll
	static void restore() {
		DecisionRegistry.setDefault(previous);
	}

	@BeforeEach
	void setUp() {
		registry = new DecisionRegistry();
		registry.load("classpath:decision/loan-rules.yaml");
		DecisionRegistry.setDefault(registry);
	}

	private static LoanContext loan(long amount, Customer customer) {
		return new LoanContext(amount).customer(customer);
	}

	private static Customer domestic() {
		return new Customer().address(new Address("US"));
	}

	private static List<String> run(LoanContext loan) {
		loan.setSTMContext(stm.createContext());
		assertEquals(STMEvent.STOP, loan.getSTMContext().start(loan));
		return loan.getHistory();
	}

	// ---- evaluation --------------------------------------------------------------------------

	@Test
	void firstMatchingRuleWinsOtherwiseDefault() {
		assertEquals("APPROVE", registry.decide("loan.rules/loanOutcome", loan(5000, domestic().vip(true))));
		assertEquals("REVIEW", registry.decide("loan.rules/loanOutcome", loan(50000, domestic().vip(true))));
		assertEquals("REJECT", registry.decide("loan.rules/loanOutcome", loan(5000, new Customer().address(new Address("FR")))), "default");
		// blocked comes first, so it wins even though the customer is also domestic
		assertEquals("REJECT", registry.decide("loan.rules/loanOutcome", loan(5000, domestic().vip(true).status(Status.BLOCKED))));
	}

	@Test
	void noMatchAndNoDefaultIsNull() {
		DecisionRegistry r = rules("conditions:\n  - { name: big, path: amount, greaterThan: 100 }\n" //
				+ "outcomes:\n  - name: size\n    rules:\n      - { when: big, result: LARGE }\n");
		assertEquals("LARGE", r.decide("t/size", new LoanContext(500)));
		assertNull(r.decide("t/size", new LoanContext(5)));
		assertNull(r.getOutcome("t/size").getDefaultResult());
	}

	@Test
	void resultsMayBeNumbersOrBooleansAndAreReturnedAsText() {
		DecisionRegistry r = rules("conditions:\n  - { name: big, path: amount, greaterThan: 100 }\n" //
				+ "outcomes:\n  - name: tier\n    rules:\n      - { when: big, result: 2 }\n    default: 1\n" //
				+ "  - name: flag\n    rules:\n      - { when: not big, result: true }\n    default: false\n");
		assertEquals("2", r.decide("t/tier", new LoanContext(500)));
		assertEquals("1", r.decide("t/tier", new LoanContext(5)));
		assertEquals("true", r.decide("t/flag", new LoanContext(5)));
	}

	@Test
	void outcomesUseFullExpressionsAndOtherRuleSets() {
		registry.loadYaml("name: promo\noutcomes:\n  - name: offer\n    rules:\n" //
				+ "      - { when: 'loan.rules/vip and not loan.rules/smallAmount', result: PLATINUM }\n" //
				+ "      - { when: loan.rules/trusted || loan.rules/lowRisk, result: GOLD }\n    default: NONE\n", "promo");
		assertEquals("PLATINUM", registry.decide("promo/offer", loan(50000, domestic().vip(true))));
		assertEquals("GOLD", registry.decide("promo/offer", loan(10, domestic().vip(true))));
		assertEquals("NONE", registry.decide("promo/offer", loan(10, domestic())));
	}

	@Test
	void lookupAndIntrospection() {
		Outcome outcome = registry.getOutcome("loan.rules/loanOutcome");
		assertSame(outcome, registry.getOutcomes("loan.rules").get("loanOutcome"));
		assertEquals(List.of("REJECT", "APPROVE", "REVIEW"), outcome.getResults());
		assertEquals("autoApprove", outcome.getBranches().get(1).getWhen());
		assertEquals("APPROVE", outcome.handle(loan(5000, domestic().vip(true))), "an outcome is an Actor");
		assertEquals("APPROVE", new DecisionActor(registry).decide(loan(5000, domestic().vip(true)), "loan.rules/loanOutcome"));
		assertNull(registry.findOutcome("loan.rules/autoApprove"));
		assertNull(registry.findOutcome("loan.rules/loanOutcome/x"));
		assertNull(registry.find("loan.rules/loanOutcome"), "outcomes are not true/false rules");
	}

	@Test
	void usingTheWrongKindIsExplained() {
		DecisionException e = assertThrows(DecisionException.class, () -> registry.evaluate("loan.rules/loanOutcome", loan(1, domestic())));
		assertTrue(e.getMessage().contains("is an outcome") && e.getMessage().contains("decide()"), e.getMessage());
		e = assertThrows(DecisionException.class, () -> registry.decide("loan.rules/autoApprove", loan(1, domestic())));
		assertTrue(e.getMessage().contains("evaluate()"), e.getMessage());
		e = assertThrows(DecisionException.class, () -> registry.decide("loan.rules/missing", loan(1, domestic())));
		assertTrue(e.getMessage().contains("no outcome 'loan.rules/missing'"), e.getMessage());
	}

	// ---- load-time errors ----------------------------------------------------------------------

	@Test
	void rejectsBadOutcomes() {
		String a = "conditions:\n  - { name: a, path: amount, equal: 1 }\n";
		assertLoadFails(a + "outcomes:\n  - { name: o }\n", "rules is empty");
		assertLoadFails(a + "outcomes:\n  - { name: o, rules: [] }\n", "rules is empty");
		assertLoadFails(a + "outcomes:\n  - { name: o, rules: [ { when: a } ] }\n", "outcome 'o' rule 1: result is missing");
		assertLoadFails(a + "outcomes:\n  - { name: o, rules: [ { when: a, result: X }, { result: Y } ] }\n", "outcome 'o' rule 2: 'when' is empty");
		assertLoadFails(a + "outcomes:\n  - { name: o, rules: [ { when: a, result: '  ' } ] }\n", "result is empty");
		assertLoadFails(a + "outcomes:\n  - { name: o, rules: [ { when: a, result: [X, Y] } ] }\n", "single value");
		assertLoadFails(a + "outcomes:\n  - { name: o, rules: [ { when: a and, result: X } ] }\n", "outcome 'o' rule 1: expression ends too early");
		assertLoadFails(a + "outcomes:\n  - { name: o, rules: [ { when: b, result: X } ] }\n", "outcome 'o': unknown condition or decision 'b'");
		assertLoadFails(a + "outcomes:\n  - { name: o, rules: [ { when: a, result: X } ] }\n  - { name: p, rules: [ { when: o, result: Y } ] }\n",
				"'o' is an outcome");
		assertLoadFails(a + "decisions:\n  - { name: d, when: o }\noutcomes:\n  - { name: o, rules: [ { when: a, result: X } ] }\n", "decision 'd': 'o' is an outcome");
		assertLoadFails(a + "outcomes:\n  - { name: a, rules: [ { when: a, result: X } ] }\n", "'a' is defined more than once");
		assertLoadFails(a + "outcomes:\n  - { name: o, rules: [ { when: a, result: X } ] }\n  - { name: o, rules: [ { when: a, result: X } ] }\n", "'o' is defined more than once");
		assertLoadFails(a + "outcomes:\n  - { name: o, rules: [ { when: a, result: X, extra: 1 } ] }\n", "extra");
	}

	@Test
	void outcomesOfAnotherRuleSetCannotBeUsedAsConditions() {
		DecisionException e = assertThrows(DecisionException.class,
				() -> registry.loadYaml("name: p\ndecisions:\n  - { name: d, when: loan.rules/loanOutcome }\n", "p"));
		assertTrue(e.getMessage().contains("is an outcome"), e.getMessage());
	}

	// ---- flow routing ----------------------------------------------------------------------------

	@Test
	void flowRoutesOnTheOutcomeThroughCases() {
		assertEquals(List.of("approved"), run(loan(5000, domestic().vip(true))));
		assertEquals(List.of("review"), run(loan(50000, domestic())));
	}

	@Test
	void resultWithoutACaseTakesTheTransitionsFire() {
		assertEquals(List.of("rejected"), run(loan(5000, domestic().status(Status.BLOCKED))));
		assertEquals(List.of("rejected"), run(loan(5000, new Customer().address(new Address("FR")))));
	}

	@Test
	void nullResultTakesTheTransitionsFire() {
		DecisionRegistry r = new DecisionRegistry();
		r.loadYaml("name: loan.rules\nconditions:\n  - { name: never, path: amount, lessThan: 0 }\n" //
				+ "outcomes:\n  - name: loanOutcome\n    rules:\n      - { when: never, result: APPROVE }\n", "no default");
		DecisionRegistry.setDefault(r);
		assertEquals(List.of("rejected"), run(loan(5000, domestic())));
	}

	@Test
	void evaluationErrorGoesToOnError() {
		DecisionRegistry r = new DecisionRegistry();
		r.loadYaml("name: loan.rules\nconditions:\n  - { name: x, path: customer.nation, equal: US }\n" //
				+ "outcomes:\n  - name: loanOutcome\n    rules:\n      - { when: x, result: APPROVE }\n", "broken");
		DecisionRegistry.setDefault(r);
		List<String> history = run(loan(5000, domestic()));
		assertEquals(1, history.size(), history.toString());
		assertTrue(history.get(0).contains("no property 'nation'"), history.toString());
	}

	private static DecisionRegistry rules(String body) {
		DecisionRegistry r = new DecisionRegistry();
		r.loadYaml("name: t\n" + body, "test");
		return r;
	}

	private static void assertLoadFails(String body, String expected) {
		DecisionException e = assertThrows(DecisionException.class, () -> rules(body), body);
		assertTrue(e.getMessage().contains(expected), "expected '" + expected + "' in: " + e.getMessage());
	}
}

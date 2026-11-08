package com.easysoft.decision;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.easysoft.decision.LoanContext.Address;
import com.easysoft.decision.LoanContext.Customer;
import com.easysoft.decision.LoanContext.Status;
import com.easysoft.decision.LoanContext.Tier;

/** {@code explain} must give the same answer as {@code evaluate}/{@code decide}, and say why. */
class ExplanationTest {

	private DecisionRegistry registry;

	@BeforeEach
	void setUp() {
		registry = new DecisionRegistry();
		registry.load("classpath:decision/loan-rules.yaml");
	}

	private static LoanContext loan(long amount, Customer customer) {
		return new LoanContext(amount).customer(customer);
	}

	/** Every combination of the inputs the example rules read. */
	private static List<LoanContext> contexts() {
		List<LoanContext> contexts = new ArrayList<>();
		for (long amount : new long[] { 5000, 50000, 60000 }) {
			for (boolean vip : new boolean[] { true, false }) {
				for (Tier tier : Tier.values()) {
					for (Status status : Status.values()) {
						for (String country : new String[] { "US", "FR", null }) {
							for (String risk : new String[] { null, "low", "high" }) {
								Customer customer = new Customer().vip(vip).tier(tier).status(status).address(country == null ? null : new Address(country));
								LoanContext context = loan(amount, customer);
								if (risk != null) {
									context.getAttributes().put("risk", risk);
								}
								contexts.add(context);
							}
						}
					}
				}
			}
		}
		return contexts;
	}

	@Test
	void explainAgreesWithEvaluateAndDecideEverywhere() {
		List<LoanContext> contexts = contexts();
		assertEquals(216, contexts.size());
		for (LoanContext context : contexts) {
			for (String name : registry.getRuleSet("loan.rules").keySet()) {
				String key = "loan.rules/" + name;
				Explanation why = registry.explain(key, context);
				assertEquals(registry.evaluate(key, context), why.getValue(), key + "\n" + why);
				assertFalse(why.isOutcome());
			}
			Explanation why = registry.explain("loan.rules/loanOutcome", context);
			assertTrue(why.isOutcome());
			assertEquals(registry.decide("loan.rules/loanOutcome", context), why.getResult(), why.toString());
		}
	}

	@Test
	void showsValuesReadAndSkipsShortCircuitedParts() {
		Explanation why = registry.explain("loan.rules/autoApprove", loan(50000, new Customer().vip(true).address(new Address("US"))));
		assertEquals("loan.rules/autoApprove = false\n" //
				+ "  and = false\n" //
				+ "    not = true\n" //
				+ "      blocked = false  [customer.status is ACTIVE; equal 'BLOCKED']\n" //
				+ "    smallAmount = false  [amount is 50000; lessOrEqual 10000]\n" //
				+ "    domestic  (not evaluated)\n" //
				+ "    (trusted or lowRisk)  (not evaluated)", why.toString());
		Explanation and = why.getChildren().get(0);
		assertNull(and.getChildren().get(2).getValue());
		assertFalse(and.getChildren().get(2).isEvaluated());
		assertEquals("amount is 50000; lessOrEqual 10000", and.getChildren().get(1).getDetail());
	}

	@Test
	void nestedDecisionsAreExpanded() {
		Explanation why = registry.explain("loan.rules/trusted", loan(1, new Customer().tier(Tier.GOLD)));
		assertEquals("loan.rules/trusted = true\n" //
				+ "  or = true\n" //
				+ "    vip = false  [customer.vip is false; equal true]\n" //
				+ "    goldTier = true  [customer.tier is GOLD; equal 'GOLD']", why.toString());
		Explanation review = registry.explain("loan.rules/needsReview", loan(50000, new Customer().address(new Address("US"))));
		assertTrue(review.toString().contains("    not = true\n      autoApprove = false\n        and = false"), review.toString());
	}

	@Test
	void inlineComparisonsShowTheirTextAndValue() {
		Explanation why = registry.explain("loan.rules/largeForeign", loan(60000, new Customer().address(new Address("FR"))));
		assertEquals("loan.rules/largeForeign = true\n" //
				+ "  and = true\n" //
				+ "    amount > 50000 = true  [amount is 60000]\n" //
				+ "    customer.address.country not in [US, CA] = true  [customer.address.country is 'FR']", why.toString());
	}

	@Test
	void outcomesShowTheMatchingRuleAndSkipTheRest() {
		Explanation why = registry.explain("loan.rules/loanOutcome", loan(5000, new Customer().vip(true).address(new Address("US"))));
		assertEquals("APPROVE", why.getResult());
		String text = why.toString();
		assertTrue(text.startsWith("loan.rules/loanOutcome = APPROVE\n  rule 1: blocked -> REJECT = false\n"), text);
		assertTrue(text.contains("\n  rule 2: autoApprove -> APPROVE = true\n"), text);
		assertTrue(text.endsWith("\n  rule 3: domestic -> REVIEW  (not evaluated)"), text);
		assertFalse(text.contains("default"), text);
	}

	@Test
	void outcomesShowWhenTheDefaultWasUsed() {
		Explanation why = registry.explain("loan.rules/loanOutcome", loan(5000, new Customer().address(new Address("FR"))));
		assertEquals("REJECT", why.getResult());
		assertTrue(why.toString().endsWith("\n  default -> REJECT = true"), why.toString());
		assertEquals(4, why.getChildren().size());

		DecisionRegistry r = new DecisionRegistry();
		r.loadYaml("name: t\noutcomes:\n  - name: o\n    rules:\n      - { when: amount > 5, result: BIG }\n", "t");
		Explanation none = r.explain("t/o", new LoanContext(1));
		assertNull(none.getResult());
		assertTrue(none.toString().startsWith("t/o = null\n"), none.toString());
	}

	@Test
	void namedConditionsCanBeExplainedOnTheirOwn() {
		Explanation why = registry.explain("loan.rules/domestic", loan(1, new Customer()));
		assertEquals("loan.rules/domestic = false  [customer.address.country is null; in ['US', 'CA']]", why.toString());
	}

	@Test
	void unknownKeysFail() {
		assertThrows(DecisionException.class, () -> registry.explain("loan.rules/nope", loan(1, new Customer())));
	}
}

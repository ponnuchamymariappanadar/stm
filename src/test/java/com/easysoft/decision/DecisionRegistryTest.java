package com.easysoft.decision;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.easysoft.decision.LoanContext.Address;
import com.easysoft.decision.LoanContext.Customer;
import com.easysoft.decision.LoanContext.Status;
import com.easysoft.decision.LoanContext.Tier;

class DecisionRegistryTest {

	private DecisionRegistry registry;

	@BeforeEach
	void setUp() {
		registry = new DecisionRegistry();
		assertEquals(List.of("loan.rules"), registry.load("classpath:decision/loan-rules.yaml"));
	}

	private static LoanContext loan(long amount, Customer customer) {
		return new LoanContext(amount).customer(customer);
	}

	private static Customer domestic() {
		return new Customer().address(new Address("US"));
	}

	/** Loads one rule set named t from the conditions and decisions given. */
	private DecisionRegistry rules(String body) {
		DecisionRegistry r = new DecisionRegistry();
		r.loadYaml("name: t\n" + body, "test");
		return r;
	}

	// ---- the example rule file -------------------------------------------------------------

	@Test
	void autoApprovesSmallDomesticLoansForTrustedCustomers() {
		assertTrue(registry.evaluate("loan.rules/autoApprove", loan(5000, domestic().vip(true))));
		assertTrue(registry.evaluate("loan.rules/autoApprove", loan(5000, domestic().tier(Tier.GOLD))));
		assertFalse(registry.evaluate("loan.rules/autoApprove", loan(5000, domestic())), "neither trusted nor low risk");
		assertFalse(registry.evaluate("loan.rules/autoApprove", loan(20000, domestic().vip(true))), "too large");
		assertFalse(registry.evaluate("loan.rules/autoApprove", loan(5000, new Customer().vip(true).address(new Address("FR")))), "not domestic");
		assertFalse(registry.evaluate("loan.rules/autoApprove", loan(5000, domestic().vip(true).status(Status.BLOCKED))), "blocked");
	}

	@Test
	void mapKeysCountTowardsTheDecision() {
		LoanContext context = loan(5000, domestic());
		context.getAttributes().put("risk", "low");
		assertTrue(registry.evaluate("loan.rules/autoApprove", context));
		context.getAttributes().put("risk", "high");
		assertFalse(registry.evaluate("loan.rules/autoApprove", context));
	}

	@Test
	void decisionsCanReferToOtherDecisions() {
		assertTrue(registry.evaluate("loan.rules/needsReview", loan(50000, domestic())));
		assertFalse(registry.evaluate("loan.rules/needsReview", loan(5000, domestic().vip(true))), "auto approved");
		assertFalse(registry.evaluate("loan.rules/needsReview", loan(50000, domestic().status(Status.BLOCKED))), "blocked");
	}

	@Test
	void conditionsCanBeEvaluatedOnTheirOwn() {
		assertTrue(registry.evaluate("loan.rules/domestic", loan(1, domestic())));
		assertFalse(registry.evaluate("loan.rules/hasEmail", loan(1, domestic())));
		assertTrue(registry.evaluate("loan.rules/hasEmail", loan(1, domestic().email("a@b.c"))));
	}

	@Test
	void nullAlongThePathIsNullNotAnError() {
		LoanContext noAddress = loan(5000, new Customer().vip(true));
		assertFalse(registry.evaluate("loan.rules/domestic", noAddress));
		assertFalse(registry.evaluate("loan.rules/autoApprove", noAddress));
		LoanContext noCustomer = new LoanContext(5000).customer(null);
		assertFalse(registry.evaluate("loan.rules/vip", noCustomer));
	}

	@Test
	void listIndexOutOfRangeIsNull() {
		LoanContext context = loan(1, domestic());
		assertTrue(registry.evaluate("loan.rules/firstTagNotTest", context), "no tags: null is not 'test'");
		context.getTags().add("test");
		assertFalse(registry.evaluate("loan.rules/firstTagNotTest", context));
	}

	@Test
	void lookupIsByExactKeyInTheTree() {
		assertTrue(registry.find("loan.rules/autoApprove") instanceof Decision);
		assertTrue(registry.find("loan.rules/vip") instanceof Condition);
		assertNull(registry.find("loan.rules/missing"));
		assertNull(registry.find("loan.rules"));
		assertNull(registry.find("loan.rules/autoApprove/extra"));
		assertNull(registry.find("other/autoApprove"));
		assertNull(registry.find(null));
		DecisionException e = assertThrows(DecisionException.class, () -> registry.evaluate("loan.rules/missing", loan(1, domestic())));
		assertTrue(e.getMessage().contains("loan.rules/missing"), e.getMessage());
		assertTrue(registry.getDecision("loan.rules/trusted").handle(loan(1, domestic().vip(true))));
		assertThrows(DecisionException.class, () -> registry.getDecision("loan.rules/vip"));
		assertEquals(List.of("smallAmount", "vip", "goldTier", "domestic", "blocked", "hasEmail", "firstTagNotTest", "lowRisk", "trusted", "autoApprove", "needsReview"),
				List.copyOf(registry.getRuleSet("loan.rules").keySet()));
	}

	// ---- operators ---------------------------------------------------------------------------

	@Test
	void equalComparesNumbersByValue() {
		DecisionRegistry r = rules("conditions:\n  - { name: a, path: amount, equal: 100 }\n  - { name: b, path: amount, equal: '100.00' }\n  - { name: c, path: score, equal: 7 }\n");
		assertTrue(r.evaluate("t/a", new LoanContext(100)));
		assertTrue(r.evaluate("t/b", new LoanContext(100)));
		assertFalse(r.evaluate("t/a", new LoanContext(101)));
		LoanContext context = new LoanContext(0);
		context.score = 7;
		assertTrue(r.evaluate("t/c", context), "public field");
	}

	@Test
	void notEqualAndCaseInsensitiveOperatorKeys() {
		DecisionRegistry r = rules("conditions:\n  - { name: a, path: customer.tier, notequal: GOLD }\n");
		assertTrue(r.evaluate("t/a", loan(1, new Customer())));
		assertFalse(r.evaluate("t/a", loan(1, new Customer().tier(Tier.GOLD))));
	}

	@Test
	void orderingOperators() {
		DecisionRegistry r = rules("conditions:\n" //
				+ "  - { name: gt, path: amount, greaterThan: 10 }\n" //
				+ "  - { name: ge, path: amount, greaterOrEqual: 10 }\n" //
				+ "  - { name: lt, path: amount, lessThan: 10.5 }\n" //
				+ "  - { name: le, path: amount, lessOrEqual: 10 }\n");
		LoanContext ten = new LoanContext(10);
		assertFalse(r.evaluate("t/gt", ten));
		assertTrue(r.evaluate("t/ge", ten));
		assertTrue(r.evaluate("t/lt", ten));
		assertTrue(r.evaluate("t/le", ten));
		assertTrue(r.evaluate("t/gt", new LoanContext(11)));
	}

	@Test
	void orderingConvertsTextForComparableTypesSuchAsDates() {
		DecisionRegistry r = rules("conditions:\n  - { name: old, path: customer.since, lessThan: 2020-01-01 }\n");
		assertTrue(r.evaluate("t/old", loan(1, new Customer().since(LocalDate.of(2019, 5, 1)))));
		assertFalse(r.evaluate("t/old", loan(1, new Customer().since(LocalDate.of(2021, 5, 1)))));
		assertFalse(r.evaluate("t/old", loan(1, new Customer())), "null is never less than");
	}

	@Test
	void inNotInIsNullAndMatches() {
		DecisionRegistry r = rules("conditions:\n" //
				+ "  - { name: in, path: amount, in: [1, 2, 3] }\n" //
				+ "  - { name: notIn, path: customer.tier, notIn: [GOLD] }\n" //
				+ "  - { name: noMail, path: customer.email, isNull: true }\n" //
				+ "  - { name: corp, path: customer.email, matches: '.*@corp\\.com' }\n");
		assertTrue(r.evaluate("t/in", new LoanContext(2)));
		assertFalse(r.evaluate("t/in", new LoanContext(4)));
		assertTrue(r.evaluate("t/notIn", loan(1, new Customer())));
		assertTrue(r.evaluate("t/noMail", loan(1, new Customer())));
		assertFalse(r.evaluate("t/corp", loan(1, new Customer())));
		assertTrue(r.evaluate("t/corp", loan(1, new Customer().email("me@corp.com"))));
		assertFalse(r.evaluate("t/corp", loan(1, new Customer().email("me@corpXcom"))));
	}

	@Test
	void equalNullAndBooleanText() {
		DecisionRegistry r = rules("conditions:\n  - { name: n, path: customer.email, equal: null }\n  - { name: v, path: customer.vip, equal: 'TRUE' }\n");
		assertTrue(r.evaluate("t/n", loan(1, new Customer())));
		assertTrue(r.evaluate("t/v", loan(1, new Customer().vip(true))));
	}

	// ---- expressions ---------------------------------------------------------------------------

	@Test
	void precedenceIsNotThenAndThenOr() {
		DecisionRegistry r = rules("conditions:\n" //
				+ "  - { name: a, path: attributes.a, equal: true }\n" //
				+ "  - { name: b, path: attributes.b, equal: true }\n" //
				+ "  - { name: c, path: attributes.c, equal: true }\n" //
				+ "decisions:\n" //
				+ "  - { name: plain, when: a or b and c }\n" //
				+ "  - { name: grouped, when: (a or b) and c }\n" //
				+ "  - { name: symbols, when: '!a && (b || c)' }\n" //
				+ "  - { name: upper, when: NOT a AND b }\n");
		LoanContext onlyA = withFlags(true, false, false);
		assertTrue(r.evaluate("t/plain", onlyA));
		assertFalse(r.evaluate("t/grouped", onlyA));
		assertFalse(r.evaluate("t/symbols", onlyA));
		assertTrue(r.evaluate("t/symbols", withFlags(false, false, true)));
		assertTrue(r.evaluate("t/upper", withFlags(false, true, false)));
	}

	@Test
	void evaluationShortCircuits() {
		// 'broken' reads a property that does not exist; it must never be reached
		DecisionRegistry r = rules("conditions:\n" //
				+ "  - { name: yes, path: amount, greaterThan: 0 }\n" //
				+ "  - { name: broken, path: noSuchProperty, equal: 1 }\n" //
				+ "decisions:\n" //
				+ "  - { name: either, when: yes or broken }\n" //
				+ "  - { name: both, when: not yes and broken }\n");
		assertTrue(r.evaluate("t/either", new LoanContext(1)));
		assertFalse(r.evaluate("t/both", new LoanContext(1)));
		DecisionException e = assertThrows(DecisionException.class, () -> r.evaluate("t/either", new LoanContext(0)));
		assertTrue(e.getMessage().contains("no property 'noSuchProperty'"), e.getMessage());
	}

	@Test
	void decisionsMayReferToAnotherRuleSetLoadedEarlier() {
		registry.loadYaml("name: promo\ndecisions:\n  - { name: offer, when: loan.rules/trusted and not loan.rules/blocked }\n", "promo");
		assertTrue(registry.evaluate("promo/offer", loan(1, domestic().vip(true))));
		assertSame(registry.find("loan.rules/trusted"), ((Expression.Reference) ((Expression.And) registry.getDecision("promo/offer").getExpression()).operands.get(0)).target);
	}

	@Test
	void multipleRuleSetsInOneFile() {
		DecisionRegistry r = new DecisionRegistry();
		List<String> names = r.loadYaml("name: one\nconditions:\n  - { name: a, path: amount, equal: 1 }\n---\nname: two\ndecisions:\n  - { name: b, when: one/a }\n", "multi");
		assertEquals(List.of("one", "two"), names);
		assertTrue(r.evaluate("two/b", new LoanContext(1)));
	}

	@Test
	void decisionActorUsesTheGivenRegistry() {
		DecisionActor actor = new DecisionActor(registry, "loan.rules/trusted");
		assertTrue(actor.handle(loan(1, domestic().vip(true))));
		assertFalse(new DecisionActor(registry).evaluate(loan(1, domestic()), "loan.rules/trusted"));
		assertThrows(DecisionException.class, () -> new DecisionActor(registry).handle(loan(1, domestic())));
	}

	// ---- load-time errors ------------------------------------------------------------------------

	@Test
	void rejectsBadRuleFiles() {
		assertLoadFails("conditions:\n  - { name: a, path: amount, equals: 1 }\n", "unknown operator 'equals'");
		assertLoadFails("conditions:\n  - { name: a, path: amount }\n", "exactly one operator");
		assertLoadFails("conditions:\n  - { name: a, path: amount, equal: 1, notEqual: 2 }\n", "exactly one operator");
		assertLoadFails("conditions:\n  - { name: a, equal: 1 }\n", "path is missing");
		assertLoadFails("conditions:\n  - { name: a, path: 'a..b', equal: 1 }\n", "empty property name");
		assertLoadFails("conditions:\n  - { name: a, path: amount, in: [1], extra: 2 }\n", "exactly one operator");
		assertLoadFails("conditions:\n  - { name: a, path: amount, equal: [1, 2] }\n", "use in/notIn");
		assertLoadFails("conditions:\n  - { name: a, path: amount, greaterThan: null }\n", "needs a single value");
		assertLoadFails("conditions:\n  - { name: a, path: amount, isNull: maybe }\n", "true or false");
		assertLoadFails("conditions:\n  - { name: a, path: amount, matches: '(' }\n", "invalid regular expression");
		assertLoadFails("conditions:\n  - { name: and, path: amount, equal: 1 }\n", "keyword");
		assertLoadFails("conditions:\n  - { name: 'a b', path: amount, equal: 1 }\n", "may only use");
		assertLoadFails("conditions:\n  - { name: a, path: amount, equal: 1 }\n  - { name: a, path: amount, equal: 2 }\n", "more than once");
		assertLoadFails("decisions:\n  - { name: d, when: missing }\n", "unknown condition or decision 'missing'");
		assertLoadFails("decisions:\n  - { name: d, when: other/x }\n", "unknown condition or decision 'other/x'");
		assertLoadFails("decisions:\n  - { name: d }\n", "'when' is empty");
		assertLoadFails("conditions:\n  - { name: a, path: amount, equal: 1 }\ndecisions:\n  - { name: d, when: a and }\n", "ends too early");
		assertLoadFails("conditions:\n  - { name: a, path: amount, equal: 1 }\ndecisions:\n  - { name: d, when: (a }\n", "missing ')'");
		assertLoadFails("conditions:\n  - { name: a, path: amount, equal: 1 }\ndecisions:\n  - { name: d, when: a a }\n", "unexpected 'a'");
		assertLoadFails("conditions:\n  - { name: a, path: amount, equal: 1 }\ndecisions:\n  - { name: d, when: 'a = 1' }\n", "unexpected character '='");
		assertLoadFails("decisions:\n  - { name: x, when: y }\n  - { name: y, when: z }\n  - { name: z, when: x }\n", "cycle: x -> y -> z -> x");
		assertLoadFails("decisions:\n  - { name: x, when: x }\n", "cycle: x -> x");
		assertLoadFails("decisionz: []\n", "decisionz");
	}

	@Test
	void ruleSetsCannotBeLoadedTwiceAndAFailedLoadRegistersNothing() {
		DecisionException e = assertThrows(DecisionException.class, () -> registry.load("classpath:decision/loan-rules.yaml"));
		assertTrue(e.getMessage().contains("already loaded"), e.getMessage());

		DecisionRegistry r = new DecisionRegistry();
		assertThrows(DecisionException.class, () -> r.loadYaml("name: t\nconditions:\n  - { name: a, path: amount, equal: 1 }\ndecisions:\n  - { name: d, when: nope }\n", "t"));
		assertEquals(List.of(), r.getRuleSetNames());
		assertNull(r.find("t/a"));
		assertEquals(Map.of(), r.getRuleSet("t"));
	}

	@Test
	void missingFileIsReported() {
		DecisionException e = assertThrows(DecisionException.class, () -> new DecisionRegistry().load("classpath:decision/nope.yaml"));
		assertTrue(e.getMessage().contains("decision/nope.yaml"), e.getMessage());
	}

	private void assertLoadFails(String body, String expected) {
		DecisionException e = assertThrows(DecisionException.class, () -> rules(body), body);
		assertTrue(e.getMessage().contains(expected), "expected '" + expected + "' in: " + e.getMessage());
	}

	private static LoanContext withFlags(boolean a, boolean b, boolean c) {
		LoanContext context = new LoanContext(0);
		context.getAttributes().put("a", a);
		context.getAttributes().put("b", b);
		context.getAttributes().put("c", c);
		return context;
	}
}

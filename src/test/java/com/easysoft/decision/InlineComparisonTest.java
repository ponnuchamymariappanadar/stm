package com.easysoft.decision;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.easysoft.decision.LoanContext.Address;
import com.easysoft.decision.LoanContext.Customer;
import com.easysoft.decision.LoanContext.Status;
import com.easysoft.decision.LoanContext.Tier;

/** Comparisons written directly in a {@code when}, without a named condition. */
class InlineComparisonTest {

	/** A rule set t with one decision d whose when is {@code when}. */
	private static DecisionRegistry decision(String when) {
		DecisionRegistry r = new DecisionRegistry();
		r.loadYaml("name: t\ndecisions:\n  - name: d\n    when: \"" + when.replace("\\", "\\\\").replace("\"", "\\\"") + "\"\n", "test");
		return r;
	}

	private static boolean eval(String when, LoanContext context) {
		return decision(when).evaluate("t/d", context);
	}

	private static LoanContext loan(long amount, Customer customer) {
		return new LoanContext(amount).customer(customer);
	}

	private static Customer us() {
		return new Customer().address(new Address("US"));
	}

	@Test
	void comparisonOperators() {
		LoanContext ten = new LoanContext(10);
		assertTrue(eval("amount == 10", ten));
		assertFalse(eval("amount != 10", ten));
		assertTrue(eval("amount > 9", ten));
		assertFalse(eval("amount > 10", ten));
		assertTrue(eval("amount >= 10", ten));
		assertTrue(eval("amount < 10.5", ten));
		assertTrue(eval("amount <= 10", ten));
		assertTrue(eval("amount>9&&amount<11", ten), "no spaces needed");
		ten.score = -3;
		assertTrue(eval("score > -5 and score < +1", ten), "signed numbers");
	}

	@Test
	void textEnumsAndBooleans() {
		LoanContext gold = loan(1, us().tier(Tier.GOLD).vip(true).email("me@corp.com"));
		assertTrue(eval("customer.tier == GOLD", gold), "unquoted word is text");
		assertTrue(eval("customer.tier == 'GOLD'", gold));
		assertTrue(eval("customer.vip == true", gold));
		assertFalse(eval("customer.vip == false", gold));
		assertTrue(eval("customer.email == \"me@corp.com\"", gold), "quote text with punctuation");
		assertTrue(eval("customer.email matches '.*@corp[.]com'", gold));
		assertFalse(eval("customer.email matches 'me'", gold));
		assertTrue(eval("customer.email == 'it\\'s'", loan(1, us().email("it's"))), "backslash escapes a quote");
	}

	@Test
	void inNotInAndNull() {
		LoanContext context = loan(1, us());
		assertTrue(eval("customer.address.country in [US, CA]", context));
		assertFalse(eval("customer.address.country not in [US, CA]", context));
		assertTrue(eval("customer.address.country in (FR, US)", context), "parentheses work for lists too");
		assertTrue(eval("amount in [1, 2, 3]", context));
		assertTrue(eval("customer.email is null", context));
		assertFalse(eval("customer.email is not null", context));
		assertTrue(eval("customer.email == null", context));
		assertTrue(eval("customer.email in [null, 'x']", context));
	}

	@Test
	void indexedPathsAndDates() {
		LoanContext context = loan(1, us().since(LocalDate.of(2019, 1, 1)));
		context.getTags().add("vip-list");
		context.getAttributes().put("risk", "low");
		assertTrue(eval("tags[0] == vip-list", context));
		assertTrue(eval("tags[1] is null", context), "out of range");
		assertTrue(eval("attributes[risk] in [low, medium]", context));
		assertTrue(eval("attributes.risk == low", context));
		assertTrue(eval("customer.since < 2020-01-01", context));
		assertFalse(eval("customer.since >= 2020-01-01", context));
	}

	@Test
	void mixesWithNamedRulesAndPrecedence() {
		DecisionRegistry r = new DecisionRegistry();
		r.loadYaml("name: t\nconditions:\n  - { name: vip, path: customer.vip, equal: true }\n" //
				+ "decisions:\n  - name: d\n    when: vip and amount > 100 or customer.tier == GOLD\n" //
				+ "  - name: n\n    when: not amount > 5\n", "test");
		assertTrue(r.evaluate("t/d", loan(500, us().vip(true))));
		assertFalse(r.evaluate("t/d", loan(50, us().vip(true))));
		assertTrue(r.evaluate("t/d", loan(50, us().tier(Tier.GOLD))), "and binds tighter than or");
		assertTrue(r.evaluate("t/n", new LoanContext(3)), "not applies to the whole comparison");
		assertFalse(r.evaluate("t/n", new LoanContext(30)));
	}

	@Test
	void outcomesCanUseInlineComparisons() {
		DecisionRegistry r = new DecisionRegistry();
		r.loadYaml("name: t\noutcomes:\n  - name: size\n    rules:\n" //
				+ "      - { when: customer.status == BLOCKED, result: NONE }\n" //
				+ "      - { when: amount > 50000, result: LARGE }\n" //
				+ "      - { when: amount > 1000, result: MEDIUM }\n    default: SMALL\n", "test");
		assertEquals("LARGE", r.decide("t/size", new LoanContext(60000)));
		assertEquals("MEDIUM", r.decide("t/size", new LoanContext(5000)));
		assertEquals("SMALL", r.decide("t/size", new LoanContext(5)));
		assertEquals("NONE", r.decide("t/size", loan(60000, us().status(Status.BLOCKED))));
	}

	@Test
	void comparisonsKeepTheirSourceText() {
		Decision d = decision("amount <= 10000 and customer.address.country not in [US,  CA]").getDecision("t/d");
		List<Condition> comparisons = d.getExpression().comparisons();
		assertEquals(List.of("amount <= 10000", "customer.address.country not in [US,  CA]"), comparisons.stream().map(Condition::getName).toList());
		assertTrue(comparisons.get(0).isInline());
		assertEquals(Operator.NOT_IN, comparisons.get(1).getOperator());
		assertEquals(List.of(), d.getExpression().references(), "comparisons are not references");
	}

	@Test
	void rejectsMalformedComparisons() {
		assertFails("amount >", "expected a value at the end");
		assertFails("amount > and x", "expected a value, found 'and'");
		assertFails("amount > [1]", "expected a value, found '['");
		assertFails("amount in US", "expected '[' after in");
		assertFails("amount in [1, 2", "expected ',' or ']' in the list");
		assertFails("amount not in 5", "expected '[' after not in");
		assertFails("customer.email is nul", "expected 'null' after 'is'");
		assertFails("customer.email is not", "expected 'null' after 'is not'");
		assertFails("amount = 5", "use '==' to compare");
		assertFails("customer.email == 'abc", "unclosed quote");
		assertFails("tags[0 == x", "'[' without ']'");
		assertFails("other/rule == true", "is a rule reference and cannot be compared");
		assertFails("customer.email matches '('", "'customer.email matches '('': invalid regular expression");
		assertFails("amount > null", "needs a single value");
		assertFails("amount..x == 1", "empty property name");
	}

	private static void assertFails(String when, String expected) {
		DecisionException e = assertThrows(DecisionException.class, () -> decision(when), when);
		assertTrue(e.getMessage().contains(expected), "expected '" + expected + "' in: " + e.getMessage());
	}
}

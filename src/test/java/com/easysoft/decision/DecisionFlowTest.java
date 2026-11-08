package com.easysoft.decision;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.easysoft.decision.LoanContext.Address;
import com.easysoft.decision.LoanContext.Customer;
import com.easysoft.decision.LoanContext.Status;
import com.easysoft.stm.STM.STMEvent;
import com.easysoft.stm.STMContext;
import com.easysoft.stm.STMStateful;
import com.easysoft.stm.config.STMOptimizedBuilder;

/**
 * Runs stm/loan-flow.yaml, whose transition conditions are decisions from
 * decision/loan-rules.yaml evaluated through {@link DecisionActor}.
 */
class DecisionFlowTest {

	private static DecisionRegistry previous;
	private static STMStateful<STMContext> stm;

	@BeforeAll
	static void load() throws Exception {
		previous = DecisionRegistry.getDefault();
		DecisionRegistry registry = new DecisionRegistry();
		registry.load("classpath:decision/loan-rules.yaml");
		DecisionRegistry.setDefault(registry);

		STMOptimizedBuilder<STMContext> builder = new STMOptimizedBuilder<>(null, false);
		builder.addFile("classpath:stm/loan-flow.yaml");
		stm = builder.build();
	}

	@AfterAll
	static void restore() {
		DecisionRegistry.setDefault(previous);
	}

	private static List<String> run(LoanContext loan) {
		loan.setSTMContext(stm.createContext());
		assertEquals(STMEvent.STOP, loan.getSTMContext().start(loan));
		return loan.getHistory();
	}

	private static Customer domestic() {
		return new Customer().address(new Address("CA"));
	}

	@Test
	void autoApproveDecisionTrueTakesTheApprovePath() {
		assertEquals(List.of("approved"), run(new LoanContext(8000).customer(domestic().vip(true))));
	}

	@Test
	void autoApproveFalseThenNeedsReviewTrueGoesToReview() {
		assertEquals(List.of("review"), run(new LoanContext(80000).customer(domestic().vip(true))));
	}

	@Test
	void bothDecisionsFalseRejects() {
		assertEquals(List.of("rejected"), run(new LoanContext(8000).customer(domestic().vip(true).status(Status.BLOCKED))));
	}

	@Test
	void anEvaluationErrorGoesToOnError() {
		DecisionRegistry registry = new DecisionRegistry();
		// same names, but 'domestic' reads a property the context does not have
		registry.loadYaml("name: loan.rules\nconditions:\n  - { name: domestic, path: customer.nation, equal: US }\n" //
				+ "decisions:\n  - { name: autoApprove, when: domestic }\n  - { name: needsReview, when: domestic }\n", "broken");
		DecisionRegistry loaded = DecisionRegistry.getDefault();
		DecisionRegistry.setDefault(registry);
		try {
			List<String> history = run(new LoanContext(1).customer(domestic()));
			assertEquals(1, history.size(), history.toString());
			assertTrue(history.get(0).startsWith("error: ") && history.get(0).contains("no property 'nation'"), history.toString());
		} finally {
			DecisionRegistry.setDefault(loaded);
		}
	}
}

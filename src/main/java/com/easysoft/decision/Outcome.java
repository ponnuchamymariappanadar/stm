package com.easysoft.decision;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.easysoft.stm.Actor;
import com.easysoft.stm.STMContext;

/**
 * A decision with a named result rather than {@code true}/{@code false}: an ordered list of
 * {@code when}/{@code result} rules where the first rule whose {@code when} is true gives the result,
 * and {@code default} is the result when none is.
 *
 * <pre>
 * outcomes:
 *   - name: loanOutcome
 *     rules:
 *       - { when: blocked, result: REJECT }
 *       - { when: autoApprove, result: APPROVE }
 *       - { when: needsReview, result: REVIEW }
 *     default: REJECT
 * </pre>
 *
 * Without a {@code default}, no match gives {@code null}. As an {@link Actor} the result becomes the
 * flow's last result, so a transition's {@code cases} can route on it; from flow configuration use
 * {@link DecisionActor#decide(STMContext, String)}.
 */
public final class Outcome implements Actor<STMContext> {

	private static final Logger logger = LoggerFactory.getLogger(Outcome.class);

	/** One {@code when}/{@code result} rule. */
	public static final class Branch {
		private final String when;
		private final Expression expression;
		private final String result;

		Branch(String when, Expression expression, String result) {
			this.when = when;
			this.expression = expression;
			this.result = result;
		}

		public String getWhen() {
			return when;
		}

		public String getResult() {
			return result;
		}

		Expression getExpression() {
			return expression;
		}
	}

	private final String ruleSet;
	private final String name;
	private final List<Branch> branches;
	private final String defaultResult;

	Outcome(String ruleSet, String name, List<Branch> branches, String defaultResult) {
		this.ruleSet = ruleSet;
		this.name = name;
		this.branches = List.copyOf(branches);
		this.defaultResult = defaultResult;
	}

	public String getName() {
		return name;
	}

	public String getKey() {
		return ruleSet + "/" + name;
	}

	public List<Branch> getBranches() {
		return branches;
	}

	/** The result when no rule matches; may be {@code null}. */
	public String getDefaultResult() {
		return defaultResult;
	}

	/** Every result this outcome can give, in rule order, default last, without duplicates. */
	public List<String> getResults() {
		List<String> results = new ArrayList<>();
		for (Branch branch : branches) {
			if (!results.contains(branch.result)) {
				results.add(branch.result);
			}
		}
		if (defaultResult != null && !results.contains(defaultResult)) {
			results.add(defaultResult);
		}
		return results;
	}

	/** Returns the result of the first rule whose {@code when} is true, else the default (or {@code null}). */
	public String decide(Object root) {
		for (Branch branch : branches) {
			if (branch.expression.evaluate(root)) {
				if (logger.isDebugEnabled()) {
					logger.debug("outcome {} = {} [{}]", getKey(), branch.result, branch.when);
				}
				return branch.result;
			}
		}
		if (logger.isDebugEnabled()) {
			logger.debug("outcome {} = {} [default]", getKey(), defaultResult);
		}
		return defaultResult;
	}

	@Override
	public String handle(STMContext context) {
		return decide(context);
	}

	@Override
	public String toString() {
		return getKey();
	}
}

package com.easysoft.decision;

import java.util.List;

/**
 * Why a condition, decision or outcome gave its result: a tree that mirrors the expression, with
 * the value of every part that was evaluated, the bean values that were read, and the parts skipped
 * by short-circuiting.
 *
 * <pre>{@code
 * Explanation why = registry.explain("loan.rules/loanOutcome", context);
 * logger.info("loan {} -> {}\n{}", id, why.getResult(), why);
 * }</pre>
 *
 * {@link #toString()} renders it as an indented tree:
 *
 * <pre>
 * loan.rules/loanOutcome = REVIEW
 *   rule 1: blocked -> REJECT = false
 *     blocked = false  [customer.status is ACTIVE; equal BLOCKED]
 *   rule 2: autoApprove -> APPROVE = false
 *     ...
 *   rule 3: domestic -> REVIEW = true
 *     domestic = true  [customer.address.country is US; in [US, CA]]
 * </pre>
 */
public final class Explanation {

	private final String label;
	private final Boolean value;
	private final String detail;
	private final String result;
	private final boolean outcome;
	private final List<Explanation> children;

	private Explanation(String label, Boolean value, String detail, String result, boolean outcome, List<Explanation> children) {
		this.label = label;
		this.value = value;
		this.detail = detail;
		this.result = result;
		this.outcome = outcome;
		this.children = List.copyOf(children);
	}

	static Explanation of(String label, boolean value, List<Explanation> children) {
		return new Explanation(label, value, null, null, false, children);
	}

	static Explanation comparison(String label, boolean value, String detail) {
		return new Explanation(label, value, detail, null, false, List.of());
	}

	static Explanation skipped(String label, List<Explanation> children) {
		return new Explanation(label, null, null, null, false, children);
	}

	static Explanation outcome(String label, String result, List<Explanation> children) {
		return new Explanation(label, null, null, result, true, children);
	}

	/** What this node is: a rule name, an inline comparison, {@code and}, {@code or}, {@code not ...}, or an outcome rule. */
	public String getLabel() {
		return label;
	}

	/** {@code true} or {@code false}, or {@code null} when this part was not evaluated (short-circuited). */
	public Boolean getValue() {
		return value;
	}

	public boolean isEvaluated() {
		return value != null;
	}

	/** For a comparison: the value read from the bean and the test applied; otherwise {@code null}. */
	public String getDetail() {
		return detail;
	}

	/** Whether this node explains an outcome, whose answer is {@link #getResult()} rather than {@link #getValue()}. */
	public boolean isOutcome() {
		return outcome;
	}

	/** For an outcome: its result (may be {@code null}); otherwise {@code null}. */
	public String getResult() {
		return result;
	}

	public List<Explanation> getChildren() {
		return children;
	}

	@Override
	public String toString() {
		StringBuilder builder = new StringBuilder();
		render(builder, 0);
		return builder.toString();
	}

	private void render(StringBuilder builder, int depth) {
		if (depth > 0) {
			builder.append('\n');
		}
		builder.append("  ".repeat(depth)).append(label);
		if (outcome) {
			builder.append(" = ").append(result);
		} else if (value == null) {
			builder.append("  (not evaluated)");
		} else {
			builder.append(" = ").append(value);
		}
		if (detail != null) {
			builder.append("  [").append(detail).append(']');
		}
		for (Explanation child : children) {
			child.render(builder, depth + 1);
		}
	}
}

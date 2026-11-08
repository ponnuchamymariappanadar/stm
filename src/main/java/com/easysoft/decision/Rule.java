package com.easysoft.decision;

/**
 * Something that evaluates to {@code true} or {@code false} against a bean: a single
 * {@link Condition} or a {@link Decision} that combines other rules.
 */
public interface Rule {

	/** The rule's name, unique within its rule set. */
	String getName();

	/** The full registry key, {@code ruleSet/name}. */
	String getKey();

	/** Evaluates the rule against {@code root}, normally the flow's {@code STMContext}. */
	boolean evaluate(Object root);
}

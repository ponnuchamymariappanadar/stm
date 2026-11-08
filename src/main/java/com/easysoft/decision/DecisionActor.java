package com.easysoft.decision;

import com.easysoft.stm.Actor;
import com.easysoft.stm.STMContext;

/**
 * The bridge between flow configuration and decision rules: a condition actor that evaluates a
 * named condition or decision against the flow's context and returns {@code Boolean}.
 * <p>
 * In flow YAML, name the decision with {@code argParameter}; the builder then calls
 * {@link #evaluate(STMContext, String)}:
 *
 * <pre>
 * - when: order.validate
 *   fire: order.pay
 *   condition: { className: com.easysoft.decision.DecisionActor, methodName: evaluate, argParameter: order.rules/canPay }
 *   onFailedCondition: { to: failed.s, fire: order.rejected }
 * </pre>
 *
 * For an {@link Outcome}, use it as the transition's actor with {@code methodName: decide} and route
 * on the result with {@code cases}:
 *
 * <pre>
 * - when: loan.check
 *   fire: loan.reject                  # when no case matches
 *   actor: { className: com.easysoft.decision.DecisionActor, methodName: decide, argParameter: loan.rules/loanOutcome }
 *   cases:
 *     - { when: APPROVE, fire: loan.approve }
 *     - { when: REVIEW, fire: loan.review }
 * </pre>
 *
 * The rule file must be loaded into {@link DecisionRegistry#getDefault()} before the flow runs. As
 * a Spring bean, construct it with the registry (and optionally a fixed decision, which
 * {@link #handle(STMContext)} then evaluates).
 */
public class DecisionActor implements Actor<STMContext> {

	private final DecisionRegistry registry;
	private final String decision;

	/** Uses {@link DecisionRegistry#getDefault()}; this is the constructor used for {@code className}. */
	public DecisionActor() {
		this(null, null);
	}

	public DecisionActor(DecisionRegistry registry) {
		this(registry, null);
	}

	/** An actor bound to one decision; {@link #handle(STMContext)} evaluates it. */
	public DecisionActor(DecisionRegistry registry, String decision) {
		this.registry = registry;
		this.decision = decision;
	}

	/** Evaluates {@code key} ({@code ruleSet/name}) against the context. */
	public Boolean evaluate(STMContext context, String key) {
		return registry().evaluate(key, context);
	}

	/**
	 * Returns the named result of the outcome {@code key} ({@code ruleSet/name}). Used as a
	 * transition actor, the result becomes the last result and the transition's {@code cases} route on it.
	 */
	public String decide(STMContext context, String key) {
		return registry().decide(key, context);
	}

	/** Evaluates the decision given to the constructor. */
	@Override
	public Boolean handle(STMContext context) {
		if (decision == null) {
			throw new DecisionException("DecisionActor has no decision; use methodName: evaluate with argParameter: ruleSet/name");
		}
		return evaluate(context, decision);
	}

	private DecisionRegistry registry() {
		return registry != null ? registry : DecisionRegistry.getDefault();
	}

	@Override
	public String toString() {
		return decision == null ? "DecisionActor" : "DecisionActor[" + decision + "]";
	}
}

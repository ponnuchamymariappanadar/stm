package com.easysoft.decision;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.easysoft.stm.Actor;
import com.easysoft.stm.STMContext;

/**
 * A named combination of conditions and other decisions, such as
 * {@code bigOrder and (express or not domestic)}.
 * <p>
 * A decision is also an {@link Actor} that returns {@code Boolean}, so it can be used directly as a
 * transition condition when building flows in code. From flow configuration, use
 * {@link DecisionActor}.
 */
public final class Decision implements Rule, Actor<STMContext> {

	private static final Logger logger = LoggerFactory.getLogger(Decision.class);

	private final String ruleSet;
	private final String name;
	private final String when;
	private final Expression expression;

	public Decision(String ruleSet, String name, String when) {
		this.ruleSet = ruleSet;
		this.name = name;
		this.when = when;
		this.expression = Expression.parse(when, "decision '" + ruleSet + "/" + name + "'");
	}

	@Override
	public String getName() {
		return name;
	}

	@Override
	public String getKey() {
		return ruleSet + "/" + name;
	}

	/** The expression as written in the rule file. */
	public String getWhen() {
		return when;
	}

	public Expression getExpression() {
		return expression;
	}

	@Override
	public boolean evaluate(Object root) {
		boolean result = expression.evaluate(root);
		if (logger.isDebugEnabled()) {
			logger.debug("decision {} [{}] = {}", getKey(), when, result);
		}
		return result;
	}

	@Override
	public Boolean handle(STMContext context) {
		return evaluate(context);
	}

	@Override
	public String toString() {
		return getKey();
	}
}

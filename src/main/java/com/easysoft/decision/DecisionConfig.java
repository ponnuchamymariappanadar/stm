package com.easysoft.decision;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * The YAML model of one rule set. A file may hold several rule sets separated by {@code ---}.
 *
 * <pre>
 * name: order.rules
 * conditions:
 *   - name: bigOrder
 *     path: amount
 *     greaterThan: 1000
 *   - name: express
 *     path: express
 *     equal: true
 * decisions:
 *   - name: fastTrack
 *     when: bigOrder and express
 * outcomes:
 *   - name: shipping
 *     rules:
 *       - { when: fastTrack, result: EXPRESS }
 *     default: STANDARD
 * </pre>
 */
public class DecisionConfig {

	public String name;
	public String description;
	public List<ConditionConfig> conditions = new ArrayList<>();
	public List<DecisionNode> decisions = new ArrayList<>();
	public List<OutcomeNode> outcomes = new ArrayList<>();

	/**
	 * A condition: {@code name}, {@code path} and exactly one operator key ({@code equal},
	 * {@code notEqual}, {@code in}, ...). Operator keys are collected as they are written so that an
	 * unknown key is reported by name.
	 */
	public static class ConditionConfig {
		public String name;
		public String path;
		public String description;

		final Map<String, Object> operators = new LinkedHashMap<>();

		@JsonAnySetter
		public void setOperator(String key, Object value) {
			operators.put(key, value);
		}
	}

	/** A decision: {@code name} and a {@code when} expression over condition and decision names. */
	public static class DecisionNode {
		public String name;
		public String when;
		public String description;
	}

	/**
	 * A decision with a named result: ordered {@code rules} of {@code when}/{@code result}; the
	 * first true {@code when} wins, otherwise {@code default}.
	 */
	public static class OutcomeNode {
		public String name;
		public String description;
		public List<OutcomeRuleNode> rules = new ArrayList<>();
		@JsonProperty("default")
		public Object defaultResult;
	}

	/** One rule of an outcome. */
	public static class OutcomeRuleNode {
		public String when;
		public Object result;
	}
}

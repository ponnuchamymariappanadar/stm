package com.easysoft.stm.config;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

import lombok.AccessLevel;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Setter;

/**
 * Parses flow YAML into an {@link STMRoot}. {@link ConfigYAMLReader} is the implementation; the
 * nested classes are the configuration model. The YAML keys are the Java field names; the one
 * exception is {@code class}, a Java keyword, held in {@code className}. {@code defaults},
 * {@code otherwise} and {@code input} are read by setters, and a {@code steps} entry with
 * {@code runStage} is a call.
 */
public interface ConfigReader {
	<T> T parse(String yaml);

	@Data
	public static class STMRoot {
		String name;
		String startStage;
		String startStep;
		/** Set through {@code defaults: { onFalse }}. */
		@JsonIgnore
		ConditionFailedNode onFalse;
		/** Set through {@code defaults: { onError }}. */
		@JsonIgnore
		ErrorNode onError;
		List<StateNode> stages;
		/** More flow files, as a YAML list or a comma-separated text. */
		@JsonDeserialize(using = IncludeDeserializer.class)
		String include;

		@JsonIgnore
		String fileName;
		String postProcessor;

		public List<StateNode> getStages() {
			if (stages == null) {
				stages = new ArrayList<StateNode>();
			}
			return stages;
		}

		/** {@code defaults}: the {@code onError} and {@code onFalse} routes for every step that has none. */
		@JsonSetter("defaults")
		void setDefaults(DefaultsNode defaults) {
			if (defaults != null) {
				onError = defaults.onError;
				onFalse = defaults.onFalse;
			}
		}

		@JsonSetter("onError")
		void rejectTopLevelOnError(Object ignored) {
			throw new IllegalArgumentException("a top-level onError moved under defaults: write defaults: { onError: { nextStage, then } }");
		}

		@JsonSetter("onFalse")
		void rejectTopLevelOnFalse(Object ignored) {
			throw new IllegalArgumentException("a top-level onFalse moved under defaults: write defaults: { onFalse: { nextStage, then } }");
		}
	}

	/** The top-level {@code defaults} block. */
	@Data
	public static class DefaultsNode {
		ErrorNode onError;
		ConditionFailedNode onFalse;
	}

	/**
	 * Reads {@code input} as text exactly as written in the file ({@code 1.50} stays "1.50"), either
	 * a single text or a read-only map of names to texts. A nested map, a list or an empty value is an
	 * error.
	 */
	public static class InputDeserializer extends JsonDeserializer<Object> {
		@Override
		public Object deserialize(JsonParser parser, DeserializationContext context) throws IOException {
			JsonToken token = parser.currentToken();
			if (token == JsonToken.START_ARRAY) {
				throw new IllegalArgumentException("input takes a text or a map of names to texts, not a list");
			}
			if (token != JsonToken.START_OBJECT) {
				return parser.getText();
			}
			Map<String, String> texts = new LinkedHashMap<>();
			while (parser.nextToken() == JsonToken.FIELD_NAME) {
				String key = parser.currentName();
				JsonToken value = parser.nextToken();
				if (value == JsonToken.VALUE_NULL) {
					throw new IllegalArgumentException("input '" + key + "' has no value; write '' for an empty text");
				}
				if (value == JsonToken.START_OBJECT || value == JsonToken.START_ARRAY) {
					throw new IllegalArgumentException("input '" + key + "' must be a single text, not " + (value == JsonToken.START_OBJECT ? "a map" : "a list"));
				}
				texts.put(key, parser.getText());
			}
			return Collections.unmodifiableMap(texts);
		}
	}

	/** Reads {@code include} as a YAML list of files or as comma-separated text. */
	public static class IncludeDeserializer extends JsonDeserializer<String> {
		@Override
		public String deserialize(JsonParser parser, DeserializationContext context) throws IOException {
			JsonNode node = parser.getCodec().readTree(parser);
			if (node.isArray()) {
				List<String> files = new ArrayList<>();
				node.forEach(file -> files.add(file.asText().trim()));
				return String.join(",", files);
			}
			if (node.isValueNode()) {
				return node.asText();
			}
			throw new IllegalArgumentException("include must be a file, a list of files, or comma-separated files");
		}
	}

	@Data
	public static class StateNode {
		String name;
		List<TransitionNode> steps;
		/** Steps with {@code runStage}; they are written in {@code steps} and separated here. */
		@JsonIgnore
		@Setter(AccessLevel.NONE)
		List<CallNode> calls;
		List<ReturnNode> returns;

		public List<TransitionNode> getSteps() {
			if (steps == null) {
				steps = new ArrayList<TransitionNode>();
			}
			return steps;
		}

		/** Reads {@code steps}: an entry with {@code runStage} is a call, every other entry an ordinary step. */
		public void setSteps(List<TransitionNode> value) {
			for (TransitionNode node : value) {
				if (node.runStage == null) {
					getSteps().add(node);
				} else if (node.nextStage != null) {
					throw new IllegalArgumentException("step '" + node.step + "' has both runStage and nextStage; a step that runs a stage names it with runStage only");
				} else {
					getCalls().add(CallNode.of(node));
				}
			}
		}

		/** The {@code calls} key was merged into {@code steps}. */
		@JsonSetter("calls")
		void rejectCallsKey(Object ignored) {
			throw new IllegalArgumentException("'calls' was merged into 'steps': write each call as a step with runStage: <stage>");
		}

		public void setReturns(List<ReturnNode> value) {
			for (ReturnNode node : value) {
				if (node.runStage != null) {
					throw new IllegalArgumentException("returns entry '" + node.step + "' cannot have runStage");
				}
				getReturns().add(node);
			}
		}

		@JsonIgnore
		public List<TransitionNode> getTransitionsForIterator() {
			List<TransitionNode> returns = new ArrayList<TransitionNode>();
			returns.addAll(getSteps());
			returns.addAll(getCalls());
			returns.addAll(getReturns());
			return returns;
		}

		public List<CallNode> getCalls() {
			if (calls == null) {
				calls = new ArrayList<CallNode>();
			}
			return calls;
		}

		public List<ReturnNode> getReturns() {
			if (returns == null) {
				returns = new ArrayList<ReturnNode>();
			}
			return returns;
		}

		@Override
		public String toString() {
			return name;
		}
	}

	@Data
	public static class TransitionNode {
		String step;
		String nextStage;
		/** On an entry in {@code steps}: makes it a call that runs this stage as a sub-flow. */
		String runStage;
		String then;
		String control;
		ActorNode action;
		ConditionNode check;
		ConditionFailedNode onFalse;
		ErrorNode onError;
		@Setter(AccessLevel.NONE)
		List<CaseNode> outcomes;

		@JsonIgnore
		String fileName;

		/** One entry per result; the catch-all is written as {@code otherwise}. */
		public void setOutcomes(List<CaseNode> value) {
			for (CaseNode outcome : value) {
				if (CaseNode.DEFAULT.equals(outcome.when)) {
					throw new IllegalArgumentException("step '" + step + "': write the catch-all outcome as otherwise: { nextStage, then } instead of when: default");
				}
			}
			outcomeList().addAll(0, value);
		}

		/** {@code otherwise}: where to go when no outcome matches the result. */
		@JsonSetter("otherwise")
		void setOtherwise(OtherwiseNode otherwise) {
			if (otherwise == null) {
				return;
			}
			CaseNode catchAll = new CaseNode();
			catchAll.when = CaseNode.DEFAULT;
			catchAll.nextStage = otherwise.nextStage;
			catchAll.then = otherwise.then;
			outcomeList().add(catchAll);
		}

		private List<CaseNode> outcomeList() {
			if (outcomes == null) {
				outcomes = new ArrayList<CaseNode>();
			}
			return outcomes;
		}

		@JsonIgnore
		public List<CaseNode> getOutcomesForIteration() {
			if (outcomes == null) {
				return new ArrayList<CaseNode>();
			}
			return outcomes;
		}

		@Override
		public String toString() {
			return "{ step: " + step + ", nextStage: " + nextStage + ", then: " + then + ", control: " + control + " }";
		}

		public String getFileName() {
			return fileName;
		}

		public void setFileName(String fileName) {
			this.fileName = fileName;
		}

	}

	@Data
	@EqualsAndHashCode(callSuper = false)
	public static class CallNode extends TransitionNode {

		@Override
		public String toString() {
			return super.toString().replace("nextStage:", "runStage:");
		}

		/** A call read from a {@code steps} entry with {@code runStage}. */
		static CallNode of(TransitionNode rule) {
			CallNode call = new CallNode();
			call.step = rule.step;
			call.nextStage = rule.runStage;
			call.runStage = rule.runStage;
			call.then = rule.then;
			call.control = rule.control;
			call.action = rule.action;
			call.check = rule.check;
			call.onFalse = rule.onFalse;
			call.onError = rule.onError;
			call.outcomes = rule.outcomes;
			call.fileName = rule.fileName;
			return call;
		}
	}

	@Data
	@EqualsAndHashCode(callSuper = false)
	public static class ReturnNode extends TransitionNode {

		@Override
		public String toString() {
			return super.toString();
		}

	}

	@Data
	public static class CaseNode {
		/** The internal {@code when} of the {@code otherwise} outcome. */
		static final String DEFAULT = "default";

		String when;
		String nextStage;
		String then;
	}

	/** {@code otherwise: { nextStage, then }} on a step. */
	@Data
	public static class OtherwiseNode {
		String nextStage;
		String then;
	}

	@Data
	public static class ActorNode {
		String beanName;
		@JsonProperty("class")
		String className;
		String method;
		String beanType;
		String lookupMethod;
		String lookupBeanType;
		String register = "true";
		String scope;
		List<ParameterNode> properties;

		@JsonIgnore
		String formattedBeanName;
		/** {@code input} written as text: the method is called as {@code method(ctx, String)}. */
		String input;

		/** {@code input} written as a map: the method is called as {@code method(ctx, Map<String, String>)}. */
		@JsonIgnore
		Map<String, String> inputMap;

		/** {@code input}: a text, or a map of names to texts (see {@link InputDeserializer}). */
		@JsonDeserialize(using = InputDeserializer.class)
		@SuppressWarnings("unchecked")
		public void setInput(Object value) {
			if (value instanceof Map<?, ?> map) {
				inputMap = (Map<String, String>) map;
			} else if (value != null) {
				input = value.toString();
			}
		}



		@JsonIgnore
		public Map<String, String> getParametesForIteration() {
			Map<String, String> returnValues = new LinkedHashMap<String, String>();
			if (properties == null) {
				return returnValues;
			}
			properties.forEach(e -> {
				returnValues.put(e.name, e.value);
			});
			return returnValues;
		}
	}

	@Data
	@EqualsAndHashCode(callSuper = false)
	public static class ConditionNode extends ActorNode {

	}

	@Data
	public static class ParameterNode {
		String name;
		String value;
	}

	@Data
	public static class ConditionFailedNode {
		String nextStage;
		String then;

		@Override
		public String toString() {
			return "onFalse: { nextStage: " + nextStage + ", then: " + then + " }";
		}
	}

	@Data
	public static class ErrorNode {
		String nextStage;
		String then;

		@Override
		public String toString() {
			return "onError: { nextStage: " + nextStage + ", then: " + then + " }";
		}
	}

	@Data
	public static class BeanRegistrationInfo {
		String beanType;
		Map<String, String> parameters;
		String beanName;
		String scope;
		Set<String> definitionPoints = new LinkedHashSet<String>();
	}
}

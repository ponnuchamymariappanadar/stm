package com.easysoft.stm.config;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Parses configuration text into an {@link STMRoot}. {@link ConfigXMLReader} and
 * {@link ConfigYAMLReader} are the implementations; the nested classes are the configuration model.
 */
public interface ConfigReader {
	<T> T parse(String xml);

	@Data
	@JacksonXmlRootElement(localName = "STM")
	public static class STMRoot {
		@JacksonXmlProperty(isAttribute = true)
		String name;

		@JacksonXmlProperty(isAttribute = true)
		String start;

		@JacksonXmlProperty(isAttribute = true)
		String fire;

		@JacksonXmlProperty(localName = "OnFailedCondition")
		ConditionFailedNode onFailedCondition;

		@JacksonXmlProperty(localName = "OnError")
		ErrorNode onError;

		@JacksonXmlElementWrapper(useWrapping = false)
		@JacksonXmlProperty(localName = "State")
		List<StateNode> states;

		@JacksonXmlProperty(isAttribute = true)
		String stmFiles;

		@JsonIgnore
		String fileName;
		
		@JacksonXmlProperty(isAttribute = true)
		String postProcessor;

		public List<StateNode> getStates() {
			if (states == null) {
				states = new ArrayList<StateNode>();
			}
			return states;
		}
	}

	@Data
	public static class StateNode {
		@JacksonXmlProperty(isAttribute = true)
		String name;

		@JacksonXmlElementWrapper(useWrapping = false)
		@JacksonXmlProperty(localName = "Transition")
		List<TransitionNode> transitions;

		@JacksonXmlElementWrapper(useWrapping = false)
		@JacksonXmlProperty(localName = "Call")
		List<CallNode> calls;

		@JacksonXmlElementWrapper(useWrapping = false)
		@JacksonXmlProperty(localName = "Return")
		List<ReturnNode> returns;

		public List<TransitionNode> getTransitions() {
			if (transitions == null) {
				transitions = new ArrayList<TransitionNode>();
			}
			return transitions;
		}

		public void setTransitions(List<TransitionNode> value) {
			if (transitions == null) {
				transitions = new ArrayList<TransitionNode>(value.size());
			}
			transitions.addAll(value);
		}

		public void setCalls(List<CallNode> value) {
			if (calls == null) {
				calls = new ArrayList<CallNode>(value.size());
			}
			calls.addAll(value);
		}

		public void setReturns(List<ReturnNode> value) {
			if (returns == null) {
				returns = new ArrayList<ReturnNode>(value.size());
			}
			returns.addAll(value);
		}

		@JsonIgnore
		public List<TransitionNode> getTransitionsForIterator() {
			List<TransitionNode> returns = new ArrayList<TransitionNode>();
			returns.addAll(getTransitions());
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
		@JacksonXmlProperty(isAttribute = true)
		String when;

		@JacksonXmlProperty(isAttribute = true)
		String to;

		@JacksonXmlProperty(isAttribute = true)
		String fire;

		@JacksonXmlProperty(isAttribute = true)
		String stmevent;
		

		@JacksonXmlProperty(localName = "Actor")
		ActorNode actor;

		@JacksonXmlProperty(localName = "Condition")
		ConditionNode condition;


		@JacksonXmlProperty(localName = "OnFailedCondition")
		ConditionFailedNode onFailedCondition;

		@JacksonXmlProperty(localName = "OnError")
		ErrorNode onError;

		@JacksonXmlElementWrapper(useWrapping = false)
		@JacksonXmlProperty(localName = "Case")
		List<CaseNode> cases;

		@JsonIgnore
		String fileName;

		@JsonIgnore
		public List<CaseNode> getCasesForIteration() {
			if (cases == null) {
				return new ArrayList<CaseNode>();
			}
			return cases;
		}

		@Override
		public String toString() {
			StringBuilder builder = new StringBuilder();
			builder.append("<transition ");
			builder.append("' when='");
			builder.append(when);
			builder.append("' to='");
			builder.append(to);

			builder.append("' fire='");
			builder.append(fire);
			builder.append("' stmevent='");
			builder.append(stmevent);
			builder.append("'/>");
			return builder.toString();
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

	}

	@Data
	@EqualsAndHashCode(callSuper = false)
	public static class ReturnNode extends TransitionNode {

	}

	@Data
	public static class CaseNode {
		@JacksonXmlProperty(isAttribute = true)
		String when;

		@JacksonXmlProperty(isAttribute = true)
		String to;

		@JacksonXmlProperty(isAttribute = true)
		String fire;
	}

	@Data
	public static class ActorNode {
		@JacksonXmlProperty(isAttribute = true)
		String beanName;

		@JacksonXmlProperty(isAttribute = true)
		String className;

		@JacksonXmlProperty(isAttribute = true)
		String methodName;

		@JacksonXmlProperty(isAttribute = true)
		String beanType;

		@JacksonXmlProperty(isAttribute = true)
		String lookupMethod;
		
		@JacksonXmlProperty(isAttribute = true)
		String lookupBeanType;

		@JacksonXmlProperty(isAttribute = true)
		String register = "true";

		@JacksonXmlProperty(isAttribute = true)
		String scope;

		@JacksonXmlElementWrapper(useWrapping = false)
		@JacksonXmlProperty(localName = "Property")
		List<ParameterNode> properties;

		@JsonIgnore
		String formattedBeanName;

		@JacksonXmlProperty(isAttribute = true)
		public String argParameter;



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
		@JacksonXmlProperty(isAttribute = true)
		String name;

		@JacksonXmlProperty(isAttribute = true)
		String value;
	}

	@Data
	public static class ConditionFailedNode {
		@JacksonXmlProperty(isAttribute = true)
		String to;

		@JacksonXmlProperty(isAttribute = true)
		String fire;

		@Override
		public String toString() {
			return "<onFailedCondition to='" + to + "' fire='" + fire + "'/>";
		}
	}

	@Data
	public static class ErrorNode {
		@JacksonXmlProperty(isAttribute = true)
		String to;

		@JacksonXmlProperty(isAttribute = true)
		String fire;

		@Override
		public String toString() {
			return "<onError to='" + to + "' fire='" + fire + "'/>";
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

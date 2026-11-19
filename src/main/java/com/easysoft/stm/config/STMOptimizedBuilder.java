package com.easysoft.stm.config;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.invoke.CallSite;
import java.lang.invoke.LambdaMetafactory;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodHandles.Lookup;
import java.lang.invoke.MethodType;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiFunction;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.ClassUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Validate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.util.StringValueResolver;

import com.easysoft.stm.Actor;
import com.easysoft.stm.Choice;
import com.easysoft.stm.ConditionAndActor;
import com.easysoft.stm.ConditionOnly;
import com.easysoft.stm.Event;
import com.easysoft.stm.EventCollection;
import com.easysoft.stm.MethodActor;
import com.easysoft.stm.MethodWithLookupArg;
import com.easysoft.stm.MethodWithMapArg;
import com.easysoft.stm.MethodWithStringArg;
import com.easysoft.stm.PlaceHolderTransition;
import com.easysoft.stm.STMContext;
import com.easysoft.stm.STMException;
import com.easysoft.stm.STMStateful;
import com.easysoft.stm.State;
import com.easysoft.stm.StateCollection;
import com.easysoft.stm.StmEventBasedTransition;
import com.easysoft.stm.Transition;
import com.easysoft.stm.TriFunction;
import com.easysoft.stm.STM.STMEvent;
import com.google.common.collect.Lists;
import com.easysoft.stm.actors.CallState;
import com.easysoft.stm.actors.CallStateResult;
import com.easysoft.stm.actors.CallStateReturn;
import com.easysoft.stm.actors.NoOpActor;
import com.easysoft.stm.condition.PassCondition;
import com.easysoft.stm.config.ConfigReader.ActorNode;
import com.easysoft.stm.config.ConfigReader.BeanRegistrationInfo;
import com.easysoft.stm.config.ConfigReader.CallNode;
import com.easysoft.stm.config.ConfigReader.CaseNode;
import com.easysoft.stm.config.ConfigReader.ConditionFailedNode;
import com.easysoft.stm.config.ConfigReader.ErrorNode;
import com.easysoft.stm.config.ConfigReader.ReturnNode;
import com.easysoft.stm.config.ConfigReader.STMRoot;
import com.easysoft.stm.config.ConfigReader.StateNode;
import com.easysoft.stm.config.ConfigReader.TransitionNode;

/**
 * Builds an {@link STMStateful} from one or more YAML flow files.
 * <p>
 * The builder fills in defaults ({@code nextStage} omitted means the current stage;
 * {@code nextStage} and {@code then} both omitted means a terminal step), binds actor methods with
 * {@code LambdaMetafactory}, validates the whole graph, and collapses chains of placeholder
 * transitions.
 *
 * <pre>{@code
 * STMOptimizedBuilder<STMContext> builder = new STMOptimizedBuilder<>(null, false);
 * builder.addFile("classpath:stm/order-flow.yaml");
 * STMStateful<STMContext> stm = builder.build();
 * }</pre>
 *
 * Pass a Spring {@code ApplicationContext} to resolve {@code beanType}/{@code beanName} actors, or
 * {@code null} when only {@code className} actors are used. With {@code validate = true}, actors are
 * not created and only the structure is checked.
 *
 * @param <T> the application context type
 */
public class STMOptimizedBuilder<T extends STMContext> {
	private static final String RESULT = "@RESULT";
	private static Logger logger = LoggerFactory.getLogger(STMOptimizedBuilder.class);

	private static String DEFAULT_STM_EVENT = "next";
	private static String DEFAULT_FOR_TO_NOT_DEFINED_STM_EVENT = "stop";
	private static Actor<STMContext> noop = new NoOpActor();
	private static Actor<STMContext> callState = new CallState();
	private static Actor<STMContext> returnState = new CallStateReturn();
	private static Actor<STMContext> resultState = new CallStateResult();
	private static PassCondition<STMContext> pass = new PassCondition<STMContext>();
	// Shared on purpose: beans with identical type and parameters are registered once across all files.
	// Concurrent/atomic so builders can run in parallel.
	private static final Map<String, String> parameterBeanNameToBeanNameMap = new ConcurrentHashMap<>();
	private static final AtomicInteger stmIndex = new AtomicInteger();
	private static final AtomicInteger fileIndex = new AtomicInteger();
	private int currentFileIndex;

	int stateIndex = 0;
	int eventIndex = 0;

	State defaultState = null;
	Event defaultEvent = null;
	String name;
	boolean debug = false;
	String fsmName;
	boolean defaultPopulated = false;

	ErrorNode globalError = null;
	ConditionFailedNode globalFailedCondition = null;

	StateCollection states = new StateCollection();
	EventCollection events = new EventCollection();
	StateCollection reducedStates = new StateCollection();

	private STMEvent noToSTMEvent;
	private ApplicationContext applicationContext;

	private boolean validateOnly = false;
	private boolean applyPlaceHolderTransitionReduction = true;
	private Map<String, String> definedStates = new HashMap<String, String>();
	public List<STMRoot> stmRoots = new ArrayList<STMRoot>();
	MethodHandles.Lookup methodHandleLookup;
	StringValueResolver resolver;
	Class<? extends STMContext> contextType = STMContext.class;
	Map<String, BeanRegistrationInfo> beanRegistrationMap = new LinkedHashMap<String, BeanRegistrationInfo>();
	Set<String> stmFileUsed = new HashSet<String>();
	Set<String> callStates = new HashSet<String>();
	Set<String> returnStates = new HashSet<String>();
	private String stmPostProcessor;

	public STMOptimizedBuilder(ApplicationContext applicationContext, boolean validate) {
		this(applicationContext, validate, true);
	}

	public STMOptimizedBuilder(ApplicationContext applicationContext, boolean validate, boolean applyReduction) {
		this.applicationContext = applicationContext;
		noToSTMEvent = STMEvent.valueOf(DEFAULT_FOR_TO_NOT_DEFINED_STM_EVENT.toUpperCase());
		validateOnly = validate;
		this.applyPlaceHolderTransitionReduction = applyReduction;
		this.methodHandleLookup = MethodHandles.lookup();
	}

	public STMOptimizedBuilder(ApplicationContext applicationContext, boolean validate, StringValueResolver resolver) {
		this(applicationContext, validate);
		this.resolver = resolver;
	}

	public STMOptimizedBuilder<T> add(ConfigReader reader, String configData, String fileName) {
		if (!stmFileUsed.add(fileName)) {
			logger.info("STM already the file loaded {}", fileName);
			return this;
		}
		STMRoot stm = reader.parse(configData);
		if (fsmName == null) {
			fsmName = stm.name;
		}
		if (stmPostProcessor == null) {
			this.stmPostProcessor = stm.postProcessor;
			postProcessorBeanInfo(stm.postProcessor, fsmName);
		}
		stmRoots.add(stm);
		currentFileIndex = fileIndex.incrementAndGet();
		stm.fileName = "<contentPassed>";
		if (fileName != null) {
			stm.fileName = fileName;
		}
		resolvePlaceHolderProperty(stm);

		if (StringUtils.isNotBlank(stm.getInclude())) {
			logger.info("STM loading the file {}", fileName);
			for (String file : stm.getInclude().split(",")) {
				try {
					addFile(file.trim());
				} catch (Exception e) {
					logger.info(e.getMessage(), e);
					throw new STMException(e);
				}
			}
		} else {
			logger.info("STM single file loaded {}", fileName);
		}
		return this;
	}

	private void postProcessorBeanInfo(String postProcessor, String fsmName) {
		if (StringUtils.isBlank(postProcessor)) {
			return;
		}
		String[] postProcessors = new String[1];
		if (postProcessor.contains(",")) {
			postProcessors = postProcessor.split(",");
		} else {
			postProcessors[0] = postProcessor;
		}
		for(String processor : postProcessors) {
			processor = processor.trim();
			BeanRegistrationInfo beanInfo = createBeanCreationInfo(processor);
			beanInfo.beanName  = fsmName + processor + "postProcessor";
			beanInfo.definitionPoints.add(beanInfo.beanName);
		}
	}

	public STMOptimizedBuilder<T> addFile(String configFile) throws Exception {
		if (!configFile.endsWith(".yml") && !configFile.endsWith(".yaml")) {
			throw new STMException("only YAML flow files (.yaml or .yml) are supported: " + configFile);
		}
		return add(new ConfigYAMLReader(), readContent(configFile), configFile);
	}

	public static String readContent(String configFile) throws IOException, Exception {
		if (configFile.startsWith("classpath:")) {
			DefaultResourceLoader resource = new DefaultResourceLoader();
			try (InputStream inputStream = resource.getResource(configFile).getInputStream()) {
				return IOUtils.toString(inputStream, "UTF-8");
			}
		}
		try (InputStream inputStream = new FileInputStream(configFile)) {
			return IOUtils.toString(inputStream, "UTF-8");
		}
	}

	public STMStateful<T> build() {
		states = new StateCollection();
		events = new EventCollection();
		reducedStates = new StateCollection();
		defaultState = null;
		defaultEvent = null;
		globalError = null;
		globalFailedCondition = null;
		definedStates.clear();

		for (STMRoot fsm : stmRoots) {
			build(fsm);
		}
		if (defaultState == null) {
			throw new STMException("Default state for stmStateful missing");
		}
		if (defaultEvent == null) {
			throw new STMException("Default event for stmStateful missing");
		}
		for (STMRoot stm : stmRoots) {
			populateErrorTransition(stm, states, events);
			wrapSwitchHandle(stm, states, events);
		}
		for (String callState : callStates) {
			if (!returnStates.contains(callState)) {
				throw new STMException("Call state dont have return defined " + callState);
			}
		}
		if (applyPlaceHolderTransitionReduction) {
			reducePlaceHolderTransition(states, events);
			reduceErrorAndCondition(reducedStates, events);
		} else {
			reducedStates = states;
		}
		logger.info("Printing the state info");
		printStates(reducedStates);
		if (applyPlaceHolderTransitionReduction) {
			logger.info("Printing the state reduction info");
			printCompareInfo(reducedStates, states);
		}
		STMStateful<T> f = createSTM(reducedStates, events, defaultState, defaultEvent);
		return f;
	}

	void resolvePlaceHolderProperty(STMRoot stm) {

		for (StateNode stateNode : stm.getStages()) {
			stateNode.name = resolvePlaceHolderProperty(stateNode.name);
			for (TransitionNode transitionNode : stateNode.getTransitionsForIterator()) {
				transitionNode.then = resolvePlaceHolderProperty(transitionNode.then);
				transitionNode.nextStage = resolvePlaceHolderProperty(transitionNode.nextStage);
				transitionNode.step = resolvePlaceHolderProperty(transitionNode.step);
				transitionNode.control = resolvePlaceHolderProperty(transitionNode.control);
				resolveActorNode(stateNode, transitionNode);
				resolveCondition(stateNode, transitionNode);
				resolveOnErrorNode(transitionNode);
				resolveOnFailedConditionNode(transitionNode);
				resolveCasesNode(transitionNode);

			}
		}
		resolveStmProperty(stm);
	}

	private void resolveStmProperty(STMRoot stm) {
		stm.startStep = resolvePlaceHolderProperty(stm.startStep);
		stm.name = resolvePlaceHolderProperty(stm.name);
		stm.startStage = resolvePlaceHolderProperty(stm.startStage);
		if (stm.onError != null) {
			stm.onError.then = resolvePlaceHolderProperty(stm.onError.then);
			stm.onError.nextStage = resolvePlaceHolderProperty(stm.onError.nextStage);
		}

		if (stm.onFalse != null) {
			stm.onFalse.then = resolvePlaceHolderProperty(stm.onFalse.then);
			stm.onFalse.nextStage = resolvePlaceHolderProperty(stm.onFalse.nextStage);
		}
	}

	private void resolveCasesNode(TransitionNode transitionNode) {
		if (transitionNode.outcomes != null && !transitionNode.outcomes.isEmpty()) {
			for (CaseNode node : transitionNode.outcomes) {
				node.then = resolvePlaceHolderProperty(node.then);
				node.nextStage = resolvePlaceHolderProperty(node.nextStage);
				node.when = resolvePlaceHolderProperty(node.when);
			}
		}
	}

	private void resolveOnFailedConditionNode(TransitionNode transitionNode) {
		if (transitionNode.onFalse != null) {
			transitionNode.onFalse.then = resolvePlaceHolderProperty(transitionNode.onFalse.then);
			transitionNode.onFalse.nextStage = resolvePlaceHolderProperty(transitionNode.onFalse.nextStage);
		}
	}

	private void resolveOnErrorNode(TransitionNode transitionNode) {
		if (transitionNode.onError != null) {
			transitionNode.onError.then = resolvePlaceHolderProperty(transitionNode.onError.then);
			transitionNode.onError.nextStage = resolvePlaceHolderProperty(transitionNode.onError.nextStage);
		}
	}

	private void resolveCondition(StateNode stateNode, TransitionNode transitionNode) {
		if (transitionNode.check != null) {
			transitionNode.check.beanName = resolvePlaceHolderProperty(transitionNode.check.beanName);
			transitionNode.check.beanType = resolvePlaceHolderProperty(transitionNode.check.beanType);
			transitionNode.check.className = resolvePlaceHolderProperty(transitionNode.check.className);
			transitionNode.check.method = resolvePlaceHolderProperty(transitionNode.check.method);
			if (transitionNode.check.beanType != null && (transitionNode.check.register == null || "true".equals(transitionNode.check.register))) {
				Map<String, String> parameters = transitionNode.check.getParametesForIteration();
				transitionNode.check.formattedBeanName = addParameter(stateNode, transitionNode, parameters, transitionNode.check.beanType, transitionNode.check.scope);
			}
		}
	}

	private void resolveActorNode(StateNode stateNode, TransitionNode transitionNode) {
		if (transitionNode.action != null) {
			transitionNode.action.beanName = resolvePlaceHolderProperty(transitionNode.action.beanName);
			transitionNode.action.beanType = resolvePlaceHolderProperty(transitionNode.action.beanType);
			transitionNode.action.className = resolvePlaceHolderProperty(transitionNode.action.className);
			transitionNode.action.method = resolvePlaceHolderProperty(transitionNode.action.method);
			if (transitionNode.action.beanType != null && (transitionNode.action.register == null || "true".equals(transitionNode.action.register))) {
				Map<String, String> parameters = transitionNode.action.getParametesForIteration();
				transitionNode.action.formattedBeanName = addParameter(stateNode, transitionNode, parameters, transitionNode.action.beanType, transitionNode.action.scope);
			}
		}
	}

	private String addParameter(StateNode stateNode, TransitionNode transitionNode, Map<String, String> parameters, String beanType, String scope) {
		String beanRegistrationKey = beanType;
		String definitionPoint = createBeanName(stateNode.name, transitionNode.step);
		if (parameters.size() > 0) {
			beanRegistrationKey = definitionPoint;
			StringBuilder parameterKey = new StringBuilder();
			parameterKey.append(beanType);
			parameters.forEach((k, v) -> {
				parameterKey.append(k);
				parameterKey.append(v);
			});
			String otherBeanName = parameterBeanNameToBeanNameMap.putIfAbsent(parameterKey.toString(), beanRegistrationKey);
			if (otherBeanName != null) {
				beanRegistrationKey = otherBeanName;
			}
		}
		BeanRegistrationInfo registration = beanRegistrationMap.get(beanRegistrationKey);
		if (registration == null) {
			registration = new BeanRegistrationInfo();
			registration.beanName = beanRegistrationKey;
			registration.beanType = beanType;
			registration.scope = scope;
			registration.parameters = parameters;
			beanRegistrationMap.put(beanRegistrationKey, registration);
		}
		registration.getDefinitionPoints().add(definitionPoint);
		return definitionPoint;
	}
	
	public BeanRegistrationInfo createBeanCreationInfo(String beanType) {
		BeanRegistrationInfo registration = new BeanRegistrationInfo();
		registration.beanType = beanType;
		registration.parameters = new LinkedHashMap<>();;
		beanRegistrationMap.put(beanType, registration);
		return registration;
	}

	public String createBeanName(String stateName, String eventName) {
		return stateName + "." + eventName + currentFileIndex;
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	String resolvePlaceHolderProperty(String placeHolder) {
		if (placeHolder == null) {
			return null;
		}
		if (placeHolder.startsWith("e:")) {
			String enumConstant = StringUtils.substringAfter(placeHolder, ":");
			String enumClazz = StringUtils.substringBeforeLast(enumConstant, ".");
			String enumValue = StringUtils.substringAfterLast(enumConstant, ".");
			try {
				Enum<?> value = Enum.valueOf((Class<Enum>) ClassUtils.getClass(enumClazz), enumValue);
				return value.name();
			} catch (Exception e) {
				logger.info(e.getMessage(), e);
				throw new STMException(e);
			}
		}
		if (resolver == null) {
			return placeHolder;
		}
		return resolver.resolveStringValue(placeHolder);
	}

	void build(STMRoot stm) {
		// global handlers come from the first file that declares them, even if it has no states
		if (globalError == null) {
			globalError = stm.onError;
		}
		if (globalFailedCondition == null) {
			globalFailedCondition = stm.onFalse;
		}

		// populate the transition, handler and condition
		for (StateNode stateNode : stm.getStages()) {
			if (StringUtils.isAllBlank(stateNode.name)) {
				String msg = "State name " + stateNode.name + " cannot be null";
				logger.info(msg);
				throw new STMException(msg);
			}
			if (definedStates.containsKey(stateNode.name)) {
				String msg = "State " + stateNode.name + " already defined on the files " + definedStates.get(stateNode.name) + "," + stm.fileName;
				logger.info(msg);
				throw new STMException(msg);
			}
			definedStates.put(stateNode.name, stm.fileName);
			State fromState = createState(states, stateNode.name);
			if (defaultState == null) {
				defaultState = fromState;
				name = stm.name;
			}
			Set<TransitionNode> newTransitions = new HashSet<TransitionNode>();
			validateTransitionUniqueness(stm, stateNode);
			stateNode.getTransitionsForIterator().addAll(newTransitions);
			for (TransitionNode transitionNode : stateNode.getTransitionsForIterator()) {
				transitionNode.setFileName(stm.fileName);
				validateTransitionNodeWhen(stm, stateNode, transitionNode);
				validateTransitionNodeToAndFire(stm, stateNode, transitionNode);
				validateTransitionNodeToAndFire2(stm, stateNode, transitionNode);
				changeTransitionNodeToThis(stm, stateNode, transitionNode);
				Event fromEvent = createEvent(events, transitionNode.step);
				if (defaultEvent == null) {
					defaultEvent = fromEvent;
				}
				changeTransitionNodeToAndFire(stm, stateNode, fromState, transitionNode);
				State toState = createState(states, transitionNode.nextStage);
				Event toEvent = createEvent(events, transitionNode.then);
				createTransition(states, events, fromState, transitionNode, fromEvent, toState, toEvent);
				if (globalError == null) {
					globalError = stm.onError;
				}
				if (globalFailedCondition == null) {
					globalFailedCondition = stm.onFalse;
				}
			}
		}
		populateDefaultStateAndEvent(stm);
	}

	private void validateTransitionUniqueness(STMRoot stm, StateNode stateNode) {
		Set<String> transitionWhen = new HashSet<String>();
		for (TransitionNode transitionNode : stateNode.getTransitionsForIterator()) {
			if (!transitionWhen.add(transitionNode.step)) {
				String msg = stm.fileName + " - transiton is duplicated in the same state " + stateNode.name + " step=" + transitionNode.step;
				logger.info(msg);
				throw new STMException(msg);
			}
		}
	}

	private void populateDefaultStateAndEvent(STMRoot stm) {
		if (!defaultPopulated) {
			if (stm.startStage != null && stm.startStep != null) {
				defaultState = states.getState(stm.startStage);
				defaultEvent = events.getEvent(stm.startStep);
				defaultPopulated = true;
				name = stm.name;
			}
		}
	}

	private void changeTransitionNodeToAndFire(STMRoot stm, StateNode stateNode, State fromState, TransitionNode transitionNode) {
		// if no to state, then create one to state from current state
		// name and id and -end-state, for event create -end-when
		if (transitionNode.nextStage == null && transitionNode.then == null) {
			transitionNode.nextStage = stateNode.name + "-" + fromState.getId() + "-end-state";
			transitionNode.then = stateNode.name + "-" + fromState.getId() + "-end-when";
			transitionNode.control = DEFAULT_FOR_TO_NOT_DEFINED_STM_EVENT;
			if (debug) {
				logger.info(stm.fileName + " - Transition nextStage and then missing, so generated for "
						+ Transition.formatTranstion(stateNode.name, transitionNode.step, transitionNode.nextStage, transitionNode.then, STMEvent.STOP));
			}
		}
	}

	private void changeTransitionNodeToThis(STMRoot stm, StateNode stateNode, TransitionNode transitionNode) {
		// if transition node to is this, then put to as current state
		if ("this".equalsIgnoreCase(transitionNode.nextStage)) {
			String msg = stm.fileName + " - Transition nextStage: this, so assuming the current to state as current state "
					+ Transition.formatTranstion(stateNode.name, transitionNode.step, transitionNode.nextStage, transitionNode.then, STMEvent.NEXT);

			if (debug) {
				logger.info(msg);
			}
			transitionNode.nextStage = stateNode.name;
		}
	}

	private void validateTransitionNodeToAndFire2(STMRoot stm, StateNode stateNode, TransitionNode transitionNode) {
		if (transitionNode.nextStage != null && transitionNode.then == null) {
			String msg = stm.fileName + " - Transition with both then and nextStage should be defined  "
					+ Transition.formatTranstion(stateNode.name, transitionNode.step, transitionNode.nextStage, transitionNode.then, STMEvent.STOP);
			logger.info(msg);
			throw new STMException(msg);
		}
	}

	private void validateTransitionNodeToAndFire(STMRoot stm, StateNode stateNode, TransitionNode transitionNode) {
		if (transitionNode.nextStage == null && transitionNode.then != null) {
			String msg = stm.fileName + " - Transition without nextStage but with then, so assuming the current to state as current state "
					+ Transition.formatTranstion(stateNode.name, transitionNode.step, transitionNode.nextStage, transitionNode.then, STMEvent.NEXT);
			if (debug) {
				logger.info(msg);
			}
			transitionNode.nextStage = stateNode.name;
		}
	}

	private void validateTransitionNodeWhen(STMRoot stm, StateNode stateNode, TransitionNode transitionNode) {
		if (transitionNode.step == null) {
			String msg = stm.fileName + " - Transition step missing for "
					+ Transition.formatTranstion(stateNode.name, transitionNode.step, transitionNode.nextStage, transitionNode.then, STMEvent.STOP);
			logger.info(msg);
			throw new STMException(msg);
		}
	}

	private void printStates(StateCollection states) {
		for (State state : states.getStates()) {
			logger.info("State {}", state.getName());
			for (Transition transition : state.getTransitons()) {
				logger.info(String.format("  %s ===>  %s ---> %s (%s)", transition.getFromEvent().getName(), transition.getToState().getName(), transition.getToEvent().getName(),
						getHandleForPrint(transition)));
				printChoiceNode(transition);
				printOnErrorNode(transition);
				printConditionOnlyOrConditionAndActor(transition);
			}
		}
	}

	private void printConditionOnlyOrConditionAndActor(Transition transition) {
		if (transition instanceof ConditionOnly || transition instanceof ConditionAndActor) {
			ConditionAndActor condition = (ConditionAndActor) transition;
			Transition failed = condition.getOnFailedConditionTransition();
			if (failed != null)
				logger.info(String.format("      OnFailedCondition ===>  %s --->  %s (%s)", failed.getFromState().getName(),
						failed.getFromEvent().getName(), getHandleForPrint(failed)));
		}
	}

	private void printOnErrorNode(Transition transition) {
		if (transition.getOnErrorTransition() != null) {
			logger.info(String.format("      OnError ===>  %s --->  %s (%s)", transition.getOnErrorTransition().getToState().getName(),
					transition.getOnErrorTransition().getToEvent().getName(), getHandleForPrint(transition.getOnErrorTransition())));
		}
	}

	private void printChoiceNode(Transition transition) {
		if (transition.getTransitionHandle() instanceof Choice) {
			@SuppressWarnings("unchecked")
			Choice<T> ch = (Choice<T>) transition.getTransitionHandle();
			logger.info(String.format("      It is switch for underlying handler %s", ch.getActualActor()));
			Map<String, Transition> transitionsCollection = ch.getTransitions();
			for (Entry<String, Transition> t : transitionsCollection.entrySet()) {
				logger.info(String.format("         %s ===>  %s --->  %s (%s)", t.getKey(), t.getValue().getFromState().getName(), t.getValue().getFromEvent().getName(),
						getHandleForPrint(t.getValue())));
			}
		}
	}

	private void printCompareInfo(StateCollection reducedStates, StateCollection states) {
		for (State state : states.getStates()) {
			logger.info("State {}", state.getName());
			State newState = reducedStates.getState(state.getName());
			if (newState == null) {
				logger.info("Reduced dont have the state " + state);
				throw new STMException("Reduction dont have state " + state);
			}
			for (Transition transition : state.getTransitons()) {
				Transition newTransition = newState.getTransition(transition.getFromEvent());
				if (newTransition == null) {
					logger.info("Reduction dont have transition {}", transition);
					throw new STMException("Reduced dont have transition " + transition);
				}
				logger.info(String.format("  %s|%s|%s <=== %s|%s|%s", newTransition.getFromEvent().getName(), newTransition.getToState().getName(),
						newTransition.getToEvent().getName(), transition.getFromEvent().getName(), transition.getToState().getName(), transition.getToEvent().getName()));
			}
		}
	}

	private void reduceErrorAndCondition(StateCollection states, EventCollection events) {
		for (State state : states.getStates()) {
			for (Transition transition : state.getTransitons()) {
				reduceOnErrorTransition(states, events, transition);
				reduceConditionOnlyOrConditionAndActor(states, events, transition);
				reduceChoiceNode(states, transition);
			}
		}
	}

	private void reduceOnErrorTransition(StateCollection states, EventCollection events, Transition transition) {
		if (transition.getOnErrorTransition() != null) {
			Transition onErrorTransition = transition.getOnErrorTransition();
			State errorState = states.getState(onErrorTransition.getFromState().getName());
			Event errorEvent = events.getEvent(onErrorTransition.getFromEvent().getName());
			Transition newErrorTransition = errorState.getTransition(errorEvent);
			if (onErrorTransition != newErrorTransition) {
				transition.setOnErrorTransition(newErrorTransition);
			}

			Transition onResultTransition = transition.getOnResultTransition();
			if (onResultTransition != null) {
				State resultState = states.getState(onResultTransition.getFromState().getName());
				Event resultEvent = events.getEvent(onResultTransition.getFromEvent().getName());
				Transition newOnResultTransition = resultState.getTransition(resultEvent);
				if (newOnResultTransition != onResultTransition) {
					transition.setOnResultTransition(newOnResultTransition);
				}
			}
		}
	}

	private void reduceConditionOnlyOrConditionAndActor(StateCollection states, EventCollection events, Transition transition) {
		if (transition instanceof ConditionOnly || transition instanceof ConditionAndActor) {
			ConditionAndActor condition = (ConditionAndActor) transition;
			if (condition.getOnFailedConditionTransition() != null) {
				Transition onConditionFailed = condition.getOnFailedConditionTransition();
				State errorState = states.getState(onConditionFailed.getFromState().getName());
				Event errorEvent = events.getEvent(onConditionFailed.getFromEvent().getName());
				Transition newOnConditionFailed = errorState.getTransition(errorEvent);
				if (onConditionFailed != newOnConditionFailed) {
					condition.setOnFailedConditionTransition(newOnConditionFailed);
				}
			}
		}
	}

	private void reduceChoiceNode(StateCollection states, Transition transition) {
		// Reduce Case Condition
		if (transition.getTransitionHandle() instanceof Choice) {
			@SuppressWarnings("unchecked")
			Choice<T> ch = (Choice<T>) transition.getTransitionHandle();
			Map<String, Transition> transitionsCollection = ch.getTransitions();
			Map<String, Transition> transCollection = new HashMap<String, Transition>(transitionsCollection);
			transitionsCollection.clear();
			for (Entry<String, Transition> entry : transCollection.entrySet()) {
				Transition t = entry.getValue();
				State s = states.getState(t.getFromState().getId());
				Transition newTransition = s.getTransition(t.getFromEvent());
				transitionsCollection.put(entry.getKey(), newTransition);
			}
		}
	}

	private STMStateful<T> createSTM(StateCollection states, EventCollection events, State defaultState, Event defaultEvent) {
		validateConfiguration(states);
		STMStateful<T> f = new STMStateful<T>(stmIndex.getAndIncrement(), name, defaultState, defaultEvent, states, events);
		if (StringUtils.isBlank(stmPostProcessor) || validateOnly) {
			return f;
		}
		String[] postProcessors = new String[1];
		if (stmPostProcessor.contains(",")) {
			postProcessors = stmPostProcessor.split(",");
		} else {
			postProcessors[0] = stmPostProcessor;
		}
		for(String processor : postProcessors) {
			processor = processor.trim();
			STMPostProcessor bean = (STMPostProcessor) getBean(applicationContext, processor);
			bean.postProcess(states, f, this);
		}
		return f;
	}

	private void reducePlaceHolderTransition(StateCollection states, EventCollection events) {
		for (State state : states.getStates()) {
			State reducedState = new State(state.getId(), state.getName());
			for (Transition transition : state.getTransitons()) {
				Transition nonPlaceHolderTransition = getNonPlaceHolderTransition(transition, state, transition.getFromEvent());
				if (transition == nonPlaceHolderTransition) {
					reducedState.addTransition(transition.getFromEvent(), nonPlaceHolderTransition);
				} else {
					Transition newTransition = cloneTransition(nonPlaceHolderTransition, reducedState, transition.getFromEvent(), nonPlaceHolderTransition.getToState(),
							nonPlaceHolderTransition.getToEvent());
					reducedState.addTransition(transition.getFromEvent(), newTransition);
				}
			}
			reducedStates.addState(reducedState);
		}
	}

	private Transition getNonPlaceHolderTransition(Transition currentTransition, State state, Event event) {
		HashSet<Transition> visited = new HashSet<Transition>();
		Transition transition = getNonPlaceHolderTransition(visited, state, event, 1);
		if (currentTransition != transition) {
			if (debug) {
				logger.info("Reduction {} ----> {}", state.getName(), event.getName());
			}
			for (Transition t : visited) {
				if (debug) {
					logger.info("    =====> {} ---->  {}", t.getFromState().getName(), t.getFromEvent().getName());
				}
			}
			if (debug) {
				logger.info(String.format("Reduction finally %s ----->  %s =======>  %s ---> %s (%s-%s)", state.getName(), event.getName(), transition.getFromState().getName(),
						transition.getFromEvent().getName(), currentTransition.getNextSTMEvent(), transition.getNextSTMEvent()));
			}
		} else {
			if (debug) {
				logger.info(String.format("Reduction nochange %s ---->  %s (%s)", state.getName(), event.getName(), currentTransition));
			}
		}
		return transition;
	}

	private Transition getNonPlaceHolderTransition(HashSet<Transition> visited, State state, Event event, int recursionCount) {
		Transition transition = state.getTransition(event);
		if (transition == null) {
			throw new STMException("no transition found for event from state " + state + ", event " + event);
		}
		if (visited.contains(transition)) {
			logger.info("Looping the transition" + transition + ", list = " + visited);
			throw new STMException("Loop on the transiton");
		}
		if (recursionCount > 20) {
			logger.info("place holder transition is too deep, level > 20" + visited);
			throw new STMException("level is too deep");
		}
		if (transition.getNextSTMEvent() == STMEvent.STOP) {
			return transition;
		}
		if (transition instanceof PlaceHolderTransition) {
			visited.add(transition);
			return getNonPlaceHolderTransition(visited, transition.getToState(), transition.getToEvent(), recursionCount + 1);
		}
		return transition;
	}

	void validateConfiguration(StateCollection states) {
		for (State state : states.getStates()) {
			for (Transition transition : state.getTransitons()) {
				if (transition.getToState().getTransition(transition.getToEvent()) == null && transition.getNextSTMEvent() != noToSTMEvent) {
					String msg = "Transition missing " + transition.getToLongStringValue();
					logger.info(msg);
					throw new STMException(msg);
				}
				if (transition.getFromState() == transition.getToState() && transition.getFromEvent() == transition.getToEvent()) {
					String msg = "Transition is looping for " + transition;
					logger.info(msg);
					throw new STMException(msg);
				}
			}
		}
	}

	void populateErrorTransition(STMRoot stmRoot, StateCollection states, EventCollection events) {

		ErrorNode stmScopedError = stmRoot.onError;
		if (stmScopedError == null) {
			stmScopedError = this.globalError;
		}
		ConditionFailedNode stmScopedOnConditionFailed = stmRoot.onFalse;
		if (stmScopedOnConditionFailed == null) {
			stmScopedOnConditionFailed = this.globalFailedCondition;
		}
		// Iteration to populate error and onFailedCondition
		// populate the transition, handler and condition
		for (StateNode stateNode : stmRoot.getStages()) {
			State fromState = states.getState(stateNode.name);
			for (TransitionNode transitionNode : stateNode.getTransitionsForIterator()) {
				Event fromEvent = events.getEvent(transitionNode.step);
				State toState = states.getState(transitionNode.nextStage);
				if (fromEvent == null || toState == null) {
					String msg = transitionNode.fileName + " -Missing stage " + stateNode.name + " step " + transitionNode.step + " nextStage " + transitionNode.nextStage;
					logger.info(msg);
					throw new STMException(msg);
				}
				Transition currentTransition = fromState.getTransition(fromEvent);
				// for place holder transition no error or
				// conditionFailedHandler
				if (currentTransition instanceof PlaceHolderTransition) {
					continue;
				}
				Transition errorTransition = null;
				Transition onFailedCondition = null;
				String errorMsg = "onError";

				if (transitionNode.onError == null) {
					if (stmScopedError == null) {
						String msg = stmRoot.fileName + " - Missing onError on the step and under defaults " + currentTransition;
						logger.info(msg);
						throw new STMException(msg);
					}
					errorTransition = getErrorFailedConditionTransition(states, events, transitionNode, stmRoot.fileName, stateNode.name, errorMsg, stmScopedError.nextStage,
							stmScopedError.then);
				} else {
					if ("this".equals(transitionNode.onError.nextStage)) {
						transitionNode.onError.nextStage = stateNode.name;
					}
					errorTransition = getErrorFailedConditionTransition(states, events, transitionNode, stmRoot.fileName, stateNode.name, errorMsg, transitionNode.onError.nextStage,
							transitionNode.onError.then);
				}
				if (currentTransition == null) {
					String msg = transitionNode.fileName + " transition is null stage " + stateNode.name + " step " + transitionNode.step + " nextStage " + transitionNode.nextStage;
					logger.info(msg);
					throw new STMException(msg);
				}
				currentTransition.setOnErrorTransition(errorTransition);
				// if transition with Condition
				if (currentTransition instanceof ConditionOnly || currentTransition instanceof ConditionAndActor) {
					if (transitionNode.onFalse == null) {
						if (stmScopedOnConditionFailed == null) {
							String msg = stmRoot.fileName + " - Missing onFalse on the step and under defaults " + currentTransition;
							logger.info(msg);
							throw new STMException(msg);
						}
						onFailedCondition = getErrorFailedConditionTransition(states, events, transitionNode, stmRoot.fileName, stateNode.name, "onFalse",
								stmScopedOnConditionFailed.nextStage, stmScopedOnConditionFailed.then);
					} else {
						if ("this".equals(transitionNode.onFalse.nextStage)) {
							transitionNode.onFalse.nextStage = stateNode.name;
						}
						onFailedCondition = getErrorFailedConditionTransition(states, events, transitionNode, stmRoot.fileName, stateNode.name, "onFalse",
								transitionNode.onFalse.nextStage, transitionNode.onFalse.then);
					}
					if (transitionNode.check != null && onFailedCondition == null) {
						String msg = stmRoot.fileName + " - Missing onConditionFailed, so use the global condition failed also not defined for " + currentTransition;
						logger.info(msg);
						throw new STMException(msg);
					}
					((ConditionAndActor) currentTransition).setOnFailedConditionTransition(onFailedCondition);
				}
			}
		}
	}

	void wrapSwitchHandle(STMRoot stm, StateCollection states, EventCollection events) {
		for (StateNode stateNode : stm.getStages()) {
			State fromState = states.getState(stateNode.name);
			for (TransitionNode transitionNode : stateNode.getTransitionsForIterator()) {
				if (returnStates.contains(transitionNode.getNextStage())) {
					if (!stateNode.name.equals(transitionNode.getNextStage())) {
						if (!(transitionNode instanceof CallNode) && !(transitionNode instanceof ReturnNode)) {
							String msg = transitionNode.fileName + " - To State dont have any runStage step or returns entry state=" + stateNode.name + ",toState=" + transitionNode.getNextStage()
									+ "," + transitionNode.getStep();

							logger.info(msg);
							throw new STMException(msg);
						}
					}
				}
				if (transitionNode.getOutcomesForIteration().size() > 0) {
					Event fromEvent = events.getEvent(transitionNode.step);
					if (transitionNode instanceof CallNode) {
						fromEvent = events.getEvent(transitionNode.step + RESULT);
					}
					Transition currentTransition = fromState.getTransition(fromEvent);
					if (currentTransition instanceof PlaceHolderTransition) {
						String msg = transitionNode.fileName + " - Transiton dont have handler, outcomes not allowed for place holder transition " + currentTransition;
						logger.info(msg);
						throw new STMException(msg);
					}
					if (currentTransition instanceof ConditionOnly) {
						String msg = transitionNode.fileName + " - Transiton contains have condition with out any handler, outcomes not allowed  transition " + currentTransition;
						logger.info(msg);
						throw new STMException(msg);
					}
					wrapSwitchHandlerIfAny(stm.fileName, stateNode.name, states, events, transitionNode, currentTransition);
				}
			}
		}
	}

	void wrapSwitchHandlerIfAny(String fileName, String stateName, StateCollection states, EventCollection events, TransitionNode transitionNode, Transition normalTransition) {
		HashMap<String, Transition> transitions = new HashMap<String, Transition>();
		for (CaseNode casen : transitionNode.getOutcomesForIteration()) {
			validateCaseWhenAndFire(fileName, casen);
			if (casen.nextStage == null) {
				String msg = fileName + " " + casen.when
						+ String.format(" - Outcome missing from for transition %s  -- %s", normalTransition.getFromState().getName(), normalTransition.getFromEvent().getName());
				if (debug) {
					logger.info(msg);
				}
				casen.nextStage = stateName;
			} else if ("this".equalsIgnoreCase(casen.nextStage)) {
				String msg = fileName + " - outcome nextStage is this, so assuming the current stage as " + stateName;
				if (debug) {
					logger.info(msg);
				}
				casen.nextStage = stateName;
			}
			State toState = states.getState(casen.nextStage);
			Event toEvent = events.getEvent(casen.then);
			if (toEvent == null || toState == null) {
				String msg = fileName + " - Missing outcome when " + casen.when + " nextStage " + toState + " then " + toEvent;
				logger.info(msg);
				msg = fileName + " - Missing outcome when " + casen.when + " nextStage " + casen.nextStage + " then " + casen.then;
				logger.info(msg);
				throw new STMException(msg);
			}
			Transition targetTransition = toState.getTransition(toEvent);
			if (targetTransition == null) {
				String msg = fileName + " - Missing transition for outcome when " + casen.when + " nextStage " + casen.nextStage + " then " + casen.then;
				logger.info(msg);
				throw new STMException(msg);
			}
			transitions.put(casen.when, targetTransition);
		}
		replaceActorForChoice(transitionNode, normalTransition, transitions);
		validateDefaultForCallNode(fileName, transitionNode, transitions);
	}

	private void validateCaseWhenAndFire(String fileName, CaseNode casen) {
		if (casen.when == null || casen.then == null) {
			String msg = fileName + " - Missing outcome when " + casen.when + " nextStage " + casen.nextStage + " then " + casen.then;
			logger.info(msg);
			throw new STMException(msg);
		}
	}

	private void validateDefaultForCallNode(String fileName, TransitionNode transitionNode, HashMap<String, Transition> transitions) {
		if (transitionNode instanceof CallNode) {
			if (!transitions.containsKey("default")) {
				String msg = fileName + " - Missing otherwise, which is mandatory for a runStage step";
				logger.info(msg);
				throw new STMException(msg);
			}
		}
	}

	private void replaceActorForChoice(TransitionNode transitionNode, Transition normalTransition, HashMap<String, Transition> transitions) {
		if (transitionNode.getOutcomesForIteration().size() > 0) {
			if (debug) {
				logger.info(String.format("switch transition %s ------>  %s(%s)", normalTransition.getFromState().getName(), normalTransition.getFromEvent().getName(),
						normalTransition.getTransitionHandle()));
			}
			for (Entry<String, Transition> t : transitions.entrySet()) {
				logger.info(String.format("     %s ------>  %s(%s)", t.getKey(), t.getValue().getFromState().getName(), normalTransition.getFromEvent().getName(),
						normalTransition.getTransitionHandle()));
			}
			Actor<STMContext> h = new Choice<STMContext>(normalTransition.getTransitionHandle(), transitions);
			normalTransition.setActor(h);
		}
	}

	private Object getHandleForPrint(Transition transition) {
		Object object = transition.getTransitionHandle();
		if (object == null) {
			if (transition instanceof ConditionOnly) {
				return "(C) " + ((ConditionOnly) transition).getCondition();
			}
			if (transition instanceof PlaceHolderTransition) {
				return "(PLACEHOLDER) " + transition.getNextSTMEvent();
			}
		} else {
			if (transition instanceof ConditionAndActor) {
				return "(C&H) (C) " + ((ConditionAndActor) transition).getCondition() + " (H) " + object;
			}
			return "(H) " + object;
		}
		return object;
	}

	private Transition cloneTransition(Transition existingTransition, State fromState, Event fromEvent, State toState, Event toEvent) {
		Transition newTransition;
		if (existingTransition instanceof PlaceHolderTransition) {
			newTransition = new PlaceHolderTransition(fromState, fromEvent, toState, toEvent, existingTransition.getNextSTMEvent());
		} else if (existingTransition instanceof ConditionOnly) {
			newTransition = new ConditionOnly(fromState, fromEvent, toState, toEvent, existingTransition.getNextSTMEvent(), existingTransition.getOnErrorTransition(),
					((ConditionOnly) existingTransition).getCondition(), ((ConditionOnly) existingTransition).getOnFailedConditionTransition());
		} else if (existingTransition instanceof ConditionAndActor) {
			newTransition = new ConditionAndActor(fromState, fromEvent, toState, toEvent, existingTransition.getNextSTMEvent(), existingTransition.getOnErrorTransition(),
					((ConditionAndActor) existingTransition).getCondition(), ((ConditionAndActor) existingTransition).getOnFailedConditionTransition());
		} else if (existingTransition instanceof StmEventBasedTransition) {
			newTransition = new StmEventBasedTransition(fromState, fromEvent, toState, toEvent, existingTransition.getNextSTMEvent(), existingTransition.getOnErrorTransition());
		} else {
			newTransition = new Transition(fromState, fromEvent, toState, toEvent, existingTransition.getNextSTMEvent(), existingTransition.getOnErrorTransition());
		}
		newTransition.setOnResultTransition(existingTransition.getOnResultTransition());
		newTransition.setActor(existingTransition.getTransitionHandle());
		return newTransition;
	}

	Transition getErrorFailedConditionTransition(StateCollection states, EventCollection events, TransitionNode transitionNode, String fileName, String state, String msg,
			String to, String fire) {

		validateToStateAndToEvent(transitionNode, fileName, state, msg, to, fire);
		State eState = states.getState(to);
		if (eState == null) {
			String logmsg = fileName + " - state not exist for stage " + to + " on <" + msg + ">  "
					+ Transition.formatTranstion(state, transitionNode.step, transitionNode.nextStage, transitionNode.then, STMEvent.STOP);
			logger.info(logmsg);
			throw new STMException(logmsg);
		}
		Transition errorTransition = eState.getTransition(events.getEvent(fire));
		if (errorTransition == null) {
			String logmsg = fileName + " - transition not exist for then " + fire + " on <" + msg + ">  "
					+ Transition.formatTranstion(state, transitionNode.step, transitionNode.nextStage, transitionNode.then, STMEvent.STOP);
			logger.info(logmsg);
			throw new STMException(logmsg);
		}
		return errorTransition;
	}

	private void validateToStateAndToEvent(TransitionNode transitionNode, String fileName, String state, String msg, String to, String fire) {
		// onError, onFailedCondition should have to and fire should be defined
		// and the transiton and to state should exist
		if (to == null) {
			String logmsg = fileName + " - Missing nextStage on " + msg + " "
					+ Transition.formatTranstion(state, transitionNode.step, transitionNode.nextStage, transitionNode.then, STMEvent.STOP);
			logger.info(logmsg);
			throw new STMException(logmsg);
		}
		if (fire == null) {
			String logmsg = fileName + " - missing then on " + msg + " "
					+ Transition.formatTranstion(state, transitionNode.step, transitionNode.nextStage, transitionNode.then, STMEvent.STOP);
			logger.info(logmsg);
			throw new STMException(logmsg);
		}
	}

	@SuppressWarnings("unchecked")
	void createTransition(StateCollection states, EventCollection events, State fromState, TransitionNode transitionNode, Event fromEvent, State toState, Event toEvent) {
		Transition transition = fromState.getTransition(fromEvent);
		if (transition != null) {
			return;
		}
		Actor<T> condition = null;

		boolean defaultEvent = false;
		if (transitionNode.control == null) {
			transitionNode.control = DEFAULT_STM_EVENT;
			defaultEvent = true;
		}
		STMEvent next = STMEvent.valueOf(transitionNode.control.toUpperCase());
		if (transitionNode.check != null) {
			condition = createActor(transitionNode.check, fromState.getName(), fromEvent.getName(), transitionNode, true);
			if (transitionNode.action == null) {
				transition = new ConditionOnly(fromState, fromEvent, toState, toEvent, next, null, (Actor<STMContext>) condition, null);
				fromState.addTransition(transition);
			}
		}
		if (transitionNode instanceof CallNode) {
			transition = new Transition(fromState, fromEvent, toState, toEvent, next, null);
			transition.setActor(callState);
			callTransition(transition, states, events, fromState, transitionNode, fromEvent, toState, toEvent);
			callStates.add(toState.getName());
		} else if (transitionNode instanceof ReturnNode) {
			transition = new Transition(fromState, fromEvent, toState, toEvent, next, null);
			transition.setActor(returnState);
			fromState.addTransition(transition);
			returnStates.add(fromState.getName());
		} else if (transitionNode.action != null) {
			if (transitionNode.check != null) {
				transition = new ConditionAndActor(fromState, fromEvent, toState, toEvent, next, null, (Actor<STMContext>) condition, null);
			} else {
				if (defaultEvent) {
					transition = new Transition(fromState, fromEvent, toState, toEvent, next, null);
				} else {
					transition = new StmEventBasedTransition(fromState, fromEvent, toState, toEvent, next, null);
				}
			}
			addActor(states, events, transitionNode, transition);
			fromState.addTransition(transition);
		} else if (transitionNode.check == null) {
			transition = new PlaceHolderTransition(fromState, fromEvent, toState, toEvent, next);
			fromState.addTransition(transition);
		}
	}

	public void callTransition(Transition transition, StateCollection states, EventCollection events, State fromState, TransitionNode transitionNode, Event fromEvent,
			State toState, Event toEvent) {
		Event newEvent = createEvent(events, transitionNode.step + RESULT);
		Transition newResultTransition = cloneTransition(transition, fromState, newEvent, toState, toEvent);
		transition.setOnResultTransition(newResultTransition);
		newResultTransition.setActor(transition.getActor());
		newResultTransition.setActor(resultState);
		fromState.addTransition(transition);
		fromState.addTransition(newResultTransition);
	}

	@SuppressWarnings({ "unchecked" })
	void addActor(StateCollection states, EventCollection events, TransitionNode transitionNode, Transition transition) {
		Actor<T> actor = createActor(transitionNode.action, transition.getFromState().getName(), transition.getFromEvent().getName(), transitionNode, false);
		transition.setActor((Actor<STMContext>) actor);
	}

	@SuppressWarnings({ "unchecked" })
	public Actor<T> createActor(ActorNode actorNode, String stateName, String eventName, TransitionNode transitionNode, boolean condition) {
		Actor<T> actor = null;
		if (actorNode.className == null && actorNode.beanName == null && actorNode.beanType == null) {
			String msg = "Handler missing either class for beanName " + actorNode.className + " " + actorNode.beanType + " " + actorNode.beanName
					+ " so assuming the event name as bean Name " + eventName;
			if (debug) {
				logger.info(msg);
			}
			actorNode.beanName = eventName;
		}
		if (validateOnly) {
			if (condition) {
				return (Actor<T>) pass;
			}
			return (Actor<T>) noop;
		}
		actor = createActorDirectOrWrapper(actorNode, transitionNode, condition);
		if (actor == null) {
			throw new STMException("State name Actor " + stateName + ",eventName=" + eventName + " handler is null for class/beanName/beanType/method "
					+ actorNode.className + "/" + actorNode.beanName + "/" + actorNode.beanType + "/" + actorNode.method);

		}
		return actor;
	}

	@SuppressWarnings({ "unchecked" })
	private Actor<T> createActorDirectOrWrapper(ActorNode actorNode, TransitionNode transitionNode, boolean condition) {
		Actor<T> actor = null;
		if (actorNode.className != null) {
			try {
				Class<?> clazz = Class.forName(actorNode.className);
				actor = (Actor<T>) clazz.getDeclaredConstructor().newInstance();
				actor = createActorForActorBean(actor, actorNode, transitionNode, condition);
			} catch (Exception e) {
				logger.error(e.getMessage(), e);
				throw new STMException("create handler instance failed for Actor " + actorNode.className + ": " + e.getMessage(), e);
			}
		} else if (actorNode.beanType != null) {
			if (actorNode.getParametesForIteration().size() > 0) {
				actor = (Actor<T>) applicationContext.getBean(actorNode.formattedBeanName);
			} else {
				actor = (Actor<T>) getBean(applicationContext, actorNode.beanType);
			}
			actor = createActorForActorBean(actor, actorNode, transitionNode, condition);
		} else if (actorNode.beanName != null) {
			actor = (Actor<T>) applicationContext.getBean(actorNode.beanName);
			actor = createActorForActorBean(actor, actorNode, transitionNode, condition);
		}
		return actor;
	}

	private Actor<T> createActorForActorBean(Actor<T> beanActor, ActorNode actorNode, TransitionNode transitionNode, boolean condition) {
		Actor<T> actor = beanActor;
		String methodName = "handle";
		MethodReturnType methodReturnType = new MethodReturnType();
		if (actorNode.method != null) {
			actor = (Actor<T>) createTransitionLambda(beanActor, actorNode.method, actorNode, condition, methodReturnType);
			methodName = actorNode.method;
		}
		if (methodReturnType.methodReturnType != null && condition && (methodReturnType.methodReturnType != Boolean.class && methodReturnType.methodReturnType != boolean.class)) {
			logger.info("Method {} return type is not boolean beanName={}", methodName, actorNode.beanName);
			actor = null;
		}
		return actor;
	}

	public static boolean isMethodHaveBooleanReturnType(Class<?> declaringClass, String methodName, List<Class<?>> argTypes) {
		Method method = getMatchingMethod(declaringClass, methodName, argTypes, Lists.newArrayList(Boolean.class, boolean.class));
		if (method == null) {
			logger.info("Method handle return type is not boolean");
			return false;
		}
		return true;
	}

	@SuppressWarnings("unchecked")
	private Actor<T> createTransitionLambda(Actor<T> bean, String methodName, ActorNode actorNode, boolean condition, MethodReturnType methodReturnType) {
		if (actorNode.lookupMethod == null && actorNode.inputMap != null) {
			requireInputParameter(bean, methodName, Map.class, "a map");
		} else if (actorNode.lookupMethod == null && actorNode.input != null) {
			requireInputParameter(bean, methodName, String.class, "a text");
		}
		try {
			if (actorNode.lookupMethod == null) {
				if (actorNode.inputMap != null) {
					return (Actor<T>) new MethodWithMapArg(bean,
							getLambdaCallSite(bean, methodHandleLookup, methodName, condition, Lists.newArrayList(STMContext.class, Map.class), methodReturnType), methodName,
							actorNode.inputMap);
				}
				if (actorNode.input != null) {
					return (Actor<T>) new MethodWithStringArg(bean,
							getLambdaCallSite(bean, methodHandleLookup, methodName, condition, Lists.newArrayList(STMContext.class, String.class), methodReturnType), methodName,
							actorNode.input);
				}
				return (Actor<T>) new MethodActor(bean, getLambdaCallSite(bean, methodHandleLookup, methodName, condition, Lists.newArrayList(STMContext.class), methodReturnType),
						methodName);
			} else {
				Actor<? extends STMContext> lookupBean = bean;
				String lookupMethodName = actorNode.lookupMethod;
				if (actorNode.lookupBeanType != null) {
					lookupBean = (Actor<? extends STMContext>) getBean(applicationContext, actorNode.lookupBeanType);
				} else if (actorNode.lookupMethod.contains(".")) {
					lookupBean = getBeanForLookup(actorNode.lookupMethod);
					lookupMethodName = StringUtils.substringAfterLast(actorNode.lookupMethod, ".");
				}
				MethodReturnType lookupReturnTypes = new MethodReturnType();
				CallSite lookupCallSite = getLambdaCallSite(lookupBean, methodHandleLookup, lookupMethodName, false, Lists.newArrayList(STMContext.class), lookupReturnTypes);

				return (Actor<T>) new MethodWithLookupArg(bean, getLambdaCallSite(bean, methodHandleLookup, methodName, condition,
						Lists.newArrayList(STMContext.class, lookupReturnTypes.methodReturnType), methodReturnType), lookupBean, lookupCallSite, methodName, lookupMethodName);

			}
		} catch (Throwable e) {
			logger.info(e.getMessage(), e);
		}
		return null;
	}

	/** {@code input} needs {@code method(context, T)} where T accepts the input's type; fail the build otherwise. */
	private static void requireInputParameter(Object bean, String methodName, Class<?> inputType, String what) {
		for (Method method : bean.getClass().getMethods()) {
			if (method.getName().equals(methodName) && method.getParameterCount() == 2 && method.getParameterTypes()[1].isAssignableFrom(inputType)) {
				return;
			}
		}
		throw new STMException("input is " + what + ", but " + bean.getClass().getName() + " has no public method " + methodName + "(context, "
				+ inputType.getSimpleName() + (inputType == Map.class ? "<String, String>" : "") + ")");
	}

		public static class MethodReturnType {
		Class<?> methodReturnType;
	}

	@SuppressWarnings("unchecked")
	private Actor<T> getBeanForLookup(String lookupMethod) {
		String lookupBeanName = StringUtils.substringBeforeLast(lookupMethod, ".");
		return (Actor<T>) getBean(applicationContext, lookupBeanName);
	}

	public static Object getBean(ApplicationContext applicationContext, String beanTypeOrName) {
		try {
			Class<?> clazz = Class.forName(beanTypeOrName);
			return applicationContext.getBean(clazz);
		} catch (Exception exception) {
			return applicationContext.getBean(beanTypeOrName);
		}
	}

	public static CallSite getLambdaCallSiteWithArg(Object object, Lookup lookup, String methodName, Object lookupBean, String lookupMethodName, boolean condition,
			List<Class<?>> methodReturnType) {
		try {
			Class<?> declaringClass = object.getClass();
			Class<?> lookupClass = lookupBean.getClass();
			Method reflected = getMatchingMethod(declaringClass, methodName,
					Lists.newArrayList(STMContext.class, getMatchingMethod(lookupClass, lookupMethodName, Lists.newArrayList(STMContext.class), null).getReturnType()),
					condition ? Lists.newArrayList(Boolean.class, boolean.class) : null);
			if (methodReturnType != null) {
				methodReturnType.add(reflected.getReturnType());
			}
			MethodHandle methodHandle = lookup.unreflect(reflected);
			MethodType invokedType = MethodType.methodType(TriFunction.class);
			CallSite site = LambdaMetafactory.metafactory(lookup, "apply", invokedType, methodHandle.type().generic(), methodHandle, methodHandle.type());
			logger.info("Object method name is used instead of object={},methodname={}", object, methodName);
			return site;
		} catch (Exception e) {
			logger.info(e.getMessage(), e);
			throw new STMException(e);
		}
	}

	public static CallSite getLambdaCallSite(Object object, Lookup lookup, String methodName, boolean condition, List<Class<?>> parameterTypes, MethodReturnType methodReturnType) {
		try {
			Class<?> declaringClass = object.getClass();
			Method reflected = getMatchingMethod(declaringClass, methodName, parameterTypes, condition ? Lists.newArrayList(Boolean.class, boolean.class) : null);
			MethodHandle methodHandle = lookup.unreflect(reflected);
			if (methodReturnType != null) {
				methodReturnType.methodReturnType = reflected.getReturnType();
			}
			MethodType invokedType = null;
			if (parameterTypes.size() == 1) {
				invokedType = MethodType.methodType(BiFunction.class);
			} else {
				invokedType = MethodType.methodType(TriFunction.class);
			}
			CallSite site = LambdaMetafactory.metafactory(lookup, "apply", invokedType, methodHandle.type().generic(), methodHandle, methodHandle.type());
			logger.info("Object method name is used instead of object={},methodname={}", object, methodName);
			return site;
		} catch (Exception e) {
			logger.info(e.getMessage(), e);
			throw new STMException(e);
		}
	}

	private static Method getMatchingMethod(Class<?> cls, String methodName, List<Class<?>> parameterTypes, List<Class<?>> matchingReturnTypes) {
		Validate.notNull(cls, "Null class not allowed");
		Validate.notEmpty(methodName, "Null or blank methodname not allowed");
		Method[] methodArray = cls.getDeclaredMethods();
		List<Method> methods = new ArrayList<Method>();
		for (Method method : methodArray) {
			if (method.getName().equals(methodName)) {
				methods.add(method);
			}
		}
		getMethodByAllSuper(cls, methodName, methods);
		Method inexactMatch = null;
		for (final Method method : methods) {
			if (matchingReturnTypes != null && !matchingReturnTypes.contains(method.getReturnType())) {
				continue;
			}
			if (matchingParameterAndSuperType(method, parameterTypes)) {
				inexactMatch = method;
				break;
			}
		}
		inexactMatch = getMethodByParameterCountAndFirstParam(parameterTypes, matchingReturnTypes, methods, inexactMatch);
		return inexactMatch;
	}

	private static boolean matchingParameterAndSuperType(Method method, List<Class<?>> parameterTypes) {
		int matchedCount = 0;
		int i = 0;
		for (Class<?> parameterType : method.getParameterTypes()) {
			if (i >= parameterTypes.size()) {
				break;
			}
			Class<?> matchingParameterType = parameterTypes.get(i);
			i++;
			if (parameterType == matchingParameterType) {
				matchedCount++;
				continue;
			}
			List<Class<?>> superClasses = ClassUtils.getAllSuperclasses(parameterType);
			superClasses.remove(superClasses.size() - 1);
			superClasses.addAll(ClassUtils.getAllInterfaces(parameterType));
			if (superClasses.contains(matchingParameterType)) {
				matchedCount++;
				continue;
			}
			break;
		}
		if (matchedCount == parameterTypes.size()) {
			return true;
		}
		return false;
	}

	private static void getMethodByAllSuper(Class<?> cls, String methodName, List<Method> methods) {
		List<Class<?>> superClasses = ClassUtils.getAllSuperclasses(cls);
		superClasses.remove(superClasses.size() - 1);
		superClasses.addAll(ClassUtils.getAllInterfaces(cls));
		for (final Class<?> klass : superClasses) {
			for (Method method : klass.getDeclaredMethods()) {
				if (method.getName().equals(methodName)) {
					methods.add(method);
				}
			}
		}
	}

	private static Method getMethodByParameterCountAndFirstParam(List<Class<?>> parameterTypes, List<Class<?>> matchingReturnTypes, List<Method> methods, Method inexactMatch) {
		if (inexactMatch == null && parameterTypes.size() > 0) {
			for (final Method method : methods) {
				if (matchingReturnTypes != null && !matchingReturnTypes.contains(method.getReturnType())) {
					continue;
				}
				if (parameterTypes.size() == method.getParameterCount()) {
					if (parameterTypes.get(0).isAssignableFrom(method.getParameterTypes()[0])) {
						inexactMatch = method;
						break;
					}
				}
			}
		}
		return inexactMatch;
	}

	public State createState(StateCollection states, String state) {
		State s = states.getState(state);
		if (s == null) {
			s = new State(stateIndex++, state);
			states.addState(s);
		}
		return s;
	}

	public Event createEvent(EventCollection events, String event) {
		Event e = events.getEvent(event);
		if (e == null) {
			e = new Event(eventIndex++, event);
			events.addEvent(e);
		}
		return e;
	}

	public boolean isDebug() {
		return debug;
	}

	public void setDebug(boolean debug) {
		this.debug = debug;
	}

	@SuppressWarnings("unchecked")
	public Class<T> getContextType() {
		return (Class<T>) contextType;
	}

	public void setContextType(Class<? extends STMContext> contextType) {
		this.contextType = contextType;
	}

	public Map<String, BeanRegistrationInfo> getBeanRegistrationMap() {
		return beanRegistrationMap;
	}

	public String getFsmName() {
		return fsmName;
	}
	
	public interface STMPostProcessor {
		public <T extends STMContext> void postProcess(StateCollection states, STMStateful<T> f, STMOptimizedBuilder<T> context);
	}
}

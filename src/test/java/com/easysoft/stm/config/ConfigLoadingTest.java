package com.easysoft.stm.config;

import static com.easysoft.stm.config.Stm.actor;
import static com.easysoft.stm.config.Stm.run;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.easysoft.stm.STMContext;
import com.easysoft.stm.STMException;
import com.easysoft.stm.STMStateful;
import com.easysoft.stm.config.ConfigReader.CallNode;
import com.easysoft.stm.config.ConfigReader.CaseNode;
import com.easysoft.stm.config.ConfigReader.STMRoot;
import com.easysoft.stm.config.ConfigReader.StateNode;
import com.easysoft.stm.config.ConfigReader.TransitionNode;

/** Loading configuration from files, the classpath, YAML, and split across several files. */
class ConfigLoadingTest {

	private static Path write(String name, String content) throws IOException {
		Path dir = Files.createTempDirectory("stm-test");
		Path file = dir.resolve(name);
		Files.write(file, content.getBytes(StandardCharsets.UTF_8));
		file.toFile().deleteOnExit();
		dir.toFile().deleteOnExit();
		return file;
	}

	@Test
	void readerMapsEveryKey() {
		STMRoot root = new ConfigYAMLReader().parse("{ name: 'n', startStage: s, startStep: f, defaults: { onError: { nextStage: e, then: ef } }, stages: [ "
				+ "{ name: s, steps: [ "
				+ "{ step: f, nextStage: t, then: g, control: stop, action: { class: C, method: m, properties: [ { name: 0, value: ctorArg } ] }, outcomes: [ { when: x, then: 'y' } ] }, "
				+ "{ step: c, runStage: sub, then: go } ], returns: [ "
				+ "{ step: r } ] } ] }");

		assertEquals("n", root.getName());
		assertEquals("s", root.getStartStage());
		assertEquals("e", root.getOnError().getNextStage());
		StateNode state = root.getStages().get(0);
		TransitionNode t = state.getSteps().get(0);
		assertEquals("f", t.getStep());
		assertEquals("stop", t.getControl());
		assertEquals("m", t.getAction().getMethod());
		assertEquals("ctorArg", t.getAction().getParametesForIteration().get("0"));
		assertEquals("x", t.getOutcomesForIteration().get(0).getWhen());
		assertEquals(1, state.getCalls().size());
		assertEquals(1, state.getReturns().size());
		assertEquals(3, state.getTransitionsForIterator().size());
	}

	@Test
	void readerAcceptsBlockStyle() {
		STMRoot root = new ConfigYAMLReader().parse("name: n\n"
				+ "stages:\n"
				+ "  - name: s\n"
				+ "    steps:\n"
				+ "      - step: go\n"
				+ "        control: stop\n"
				+ "        action: { class: C, method: m }\n");
		assertEquals("n", root.getName());
		assertEquals("go", root.getStages().get(0).getSteps().get(0).getStep());
		assertEquals("C", root.getStages().get(0).getSteps().get(0).getAction().getClassName());
	}

	@Test
	void invalidYamlIsReported() {
		assertThrows(STMException.class, () -> new ConfigYAMLReader().parse("name: [unclosed"));
	}

	@Test
	void loadsXmlFromTheFileSystem() throws Exception {
		Path file = write("flow.yaml", Stm.machine("{ name: s, steps: [ "
				+ "{ step: go, control: stop, action: " + actor("a") + " } ] }"));
		STMOptimizedBuilder<STMContext> b = new STMOptimizedBuilder<>(null, false);
		b.addFile(file.toString());
		assertEquals(List.of("a"), run(b.build()).log);
	}

	@Test
	void yamlAndYmlFilesLoad() throws Exception {
		String yaml = "name: yaml.flow\n"
				+ "defaults:\n"
				+ "  onError: { nextStage: s, then: done }\n"
				+ "stages:\n"
				+ "  - name: s\n"
				+ "    steps:\n"
				+ "      - step: go\n"
				+ "        then: done\n"
				+ "        action: { class: " + Stm.A + ", method: a }\n"
				+ "      - step: done\n"
				+ "        control: stop\n";
		for (String name : new String[] { "flow.yml", "flow.yaml" }) {
			STMOptimizedBuilder<STMContext> b = new STMOptimizedBuilder<>(null, false);
			b.addFile(write(name, yaml).toString());
			STMStateful<STMContext> stm = b.build();
			assertEquals("yaml.flow", stm.getName());
			assertEquals(List.of("a"), run(stm).log);
		}
	}

	@Test
	void readsClasspathResources() throws Exception {
		String content = STMOptimizedBuilder.readContent("classpath:stm/order-flow.yaml");
		assertTrue(content.contains("name: order.flow"));
	}

	@Test
	void missingFileFails() {
		assertThrows(IOException.class, () -> STMOptimizedBuilder.readContent("/definitely/not/here.yaml"));
		assertThrows(IOException.class, () -> STMOptimizedBuilder.readContent("classpath:not/here.yaml"));
	}

	@Test
	void includeLoadsOtherFiles() throws Exception {
		Path part = write("part.yaml", "{ name: part, stages: [ "
				+ "{ name: second, steps: [ "
				+ "{ step: go2, then: end, action: " + actor("b") + " }, "
				+ "{ step: end, control: stop } ] } ] }");
		Path main = write("main.yaml", "{ name: multi, include: [ '" + part + "' ], defaults: { onError: { nextStage: first, then: done } }, stages: [ "
				+ "{ name: first, steps: [ "
				+ "{ step: go, nextStage: second, then: go2, action: " + actor("a") + " }, "
				+ "{ step: done, control: stop } ] } ] }");

		STMOptimizedBuilder<STMContext> b = new STMOptimizedBuilder<>(null, false);
		b.addFile(main.toString());
		STMStateful<STMContext> stm = b.build();

		assertEquals("multi", stm.getName(), "the first file names the machine");
		assertNotNull(stm.getState("second"));
		assertEquals(List.of("a", "b"), run(stm).log);
		assertEquals(2, b.stmRoots.size());
	}

	@Test
	void theSameFileIsOnlyLoadedOnce() throws Exception {
		Path file = write("once.yaml", Stm.machine("{ name: s, steps: [ "
				+ "{ step: go, control: stop, action: " + actor("a") + " } ] }"));
		STMOptimizedBuilder<STMContext> b = new STMOptimizedBuilder<>(null, false);
		b.addFile(file.toString());
		b.addFile(file.toString());
		assertEquals(1, b.stmRoots.size());
		b.build();
	}

	@Test
	void stateDefinedInTwoFilesNamesBothFiles() throws Exception {
		Path one = write("one.yaml", Stm.machine("{ name: dup, steps: [ "
				+ "{ step: go, control: stop, action: " + actor("a") + " } ] }"));
		Path two = write("two.yaml", "{ name: two, stages: [ "
				+ "{ name: dup, steps: [ "
				+ "{ step: other, control: stop, action: " + actor("a") + " } ] } ] }");
		STMOptimizedBuilder<STMContext> b = new STMOptimizedBuilder<>(null, false);
		b.addFile(one.toString());
		b.addFile(two.toString());
		STMException e = assertThrows(STMException.class, b::build);
		assertTrue(e.getMessage().contains(one.toString()) && e.getMessage().contains(two.toString()), e.getMessage());
	}

	@Test
	void transitionNodeToStringIsReadable() {
		TransitionNode t = new TransitionNode();
		t.setStep("w");
		t.setNextStage("t");
		t.setThen("f");
		assertEquals("{ step: w, nextStage: t, then: f, control: null }", t.toString());
		assertNull(t.getFileName());
	}

	@Test
	void onlyYamlFilesAreAccepted() throws Exception {
		Path xml = write("flow.xml", "<STM name='x'/>");
		STMException e = assertThrows(STMException.class, () -> new STMOptimizedBuilder<STMContext>(null, false).addFile(xml.toString()));
		assertTrue(e.getMessage().contains("only YAML flow files (.yaml or .yml) are supported"), e.getMessage());
	}

	/** The old key names are rejected outright; each is named in the error. */
	@Test
	void oldKeyNamesAreRejected() {
		String rule = "{ step: go, then: done, action: { class: C, method: m } }";
		String[][] cases = {
				{ "start", "name: n\nstart: s\nstages: [ { name: s, steps: [ " + rule + " ] } ]" },
				{ "states", "name: n\nstates: [ { name: s, steps: [ " + rule + " ] } ]" },
				{ "transitions", "name: n\nstages: [ { name: s, transitions: [ " + rule + " ] } ]" },
				{ "actor", "name: n\nstages: [ { name: s, steps: [ { step: go, actor: { class: C } } ] } ]" },
				{ "condition", "name: n\nstages: [ { name: s, steps: [ { step: go, condition: { class: C } } ] } ]" },
				{ "onFailedCondition", "name: n\nonFailedCondition: { nextStage: s, then: f }" },
				{ "onFailedCondition", "name: n\nstages: [ { name: s, steps: [ { step: go, onFailedCondition: { nextStage: s, then: f } } ] } ]" },
				{ "to", "name: n\nstages: [ { name: s, steps: [ { step: go, to: s, then: f } ] } ]" },
				{ "to", "name: n\ndefaults: { onError: { to: s, then: f } }" },
				{ "to", "name: n\nstages: [ { name: s, steps: [ { step: go, outcomes: [ { when: x, to: s } ] } ] } ]" },
				{ "fire", "name: n\nfire: go" },
				{ "fire", "name: n\nstages: [ { name: s, steps: [ { step: go, fire: x } ] } ]" },
				{ "fire", "name: n\ndefaults: { onError: { nextStage: s, fire: f } }" },
				{ "fire", "name: n\nstages: [ { name: s, steps: [ { step: go, outcomes: [ { when: x, fire: y } ] } ] } ]" },
				{ "stmevent", "name: n\nstages: [ { name: s, steps: [ { step: go, stmevent: stop } ] } ]" },
				{ "cases", "name: n\nstages: [ { name: s, steps: [ { step: go, cases: [ { when: x, then: y } ] } ] } ]" },
				{ "when", "name: n\nstages: [ { name: s, steps: [ { when: go, then: x } ] } ]" },
				{ "when", "name: n\nstages: [ { name: s, returns: [ { when: r } ] } ]" },
				{ "className", "name: n\nstages: [ { name: s, steps: [ { step: go, action: { className: C } } ] } ]" },
				{ "methodName", "name: n\nstages: [ { name: s, steps: [ { step: go, check: { class: C, methodName: m } } ] } ]" },
				{ "ifNot", "name: n\nstages: [ { name: s, steps: [ { step: go, ifNot: { nextStage: s, then: f } } ] } ]" },
				{ "ifNot", "name: n\ndefaults: { ifNot: { nextStage: s, then: f } }" },
				{ "callStage", "name: n\nstages: [ { name: s, steps: [ { step: go, callStage: sub } ] } ]" },
				{ "rules", "name: n\nstages: [ { name: s, rules: [ { step: go, then: x } ] } ]" },
				{ "then", "name: n\nstartStage: s\nthen: go\nstages: [ { name: s, steps: [ { step: go, control: stop } ] } ]" },
				{ "stmFiles", "name: n\nstmFiles: other.yaml" },
				{ "argParameter", "name: n\nstages: [ { name: s, steps: [ { step: go, action: { class: C, method: m, argParameter: x } } ] } ]" },
				{ "fileName", "name: n\nfileName: x" },
				// outcomes keep 'when'
				{ "step", "name: n\nstages: [ { name: s, steps: [ { step: go, outcomes: [ { step: x, then: y } ] } ] } ]" },
		};
		for (String[] c : cases) {
			STMException e = assertThrows(STMException.class, () -> new ConfigYAMLReader().parse(c[1]), c[1]);
			assertTrue(e.getMessage().matches("(?s).*(Unrecognized|Ignored) field \"" + c[0] + "\".*"), e.getMessage());
		}
	}

	@Test
	void movedKeysAreRejectedWithAHint() {
		String[][] cases = {
				{ "name: n\nonError: { nextStage: s, then: e }", "a top-level onError moved under defaults" },
				{ "name: n\nonFalse: { nextStage: s, then: e }", "a top-level onFalse moved under defaults" },
				{ "name: n\nstages: [ { name: s, steps: [ { step: go, outcomes: [ { when: a, then: b }, { when: default, then: c } ] } ] } ]",
						"step 'go': write the catch-all outcome as otherwise: { nextStage, then } instead of when: default" },
		};
		for (String[] c : cases) {
			STMException e = assertThrows(STMException.class, () -> new ConfigYAMLReader().parse(c[0]), c[0]);
			assertTrue(e.getMessage().contains(c[1]), e.getMessage());
		}
	}

	@Test
	void otherwiseIsTheCatchAllOutcome() {
		STMRoot root = new ConfigYAMLReader().parse("name: n\nstages: [ { name: s, steps: [ "
				+ "{ step: go, outcomes: [ { when: A, then: a } ], otherwise: { nextStage: t, then: b } }, "
				+ "{ step: only, otherwise: { then: c } } ] } ]");
		List<CaseNode> outcomes = root.getStages().get(0).getSteps().get(0).getOutcomesForIteration();
		assertEquals(List.of("A", "default"), outcomes.stream().map(CaseNode::getWhen).toList());
		assertEquals("t", outcomes.get(1).getNextStage());
		assertEquals("b", outcomes.get(1).getThen());
		List<CaseNode> only = root.getStages().get(0).getSteps().get(1).getOutcomesForIteration();
		assertEquals(1, only.size(), "otherwise alone, without outcomes");
		assertEquals("c", only.get(0).getThen());
		assertThrows(STMException.class, () -> new ConfigYAMLReader().parse("name: n\nstages: [ { name: s, steps: [ { step: go, otherwise: { when: x, then: c } } ] } ]"),
				"otherwise takes only nextStage and then");
	}

	@Test
	void includeTakesAListOrCommaSeparatedText() {
		assertEquals("a.yaml,b.yaml", new ConfigYAMLReader().<STMRoot>parse("name: n\ninclude: [ a.yaml, b.yaml ]").getInclude());
		assertEquals("a.yaml, b.yaml", new ConfigYAMLReader().<STMRoot>parse("name: n\ninclude: 'a.yaml, b.yaml'").getInclude());
		assertEquals("a.yaml", new ConfigYAMLReader().<STMRoot>parse("name: n\ninclude:\n  - a.yaml\n").getInclude());
	}

	@Test
	void callsKeyIsRejectedWithAHint() {
		STMException e = assertThrows(STMException.class,
				() -> new ConfigYAMLReader().parse("name: n\nstages: [ { name: s, calls: [ { step: go, nextStage: sub, then: x } ] } ]"));
		assertTrue(e.getMessage().contains("'calls' was merged into 'steps': write each call as a step with runStage: <stage>"), e.getMessage());
	}

	@Test
	void aRuleWithRunStageIsACall() {
		STMRoot root = new ConfigYAMLReader().parse("name: n\nstages: [ { name: s, steps: [ "
				+ "{ step: a, then: b }, "
				+ "{ step: b, runStage: sub, then: sub.go, action: { class: A }, otherwise: { then: c } }, "
				+ "{ step: c, control: stop } ] } ]");
		StateNode stage = root.getStages().get(0);
		assertEquals(List.of("a", "c"), stage.getSteps().stream().map(TransitionNode::getStep).toList(), "calls are kept apart from ordinary rules");
		assertEquals(1, stage.getCalls().size());
		CallNode call = stage.getCalls().get(0);
		assertEquals("b", call.getStep());
		assertEquals("sub", call.getNextStage(), "runStage is the stage the call runs");
		assertEquals("sub.go", call.getThen());
		assertEquals("A", call.getAction().getClassName());
		assertEquals("c", call.getOutcomesForIteration().get(0).getThen());
		assertEquals("{ step: b, runStage: sub, then: sub.go, control: null }", call.toString());
	}

	@Test
	void runStageMisuseIsRejected() {
		STMException e = assertThrows(STMException.class,
				() -> new ConfigYAMLReader().parse("name: n\nstages: [ { name: s, steps: [ { step: go, runStage: a, nextStage: b } ] } ]"));
		assertTrue(e.getMessage().contains("step 'go' has both runStage and nextStage"), e.getMessage());
		e = assertThrows(STMException.class, () -> new ConfigYAMLReader().parse("name: n\nstages: [ { name: s, returns: [ { step: r, runStage: a } ] } ]"));
		assertTrue(e.getMessage().contains("returns entry 'r' cannot have runStage"), e.getMessage());
	}

	@Test
	void everyNewKeyIsRead() {
		STMRoot root = new ConfigYAMLReader().parse("name: n\nstartStage: s\nstartStep: go\n"
				+ "defaults: { onError: { nextStage: e, then: ef }, onFalse: { nextStage: r, then: rf } }\n"
				+ "stages: [ { name: s, steps: [ { step: go, nextStage: t, then: f, action: { class: A }, check: { class: C }, "
				+ "onFalse: { nextStage: x, then: xf }, outcomes: [ { when: k, nextStage: y, then: yf } ] } ] } ]");
		assertEquals("s", root.getStartStage());
		assertEquals("e", root.getOnError().getNextStage());
		assertEquals("r", root.getOnFalse().getNextStage());
		TransitionNode t = root.getStages().get(0).getSteps().get(0);
		assertEquals("t", t.getNextStage());
		assertEquals("A", t.getAction().getClassName());
		assertEquals("C", t.getCheck().getClassName());
		assertEquals("x", t.getOnFalse().getNextStage());
		assertEquals("y", t.getOutcomesForIteration().get(0).getNextStage());
		assertEquals("f", t.getThen(), "then");
		assertEquals("go", root.getStartStep(), "top-level then");
		assertEquals("ef", root.getOnError().getThen());
		assertEquals("yf", t.getOutcomesForIteration().get(0).getThen());
	}
}

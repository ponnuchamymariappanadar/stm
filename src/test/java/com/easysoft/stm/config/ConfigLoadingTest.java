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
	void xmlReaderMapsElementsAndAttributes() {
		STMRoot root = new ConfigXMLReader().parse("<STM name='n' start='s' fire='f'>"
				+ "<OnError to='e' fire='ef'/>"
				+ "<State name='s'>"
				+ "  <Transition when='f' to='t' fire='g' stmevent='stop'>"
				+ "    <Actor className='C' methodName='m'><Property name='0' value='ctorArg'/></Actor>"
				+ "    <Case when='x' fire='y'/>"
				+ "  </Transition>"
				+ "  <Call when='c' to='sub' fire='go'/>"
				+ "  <Return when='r'/>"
				+ "</State></STM>");

		assertEquals("n", root.getName());
		assertEquals("s", root.getStart());
		assertEquals("e", root.getOnError().getTo());
		StateNode state = root.getStates().get(0);
		TransitionNode t = state.getTransitions().get(0);
		assertEquals("f", t.getWhen());
		assertEquals("stop", t.getStmevent());
		assertEquals("m", t.getActor().getMethodName());
		assertEquals("ctorArg", t.getActor().getParametesForIteration().get("0"));
		assertEquals("x", t.getCasesForIteration().get(0).getWhen());
		assertEquals(1, state.getCalls().size());
		assertEquals(1, state.getReturns().size());
		assertEquals(3, state.getTransitionsForIterator().size());
	}

	@Test
	void yamlReaderUsesJavaPropertyNames() {
		STMRoot root = new ConfigYAMLReader().parse("name: n\n"
				+ "states:\n"
				+ "  - name: s\n"
				+ "    transitions:\n"
				+ "      - when: go\n"
				+ "        stmevent: stop\n"
				+ "        actor: { className: C, methodName: m }\n");
		assertEquals("n", root.getName());
		assertEquals("go", root.getStates().get(0).getTransitions().get(0).getWhen());
		assertEquals("C", root.getStates().get(0).getTransitions().get(0).getActor().getClassName());
	}

	@Test
	void invalidYamlIsReported() {
		assertThrows(STMException.class, () -> new ConfigYAMLReader().parse("name: [unclosed"));
	}

	@Test
	void loadsXmlFromTheFileSystem() throws Exception {
		Path file = write("flow.xml", Stm.machine("<State name='s'><Transition when='go' stmevent='stop'>" + actor("a") + "</Transition></State>"));
		STMOptimizedBuilder<STMContext> b = new STMOptimizedBuilder<>(null, false);
		b.addFile(file.toString());
		assertEquals(List.of("a"), run(b.build()).log);
	}

	@Test
	void fileExtensionSelectsTheYamlReader() throws Exception {
		String yaml = "name: yaml.flow\n"
				+ "onError: { to: s, fire: done }\n"
				+ "states:\n"
				+ "  - name: s\n"
				+ "    transitions:\n"
				+ "      - when: go\n"
				+ "        fire: done\n"
				+ "        actor: { className: " + Stm.A + ", methodName: a }\n"
				+ "      - when: done\n"
				+ "        stmevent: stop\n";
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
		String content = STMOptimizedBuilder.readContent("classpath:stm/order-flow.xml");
		assertTrue(content.contains("<STM name=\"order.flow\""));
	}

	@Test
	void missingFileFails() {
		assertThrows(IOException.class, () -> STMOptimizedBuilder.readContent("/definitely/not/here.xml"));
		assertThrows(IOException.class, () -> STMOptimizedBuilder.readContent("classpath:not/here.xml"));
	}

	@Test
	void stmFilesAttributeIncludesOtherFiles() throws Exception {
		Path part = write("part.xml", "<STM name='part'>"
				+ "<State name='second'><Transition when='go2' fire='end'>" + actor("b") + "</Transition>"
				+ "<Transition when='end' stmevent='stop'/></State></STM>");
		Path main = write("main.xml", "<STM name='multi' stmFiles='" + part + "'>"
				+ "<OnError to='first' fire='done'/>"
				+ "<State name='first'><Transition when='go' to='second' fire='go2'>" + actor("a") + "</Transition>"
				+ "<Transition when='done' stmevent='stop'/></State></STM>");

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
		Path file = write("once.xml", Stm.machine("<State name='s'><Transition when='go' stmevent='stop'>" + actor("a") + "</Transition></State>"));
		STMOptimizedBuilder<STMContext> b = new STMOptimizedBuilder<>(null, false);
		b.addFile(file.toString());
		b.addFile(file.toString());
		assertEquals(1, b.stmRoots.size());
		b.build();
	}

	@Test
	void stateDefinedInTwoFilesNamesBothFiles() throws Exception {
		Path one = write("one.xml", Stm.machine("<State name='dup'><Transition when='go' stmevent='stop'>" + actor("a") + "</Transition></State>"));
		Path two = write("two.xml", "<STM name='two'><State name='dup'><Transition when='other' stmevent='stop'>" + actor("a") + "</Transition></State></STM>");
		STMOptimizedBuilder<STMContext> b = new STMOptimizedBuilder<>(null, false);
		b.addFile(one.toString());
		b.addFile(two.toString());
		STMException e = assertThrows(STMException.class, b::build);
		assertTrue(e.getMessage().contains(one.toString()) && e.getMessage().contains(two.toString()), e.getMessage());
	}

	@Test
	void transitionNodeToStringIsReadable() {
		TransitionNode t = new TransitionNode();
		t.setWhen("w");
		t.setTo("t");
		t.setFire("f");
		assertTrue(t.toString().contains("when='w' to='t' fire='f'"), t.toString());
		assertNull(t.getFileName());
	}
}

package com.easysoft.stm.config;

import static com.easysoft.stm.config.Stm.actor;
import static com.easysoft.stm.config.Stm.run;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.Test;

import com.easysoft.stm.STMContext;
import com.easysoft.stm.STMStateful;
import com.easysoft.stm.condition.FailCondition;
import com.easysoft.stm.condition.PassCondition;

/** Regression tests for builder/configuration bugs fixed after the first test pass (see CHANGELOG.md). */
class ConfigRegressionTest {

	@Test
	void builtInConditionsRouteAsTheirNamesSay() {
		String xml = Stm.machine("<State name='s'>"
				+ "<Transition when='start' fire='next'><Condition className='" + PassCondition.class.getName() + "'/></Transition>"
				+ "<Transition when='next' fire='end'><Condition className='" + FailCondition.class.getName() + "'/>"
				+ "  <OnFailedCondition to='this' fire='failed'/></Transition>"
				+ "<Transition when='failed' fire='end'>" + actor("localRejected") + "</Transition>"
				+ "<Transition when='end' stmevent='stop'/></State>");
		assertEquals(List.of("localRejected"), run(Stm.build(xml)).log);
	}

	@Test
	void buildersCanRunInParallelAndGetUniqueIds() throws Exception {
		String xml = Stm.machine("<State name='s'><Transition when='go' stmevent='stop'>" + actor("a") + "</Transition></State>");
		ExecutorService pool = Executors.newFixedThreadPool(8);
		try {
			List<Callable<STMStateful<STMContext>>> jobs = new ArrayList<>();
			for (int i = 0; i < 32; i++) {
				jobs.add(() -> Stm.build(xml));
			}
			Set<Integer> ids = new HashSet<>();
			for (Future<STMStateful<STMContext>> f : pool.invokeAll(jobs)) {
				STMStateful<STMContext> stm = f.get();
				ids.add(stm.getId());
				assertEquals(List.of("a"), run(stm).log);
			}
			assertEquals(32, ids.size(), "every machine gets its own id");
		} finally {
			pool.shutdown();
		}
	}

	@Test
	void globalHandlersCanLiveInAnIncludeOnlyMainFile() throws Exception {
		Path dir = Files.createTempDirectory("stm-known");
		Path part = dir.resolve("part.xml");
		Files.write(part, ("<STM name='part'><State name='s'>"
				+ "<Transition when='go' fire='end'>" + actor("a") + "</Transition>"
				+ "<Transition when='end' stmevent='stop'/></State></STM>").getBytes(StandardCharsets.UTF_8));
		Path main = dir.resolve("main.xml");
		Files.write(main, ("<STM name='main' stmFiles='" + part + "'><OnError to='s' fire='end'/></STM>").getBytes(StandardCharsets.UTF_8));

		STMOptimizedBuilder<STMContext> b = new STMOptimizedBuilder<>(null, false);
		b.addFile(main.toString());
		assertEquals(List.of("a"), run(b.build()).log);
	}
}

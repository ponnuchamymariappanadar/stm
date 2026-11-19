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
		String yaml = Stm.machine("{ name: s, steps: [ "
				+ "{ step: start, then: next, check: { class: '" + PassCondition.class.getName() + "' } }, "
				+ "{ step: next, then: end, check: { class: '" + FailCondition.class.getName() + "' }, onFalse: { nextStage: this, then: failed } }, "
				+ "{ step: failed, then: end, action: " + actor("localRejected") + " }, "
				+ "{ step: end, control: stop } ] }");
		assertEquals(List.of("localRejected"), run(Stm.build(yaml)).log);
	}

	@Test
	void buildersCanRunInParallelAndGetUniqueIds() throws Exception {
		String yaml = Stm.machine("{ name: s, steps: [ "
				+ "{ step: go, control: stop, action: " + actor("a") + " } ] }");
		ExecutorService pool = Executors.newFixedThreadPool(8);
		try {
			List<Callable<STMStateful<STMContext>>> jobs = new ArrayList<>();
			for (int i = 0; i < 32; i++) {
				jobs.add(() -> Stm.build(yaml));
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
		Path part = dir.resolve("part.yaml");
		Files.write(part, ("{ name: part, stages: [ "
				+ "{ name: s, steps: [ "
				+ "{ step: go, then: end, action: " + actor("a") + " }, "
				+ "{ step: end, control: stop } ] } ] }").getBytes(StandardCharsets.UTF_8));
		Path main = dir.resolve("main.yaml");
		Files.write(main, ("{ name: main, include: '" + part + "', defaults: { onError: { nextStage: s, then: end } } }").getBytes(StandardCharsets.UTF_8));

		STMOptimizedBuilder<STMContext> b = new STMOptimizedBuilder<>(null, false);
		b.addFile(main.toString());
		assertEquals(List.of("a"), run(b.build()).log);
	}
}

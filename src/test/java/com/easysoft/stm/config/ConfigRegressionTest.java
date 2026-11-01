package com.easysoft.stm.config;

import static com.easysoft.stm.config.Stm.actor;
import static com.easysoft.stm.config.Stm.run;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.easysoft.stm.STMContext;

/** Regression tests for builder/configuration bugs fixed after the first test pass (see CHANGELOG.md). */
class ConfigRegressionTest {

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

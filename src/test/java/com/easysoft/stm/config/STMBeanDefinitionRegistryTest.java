package com.easysoft.stm.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;

import com.easysoft.stm.STMContext;
import com.easysoft.stm.STMStateful;
import com.easysoft.stm.TestContext;

/**
 * Spring integration: {@link STMBeanDefinitionRegistry} reads the files listed in
 * {@code stm.config.locations}, registers every {@code beanType} actor as a bean, and registers
 * an {@link STMFactory} bean per machine that builds and publishes it on startup.
 */
class STMBeanDefinitionRegistryTest {

	@Test
	void registersActorBeansAndPublishesTheMachine() {
		try (AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext()) {
			ctx.getEnvironment().getPropertySources().addFirst(
					new MapPropertySource("test", Map.of(STMBeanDefinitionRegistry.STM_CONFIG_LOCATIONS, "classpath:stm/bean-flow.xml")));
			ctx.registerBean(STMBeanDefinitionRegistry.class);
			ctx.refresh();

			assertNotNull(ctx.getBean(FlowActions.class), "beanType actors are registered as beans");
			assertInstanceOf(STMFactory.class, ctx.getBean("bean.flow"), "one STMFactory bean per machine, named after it");

			STMStateful<STMContext> stm = STMFactory.getFsmAllStateful().get("bean.flow");
			assertNotNull(stm);
			TestContext run = new TestContext(stm.createContext());
			run.getSTMContext().start(run);
			assertEquals(List.of("a", "b"), run.log);
		}
	}

	@Test
	void explicitConfigFileOverridesTheProperty() {
		try (AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext()) {
			ctx.registerBean(STMBeanDefinitionRegistry.class, () -> new STMBeanDefinitionRegistry("classpath:stm/order-flow.xml"));
			ctx.refresh();
			assertInstanceOf(STMFactory.class, ctx.getBean("order.flow"));
			assertNotNull(STMFactory.getFsmAllStateful().get("order.flow"));
		}
	}
}

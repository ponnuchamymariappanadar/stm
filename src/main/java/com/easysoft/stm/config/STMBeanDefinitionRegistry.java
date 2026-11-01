package com.easysoft.stm.config;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.apache.commons.lang3.ClassUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.MutablePropertyValues;
import org.springframework.beans.factory.BeanCreationException;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.config.ConstructorArgumentValues;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.EmbeddedValueResolverAware;
import org.springframework.core.ResolvableType;
import org.springframework.core.env.Environment;
import org.springframework.util.StringValueResolver;

import com.easysoft.stm.STMContext;
import com.easysoft.stm.config.ConfigReader.BeanRegistrationInfo;

/**
 * Spring integration. Declare it as a {@code static @Bean}; at startup it reads the files listed in
 * {@code stm.config.locations} (or passed to the constructor), registers every {@code beanType}
 * actor as a Spring bean, and registers one {@link STMFactory} bean per machine.
 */
public class STMBeanDefinitionRegistry implements BeanDefinitionRegistryPostProcessor, EmbeddedValueResolverAware, ApplicationContextAware {
	private static final int MAX_CONSTUCTOR_ARGS_ALLOWED = 5;

	public static final String STM_CONFIG_LOCATIONS = "stm.config.locations";

	private static final Logger logger = LoggerFactory.getLogger(STMBeanDefinitionRegistry.class);;

	private ApplicationContext applicationContext;
	private Environment configurableEnvironment;

	private StringValueResolver resolver;
	private String stmConfigFile;

	public STMBeanDefinitionRegistry() {

	}

	public STMBeanDefinitionRegistry(String stmConfigFile) {
		this.stmConfigFile = stmConfigFile;
	}

	public Map<String, STMOptimizedBuilder<STMContext>> load(Map<String, BeanRegistrationInfo> beans) throws Exception {
		this.configurableEnvironment = applicationContext.getEnvironment();
		if (stmConfigFile == null) {
			stmConfigFile = configurableEnvironment.getProperty(STM_CONFIG_LOCATIONS);
		}
		Map<String, STMOptimizedBuilder<STMContext>> map = new HashMap<>();
		for (String file : stmConfigFile.split(",")) {
			STMOptimizedBuilder<STMContext> builder = new STMOptimizedBuilder<STMContext>(applicationContext, false, resolver);
			builder.addFile(file.trim());
			map.put(builder.getFsmName(), builder);
			beans.putAll(builder.getBeanRegistrationMap());
		}
		return map;
	}

	@Override
	public void postProcessBeanDefinitionRegistry(BeanDefinitionRegistry registry) throws BeansException {
		try {
			Map<String, BeanRegistrationInfo> beans = new HashMap<>();
			Map<String, STMOptimizedBuilder<STMContext>> fsmBuilders = load(beans);
			for (Entry<String, BeanRegistrationInfo> mapConfigurationEntry : beans.entrySet()) {
				registerSTMBean(registry, mapConfigurationEntry.getKey(), mapConfigurationEntry.getValue());
			}
			fsmBuilders.forEach((k, v) -> registerSTMFactoryBean(registry, k, v));
			
		} catch (Exception e) {
			String message = e.getMessage() + "creating custom bean definition failed";
			logger.info(message, e);
			throw new BeanCreationException(message, e);
		}
	}

	protected void registerSTMBean(BeanDefinitionRegistry registry, String beanName, BeanRegistrationInfo registrationInfo) throws Exception {
		Class<?> beanClass = ClassUtils.getClass(registrationInfo.getBeanType());
		RootBeanDefinition beanDefinition = new RootBeanDefinition(beanClass);
		beanDefinition.setBeanClassName(registrationInfo.getBeanType());
		beanDefinition.setTargetType(ResolvableType.forClass(beanClass));
		beanDefinition.setPrimary(false);
		if ("prototype".equals(registrationInfo.getScope())) {
			beanDefinition.setScope(ConfigurableBeanFactory.SCOPE_PROTOTYPE);
		} else {
			beanDefinition.setScope(ConfigurableBeanFactory.SCOPE_SINGLETON);
		}
		registerSTMBean(beanDefinition, registrationInfo);
		logger.info("STM Bean Registered Factory beanName=" + beanClass.getSimpleName() + ",types=" + beanDefinition.getTargetType() + ",beanName=" + beanName + ",parameters="
				+ registrationInfo.getParameters() + ",registrationPoint=" + registrationInfo.getDefinitionPoints());
		registry.registerBeanDefinition(beanName, beanDefinition);
	}

	protected void registerSTMFactoryBean(BeanDefinitionRegistry registry, String beanName,
			STMOptimizedBuilder<STMContext> stmBuilder) {
		RootBeanDefinition beanDefinition = new RootBeanDefinition(STMFactory.class);
		beanDefinition.setBeanClassName(STMFactory.class.getCanonicalName());
		beanDefinition.setTargetType(ResolvableType.forClass(STMFactory.class));
		beanDefinition.setPrimary(false);
		beanDefinition.setScope(ConfigurableBeanFactory.SCOPE_SINGLETON);
		MutablePropertyValues propertyValues = new MutablePropertyValues();
		propertyValues.add("stmBuilder", stmBuilder);
		propertyValues.add("fsmName", beanName);
		beanDefinition.setPropertyValues(propertyValues);
		logger.info("STM Bean Registered beanName=" + beanName + ",types=" + beanDefinition.getTargetType());
		registry.registerBeanDefinition(beanName, beanDefinition);
	}

	protected void registerSTMBean(RootBeanDefinition beanDefinition, BeanRegistrationInfo registrationInfo) throws Exception {
		if (registrationInfo.getParameters().isEmpty()) {
			return;
		}
		Set<String> keys = new HashSet<String>(registrationInfo.getParameters().keySet());
		ConstructorArgumentValues constructorArgumentValues = new ConstructorArgumentValues();
		for (int i = 0; i < MAX_CONSTUCTOR_ARGS_ALLOWED; i++) {
			String key = "" + i;
			if (registrationInfo.getParameters().containsKey(key)) {
				constructorArgumentValues.addIndexedArgumentValue(i, registrationInfo.getParameters().get(key));
				keys.remove(key);
			}
		}
		if (!constructorArgumentValues.isEmpty()) {
			beanDefinition.setConstructorArgumentValues(constructorArgumentValues);
		}
		MutablePropertyValues propertyValues = new MutablePropertyValues();
		registrationInfo.getParameters().forEach((k, v) -> {
			if (keys.contains(k)) {
				propertyValues.add(k, v);
			}
		});
		if (!propertyValues.isEmpty()) {
			beanDefinition.setPropertyValues(propertyValues);
		}
	}

	@Override
	public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {

	}

	@Override
	public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
		this.applicationContext = applicationContext;
	}

	@Override
	public void setEmbeddedValueResolver(StringValueResolver resolver) {
		this.resolver = resolver;
	}
}

package com.easysoft.stm.config;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

import com.easysoft.stm.STMContext;
import com.easysoft.stm.STMStateful;

import jakarta.annotation.PostConstruct;

/**
 * Builds a machine on startup ({@code @PostConstruct}) and publishes it by name. Look machines up
 * with {@link #getFsmAllStateful()} or subscribe with {@link #addConsumer}.
 */
public class STMFactory  {
	private static Map<String, Set<Consumer<STMStateful<STMContext>>>> consumer = new 
			ConcurrentHashMap<>();
	private static Map<String, STMStateful<STMContext>> fsmAllStateful = new 
			ConcurrentHashMap<>();
	
	STMOptimizedBuilder<STMContext> stmBuilder;
	private STMStateful<STMContext> fsmStateful;
	private String fsmName;
	
	@PostConstruct
	public void init() {
		fsmStateful = stmBuilder.build();
		notifyAllListener(fsmName, fsmStateful);
	}

	private static synchronized void notifyAllListener(String fsmName, STMStateful<STMContext> fsmStateful) {
		fsmAllStateful.put(fsmName, fsmStateful);
		if (consumer.containsKey(fsmName)) {
			consumer.get(fsmName).forEach(listener -> {
				listener.accept(fsmStateful);
			});
		}
	}

	public STMOptimizedBuilder<STMContext> getStmBuilder() {
		return stmBuilder;
	}

	public void setStmBuilder(STMOptimizedBuilder<STMContext> stmBuilder) {
		this.stmBuilder = stmBuilder;
	}
	
	public static synchronized void addConsumer(String creatorBeanName, Consumer< STMStateful<STMContext>> listener)  {
		Set<Consumer<STMStateful<STMContext>>> set = consumer.computeIfAbsent(creatorBeanName, k -> new HashSet<Consumer<STMStateful<STMContext>>>());
		set.add(listener);
		if (fsmAllStateful.containsKey(creatorBeanName)) {
			listener.accept(fsmAllStateful.get(creatorBeanName));
		}
	}

	public String getFsmName() {
		return fsmName;
	}

	public void setFsmName(String fsmName) {
		this.fsmName = fsmName;
	}

	public static Map<String, STMStateful<STMContext>> getFsmAllStateful() {
		return fsmAllStateful;
	}
}

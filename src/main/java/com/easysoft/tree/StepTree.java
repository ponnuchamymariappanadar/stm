package com.easysoft.tree;

public interface StepTree<K, V> {
    void put(K key, V value);

    boolean putIfAbsent(K key, V value);

    V get(K key);

    V getRootIfAbsent(K key);

    int size();
}

package com.easysoft.tree;

public interface Node<K, V> {
    void setKey(K key);
    void setValue(V value);
    K getKey();
    V getValue();
}

package com.easysoft.tree;

public class BaseNode<K, V> implements Node<K, V> {
    protected K key;
    protected V value;

    public void setKey(K key) {
        this.key = key;
    }

    @Override
    public void setValue(V value) {
        this.value = value;
    }

    @Override
    public K getKey() {
        return key;
    }

    @Override
    public V getValue() {
        return value;
    }
}

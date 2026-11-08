package com.easysoft.tree;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

public class Tree<V> implements StepTree<String, V> {
    private static final Logger LOG = LogManager.getLogger();
    protected static final String STEP_DELIMITER = "/";
    protected static final String WILD_CARD = "*";
    protected TreeNode<V> root;
    protected int size = 0;

    public Tree() {
        root = createNode(null);
        size = 1;
    }

    protected String[] getSplittedStep(String step) {
        if (step == null) {
            step = STEP_DELIMITER;
        }
        if (step.endsWith(STEP_DELIMITER)) {
            StringBuilder stepBuilder = new StringBuilder(step);
            stepBuilder.append(" ");
            step = stepBuilder.toString();
        }
        return StringUtils.split(step, STEP_DELIMITER);
    }

    @Override
    public V get(String step) {
        TreeNode<V> node = root.find(getSplittedStep(step));
        if (node == null) {
            return null;
        }
        V value = node.getValue();
        return value;
    }

    @Override
    public V getRootIfAbsent(String step) {
        TreeNode<V> value = root.find(getSplittedStep(step));
        if (value == null) {
            return root.getValue();
        }
        return value.getValue();
    }

    @Override
    public int size() {
        return size;
    }

    protected boolean isMatched(String step) {
        return getMatchedNode(step) != null;
    }

    @Override
    public boolean putIfAbsent(String step, V value) {
        if (step == null) {
            return false;
        }
        TreeNode<V> matchedNode = getMatchedNode(step);
        if (matchedNode != null) {
            return false;
        }
        put(step, value);
        return true;
    }

    @Override
    public void put(String step, V value) {
        if (step == null) {
            return;
        }
        TreeNode<V> matchedNode = getMatchedNode(step);
        if (matchedNode != null) {
            LOG.info("The new code is overriding the existing " + step + " " + matchedNode.getValue() + " " + value);
        }
        String[] components = getSplittedStepForPut(step);
        Iterator<String> iterator = Arrays.asList(components).iterator();
        List<TreeNode<V>> nodeList = new ArrayList<TreeNode<V>>(components.length);
        root.add(this, iterator, nodeList);
        size += nodeList.size();
        TreeNode<V> node = root.findLeaf(Arrays.asList(components).iterator());
        if (node == null) {
            LOG.error("Could not get leaf node which was added " + step);
            return;
        }
        node.setValue(value);
    }

    protected String[] getSplittedStepForPut(String step) {
        return StringUtils.split(step, STEP_DELIMITER);
    }

    protected TreeNode<V> getMatchedNode(String key) {
        String[] components = getSplittedStepForPut(key);
        TreeNode<V> node = root.findLeaf(Arrays.asList(components).iterator());
        return node;
    }

    public void setRoot(TreeNode<V> node) {
        this.root = node;
    }

    protected TreeNode<V> createNode(TreeNode<V> parentNode) {
        TreeNode<V> node = new TreeNode<V>();
        node.parent = parentNode;
        return node;
    }

    protected TreeNode<V> getRegularNode(TreeNode<V> node, String stepComponent) {
        return node.regularChildren.get(stepComponent);
    }

    protected TreeNode<V> getPatternedNode(TreeNode<V> node, String stepComponent) {
        return node.patternedChildren.get(stepComponent);
    }

    public TreeNode<V> getParent(TreeNode<V> node) {
        return node.parent;
    }
}

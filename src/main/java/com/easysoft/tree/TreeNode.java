package com.easysoft.tree;

import org.apache.commons.lang3.StringUtils;

import java.util.*;
import java.util.regex.Pattern;

public class TreeNode<V> extends BaseNode<String, V> {
    protected Pattern patternedKey;
    protected TreeNode<V> parent;

    public static Comparator<String> stepComponentComparator = new Comparator<String>() {
        @Override
        public int compare(String stepComponent1, String stepComponent2) {
            if (Tree.WILD_CARD.equals(stepComponent1) && !Tree.WILD_CARD.equals(stepComponent2)) {
                return 1;
            }
            if (!Tree.WILD_CARD.equals(stepComponent1) && Tree.WILD_CARD.equals(stepComponent2)) {
                return -1;
            }
            if (Tree.WILD_CARD.contains(stepComponent1) && !Tree.WILD_CARD.contains(stepComponent2)) {
                return 1;
            }
            if (!Tree.WILD_CARD.contains(stepComponent1) && Tree.WILD_CARD.contains(stepComponent2)) {
                return -1;
            }
            return stepComponent1.compareTo(stepComponent2);
        }
    };

    protected Map<String, TreeNode<V>> regularChildren = new HashMap<String, TreeNode<V>>();
    protected Map<String, TreeNode<V>> patternedChildren = new TreeMap<String, TreeNode<V>>(stepComponentComparator);


    public void add(Tree<V> tree, Iterator<String> components, List<TreeNode<V>> list) {
        if (!components.hasNext()) {
            return;
        }
        String stepComponent = components.next();
        if (isPatternedChild(stepComponent)) {
            addPatternedChild(tree, components, list, stepComponent);
        } else {
            addRegularChild(tree, components, list, stepComponent);
        }
    }

    protected boolean isPatternedChild(String stepComponent) {
        return stepComponent.contains(Tree.WILD_CARD);
    }

    protected TreeNode<V> addPatternedChild(Tree<V> tree, Iterator<String> components, List<TreeNode<V>> list, String stepComponent) {
        TreeNode<V> node = tree.getPatternedNode(this, stepComponent);
        if (node == null) {
            node = tree.createNode(this);
            String escapedStepComponent = stepComponent.replace(".", "\\.");
            escapedStepComponent = escapedStepComponent.replaceAll("\\*\\*", ".*");
            escapedStepComponent = escapedStepComponent.replaceAll("\\*", ".*");
            Pattern pattern = Pattern.compile(escapedStepComponent);
            node.patternedKey = pattern;
            node.key = stepComponent;
            patternedChildren.put(stepComponent, node);
            list.add(node);
        }
        node.add(tree, components, list);
        return node;
    }

    protected TreeNode<V> addRegularChild(Tree<V> tree, Iterator<String> components, List<TreeNode<V>> list, String stepComponent) {
        TreeNode<V> node = tree.getRegularNode(this, stepComponent);
        if (node == null) {
            node = tree.createNode(this);
            regularChildren.put(stepComponent, node);
            node.key = stepComponent;
            list.add(node);
        }
        node.add(tree, components, list);
        return node;
    }

    public TreeNode<V> find(String[] splittedStep) {
        TreeNode<V> node = find(splittedStep, 0);
        if (node != null && node.getValue() == null) {
            for (int i = splittedStep.length - 1; i > -1; i--) {
                TreeNode<V> backTrackedNode = node.findPatternedNode(StringUtils.join(splittedStep,
                        Tree.STEP_DELIMITER, i, splittedStep.length));
                if (backTrackedNode != null) {
                    if (backTrackedNode.getValue() != null) {
                        return backTrackedNode;
                    } else {
                        node = backTrackedNode.parent;
                    }
                } else {
                    node = node.parent;
                }
                if (node == null) {
                    return null;
                }
            }
        }
        return node;
    }

    protected TreeNode<V> find(String[] spittedStep, int i) {
        if (i == spittedStep.length) {
            return this;
        }
        String stepComponent = spittedStep[i];
        int nextIndex = i + 1;
        TreeNode<V> node = regularChildren.get(stepComponent);
        if (node != null && nextIndex < spittedStep.length) {
            if (nextIndex == (spittedStep.length - 1) && spittedStep[nextIndex].equals(" ")) {
            } else {
                node = node.find(spittedStep, nextIndex);
            }
        }
        if (node == null) {
            node = findPatternedNode(stepComponent);
            if (node != null && nextIndex < spittedStep.length) {
                TreeNode<V> patternedNode = node.find(spittedStep, nextIndex);
                if (patternedNode == node && node.key.contains(Tree.WILD_CARD)) {
                    node = findPatternedNode(StringUtils.join(spittedStep, Tree.STEP_DELIMITER, i, spittedStep.length));
                } else {
                    node = patternedNode;
                }
            }
        }
        if (node == null) {
            return this;
        }
        return node;
    }

    protected TreeNode<V> findPatternedNode(String stepComponent) {
        TreeNode<V> node = null;
        if (patternedChildren.size() > 0) {
            for (TreeNode<V> patternedNode : patternedChildren.values()) {
                if (patternedNode.key.equals(stepComponent)) {
                    node = patternedNode;
                    break;
                }
                if (patternedNode.patternedKey.matcher(stepComponent).matches()) {
                    node = patternedNode;
                    break;
                }
            }
        }
        return node;
    }

    protected TreeNode<V> findLeaf(Iterator<String> iterator) {
        TreeNode<V> node = this;
        while (iterator.hasNext()) {
            String stepComponent = iterator.next();
            if (isPatternedChild(stepComponent)) {
                node = node.patternedChildren.get(stepComponent);
            } else {
                node = node.regularChildren.get(stepComponent);
            }
            if (node == null) {
                return null;
            }
        }
        return node;
    }
}

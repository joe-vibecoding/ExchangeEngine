package com.nanosecond.core;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

public class RedBlackTreeFuzzTest {

    private static final long SEED = 123456789L;
    private static final int ITERATIONS = 1_000_000;
    private static final int MAX_PRICE = 1_000_000;

    // We use a shadow map to verify correctness of presence
    private final TreeMap<Long, PriceLevel> shadowMap = new TreeMap<>();
    private final RedBlackTree tree = new RedBlackTree();

    @Test
    public void fuzzTest() {
        Random random = new Random(SEED);
        List<PriceLevel> activeNodes = new ArrayList<>();

        System.out.println("Starting RedBlackTree Fuzz Test (" + ITERATIONS + " ops)...");

        for (int i = 0; i < ITERATIONS; i++) {
            boolean insert = random.nextBoolean();

            // Bias towards insert if empty
            if (activeNodes.isEmpty())
                insert = true;
            // Bias towards delete if large to keep tree churning
            if (activeNodes.size() > 1000)
                insert = random.nextBoolean() && random.nextBoolean(); // 25% insert

            if (insert) {
                long price = random.nextInt(MAX_PRICE) + 1;
                if (!shadowMap.containsKey(price)) {
                    PriceLevel node = new PriceLevel();
                    node.reset();
                    node.price = price;

                    tree.insert(node);
                    shadowMap.put(price, node);
                    activeNodes.add(node);
                }
            } else {
                // Delete random node
                if (!activeNodes.isEmpty()) {
                    int idx = random.nextInt(activeNodes.size());
                    PriceLevel node = activeNodes.get(idx);

                    // Swap with last for efficient ArrayList remove
                    PriceLevel last = activeNodes.get(activeNodes.size() - 1);
                    activeNodes.set(idx, last);
                    activeNodes.remove(activeNodes.size() - 1);

                    tree.remove(node);
                    shadowMap.remove(node.price);
                }
            }

            // Verify Min/Max logic periodically (and on small trees always)
            if (i % 100 == 0 || activeNodes.size() < 50) {
                verifyMinMax();
            }

            // Periodically valid full structure (expensive)
            if (i % 10000 == 0) {
                validateTree();
            }
        }

        System.out.println("Fuzz Test Completed Successfully.");
    }

    private void verifyMinMax() {
        if (shadowMap.isEmpty()) {
            assertNull(tree.getBestPrice(true), "Tree Min should be null");
            assertNull(tree.getBestPrice(false), "Tree Max should be null");
        } else {
            long expectedMin = shadowMap.firstKey();
            long expectedMax = shadowMap.lastKey();

            PriceLevel minNode = tree.getBestPrice(true);
            PriceLevel maxNode = tree.getBestPrice(false);

            assertNotNull(minNode, "Tree Min is null but map is not");
            assertNotNull(maxNode, "Tree Max is null but map is not");

            assertEquals(expectedMin, minNode.price, "Min Price Mismatch");
            assertEquals(expectedMax, maxNode.price, "Max Price Mismatch");
        }
    }

    private void validateTree() {
        PriceLevel root = tree.getRoot();
        if (root == null) {
            assertTrue(shadowMap.isEmpty(), "Tree is empty but shadow map is not");
            return;
        }

        // 1. Verify BST Property (In-Order Traversal)
        List<Long> treeKeys = new ArrayList<>();
        validateBST(root, Long.MIN_VALUE, Long.MAX_VALUE, treeKeys);

        // Compare with shadow
        assertEquals(new ArrayList<>(shadowMap.keySet()), treeKeys, "Tree structure mismatch with Shadow Map");

        // 2. Verify RBT Properties
        // Root is Black
        assertFalse(root.color, "Root must be BLACK");

        // No Double Red
        validateColors(root);

        // Black Height Consistency
        validateBlackHeight(root);

        // Parent Pointers
        validateParents(root, null);
    }

    private void validateBST(PriceLevel node, long min, long max, List<Long> keys) {
        if (node == null)
            return;

        assertTrue(node.price > min, "Node " + node.price + " <= min " + min);
        assertTrue(node.price < max, "Node " + node.price + " >= max " + max);

        validateBST(node.left, min, node.price, keys);
        keys.add(node.price);
        validateBST(node.right, node.price, max, keys);
    }

    private void validateColors(PriceLevel node) {
        if (node == null)
            return;

        if (node.color) { // RED
            if (node.left != null)
                assertFalse(node.left.color, "Red node has Red left child");
            if (node.right != null)
                assertFalse(node.right.color, "Red node has Red right child");
        }
        validateColors(node.left);
        validateColors(node.right);
    }

    private int validateBlackHeight(PriceLevel node) {
        if (node == null)
            return 1; // Null is black

        int leftH = validateBlackHeight(node.left);
        int rightH = validateBlackHeight(node.right);

        assertEquals(leftH, rightH, "Black height mismatch at node " + node.price);

        return leftH + (node.color ? 0 : 1);
    }

    private void validateParents(PriceLevel node, PriceLevel parent) {
        if (node == null)
            return;

        assertEquals(parent, node.parent, "Parent mismatch for node " + node.price);
        validateParents(node.left, node);
        validateParents(node.right, node);
    }
}

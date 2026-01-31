package com.nanosecond.core;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class RedBlackTreeTest {

    private RedBlackTree tree;

    @BeforeEach
    public void setup() {
        tree = new RedBlackTree();
    }

    private PriceLevel createNode(long price) {
        PriceLevel node = new PriceLevel();
        node.reset();
        node.price = price;
        return node;
    }

    @Test
    public void testRootInsertion() {
        PriceLevel node = createNode(100);
        tree.insert(node);

        assertEquals(node, tree.getRoot());
        assertFalse(node.color, "Root must always be BLACK"); // using boolean for now
        assertNull(node.parent);
    }

    @Test
    public void testSimpleRedInsertion() {
        // Root: 100 (Black)
        // Child: 50 (Red)
        PriceLevel root = createNode(100);
        tree.insert(root);

        PriceLevel child = createNode(50);
        tree.insert(child);

        assertEquals(root, tree.getRoot());
        assertEquals(child, root.left);
        assertTrue(child.color, "Child of black root should be RED");
    }

    @Test
    public void testRecoloringCase1() {
        // Case 1: Uncle is Red. Solution: Flip colors.
        // 10(B)
        // / \
        // 5(R) 15(R)
        // /
        // 1(R) -> Trigger Recolor

        tree.insert(createNode(10));
        tree.insert(createNode(5));
        tree.insert(createNode(15)); // Uncle is 15 (Red)

        PriceLevel newNode = createNode(1);
        tree.insert(newNode);

        // After fixup:
        // 10 should stay Black (Root)
        // 5 should become Black
        // 15 should become Black
        // 1 should be Red

        assertFalse(tree.getRoot().color, "Root 10 is Black");
        assertFalse(tree.getRoot().left.color, "Node 5 is Black");
        assertFalse(tree.getRoot().right.color, "Node 15 is Black");
        assertTrue(tree.getRoot().left.left.color, "Node 1 is Red");
    }

    @Test
    public void testRotationCaseRight() {
        // Case 3: Line left-heavy. Solution: Right Rotate.
        // 10(B)
        // /
        // 5(R)
        // /
        // 1(R) -> Trigger Rotation

        tree.insert(createNode(10));
        tree.insert(createNode(5));

        PriceLevel newNode = createNode(1);
        tree.insert(newNode);

        // Expected Tree:
        // 5(B)
        // / \
        // 1(R) 10(R)

        assertEquals(5, tree.getRoot().price);
        assertFalse(tree.getRoot().color, "New Root 5 is Black");

        assertEquals(1, tree.getRoot().left.price);
        assertTrue(tree.getRoot().left.color, "Node 1 is Red");

        assertEquals(10, tree.getRoot().right.price);
        assertTrue(tree.getRoot().right.color, "Node 10 is Red");
    }

    @Test
    public void testRotationCaseLeft() {
        // Case 3 (Mirror): Line right-heavy. Solution: Left Rotate.
        // 10(B)
        // \
        // 15(R)
        // \
        // 20(R) -> Trigger Rotation

        tree.insert(createNode(10));
        tree.insert(createNode(15));

        PriceLevel newNode = createNode(20);
        tree.insert(newNode);

        // Expected Tree:
        // 15(B)
        // / \
        // 10(R) 20(R)

        assertEquals(15, tree.getRoot().price);
        assertFalse(tree.getRoot().color, "New Root 15 is Black");

        assertEquals(10, tree.getRoot().left.price);
        assertTrue(tree.getRoot().left.color, "Node 10 is Red");

        assertEquals(20, tree.getRoot().right.price);
        assertTrue(tree.getRoot().right.color, "Node 20 is Red");
    }

    @Test
    public void testFindMinMax() {
        tree.insert(createNode(50));
        tree.insert(createNode(20));
        tree.insert(createNode(80));
        tree.insert(createNode(10));
        tree.insert(createNode(30));

        assertEquals(10, tree.getBestPrice(true).price, "Min should be 10");
        assertEquals(80, tree.getBestPrice(false).price, "Max should be 80");
    }
}

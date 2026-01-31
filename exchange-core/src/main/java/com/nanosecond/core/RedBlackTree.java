package com.nanosecond.core;

/**
 * <h1>Intrusive Red-Black Tree: A Story of Balance</h1>
 * 
 * <p>
 * Welcome to the heart of the Exchange's memory management. This is not just a
 * data structure; it is a carefully choreographed dance to keep our Order Book
 * balanced in <b>O(log N)</b> time, without ever creating garbage for the GC.
 * </p>
 *
 * <h2>The "Intrusive" Concept</h2>
 * <p>
 * In a standard Java {@code TreeMap}, your data sits inside a wrapper object
 * (a {@code Node}). That wrapper is "Garbage" waiting to happen.
 * <br>
 * Here, we are <b>Intrusive</b>. The {@link PriceLevel} object <i>is</i> the
 * node. It knows its own left, right, and parent.
 * <br>
 * <b>Result:</b> When an order arrives, we just link pointers. <b>Zero
 * Allocation.</b>
 * </p>
 *
 * <h2>The Rules of the Red-Black Game</h2>
 * <ol>
 * <li><b>Every node is either RED or BLACK.</b></li>
 * <li><b>The Root is always BLACK.</b> (It anchors the tree).</li>
 * <li><b>No two RED nodes can be neighbors.</b> (Red represents potential
 * imbalance).</li>
 * <li><b>Every path from Root to Leaf has the same number of BLACK nodes.</b>
 * (Perfect balance).</li>
 * </ol>
 */
public class RedBlackTree {

    // We use booleans for speed, but these constants make it readable.
    private static final boolean RED = true;
    private static final boolean BLACK = false;

    private PriceLevel root;

    /**
     * @return The absolute root of the tree (The anchor).
     */
    public PriceLevel getRoot() {
        return root;
    }

    /**
     * Finds a PriceLevel in the tree.
     * <p>
     * This is a standard Binary Search:
     * <ul>
     * <li>Smaller? Go Left.</li>
     * <li>Larger? Go Right.</li>
     * <li>Found? Return it.</li>
     * </ul>
     * </p>
     * 
     * @param price The price to search for.
     */
    public PriceLevel find(long price) {
        PriceLevel current = root;
        while (current != null) {
            if (price == current.price) {
                return current;
            } else if (price < current.price) {
                current = current.left;
            } else {
                current = current.right;
            }
        }
        return null;
    }

    /**
     * Finds the extreme values (Best Bid or Best Ask).
     * 
     * @param min If true, finds the Minimum (Best Ask). If false, finds Maximum
     *            (Best Bid).
     */
    public PriceLevel getBestPrice(boolean min) {
        PriceLevel current = root;
        if (current == null)
            return null;

        // Just keep going Left (for Min) or Right (for Max) until you hit the wall.
        while (true) {
            if (min) {
                if (current.left == null)
                    return current;
                current = current.left;
            } else {
                if (current.right == null)
                    return current;
                current = current.right;
            }
        }
    }

    /**
     * Inserts a new PriceLevel into the tree and fixes the balance.
     * 
     * @param node The pre-allocated, recycled PriceLevel object.
     */
    public void insert(PriceLevel node) {
        if (node == null)
            return;

        // 1. Reset pointers (Just in case it came from a pool)
        node.left = null;
        node.right = null;
        node.parent = null;
        node.color = RED; // Rule: New nodes are always RED (optimistic).

        // 2. Handle Empty Tree
        if (root == null) {
            root = node;
            root.color = BLACK; // Rule 2: Root must be BLACK.
            return;
        }

        // 3. Find the Spot (Standard BST Insert)
        PriceLevel current = root;
        PriceLevel parent = null;

        while (current != null) {
            parent = current;
            if (node.price < current.price) {
                current = current.left;
            } else if (node.price > current.price) {
                current = current.right;
            } else {
                return; // Duplicate price (Shouldn't happen in this logic)
            }
        }

        // 4. Link it up
        node.parent = parent;
        if (node.price < parent.price) {
            parent.left = node;
        } else {
            parent.right = node;
        }

        // 5. Use The Force (Rebalance the Color)
        rebalanceAfterInsertion(node);
    }

    /**
     * Removes a node. This is where the magic (and complexity) happens.
     */
    public void remove(PriceLevel node) {
        if (node == null || find(node.price) == null)
            return;
        deleteNode(node);
    }

    // =========================================================================
    // THE BALANCING ACT (Here be dragons... explained nicely)
    // =========================================================================

    /**
     * Restores order to the galaxy after a new RED node is inserted.
     * <p>
     * <b>The Problem:</b> We inserted a RED node. If its parent is RED, we have a
     * "Double Red" violation.
     * <br>
     * <b>The Solutions:</b>
     * <ul>
     * <li><b>Case 1 (The Rich Uncle):</b> If the uncle is RED, we just flip colors.
     * Easy.</li>
     * <li><b>Case 2 (The Line):</b> If we are a straight line of children, we
     * Rotate once.</li>
     * <li><b>Case 3 (The Triangle):</b> If we are a zig-zag, we Rotate twice.</li>
     * </ul>
     * </p>
     */
    private void rebalanceAfterInsertion(PriceLevel node) {
        node.color = RED;

        // While we have a violation (Parent is also RED)
        while (node != null && node != root && isRed(node.parent)) {

            // Is our Parent the LEFT child of Grandparent?
            if (parentOf(node) == leftOf(grandparentOf(node))) {
                PriceLevel uncle = rightOf(grandparentOf(node));

                // --- CASE 1: Recolor ---
                if (isRed(uncle)) {
                    setColor(parentOf(node), BLACK);
                    setColor(uncle, BLACK);
                    setColor(grandparentOf(node), RED);
                    node = grandparentOf(node); // Problem moves up to Grandparent
                } else {
                    // --- CASE 2: The Triangle (Zig-Zag) ---
                    if (node == rightOf(parentOf(node))) {
                        node = parentOf(node);
                        rotateLeft(node);
                    }

                    // --- CASE 3: The Line ---
                    setColor(parentOf(node), BLACK);
                    setColor(grandparentOf(node), RED);
                    rotateRight(grandparentOf(node));
                }
            }
            // Symmetric Mirror Image (Parent is RIGHT child)
            else {
                PriceLevel uncle = leftOf(grandparentOf(node));

                if (isRed(uncle)) { // Case 1
                    setColor(parentOf(node), BLACK);
                    setColor(uncle, BLACK);
                    setColor(grandparentOf(node), RED);
                    node = grandparentOf(node);
                } else {
                    if (node == leftOf(parentOf(node))) { // Case 2 (Triangle)
                        node = parentOf(node);
                        rotateRight(node);
                    }
                    // Case 3 (Line)
                    setColor(parentOf(node), BLACK);
                    setColor(grandparentOf(node), RED);
                    rotateLeft(grandparentOf(node));
                }
            }
        }
        root.color = BLACK; // Safety: Root is always BLACK
    }

    /**
     * <h3>Left Rotation</h3>
     * Imagine grabbing the 'child' and pulling it UP, forcing the 'parent' DOWN to
     * the left.
     * 
     * <pre>
     *      P          R
     *     / \        / \
     *    A   R  ==> P   B
     *       / \    / \
     *      A   B  A   A
     * </pre>
     */
    private void rotateLeft(PriceLevel p) {
        if (p != null) {
            PriceLevel r = p.right;
            p.right = r.left;
            if (r.left != null)
                r.left.parent = p;
            r.parent = p.parent;
            if (p.parent == null)
                root = r;
            else if (p.parent.left == p)
                p.parent.left = r;
            else
                p.parent.right = r;
            r.left = p;
            p.parent = r;
        }
    }

    /**
     * <h3>Right Rotation</h3>
     * The mirror of Left Rotation. Pulls the Left child UP.
     */
    private void rotateRight(PriceLevel p) {
        if (p != null) {
            PriceLevel l = p.left;
            p.left = l.right;
            if (l.right != null)
                l.right.parent = p;
            l.parent = p.parent;
            if (p.parent == null)
                root = l;
            else if (p.parent.right == p)
                p.parent.right = l;
            else
                p.parent.left = l;
            l.right = p;
            p.parent = l;
        }
    }

    // =========================================================================
    // DELETION (The Surgical Procedure)
    // =========================================================================

    private void deleteNode(PriceLevel node) {
        if (node == null)
            return;

        // Step 1: Reduce complex case (2 children) to simple case (0 or 1 child).
        // If the node has 2 children, we SWAP places with its Successor.
        if (node.left != null && node.right != null) {
            PriceLevel s = successor(node);
            intrusiveNodeSwap(node, s); // Physical swap in the tree structure
        }

        // Step 2: Now 'node' has at most 1 child. Let's find the replacement.
        PriceLevel replacement = (node.left != null ? node.left : node.right);

        if (replacement != null) {
            // Link replacement to parent
            replacement.parent = node.parent;
            if (node.parent == null)
                root = replacement;
            else if (node == node.parent.left)
                node.parent.left = replacement;
            else
                node.parent.right = replacement;

            // Disconnect node
            node.left = node.right = node.parent = null;

            // If we deleted a BLACK node, the tree is now unbalanced (short on Black).
            if (node.color == BLACK)
                rebalanceAfterDeletion(replacement);

        } else if (node.parent == null) {
            // Tree is now empty
            root = null;
        } else {
            // Node is a leaf
            if (node.color == BLACK)
                rebalanceAfterDeletion(node);

            if (node.parent != null) {
                if (node == node.parent.left)
                    node.parent.left = null;
                else if (node == node.parent.right)
                    node.parent.right = null;
                node.parent = null;
            }
        }
    }

    private void rebalanceAfterDeletion(PriceLevel x) {
        while (x != root && isBlack(x)) {
            if (x == leftOf(parentOf(x))) {
                PriceLevel sib = rightOf(parentOf(x));

                if (isRed(sib)) {
                    setColor(sib, BLACK);
                    setColor(parentOf(x), RED);
                    rotateLeft(parentOf(x));
                    sib = rightOf(parentOf(x));
                }

                if (isBlack(leftOf(sib)) && isBlack(rightOf(sib))) {
                    setColor(sib, RED);
                    x = parentOf(x);
                } else {
                    if (isBlack(rightOf(sib))) {
                        setColor(leftOf(sib), BLACK);
                        setColor(sib, RED);
                        rotateRight(sib);
                        sib = rightOf(parentOf(x));
                    }
                    setColor(sib, colorOf(parentOf(x)));
                    setColor(parentOf(x), BLACK);
                    setColor(rightOf(sib), BLACK);
                    rotateLeft(parentOf(x));
                    x = root;
                }
            } else { // Symmetric
                PriceLevel sib = leftOf(parentOf(x));

                if (isRed(sib)) {
                    setColor(sib, BLACK);
                    setColor(parentOf(x), RED);
                    rotateRight(parentOf(x));
                    sib = leftOf(parentOf(x));
                }

                if (isBlack(rightOf(sib)) && isBlack(leftOf(sib))) {
                    setColor(sib, RED);
                    x = parentOf(x);
                } else {
                    if (isBlack(leftOf(sib))) {
                        setColor(rightOf(sib), BLACK);
                        setColor(sib, RED);
                        rotateLeft(sib);
                        sib = leftOf(parentOf(x));
                    }
                    setColor(sib, colorOf(parentOf(x)));
                    setColor(parentOf(x), BLACK);
                    setColor(leftOf(sib), BLACK);
                    rotateRight(parentOf(x));
                    x = root;
                }
            }
        }
        setColor(x, BLACK);
    }

    /**
     * Swaps two nodes physically in the tree topology.
     * Crucial for Intrusive Trees because we cannot copy data.
     */
    private void intrusiveNodeSwap(PriceLevel x, PriceLevel y) {
        // [Logic preserved from original, renamed for clarity]
        PriceLevel xParent = x.parent;
        PriceLevel xLeft = x.left;
        PriceLevel xRight = x.right;
        boolean xColor = x.color;

        PriceLevel yParent = y.parent;
        PriceLevel yLeft = y.left;
        PriceLevel yRight = y.right;
        boolean yColor = y.color;

        boolean yIsChild = (y == xRight);

        // 1. Move Y into X's spot
        y.parent = xParent;
        if (xParent != null) {
            if (xParent.left == x)
                xParent.left = y;
            else
                xParent.right = y;
        } else {
            root = y;
        }
        y.left = xLeft;
        if (xLeft != null)
            xLeft.parent = y;

        if (yIsChild) {
            y.right = x;
        } else {
            y.right = xRight;
            if (xRight != null)
                xRight.parent = y;
        }
        y.color = xColor;

        // 2. Move X into Y's spot
        if (yIsChild) {
            x.parent = y;
        } else {
            x.parent = yParent;
            if (yParent != null) {
                if (yParent.left == y)
                    yParent.left = x;
                else
                    yParent.right = x;
            }
        }
        x.left = yLeft;
        if (yLeft != null)
            yLeft.parent = x;
        x.right = yRight;
        if (yRight != null)
            yRight.parent = x;
        x.color = yColor;
    }

    // --- Readable Helpers (No more Magic Null Checks) ---

    private boolean isRed(PriceLevel p) {
        return p != null && p.color == RED;
    }

    private boolean isBlack(PriceLevel p) {
        return p == null || p.color == BLACK;
    }

    private boolean colorOf(PriceLevel p) {
        return (p == null ? BLACK : p.color);
    }

    private PriceLevel parentOf(PriceLevel p) {
        return (p == null ? null : p.parent);
    }

    // Explicit Grandparent Helper
    private PriceLevel grandparentOf(PriceLevel p) {
        return (p != null && p.parent != null) ? p.parent.parent : null;
    }

    private void setColor(PriceLevel p, boolean c) {
        if (p != null)
            p.color = c;
    }

    private PriceLevel leftOf(PriceLevel p) {
        return (p == null) ? null : p.left;
    }

    private PriceLevel rightOf(PriceLevel p) {
        return (p == null) ? null : p.right;
    }

    private PriceLevel successor(PriceLevel t) {
        if (t == null)
            return null;
        else if (t.right != null) {
            PriceLevel p = t.right;
            while (p.left != null)
                p = p.left;
            return p;
        } else {
            PriceLevel p = t.parent;
            PriceLevel ch = t;
            while (p != null && ch == p.right) {
                ch = p;
                p = p.parent;
            }
            return p;
        }
    }
}

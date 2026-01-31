package com.nanosecond.core;

/**
 * <b>The Price Level: A "Fat" Node</b>
 * <p>
 * Represents all orders at a specific price.
 * </p>
 * 
 * <h3>HFT Pattern: Intrusive Linked List & Tree Node</h3>
 * <p>
 * Instead of having a `List` object that holds `Node` objects that hold `Order`
 * objects,
 * we make the `Order` object itself a node (prev/next).
 * Similarly, this `PriceLevel` object is ITSELF a node in the Red-Black Tree
 * (left/right/parent).
 * </p>
 * 
 * <b>Why?</b>
 * <ul>
 * <li><b>Pointer Chasing:</b> We remove 2 layers of indirection.</li>
 * <li><b>GC Pressure:</b> We save millions of `Node` objects.</li>
 * </ul>
 */
public class PriceLevel {
    public long price;
    // List pointers
    public Order head;
    public Order tail;
    public long totalQuantity;

    // Red-Black Tree pointers (Intrusive)
    public PriceLevel left;
    public PriceLevel right;
    public PriceLevel parent;
    public boolean color; // true = RED, false = BLACK

    public void reset() {
        price = 0;
        head = null;
        tail = null;
        totalQuantity = 0;

        left = null;
        right = null;
        parent = null;
        color = false; // Default to BLACK
    }

    public void addOrder(Order order) {
        if (head == null) {
            head = order;
            tail = order;
            order.prev = null;
            order.next = null;
        } else {
            tail.next = order;
            order.prev = tail;
            order.next = null;
            tail = order;
        }
        totalQuantity += order.quantity;
    }

    /**
     * Removes the order from the level.
     * Note: This assumes the order is actually in this level.
     */
    public void removeOrder(Order order) {
        if (order.prev != null) {
            order.prev.next = order.next;
        } else {
            // It was head
            head = order.next;
        }

        if (order.next != null) {
            order.next.prev = order.prev;
        } else {
            // It was tail
            tail = order.prev;
        }

        totalQuantity -= order.quantity;
        order.next = null;
        order.prev = null;
    }

    public boolean isEmpty() {
        return head == null;
    }
}

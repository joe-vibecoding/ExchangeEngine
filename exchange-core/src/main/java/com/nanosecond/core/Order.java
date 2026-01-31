package com.nanosecond.core;

/**
 * The internal Order representation for the Matching Engine.
 * This is the "heavy" object that sits in the OrderBook.
 * It is pooled to avoid GC.
 */
public class Order {
    public long id;
    public long price;
    public long quantity;
    public byte side; // 0=Buy, 1=Sell
    public long timestamp;

    // For intrusive linked lists in PriceLevels (to avoid extensive object
    // allocation for list nodes)
    public Order next;
    public Order prev;

    public void reset() {
        id = 0;
        price = 0;
        quantity = 0;
        side = 0;
        timestamp = 0;
        next = null;
        prev = null;
    }

    @Override
    public String toString() {
        return "Order{" +
                "id=" + id +
                ", price=" + price +
                ", quantity=" + quantity +
                ", side=" + side +
                '}';
    }
}

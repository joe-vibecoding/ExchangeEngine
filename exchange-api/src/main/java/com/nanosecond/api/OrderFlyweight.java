package com.nanosecond.api;

import org.agrona.MutableDirectBuffer;

/**
 * <b>The OrderFlyweight: Bridging the "Java Paradox".</b>
 * <p>
 * In standard Java "Enterprise" architecture, an Order would be a POJO (Plain
 * Old Java Object).
 * While readable, that approach is fatal for High-Frequency Trading (HFT) due
 * to:
 * </p>
 *
 * <h3>The Problem with POJOs:</h3>
 * <ol>
 * <li><b>Object Overhead:</b> A standard object carries 12-16 bytes of header
 * metadata (Mark Word + Class Pointer).
 * For a massive order book, this overhead destroys cache density.</li>
 * <li><b>Indirection (Pointer Chasing):</b> Objects are scattered across the
 * Heap. Accessing them
 * requires dereferencing pointers, typically causing a <b>CPU Cache Miss</b>
 * (~100 nanoseconds penalty).</li>
 * <li><b>Garbage Collection:</b> Creating millions of short-lived objects
 * generates "Garbage", triggering
 * unpredictable Stop-The-World pauses.</li>
 * </ol>
 *
 * <h3>The Solution: Mechanical Sympathy (Flyweight Pattern)</h3>
 * <p>
 * This class implements the <b>Flyweight Pattern</b> over an Agrona
 * {@link MutableDirectBuffer}.
 * It acts as a "sliding window" or "view" over raw, off-heap memory.
 * </p>
 * <ul>
 * <li><b>Zero Allocation:</b> The Flyweight object itself is reused
 * indefinitely. It just changes its
 * internal pointer (offset).</li>
 * <li><b>Cache Line Friendly:</b> Data is laid out sequentially (ID, Price,
 * Qty, Side). Loading the ID
 * likely loads the Price/Qty into L1 Cache automatically (Spatial
 * Locality).</li>
 * <li><b>Off-Heap Storage:</b> The underlying data is stored in direct memory,
 * invisible to the GC.</li>
 * </ul>
 *
 * <h3>Memory Layout:</h3>
 * 
 * <pre>
 *   0                   8                   16                  24   25
 *   +-------------------+-------------------+-------------------+----+
 *   |     OrderID       |      Price        |     Quantity      |Side|
 *   +-------------------+-------------------+-------------------+----+
 *   (8 bytes)           (8 bytes)           (8 bytes)           (1 byte)
 * </pre>
 */
public class OrderFlyweight {

    // Offsets
    public static final int ORDER_ID_OFFSET = 0;
    public static final int PRICE_OFFSET = 8;
    public static final int QUANTITY_OFFSET = 16;
    public static final int SIDE_OFFSET = 24;

    // Total length in bytes
    public static final int LENGTH = 25;

    private MutableDirectBuffer buffer;
    private int offset;

    public OrderFlyweight() {
        // Typically used for flyweight pattern where wrap() is called later
    }

    public OrderFlyweight wrap(MutableDirectBuffer buffer, int offset) {
        this.buffer = buffer;
        this.offset = offset;
        return this;
    }

    public long orderId() {
        return buffer.getLong(offset + ORDER_ID_OFFSET);
    }

    public void orderId(long orderId) {
        buffer.putLong(offset + ORDER_ID_OFFSET, orderId);
    }

    public long price() {
        return buffer.getLong(offset + PRICE_OFFSET);
    }

    public void price(long price) {
        buffer.putLong(offset + PRICE_OFFSET, price);
    }

    public long quantity() {
        return buffer.getLong(offset + QUANTITY_OFFSET);
    }

    public void quantity(long quantity) {
        buffer.putLong(offset + QUANTITY_OFFSET, quantity);
    }

    public byte side() {
        return buffer.getByte(offset + SIDE_OFFSET);
    }

    public void side(byte side) {
        buffer.putByte(offset + SIDE_OFFSET, side);
    }

    @Override
    public String toString() {
        if (buffer == null) {
            return "OrderFlyweight{unwrapped}";
        }
        return "OrderFlyweight{" +
                "id=" + orderId() +
                ", price=" + price() +
                ", qty=" + quantity() +
                ", side=" + side() +
                '}';
    }
}

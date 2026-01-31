package com.nanosecond.api;

import org.agrona.MutableDirectBuffer;

/**
 * <b>The Execution Report Flyweight</b>
 * <p>
 * Represents the layout of an Execution Report message sent back to the client.
 * <p>
 * <b>Layout:</b>
 * 
 * <pre>
 *   0                   8                   16                  24     25
 *   +-------------------+-------------------+-------------------+------+
 *   |     OrderID       |    FilledQty      |    FillPrice      |Status| ...
 *   +-------------------+-------------------+-------------------+------+
 * </pre>
 */
public class ExecutionReportFlyweight {

    public static final int ORDER_ID_OFFSET = 0;
    public static final int FILLED_QTY_OFFSET = 8;
    public static final int FILL_PRICE_OFFSET = 16;
    public static final int STATUS_OFFSET = 24;
    public static final int SIDE_OFFSET = 25;

    public static final int LENGTH = 26;

    private org.agrona.DirectBuffer buffer;
    private int offset;

    public ExecutionReportFlyweight() {
    }

    public ExecutionReportFlyweight wrap(org.agrona.DirectBuffer buffer, int offset) {
        this.buffer = buffer;
        this.offset = offset;
        return this;
    }

    public void orderId(long id) {
        ((MutableDirectBuffer) buffer).putLong(offset + ORDER_ID_OFFSET, id);
    }

    public long orderId() {
        return buffer.getLong(offset + ORDER_ID_OFFSET);
    }

    public void filledQuantity(long qty) {
        ((MutableDirectBuffer) buffer).putLong(offset + FILLED_QTY_OFFSET, qty);
    }

    public long filledQuantity() {
        return buffer.getLong(offset + FILLED_QTY_OFFSET);
    }

    public void fillPrice(long price) {
        ((MutableDirectBuffer) buffer).putLong(offset + FILL_PRICE_OFFSET, price);
    }

    public long fillPrice() {
        return buffer.getLong(offset + FILL_PRICE_OFFSET);
    }

    public void status(byte status) {
        ((MutableDirectBuffer) buffer).putByte(offset + STATUS_OFFSET, status);
    }

    public byte status() {
        return buffer.getByte(offset + STATUS_OFFSET);
    }

    public void side(byte side) {
        ((MutableDirectBuffer) buffer).putByte(offset + SIDE_OFFSET, side);
    }

    public byte side() {
        return buffer.getByte(offset + SIDE_OFFSET);
    }
}

package com.nanosecond.api;

/**
 * <b>Side: The Direction of the Order.</b>
 * <p>
 * Represents whether an order is a BUY (Bid) or a SELL (Ask).
 * </p>
 * 
 * <h3>Educational Note: Why Primitive Constants?</h3>
 * <p>
 * In Enterprise Java, we would use an `Enum`.
 * In HFT, we often prefer primitive `byte` or `char` constants because:
 * <ol>
 * <li><b>Packet Size:</b> A byte takes 1 byte on the wire. Strings take `len +
 * bytes`.</li>
 * <li><b>Switch Statements:</b> Byte/Int switches are compiled to `tableswitch`
 * or `lookupswitch` (O(1) jump).</li>
 * <li><b>Flyweights:</b> We write this byte directly to the raw memory buffer.
 * Converting Enum -> Ordinal -> Byte is an extra step.</li>
 * </ol>
 * </p>
 */
public class Side {
    /** Buy Side (Bid) */
    public static final byte BUY = 0;

    /** Sell Side (Ask) */
    public static final byte SELL = 1;

    private Side() {
        // Prevent instantiation
    }
}

package com.nanosecond.core;

import org.agrona.MutableDirectBuffer;
import com.nanosecond.api.OrderFlyweight;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;

import static org.junit.jupiter.api.Assertions.assertEquals;

class OrderFlyweightTest {

    @Test
    void shouldReadAndWriteToOffHeapMemory() {
        // 1. Allocate a 32-byte off-heap buffer
        // Using direct ByteBuffer to simulate off-heap memory
        final ByteBuffer byteBuffer = ByteBuffer.allocateDirect(32);
        // Wrap it with Agrona's UnsafeBuffer (which implements MutableDirectBuffer)
        final MutableDirectBuffer buffer = new UnsafeBuffer(byteBuffer);

        // 2. Wrap the OrderFlyweight around it
        final OrderFlyweight order = new OrderFlyweight();
        // Wrap at offset 0
        order.wrap(buffer, 0);

        // 3. Set price, quantity, etc.
        final long expectedId = 12345L;
        final long expectedPrice = 99_999L; // e.g., 99.999
        final long expectedQty = 1000L;
        final byte expectedSide = (byte) 1; // 1 for Buy

        order.orderId(expectedId);
        order.price(expectedPrice);
        order.quantity(expectedQty);
        order.side(expectedSide);

        // 4. Assert that the data was written correctly via the flyweight
        assertEquals(expectedId, order.orderId());
        assertEquals(expectedPrice, order.price());
        assertEquals(expectedQty, order.quantity());
        assertEquals(expectedSide, order.side());

        // 5. Assert that the data was written correctly to the raw memory
        // Verify underlying buffer contents at specific offsets
        assertEquals(expectedId, buffer.getLong(0));
        assertEquals(expectedPrice, buffer.getLong(8));
        assertEquals(expectedQty, buffer.getLong(16));
        assertEquals(expectedSide, buffer.getByte(24));
    }
}

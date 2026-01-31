package com.nanosecond.infra;

import com.nanosecond.api.ExecutionReportFlyweight;
import io.aeron.Aeron;
import io.aeron.Publication;
import org.agrona.concurrent.UnsafeBuffer;

import java.nio.ByteBuffer;

/**
 * <b>The Egress Gateway: Publishing Execution Reports.</b>
 * <p>
 * This component is responsible for serializing internal engine events back
 * onto the network (Aeron).
 * </p>
 *
 * <h3>Design Rationality:</h3>
 * <ol>
 * <li><b>Reused Buffer:</b> We use a single, pre-allocated {@link UnsafeBuffer}
 * for serialization.
 * Since this class is called from a single thread (or has thread-local
 * buffers), we avoid
 * allocating new byte arrays for every message, preserving the Zero GC
 * guarantee.</li>
 * <li><b>Flyweight Serialization:</b> We use the
 * {@link ExecutionReportFlyweight} to write fields directly
 * into the buffer (Primitive Puts), skipping the overhead of Object
 * serialization frameworks like Kryo or Protobuf.</li>
 * </ol>
 */
public class AeronEgress {

    private final Publication publication;
    private final UnsafeBuffer tempBuffer;
    private final ExecutionReportFlyweight flyweight;

    public AeronEgress(Aeron aeron, String channel, int streamId) {
        this.publication = aeron.addPublication(channel, streamId);

        // In a single-threaded matching engine, we can reuse this buffer securely.
        // If Egress is on a separate thread, this buffer belongs to that thread.
        this.tempBuffer = new UnsafeBuffer(ByteBuffer.allocateDirect(256));
        this.flyweight = new ExecutionReportFlyweight();
        this.flyweight.wrap(tempBuffer, 0);
    }

    public void sendExecutionReport(long orderId, long filledQty, long price, byte status, byte side) {
        if (!publication.isConnected()) {
            return;
        }
        send(orderId, filledQty, price, status, side);
    }

    public void sendOrderAccepted(long orderId, long qty, long price, byte side) {
        if (!publication.isConnected()) {
            return;
        }
        // Status 0 = NEW.
        send(orderId, qty, price, (byte) 0, side);
    }

    private void send(long orderId, long qty, long price, byte status, byte side) {
        flyweight.orderId(orderId);
        flyweight.filledQuantity(qty);
        flyweight.fillPrice(price);
        flyweight.status(status);
        flyweight.side(side);
        long result = publication.offer(tempBuffer, 0, ExecutionReportFlyweight.LENGTH);
    }

    public void close() {
        publication.close();
    }

    public boolean isConnected() {
        return publication.isConnected();
    }
}

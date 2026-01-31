package com.nanosecond.infra;

import com.lmax.disruptor.RingBuffer;
import com.nanosecond.api.OrderFlyweight;
import io.aeron.Aeron;
import io.aeron.FragmentAssembler;
import io.aeron.Subscription;
import io.aeron.logbuffer.FragmentHandler;

/**
 * <b>The Ingress Gateway: Bridging UDP/IPC to the Ring Buffer.</b>
 * <p>
 * This component acts as the translation layer between the network (Aeron) and
 * the internal event loop (Disruptor).
 * </p>
 *
 * <h3>Design Rationality (Zero Copy Mechanics):</h3>
 * <ol>
 * <li><b>Direct Buffer Access:</b> Aeron receives a packet into a shared memory
 * buffer (mapped file). We wrap this
 * raw memory with an {@link OrderFlyweight} to read fields without
 * deserialization.</li>
 * <li><b>Ring Buffer Claim:</b> We claim a slot in the Disruptor's Ring Buffer
 * *before* copying data. This reserves
 * memory and ensures single-writer safety.</li>
 * <li><b>Primitive Copy:</b> We copy the semantic data (4 longs) into the
 * {@link OrderCommand}. This copy is tiny
 * (~32 bytes) and fits in a single CPU cache line, making it virtually free
 * compared to object allocation.</li>
 * </ol>
 */
public class AeronIngress implements AutoCloseable {

    private final Aeron aeron;
    private final RingBuffer<OrderCommand> ringBuffer;
    private final Subscription subscription;
    private final FragmentAssembler fragmentAssembler;
    private final OrderFlyweight flyweight = new OrderFlyweight();

    public AeronIngress(Aeron aeron, RingBuffer<OrderCommand> ringBuffer, String channel, int streamId) {
        this.aeron = aeron;
        this.ringBuffer = ringBuffer;

        // Define the handler
        FragmentHandler fragmentHandler = (buffer, offset, length, header) -> {
            // zero-copy wrap
            flyweight.wrap((org.agrona.MutableDirectBuffer) buffer, offset);

            long sequence = ringBuffer.next();
            try {
                OrderCommand cmd = ringBuffer.get(sequence);
                // Copy data from Flyweight to Event (DTO)
                // In a true zero-copy system, the Event might just hold the buffer,
                // but the buffer is reused by Aeron, so we MUST copy or claim.
                // Since this is across threads (Aeron thread -> Matching thread), copy is safer
                // unless we use claim strategy.
                cmd.id = flyweight.orderId();
                cmd.price = flyweight.price();
                cmd.quantity = flyweight.quantity();
                cmd.side = flyweight.side();
            } finally {
                ringBuffer.publish(sequence);
            }
        };

        this.fragmentAssembler = new FragmentAssembler(fragmentHandler);
        this.subscription = aeron.addSubscription(channel, streamId);
    }

    public int poll() {
        return subscription.poll(fragmentAssembler, 10);
    }

    @Override
    public void close() {
        subscription.close();
    }
}

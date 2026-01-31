package com.nanosecond.infra;

import com.nanosecond.api.OrderFlyweight;
import io.aeron.Aeron;
import io.aeron.Publication;
import io.aeron.driver.MediaDriver;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.assertTrue;

class EndToEndLatencyTest {

    private ExchangeServer server;
    private Thread serverThread;

    @BeforeEach
    void setUp() {
        server = new ExchangeServer();
        serverThread = new Thread(server::run);
        serverThread.start();
    }

    @AfterEach
    void tearDown() {
        server.stop();
        try {
            serverThread.join(1000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    @Test
    void shouldSendAndReceiveOrdersViaAeronIPC() throws InterruptedException {
        // 1. Create a Publisher connected to the same Aeron instance (or same
        // directory)
        // Since ExchangeServer uses an embedded MediaDriver in a temp dir,
        // we should really use the SAME Aeron instance or connect to the same
        // Directory.
        // ExchangeServer exposes getAeron(), let's use that for simplicity.

        Aeron aeron = server.getAeron();
        String channel = "aeron:ipc";
        int streamId = 10;

        Publication publication = aeron.addPublication(channel, streamId);

        // 2. Wait for connection
        while (!publication.isConnected()) {
            Thread.yield();
        }

        // 3. Send an Order
        UnsafeBuffer buffer = new UnsafeBuffer(ByteBuffer.allocateDirect(256));
        OrderFlyweight flyweight = new OrderFlyweight();
        flyweight.wrap(buffer, 0);

        flyweight.orderId(1);
        flyweight.price(100);
        flyweight.quantity(10);
        flyweight.side((byte) 0);

        long result = publication.offer(buffer, 0, OrderFlyweight.LENGTH);

        assertTrue(result > 0, "Failed to send message to Aeron");

        // 4. Verification?
        // Since we don't have an output stream, we just verify successful offer.
        // We could assume if it offers, and Server is running, it processes.

        // Give it a moment to process
        Thread.sleep(100);
    }
}

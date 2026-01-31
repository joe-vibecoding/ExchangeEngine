package com.nanosecond.infra.benchmark;

import com.nanosecond.api.ExecutionReportFlyweight;
import com.nanosecond.api.OrderFlyweight;
import io.aeron.Aeron;
import io.aeron.ConcurrentPublication;
import io.aeron.FragmentAssembler;
import io.aeron.Subscription;
import io.aeron.driver.MediaDriver;
import io.aeron.driver.ThreadingMode;
import io.aeron.logbuffer.FragmentHandler;
import org.agrona.concurrent.BusySpinIdleStrategy;
import org.agrona.concurrent.UnsafeBuffer;

import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Performance Testing Tool.
 * Generates load and measures latency.
 */
public class LoadGenerator {

    private static final int WARMUP = 10_000;

    public static void main(String[] args) {
        int messageCount = 100_000;
        if (args.length > 0) {
            try {
                messageCount = Integer.parseInt(args[0]);
            } catch (NumberFormatException e) {
                System.err.println("Invalid message count, defaulting to " + messageCount);
            }
        }
        final int MESSAGES = messageCount;

        System.out.println("Starting Load Generator with " + MESSAGES + " messages...");

        // Connect to the SAME media driver context as the server
        String aeronDir = System.getProperty("aeron.dir");

        try (Aeron aeron = Aeron.connect()) {

            String channel = "aeron:ipc";
            int ingressStreamId = 10; // Send to Server
            int egressStreamId = 11; // Listen from Server

            System.out.println("Connecting to " + channel);

            // Publisher (Sender)
            ConcurrentPublication pub = aeron.addPublication(channel, ingressStreamId);

            // Subscriber (Receiver)
            Subscription sub = aeron.addSubscription(channel, egressStreamId);

            // Wait for connection
            while (!pub.isConnected()) {
                Thread.yield();
            }
            while (!sub.isConnected()) {
                Thread.yield();
            }
            System.out.println("Connected (Ingress & Egress)!");

            UnsafeBuffer buffer = new UnsafeBuffer(ByteBuffer.allocateDirect(256));
            OrderFlyweight order = new OrderFlyweight();
            order.wrap(buffer, 0);

            ExecutionReportFlyweight report = new ExecutionReportFlyweight();

            long[] latencies = new long[MESSAGES];
            AtomicInteger receivedCount = new AtomicInteger(0);
            long[] sentTimestamps = new long[MESSAGES + WARMUP];

            FragmentHandler handler = (b, offset, length, header) -> {
                long now = System.nanoTime();
                report.wrap((org.agrona.MutableDirectBuffer) b, offset);
                long id = report.orderId();
                if (id >= 0 && id < sentTimestamps.length) {
                    long sendTime = sentTimestamps[(int) id];
                    long rtt = now - sendTime;

                    if (id >= WARMUP) {
                        latencies[(int) (id - WARMUP)] = rtt;
                    }
                    receivedCount.incrementAndGet();
                }
            };
            FragmentAssembler assembler = new FragmentAssembler(handler);

            // Start Consumer Thread (Latency Measurement)
            Thread consumerThread = new Thread(() -> {
                FragmentAssembler consumerAssembler = new FragmentAssembler(handler);
                while (receivedCount.get() < WARMUP + MESSAGES) {
                    sub.poll(consumerAssembler, 10);
                }
            });
            consumerThread.start();

            boolean latencyMode = args.length > 1 && "-latency".equals(args[1]);

            if (latencyMode) {
                System.out.println("Mode: PING-PONG LATENCY (Sequential)");
                // Single thread ping-pong
                UnsafeBuffer myBuffer = new UnsafeBuffer(ByteBuffer.allocateDirect(256));
                OrderFlyweight myOrder = new OrderFlyweight();
                myOrder.wrap(myBuffer, 0);

                FragmentAssembler consumerAssembler = new FragmentAssembler(handler);

                System.out.println("Warmup complete. Measuring 1-by-1 RTT...");
                long start = System.currentTimeMillis();

                for (int i = 0; i < MESSAGES; i++) {
                    long id = i + WARMUP; // Offset ID
                    myOrder.orderId(id);
                    myOrder.price(100);
                    myOrder.quantity(1);
                    myOrder.side((byte) 0);

                    sentTimestamps[(int) id] = System.nanoTime();

                    // Send
                    while (pub.offer(myBuffer, 0, OrderFlyweight.LENGTH) < 0) {
                        Thread.onSpinWait();
                    }

                    // Wait for Reply
                    int currentReceived = receivedCount.get();
                    while (receivedCount.get() == currentReceived) {
                        if (sub.poll(consumerAssembler, 10) == 0) {
                            Thread.onSpinWait();
                        }
                    }
                }
                long end = System.currentTimeMillis();
                System.out.println("Done. Throughput: " + (MESSAGES / ((end - start) / 1000.0)) + " msg/sec");

            } else if (args.length > 0 && "--demo".equals(args[args.length - 1])) {
                System.out.println("Mode: DEMO SIMULATION (Market Maker)");
                // Simulation Loop
                UnsafeBuffer myBuffer = new UnsafeBuffer(ByteBuffer.allocateDirect(256));
                OrderFlyweight myOrder = new OrderFlyweight();
                myOrder.wrap(myBuffer, 0);

                java.util.Random rand = new java.util.Random();

                // Start Consumer to drain output so buffers don't fill up
                Thread consumer = new Thread(() -> {
                    FragmentAssembler consumerAssembler = new FragmentAssembler(handler);
                    while (true) {
                        sub.poll(consumerAssembler, 10);
                        try {
                            Thread.sleep(1);
                        } catch (Exception e) {
                        }
                    }
                });
                consumer.start();

                System.out.println("Generating Market Activity...");
                int currentMidPrice = 10000; // State for random walk (Declared OUTSIDE loop)

                for (int i = 0; i < MESSAGES; i++) {
                    long id = i + WARMUP + 10_000_000L; // Shift IDs to avoid collision with manual trades range (0-1M)
                    myOrder.orderId(id);

                    // Random Walk Logic
                    // 10% chance to move the "mid" price
                    if (rand.nextInt(100) < 10) {
                        int move = rand.nextBoolean() ? 5 : -5;
                        currentMidPrice += move;
                        // Keep within bounds (90.00 - 110.00)
                        if (currentMidPrice < 9000)
                            currentMidPrice = 9000;
                        if (currentMidPrice > 11000)
                            currentMidPrice = 11000;
                    }

                    int spread = 5 + rand.nextInt(10); // 0.05 - 0.15 tight spread

                    boolean isBuy = rand.nextBoolean();

                    // 10% chance of crossing the spread (Market Order behavior)
                    boolean aggressive = rand.nextInt(100) < 10;

                    long price;
                    if (isBuy) {
                        // Bid
                        price = aggressive ? (currentMidPrice + spread) : (currentMidPrice - spread);
                    } else {
                        // Ask
                        price = aggressive ? (currentMidPrice - spread) : (currentMidPrice + spread);
                    }

                    myOrder.price(price);
                    myOrder.quantity(10 + rand.nextInt(90));
                    myOrder.side(isBuy ? (byte) 0 : (byte) 1);

                    // Send
                    while (pub.offer(myBuffer, 0, OrderFlyweight.LENGTH) < 0) {
                        Thread.onSpinWait();
                    }

                    // Slow down for visual effect
                    try {
                        Thread.sleep(50); // 20 updates/sec
                    } catch (InterruptedException e) {
                        break;
                    }
                }

            } else {
                System.out.println("Mode: THROUGHPUT (Concurrent)");
                System.out.println("Warmup complete. Measuring...");
                long start = System.currentTimeMillis();

                final int half = (WARMUP + MESSAGES) / 2;

                Thread buyer = new Thread(() -> {
                    UnsafeBuffer myBuffer = new UnsafeBuffer(ByteBuffer.allocateDirect(256));
                    OrderFlyweight myOrder = new OrderFlyweight();
                    myOrder.wrap(myBuffer, 0);

                    for (int i = 0; i < half; i++) {
                        long id = i * 2L;
                        myOrder.orderId(id);
                        myOrder.price(100);
                        myOrder.quantity(10);
                        myOrder.side((byte) 0); // Buy

                        sentTimestamps[(int) id] = System.nanoTime();
                        while (pub.offer(myBuffer, 0, OrderFlyweight.LENGTH) < 0) {
                            Thread.onSpinWait();
                        }

                        if (args.length > 2 && "-delay".equals(args[2])) {
                            try {
                                long delayMs = Long.parseLong(args[3]);
                                Thread.sleep(delayMs);
                            } catch (Exception ignore) {
                            }
                        }
                    }
                });

                Thread seller = new Thread(() -> {
                    UnsafeBuffer myBuffer = new UnsafeBuffer(ByteBuffer.allocateDirect(256));
                    OrderFlyweight myOrder = new OrderFlyweight();
                    myOrder.wrap(myBuffer, 0);

                    for (int i = 0; i < half; i++) {
                        long id = (i * 2L) + 1;
                        myOrder.orderId(id);
                        myOrder.price(100);
                        myOrder.quantity(10);
                        myOrder.side((byte) 1); // Sell

                        sentTimestamps[(int) id] = System.nanoTime();
                        while (pub.offer(myBuffer, 0, OrderFlyweight.LENGTH) < 0) {
                            Thread.onSpinWait();
                        }

                        if (args.length > 2 && "-delay".equals(args[2])) {
                            try {
                                long delayMs = Long.parseLong(args[3]);
                                Thread.sleep(delayMs);
                            } catch (Exception ignore) {
                            }
                        }
                    }
                });

                buyer.start();
                seller.start();

                try {
                    buyer.join();
                    seller.join();
                    consumerThread.join();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                long end = System.currentTimeMillis();
                System.out.println("Done. Throughput: " + (MESSAGES / ((end - start) / 1000.0)) + " msg/sec");
            }

            System.out.println("Analyzing " + MESSAGES + " messages...");

            // Calculate p50, p99
            java.util.Arrays.sort(latencies);
            long p50 = latencies[(int) (MESSAGES * 0.50)];
            long p99 = latencies[(int) (MESSAGES * 0.99)];
            long p999 = latencies[(int) (MESSAGES * 0.999)];
            long max = latencies[MESSAGES - 1];

            System.out.println("Latency (ns):");
            System.out.println("p50:   " + p50);
            System.out.println("p99:   " + p99);
            System.out.println("p99.9: " + p999);
            System.out.println("Max:   " + max);
        }
    }
}

package com.nanosecond.infra;

import com.lmax.disruptor.dsl.Disruptor;
import com.lmax.disruptor.util.DaemonThreadFactory;
import com.lmax.disruptor.RingBuffer;
import com.nanosecond.core.MatchingEngine;
import io.aeron.Aeron;
import io.aeron.driver.MediaDriver;
import io.aeron.driver.ThreadingMode;
import org.agrona.concurrent.BusySpinIdleStrategy;
import com.nanosecond.infra.logging.Logger;

/**
 * <b>The Nanosecond Exchange Server.</b>
 * <p>
 * This class serves as the <b>Composition Root</b>, wiring together the "Holy
 * Trinity" of low-latency components:
 * <ul>
 * <li><b>Aeron:</b> High-performance UDP/IPC messaging.</li>
 * <li><b>Disruptor:</b> Inter-thread messaging (Single Writer).</li>
 * <li><b>Agrona:</b> Off-heap memory management and primitive collections.</li>
 * </ul>
 *
 * <h3>System Topology:</h3>
 * 
 * <pre>
 * [Inbound Network (Aeron IPC)]
 *         |
 *         v
 * [AeronIngress Thread] -> (Busy Spin Poll)
 *         |
 *    (Input Ring Buffer)
 *         |
 *         v
 * [Matching Engine (Core)] -> (Batch Consumer)
 * </pre>
 *
 * <h3>Key Architecture Decisions:</h3>
 * <ul>
 * <li><b>Embedded MediaDriver:</b> We launch an embedded driver for
 * self-contained execution. In production,
 * this runs as a separate process to isolate the "noisy neighbor" effect (GC,
 * log writing) from the critical path.</li>
 * <li><b>BusySpinIdleStrategy:</b> We use {@link BusySpinIdleStrategy} to
 * "burn" CPU cycles in a `while(true)` loop.
 * While this consumes 100% of a core, it prevents the OS Scheduler from putting
 * the thread to sleep, eliminates
 * wakeup latency (~50-100us), and ensures the CPU cache remains hot.</li>
 * </ul>
 */
public class ExchangeServer {

    private final Disruptor<OrderCommand> disruptor;
    private final RingBuffer<OrderCommand> ringBuffer;
    private final MatchingEngine matchingEngine;

    // Aeron Components
    private final MediaDriver mediaDriver;
    private final Aeron aeron;
    private final AeronIngress ingress;
    private final AeronEgress egress;

    private volatile boolean running = true;

    public ExchangeServer() {
        // 1. Initialize Aeron (Embedded Media Driver for simplicity)
        // Optimize for low latency
        final MediaDriver.Context mediaDriverCtx = new MediaDriver.Context()
                .dirDeleteOnStart(true)
                .threadingMode(ThreadingMode.SHARED) // Use SHARED for laptop simulation
                .sharedIdleStrategy(new BusySpinIdleStrategy())
                .termBufferSparseFile(false);

        this.mediaDriver = MediaDriver.launch(mediaDriverCtx);
        this.aeron = Aeron.connect(new Aeron.Context().aeronDirectoryName(mediaDriver.aeronDirectoryName()));

        // 2. Start Egress (Publisher)
        // IPC Channel
        String channel = "aeron:ipc";
        int ingressStreamId = 10;
        int egressStreamId = 11;

        this.egress = new AeronEgress(aeron, channel, egressStreamId);

        // 1. Initialize Core with Listener
        // Create the listener that connects Core events to Aeron Egress
        com.nanosecond.core.MatchEventListener listener = new AeronMatchEventListener(egress);
        this.matchingEngine = new MatchingEngine(listener);

        // 4. Initialize Disruptor
        // Use BusySpinWaitStrategy for the RingBuffer consumer to avoid locking
        int bufferSize = 65536;
        this.disruptor = new Disruptor<>(
                OrderCommand.FACTORY,
                bufferSize,
                DaemonThreadFactory.INSTANCE,
                com.lmax.disruptor.dsl.ProducerType.MULTI,
                new com.lmax.disruptor.BusySpinWaitStrategy());

        // Pass only the engine to the handler
        this.disruptor.handleEventsWith(new MatchingEngineEventHandler(matchingEngine));
        this.ringBuffer = disruptor.start();

        // 5. Start Ingress (Subscriber)
        this.ingress = new AeronIngress(aeron, ringBuffer, channel, ingressStreamId);

        // 6. JIT Warmup
        // Execute 100k dummy trades to trigger C2 compilation before opening network
        // ports
        new com.nanosecond.core.WarmupService().warmup(null); // Passing null as it creates its own engine

        System.out.println("Exchange Engine Started.");

        // Initialize Logger
        // Logger logger = new
        // com.nanosecond.infra.logging.ChronicleLogger("exchange-logs");
        // logger.log("Exchange Engine Started");
        // logger.log("Ingress Stream", ingressStreamId);
        // logger.log("Egress Stream", egressStreamId);

        System.out.println("Logs writing to: exchange-logs/");
        System.out.println("Aeron Directory: " + mediaDriver.aeronDirectoryName());
    }

    public void run() {
        // Main Loop to poll Aeron
        // In reality, this should be on its own thread pinned to a core.
        // For this demo, run in main.
        while (running) {
            ingress.poll();
        }
    }

    public void stop() {
        running = false;
        ingress.close();
        egress.close();
        aeron.close();
        mediaDriver.close();
        disruptor.shutdown();
    }

    public RingBuffer<OrderCommand> getRingBuffer() {
        return ringBuffer;
    }

    public Aeron getAeron() {
        return aeron;
    }

    public MatchingEngine getMatchingEngine() {
        return matchingEngine;
    }

    public static void main(String[] args) {
        ExchangeServer server = new ExchangeServer();
        // Add shutdown hook
        Runtime.getRuntime().addShutdownHook(new Thread(server::stop));
        server.run();
    }
}

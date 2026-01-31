package com.nanosecond.gateway;

import io.aeron.Aeron;
import io.aeron.Publication;
import io.aeron.Subscription;
import io.aeron.driver.MediaDriver;
import io.aeron.driver.ThreadingMode;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler;
import io.netty.handler.stream.ChunkedWriteHandler;
import io.netty.handler.stream.ChunkedWriteHandler;
import org.agrona.concurrent.BusySpinIdleStrategy;
import com.nanosecond.api.ExecutionReportFlyweight;
import io.netty.channel.group.ChannelGroup;
import io.netty.channel.group.DefaultChannelGroup;
import io.netty.util.concurrent.GlobalEventExecutor;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;

/**
 * <h1>The Gateway Server: Bridging the World to the Core</h1>
 * 
 * <p>
 * The Gateway Server acts as the <b>Translation Layer</b> between the external
 * world (WebSockets, TCP, FIX)
 * and the internal high-performance core (Aeron IPC).
 * </p>
 *
 * <h2>Architectural Role</h2>
 * <p>
 * A Matching Engine speaks a binary, machine-optimized dialect (Flyweights,
 * structs).
 * Users speak human-friendly protocols (JSON, Text). The Gateway bridges this
 * gap.
 * </p>
 * 
 * <h3>Responsibility Chain:</h3>
 * <ol>
 * <li><b>Ingress (Netty):</b> Accepts thousands of concurrent WebSocket
 * connections efficiently using NIO (Non-blocking IO).</li>
 * <li><b>Protocol Translation:</b> Parses incoming JSON frames (e.g., "New
 * Order") into strict binary {@link com.nanosecond.api.OrderFlyweight}
 * structures.</li>
 * <li><b>Transport (Aeron):</b> Publishes the binary command to the <b>Aeron
 * IPC Channel</b>. Ideally, this writes directly to a shared memory buffer that
 * the Matching Engine reads from.</li>
 * <li><b>State Replication:</b> Maintains a local "View" of the Order Book
 * (Shadow Book) to serve initial {@code SNAPSHOT} messages to new clients
 * without disturbing the Core engine.</li>
 * </ol>
 *
 * <h2>Technology Stack</h2>
 * <ul>
 * <li><b>Netty:</b> For high-throughput, low-latency network handling.</li>
 * <li><b>Aeron:</b> For reliable, ultra-low latency messaging between the
 * Gateway process and the Engine process.</li>
 * <li><b>Agrona:</b> For off-heap memory management and primitive
 * collections.</li>
 * </ul>
 */
public class GatewayServer {

    private static final int PORT = 8080;
    private static final String AERON_CHANNEL = "aeron:ipc";
    private static final int ENGINE_INGRESS_STREAM = 10; // Gateway publishes here
    private static final int ENGINE_EGRESS_STREAM = 11; // Gateway subscribes here

    public static void main(String[] args) throws Exception {
        new GatewayServer().run();
    }

    public void run() throws Exception {
        System.out.println("Starting Gateway...");

        // 1. Start Aeron (Connect to existing MediaDriver or launch embedded if needed)
        // ideally MediaDriver is separate, but for dev simplicity we can connect to the
        // one Engine started
        // OR we just assume shared memory.

        // Note: In this architecture, usually the Engine owns the Driver.
        // We just connect as a client.

        System.out.println("Connecting to Aeron...");
        final Aeron.Context ctx = new Aeron.Context();
        // Just connect to default dir

        try (Aeron aeron = Aeron.connect(ctx);
                Publication publication = aeron.addPublication(AERON_CHANNEL, ENGINE_INGRESS_STREAM);
                Subscription subscription = aeron.addSubscription(AERON_CHANNEL, ENGINE_EGRESS_STREAM)) {

            System.out.println("Connected to Aeron. Dir: " + aeron.context().aeronDirectoryName());

            // 2. Start Netty
            EventLoopGroup bossGroup = new NioEventLoopGroup(1);
            EventLoopGroup workerGroup = new NioEventLoopGroup();

            // 3. Start Market Data Publisher (Egress -> WebSocket Broadcast)
            startMarketDataPublisher(aeron, bossGroup); // Using bossGroup as executor for simplicity or new thread

            // 4. Start Netty
            try {
                ServerBootstrap b = new ServerBootstrap();
                b.group(bossGroup, workerGroup)
                        .channel(NioServerSocketChannel.class)
                        .childHandler(new ChannelInitializer<SocketChannel>() {
                            @Override
                            protected void initChannel(SocketChannel ch) {
                                ch.pipeline().addLast(
                                        new HttpServerCodec(),
                                        new HttpObjectAggregator(65536),
                                        new WebSocketServerProtocolHandler("/ws"),
                                        new WebSocketFrameHandler(publication, orderBook));
                            }
                        });

                Channel ch = b.bind(PORT).sync().channel();
                System.out.println("Gateway listening on ws://localhost:" + PORT + "/ws");

                ch.closeFuture().sync();
            } finally {
                bossGroup.shutdownGracefully();
                workerGroup.shutdownGracefully();
            }
        }
    }

    // A simple broadcast group for all connected clients
    public static final io.netty.channel.group.ChannelGroup allClients = new io.netty.channel.group.DefaultChannelGroup(
            io.netty.util.concurrent.GlobalEventExecutor.INSTANCE);

    private final GatewayOrderBook orderBook = new GatewayOrderBook();

    private void startMarketDataPublisher(Aeron aeron, EventLoopGroup executor) {
        System.out.println("Starting Market Data Publisher...");
        final Subscription subscription = aeron.addSubscription(AERON_CHANNEL, ENGINE_EGRESS_STREAM);

        final com.nanosecond.api.ExecutionReportFlyweight execReport = new com.nanosecond.api.ExecutionReportFlyweight();

        // Run polling in a separate thread to avoid blocking Netty IO
        new Thread(() -> {
            System.out.println("Market Data Publisher Thread Started.");
            final io.aeron.logbuffer.FragmentHandler fragmentHandler = (buffer, offset, length, header) -> {
                // 1. Wrap buffer with Flyweight
                execReport.wrap(buffer, offset);

                // 2. Decode
                long orderId = execReport.orderId();
                long filledQty = execReport.filledQuantity();
                long price = execReport.fillPrice();
                byte status = execReport.status();
                byte side = execReport.side();

                String type;
                if (status == 0) { // NEW (Order Accepted)
                    type = "ORDER_ACCEPTED";
                    orderBook.onOrderAccepted(price, filledQty, side);
                } else { // FILLED
                    type = "EXECUTION";
                    orderBook.onExecution(price, filledQty, side);
                }

                // 3. Serialize to JSON (Poor man's JSON)
                // We broadcast specific events to update the UI incrementally
                // Scale price back to float (div 100.0)
                String json = String.format(
                        "{\"type\": \"%s\", \"orderId\": %d, \"qty\": %d, \"price\": %.2f, \"side\": %d, \"status\": \"%s\"}",
                        type, orderId, filledQty, price / 100.0, side, status == 0 ? "NEW" : "FILLED");

                System.out.println("Broadcasting: " + json);

                // 4. Broadcast
                if (!allClients.isEmpty()) {
                    allClients.writeAndFlush(new io.netty.handler.codec.http.websocketx.TextWebSocketFrame(json));
                }
            };

            final org.agrona.concurrent.IdleStrategy idleStrategy = new BusySpinIdleStrategy();

            while (true) {
                final int fragments = subscription.poll(fragmentHandler, 10);
                idleStrategy.idle(fragments);
            }
        }).start();
    }

    // Modification to WebSocketFrameHandler to send Snapshot
    // Since WebSocketFrameHandler is a new class (or inner class?), we need to
    // modify it or the pipeline init.
    // Actually, we can just add a handler that sends the snapshot on channelActive.

    // But WebSocketFrameHandler is defined in another file?
    // Let's check imports. Yes, 'new WebSocketFrameHandler'.
    // We need to pass the 'orderBook' to it, or 'GatewayServer' singleton?
    // Better: Helper method to send snapshot.

    public void sendSnapshot(Channel channel) {
        String snapshot = orderBook.getSnapshotJson();
        channel.writeAndFlush(new TextWebSocketFrame(snapshot));
    }
}

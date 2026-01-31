package com.nanosecond.gateway;

import com.nanosecond.api.OrderFlyweight;
import com.nanosecond.api.Side;
import io.aeron.Publication;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketFrame;
import org.agrona.concurrent.UnsafeBuffer;
import org.json.JSONObject;

import java.nio.ByteBuffer;

/**
 * <b>Handles WebSocket Frames.</b>
 * <p>
 * Parses incoming JSON orders and publishes them to Aeron.
 * </p>
 */
public class WebSocketFrameHandler extends SimpleChannelInboundHandler<WebSocketFrame> {

    private final Publication publication;
    private final UnsafeBuffer buffer;
    private final OrderFlyweight flyweight;
    private final RiskManager riskManager;
    private final GatewayOrderBook orderBook;

    public WebSocketFrameHandler(Publication publication, GatewayOrderBook orderBook) {
        this.publication = publication;
        this.orderBook = orderBook;
        this.buffer = new UnsafeBuffer(ByteBuffer.allocateDirect(128));
        this.flyweight = new OrderFlyweight();
        this.riskManager = new RiskManager();
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        if (evt instanceof io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler.HandshakeComplete) {
            // handshake complete
            GatewayServer.allClients.add(ctx.channel());

            // Send Snapshot immediately after handshake
            String snapshot = orderBook.getSnapshotJson();
            ctx.channel().writeAndFlush(new TextWebSocketFrame(snapshot));
        }
        super.userEventTriggered(ctx, evt);
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, WebSocketFrame frame) throws Exception {
        if (frame instanceof TextWebSocketFrame) {
            String text = ((TextWebSocketFrame) frame).text();
            handleMessage(ctx, text);
        } else {
            String message = "unsupported frame type: " + frame.getClass().getName();
            throw new UnsupportedOperationException(message);
        }
    }

    private static final java.util.concurrent.atomic.AtomicLong ORDER_ID_SEQ = new java.util.concurrent.atomic.AtomicLong(
            1);

    private void handleMessage(ChannelHandlerContext ctx, String text) {
        try {
            JSONObject json = new JSONObject(text);
            String type = json.optString("type");

            if ("ORDER".equals(type)) {
                // Parse fields robustly
                // Use a simple counter for User Orders (Range 1 - 9,999,999)
                // This allows Frontend to easily distinguish them from LoadGen IDs (>10M)
                long orderId = ORDER_ID_SEQ.getAndIncrement();

                // Handle Price: Support both "100.55" (Number) and "100" (Number)
                // RiskManager expects price in cents (long).
                // If it's a double (100.55), multiply by 100.
                long priceLong;
                Object priceObj = json.get("price");
                if (priceObj instanceof Number) {
                    double p = ((Number) priceObj).doubleValue();
                    priceLong = (long) (Math.round(p * 100.0));
                } else {
                    throw new IllegalArgumentException("Invalid price format");
                }

                long qty = json.getLong("qty");

                // Handle Side: Support "BUY"/"SELL" string OR 0/1 int
                byte side;
                Object sideObj = json.get("side");
                if (sideObj instanceof String) {
                    String s = (String) sideObj;
                    side = "BUY".equals(s) ? Side.BUY : Side.SELL;
                } else if (sideObj instanceof Number) {
                    int s = ((Number) sideObj).intValue();
                    side = (s == 0) ? Side.BUY : Side.SELL;
                } else {
                    throw new IllegalArgumentException("Invalid side format");
                }

                // RISK CHECK
                RiskManager.ValidationResult result = riskManager.validate(priceLong, qty);
                if (result != RiskManager.ValidationResult.VALID) {
                    String rejectJson = String.format(
                            "{\"type\": \"EXECUTION\", \"orderId\": %d, \"status\": \"REJECTED\", \"reason\": \"%s\"}",
                            orderId, result.name());
                    ctx.channel().writeAndFlush(new TextWebSocketFrame(rejectJson));
                    return;
                }

                // Wrap buffer with Flyweight
                flyweight.wrap(buffer, 0);

                flyweight.orderId(orderId);
                flyweight.price(priceLong);
                flyweight.quantity(qty);
                flyweight.side(side);

                // Publish to Aeron
                long offerResult = publication.offer(buffer, 0, OrderFlyweight.LENGTH);

                if (offerResult > 0) {
                    ctx.channel()
                            .writeAndFlush(new TextWebSocketFrame("{\"status\":\"SENT\", \"id\":" + orderId + "}"));
                } else {
                    ctx.channel().writeAndFlush(new TextWebSocketFrame("{\"status\":\"BUSY\"}"));
                }

            } else {
                ctx.channel().writeAndFlush(new TextWebSocketFrame("{\"error\":\"UNKNOWN_TYPE\"}"));
            }
        } catch (Exception e) {
            // System.err.println("JSON Parse Error: " + e.getMessage());
            ctx.channel().writeAndFlush(new TextWebSocketFrame("{\"error\":\"INVALID_FORMAT\"}"));
        }
    }
}

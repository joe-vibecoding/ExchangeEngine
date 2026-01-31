package com.nanosecond.infra.integration;

import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import org.json.JSONArray;
import org.json.JSONObject;

import java.net.URI;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

public class VerificationBot extends WebSocketClient {

    private final CountDownLatch connectLatch = new CountDownLatch(1);
    private final CountDownLatch orderAckLatch = new CountDownLatch(1);
    private final CountDownLatch tradeLatch = new CountDownLatch(2); // Expecting 2 execution reports (Buy + Sell)

    private final long testOrderIdBase;
    private final double testPrice = 500.00;

    // Test State
    private boolean verificationPassed = false;
    private StringBuilder failureReason = new StringBuilder();

    public VerificationBot(URI serverUri, long startOrderId) {
        super(serverUri);
        this.testOrderIdBase = startOrderId;
    }

    public static void main(String[] args) {
        try {
            System.out.println("==========================================");
            System.out.println("   VERIFICATION BOT - INTEGRATION TEST    ");
            System.out.println("==========================================");

            // Connect to Gateway
            VerificationBot bot = new VerificationBot(new URI("ws://localhost:8080/ws"), 90000000);
            bot.connect();

            if (!bot.connectLatch.await(30, TimeUnit.SECONDS)) {
                System.err.println("FATAL: Could not connect to Gateway.");
                System.exit(1);
            }

            System.out.println("STEP 1: Placing Passive BUY Order @ " + bot.testPrice);
            // {"type": "ORDER", "orderId": ..., "amount": ..., "price": ..., "side": ...}
            // Note: Gateway expects raw JSON. Adjust fields to match OrderEntryWidget
            JSONObject buyOrder = new JSONObject();
            buyOrder.put("type", "ORDER");
            buyOrder.put("price", bot.testPrice);
            buyOrder.put("qty", 10);
            buyOrder.put("side", "BUY"); // String for robust testing
            bot.send(buyOrder.toString());

            if (!bot.orderAckLatch.await(30, TimeUnit.SECONDS)) {
                System.err.println("FAILURE: Did not receive ORDER_ACCEPTED for Buy Order.");
                System.exit(1);
            }
            System.out.println("SUCCESS: Buy Order Accepted.");

            // Wait a bit for book update (Snapshot/Update logic usually async)
            Thread.sleep(500);

            System.out.println("STEP 2: Placing Aggressive SELL Order @ " + bot.testPrice);
            JSONObject sellOrder = new JSONObject();
            sellOrder.put("type", "ORDER");
            sellOrder.put("price", bot.testPrice);
            sellOrder.put("qty", 10);
            sellOrder.put("side", "SELL"); // String
            bot.send(sellOrder.toString());

            if (!bot.tradeLatch.await(30, TimeUnit.SECONDS)) {
                System.err.println("FAILURE: Did not receive EXECUTION reports.");
                System.exit(1);
            }

            System.out.println("SUCCESS: Trade Executed for both sides.");
            System.out.println("------------------------------------------");
            System.out.println("INTEGRATION TEST PASSED");
            System.exit(0);

        } catch (Exception e) {
            e.printStackTrace();
            System.exit(1);
        }
    }

    @Override
    public void onOpen(ServerHandshake handshakedata) {
        System.out.println("Bot Connected.");
        connectLatch.countDown();
    }

    @Override
    public void onMessage(String message) {
        try {
            JSONObject msg = new JSONObject(message);
            String type = msg.optString("type");

            if ("ORDER_ACCEPTED".equals(type)) {
                double price = msg.optDouble("price");
                if (Math.abs(price - testPrice) < 0.001) {
                    System.out.println("Received ACK for Price: " + price);
                    orderAckLatch.countDown();
                }
            } else if ("EXECUTION".equals(type)) {
                double price = msg.optDouble("price");
                if (Math.abs(price - testPrice) < 0.001) {
                    String status = msg.optString("status");
                    if ("FILLED".equals(status)) {
                        System.out.println("Received EXECUTION: " + msg.toString());
                        tradeLatch.countDown();
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Error parsing message: " + e.getMessage());
        }
    }

    @Override
    public void onClose(int code, String reason, boolean remote) {
        System.out.println("Bot Disconnected: " + reason);
    }

    @Override
    public void onError(Exception ex) {
        ex.printStackTrace();
    }
}

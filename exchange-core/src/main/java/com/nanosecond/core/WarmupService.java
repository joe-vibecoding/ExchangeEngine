package com.nanosecond.core;

import com.nanosecond.core.OrderBook.MatchResult;
import com.nanosecond.api.Side;

public class WarmupService {

    private static final int WARMUP_ITERATIONS = 200_000;

    public void warmup(MatchingEngine engine) {
        System.out.println("WARMUP: Starting JIT compilation cycles (" + WARMUP_ITERATIONS + ")...");
        long start = System.nanoTime();

        // 1. Create a dummy MatchEventListener to avoid side effects
        MatchEventListener dummyListener = new MatchEventListener() {
            public void onTrade(long orderId, long price, long qty, byte side) {
            }

            public void onOrderAccepted(long orderId, long price, long qty, byte side) {
            }

            public void onOrderRejected(long orderId, long price, long qty, byte side, String reason) {
            }
        };

        // 2. Perform dummy matching cycles
        // We use IDs that won't conflict with real traffic (negative IDs)
        // We use a clean OrderBook for this, or clear it after?
        // Better to use the REAL engine but clear data after.
        // OR better: Create a temporary engine context just for warmup (class loading
        // is shared).
        // Since JIT is optimizing the METHODS, we don't need the exact same object
        // instance.

        // However, passing the real Engine is fine if we reverse the trades.

        // Let's use a disposable OrderBook for safety to not dirty the real book.
        // But we want to warm up the *MatchingEngine* methods too.

        // Actually, simplest is to create a throwaway instance of MatchingEngine +
        // OrderBook
        // invoke the methods heavily, then discard it.
        // The JVM optimizations apply to the *Code*, not the Instance.

        MatchingEngine warmEngine = new MatchingEngine(dummyListener);

        for (int i = 0; i < WARMUP_ITERATIONS; i++) {
            // Place Sell
            warmEngine.acceptOrder(-1, 100, 10, Side.SELL);
            // Place Buy (Match)
            warmEngine.acceptOrder(-2, 100, 10, Side.BUY);

            // Occasional Tree operations
            if (i % 100 == 0) {
                warmEngine.acceptOrder(-3, 50, 5, Side.BUY); // Resting
                warmEngine.acceptOrder(-4, 50, 5, Side.SELL); // Match Resting
            }
        }

        long end = System.nanoTime();
        System.out.println("WARMUP: Completed in " + (end - start) / 1_000_000 + "ms. JIT should be hot.");
    }
}

package com.nanosecond.core;

import com.nanosecond.api.OrderFlyweight;

/**
 * <h1>The Matching Engine: The "Hot Path" Processor</h1>
 * 
 * <p>
 * This class represents the core business logic of the exchange. It operates
 * under strict
 * HFT constraints to ensure deterministic, microsecond-level latency. It acts
 * as the
 * <b>State Machine</b> for the entire matching system.
 * </p>
 *
 * <h2>Design Rationality</h2>
 * 
 * <h3>1. Single-Threaded Execution (The "Single Writer")</h3>
 * <p>
 * Contrary to web servers dealing with request concurrency, an Exchange
 * Matching Engine
 * is often CPU-bound by memory access patterns, not compute.
 * </p>
 * <ul>
 * <li>
 * <b>The Cost of Locking:</b> A modern CPU core can execute ~4 billion
 * instructions/sec.
 * A single OS-level Context Switch (required for locks) costs ~2000 cycles
 * (~500ns).
 * Waiting for a lock is orders of magnitude slower than just doing the work.
 * </li>
 * <li>
 * <b>The Solution:</b> We serialize all inputs (New Order, Cancel, Replace)
 * into a single
 * queue (The Disruptor). This {@code MatchingEngine} runs on a dedicated,
 * pinned CPU core
 * that pulls from that queue. Because it has <b>exclusive access</b> to the
 * data,
 * no locks are required.
 * </li>
 * </ul>
 * 
 * <h3>2. Branch Prediction & Pipelining</h3>
 * <p>
 * The code path (`acceptOrder`) is kept as linear as possible. We avoid deep
 * inheritance
 * hierarchies (Virtual Method Dispatch tables hurt CPU prediction) and try to
 * keep common
 * paths (Order Acceptance) hot in the instruction cache.
 * </p>
 *
 * <h3>3. Determinism & Event Sourcing</h3>
 * <p>
 * Because the logic is single-threaded and the input stream is totally ordered,
 * the state
 * of the engine is a <b>pure function of the input stream</b>.
 * <br>
 * {@code State(T) = Apply(State(T-1), Event(T))}
 * <br>
 * This property allows us to recover from crashes by simply replaying the
 * journaled events
 * (via Chronicle Queue) from the start of the day, restoring the in-memory
 * state exactly.
 * </p>
 */
public class MatchingEngine {

    private final OrderBook orderBook;

    // We can pre-allocate pools here
    private final ObjectPool<Order> orderPool;
    private final ObjectPool<PriceLevel> priceLevelPool;

    private final MatchEventListener listener;

    public MatchingEngine(MatchEventListener listener) {
        this.listener = listener;
        // Capacity for pools - in a real system this would be configured
        this.orderPool = new ObjectPool<>(1024 * 1024, Order::new, Order::reset);
        this.priceLevelPool = new ObjectPool<>(1024, PriceLevel::new, PriceLevel::reset);
        this.orderBook = new OrderBook(priceLevelPool, orderPool);
    }

    /**
     * The core processor method.
     * <p>
     * <b>Logic Flow (The "Crossing the Spread" Algorithm):</b>
     * <ol>
     * <li><b>Match:</b> First, check if the incoming order crosses the spread
     * (matches existing liquidity).</li>
     * <li><b>Fill:</b> If matches occur, emit execution reports.</li>
     * <li><b>Rest:</b> If quantity remains, add the remainder to the book (unless
     * IOC/FOK).</li>
     * </ol>
     * </p>
     * 
     * @param id       The unique Order ID (from sequencer)
     * @param price    The limit price (fixed point, e.g., 10000 = $100.00)
     * @param quantity Number of lots
     * @param side     BUY or SELL
     */
    public void acceptOrder(long id, long price, long quantity, byte side) {
        // 1. Match against opposite side (Events emitted internally by OrderBook)
        // We pass the listener so OrderBook can report fills for BOTH Maker and Taker
        OrderBook.MatchResult result = orderBook.match(id, price, quantity, side, listener);

        // 2. If remainder, place in book
        long remaining = quantity - result.filledQty;
        if (remaining > 0) {
            // Note: addOrder will grab an Order object from the Pool.
            // This is "Zero Copy" from the network perspective, but "Zero Allocation"
            // from the Java heap perspective.
            orderBook.addOrder(id, price, remaining, side);

            // 3. Acknowledge the New Order
            listener.onOrderAccepted(id, price, remaining, side);
        }
    }

    /**
     * @return The OrderBook instance (for testing/inspection only)
     */
    public OrderBook getOrderBook() {
        return orderBook;
    }
}

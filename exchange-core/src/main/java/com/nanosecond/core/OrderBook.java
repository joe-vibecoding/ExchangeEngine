package com.nanosecond.core;

import com.nanosecond.api.Side;
import org.agrona.collections.Long2ObjectHashMap;

/**
 * <h1>The Order Book: Optimizing for the L1/L2 Cache</h1>
 * 
 * <p>
 * This class implements a Limit Order Book (LOB) optimized for <b>Spatial
 * Locality</b> and
 * <b>Zero Allocation</b>. It serves as the central data structure for the
 * exchange, managing
 * the state of all resting orders.
 * </p>
 *
 * <h2>Design Rationality</h2>
 * <p>
 * In High-Frequency Trading (HFT), the Order Book is the most contented
 * resource. Standard
 * Java collections (like {@code java.util.TreeMap}) differ from our
 * requirements in two
 * critical ways:
 * </p>
 * 
 * <ul>
 * <li>
 * <b>Memory Layout (Pointer Chasing):</b> Standard collections link objects via
 * pointers
 * scattered across the heap. Traversing them causes "Cache Misses" as the CPU
 * waits
 * ~100ns for data from RAM.
 * </li>
 * <li>
 * <b>Garbage Collection (GC):</b> Adding/Removing orders involves creating
 * `Node` objects.
 * This generates garbage, triggering "Stop-the-World" pauses.
 * </li>
 * </ul>
 *
 * <h2>Our Solution: Hybrid Data Structure</h2>
 * <p>
 * We employ a hybrid approach to get the best of both worlds (Speed and
 * Ordering):
 * </p>
 * 
 * <table border="1">
 * <tr>
 * <th>Component</th>
 * <th>Technology</th>
 * <th>Purpose (Time Complexity)</th>
 * </tr>
 * <tr>
 * <td><b>Lookup</b></td>
 * <td>{@link org.agrona.collections.Long2ObjectHashMap}</td>
 * <td><b>O(1)</b> access to any price level. Used for direct insertions and
 * cancellations.</td>
 * </tr>
 * <tr>
 * <td><b>Ordering</b></td>
 * <td>{@link RedBlackTree} (Intrusive)</td>
 * <td><b>O(log N)</b> ordered traversal. Used to efficiently find the "Next
 * Best Price"
 * when a level is fully consumed.</td>
 * </tr>
 * </table>
 *
 * <h2>Concurrency Model: Single Writer Principle</h2>
 * <p>
 * This class is <b>NOT Thread-Safe</b> by design. It constitutes the critical
 * section of the exchange.
 * </p>
 * <ul>
 * <li>
 * <b>Lock-Free:</b> We use NO locks (`synchronized`, `ReentrantLock`). Locking
 * requires
 * OS intervention (Context Switches), costing microseconds of latency and
 * introducing jitter.
 * </li>
 * <li>
 * <b>Serialized Access:</b> Only the single {@link MatchingEngine} thread
 * represents the
 * "Single Writer" and is allowed to mutate this book. All inputs are serialized
 * via the
 * Ring Buffer before reaching here.
 * </li>
 * </ul>
 */
public class OrderBook {

    private final Long2ObjectHashMap<PriceLevel> bids = new Long2ObjectHashMap<>();
    private final Long2ObjectHashMap<PriceLevel> asks = new Long2ObjectHashMap<>();

    private final ObjectPool<PriceLevel> priceLevelPool;
    private final ObjectPool<Order> orderPool;

    private final RedBlackTree bidTree = new RedBlackTree();
    private final RedBlackTree askTree = new RedBlackTree();

    public OrderBook(ObjectPool<PriceLevel> priceLevelPool, ObjectPool<Order> orderPool) {
        System.err.println("DEBUG: ORDER BOOK INIT");
        System.err.flush();
        this.priceLevelPool = priceLevelPool;
        this.orderPool = orderPool;
    }

    public void addOrder(long id, long price, long quantity, byte side) {
        Order order = orderPool.borrow();
        order.id = id;
        order.price = price;
        order.quantity = quantity;
        order.side = side;

        Long2ObjectHashMap<PriceLevel> sideMap = (side == Side.BUY) ? bids : asks;
        RedBlackTree sideTree = (side == Side.BUY) ? bidTree : askTree;

        PriceLevel level = sideMap.get(price);

        if (level == null) {
            level = priceLevelPool.borrow();
            level.reset();
            level.price = price;
            sideMap.put(price, level);
            sideTree.insert(level);
        }

        level.addOrder(order);
    }

    public MatchResult match(long inboundOrderId, long inboundPrice, long inboundQty, byte side,
            MatchEventListener listener) {
        long remainingQty = inboundQty;
        System.err.println("DEBUG: Matching " + (side == Side.BUY ? "BUY" : "SELL") + " " + inboundQty + " @ "
                + inboundPrice + " ID:" + inboundOrderId);
        System.err.flush();

        if (side == Side.BUY) { // Buy vs Asks
            while (remainingQty > 0) {
                // Find Best Ask (Min)
                // O(log N) - Efficient Red-Black Tree lookup
                PriceLevel bestLevel = askTree.getBestPrice(true);

                if (bestLevel == null) {
                    break;
                }
                if (bestLevel.price > inboundPrice) {
                    break;
                }

                remainingQty = matchLevel(bestLevel, remainingQty, asks, askTree, inboundOrderId, side, listener);
            }
        } else { // Sell vs Bids
            while (remainingQty > 0) {
                // Find Best Bid (Max)
                // O(log N) - Efficient Red-Black Tree lookup
                PriceLevel bestLevel = bidTree.getBestPrice(false);

                if (bestLevel == null) {
                    break;
                }

                // Logic Check: Sell Price 500. Best Bid 500.
                // 500 < 500 is FALSE. So we matched!
                // 500 < 501 is TRUE. So we stop if Bid is lower than limit.
                if (bestLevel.price < inboundPrice) {
                    break;
                }

                remainingQty = matchLevel(bestLevel, remainingQty, bids, bidTree, inboundOrderId, side, listener);
            }
        }

        return new MatchResult(inboundQty - remainingQty);
    }

    private long matchLevel(PriceLevel level, long quantity, Long2ObjectHashMap<PriceLevel> map, RedBlackTree tree,
            long inboundOrderId, byte inboundSide, MatchEventListener listener) {
        Order head = level.head;
        while (head != null && quantity > 0) {
            long tradeQty = Math.min(quantity, head.quantity);

            // EMIT FILLS (Maker & Taker)
            // 1. Maker (Passive)
            listener.onTrade(head.id, level.price, tradeQty, head.side);
            // 2. Taker (Aggressive)
            listener.onTrade(inboundOrderId, level.price, tradeQty, inboundSide);

            head.quantity -= tradeQty;
            quantity -= tradeQty;

            if (head.quantity == 0) {
                Order filled = head;
                head = head.next;
                level.removeOrder(filled);
                orderPool.returnObject(filled);
            }
        }

        if (level.isEmpty()) {
            map.remove(level.price);
            tree.remove(level); // Efficient O(log N) removal
            priceLevelPool.returnObject(level);
        }

        return quantity;
    }

    public static class MatchResult {
        public long filledQty;

        public MatchResult(long f) {
            filledQty = f;
        }
    }
}

# Architecture Whitepaper: The Nanosecond Exchange

**Version:** 1.4
**Date:** Jan 31, 2026

---

## 1. Executive Summary

The **Nanosecond Exchange** is a reference implementation of a low-latency Limit Order Book (LOB) matching engine. It challenges the common misconception that the Java Virtual Machine (JVM) is unsuitable for High-Frequency Trading (HFT) due to Garbage Collection (GC) pauses.

By adhering to principles of **Mechanical Sympathy** and utilizing **Zero-Allocation** patterns, this system achieves deterministic microsecond-scale execution, proving that Java can compete with C++ in the user-space HFT domain.

---

## 2. Design Philosophy & Engineering Rationales

### 2.1. Determinism over Throughput
**The Problem:** Traditional enterprise systems optimize for "Average Throughput" (e.g., serving 10,000 requests/sec). However, they tolerate occasional outliers using retries. In HFT, time is money. A single 5ms GC pause during a market crash allows competitors to pick off stale quotes.
**Our Choice:** We prioritize **Deterministic Tail Latency**.
*   **Trade-off:** We sacrifice some ease of development (no `new` keyword, no standard Collections) for absolute predictability in execution time.
*   **Implementation:** The hot path allocates zero bytes. No allocation means no GC work.

### 2.2. The Single Writer Principle
**The Problem:** Multi-threaded systems attempting to modify shared state (the Order Book) require **Locks** (`synchronized`) or **Atomic CAS** operations.
*   **Cost:** A lock acquisition involves the OS Kernel and can cost ~2000 CPU cycles. Even lock-free CAS (Compare-And-Swap) causes cache contention, where CPU cores fight for ownership of a cache line.
**Our Choice:** **Single-Threaded Pinned Execution**.
*   **Implementation:** All matching logic runs on one thread, pinned to one core. It has exclusive ownership of the L1/L2 Cache.
*   **Result:** Operations complete in nanoseconds because the data is always "hot" in the cache and no arbitration is needed.

---

## 3. System Components: An In-Depth Look

### 3.1. Messaging Layer: Aeron Transport
We utilize **Aeron** (UDP Unicast/IPC) instead of TCP or REST.
*   **Why UDP? (The Reliability Trap):** TCP guarantees order and delivery. If Packet 100 is lost, the OS pauses the stream to wait for retransmission, blocking Packet 101, 102, etc. This is **Head-of-Line Blocking**.
*   **Our Rationality:** In HFT, data expires instantly. If we miss a price update, we don't want to wait for it; we want the *next* price immediately. Aeron allows us to control this reliability window.
*   **Why IPC?** When Gateway and Engine run on the same machine, they share memory (`/dev/shm`). This allows passing messages with **Zero Copying** (just passing a pointer offset).

### 3.2. Concurrency: The LMAX Disruptor
The **Disruptor** is the engine's heartbeat. It replaces the standard `ArrayBlockingQueue`.
*   **False Sharing:** Standard queues often place the `head` and `tail` pointers on the same 64-byte Cache Line. When Thread A writes `head` and Thread B writes `tail`, the CPU cores constantly invalidate each other's cache.
*   **Our Rationality:** The Disruptor pads these variables, ensuring they sit on different cache lines. This hardware-aware optimization unlocks throughputs of 6M+ msg/sec.

### 3.3. Core Data Structure: The Intrusive Hybrid Book
The Order Book requires conflicting performance characteristics:
1.  **O(1)** Random Access (To Cancel an Order by ID).
2.  **O(log N)** Ordered Iteration (To Match Orders by Price Priority).

**The Naive Approach (TreeMap):**
*   Allocates a `Node` object for every entry -> **GC Pressure**.
*   Nodes are scattered in heap memory -> **Cache Misses**.

**Our Solution: Intrusive Red-Black Tree + FlatMap**
*   **Intrusiveness:** The `PriceLevel` object *is* the node. It contains `left`, `right`, and `parent` fields. We don't allocate a wrapper.
*   **Hybrid:**
    *   `Long2ObjectHashMap` (Agrona) gives **O(1)** access to the PriceLevel.
    *   `RedBlackTree` gives **O(log N)** next-best-price finding.
*   **Benefit:** We get the speed of a Hash Map for 90% of operations (Cancels/Inserts) and the ordering of a Tree only when needed (Matching).

---

## 4. Functional Specification: Matching Logic

This section details how the engine functionally handles the market, specifically focusing on **Price-Time Priority**.

### 4.1. Price-Time Priority
Markets must be fair. The engine guarantees that orders are filled in a strict sequence:
1.  **Best Price:** A Buyer always matches with the lowest Seller. (Price Priority).
2.  **Earliest Arrival:** If two Sellers offer the same price, the one who arrived first gets filled first. (Time Priority).

#### Implementation Mapping
*   **Price Priority (Tree):**
    *   The `RedBlackTree` ensures that we always retrieve the Min/Max price node in `O(log N)`.
    *   *Code Reference:* `OrderBook.match()` calls `askTree.getBestPrice(true)` (Min) or `bidTree.getBestPrice(false)` (Max).
*   **Time Priority (Queue):**
    *   Inside each `PriceLevel` object, there is a **Doubly Linked List** of orders `head` -> `tail`.
    *   New Limit Orders are appended to the `tail` (`PriceLevel.addOrder`).
    *   Matching consumes from the `head` (`PriceLevel.matchLevel`).
    *   *Result:* This circular buffer logic inherently guarantees FIFO (First-In, First-Out) behavior without needing a separate timestamp comparator.

### 4.2. Crossing the Spread
"Crossing the Spread" occurs when a Buyer sends a price `>=` the best available Sell price. The trade happens immediately ("Aggressive" or "Taker" order).

**The Algorithm (`MatchingEngine.acceptOrder`)**:
1.  **Check Liquidity:** The engine scans the opposite side of the book.
    *   *Example:* Incoming BUY @ 100. Best SELL is 99.
2.  **Iterate Levels:**
    *   It grabs the Best SELL (99).
    *   Since 99 <= 100, a match is valid.
3.  **Drain Logic:**
    *   It iterates the Linked List at Price 99, filling orders one by one.
    *   It generates `Trade` events for each fill.
    *   If the SELL level (99) is empty, it removes the level from the Tree and the Map.
    *   It then checks the *next* Best SELL (e.g., 99.50).
4.  **Residue (Resting):**
    *   If the incoming BUY order is not fully filled (e.g., it bought all 99s and 99.50s but still has quantity), and the next SELL is 101 (Price > Limit), the remainder is posted to the Book.
    *   It becomes a "Resting" (Maker) order.

### 4.3. Maker vs. Taker
*   **Taker (Aggressive):** The incoming order that "crosses the spread" and executes immediately. It removes liquidity.
*   **Maker (Passive):** The resting order that was already in the book. It provided liquidity.
*   **Implementation:**
    *   The `MatchingEngine` emits events distinguishing these roles. This is crucial for Exchange Fees (often Makers get a rebate, Takers pay a fee).

---

## 5. Memory Management Strategy

### 5.1. The Warmup Phase (JIT Compilation)
The JVM is a dynamic machine. It interprets bytecode before compiling it to native machine code (Assembly).
*   **Action:** Before the market opens, the engine runs a "Warmup" routine.
*   **Goal:** Trigger the C2 Compiler (Top Tier OSR) to compile the `MatchingEngine.process()` method into highly optimized Assembly instructions *before* real money is at risk.

### 5.2. Object Pooling
Dynamic allocation is strictly forbidden in the main loop.
*   **Order Pool:** A stack-based pool of `Order` objects.
*   **Lifecycle:**
    1.  `pool.borrow()`: Pops an existing object from the stack. ~5 nanoseconds.
    2.  Use object.
    3.  `pool.return()`: Pushes it back.
*   **Comparison:** `new Order()` takes ~15ns to allocate, but the hidden cost is the GC pause minutes later. Pooling pays the cost upfront.

---

## 6. Performance Characteristics & Benchmarks

**Architecture Constraints:**
*   **Heap Size:** Fixed (e.g., 2GB) to prevent resizing.
*   **GC Policy:** SerialGC (or EpsilonGC). Since we don't generate garbage, the simplest GC is the best.

**Latency Profile (Estimated on Modern i9/Ryzen):**
*   **Wire-to-Wire:** < 50µs (internal processing time).
*   **Matching Logic:** < 200ns per order.
*   **Jitter (p99):** < 10µs deviation (attributable to OS interrupts).

---

## 7. Conclusion

The Nanosecond Exchange is not just a trading engine; it is a case study in **Hardware-Oriented Software Design**. By stripping away the comfortable abstractions of Enterprise Java and working with the raw mechanics of Memory, Caches, and CPU piplines, we achieve necessary performance for the modern financial ecosystem.

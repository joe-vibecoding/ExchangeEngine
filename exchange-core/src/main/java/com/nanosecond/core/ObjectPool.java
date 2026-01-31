package com.nanosecond.core;

import com.nanosecond.api.OrderFlyweight;

import java.util.function.Consumer;

/**
 * <h1>Object Pool: The Antidote to Garbage Collection</h1>
 * 
 * <p>
 * A generic, high-performance Object Pool designed to eliminate Java Garbage
 * Collection (GC)
 * pauses in the critical path.
 * </p>
 *
 * <h2>The "GC Problem" in HFT</h2>
 * <p>
 * In a standard Java application, object lifecycles are managed by the JVM:
 * <br>
 * {@code new Object() -> Usage -> Garbage -> Stop-the-World Collection}
 * <br>
 * For a Trading Engine processing 100,000 orders/sec, creating a
 * {@code new Order()} object
 * for every message would generate megabytes of garbage per second. Eventually,
 * the GC
 * pauses the application (for 1ms - 100ms) to clean up.
 * <br>
 * <b>In HFT, a 1ms pause means the market moves against you. You lose
 * money.</b>
 * </p>
 *
 * <h2>The Solution: Pooling</h2>
 * <p>
 * Instead of allocating and de-allocating, we pre-allocate a large array of
 * objects at
 * startup.
 * </p>
 * <ul>
 * <li><b>Zero GC (This Class):</b> The worker has a tool belt (this Pool). They
 * take a screwdriver, use it,
 * and put it back. The floor remains clean. The line never stops.</li>
 * </ul>
 * <p>
 * <b>Why not {@code java.util.concurrent.ConcurrentLinkedQueue}?</b>
 * <br>
 * Standard concurrent queues use CAS (Compare-And-Swap) operations which are
 * expensive (CPU pipeline flushes).
 * Since our architecture follows the <b>Single Writer Principle</b>, we are
 * guaranteed that only ONE thread
 * accesses this pool at a time (within the Matching Engine).
 * <p>
 * Therefore, we can use a simple array with a strictly monotonic pointer
 * (`head`), which compiles down
 * to simple register increments/decrements. This is <b>nanosecond-level</b>
 * efficiency.
 *
 * @param <T> The type of object to pool.
 */
public class ObjectPool<T> {

    private final T[] pool;
    private int head;
    private final Consumer<T> resetter;

    @SuppressWarnings("unchecked")
    public ObjectPool(int capacity, java.util.function.Supplier<T> factory, Consumer<T> resetter) {
        this.resetter = resetter;
        this.pool = (T[]) new Object[capacity];
        this.head = capacity - 1;

        // Pre-allocate everything
        for (int i = 0; i < capacity; i++) {
            pool[i] = factory.get();
        }
    }

    public T borrow() {
        if (head < 0) {
            throw new RuntimeException("Pool exhausted! Size: " + pool.length);
        }
        return pool[head--];
    }

    public void returnObject(T object) {
        if (object == null) {
            return;
        }
        resetter.accept(object);
        if (head + 1 >= pool.length) {
            // Should not happen in a correctly sized closed system
            throw new IllegalStateException("Pool overflow!");
        }
        pool[++head] = object;
    }

    public int available() {
        return head + 1;
    }
}

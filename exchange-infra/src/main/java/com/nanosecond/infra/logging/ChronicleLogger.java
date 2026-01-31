package com.nanosecond.infra.logging;

import net.openhft.chronicle.queue.ChronicleQueue;
import net.openhft.chronicle.queue.ExcerptAppender;
import net.openhft.chronicle.queue.impl.single.SingleChronicleQueueBuilder;

/**
 * <b>Chronicle Logger Implementation.</b>
 * <p>
 * Writes logs to a memory-mapped file using Chronicle Queue.
 * This is "Zero Copy" (mostly) and "Zero GC".
 * </p>
 * <p>
 * <b>Why not Log4j2 Async?</b>
 * Log4j2 is great but still deals with Strings and formatting objects.
 * Chronicle Queue allows us to write raw bytes.
 * </p>
 */
public class ChronicleLogger implements Logger {

    private final ChronicleQueue queue;
    private final ExcerptAppender appender;

    public ChronicleLogger(String path) {
        this.queue = SingleChronicleQueueBuilder.binary(path).build();
        this.appender = queue.acquireAppender();
    }

    @Override
    public void log(CharSequence message) {
        // Writes: [Length][String Bytes]
        appender.writeDocument(w -> w.write("msg").text(message));
    }

    @Override
    public void log(long value) {
        appender.writeDocument(w -> w.write("val").int64(value));
    }

    @Override
    public void log(CharSequence message, long value) {
        appender.writeDocument(w -> w.write("evt").text(message)
                .write("val").int64(value));
    }

    public void close() {
        queue.close();
    }
}

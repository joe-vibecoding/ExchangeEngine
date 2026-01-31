#!/bin/bash
set -e

# 1. Environment Setup
# Use /tmp to avoid permission issues in target/
AERON_DIR="/tmp/aeron-$(whoami)-profile"
LOG_DIR="/tmp/exchange-logs-profile"

echo "Cleaning up..."
rm -rf "$AERON_DIR"
rm -rf "$LOG_DIR"
mkdir -p "$LOG_DIR"

# 2. JVM Flags for Profiling
# -XX:+UseSerialGC: Simple GC. If we allocate, it WILL pause.
# -Xmx256M: Small heap. If we leak/churn, we will GC frequently or OOM.
# -Xlog:gc*: Print all GC events.
JVM_FLAGS="--add-opens java.base/sun.nio.ch=ALL-UNNAMED \
--add-opens java.base/java.nio=ALL-UNNAMED \
--add-opens java.base/jdk.internal.misc=ALL-UNNAMED \
--add-opens java.base/java.lang.reflect=ALL-UNNAMED \
--add-exports java.base/jdk.internal.ref=ALL-UNNAMED \
-XX:+UnlockExperimentalVMOptions \
-XX:+UseSerialGC \
-Xmx256M -Xms256M \
-Xlog:gc*:file=$LOG_DIR/gc.log:time,uptime,level:filecount=5,filesize=10M \
-Daeron.dir=$AERON_DIR"

echo "----------------------------------------------------------------"
echo "Starting Exchange Server (Profiling Mode)..."
echo "Heap: 256MB"
echo "GC Log: $LOG_DIR/gc.log"
echo "----------------------------------------------------------------"

# Cleanup function
cleanup() {
    echo "Stopping processes..."
    pkill -f com.nanosecond.infra.ExchangeServer || true
    echo "Done."
    
    echo "----------------------------------------------------------------"
    echo "GC LOG ANALYSIS"
    echo "----------------------------------------------------------------"
    if [ -f "$LOG_DIR/gc.log" ]; then
        grep "Pause" "$LOG_DIR/gc.log" || echo "SUCCESS: No GC Pauses detected!"
    else
        echo "WARNING: No GC log found."
    fi
    echo "----------------------------------------------------------------"
}
trap cleanup EXIT

# 3. Start Server
java $JVM_FLAGS -cp exchange-infra/target/exchange-infra-1.0-SNAPSHOT.jar:exchange-core/target/exchange-core-1.0-SNAPSHOT.jar:exchange-infra/target/lib/* \
    com.nanosecond.infra.ExchangeServer > "$LOG_DIR/server.out" 2>&1 &
SERVER_PID=$!

echo "Server PID: $SERVER_PID"
echo "Waiting 5s for warmup..."
sleep 5

echo "----------------------------------------------------------------"
echo "Starting Load Generator (30 seconds)..."
echo "----------------------------------------------------------------"

# 4. Start Load Generator
# Generate 1M messages/sec to stress memory
java $JVM_FLAGS -cp exchange-infra/target/exchange-infra-1.0-SNAPSHOT.jar:exchange-core/target/exchange-core-1.0-SNAPSHOT.jar:exchange-infra/target/lib/* \
    com.nanosecond.infra.benchmark.LoadGenerator 100000 30 > "$LOG_DIR/loadgen.out" 2>&1

echo "Load Test Complete."

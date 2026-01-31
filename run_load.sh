#!/bin/bash
# ==============================================================================
# Helper Script: Run Load Generator Manually
# Usage: ./run_load.sh [count]
# Example: ./run_load.sh        (Defaults to 100,000 orders)
#          ./run_load.sh 5000   (Runs 5,000 orders)
# ==============================================================================

COUNT=${1:-100000}
AERON_DIR="/tmp/aeron-$(whoami)-exchange"

echo "Starting Load Generator ($COUNT orders)..."
echo "Connecting to Aeron Dir: $AERON_DIR"

# Match JVM Flags from run_exchange.sh
java \
    --add-opens java.base/sun.nio.ch=ALL-UNNAMED \
    --add-opens java.base/java.nio=ALL-UNNAMED \
    --add-opens java.base/jdk.internal.misc=ALL-UNNAMED \
    --add-opens java.base/java.lang.reflect=ALL-UNNAMED \
    --add-exports java.base/jdk.internal.ref=ALL-UNNAMED \
    -XX:+UnlockExperimentalVMOptions \
    -XX:+UseEpsilonGC \
    -XX:+AlwaysPreTouch \
    -Xmx1G -Xms1G \
    -Daeron.dir="$AERON_DIR" \
    -cp "exchange-infra/target/exchange-infra-1.0-SNAPSHOT.jar:exchange-infra/target/dependency/*" \
    com.nanosecond.infra.benchmark.LoadGenerator "$COUNT" --demo

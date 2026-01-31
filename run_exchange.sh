#!/bin/bash
set -e

# ==============================================================================
# NANOSECOND EXCHANGE - UNIFIED LAUNCHER
# ==============================================================================
# Usage:
#   ./run_exchange.sh [options]
#
# Options:
#   --skip-build    Skip Maven build (fast start)
#   --demo          Run Load Generator in "Demo Mode" (1000 orders, 10ms delay)
#   --load <N>      Run Load Generator with N orders immediately
#   --help          Show this help
# ==============================================================================

SKIP_BUILD=false
DEMO_MODE=false
LOAD_COUNT=0

# Parse Arguments
while [[ "$#" -gt 0 ]]; do
    case $1 in
        --skip-build) SKIP_BUILD=true ;;
        --demo) DEMO_MODE=true ;;
        --load) LOAD_COUNT="$2"; shift ;;
        --help)
            echo "Usage: ./run_exchange.sh [--skip-build] [--demo] [--load <count>]"
            exit 0
            ;;
        *) echo "Unknown parameter: $1"; exit 1 ;;
    esac
    shift
done

# Cleanup Trap
cleanup() {
    echo ""
    echo "=================================================="
    echo "   SHUTTING DOWN EXCHANGE ENVIRONMENT"
    echo "=================================================="
    pkill -f com.nanosecond.infra.ExchangeServer || true
    pkill -f com.nanosecond.gateway.GatewayServer || true
    pkill -f "vite" || true
    echo "Done."
    exit 0
}
trap cleanup EXIT INT TERM

echo "=================================================="
echo "   STARTING NANOSECOND EXCHANGE"
echo "=================================================="

# 1. Build
if [ "$SKIP_BUILD" = false ]; then
    echo "[1/4] Building Project..."
    mvn clean package dependency:copy-dependencies -DskipTests -q
    echo "Build Complete."
else
    echo "[1/4] Skipping Build..."
fi

# 2. Environment Setup
AERON_DIR="/tmp/aeron-$(whoami)-exchange"
LOG_DIR="/tmp/exchange-logs"

echo "Cleaning up shared memory..."
rm -rf "$AERON_DIR"
mkdir -p "$LOG_DIR"

# JVM Flags (Java 17+ & EpsilonGC Support)
JVM_FLAGS="--add-opens java.base/sun.nio.ch=ALL-UNNAMED \
--add-opens java.base/java.nio=ALL-UNNAMED \
--add-opens java.base/jdk.internal.misc=ALL-UNNAMED \
--add-opens java.base/java.lang.reflect=ALL-UNNAMED \
--add-exports java.base/jdk.internal.ref=ALL-UNNAMED \
-XX:+UnlockExperimentalVMOptions \
-XX:+UseEpsilonGC \
-XX:+AlwaysPreTouch \
-Xmx2G -Xms2G \
-Daeron.dir=$AERON_DIR"

# 2. Start Backend (Core Engine)
echo "[2/4] Starting Exchange Engine..."
nohup java $JVM_FLAGS -cp "exchange-infra/target/exchange-infra-1.0-SNAPSHOT.jar:exchange-infra/target/dependency/*" \
    com.nanosecond.infra.ExchangeServer > "$LOG_DIR/server.log" 2>&1 &
SERVER_PID=$!
echo "Exchange Server PID: $SERVER_PID"
sleep 5 # Warmup

# 3. Start Gateway
echo "[3/4] Starting Gateway..."
nohup java $JVM_FLAGS -cp "exchange-gateway/target/exchange-gateway-1.0-SNAPSHOT.jar:exchange-gateway/target/dependency/*" \
    com.nanosecond.gateway.GatewayServer > "$LOG_DIR/gateway.log" 2>&1 &
GATEWAY_PID=$!
echo "Gateway Server PID: $GATEWAY_PID"
sleep 2

# 4. Start Frontend
echo "[4/4] Starting Web UI..."
cd exchange-web
nohup npm run dev > "$LOG_DIR/web.log" 2>&1 &
WEB_PID=$!
cd ..
echo "Web UI PID: $WEB_PID"
echo "URL: http://localhost:5173"

echo "=================================================="
echo "   SYSTEM READY"
echo "=================================================="
echo "Press Ctrl+C to exit."

# Run Load Generator (Demo or Load)
if [ "$DEMO_MODE" = true ]; then
    echo "Starting Demo Load Generator..."
    java $JVM_FLAGS -cp "exchange-infra/target/exchange-infra-1.0-SNAPSHOT.jar:exchange-infra/target/dependency/*" \
         com.nanosecond.infra.benchmark.LoadGenerator 100000 --demo &
    LOAD_GEN_PID=$!
    echo "Load Generator PID: $LOAD_GEN_PID"
elif [ "$LOAD_COUNT" -gt 0 ]; then
    echo "Starting Load Generator with $LOAD_COUNT orders..."
    java $JVM_FLAGS -cp "exchange-infra/target/exchange-infra-1.0-SNAPSHOT.jar:exchange-infra/target/dependency/*" \
         com.nanosecond.infra.benchmark.LoadGenerator "$LOAD_COUNT" &
    LOAD_GEN_PID=$!
    echo "Load Generator PID: $LOAD_GEN_PID"
fi

# Keep alive
wait

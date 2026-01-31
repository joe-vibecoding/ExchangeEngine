#!/bin/bash
# run_integration_stress.sh
# Orchestrates the "Price Partitioned" Integration Test

echo "=================================================="
echo "   INTEGRATION STRESS TEST SUITE"
echo "=================================================="

# 1. Prepare Dependencies
echo "[1/5] Using Pre-built Dependencies..."
# mvn dependency:copy-dependencies -pl exchange-infra -DincludeScope=test -q
# mvn dependency:copy-dependencies -pl exchange-gateway -q

# Construct Classpaths
# Note: * wildcard is expanded by the shell or java if quoted correcty. 
# We use quotes in the java command to let Java expand checks, OR we rely on shell.
# Safest is usually 'target/dependency/*' in quotes for java to handle on Linux/Mac.

CP_INFRA="exchange-infra/target/classes:exchange-infra/target/test-classes:exchange-core/target/classes:exchange-api/target/classes:exchange-infra/target/dependency/*"
CP_GATEWAY="exchange-gateway/target/classes:exchange-api/target/classes:exchange-gateway/target/dependency/*"

# JVM Flags for Aeron/Agrona
# Force custom Aeron dir to avoid permission issues in /var/folders
AERON_DIR="/tmp/aeron-$(whoami)"
rm -rf "$AERON_DIR"
mkdir -p "$AERON_DIR"
rm -rf exchange-logs

JVM_FLAGS="--add-opens java.base/sun.nio.ch=ALL-UNNAMED \
--add-opens java.base/java.nio=ALL-UNNAMED \
--add-opens java.base/jdk.internal.misc=ALL-UNNAMED \
--add-opens java.base/java.lang.reflect=ALL-UNNAMED \
--add-exports java.base/jdk.internal.ref=ALL-UNNAMED \
-XX:+UnlockExperimentalVMOptions \
-XX:+UseEpsilonGC \
-XX:+AlwaysPreTouch \
-Xmx1G -Xms1G \
-Daeron.dir=$AERON_DIR"

# 2. Start Exchange (Background)
echo "[2/5] Starting Exchange Server..."
# Direct output to stdout for command_status capture. No file, no grep.
java $JVM_FLAGS -cp "$CP_INFRA" \
    com.nanosecond.infra.ExchangeServer 2>&1 &
EXCHANGE_PID=$!
echo "Exchange PID: $EXCHANGE_PID"

# Wait for Ready
echo "Waiting for Exchange (10s)..."
sleep 10

# Start Gateway (Required for WebSocket)
echo "Starting Gateway..."
java $JVM_FLAGS -cp "$CP_GATEWAY" \
    com.nanosecond.gateway.GatewayServer 2>&1 &
GATEWAY_PID=$!
echo "Gateway PID: $GATEWAY_PID"
sleep 10

# 3. Start Load Generator (Background - NOISE)
echo "[3/5] Starting Load Generator (Noise @ \$100)..."
# Silence load generator noise, only show errors
java $JVM_FLAGS -cp "$CP_INFRA" \
    com.nanosecond.infra.benchmark.LoadGenerator \
    100000 --demo > /dev/null 2>&1 &
LOADGEN_PID=$!

# 4. Run Verification Bot (Foreground - SIGNAL)
echo "[4/5] Running Verification Bot (Signal @ \$500)..."
java -cp "$CP_INFRA" \
    com.nanosecond.infra.integration.VerificationBot

BOT_EXIT_CODE=$?

# 5. Cleanup and Report
echo "[5/5] Cleanup..."
kill $LOADGEN_PID 2>/dev/null
kill $GATEWAY_PID 2>/dev/null
kill $EXCHANGE_PID 2>/dev/null
# Hard kill just in case
pkill -f com.nanosecond.infra.ExchangeServer || true
pkill -f com.nanosecond.gateway.GatewayServer || true
pkill -f com.nanosecond.infra.benchmark.LoadGenerator || true

echo "--------------------------------------------------"
if [ $BOT_EXIT_CODE -eq 0 ]; then
    echo "TEST RESULT: PASSED"
else
    echo "TEST RESULT: FAILED (Exit Code: $BOT_EXIT_CODE)"
fi
echo "=================================================="
exit $BOT_EXIT_CODE

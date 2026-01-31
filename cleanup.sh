#!/bin/bash
# ==============================================================================
# SURGICAL CLEANUP SCRIPT
# Safely terminates Exchange Application processes without killing IDE support.
# ==============================================================================

echo "Performing surgical cleanup..."

# 1. Kill Exchange Java Components (Server, Gateway, LoadGen)
# We match "com.nanosecond" to avoid killing the IDE's Language Server.
if pkill -f "com.nanosecond"; then
    echo "✅ Terminated Exchange Java processes."
else
    echo "ℹ️  No Exchange Java processes found."
fi

# 2. Kill Frontend (Vite)
if pkill -f "vite"; then
    echo "✅ Terminated Vite (Frontend)."
else
    echo "ℹ️  No Vite process found."
fi

# 3. Clean Temporary Files & Shared Memory
rm -rf /tmp/exchange-logs
rm -rf /tmp/aeron-*
echo "✅ Cleaned logs and Aeron IPC files."

echo "--------------------------------------------------"
echo "Cleanup Complete. You can now run ./run_exchange.sh"
echo "--------------------------------------------------"

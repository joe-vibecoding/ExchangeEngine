package com.nanosecond.gateway;

import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListMap;

public class GatewayOrderBook {

    // Price -> Quantity (L2)
    // Use ConcurrentSkipListMap for thread safety and sorting
    private final ConcurrentSkipListMap<Long, Long> bids = new ConcurrentSkipListMap<>(Collections.reverseOrder());
    private final ConcurrentSkipListMap<Long, Long> asks = new ConcurrentSkipListMap<>();

    public void onOrderAccepted(long price, long qty, byte side) {
        // Add liquidity
        getSideMap(side).merge(price, qty, Long::sum);
    }

    public void onExecution(long price, long filledQty, byte side) {
        byte liquiditySide = (byte) (side == 0 ? 1 : 0);
        getSideMap(liquiditySide).compute(price, (k, currentQty) -> {
            if (currentQty == null)
                return null;
            long newQty = currentQty - filledQty;
            return newQty > 0 ? newQty : null;
        });
    }

    private Map<Long, Long> getSideMap(byte side) {
        return side == 0 ? bids : asks;
    }

    public String getSnapshotJson() {
        // Naive JSON construction for demo
        StringBuilder sb = new StringBuilder();
        sb.append("{\"type\":\"SNAPSHOT\", \"bids\":[");
        appendLevels(sb, bids);
        sb.append("], \"asks\":[");
        appendLevels(sb, asks);
        sb.append("]}");
        return sb.toString();
    }

    private void appendLevels(StringBuilder sb, Map<Long, Long> levels) {
        boolean first = true;
        // Limit to top 10 for snapshot
        int count = 0;
        for (Map.Entry<Long, Long> entry : levels.entrySet()) {
            if (count++ >= 10)
                break;
            if (!first)
                sb.append(",");
            sb.append("{\"price\":").append(entry.getKey() / 100.0)
                    .append(",\"qty\":").append(entry.getValue()).append("}");
            first = false;
        }
    }
}

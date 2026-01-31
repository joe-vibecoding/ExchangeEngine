package com.nanosecond.infra;

import com.nanosecond.core.MatchEventListener;

public class AeronMatchEventListener implements MatchEventListener {

    private final AeronEgress egress;

    public AeronMatchEventListener(AeronEgress egress) {
        this.egress = egress;
    }

    @Override
    public void onTrade(long orderId, long price, long qty, byte side) {
        if (egress != null) {
            egress.sendExecutionReport(orderId, qty, price, (byte) 1, side);
        }
    }

    @Override
    public void onOrderAccepted(long orderId, long price, long qty, byte side) {
        if (egress != null) {
            egress.sendOrderAccepted(orderId, qty, price, side);
        }
    }

    @Override
    public void onOrderRejected(long orderId, long price, long qty, byte side, String reason) {
        // Not implemented on egress yet
    }
}

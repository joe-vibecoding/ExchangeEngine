package com.nanosecond.core;

public interface MatchEventListener {
    void onTrade(long orderId, long price, long qty, byte side);

    void onOrderAccepted(long orderId, long price, long qty, byte side);

    void onOrderRejected(long orderId, long price, long qty, byte side, String reason);
}

package com.nanosecond.infra;

import com.lmax.disruptor.EventFactory;

/**
 * Event wrapper for match results/executions in the Output Ring Buffer.
 */
public class MatchEvent {
    public long orderId;
    public long price;
    public long filledQuantity;
    public byte status; // 0=New, 1=Partial, 2=Filled

    public void reset() {
        orderId = 0;
        price = 0;
        filledQuantity = 0;
        status = 0;
    }

    public final static EventFactory<MatchEvent> FACTORY = MatchEvent::new;
}

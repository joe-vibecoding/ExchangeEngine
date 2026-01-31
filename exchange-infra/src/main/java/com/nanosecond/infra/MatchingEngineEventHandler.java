package com.nanosecond.infra;

import com.lmax.disruptor.EventHandler;
import com.nanosecond.core.MatchingEngine;

/**
 * The Consumer in the Disruptor pattern.
 * Takes OrderCommands from the RingBuffer and feeds them to the Matching
 * Engine.
 */
public class MatchingEngineEventHandler implements EventHandler<OrderCommand> {

    private final MatchingEngine matchingEngine;

    public MatchingEngineEventHandler(MatchingEngine matchingEngine) {
        this.matchingEngine = matchingEngine;
    }

    @Override
    public void onEvent(OrderCommand event, long sequence, boolean endOfBatch) {
        // System.out.println("Processing Order: " + event.id);
        matchingEngine.acceptOrder(event.id, event.price, event.quantity, event.side);
    }
}

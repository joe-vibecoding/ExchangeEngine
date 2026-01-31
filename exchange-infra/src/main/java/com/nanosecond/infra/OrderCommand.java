package com.nanosecond.infra;

import com.lmax.disruptor.EventFactory;

/**
 * Event wrapper for incoming orders in the Ring Buffer.
 */
public class OrderCommand {
    public long id;
    public long price;
    public long quantity;
    public byte side;

    public void reset() {
        id = 0;
        price = 0;
        quantity = 0;
        side = 0;
    }

    public final static EventFactory<OrderCommand> FACTORY = OrderCommand::new;
}

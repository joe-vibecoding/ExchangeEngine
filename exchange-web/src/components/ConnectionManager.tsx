import { useEffect } from 'react';
import { useExchangeStore } from '../store';

const GATEWAY_URL = 'ws://localhost:8080/ws';

let socket: WebSocket | null = null;

export const sendOrder = (order: any) => {
    if (socket && socket.readyState === WebSocket.OPEN) {
        socket.send(JSON.stringify(order));
    } else {
        console.warn('Cannot send order: Disconnected');
    }
};

export const ConnectionManager = () => {
    const setConnected = useExchangeStore((state: any) => state.setConnected);

    useEffect(() => {
        let active = true;

        const connect = () => {
            if (!active) return;
            console.log('Connecting to Exchange Gateway...');
            const ws = new WebSocket(GATEWAY_URL);
            socket = ws;

            ws.onopen = () => {
                if (!active) {
                    ws.close();
                    return;
                }
                console.log('Connected!');
                setConnected(true);
            };

            ws.onclose = () => {
                console.log('Disconnected.');
                // Only clear global socket if it is THIS socket
                if (socket === ws) {
                    setConnected(false);
                    socket = null;
                }
                if (active) {
                    console.log('Retrying in 3s...');
                    setTimeout(connect, 3000); // Auto-reconnect only if still mounted
                }
            };

            ws.onmessage = (event) => {
                if (!active) return;
                try {
                    const msg = JSON.parse(event.data);
                    const store = useExchangeStore.getState();

                    if (msg.type === 'SNAPSHOT') {
                        const mapLevels = (levels: any[]) => levels.map((l: any) => ({ ...l, count: 1 }));
                        store.updateBook(mapLevels(msg.bids), mapLevels(msg.asks));

                    } else if (msg.type === 'ORDER_ACCEPTED') {
                        // {"type": "ORDER_ACCEPTED", "orderId": ..., "qty": ..., "price": ..., "side": 0/1, "status": "NEW"}
                        // 0=BUY, 1=SELL
                        const sideStr = msg.side === 0 ? 'BUY' : 'SELL';

                        // 1. Add to Active Orders (ONLY IF IT'S MINE)
                        // Heuristic: User Orders are < 10,000,000 (from Gateway)
                        // Simulation Orders are > 10,000,000 (from LoadGenerator)
                        if (msg.orderId < 10000000) {
                            store.addOrder({
                                id: msg.orderId,
                                symbol: 'BTC/USD',
                                side: sideStr,
                                price: msg.price,
                                qty: msg.qty,
                                status: 'NEW'
                            });
                        }

                        // 2. Add Liquidity to Book (Always, market data is public)
                        store.addLiquidity(msg.price, msg.qty, sideStr);


                    } else if (msg.type === 'EXECUTION') {
                        // Backend sent: {"type": "EXECUTION", "orderId": ..., "qty": ..., "price": ..., "side": ..., "status": "FILLED"}

                        if (msg.status === 'FILLED') {
                            const aggressorSide = msg.side === 0 ? 'BUY' : 'SELL';

                            // 1. Add to Trade History
                            store.addTrade({
                                id: msg.orderId,
                                price: msg.price,
                                qty: msg.qty,
                                side: aggressorSide,
                                timestamp: Date.now()
                            });

                            // 2. Update Active Order (if exists - e.g. the aggressor)
                            // If it DOESN'T exist in activeOrders, it was an Immediate Fill (never Resting).
                            // We need to record it so "YOU" badge works.
                            const orderExists = store.activeOrders.some(o => o.id === msg.orderId);
                            if (orderExists) {
                                store.updateOrder(msg.orderId, 'FILLED', msg.qty);
                            } else if (msg.orderId < 10000000) {
                                // Immediate Fill! (And it's MINE because ID < 10M)
                                store.logImmediateFill({
                                    id: msg.orderId,
                                    symbol: 'BTC/USD',
                                    side: aggressorSide,
                                    price: msg.price,
                                    qty: msg.qty,
                                    status: 'FILLED'
                                });
                            }


                            // 3. Remove Liquidity from OPPOSITE side
                            const passiveSide = aggressorSide === 'BUY' ? 'SELL' : 'BUY';
                            store.removeLiquidity(msg.price, msg.qty, passiveSide);

                        } else if (msg.status === 'REJECTED') {
                            console.warn("Order Rejected:", msg.reason);
                        }
                    } else if (msg.status === 'SENT') {
                        // This is the local ACK that we sent an order.
                        // Use this to track "My Orders" that haven't been accepted yet?
                        // For now, we trust ORDER_ACCEPTED or EXECUTION follows.
                    }
                } catch (e) {
                    console.error('Parse error:', e);
                }
            };
        };

        if (!socket) {
            connect();
        }

        return () => {
            active = false;
            if (socket) {
                socket.close();
                socket = null;
            }
        };
    }, [setConnected]);

    return null; // Headless component
};

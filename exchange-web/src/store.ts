import { create } from 'zustand';

export interface OrderBookLevel {
    price: number;
    qty: number;
    count: number;
}

export interface Trade {
    id: number;
    price: number;
    qty: number;
    side: 'BUY' | 'SELL';
    timestamp: number;
}

export interface Order {
    id: number;
    symbol: string;
    side: 'BUY' | 'SELL';
    price: number;
    qty: number;
    status: 'NEW' | 'PARTIAL' | 'FILLED' | 'CANCELED';
}

interface ExchangeState {
    // Connection Status
    isConnected: boolean;
    latency: number;
    setConnected: (status: boolean) => void;
    setLatency: (ms: number) => void;

    // Market Data
    bids: OrderBookLevel[];
    asks: OrderBookLevel[];
    trades: Trade[];

    // Actions
    updateBook: (bids: OrderBookLevel[], asks: OrderBookLevel[]) => void;
    addTrade: (trade: Trade) => void;

    // User Data
    activeOrders: Order[];
    filledOrders: Order[]; // Keep track of history
    addOrder: (order: Order) => void;
    updateOrder: (id: number, status: Order['status'], filledQty?: number) => void;
    // Helper to log a filled order that was never active (Immediate Fill)
    logImmediateFill: (order: Order) => void;

    addLiquidity: (price: number, qty: number, side: 'BUY' | 'SELL') => void;
    removeLiquidity: (price: number, qty: number, side: 'BUY' | 'SELL') => void;
}

export const useExchangeStore = create<ExchangeState>((set) => ({
    isConnected: false,
    latency: 0,
    setConnected: (isConnected: boolean) => set({ isConnected }),
    setLatency: (latency: number) => set({ latency }),

    bids: [],
    asks: [],
    trades: [],

    updateBook: (bids: OrderBookLevel[], asks: OrderBookLevel[]) => set({ bids, asks }),
    addTrade: (trade: Trade) => set((state: ExchangeState) => {
        // Simple deduplication - don't add if identical ID exists
        if (state.trades.some(t => t.id === trade.id)) return { trades: state.trades };
        return {
            trades: [trade, ...state.trades].slice(0, 50)
        };
    }),

    activeOrders: [],
    filledOrders: [],

    addOrder: (order: Order) => set((state: ExchangeState) => {
        if (state.activeOrders.some(o => o.id === order.id)) return { activeOrders: state.activeOrders };
        return {
            activeOrders: [order, ...state.activeOrders].slice(0, 50)
        };
    }),

    updateOrder: (id: number, status: Order['status'], filledQty?: number) => set((state: ExchangeState) => {
        const isTerminal = status === 'FILLED' || status === 'CANCELED';

        // Find the order
        const order = state.activeOrders.find(o => o.id === id);

        let newActive = state.activeOrders;
        let newFilled = state.filledOrders;

        if (order) {
            const updatedOrder = { ...order, status, qty: filledQty ? order.qty - filledQty : order.qty };

            if (isTerminal) {
                // Remove from Active, Add to Filled
                newActive = state.activeOrders.filter(o => o.id !== id);
                // If it's filled, track it (keep last 50)
                if (status === 'FILLED') {
                    newFilled = [updatedOrder, ...state.filledOrders].slice(0, 50);
                }
            } else {
                // Just update in place
                newActive = state.activeOrders.map(o => o.id === id ? updatedOrder : o);
            }
        }

        return { activeOrders: newActive, filledOrders: newFilled };
    }),

    logImmediateFill: (order: Order) => set((state: ExchangeState) => ({
        filledOrders: [order, ...state.filledOrders].slice(0, 50)
    })),


    // --- Book Mutation Helpers ---
    addLiquidity: (price: number, qty: number, side: 'BUY' | 'SELL') => set((state: ExchangeState) => {
        const list = side === 'BUY' ? [...state.bids] : [...state.asks];
        const existing = list.find(l => l.price === price);
        if (existing) {
            existing.qty += qty;
            existing.count += 1;
        } else {
            list.push({ price, qty, count: 1 });
            // Sort: Bids Descending, Asks Ascending
            if (side === 'BUY') list.sort((a, b) => b.price - a.price);
            else list.sort((a, b) => a.price - b.price);
        }
        return side === 'BUY' ? { bids: list } : { asks: list };
    }),

    removeLiquidity: (price: number, qty: number, side: 'BUY' | 'SELL') => set((state: ExchangeState) => {
        // Note: Execution (Trade) reduces liquidity.
        // If Side=BUY (Aggressor), it consumes from ASKS (Side=SELL).
        // If Side=SELL (Aggressor), it consumes from BIDS (Side=BUY).

        // Wait, the callers must handle the "Aggressor vs Passive" confusion.
        // This helper should just "Remove from [Side] list".
        const list = side === 'BUY' ? [...state.bids] : [...state.asks];
        const index = list.findIndex(l => l.price === price);

        if (index !== -1) {
            const level = list[index];
            level.qty -= qty;
            if (level.qty <= 0) {
                list.splice(index, 1);
            }
        }
        return side === 'BUY' ? { bids: list } : { asks: list };
    }),
}));

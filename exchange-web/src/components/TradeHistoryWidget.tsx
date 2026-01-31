import { useExchangeStore } from "../store";
import type { Trade } from "../store";

export const TradeHistoryWidget = () => {
    const trades = useExchangeStore((state: any) => state.trades);
    const activeOrders = useExchangeStore((state: any) => state.activeOrders);
    const filledOrders = useExchangeStore((state: any) => state.filledOrders);

    // Create a Set of My Order IDs for fast lookup
    const myOrderIds = new Set([
        ...activeOrders.map((o: any) => o.id),
        ...filledOrders.map((o: any) => o.id)
    ]);

    return (
        <div className="bg-slate-800 rounded-lg border border-slate-700 shadow-xl overflow-hidden h-full flex flex-col">
            <h2 className="text-slate-400 text-xs font-bold uppercase tracking-wider p-3 border-b border-slate-700 flex justify-between items-center">
                <span>Recent Trades</span>
                <span className="text-[10px] bg-slate-700 px-1.5 py-0.5 rounded text-slate-300">Live</span>
            </h2>

            <div className="flex-1 overflow-auto font-mono text-xs">
                {/* Header */}
                <div className="grid grid-cols-4 px-2 py-1 sticky top-0 bg-slate-800/95 backdrop-blur z-10 text-slate-500 border-b border-slate-700">
                    <div>Price</div>
                    <div className="text-right">Qty</div>
                    <div className="text-right">Time</div>
                    <div className="text-right">User</div>
                </div>

                {trades.length === 0 ? (
                    <div className="p-4 text-center text-slate-600 italic">No trades yet...</div>
                ) : (
                    <div className="flex flex-col">
                        {trades.map((trade: Trade) => {
                            const isMyTrade = myOrderIds.has(trade.id);
                            const priceColor = trade.side === 'BUY' ? 'text-emerald-500' : 'text-red-500';

                            return (
                                <div
                                    key={`${trade.id}-${trade.timestamp}`}
                                    className={`grid grid-cols-4 px-2 py-1 border-b border-slate-700/50 hover:bg-slate-700/30 transition-colors ${isMyTrade ? 'bg-blue-500/10' : ''}`}
                                >
                                    <div className={`${priceColor} font-bold`}>{trade.price.toFixed(2)}</div>
                                    <div className="text-right text-slate-300">{trade.qty}</div>
                                    <div className="text-right text-slate-500">
                                        {new Date(trade.timestamp).toLocaleTimeString([], { hour12: false, hour: '2-digit', minute: '2-digit', second: '2-digit' })}
                                    </div>
                                    <div className="text-right">
                                        {isMyTrade && (
                                            <span className="bg-blue-500 text-white text-[9px] px-1 rounded font-bold">YOU</span>
                                        )}
                                    </div>
                                </div>
                            );
                        })}
                    </div>
                )}
            </div>
        </div>
    );
};

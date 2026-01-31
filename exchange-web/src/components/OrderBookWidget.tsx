import { useExchangeStore } from "../store";

export const OrderBookWidget = () => {
    // Mock Data for UI Dev
    const bids = useExchangeStore((state: any) => state.bids);
    const asks = useExchangeStore((state: any) => state.asks);
    const activeOrders = useExchangeStore((state: any) => state.activeOrders);

    // If empty, show nothing (Real Mode)
    const displayBids = bids;
    const displayAsks = asks;

    return (
        <div className="bg-slate-800 rounded-lg border border-slate-700 shadow-xl overflow-hidden h-full flex flex-col">
            <h2 className="text-slate-400 text-xs font-bold uppercase tracking-wider p-3 border-b border-slate-700">Order Book</h2>

            <div className="flex-1 overflow-auto font-mono text-sm">
                {/* Header */}
                <div className="grid grid-cols-3 text-xs text-slate-500 px-2 py-1 sticky top-0 bg-slate-800 z-10">
                    <div className="text-left">Price</div>
                    <div className="text-right">Qty</div>
                    <div className="text-right">Total</div>
                </div>

                <div className="flex flex-col-reverse">
                    {displayAsks.map((level: any, i: number) => {
                        const isMyLevel = activeOrders.some((o: any) => o.side === 'SELL' && Math.abs(o.price - level.price) < 0.001);
                        return (
                            <div key={i} className={`grid grid-cols-3 px-2 py-0.5 relative hover:bg-slate-700/50 ${isMyLevel ? 'bg-red-500/20 ring-1 ring-red-500/50 inset-0' : ''}`}>
                                <div
                                    className="absolute right-0 top-0 bottom-0 bg-red-500/10 z-0"
                                    style={{ width: `${Math.min(level.qty, 100)}%` }}
                                />
                                <div className="text-red-500 z-10 text-left font-mono">{level.price.toFixed(2)}</div>
                                <div className="text-slate-300 z-10 text-right">{level.qty}</div>
                                <div className="text-slate-500 z-10 text-right">{level.count} {isMyLevel && '★'}</div>
                            </div>
                        );
                    })}
                </div>

                <div className="py-1 my-1 border-t border-b border-slate-700 text-center text-xs text-slate-500">
                    Spread: {(displayAsks.length > 0 && displayBids.length > 0)
                        ? (displayAsks[0].price - displayBids[0].price).toFixed(2)
                        : '--'}
                </div>

                <div>
                    {displayBids.map((level: any, i: number) => {
                        const isMyLevel = activeOrders.some((o: any) => o.side === 'BUY' && Math.abs(o.price - level.price) < 0.001);
                        return (
                            <div key={i} className={`grid grid-cols-3 px-2 py-0.5 relative hover:bg-slate-700/50 ${isMyLevel ? 'bg-emerald-500/20 ring-1 ring-emerald-500/50 inset-0' : ''}`}>
                                <div
                                    className="absolute right-0 top-0 bottom-0 bg-emerald-500/10 z-0"
                                    style={{ width: `${Math.min(level.qty, 100)}%` }}
                                />
                                <div className="text-emerald-500 z-10 text-left font-mono">{level.price.toFixed(2)}</div>
                                <div className="text-slate-300 z-10 text-right">{level.qty}</div>
                                <div className="text-slate-500 z-10 text-right">{level.count} {isMyLevel && '★'}</div>
                            </div>
                        );
                    })}
                </div>
            </div>
        </div>
    );
};

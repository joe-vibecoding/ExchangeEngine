import { ConnectionManager } from "./components/ConnectionManager";
import { OrderBookWidget } from "./components/OrderBookWidget";
import { OrderEntryWidget } from "./components/OrderEntryWidget";
import { TradeHistoryWidget } from "./components/TradeHistoryWidget";
import { useExchangeStore } from "./store";

function App() {
  const isConnected = useExchangeStore((state: any) => state.isConnected);
  const activeOrders = useExchangeStore((state: any) => state.activeOrders);

  return (
    <div className="min-h-screen bg-slate-900 text-slate-200 font-sans selection:bg-blue-500/30">
      <ConnectionManager />

      {/* Navbar */}
      <header className="px-6 py-3 bg-slate-800 border-b border-slate-700 flex justify-between items-center shadow-lg">
        <div className="flex items-center gap-3">
          <div className="w-3 h-3 rounded-full bg-blue-500 animate-pulse" />
          <h1 className="font-bold text-lg tracking-tight bg-gradient-to-r from-blue-400 to-cyan-300 bg-clip-text text-transparent">
            NANOSECOND EXCHANGE
          </h1>
        </div>
        <div className="flex items-center gap-4 text-xs font-mono">
          <div className={`flex items-center gap-2 px-3 py-1 rounded bg-slate-900 border ${isConnected ? 'border-emerald-500/50 text-emerald-400' : 'border-red-500/50 text-red-400'}`}>
            <div className={`w-2 h-2 rounded-full ${isConnected ? 'bg-emerald-500' : 'bg-red-500'}`} />
            {isConnected ? 'SYSTEM ONLINE' : 'DISCONNECTED'}
          </div>
        </div>
      </header>

      {/* Main Grid */}
      <main className="p-4 grid grid-cols-12 gap-4 h-[calc(100vh-64px)]">

        {/* Left Col: Order Entry & Active Orders (3/12) */}
        <div className="col-span-3 flex flex-col gap-4">
          <OrderEntryWidget />

          <div className="flex-1 bg-slate-800 rounded-lg border border-slate-700 shadow-xl overflow-hidden flex flex-col">
            <h2 className="text-slate-400 text-xs font-bold uppercase tracking-wider p-3 border-b border-slate-700">Active Orders</h2>
            <div className="flex-1 overflow-auto p-2 space-y-2">
              {activeOrders.length === 0 && <div className="text-slate-600 text-xs text-center mt-4">No active orders</div>}
              {activeOrders.map((order: any) => (
                <div key={order.id} className="bg-slate-900 border border-slate-700 p-2 rounded text-xs flex justify-between items-center group hover:border-slate-600 transition-colors">
                  <div>
                    <span className={`font-bold ${order.side === 'BUY' ? 'text-emerald-500' : 'text-red-500'}`}>{order.side}</span>
                    <span className="mx-2 text-slate-400">{order.qty} @ {order.price.toFixed(2)}</span>
                  </div>
                  <span className={`px-1.5 py-0.5 rounded text-[10px] font-bold ${order.status === 'FILLED' ? 'bg-blue-500/20 text-blue-400' : 'bg-slate-700 text-slate-400'
                    }`}>
                    {order.status}
                  </span>
                </div>
              ))}
            </div>
          </div>
        </div>

        {/* Center Col: Order Book (6/12) */}
        <div className="col-span-6">
          <OrderBookWidget />
        </div>

        {/* Right Col: Trade History (3/12) */}
        <div className="col-span-3">
          <TradeHistoryWidget />
        </div>

      </main>
    </div>
  );
}

export default App;

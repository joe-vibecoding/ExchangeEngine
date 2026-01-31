import React, { useState } from 'react';
import { sendOrder } from './ConnectionManager';

export const OrderEntryWidget = () => {
    const [price, setPrice] = useState<string>('100.00');
    const [qty, setQty] = useState<string>('10');
    const [side, setSide] = useState<'BUY' | 'SELL'>('BUY');

    const handleSubmit = (e: React.FormEvent) => {
        e.preventDefault();

        const priceNum = parseFloat(price);
        const qtyNum = parseInt(qty);

        if (isNaN(priceNum) || isNaN(qtyNum) || qtyNum <= 0) {
            alert("Invalid Input");
            return;
        }

        // Send JSON order which Gateway translates
        sendOrder({
            type: "ORDER",
            price: priceNum,
            qty: qtyNum,
            side: side
        });
    };

    return (
        <div className="bg-slate-800 p-4 rounded-lg border border-slate-700 shadow-xl">
            <h2 className="text-slate-400 text-xs font-bold uppercase tracking-wider mb-4">Order Entry</h2>

            <form onSubmit={handleSubmit} className="space-y-4">
                {/* Side Selection */}
                <div className="flex gap-2">
                    <button
                        type="button"
                        onClick={() => setSide('BUY')}
                        className={`flex-1 py-2 font-bold rounded transition-colors ${side === 'BUY'
                                ? 'bg-emerald-500 text-white shadow-emerald-500/20 shadow-lg'
                                : 'bg-slate-700 text-slate-400 hover:bg-slate-600'
                            }`}
                    >
                        BUY
                    </button>
                    <button
                        type="button"
                        onClick={() => setSide('SELL')}
                        className={`flex-1 py-2 font-bold rounded transition-colors ${side === 'SELL'
                                ? 'bg-red-500 text-white shadow-red-500/20 shadow-lg'
                                : 'bg-slate-700 text-slate-400 hover:bg-slate-600'
                            }`}
                    >
                        SELL
                    </button>
                </div>

                {/* Inputs */}
                <div className="grid grid-cols-2 gap-4">
                    <div>
                        <label className="block text-xs text-slate-500 mb-1">Price</label>
                        <input
                            type="number"
                            step="0.01"
                            value={price}
                            onChange={(e) => setPrice(e.target.value)}
                            className="w-full bg-slate-900 border border-slate-700 rounded p-2 text-right text-white font-mono focus:border-blue-500 focus:outline-none"
                        />
                    </div>
                    <div>
                        <label className="block text-xs text-slate-500 mb-1">Quantity</label>
                        <input
                            type="number"
                            value={qty}
                            onChange={(e) => setQty(e.target.value)}
                            className="w-full bg-slate-900 border border-slate-700 rounded p-2 text-right text-white font-mono focus:border-blue-500 focus:outline-none"
                        />
                    </div>
                </div>

                {/* Submit */}
                <button
                    type="submit"
                    className={`w-full py-3 font-bold rounded text-sm uppercase tracking-widest transition-transform active:scale-95 ${side === 'BUY' ? 'bg-emerald-600 hover:bg-emerald-500' : 'bg-red-600 hover:bg-red-500'
                        }`}
                >
                    Submit Order
                </button>
            </form>
        </div>
    );
};

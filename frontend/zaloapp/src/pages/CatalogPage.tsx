import { useMemo, useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { Link } from 'react-router-dom';
import { listProducts, formatVnd } from '@shop/shared';
import { api } from '@/lib/api';
import { ProductCard } from '@/components/ProductCard';
import { useCart } from '@/features/cart/use-cart';

export function CatalogPage() {
  const [search, setSearch] = useState('');
  const { data, isLoading, error } = useQuery({
    queryKey: ['products'],
    queryFn: () => listProducts(api, 0, 50),
  });
  const cart = useCart();

  const filtered = useMemo(() => {
    const q = search.trim().toLowerCase();
    const items = data?.content ?? [];
    if (!q) return items;
    return items.filter(p =>
      p.name.toLowerCase().includes(q) ||
      (p.description?.toLowerCase().includes(q) ?? false)
    );
  }, [data, search]);

  return (
    <div className="-mx-4 -mt-4">
      {/* Hero / greeting strip — Zalo blue */}
      <div className="bg-gradient-to-br from-zalo to-zalo-dark px-4 pt-5 pb-8 text-white">
        <div className="flex items-start justify-between gap-3">
          <div>
            <p className="text-xs opacity-90">🛵 Shop Giao Hàng • Zalo</p>
            <h1 className="text-2xl font-bold mt-0.5">Hôm nay ăn gì?</h1>
            <p className="text-xs opacity-90 mt-1">{filtered.length} món sẵn sàng giao</p>
          </div>
          <Link
            to="/customer/orders"
            className="bg-white/15 backdrop-blur rounded-full px-3 py-1.5 text-xs font-medium hover:bg-white/25 transition"
          >
            Đơn của tôi
          </Link>
        </div>
      </div>

      {/* Search bar — overlaps the hero by half */}
      <div className="px-4 -mt-5">
        <div className="bg-white rounded-2xl shadow-md px-4 py-2.5 flex items-center gap-2 ring-1 ring-black/5">
          <span className="text-gray-400">🔍</span>
          <input
            type="text"
            value={search}
            onChange={e => setSearch(e.target.value)}
            placeholder="Tìm phở, cơm gà, trà sữa…"
            className="flex-1 outline-none text-sm bg-transparent placeholder-gray-400"
          />
          {search && (
            <button
              onClick={() => setSearch('')}
              className="text-gray-400 text-lg leading-none"
              aria-label="Xoá tìm kiếm"
            >×</button>
          )}
        </div>
      </div>

      <div className="px-4 pt-5 pb-28">
        {isLoading && (
          <div className="space-y-3">
            {[1,2,3,4].map(i => (
              <div key={i} className="h-28 bg-white rounded-2xl animate-pulse border border-gray-100" />
            ))}
          </div>
        )}

        {error && (
          <div className="rounded-2xl bg-red-50 border border-red-200 p-4 text-sm text-red-700">
            <p className="font-medium mb-1">Không tải được danh sách sản phẩm.</p>
            <p className="text-xs opacity-80">Mở Mini App qua Zalo để dùng đầy đủ, hoặc thử lại sau.</p>
          </div>
        )}

        {!isLoading && !error && filtered.length === 0 && (
          <div className="text-center py-16 text-gray-500">
            <p className="text-4xl mb-2">🔎</p>
            <p className="text-sm">Không tìm thấy món "{search}"</p>
          </div>
        )}

        <div className="space-y-3">
          {filtered.map(p => (
            <ProductCard key={p.id} product={p} />
          ))}
        </div>
      </div>

      {cart.totalItems() > 0 && (
        <Link
          to="/customer/cart"
          className="fixed bottom-4 left-4 right-4 max-w-md mx-auto bg-zalo text-white rounded-2xl py-3.5 px-4 flex items-center justify-between shadow-xl shadow-zalo/30 active:scale-[0.98] transition"
        >
          <span className="flex items-center gap-2">
            <span className="bg-white/25 rounded-full w-7 h-7 inline-flex items-center justify-center font-bold text-sm">
              {cart.totalItems()}
            </span>
            <span className="font-semibold">Xem giỏ hàng</span>
          </span>
          <span className="font-bold">{formatVnd(cart.subtotal())}</span>
        </Link>
      )}
    </div>
  );
}

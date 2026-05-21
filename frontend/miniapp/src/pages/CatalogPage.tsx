import { useQuery } from '@tanstack/react-query';
import { Link } from 'react-router-dom';
import { listProducts, formatVnd } from '@shop/shared';
import { api } from '@/lib/api';
import { ProductCard } from '@/components/ProductCard';
import { useCart } from '@/features/cart/use-cart';

export function CatalogPage() {
  const { data, isLoading, error } = useQuery({
    queryKey: ['products'],
    queryFn: () => listProducts(api, 0, 50),
  });
  const cart = useCart();

  return (
    <div>
      <h1 className="text-2xl font-bold mb-4">Sản phẩm</h1>

      {isLoading && <p className="text-tg-hint">Đang tải...</p>}
      {error && <p className="text-red-500">Không tải được danh sách sản phẩm.</p>}

      <div className="space-y-3">
        {data?.content.map(p => (
          <ProductCard key={p.id} product={p} />
        ))}
      </div>

      {cart.totalItems() > 0 && (
        <Link
          to="/customer/cart"
          className="fixed bottom-4 left-4 right-4 max-w-md mx-auto bg-tg-button text-tg-buttonText rounded-lg py-3 px-4 flex items-center justify-between shadow-lg"
        >
          <span>🛒 Giỏ hàng ({cart.totalItems()})</span>
          <span className="font-bold">{formatVnd(cart.subtotal())}</span>
        </Link>
      )}
    </div>
  );
}

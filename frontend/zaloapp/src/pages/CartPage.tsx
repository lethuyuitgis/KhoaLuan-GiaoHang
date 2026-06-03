import { Link } from 'react-router-dom';
import { formatVnd } from '@shop/shared';
import { useCart } from '@/features/cart/use-cart';

export function CartPage() {
  const cart = useCart();

  if (cart.items.length === 0) {
    return (
      <div>
        <h1 className="text-2xl font-bold mb-4">Giỏ hàng</h1>
        <div className="text-center py-16">
          <p className="text-5xl mb-3">🛒</p>
          <p className="font-medium text-gray-700 mb-1">Giỏ hàng trống</p>
          <p className="text-sm text-gray-500 mb-5">Khám phá menu và thêm món yêu thích nhé!</p>
          <Link
            to="/customer/shop"
            className="inline-block px-5 py-2.5 rounded-2xl bg-zalo text-white font-semibold text-sm shadow-md shadow-zalo/30 active:scale-[0.98] transition"
          >
            Xem menu
          </Link>
        </div>
      </div>
    );
  }

  return (
    <div>
      <h1 className="text-2xl font-bold mb-4">Giỏ hàng</h1>

      <div className="space-y-3">
        {cart.items.map(item => (
          <div
            key={item.product.id}
            className="bg-white rounded-2xl shadow-sm border border-gray-100 p-3 flex gap-3"
          >
            {item.product.imageUrl ? (
              <img
                src={item.product.imageUrl}
                alt={item.product.name}
                className="w-16 h-16 rounded-xl object-cover bg-gray-100 flex-shrink-0"
              />
            ) : (
              <div className="w-16 h-16 rounded-xl bg-gradient-to-br from-zalo to-zalo-dark flex-shrink-0 flex items-center justify-center text-white font-bold text-xl">
                {item.product.name.charAt(0).toUpperCase()}
              </div>
            )}
            <div className="flex-1 min-w-0">
              <h3 className="font-semibold text-[15px] line-clamp-1">{item.product.name}</h3>
              <p className="text-sm font-bold text-zalo mt-0.5">
                {formatVnd(item.product.price)}
              </p>
              <div className="flex items-center gap-2 mt-2">
                <div className="inline-flex items-center bg-gray-50 rounded-full ring-1 ring-gray-200">
                  <button
                    onClick={() => cart.setQuantity(item.product.id, item.quantity - 1)}
                    className="w-8 h-8 inline-flex items-center justify-center text-lg leading-none text-gray-600 active:scale-95 transition"
                    aria-label="Giảm"
                  >
                    −
                  </button>
                  <span className="w-7 text-center font-semibold text-sm">{item.quantity}</span>
                  <button
                    onClick={() => cart.setQuantity(item.product.id, item.quantity + 1)}
                    className="w-8 h-8 inline-flex items-center justify-center text-lg leading-none text-gray-600 active:scale-95 transition"
                    aria-label="Tăng"
                  >
                    +
                  </button>
                </div>
                <button
                  onClick={() => cart.remove(item.product.id)}
                  className="ml-auto text-xs text-red-500 font-medium active:scale-95 transition"
                >
                  Xoá
                </button>
              </div>
            </div>
          </div>
        ))}
      </div>

      <div className="mt-5 bg-white rounded-2xl shadow-sm border border-gray-100 p-4">
        <div className="flex justify-between text-sm">
          <span className="text-gray-500">Tạm tính ({cart.totalItems()} món)</span>
          <span className="font-semibold">{formatVnd(cart.subtotal())}</span>
        </div>
        <p className="text-xs text-gray-400 mt-1.5">
          Phí ship sẽ tính khi bạn nhập địa chỉ giao
        </p>
      </div>

      <Link
        to="/customer/checkout"
        className="fixed bottom-4 left-4 right-4 max-w-md mx-auto bg-zalo text-white rounded-2xl py-3.5 px-4 flex items-center justify-between shadow-xl shadow-zalo/30 active:scale-[0.98] transition"
      >
        <span className="flex items-center gap-2">
          <span className="bg-white/25 rounded-full w-7 h-7 inline-flex items-center justify-center font-bold text-sm">
            {cart.totalItems()}
          </span>
          <span className="font-semibold">Đặt hàng</span>
        </span>
        <span className="font-bold">{formatVnd(cart.subtotal())}</span>
      </Link>
    </div>
  );
}

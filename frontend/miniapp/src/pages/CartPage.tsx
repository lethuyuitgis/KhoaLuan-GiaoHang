import { Link } from 'react-router-dom';
import { formatVnd } from '@shop/shared';
import { useCart } from '@/features/cart/use-cart';

export function CartPage() {
  const cart = useCart();

  if (cart.items.length === 0) {
    return (
      <div>
        <h1 className="text-2xl font-bold mb-4">Giỏ hàng</h1>
        <div className="text-center py-12">
          <p className="text-tg-hint mb-4">Giỏ hàng trống</p>
          <Link to="/customer/shop" className="text-tg-link">Quay lại mua hàng</Link>
        </div>
      </div>
    );
  }

  return (
    <div>
      <h1 className="text-2xl font-bold mb-4">Giỏ hàng</h1>

      <div className="space-y-3">
        {cart.items.map(item => (
          <div key={item.product.id} className="bg-tg-secondaryBg rounded-lg p-3 flex gap-3">
            {item.product.imageUrl ? (
              <img
                src={item.product.imageUrl}
                alt={item.product.name}
                className="w-16 h-16 rounded-md object-cover bg-tg-hint/20"
              />
            ) : (
              <div className="w-16 h-16 rounded-md bg-tg-hint/20" />
            )}
            <div className="flex-1 min-w-0">
              <h3 className="font-medium truncate">{item.product.name}</h3>
              <p className="text-sm text-tg-hint">{formatVnd(item.product.price)}</p>
              <div className="flex items-center gap-2 mt-2">
                <button
                  onClick={() => cart.setQuantity(item.product.id, item.quantity - 1)}
                  className="w-7 h-7 rounded-full bg-tg-hint/20 flex items-center justify-center"
                  aria-label="Giảm"
                >
                  −
                </button>
                <span className="w-8 text-center font-medium">{item.quantity}</span>
                <button
                  onClick={() => cart.setQuantity(item.product.id, item.quantity + 1)}
                  className="w-7 h-7 rounded-full bg-tg-hint/20 flex items-center justify-center"
                  aria-label="Tăng"
                >
                  +
                </button>
                <button
                  onClick={() => cart.remove(item.product.id)}
                  className="ml-auto text-sm text-red-500"
                >
                  Xóa
                </button>
              </div>
            </div>
          </div>
        ))}
      </div>

      <div className="mt-6 bg-tg-secondaryBg rounded-lg p-4">
        <div className="flex justify-between text-tg-hint">
          <span>Tạm tính</span>
          <span>{formatVnd(cart.subtotal())}</span>
        </div>
        <div className="flex justify-between text-tg-hint text-sm mt-1">
          <span>Phí ship sẽ tính khi nhập địa chỉ</span>
        </div>
      </div>

      <Link
        to="/customer/checkout"
        className="fixed bottom-4 left-4 right-4 max-w-md mx-auto bg-tg-button text-tg-buttonText rounded-lg py-3 px-4 text-center font-medium shadow-lg"
      >
        Đặt hàng
      </Link>
    </div>
  );
}

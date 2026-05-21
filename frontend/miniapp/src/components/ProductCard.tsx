import type { Product } from '@shop/shared';
import { formatVnd } from '@shop/shared';
import { useCart } from '@/features/cart/use-cart';

interface Props { product: Product }

export function ProductCard({ product }: Props) {
  const { add, getQuantity } = useCart();
  const qty = getQuantity(product.id);

  return (
    <div className="bg-tg-secondaryBg rounded-lg p-3 flex gap-3">
      {product.imageUrl ? (
        <img
          src={product.imageUrl}
          alt={product.name}
          className="w-20 h-20 rounded-md object-cover bg-tg-hint/20"
          loading="lazy"
        />
      ) : (
        <div className="w-20 h-20 rounded-md bg-tg-hint/20 flex items-center justify-center text-tg-hint text-xs">
          No image
        </div>
      )}
      <div className="flex-1 min-w-0">
        <h3 className="font-semibold truncate">{product.name}</h3>
        {product.description && (
          <p className="text-sm text-tg-hint line-clamp-2 mt-0.5">{product.description}</p>
        )}
        <div className="mt-2 flex items-center justify-between">
          <span className="font-bold text-tg-button">{formatVnd(product.price)}</span>
          <button
            onClick={() => add(product, 1)}
            className="px-3 py-1 text-sm bg-tg-button text-tg-buttonText rounded-md disabled:opacity-50"
            disabled={product.stock <= 0}
          >
            {qty > 0 ? `Thêm (${qty})` : product.stock <= 0 ? 'Hết hàng' : 'Thêm'}
          </button>
        </div>
      </div>
    </div>
  );
}

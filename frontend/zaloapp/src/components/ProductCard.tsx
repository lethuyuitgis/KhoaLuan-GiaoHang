import type { Product } from '@shop/shared';
import { formatVnd } from '@shop/shared';
import { useTranslation } from 'react-i18next';
import { useCart } from '@/features/cart/use-cart';

interface Props { product: Product }

/** Deterministic colored fallback so out-of-image products still look intentional. */
const FALLBACK_COLORS = [
  'from-blue-400 to-blue-600',
  'from-sky-400 to-indigo-600',
  'from-cyan-400 to-blue-600',
  'from-indigo-400 to-violet-600',
  'from-blue-500 to-purple-600',
  'from-teal-400 to-blue-600',
];

export function ProductCard({ product }: Props) {
  const { t, i18n } = useTranslation();
  const { add, getQuantity } = useCart();
  const qty = getQuantity(product.id);
  const outOfStock = product.stock <= 0;
  const fallback = FALLBACK_COLORS[product.id % FALLBACK_COLORS.length];

  return (
    <div className="bg-white rounded-2xl overflow-hidden shadow-sm border border-gray-100 flex active:scale-[0.98] transition-transform">
      <div className="relative w-28 flex-shrink-0">
        {product.imageUrl ? (
          <img
            src={product.imageUrl}
            alt={product.name}
            className="w-full h-full object-cover bg-gray-100"
            loading="lazy"
          />
        ) : (
          <div className={`w-full h-full bg-gradient-to-br ${fallback} flex items-center justify-center text-white font-bold text-2xl`}>
            {product.name.charAt(0).toUpperCase()}
          </div>
        )}
        {outOfStock && (
          <div className="absolute inset-0 bg-black/50 flex items-center justify-center">
            <span className="text-white text-xs font-semibold uppercase tracking-wide">{t('product.outOfStock')}</span>
          </div>
        )}
      </div>

      <div className="flex-1 min-w-0 p-3 flex flex-col justify-between">
        <div>
          <h3 className="font-semibold text-[15px] leading-tight line-clamp-1">{product.name}</h3>
          {product.description && (
            <p className="text-xs text-gray-500 line-clamp-2 mt-1">{product.description}</p>
          )}
        </div>
        <div className="mt-2 flex items-center justify-between gap-2">
          <span className="font-bold text-zalo text-base whitespace-nowrap">
            {formatVnd(product.price, i18n.language)}
          </span>
          <button
            onClick={() => add(product, 1)}
            disabled={outOfStock}
            className={
              qty > 0
                ? 'h-8 px-3 inline-flex items-center gap-1 rounded-full bg-zalo text-white text-xs font-semibold shadow-sm active:scale-95 transition'
                : 'h-8 w-8 inline-flex items-center justify-center rounded-full bg-zalo text-white text-lg leading-none shadow-sm active:scale-95 transition disabled:bg-gray-300 disabled:shadow-none'
            }
            aria-label={t('product.add', { name: product.name })}
          >
            {qty > 0 ? <><span>+</span><span>{qty}</span></> : '+'}
          </button>
        </div>
      </div>
    </div>
  );
}

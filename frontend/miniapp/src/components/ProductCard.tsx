import type { Product } from '@shop/shared';
import { formatVnd } from '@shop/shared';
import { useTranslation } from 'react-i18next';
import { useCart } from '@/features/cart/use-cart';

interface Props {
  product: Product;
  /** Marks the card with a small "best seller" flame ribbon. */
  highlighted?: boolean;
}

/**
 * Deterministic warm gradient fallback for products without an image —
 * keeps the grid feeling intentional even on a fresh DB.
 */
const FALLBACK_GRADIENTS = [
  'from-brand-300 to-brand-500',
  'from-brand-400 to-brand-600',
  'from-brand-500 to-brand-700',
  'from-brand-200 to-brand-400',
  'from-brand-600 to-brand-800',
  'from-cream-200 to-brand-300',
];

export function ProductCard({ product, highlighted = false }: Props) {
  const { t, i18n } = useTranslation();
  const { add, setQuantity, getQuantity } = useCart();
  const qty = getQuantity(product.id);
  const outOfStock = product.stock <= 0;
  const fallback = FALLBACK_GRADIENTS[product.id % FALLBACK_GRADIENTS.length];

  return (
    <div className="bg-white rounded-3xl overflow-hidden shadow-warm flex flex-col active:scale-[0.98] transition-transform">
      {/* Image area — 4:3, image fills, "+" button overlaps bottom-right. */}
      <div className="relative aspect-[4/3] bg-brand-100">
        {product.imageUrl ? (
          <img
            src={product.imageUrl}
            alt={product.name}
            className="absolute inset-0 w-full h-full object-cover"
            loading="lazy"
          />
        ) : (
          <div className={`absolute inset-0 bg-gradient-to-br ${fallback} flex items-center justify-center`}>
            <span className="text-white font-bold text-4xl drop-shadow">
              {product.name.charAt(0).toUpperCase()}
            </span>
          </div>
        )}

        {highlighted && !outOfStock && (
          <span className="absolute top-2.5 left-2.5 inline-flex items-center gap-1 px-2 py-0.5 rounded-full bg-brand-700/95 text-cream-50 text-[10px] font-bold uppercase tracking-wide backdrop-blur">
            <span aria-hidden="true">🔥</span>
            {t('catalog.bestSellerTag')}
          </span>
        )}

        {outOfStock && (
          <div className="absolute inset-0 bg-brand-900/60 flex items-center justify-center">
            <span className="px-3 py-1 rounded-full bg-white/90 text-brand-800 text-[11px] font-bold uppercase tracking-wider">
              {t('product.outOfStock')}
            </span>
          </div>
        )}

        {/* Quantity stepper overlay */}
        {!outOfStock && (
          <div className="absolute bottom-2.5 right-2.5">
            {qty > 0 ? (
              <div className="inline-flex items-center gap-1 bg-white rounded-full shadow-warm ring-1 ring-brand-100 pl-1 pr-1">
                <button
                  type="button"
                  onClick={() => setQuantity(product.id, qty - 1)}
                  className="w-7 h-7 inline-flex items-center justify-center text-brand-700 text-lg leading-none active:scale-90 transition"
                  aria-label={t('product.decrease')}
                >−</button>
                <span className="min-w-[18px] text-center text-sm font-bold text-brand-800">{qty}</span>
                <button
                  type="button"
                  onClick={() => add(product, 1)}
                  className="w-7 h-7 inline-flex items-center justify-center text-cream-50 text-lg leading-none rounded-full bg-brand-700 active:scale-90 transition"
                  aria-label={t('product.increase')}
                >+</button>
              </div>
            ) : (
              <button
                type="button"
                onClick={() => add(product, 1)}
                className="w-9 h-9 inline-flex items-center justify-center rounded-full bg-brand-700 text-cream-50 text-xl leading-none shadow-warm-lg active:scale-90 transition"
                aria-label={t('product.add', { name: product.name })}
              >+</button>
            )}
          </div>
        )}
      </div>

      {/* Body */}
      <div className="flex-1 flex flex-col p-3 gap-1">
        <h3 className="font-semibold text-sm text-brand-800 leading-snug line-clamp-1">
          {product.name}
        </h3>
        {product.description && (
          <p className="text-[11px] text-brand-500/80 line-clamp-2 min-h-[28px]">
            {product.description}
          </p>
        )}
        <p className="mt-1 text-brand-700 font-bold text-base">
          {formatVnd(product.price, i18n.language)}
        </p>
      </div>
    </div>
  );
}

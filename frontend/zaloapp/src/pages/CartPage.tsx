import { Link } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { formatVnd } from '@shop/shared';
import { useCart } from '@/features/cart/use-cart';

export function CartPage() {
  const { t, i18n } = useTranslation();
  const cart = useCart();

  if (cart.items.length === 0) {
    return (
      <div className="px-5 pt-6 pb-10">
        <h1 className="text-2xl font-bold text-brand-800 mb-6">{t('cart.title')}</h1>
        <div className="text-center py-16">
          <div className="w-24 h-24 rounded-full bg-brand-100 mx-auto flex items-center justify-center mb-5">
            <span className="text-5xl" aria-hidden="true">☕</span>
          </div>
          <p className="font-bold text-lg text-brand-800 mb-1.5">{t('cart.emptyTitle')}</p>
          <p className="text-sm text-brand-500 mb-6">{t('cart.emptyHint')}</p>
          <Link
            to="/customer/shop"
            className="inline-block px-6 py-3 rounded-full bg-brand-700 text-cream-50 font-semibold text-sm shadow-warm-lg active:scale-95 transition"
          >
            {t('cart.exploreMenu')}
          </Link>
        </div>
      </div>
    );
  }

  return (
    <div className="px-5 pt-6 pb-32">
      <h1 className="text-2xl font-bold text-brand-800 mb-5">{t('cart.title')}</h1>

      <div className="space-y-3">
        {cart.items.map(item => (
          <div
            key={item.product.id}
            className="bg-white rounded-3xl shadow-warm p-3 flex gap-3"
          >
            {item.product.imageUrl ? (
              <img
                src={item.product.imageUrl}
                alt={item.product.name}
                className="w-20 h-20 rounded-2xl object-cover bg-brand-100 flex-shrink-0"
              />
            ) : (
              <div className="w-20 h-20 rounded-2xl bg-gradient-to-br from-brand-400 to-brand-600 flex-shrink-0 flex items-center justify-center text-white font-bold text-2xl">
                {item.product.name.charAt(0).toUpperCase()}
              </div>
            )}
            <div className="flex-1 min-w-0 flex flex-col justify-between">
              <div>
                <h3 className="font-semibold text-brand-800 text-[15px] line-clamp-1">{item.product.name}</h3>
                <p className="text-base font-bold text-brand-700 mt-0.5">
                  {formatVnd(item.product.price, i18n.language)}
                </p>
              </div>
              <div className="flex items-center justify-between mt-2">
                <div className="inline-flex items-center bg-brand-50 rounded-full ring-1 ring-brand-100">
                  <button
                    onClick={() => cart.setQuantity(item.product.id, item.quantity - 1)}
                    className="w-8 h-8 inline-flex items-center justify-center text-lg leading-none text-brand-700 active:scale-90 transition"
                    aria-label={t('product.decrease')}
                  >−</button>
                  <span className="min-w-[28px] text-center font-bold text-sm text-brand-800">{item.quantity}</span>
                  <button
                    onClick={() => cart.setQuantity(item.product.id, item.quantity + 1)}
                    className="w-8 h-8 inline-flex items-center justify-center text-lg leading-none text-cream-50 bg-brand-700 rounded-full active:scale-90 transition"
                    aria-label={t('product.increase')}
                  >+</button>
                </div>
                <button
                  onClick={() => cart.remove(item.product.id)}
                  className="text-xs text-rose-500 font-semibold active:scale-95 transition"
                >
                  {t('cart.remove')}
                </button>
              </div>
            </div>
          </div>
        ))}
      </div>

      <div className="mt-5 bg-white rounded-3xl shadow-warm p-4 space-y-2">
        <div className="flex justify-between text-sm">
          <span className="text-brand-500">{t('cart.subtotal', { count: cart.totalItems() })}</span>
          <span className="font-bold text-brand-800">{formatVnd(cart.subtotal(), i18n.language)}</span>
        </div>
        <p className="text-xs text-brand-500/80 leading-relaxed">{t('cart.shippingNote')}</p>
      </div>

      <Link
        to="/customer/checkout"
        className="fixed bottom-20 left-4 right-4 max-w-md mx-auto bg-brand-700 text-cream-50 rounded-2xl py-3.5 px-4 flex items-center justify-between shadow-warm-lg active:scale-[0.98] transition"
      >
        <span className="flex items-center gap-2">
          <span className="bg-white/20 rounded-full w-7 h-7 inline-flex items-center justify-center font-bold text-sm">
            {cart.totalItems()}
          </span>
          <span className="font-semibold">{t('cart.placeOrder')}</span>
        </span>
        <span className="font-bold">{formatVnd(cart.subtotal(), i18n.language)}</span>
      </Link>
    </div>
  );
}

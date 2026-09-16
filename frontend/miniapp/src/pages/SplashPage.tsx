import { useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import { fetchMe } from '@shop/shared';
import { api } from '@/lib/api';
import { tg } from '@/lib/telegram';
import { useShopConfig } from '@/hooks/useShopConfig';

export function SplashPage() {
  const navigate = useNavigate();
  const { t } = useTranslation();
  const { data: shop } = useShopConfig();
  // Fall back to i18n keys so the splash never looks empty during the first
  // fetch — admins can override per-shop, customers see good defaults regardless.
  const brandName = shop?.name ?? t('splash.brandName');
  const tagline = shop?.tagline ?? t('app.tagline');

  const { data, isLoading, error } = useQuery({
    queryKey: ['me'],
    queryFn: () => fetchMe(api),
    enabled: tg.isInTelegram(),
    retry: 0,
  });

  const isShipper = !!data?.roles.includes('SHIPPER');

  useEffect(() => {
    // Khách thường → vào shop luôn. Shipper (kiêm khách) KHÔNG ép vào UI shipper
    // nữa — hiện màn chọn "Đặt hàng / Đi giao" bên dưới để vẫn đặt hàng được.
    if (data && !isShipper) {
      navigate('/customer/shop', { replace: true });
    }
  }, [data, isShipper, navigate]);

  // Out-of-Telegram dev landing — also doubles as the brand splash.
  if (!tg.isInTelegram()) {
    return (
      <div className="min-h-screen flex flex-col bg-gradient-to-br from-brand-800 via-brand-700 to-brand-600 text-cream-50">
        <div className="flex-1 px-6 pt-20 pb-10 text-center flex flex-col items-center justify-center">
          {shop?.logoUrl ? (
            <img src={shop.logoUrl} alt={brandName} className="w-24 h-24 mb-5 rounded-2xl object-contain bg-white/10 p-2" />
          ) : (
            <div className="text-7xl mb-5 drop-shadow-lg" aria-hidden="true">🛵</div>
          )}
          <p className="text-[11px] uppercase tracking-[0.3em] text-brand-200 mb-2">{t('splash.eyebrow')}</p>
          <h1 className="text-4xl font-extrabold leading-tight">{brandName}</h1>
          <p className="text-sm text-brand-100 mt-3 max-w-xs leading-relaxed">{tagline}</p>
        </div>
        <div className="px-5 pb-8 space-y-3">
          {import.meta.env.DEV && (
            <div className="bg-white/10 backdrop-blur border border-white/15 rounded-2xl p-3 text-xs text-cream-100 flex items-start gap-2">
              <span aria-hidden="true">⚠️</span>
              <span>{t('splash.devBanner')}</span>
            </div>
          )}
          <button
            type="button"
            onClick={() => navigate('/customer/shop')}
            className="w-full py-4 bg-cream-50 text-brand-700 rounded-full font-bold shadow-warm-lg active:scale-95 transition"
          >
            {t('splash.cta')}
          </button>
          <button
            type="button"
            onClick={() => navigate('/customer/orders')}
            className="w-full py-3 bg-white/10 backdrop-blur text-cream-50 rounded-full font-semibold border border-white/20 active:scale-95 transition"
          >
            {t('splash.myOrders')}
          </button>
        </div>
      </div>
    );
  }

  if (isLoading) {
    return (
      <div className="min-h-screen flex flex-col items-center justify-center text-center px-6 bg-gradient-to-br from-brand-800 to-brand-600 text-cream-50">
        <div className="text-6xl mb-4" aria-hidden="true">🛵</div>
        <h1 className="text-2xl font-bold">{brandName}</h1>
        <div className="mt-6 inline-block w-8 h-8 border-2 border-cream-200/40 border-t-cream-50 rounded-full animate-spin" />
        <p className="mt-4 text-sm text-brand-100">{t('splash.authenticating')}</p>
      </div>
    );
  }

  if (error) {
    return (
      <div className="min-h-screen flex flex-col items-center justify-center text-center px-6 bg-brand-50 text-brand-800">
        <div className="w-24 h-24 rounded-full bg-brand-100 flex items-center justify-center mb-4">
          <span className="text-5xl" aria-hidden="true">😞</span>
        </div>
        <h2 className="text-xl font-bold mb-2">{t('splash.authFailed')}</h2>
        <p className="text-sm text-brand-500">{t('splash.authFailedHint')}</p>
      </div>
    );
  }

  // Shipper (kiêm khách): chọn vào khu nào — vẫn đặt hàng được như khách.
  if (isShipper) {
    return (
      <div className="min-h-screen flex flex-col bg-gradient-to-br from-brand-800 via-brand-700 to-brand-600 text-cream-50">
        <div className="flex-1 px-6 pt-20 pb-6 text-center flex flex-col items-center justify-center">
          {shop?.logoUrl ? (
            <img src={shop.logoUrl} alt={brandName} className="w-20 h-20 mb-4 rounded-2xl object-contain bg-white/10 p-2" />
          ) : (
            <div className="text-6xl mb-4" aria-hidden="true">🛵</div>
          )}
          <h1 className="text-3xl font-extrabold leading-tight">{brandName}</h1>
          <p className="text-sm text-brand-100 mt-2">Bạn muốn làm gì hôm nay?</p>
        </div>
        <div className="px-5 pb-10 space-y-3">
          <button
            type="button"
            onClick={() => navigate('/customer/shop')}
            className="w-full py-4 bg-cream-50 text-brand-700 rounded-2xl font-bold shadow-warm-lg active:scale-95 transition flex items-center justify-center gap-2"
          >
            🛍️ Đặt hàng
          </button>
          <button
            type="button"
            onClick={() => navigate('/shipper/assignments')}
            className="w-full py-4 bg-white/10 backdrop-blur text-cream-50 rounded-2xl font-semibold border border-white/25 active:scale-95 transition flex items-center justify-center gap-2"
          >
            🛵 Đi giao (Shipper)
          </button>
        </div>
      </div>
    );
  }

  return null;
}

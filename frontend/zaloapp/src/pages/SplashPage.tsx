import { useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import { fetchMe } from '@shop/shared';
import { api } from '@/lib/api';
import { zalo } from '@/lib/zalo';

export function SplashPage() {
  const navigate = useNavigate();
  const { t } = useTranslation();

  const { data, isLoading, error } = useQuery({
    queryKey: ['me'],
    queryFn: () => fetchMe(api),
    enabled: zalo.isInZalo() && !!zalo.accessToken(),
    retry: 0,
  });

  useEffect(() => {
    if (data) {
      navigate('/customer/shop', { replace: true });
    }
  }, [data, navigate]);

  if (!zalo.isInZalo()) {
    return (
      <div className="min-h-screen flex flex-col bg-gradient-to-br from-brand-800 via-brand-700 to-brand-600 text-cream-50">
        <div className="flex-1 px-6 pt-20 pb-10 text-center flex flex-col items-center justify-center">
          <div className="text-7xl mb-5 drop-shadow-lg" aria-hidden="true">☕</div>
          <p className="text-[11px] uppercase tracking-[0.3em] text-brand-200 mb-2">{t('splash.eyebrow')}</p>
          <h1 className="text-4xl font-extrabold leading-tight">{t('splash.brandName')}</h1>
          <p className="text-xs uppercase tracking-widest text-brand-100/80 mt-3">Zalo Mini App</p>
          <p className="text-sm text-brand-100 mt-3 max-w-xs leading-relaxed">{t('app.tagline')}</p>
        </div>
        <div className="px-5 pb-8 space-y-3">
          {import.meta.env.DEV && (
            <div className="bg-white/10 backdrop-blur border border-white/15 rounded-2xl p-3 text-xs text-cream-100 flex items-start gap-2">
              <span aria-hidden="true">⚠️</span>
              <span>{t('splash.devBannerZalo')}</span>
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
        <div className="text-6xl mb-4" aria-hidden="true">☕</div>
        <h1 className="text-2xl font-bold">{t('splash.brandName')}</h1>
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

  return null;
}

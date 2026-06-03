import { useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import { fetchMe } from '@shop/shared';
import { api } from '@/lib/api';
import { tg } from '@/lib/telegram';

export function SplashPage() {
  const navigate = useNavigate();
  const { t } = useTranslation();

  const { data, isLoading, error } = useQuery({
    queryKey: ['me'],
    queryFn: () => fetchMe(api),
    enabled: tg.isInTelegram(),
    retry: 0,
  });

  useEffect(() => {
    if (data) {
      if (data.roles.includes('SHIPPER')) {
        navigate('/shipper/assignments', { replace: true });
      } else {
        navigate('/customer/shop', { replace: true });
      }
    }
  }, [data, navigate]);

  // Out-of-Telegram dev landing — also doubles as the brand splash.
  if (!tg.isInTelegram()) {
    return (
      <div className="-mx-4 -mt-4 min-h-screen flex flex-col">
        <div className="flex-1 bg-gradient-to-br from-orange-500 to-red-600 px-6 pt-16 pb-10 text-white text-center">
          <div className="text-6xl mb-3">🛵</div>
          <h1 className="text-3xl font-bold">{t('app.name')}</h1>
          <p className="text-sm opacity-90 mt-2">{t('app.tagline')}</p>
        </div>
        <div className="px-4 py-6 bg-white space-y-3">
          {import.meta.env.DEV && (
            <div className="bg-amber-50 border border-amber-200 rounded-2xl p-3 text-xs text-amber-800 flex items-start gap-2">
              <span>⚠️</span>
              <span>{t('splash.devBanner')}</span>
            </div>
          )}
          <button
            onClick={() => navigate('/customer/shop')}
            className="w-full py-3.5 bg-orange-500 text-white rounded-2xl font-semibold shadow-md shadow-orange-500/30 active:scale-[0.98] transition"
          >
            {t('splash.enterMenu')}
          </button>
          <button
            onClick={() => navigate('/customer/orders')}
            className="w-full py-3 bg-gray-50 text-gray-700 rounded-2xl font-medium border border-gray-200 active:scale-[0.98] transition"
          >
            {t('splash.myOrders')}
          </button>
        </div>
      </div>
    );
  }

  if (isLoading) {
    return (
      <div className="min-h-screen flex flex-col items-center justify-center text-center px-6">
        <div className="text-5xl mb-4">🛵</div>
        <h1 className="text-2xl font-bold text-orange-600">{t('app.name')}</h1>
        <div className="mt-6 inline-block w-8 h-8 border-2 border-orange-200 border-t-orange-500 rounded-full animate-spin"></div>
        <p className="mt-4 text-sm text-gray-500">{t('splash.authenticating')}</p>
      </div>
    );
  }

  if (error) {
    return (
      <div className="min-h-screen flex flex-col items-center justify-center text-center px-6">
        <div className="text-5xl mb-3">😞</div>
        <h2 className="text-xl font-bold mb-2">{t('splash.authFailed')}</h2>
        <p className="text-sm text-gray-500">{t('splash.authFailedHint')}</p>
      </div>
    );
  }

  return null;
}

import { Link } from 'react-router-dom';
import { useTranslation } from 'react-i18next';

export function NotFoundPage() {
  const { t } = useTranslation();
  return (
    <div className="min-h-[80vh] flex flex-col items-center justify-center text-center px-6 bg-brand-50">
      <p className="text-[88px] font-extrabold text-brand-700 leading-none drop-shadow-sm" aria-hidden="true">404</p>
      <div className="text-5xl mt-2 mb-5" aria-hidden="true">☕</div>
      <h2 className="text-2xl font-bold text-brand-800 mb-2">{t('notFound.title')}</h2>
      <p className="text-sm text-brand-500 mb-6 max-w-xs leading-relaxed">{t('notFound.hint')}</p>
      <Link
        to="/"
        className="px-6 py-3 rounded-full bg-brand-700 text-cream-50 font-semibold text-sm shadow-warm-lg active:scale-95 transition"
      >
        {t('notFound.home')}
      </Link>
    </div>
  );
}

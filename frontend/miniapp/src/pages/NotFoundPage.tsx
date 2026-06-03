import { Link } from 'react-router-dom';
import { useTranslation } from 'react-i18next';

export function NotFoundPage() {
  const { t } = useTranslation();
  return (
    <div className="min-h-[70vh] flex flex-col items-center justify-center text-center px-6">
      <p className="text-6xl mb-3">🛵💨</p>
      <h2 className="text-2xl font-bold mb-1">{t('notFound.title')}</h2>
      <p className="text-sm text-gray-500 mb-6 max-w-xs">{t('notFound.hint')}</p>
      <Link
        to="/"
        className="px-5 py-2.5 rounded-2xl bg-orange-500 text-white font-semibold text-sm shadow-md shadow-orange-500/30 active:scale-[0.98] transition"
      >
        {t('notFound.home')}
      </Link>
    </div>
  );
}

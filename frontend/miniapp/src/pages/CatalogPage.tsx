import { useMemo, useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import { listProducts } from '@shop/shared';
import { api } from '@/lib/api';
import { ProductCard } from '@/components/ProductCard';
import { LanguageSwitcher } from '@/components/LanguageSwitcher';
import { PromoCarousel } from '@/components/PromoCarousel';
import { CategoryChips, categorize, isBestseller, type CategoryKey } from '@/components/CategoryChips';
import { tg } from '@/lib/telegram';
import { useShopConfig } from '@/hooks/useShopConfig';

export function CatalogPage() {
  const { t } = useTranslation();
  const [search, setSearch] = useState('');
  const [category, setCategory] = useState<CategoryKey>('all');
  const { data, isLoading, error } = useQuery({
    queryKey: ['products'],
    queryFn: () => listProducts(api, 0, 50),
  });

  const items = data?.content ?? [];

  // Combined filter: category bucket + free-text search.
  const filtered = useMemo(() => {
    const q = search.trim().toLowerCase();
    return items.filter(p => {
      if (q) {
        const matches = p.name.toLowerCase().includes(q)
          || (p.description?.toLowerCase().includes(q) ?? false);
        if (!matches) return false;
      }
      if (category === 'all')        return true;
      if (category === 'bestseller') return isBestseller(p.id);
      return categorize(p.id) === category;
    });
  }, [items, search, category]);

  // Bestseller carousel — independent of category filter so it stays visible
  // on the "All" tab as a highlight rail.
  const bestSellers = useMemo(
    () => items.filter(p => isBestseller(p.id) && p.stock > 0).slice(0, 4),
    [items],
  );

  const firstName = tg.unsafeUser()?.first_name?.trim();
  const greeting = firstName
    ? t('catalog.greetingNamed', { name: firstName })
    : t('catalog.greeting');
  const { data: shop } = useShopConfig();
  const brandLine = shop?.name ?? t('catalog.brand');
  const tagline = shop?.tagline ?? t('catalog.heroSubtitle');

  return (
    <div>
      {/* Hero — dark coffee bg with curved bottom corners. */}
      <header className="bg-brand-700 text-cream-50 px-5 pt-6 pb-10 rounded-b-3xl shadow-warm">
        <div className="flex items-start justify-between gap-3">
          <div className="min-w-0">
            <p className="text-[11px] uppercase tracking-[0.2em] text-brand-200">{brandLine}</p>
            <h1 className="text-2xl font-bold mt-1 leading-tight">{greeting}</h1>
            <p className="text-xs text-brand-200 mt-2">{tagline}</p>
          </div>
          <LanguageSwitcher variant="light" />
        </div>
      </header>

      {/* Search bar — overlaps hero. */}
      <div className="px-4 -mt-6">
        <label className="flex items-center gap-2 bg-white rounded-2xl shadow-warm px-4 py-3 ring-1 ring-brand-100">
          <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" className="text-brand-400 flex-shrink-0" aria-hidden="true">
            <circle cx="11" cy="11" r="7" />
            <path d="M21 21l-4.3-4.3" />
          </svg>
          <input
            type="text"
            value={search}
            onChange={e => setSearch(e.target.value)}
            placeholder={t('catalog.searchPlaceholder')}
            className="flex-1 outline-none text-sm bg-transparent placeholder-brand-400/70 text-brand-800"
          />
          {search && (
            <button
              type="button"
              onClick={() => setSearch('')}
              className="text-brand-400 text-xl leading-none active:scale-90"
              aria-label={t('catalog.searchClear')}
            >×</button>
          )}
        </label>
      </div>

      {/* Promo carousel. */}
      <div className="mt-4">
        <PromoCarousel />
      </div>

      {/* Categories. */}
      <div className="mt-5">
        <CategoryChips value={category} onChange={setCategory} />
      </div>

      {/* Best sellers — only show on "All" tab to avoid duplicates. */}
      {category === 'all' && !search && bestSellers.length > 0 && (
        <section className="mt-6 px-4">
          <div className="flex items-baseline justify-between mb-3">
            <h2 className="text-base font-bold text-brand-800 flex items-center gap-1.5">
              <span aria-hidden="true">🔥</span>
              {t('catalog.bestSeller')}
            </h2>
            <button
              type="button"
              onClick={() => setCategory('bestseller')}
              className="text-xs text-brand-600 font-semibold active:scale-95"
            >
              {t('catalog.seeAll')} →
            </button>
          </div>
          <div className="grid grid-cols-2 gap-3">
            {bestSellers.map(p => (
              <ProductCard key={p.id} product={p} highlighted />
            ))}
          </div>
        </section>
      )}

      {/* All products section. */}
      <section className="mt-6 px-4">
        <h2 className="text-base font-bold text-brand-800 mb-3">
          {category === 'all' ? t('catalog.allProducts') : t(`categories.${category}`)}
        </h2>

        {isLoading && (
          <div className="grid grid-cols-2 gap-3">
            {[1, 2, 3, 4].map(i => (
              <div key={i} className="aspect-[4/3] bg-white rounded-3xl shadow-warm animate-pulse" />
            ))}
          </div>
        )}

        {error && (
          <div className="rounded-3xl bg-rose-50 border border-rose-200 p-4 text-sm text-rose-700">
            <p className="font-semibold mb-1">{t('catalog.errorLoad')}</p>
            <p className="text-xs opacity-80">{t('catalog.errorLoadHint')}</p>
          </div>
        )}

        {!isLoading && !error && filtered.length === 0 && (
          <div className="text-center py-12 text-brand-500">
            <p className="text-5xl mb-3">🍱</p>
            <p className="text-sm">{search
              ? t('catalog.searchEmpty', { query: search })
              : t('catalog.categoryEmpty')}</p>
          </div>
        )}

        <div className="grid grid-cols-2 gap-3">
          {filtered.map(p => (
            <ProductCard
              key={p.id}
              product={p}
              highlighted={category !== 'bestseller' && isBestseller(p.id)}
            />
          ))}
        </div>
      </section>
    </div>
  );
}

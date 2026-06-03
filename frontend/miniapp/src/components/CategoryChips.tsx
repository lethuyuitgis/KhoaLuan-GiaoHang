import { useTranslation } from 'react-i18next';

export type CategoryKey = 'all' | 'food' | 'drink' | 'dessert' | 'bestseller';

const CATEGORY_ORDER: CategoryKey[] = ['all', 'bestseller', 'food', 'drink', 'dessert'];

const ICONS: Record<CategoryKey, string> = {
  all: '🍽️',
  bestseller: '🔥',
  food: '🍜',
  drink: '☕',
  dessert: '🍰',
};

interface Props {
  value: CategoryKey;
  onChange: (next: CategoryKey) => void;
}

/**
 * Horizontally scrollable category pills.
 * Active chip is filled brown; inactive chips use cream background with brown text.
 */
export function CategoryChips({ value, onChange }: Props) {
  const { t } = useTranslation();
  return (
    <div className="px-4 -mx-1">
      <div className="flex gap-2 overflow-x-auto scrollbar-hide py-1 px-1 no-select">
        {CATEGORY_ORDER.map(key => {
          const active = key === value;
          return (
            <button
              key={key}
              type="button"
              onClick={() => onChange(key)}
              aria-pressed={active}
              className={
                'flex-shrink-0 inline-flex items-center gap-1.5 px-4 py-2 rounded-full text-sm font-semibold transition active:scale-95 ' +
                (active
                  ? 'bg-brand-700 text-cream-50 shadow-warm'
                  : 'bg-brand-100 text-brand-700 hover:bg-brand-200')
              }
            >
              <span aria-hidden="true" className="text-base leading-none">{ICONS[key]}</span>
              <span>{t(`categories.${key}`)}</span>
            </button>
          );
        })}
      </div>
    </div>
  );
}

/**
 * Deterministic mapping product.id → category. Used until backend exposes a
 * real `category` field on products. Keeps the demo demo-feels-real without
 * a schema change.
 */
export function categorize(productId: number): CategoryKey {
  // Skip 'all' (it's a filter, not a bucket) and 'bestseller' (handled by separate logic).
  const buckets: CategoryKey[] = ['food', 'drink', 'dessert'];
  return buckets[productId % buckets.length];
}

export function isBestseller(productId: number): boolean {
  // Top quarter of products get the bestseller tag — deterministic so the
  // homepage section always has content.
  return productId % 4 === 0;
}

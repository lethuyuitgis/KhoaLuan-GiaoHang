import { useEffect, useRef, useState } from 'react';
import { useTranslation } from 'react-i18next';

interface Slide {
  titleKey: string;
  subtitleKey: string;
  gradient: string;
  emoji: string;
}

const SLIDES: Slide[] = [
  { titleKey: 'promo.discount20.title',        subtitleKey: 'promo.discount20.sub',        gradient: 'from-brand-700 via-brand-600 to-brand-500', emoji: '☕' },
  { titleKey: 'promo.freeship.title',          subtitleKey: 'promo.freeship.sub',          gradient: 'from-brand-500 via-brand-400 to-brand-300', emoji: '🛵' },
  { titleKey: 'promo.coffeeCollection.title',  subtitleKey: 'promo.coffeeCollection.sub',  gradient: 'from-brand-800 via-brand-700 to-brand-600', emoji: '✨' },
];

const ROTATE_MS = 4000;
const SWIPE_THRESHOLD = 40;

/**
 * Auto-rotating promo carousel — pure-React, no library. Touch-swipe enabled.
 * Dots indicate / jump to a specific slide.
 */
export function PromoCarousel() {
  const { t } = useTranslation();
  const [index, setIndex] = useState(0);
  const [paused, setPaused] = useState(false);
  const touchStart = useRef<number | null>(null);

  useEffect(() => {
    if (paused) return;
    const id = setInterval(() => {
      setIndex(i => (i + 1) % SLIDES.length);
    }, ROTATE_MS);
    return () => clearInterval(id);
  }, [paused]);

  function onTouchStart(e: React.TouchEvent) {
    touchStart.current = e.touches[0]?.clientX ?? null;
    setPaused(true);
  }
  function onTouchEnd(e: React.TouchEvent) {
    const end = e.changedTouches[0]?.clientX ?? null;
    const start = touchStart.current;
    touchStart.current = null;
    setPaused(false);
    if (start === null || end === null) return;
    const dx = end - start;
    if (Math.abs(dx) < SWIPE_THRESHOLD) return;
    if (dx < 0) setIndex(i => (i + 1) % SLIDES.length);
    else        setIndex(i => (i - 1 + SLIDES.length) % SLIDES.length);
  }

  return (
    <div className="px-4">
      <div
        className="relative aspect-[16/9] rounded-3xl overflow-hidden shadow-warm"
        onTouchStart={onTouchStart}
        onTouchEnd={onTouchEnd}
        role="region"
        aria-label={t('promo.label')}
      >
        {SLIDES.map((s, i) => (
          <div
            key={s.titleKey}
            className={
              'absolute inset-0 bg-gradient-to-br ' + s.gradient +
              ' text-white flex items-center justify-between gap-3 px-6 transition-opacity duration-500 ease-out ' +
              (i === index ? 'opacity-100' : 'opacity-0 pointer-events-none')
            }
            aria-hidden={i !== index}
          >
            <div className="flex-1 min-w-0">
              <p className="text-[11px] uppercase tracking-[0.18em] opacity-80 mb-1.5">{t('promo.tag')}</p>
              <h2 className="text-lg font-bold leading-tight">{t(s.titleKey)}</h2>
              <p className="text-xs opacity-90 mt-1.5 line-clamp-2">{t(s.subtitleKey)}</p>
            </div>
            <div className="text-5xl flex-shrink-0 opacity-90 drop-shadow-lg">{s.emoji}</div>
          </div>
        ))}

        <div className="absolute bottom-3 left-1/2 -translate-x-1/2 flex gap-1.5">
          {SLIDES.map((s, i) => (
            <button
              key={s.titleKey}
              type="button"
              onClick={() => setIndex(i)}
              aria-label={t('promo.gotoSlide', { n: i + 1 })}
              className={
                'h-1.5 rounded-full transition-all ' +
                (i === index ? 'w-6 bg-white' : 'w-1.5 bg-white/50')
              }
            />
          ))}
        </div>
      </div>
    </div>
  );
}

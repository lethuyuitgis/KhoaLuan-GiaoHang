import { useState } from 'react';
import { useMutation } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import { rateOrder } from '@shop/shared';
import { api } from '@/lib/api';

/**
 * Khách đánh giá shipper sau khi đơn đã giao (1..5 sao + nhận xét tuỳ chọn) —
 * đánh giá ngay trong Mini App qua REST (POST /api/orders/{id}/rating), thay vì
 * phải quay lại chat bot bấm nút FSM.
 */
export function RatingForm({ orderId }: { orderId: string }) {
  const { t } = useTranslation();
  const [stars, setStars] = useState(0);
  const [hover, setHover] = useState(0);
  const [comment, setComment] = useState('');

  const rateMut = useMutation({
    mutationFn: () => rateOrder(api, orderId, stars, comment.trim() || undefined),
  });

  const alreadyRated =
    (rateMut.error as { response?: { data?: { code?: string } } })?.response?.data?.code === 'ALREADY_RATED';

  if (rateMut.isSuccess || alreadyRated) {
    return (
      <section className="rounded-3xl bg-gradient-to-br from-brand-100 to-cream-200 p-5 text-center shadow-warm">
        <p className="text-2xl mb-1">🙏</p>
        <p className="font-semibold text-brand-800">
          {alreadyRated ? t('rating.already') : t('rating.thanks')}
        </p>
      </section>
    );
  }

  const shown = hover || stars;
  return (
    <section className="rounded-3xl bg-gradient-to-br from-brand-100 to-cream-200 p-5 shadow-warm">
      <h2 className="text-[11px] font-bold uppercase tracking-[0.18em] text-brand-600 mb-3">
        {t('rating.title')}
      </h2>

      <div className="flex justify-center gap-2 mb-3" data-testid="rating-stars">
        {[1, 2, 3, 4, 5].map(n => (
          <button
            key={n}
            type="button"
            aria-label={t('rating.starLabel', { n })}
            onMouseEnter={() => setHover(n)}
            onMouseLeave={() => setHover(0)}
            onClick={() => setStars(n)}
            className={`text-3xl leading-none active:scale-90 transition ${n <= shown ? 'text-amber-500' : 'text-brand-200'}`}
          >
            ★
          </button>
        ))}
      </div>

      <textarea
        value={comment}
        onChange={e => setComment(e.target.value)}
        placeholder={t('rating.commentPlaceholder')}
        maxLength={1000}
        rows={2}
        className="w-full px-4 py-3 rounded-2xl bg-white/70 border border-brand-100 text-sm text-brand-800 placeholder-brand-400/70 focus:outline-none focus:ring-2 focus:ring-brand-600 resize-none mb-3"
      />

      {rateMut.isError && !alreadyRated && <p className="text-xs text-red-600 mb-2">{t('rating.error')}</p>}

      <button
        type="button"
        onClick={() => rateMut.mutate()}
        disabled={stars === 0 || rateMut.isPending}
        className="w-full py-3 rounded-2xl bg-brand-700 text-cream-50 font-bold shadow-warm active:scale-[0.98] transition disabled:bg-brand-200 disabled:text-brand-400 disabled:shadow-none"
      >
        {rateMut.isPending ? t('rating.sending') : t('rating.submit')}
      </button>
    </section>
  );
}

import { useState, type FormEvent } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import { getOrderChat, sendOrderChat } from '@shop/shared';
import { api } from '@/lib/api';

const CHAT_POLL_MS = 4000;

/**
 * Anonymous customer↔shipper chat for the Zalo Mini App (no bot DM like
 * Telegram). Polls the REST chat endpoint while the delivery is active; the
 * customer's messages are relayed to the shipper's Telegram bot server-side.
 */
export function OrderChat({ orderId, active }: { orderId: string; active: boolean }) {
  const { t } = useTranslation();
  const qc = useQueryClient();
  const [draft, setDraft] = useState('');

  const { data: messages = [] } = useQuery({
    queryKey: ['order', orderId, 'chat'],
    queryFn: () => getOrderChat(api, orderId),
    refetchInterval: active ? CHAT_POLL_MS : false,
  });

  const sendMut = useMutation({
    mutationFn: (body: string) => sendOrderChat(api, orderId, body),
    onSuccess: () => {
      setDraft('');
      qc.invalidateQueries({ queryKey: ['order', orderId, 'chat'] });
    },
  });

  const onSubmit = (e: FormEvent) => {
    e.preventDefault();
    const body = draft.trim();
    if (body && !sendMut.isPending) sendMut.mutate(body);
  };

  return (
    <section className="bg-white rounded-3xl shadow-warm p-5">
      <h2 className="text-[11px] font-bold uppercase tracking-[0.18em] text-brand-500 mb-3">
        {t('chat.title')}
      </h2>

      <div className="space-y-2 max-h-72 overflow-y-auto mb-3" data-testid="chat-messages">
        {messages.length === 0 && (
          <p className="text-xs text-brand-400 italic text-center py-4" data-testid="chat-empty">{t('chat.empty')}</p>
        )}
        {messages.map(m => {
          const mine = m.senderRole === 'CUSTOMER';
          return (
            <div key={m.id} className={`flex ${mine ? 'justify-end' : 'justify-start'}`}>
              <div className={`max-w-[80%] px-3 py-2 rounded-2xl text-sm ${
                mine ? 'bg-brand-600 text-cream-50 rounded-br-sm'
                     : 'bg-brand-50 text-brand-800 rounded-bl-sm'}`}>
                <span className="block text-[10px] opacity-70 mb-0.5">
                  {mine ? t('chat.you') : t('chat.shipper')}
                </span>
                {m.body}
              </div>
            </div>
          );
        })}
      </div>

      {sendMut.isError && (
        <p className="text-xs text-red-600 mb-2">{t('chat.sendError')}</p>
      )}

      <form onSubmit={onSubmit} className="flex items-center gap-2">
        <input
          type="text"
          value={draft}
          onChange={e => setDraft(e.target.value)}
          placeholder={t('chat.placeholder')}
          maxLength={1000}
          className="flex-1 px-4 py-2.5 rounded-2xl bg-brand-50 border border-brand-100 text-sm text-brand-800 placeholder-brand-400/70 focus:outline-none focus:ring-2 focus:ring-brand-600"
          data-testid="chat-input"
        />
        <button
          type="submit"
          disabled={!draft.trim() || sendMut.isPending}
          className="px-4 py-2.5 rounded-2xl bg-brand-700 text-cream-50 text-sm font-bold disabled:bg-brand-200 disabled:text-brand-400"
          data-testid="chat-send"
        >
          {t('chat.send')}
        </button>
      </form>
    </section>
  );
}

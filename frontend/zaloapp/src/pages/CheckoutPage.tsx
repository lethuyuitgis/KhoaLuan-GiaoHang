import { useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useMutation } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import { createOrder, formatVnd, type CreateOrderRequest, type PaymentMethod } from '@shop/shared';
import { api } from '@/lib/api';
import { useCart } from '@/features/cart/use-cart';
import { usePayWithVnpay } from '@/features/payment/use-pay-with-vnpay';
import { useToast } from '@/components/Toast';
import { AddressPicker } from '@/features/address/AddressPicker';
import { isInHanoi, isInVietnam, validateAddressString } from '@/features/address/nominatim';

const INPUT_CLS =
  'mt-1 w-full px-4 py-3 rounded-2xl bg-brand-50 border border-brand-100 ' +
  'text-sm text-brand-800 placeholder-brand-400/70 focus:outline-none focus:ring-2 focus:ring-brand-600 focus:border-brand-600 transition';

const SECTION_TITLE_CLS = 'text-[11px] font-bold uppercase tracking-[0.18em] text-brand-500 mb-3';
const SECTION_CARD_CLS  = 'bg-white rounded-3xl shadow-warm p-5';

export function CheckoutPage() {
  const navigate = useNavigate();
  const cart = useCart();
  const toast = useToast();
  const { t, i18n } = useTranslation();

  const [customerName, setCustomerName] = useState('');
  const [customerPhone, setCustomerPhone] = useState('');
  const [deliveryAddress, setDeliveryAddress] = useState('');
  const [deliveryLat, setDeliveryLat] = useState<number | null>(null);
  const [deliveryLng, setDeliveryLng] = useState<number | null>(null);
  const [paymentMethod, setPaymentMethod] = useState<PaymentMethod>('COD');
  const [note, setNote] = useState('');
  const [submitAttempted, setSubmitAttempted] = useState(false);

  const payWithVnpay = usePayWithVnpay();

  const addressError = useMemo<string | null>(() => {
    if (deliveryLat === null || deliveryLng === null) {
      return t('checkout.errorAddress');
    }
    if (!isInVietnam(deliveryLat, deliveryLng)) {
      return t('checkout.errorOutsideVN');
    }
    if (!isInHanoi(deliveryLat, deliveryLng)) {
      return t('checkout.errorOutsideHanoi');
    }
    const shapeIssue = validateAddressString(deliveryAddress);
    if (shapeIssue) return shapeIssue;
    return null;
  }, [deliveryAddress, deliveryLat, deliveryLng, t]);

  const placeOrder = useMutation({
    mutationFn: (req: CreateOrderRequest) => createOrder(api, req),
    onSuccess: order => {
      cart.clear();
      toast.success(t('checkout.success'));
      if (order.paymentMethod === 'VNPAY') {
        navigate(`/customer/orders/${order.id}`, { replace: true });
        payWithVnpay.mutate(order.id);
      } else {
        navigate(`/customer/orders/${order.id}`, { replace: true });
      }
    },
    onError: (err: unknown) => {
      const msg =
        (err as { response?: { data?: { message?: string } } })?.response?.data?.message
        ?? t('checkout.error');
      toast.error(msg);
    },
  });

  const submit = (e: React.FormEvent) => {
    e.preventDefault();
    setSubmitAttempted(true);
    if (cart.items.length === 0) return;
    if (addressError) {
      toast.error(addressError);
      return;
    }
    placeOrder.mutate({
      customerName: customerName || undefined,
      customerPhone: customerPhone || undefined,
      deliveryAddress,
      deliveryLat: String(deliveryLat),
      deliveryLng: String(deliveryLng),
      items: cart.items.map(i => ({ productId: i.product.id, quantity: i.quantity })),
      paymentMethod,
      note: note || undefined,
    });
  };

  if (cart.items.length === 0) {
    return (
      <div className="px-5 pt-6 text-center py-16">
        <p className="text-5xl mb-3">☕</p>
        <p className="font-semibold text-brand-800">{t('cart.emptyTitle')}</p>
      </div>
    );
  }

  const submitDisabled = placeOrder.isPending || addressError !== null;
  const showInlineError = submitAttempted && addressError !== null;

  return (
    <div className="px-4 pt-4 pb-32">
      <button
        type="button"
        onClick={() => navigate(-1)}
        className="mb-2 text-sm text-brand-500 font-medium active:text-brand-700"
      >
        ← {t('checkout.back')}
      </button>
      <h1 className="text-2xl font-bold text-brand-800 mb-5">{t('checkout.title')}</h1>

      <form onSubmit={submit} className="space-y-3">
        <section className={SECTION_CARD_CLS}>
          <h2 className={SECTION_TITLE_CLS}>
            {t('checkout.summary', { count: cart.totalItems() })}
          </h2>
          <div className="space-y-1.5">
            {cart.items.map(i => (
              <div key={i.product.id} className="flex justify-between py-1 text-sm">
                <span className="truncate pr-2 text-brand-800">{i.product.name} × {i.quantity}</span>
                <span className="font-semibold text-brand-800 whitespace-nowrap">
                  {formatVnd(i.product.price * i.quantity, i18n.language)}
                </span>
              </div>
            ))}
          </div>
          <div className="border-t border-brand-100 mt-3 pt-3 flex justify-between font-bold">
            <span className="text-brand-800">{t('checkout.subtotal')}</span>
            <span className="text-brand-700">{formatVnd(cart.subtotal(), i18n.language)}</span>
          </div>
        </section>

        <section className={SECTION_CARD_CLS}>
          <h2 className={SECTION_TITLE_CLS}>{t('checkout.recipient')}</h2>
          <div className="space-y-3">
            <label className="block">
              <span className="text-xs font-medium text-brand-500">{t('checkout.name')}</span>
              <input
                type="text"
                value={customerName}
                onChange={e => setCustomerName(e.target.value)}
                placeholder={t('checkout.namePlaceholderZalo')}
                className={INPUT_CLS}
              />
            </label>
            <label className="block">
              <span className="text-xs font-medium text-brand-500">{t('checkout.phone')}</span>
              <input
                type="tel"
                value={customerPhone}
                onChange={e => setCustomerPhone(e.target.value)}
                placeholder={t('checkout.phonePlaceholder')}
                className={INPUT_CLS}
              />
            </label>

            <div>
              <span className="text-xs font-medium text-brand-500 flex items-center justify-between">
                <span>{t('checkout.address')} <span className="text-rose-500">*</span></span>
                <span className="text-[10px] text-brand-400">{t('checkout.addressRequired')}</span>
              </span>
              <div className="mt-1">
                <AddressPicker
                  address={deliveryAddress}
                  lat={deliveryLat}
                  lng={deliveryLng}
                  onChange={({ address, lat, lng }) => {
                    setDeliveryAddress(address);
                    setDeliveryLat(lat);
                    setDeliveryLng(lng);
                  }}
                  error={showInlineError ? (addressError ?? undefined) : undefined}
                />
              </div>
            </div>

            <label className="block">
              <span className="text-xs font-medium text-brand-500">{t('checkout.note')}</span>
              <textarea
                value={note}
                onChange={e => setNote(e.target.value)}
                placeholder={t('checkout.notePlaceholder')}
                className={INPUT_CLS + ' resize-none'}
                rows={2}
              />
            </label>
          </div>
        </section>

        <section className={SECTION_CARD_CLS}>
          <h2 className={SECTION_TITLE_CLS}>{t('checkout.paymentMethod')}</h2>
          <div className="space-y-2">
            <PaymentOption
              value="COD"
              checked={paymentMethod === 'COD'}
              onChange={() => setPaymentMethod('COD')}
              icon="💵"
              title={t('checkout.cod')}
              subtitle={t('checkout.codDescription')}
            />
            <PaymentOption
              value="VNPAY"
              checked={paymentMethod === 'VNPAY'}
              onChange={() => setPaymentMethod('VNPAY')}
              icon="💳"
              title={t('checkout.vnpay')}
              subtitle={t('checkout.vnpayDescription')}
            />
          </div>
        </section>

        <button
          type="submit"
          disabled={submitDisabled}
          className="fixed bottom-20 left-4 right-4 max-w-md mx-auto bg-brand-700 text-cream-50 rounded-2xl py-4 px-4 font-bold shadow-warm-lg active:scale-[0.98] transition disabled:bg-brand-200 disabled:text-brand-400 disabled:shadow-none"
        >
          {placeOrder.isPending
            ? t('checkout.placing')
            : addressError
              ? t('checkout.cta.disabled')
              : t('checkout.cta.ready', { total: formatVnd(cart.subtotal(), i18n.language) })}
        </button>
      </form>
    </div>
  );
}

function PaymentOption({
  value, checked, onChange, icon, title, subtitle,
}: {
  value: string;
  checked: boolean;
  onChange: () => void;
  icon: string;
  title: string;
  subtitle: string;
}) {
  return (
    <label
      className={
        'flex items-center gap-3 py-3 px-3.5 rounded-2xl cursor-pointer transition border-2 ' +
        (checked
          ? 'border-brand-600 bg-brand-50 shadow-warm'
          : 'border-brand-100 bg-brand-50/40 hover:border-brand-200')
      }
    >
      <input
        type="radio"
        name="payment"
        value={value}
        checked={checked}
        onChange={onChange}
        className="accent-brand-700"
      />
      <span className="text-2xl" aria-hidden="true">{icon}</span>
      <span className="flex-1 min-w-0">
        <span className="block font-semibold text-sm text-brand-800">{title}</span>
        <span className="block text-xs text-brand-500 line-clamp-1">{subtitle}</span>
      </span>
    </label>
  );
}

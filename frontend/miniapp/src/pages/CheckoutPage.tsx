import { useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useMutation } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import { createOrder, formatVnd, type CreateOrderRequest, type PaymentMethod } from '@shop/shared';
import { api } from '@/lib/api';
import { useCart } from '@/features/cart/use-cart';
import { tg } from '@/lib/telegram';
import { usePayWithVnpay } from '@/features/payment/use-pay-with-vnpay';
import { useToast } from '@/components/Toast';
import { AddressPicker } from '@/features/address/AddressPicker';
import { isInHanoi, isInVietnam, validateAddressString } from '@/features/address/nominatim';

const INPUT_CLS =
  'mt-1 w-full px-3.5 py-2.5 rounded-xl bg-gray-50 border border-gray-200 ' +
  'text-sm focus:outline-none focus:ring-2 focus:ring-orange-500 focus:border-orange-500';

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

  // Compute a single validation error message — used for both inline banner and submit guard.
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
    onError: async (err: unknown) => {
      const msg =
        (err as { response?: { data?: { message?: string } } })?.response?.data?.message
        ?? t('checkout.error');
      if (tg.isInTelegram()) await tg.showAlert(msg);
      else toast.error(msg);
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
    // addressError === null implies lat/lng non-null
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
      <div className="text-center py-16">
        <p className="text-5xl mb-3">🛒</p>
        <p className="font-medium text-gray-700">{t('cart.empty')}</p>
      </div>
    );
  }

  const submitDisabled = placeOrder.isPending || addressError !== null;
  const showInlineError = submitAttempted && addressError !== null;

  return (
    <div className="pb-32">
      <button
        onClick={() => navigate(-1)}
        className="mb-2 text-sm text-gray-500 active:text-gray-700"
      >
        {t('checkout.back')}
      </button>
      <h1 className="text-2xl font-bold mb-4">{t('checkout.title')}</h1>

      <form onSubmit={submit} className="space-y-4">
        {/* Order summary card */}
        <section className="bg-white rounded-2xl shadow-sm border border-gray-100 p-4">
          <h2 className="text-sm font-semibold text-gray-500 mb-2">
            {t('checkout.summary', { count: cart.totalItems() })}
          </h2>
          {cart.items.map(i => (
            <div key={i.product.id} className="flex justify-between py-1 text-sm">
              <span className="truncate pr-2">{i.product.name} × {i.quantity}</span>
              <span className="font-medium whitespace-nowrap">{formatVnd(i.product.price * i.quantity, i18n.language)}</span>
            </div>
          ))}
          <div className="border-t border-gray-100 mt-2 pt-2 flex justify-between font-bold">
            <span>{t('checkout.subtotal')}</span>
            <span className="text-orange-600">{formatVnd(cart.subtotal(), i18n.language)}</span>
          </div>
        </section>

        {/* Recipient info card */}
        <section className="bg-white rounded-2xl shadow-sm border border-gray-100 p-4 space-y-3">
          <h2 className="text-sm font-semibold text-gray-500">{t('checkout.recipient')}</h2>
          <label className="block">
            <span className="text-xs text-gray-500">{t('checkout.name')}</span>
            <input
              type="text"
              value={customerName}
              onChange={e => setCustomerName(e.target.value)}
              placeholder={t('checkout.namePlaceholder')}
              className={INPUT_CLS}
            />
          </label>
          <label className="block">
            <span className="text-xs text-gray-500">{t('checkout.phone')}</span>
            <input
              type="tel"
              value={customerPhone}
              onChange={e => setCustomerPhone(e.target.value)}
              placeholder={t('checkout.phonePlaceholder')}
              className={INPUT_CLS}
            />
          </label>

          <div>
            <span className="text-xs text-gray-500 flex items-center justify-between">
              <span>{t('checkout.address')} <span className="text-red-500">*</span></span>
              <span className="text-[10px] text-gray-400">{t('checkout.addressRequired')}</span>
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
            <span className="text-xs text-gray-500">{t('checkout.note')}</span>
            <textarea
              value={note}
              onChange={e => setNote(e.target.value)}
              placeholder={t('checkout.notePlaceholder')}
              className={INPUT_CLS + ' resize-none'}
              rows={2}
            />
          </label>
        </section>

        {/* Payment method card */}
        <section className="bg-white rounded-2xl shadow-sm border border-gray-100 p-4">
          <h2 className="text-sm font-semibold text-gray-500 mb-2">{t('checkout.paymentMethod')}</h2>
          <PaymentOption
            value="COD"
            checked={paymentMethod === 'COD'}
            onChange={() => setPaymentMethod('COD')}
            icon="💰"
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
        </section>

        <button
          type="submit"
          disabled={submitDisabled}
          className="fixed bottom-4 left-4 right-4 max-w-md mx-auto bg-orange-500 text-white rounded-2xl py-3.5 px-4 font-semibold shadow-xl shadow-orange-500/30 active:scale-[0.98] transition disabled:bg-gray-300 disabled:shadow-none"
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
        'flex items-center gap-3 py-2.5 px-3 rounded-xl cursor-pointer transition border ' +
        (checked
          ? 'border-orange-500 bg-orange-50'
          : 'border-transparent hover:bg-gray-50')
      }
    >
      <input
        type="radio"
        name="payment"
        value={value}
        checked={checked}
        onChange={onChange}
        className="accent-orange-500"
      />
      <span className="text-2xl">{icon}</span>
      <span className="flex-1 min-w-0">
        <span className="block font-semibold text-sm">{title}</span>
        <span className="block text-xs text-gray-500 line-clamp-1">{subtitle}</span>
      </span>
    </label>
  );
}

import { useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useMutation } from '@tanstack/react-query';
import { createOrder, formatVnd, type CreateOrderRequest, type PaymentMethod } from '@shop/shared';
import { api } from '@/lib/api';
import { useCart } from '@/features/cart/use-cart';
import { usePayWithVnpay } from '@/features/payment/use-pay-with-vnpay';
import { useToast } from '@/components/Toast';
import { AddressPicker } from '@/features/address/AddressPicker';
import { isInHanoi, isInVietnam, validateAddressString } from '@/features/address/nominatim';

const INPUT_CLS =
  'mt-1 w-full px-3.5 py-2.5 rounded-xl bg-gray-50 border border-gray-200 ' +
  'text-sm focus:outline-none focus:ring-2 focus:ring-zalo focus:border-zalo';

export function CheckoutPage() {
  const navigate = useNavigate();
  const cart = useCart();
  const toast = useToast();

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
      return 'Vui lòng chọn vị trí giao hàng trên bản đồ.';
    }
    if (!isInVietnam(deliveryLat, deliveryLng)) {
      return 'Toạ độ ngoài lãnh thổ Việt Nam.';
    }
    if (!isInHanoi(deliveryLat, deliveryLng)) {
      return 'Shop hiện chỉ giao trong nội thành Hà Nội.';
    }
    const shapeIssue = validateAddressString(deliveryAddress);
    if (shapeIssue) return shapeIssue;
    return null;
  }, [deliveryAddress, deliveryLat, deliveryLng]);

  const placeOrder = useMutation({
    mutationFn: (req: CreateOrderRequest) => createOrder(api, req),
    onSuccess: order => {
      cart.clear();
      toast.success('Đặt hàng thành công!');
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
        ?? 'Đặt đơn thất bại';
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
      <div className="text-center py-16">
        <p className="text-5xl mb-3">🛒</p>
        <p className="font-medium text-gray-700">Giỏ hàng trống</p>
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
        ← Quay lại
      </button>
      <h1 className="text-2xl font-bold mb-4">Xác nhận đặt hàng</h1>

      <form onSubmit={submit} className="space-y-4">
        <section className="bg-white rounded-2xl shadow-sm border border-gray-100 p-4">
          <h2 className="text-sm font-semibold text-gray-500 mb-2">
            Sản phẩm ({cart.totalItems()})
          </h2>
          {cart.items.map(i => (
            <div key={i.product.id} className="flex justify-between py-1 text-sm">
              <span className="truncate pr-2">{i.product.name} × {i.quantity}</span>
              <span className="font-medium whitespace-nowrap">{formatVnd(i.product.price * i.quantity)}</span>
            </div>
          ))}
          <div className="border-t border-gray-100 mt-2 pt-2 flex justify-between font-bold">
            <span>Tạm tính</span>
            <span className="text-zalo">{formatVnd(cart.subtotal())}</span>
          </div>
        </section>

        <section className="bg-white rounded-2xl shadow-sm border border-gray-100 p-4 space-y-3">
          <h2 className="text-sm font-semibold text-gray-500">Thông tin người nhận</h2>
          <label className="block">
            <span className="text-xs text-gray-500">Tên người nhận</span>
            <input
              type="text"
              value={customerName}
              onChange={e => setCustomerName(e.target.value)}
              placeholder="Để trống = dùng tên Zalo"
              className={INPUT_CLS}
            />
          </label>
          <label className="block">
            <span className="text-xs text-gray-500">Số điện thoại</span>
            <input
              type="tel"
              value={customerPhone}
              onChange={e => setCustomerPhone(e.target.value)}
              placeholder="+849xxxxxxxx"
              className={INPUT_CLS}
            />
          </label>

          <div>
            <span className="text-xs text-gray-500 flex items-center justify-between">
              <span>Địa chỉ giao <span className="text-red-500">*</span></span>
              <span className="text-[10px] text-gray-400">Bắt buộc chọn trên bản đồ</span>
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
            <span className="text-xs text-gray-500">Ghi chú (tuỳ chọn)</span>
            <textarea
              value={note}
              onChange={e => setNote(e.target.value)}
              placeholder="Vd: Giao tối 6–8h, gọi trước khi đến…"
              className={INPUT_CLS + ' resize-none'}
              rows={2}
            />
          </label>
        </section>

        <section className="bg-white rounded-2xl shadow-sm border border-gray-100 p-4">
          <h2 className="text-sm font-semibold text-gray-500 mb-2">Phương thức thanh toán</h2>
          <PaymentOption
            value="COD"
            checked={paymentMethod === 'COD'}
            onChange={() => setPaymentMethod('COD')}
            icon="💰"
            title="Thanh toán khi nhận hàng"
            subtitle="Trả tiền mặt cho shipper khi nhận đơn"
          />
          <PaymentOption
            value="VNPAY"
            checked={paymentMethod === 'VNPAY'}
            onChange={() => setPaymentMethod('VNPAY')}
            icon="💳"
            title="VNPay (sandbox)"
            subtitle="Quét QR / ATM / thẻ — xác nhận tức thì"
          />
        </section>

        <button
          type="submit"
          disabled={submitDisabled}
          className="fixed bottom-4 left-4 right-4 max-w-md mx-auto bg-zalo text-white rounded-2xl py-3.5 px-4 font-semibold shadow-xl shadow-zalo/30 active:scale-[0.98] transition disabled:bg-gray-300 disabled:shadow-none"
        >
          {placeOrder.isPending
            ? 'Đang đặt…'
            : addressError
              ? 'Chọn địa chỉ trên bản đồ để tiếp tục'
              : `Đặt hàng • ${formatVnd(cart.subtotal())} + ship`}
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
          ? 'border-zalo bg-blue-50'
          : 'border-transparent hover:bg-gray-50')
      }
    >
      <input
        type="radio"
        name="payment"
        value={value}
        checked={checked}
        onChange={onChange}
        className="accent-zalo"
      />
      <span className="text-2xl">{icon}</span>
      <span className="flex-1 min-w-0">
        <span className="block font-semibold text-sm">{title}</span>
        <span className="block text-xs text-gray-500 line-clamp-1">{subtitle}</span>
      </span>
    </label>
  );
}

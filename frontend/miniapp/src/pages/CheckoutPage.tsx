import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useMutation } from '@tanstack/react-query';
import { createOrder, formatVnd, type CreateOrderRequest, type PaymentMethod } from '@shop/shared';
import { api } from '@/lib/api';
import { useCart } from '@/features/cart/use-cart';
import { tg } from '@/lib/telegram';
import { usePayWithVnpay } from '@/features/payment/use-pay-with-vnpay';

export function CheckoutPage() {
  const navigate = useNavigate();
  const cart = useCart();

  const [customerName, setCustomerName] = useState('');
  const [customerPhone, setCustomerPhone] = useState('');
  const [deliveryAddress, setDeliveryAddress] = useState('');
  const [deliveryLat] = useState('21.0193');
  const [deliveryLng] = useState('105.8503');
  const [paymentMethod, setPaymentMethod] = useState<PaymentMethod>('COD');
  const [note, setNote] = useState('');

  const payWithVnpay = usePayWithVnpay();

  const placeOrder = useMutation({
    mutationFn: (req: CreateOrderRequest) => createOrder(api, req),
    onSuccess: order => {
      cart.clear();
      if (order.paymentMethod === 'VNPAY') {
        // 1. Navigate to detail page first — when the overlay closes,
        //    the user lands on the order detail with polling active.
        navigate(`/customer/orders/${order.id}`, { replace: true });
        // 2. Fire-and-forget: open VNPay in Telegram overlay
        payWithVnpay.mutate(order.id);
      } else {
        navigate(`/customer/orders/${order.id}`, { replace: true });
      }
    },
    onError: async (err: any) => {
      const msg = err.response?.data?.message ?? 'Đặt đơn thất bại';
      if (tg.isInTelegram()) await tg.showAlert(msg);
      else alert(msg);
    },
  });

  const submit = (e: React.FormEvent) => {
    e.preventDefault();
    if (cart.items.length === 0) return;
    placeOrder.mutate({
      customerName: customerName || undefined,
      customerPhone: customerPhone || undefined,
      deliveryAddress,
      deliveryLat,
      deliveryLng,
      items: cart.items.map(i => ({ productId: i.product.id, quantity: i.quantity })),
      paymentMethod,
      note: note || undefined,
    });
  };

  if (cart.items.length === 0) {
    return (
      <div className="text-center py-12">
        <p className="text-tg-hint">Giỏ hàng trống</p>
      </div>
    );
  }

  return (
    <div>
      <h1 className="text-2xl font-bold mb-4">Xác nhận đặt hàng</h1>

      <form onSubmit={submit} className="space-y-4">
        <div className="bg-tg-secondaryBg rounded-lg p-3">
          <p className="text-sm text-tg-hint mb-2">Sản phẩm ({cart.totalItems()})</p>
          {cart.items.map(i => (
            <div key={i.product.id} className="flex justify-between py-1 text-sm">
              <span>{i.product.name} × {i.quantity}</span>
              <span>{formatVnd(i.product.price * i.quantity)}</span>
            </div>
          ))}
          <div className="border-t border-tg-hint/20 mt-2 pt-2 flex justify-between font-bold">
            <span>Tạm tính</span>
            <span>{formatVnd(cart.subtotal())}</span>
          </div>
        </div>

        <label className="block">
          <span className="text-sm text-tg-hint">Tên người nhận</span>
          <input
            type="text"
            value={customerName}
            onChange={e => setCustomerName(e.target.value)}
            placeholder="Để trống = dùng tên Telegram"
            className="mt-1 w-full px-3 py-2 rounded-md bg-tg-secondaryBg border border-tg-hint/30"
          />
        </label>

        <label className="block">
          <span className="text-sm text-tg-hint">SĐT</span>
          <input
            type="tel"
            value={customerPhone}
            onChange={e => setCustomerPhone(e.target.value)}
            placeholder="+849xxxxxxxx"
            className="mt-1 w-full px-3 py-2 rounded-md bg-tg-secondaryBg border border-tg-hint/30"
          />
        </label>

        <label className="block">
          <span className="text-sm text-tg-hint">Địa chỉ giao *</span>
          <input
            type="text"
            value={deliveryAddress}
            onChange={e => setDeliveryAddress(e.target.value)}
            required
            placeholder="Số nhà, đường, phường, quận"
            className="mt-1 w-full px-3 py-2 rounded-md bg-tg-secondaryBg border border-tg-hint/30"
          />
          <p className="text-xs text-tg-hint mt-1">
            (P6 sẽ thay bằng map picker — hiện gửi tọa độ default Bà Triệu)
          </p>
        </label>

        <fieldset className="block">
          <legend className="text-sm text-tg-hint mb-2">Phương thức thanh toán</legend>
          <label className="flex items-center gap-2 py-2">
            <input
              type="radio"
              name="payment"
              value="COD"
              checked={paymentMethod === 'COD'}
              onChange={() => setPaymentMethod('COD')}
            />
            <span>💰 Thanh toán khi nhận hàng (COD)</span>
          </label>
          <label className="flex items-center gap-2 py-2">
            <input
              type="radio"
              name="payment"
              value="VNPAY"
              checked={paymentMethod === 'VNPAY'}
              onChange={() => setPaymentMethod('VNPAY')}
            />
            <span>💳 Thanh toán qua VNPay (sandbox)</span>
          </label>
        </fieldset>

        <label className="block">
          <span className="text-sm text-tg-hint">Ghi chú</span>
          <textarea
            value={note}
            onChange={e => setNote(e.target.value)}
            placeholder="Vd: Giao tối 6-8h, gọi trước khi đến..."
            className="mt-1 w-full px-3 py-2 rounded-md bg-tg-secondaryBg border border-tg-hint/30"
            rows={2}
          />
        </label>

        <button
          type="submit"
          disabled={placeOrder.isPending}
          className="w-full py-3 bg-tg-button text-tg-buttonText rounded-lg font-medium disabled:opacity-50"
        >
          {placeOrder.isPending ? 'Đang đặt...' : `Đặt hàng (${formatVnd(cart.subtotal())} + phí ship)`}
        </button>
      </form>
    </div>
  );
}

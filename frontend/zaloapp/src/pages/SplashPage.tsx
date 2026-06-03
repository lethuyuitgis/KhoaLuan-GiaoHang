import { useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { fetchMe } from '@shop/shared';
import { api } from '@/lib/api';
import { zalo } from '@/lib/zalo';

export function SplashPage() {
  const navigate = useNavigate();

  const { data, isLoading, error } = useQuery({
    queryKey: ['me'],
    queryFn: () => fetchMe(api),
    // Only auto-fetch when running inside the Zalo container with a real
    // access token — outside of Zalo we show the dev landing instead.
    enabled: zalo.isInZalo() && !!zalo.accessToken(),
    retry: 0,
  });

  useEffect(() => {
    if (data) {
      navigate('/customer/shop', { replace: true });
    }
  }, [data, navigate]);

  // Out-of-Zalo dev landing — also doubles as the brand splash.
  if (!zalo.isInZalo()) {
    return (
      <div className="-mx-4 -mt-4 min-h-screen flex flex-col">
        <div className="flex-1 bg-gradient-to-br from-zalo to-zalo-dark px-6 pt-16 pb-10 text-white text-center">
          <div className="text-6xl mb-3">🛵</div>
          <h1 className="text-3xl font-bold">Shop Giao Hàng</h1>
          <p className="text-xs uppercase tracking-widest opacity-80 mt-3">Zalo Mini App</p>
          <p className="text-sm opacity-90 mt-2">Đặt món yêu thích — giao tận nơi trong 30 phút</p>
        </div>
        <div className="px-4 py-6 bg-white space-y-3">
          {import.meta.env.DEV && (
            <div className="bg-amber-50 border border-amber-200 rounded-2xl p-3 text-xs text-amber-800 flex items-start gap-2">
              <span>⚠️</span>
              <span>
                Đang chạy ngoài Zalo (dev mode). Ứng dụng đang dùng SDK giả lập —
                tính năng thật sẽ kích hoạt khi mở qua Zalo Mini App Studio.
              </span>
            </div>
          )}
          <button
            onClick={() => navigate('/customer/shop')}
            className="w-full py-3.5 bg-zalo text-white rounded-2xl font-semibold shadow-md shadow-zalo/30 active:scale-[0.98] transition"
          >
            Vào catalog
          </button>
          <button
            onClick={() => navigate('/customer/orders')}
            className="w-full py-3 bg-gray-50 text-gray-700 rounded-2xl font-medium border border-gray-200 active:scale-[0.98] transition"
          >
            Đơn của tôi
          </button>
        </div>
      </div>
    );
  }

  if (isLoading) {
    return (
      <div className="min-h-screen flex flex-col items-center justify-center text-center px-6">
        <div className="text-5xl mb-4">🛵</div>
        <h1 className="text-2xl font-bold text-zalo">Shop Giao Hàng</h1>
        <div className="mt-6 inline-block w-8 h-8 border-2 border-blue-200 border-t-zalo rounded-full animate-spin"></div>
        <p className="mt-4 text-sm text-gray-500">Đang xác thực qua Zalo…</p>
      </div>
    );
  }

  if (error) {
    return (
      <div className="min-h-screen flex flex-col items-center justify-center text-center px-6">
        <div className="text-5xl mb-3">😞</div>
        <h2 className="text-xl font-bold mb-2">Xác thực Zalo thất bại</h2>
        <p className="text-sm text-gray-500">
          Không thể xác thực với server. Vui lòng đóng và mở lại Mini App từ Zalo.
        </p>
      </div>
    );
  }

  return null;
}

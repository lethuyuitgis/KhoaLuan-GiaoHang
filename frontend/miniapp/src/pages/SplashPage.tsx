import { useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { fetchMe } from '@shop/shared';
import { api } from '@/lib/api';
import { tg } from '@/lib/telegram';

export function SplashPage() {
  const navigate = useNavigate();

  const { data, isLoading, error } = useQuery({
    queryKey: ['me'],
    queryFn: () => fetchMe(api),
    enabled: tg.isInTelegram(),
    retry: 0,
  });

  useEffect(() => {
    if (data) {
      if (data.roles.includes('SHIPPER')) {
        navigate('/shipper/assignments', { replace: true });
      } else {
        navigate('/customer/shop', { replace: true });
      }
    }
  }, [data, navigate]);

  if (!tg.isInTelegram()) {
    return (
      <div className="p-6 text-center">
        <div className="bg-yellow-100 text-yellow-800 rounded-md p-4 mb-4">
          ⚠️ Đang chạy ngoài Telegram (dev mode)
        </div>
        <h1 className="text-2xl font-bold mb-2">Shop Giao Hàng</h1>
        <p className="text-tg-hint mb-6">
          Mở Mini App qua Telegram để dùng bình thường. Chế độ dev cho phép xem UI nhưng các API gọi sẽ trả 401.
        </p>
        <button
          onClick={() => navigate('/customer/shop')}
          className="px-4 py-2 bg-tg-button text-tg-buttonText rounded-md"
        >
          Vào catalog (dev)
        </button>
      </div>
    );
  }

  if (isLoading) {
    return (
      <div className="p-6 text-center">
        <div className="inline-block animate-spin rounded-full h-8 w-8 border-b-2 border-tg-button"></div>
        <p className="mt-4 text-tg-hint">Đang xác thực...</p>
      </div>
    );
  }

  if (error) {
    return (
      <div className="p-6 text-center">
        <h2 className="text-xl font-bold mb-2">Xác thực thất bại</h2>
        <p className="text-tg-hint text-sm mb-4">
          Không thể xác thực với server. Vui lòng đóng và mở lại Mini App.
        </p>
      </div>
    );
  }

  return null;
}

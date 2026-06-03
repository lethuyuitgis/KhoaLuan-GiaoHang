import { Link } from 'react-router-dom';

export function NotFoundPage() {
  return (
    <div className="min-h-[70vh] flex flex-col items-center justify-center text-center px-6">
      <p className="text-6xl mb-3">🛵💨</p>
      <h2 className="text-2xl font-bold mb-1">Không tìm thấy trang</h2>
      <p className="text-sm text-gray-500 mb-6 max-w-xs">
        Đường dẫn này không có trong ứng dụng. Có thể bạn vừa gõ nhầm — hoặc shipper đang giao nó đi đâu đó.
      </p>
      <Link
        to="/"
        className="px-5 py-2.5 rounded-2xl bg-zalo text-white font-semibold text-sm shadow-md shadow-zalo/30 active:scale-[0.98] transition"
      >
        Về trang chủ
      </Link>
    </div>
  );
}

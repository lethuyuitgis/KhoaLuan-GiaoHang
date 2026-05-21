import { Link } from 'react-router-dom';

export function NotFoundPage() {
  return (
    <div className="p-6 text-center">
      <h2 className="text-xl font-bold mb-2">Không tìm thấy</h2>
      <Link to="/" className="text-tg-link">Về trang chủ</Link>
    </div>
  );
}

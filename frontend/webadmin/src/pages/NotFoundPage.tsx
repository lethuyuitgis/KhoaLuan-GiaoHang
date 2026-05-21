import { Link } from 'react-router-dom';

export function NotFoundPage() {
  return (
    <div className="p-6">
      <h2 className="text-xl font-bold mb-2">404 — Không tìm thấy</h2>
      <Link to="/" className="text-brand-600 hover:underline">Về dashboard</Link>
    </div>
  );
}

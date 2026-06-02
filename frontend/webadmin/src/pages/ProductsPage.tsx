import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { Link } from 'react-router-dom';
import { formatVnd, type Product, type Page } from '@shop/shared';
import { api } from '@/lib/api';

export function ProductsPage() {
  const qc = useQueryClient();
  const { data, isLoading } = useQuery({
    queryKey: ['admin', 'products', 'list'],
    queryFn: async () => {
      const { data } = await api.get<Page<Product>>('/api/admin/products?size=100');
      return data;
    },
  });

  const toggleActive = useMutation({
    mutationFn: async ({ id, active }: { id: number; active: boolean }) => {
      if (active) {
        await api.delete(`/api/admin/products/${id}`);
      } else {
        await api.post(`/api/admin/products/${id}/activate`);
      }
    },
    onSuccess: () => qc.invalidateQueries({ queryKey: ['admin', 'products', 'list'] }),
  });

  const activeCount = data?.content.filter(p => p.active).length ?? 0;
  const totalCount = data?.content.length ?? 0;

  return (
    <div>
      <div className="mb-6 flex items-end justify-between flex-wrap gap-3">
        <div>
          <h1 className="text-2xl font-bold text-gray-900">Sản phẩm</h1>
          <p className="text-sm text-gray-500 mt-1">{activeCount}/{totalCount} đang bán</p>
        </div>
        <Link to="/products/new"
          className="px-4 py-2 bg-orange-500 hover:bg-orange-600 text-white text-sm font-medium rounded-lg shadow-sm transition-colors flex items-center gap-1.5">
          <svg className="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth="2"><path strokeLinecap="round" d="M12 4v16m8-8H4"/></svg>
          Thêm sản phẩm
        </Link>
      </div>

      <div className="bg-white rounded-xl border border-gray-100 shadow-sm overflow-hidden">
        <table className="w-full text-sm">
          <thead>
            <tr className="bg-gray-50 text-gray-500 text-xs uppercase tracking-wider">
              <th className="px-4 py-3 text-left font-semibold">Sản phẩm</th>
              <th className="px-4 py-3 text-right font-semibold">Giá</th>
              <th className="px-4 py-3 text-right font-semibold">Tồn kho</th>
              <th className="px-4 py-3 text-left font-semibold">Trạng thái</th>
              <th className="px-4 py-3 text-right font-semibold">&nbsp;</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-gray-100">
            {isLoading && Array.from({ length: 4 }).map((_, i) => (
              <tr key={i}><td colSpan={5} className="px-4 py-4"><div className="h-4 bg-gray-100 rounded animate-pulse" /></td></tr>
            ))}
            {!isLoading && data?.content.length === 0 && (
              <tr>
                <td colSpan={5} className="px-4 py-12 text-center text-gray-400">
                  <p className="text-3xl mb-1">📦</p>
                  <p className="text-sm">Chưa có sản phẩm — bấm "Thêm sản phẩm" để bắt đầu</p>
                </td>
              </tr>
            )}
            {data?.content.map(p => (
              <tr key={p.id} className="hover:bg-orange-50/40 transition-colors">
                <td className="px-4 py-3">
                  <div className="flex items-center gap-3">
                    {p.imageUrl ? (
                      <img src={p.imageUrl} alt={p.name} className="w-10 h-10 rounded-lg object-cover bg-gray-100 flex-shrink-0" />
                    ) : (
                      <div className="w-10 h-10 rounded-lg bg-gradient-to-br from-orange-200 to-red-300 flex items-center justify-center text-white font-bold flex-shrink-0">
                        {p.name.charAt(0)}
                      </div>
                    )}
                    <div className="min-w-0">
                      <p className="font-medium text-gray-900">{p.name}</p>
                      {p.description && <p className="text-xs text-gray-500 mt-0.5 truncate max-w-md">{p.description}</p>}
                    </div>
                  </div>
                </td>
                <td className="px-4 py-3 text-right font-semibold text-orange-600 whitespace-nowrap">{formatVnd(p.price)}</td>
                <td className="px-4 py-3 text-right">
                  <span className={`font-medium ${p.stock <= 10 ? 'text-red-600' : 'text-gray-900'}`}>{p.stock}</span>
                </td>
                <td className="px-4 py-3">
                  <span className={`inline-flex items-center gap-1.5 px-2.5 py-1 rounded-full text-xs font-medium ${
                    p.active ? 'bg-green-50 text-green-700' : 'bg-gray-100 text-gray-600'
                  }`}>
                    <span className={`w-1.5 h-1.5 rounded-full ${p.active ? 'bg-green-500' : 'bg-gray-400'}`}></span>
                    {p.active ? 'Đang bán' : 'Tạm ngưng'}
                  </span>
                </td>
                <td className="px-4 py-3 text-right space-x-3 whitespace-nowrap">
                  <Link to={`/products/${p.id}/edit`} className="text-orange-600 hover:text-orange-700 text-sm font-medium">Sửa</Link>
                  <button
                    onClick={() => toggleActive.mutate({ id: p.id, active: p.active })}
                    className="text-gray-500 hover:text-gray-700 text-sm font-medium"
                  >
                    {p.active ? 'Tạm ngưng' : 'Kích hoạt'}
                  </button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
}

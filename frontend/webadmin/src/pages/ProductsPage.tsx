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

  return (
    <div>
      <div className="flex items-center justify-between mb-6">
        <h1 className="text-2xl font-bold">Sản phẩm</h1>
        <Link to="/products/new" className="px-4 py-2 bg-brand-600 text-white rounded-md hover:bg-brand-700">
          + Thêm sản phẩm
        </Link>
      </div>

      {isLoading && <p className="text-gray-600">Đang tải...</p>}

      <div className="bg-white rounded-lg shadow overflow-hidden">
        <table className="w-full text-sm">
          <thead className="bg-gray-50 text-gray-600">
            <tr>
              <th className="px-4 py-2 text-left">Tên</th>
              <th className="px-4 py-2 text-right">Giá</th>
              <th className="px-4 py-2 text-right">Tồn</th>
              <th className="px-4 py-2 text-left">Trạng thái</th>
              <th className="px-4 py-2 text-right">Hành động</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-gray-100">
            {data?.content.length === 0 && (
              <tr><td colSpan={5} className="px-4 py-8 text-center text-gray-500">Chưa có sản phẩm</td></tr>
            )}
            {data?.content.map(p => (
              <tr key={p.id} className="hover:bg-gray-50">
                <td className="px-4 py-3">
                  <p className="font-medium">{p.name}</p>
                  {p.description && <p className="text-xs text-gray-500 mt-0.5 truncate max-w-md">{p.description}</p>}
                </td>
                <td className="px-4 py-3 text-right font-medium">{formatVnd(p.price)}</td>
                <td className="px-4 py-3 text-right">{p.stock}</td>
                <td className="px-4 py-3">
                  <span className={`px-2 py-0.5 rounded-full text-xs ${p.active ? 'bg-green-100 text-green-800' : 'bg-gray-200 text-gray-700'}`}>
                    {p.active ? 'Đang bán' : 'Tạm ngưng'}
                  </span>
                </td>
                <td className="px-4 py-3 text-right space-x-2">
                  <Link to={`/products/${p.id}/edit`} className="text-brand-600 hover:underline">Sửa</Link>
                  <button
                    onClick={() => toggleActive.mutate({ id: p.id, active: p.active })}
                    className="text-gray-600 hover:underline"
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

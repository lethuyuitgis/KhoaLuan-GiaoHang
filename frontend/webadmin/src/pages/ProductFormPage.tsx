import { useState, useEffect, type FormEvent } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { useQuery, useMutation } from '@tanstack/react-query';
import type { Product } from '@shop/shared';
import { api } from '@/lib/api';

export function ProductFormPage() {
  const { id } = useParams<{ id?: string }>();
  const navigate = useNavigate();
  const isEdit = !!id;

  const { data: existing, isLoading } = useQuery({
    queryKey: ['admin', 'product', id],
    queryFn: async () => {
      const { data } = await api.get<Product>(`/api/products/${id}`);
      return data;
    },
    enabled: isEdit,
  });

  const [name, setName] = useState('');
  const [description, setDescription] = useState('');
  const [price, setPrice] = useState('');
  const [imageUrl, setImageUrl] = useState('');
  const [stock, setStock] = useState('');

  useEffect(() => {
    if (existing) {
      setName(existing.name);
      setDescription(existing.description ?? '');
      setPrice(String(existing.price));
      setImageUrl(existing.imageUrl ?? '');
      setStock(String(existing.stock));
    }
  }, [existing]);

  const saveMut = useMutation({
    mutationFn: async () => {
      const body = {
        name,
        description: description || undefined,
        price: Number(price),
        imageUrl: imageUrl || undefined,
        stock: Number(stock || 0),
      };
      if (isEdit) {
        await api.put(`/api/admin/products/${id}`, body);
      } else {
        await api.post('/api/admin/products', body);
      }
    },
    onSuccess: () => navigate('/products', { replace: true }),
  });

  if (isEdit && isLoading) return <p className="text-gray-600">Đang tải...</p>;

  return (
    <div className="max-w-lg">
      <h1 className="text-2xl font-bold mb-6">
        {isEdit ? 'Sửa sản phẩm' : 'Thêm sản phẩm'}
      </h1>

      <form
        onSubmit={(e: FormEvent) => { e.preventDefault(); saveMut.mutate(); }}
        className="bg-white rounded-lg shadow p-6 space-y-4"
      >
        <label className="block">
          <span className="text-sm text-gray-700">Tên sản phẩm *</span>
          <input type="text" required value={name} onChange={e => setName(e.target.value)}
            className="mt-1 w-full px-3 py-2 border border-gray-300 rounded-md" />
        </label>

        <label className="block">
          <span className="text-sm text-gray-700">Mô tả</span>
          <textarea value={description} onChange={e => setDescription(e.target.value)} rows={3}
            className="mt-1 w-full px-3 py-2 border border-gray-300 rounded-md" />
        </label>

        <label className="block">
          <span className="text-sm text-gray-700">Giá (VND) *</span>
          <input type="number" required min={0} value={price} onChange={e => setPrice(e.target.value)}
            className="mt-1 w-full px-3 py-2 border border-gray-300 rounded-md" />
        </label>

        <label className="block">
          <span className="text-sm text-gray-700">URL ảnh</span>
          <input type="url" value={imageUrl} onChange={e => setImageUrl(e.target.value)} placeholder="https://..."
            className="mt-1 w-full px-3 py-2 border border-gray-300 rounded-md" />
        </label>

        <label className="block">
          <span className="text-sm text-gray-700">Tồn kho</span>
          <input type="number" min={0} value={stock} onChange={e => setStock(e.target.value)}
            className="mt-1 w-full px-3 py-2 border border-gray-300 rounded-md" />
        </label>

        <div className="flex gap-2 pt-2">
          <button type="submit" disabled={saveMut.isPending}
            className="flex-1 py-2 bg-brand-600 text-white rounded-md hover:bg-brand-700 disabled:opacity-50">
            {saveMut.isPending ? 'Đang lưu...' : 'Lưu'}
          </button>
          <button type="button" onClick={() => navigate('/products')}
            className="px-4 py-2 border border-gray-300 rounded-md hover:bg-gray-50">
            Hủy
          </button>
        </div>
      </form>
    </div>
  );
}

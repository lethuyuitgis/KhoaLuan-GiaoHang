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
  const [category, setCategory] = useState<'food' | 'drink' | 'dessert'>('food');
  const [stock, setStock] = useState('');
  const [uploadError, setUploadError] = useState<string | null>(null);

  useEffect(() => {
    if (existing) {
      setName(existing.name);
      setDescription(existing.description ?? '');
      setPrice(String(existing.price));
      setImageUrl(existing.imageUrl ?? '');
      setCategory(existing.category ?? 'food');
      setStock(String(existing.stock));
    }
  }, [existing]);

  // Upload file → backend lưu vào ổ đĩa và trả về URL nội bộ /api/files/...
  const uploadMut = useMutation({
    mutationFn: async (file: File) => {
      const form = new FormData();
      form.append('file', file);
      const { data } = await api.post<{ url: string }>('/api/admin/products/images', form, {
        headers: { 'Content-Type': 'multipart/form-data' },
      });
      return data.url;
    },
    onSuccess: url => { setImageUrl(url); setUploadError(null); },
    onError: (err: any) => setUploadError(err.response?.data?.message ?? 'Upload ảnh thất bại'),
  });

  const saveMut = useMutation({
    mutationFn: async () => {
      const body = {
        name,
        description: description || undefined,
        price: Number(price),
        imageUrl: imageUrl || undefined,
        category,
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
          <span className="text-sm text-gray-700">Danh mục *</span>
          <select value={category} onChange={e => setCategory(e.target.value as typeof category)}
            className="mt-1 w-full px-3 py-2 border border-gray-300 rounded-md bg-white">
            <option value="food">🍜 Món ăn</option>
            <option value="drink">☕ Đồ uống</option>
            <option value="dessert">🍰 Tráng miệng</option>
          </select>
        </label>

        <div className="block">
          <span className="text-sm text-gray-700">Ảnh sản phẩm</span>
          <div className="mt-1 flex items-start gap-3">
            {imageUrl ? (
              <img src={imageUrl} alt="Ảnh sản phẩm"
                className="w-24 h-24 rounded-lg object-cover border border-gray-200 flex-shrink-0" />
            ) : (
              <div className="w-24 h-24 rounded-lg border border-dashed border-gray-300 flex items-center justify-center text-2xl text-gray-300 flex-shrink-0">
                🖼
              </div>
            )}
            <div className="flex-1 space-y-1.5">
              <label className={`inline-block px-3 py-2 rounded-md border text-sm font-medium cursor-pointer transition ${
                uploadMut.isPending
                  ? 'bg-gray-100 text-gray-400 border-gray-200 cursor-wait'
                  : 'bg-white text-gray-700 border-gray-300 hover:bg-gray-50'
              }`}>
                {uploadMut.isPending ? 'Đang tải lên…' : imageUrl ? '🔄 Đổi ảnh' : '📤 Chọn ảnh từ máy'}
                <input
                  type="file"
                  accept="image/jpeg,image/png,image/webp"
                  className="hidden"
                  disabled={uploadMut.isPending}
                  onChange={e => {
                    const f = e.target.files?.[0];
                    if (f) uploadMut.mutate(f);
                    e.target.value = ''; // cho phép chọn lại cùng file
                  }}
                />
              </label>
              <p className="text-xs text-gray-400">JPG / PNG / WebP, tối đa 5MB</p>
              {uploadError && <p className="text-xs text-red-600">{uploadError}</p>}
            </div>
          </div>
        </div>

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

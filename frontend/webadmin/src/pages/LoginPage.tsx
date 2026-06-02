import { useState, type FormEvent } from 'react';
import { useNavigate, useLocation } from 'react-router-dom';
import { useMutation } from '@tanstack/react-query';
import { api } from '@/lib/api';
import { useAuthStore } from '@/stores/auth-store';

interface TokenResponse {
  accessToken: string;
  refreshToken: string;
  adminUserId: number;
  email: string;
}

export function LoginPage() {
  const navigate = useNavigate();
  const location = useLocation();
  const setAuth = useAuthStore(s => s.setAuth);

  const [email, setEmail] = useState('shop@example.com');
  const [password, setPassword] = useState('');
  const [error, setError] = useState<string | null>(null);

  const loginMut = useMutation({
    mutationFn: async (vars: { email: string; password: string }) => {
      const { data } = await api.post<TokenResponse>('/api/admin/auth/login', vars);
      return data;
    },
    onSuccess: data => {
      setAuth({
        accessToken: data.accessToken,
        refreshToken: data.refreshToken,
        adminUserId: data.adminUserId,
        email: data.email,
      });
      const from = (location.state as any)?.from?.pathname ?? '/';
      navigate(from, { replace: true });
    },
    onError: (err: any) => {
      setError(err.response?.data?.message ?? 'Đăng nhập thất bại');
    },
  });

  const onSubmit = (e: FormEvent) => {
    e.preventDefault();
    setError(null);
    loginMut.mutate({ email, password });
  };

  return (
    <div className="min-h-screen flex items-center justify-center bg-gray-50 px-4">
      <div className="w-full max-w-sm bg-white rounded-lg shadow p-8">
        <h1 className="text-2xl font-bold mb-1">Shop Admin</h1>
        <p className="text-sm text-gray-600 mb-6">Đăng nhập để quản lý đơn hàng</p>

        {error && (
          <div className="bg-red-50 border border-red-200 text-red-700 text-sm rounded-md p-3 mb-4">
            {error}
          </div>
        )}

        <form onSubmit={onSubmit} className="space-y-4">
          <label className="block">
            <span className="text-sm text-gray-700">Email</span>
            <input
              type="email"
              required
              value={email}
              onChange={e => setEmail(e.target.value)}
              className="mt-1 w-full px-3 py-2 border border-gray-300 rounded-md focus:ring-2 focus:ring-brand-500 focus:border-brand-500"
              autoComplete="email"
            />
          </label>

          <label className="block">
            <span className="text-sm text-gray-700">Mật khẩu</span>
            <input
              type="password"
              required
              value={password}
              onChange={e => setPassword(e.target.value)}
              className="mt-1 w-full px-3 py-2 border border-gray-300 rounded-md focus:ring-2 focus:ring-brand-500 focus:border-brand-500"
              autoComplete="current-password"
            />
          </label>

          <button
            type="submit"
            disabled={loginMut.isPending}
            className="w-full py-2 bg-brand-600 text-white rounded-md hover:bg-brand-700 disabled:opacity-50 font-medium"
          >
            {loginMut.isPending ? 'Đang đăng nhập...' : 'Đăng nhập'}
          </button>
        </form>

        <p className="mt-6 text-xs text-gray-500 text-center">
          Demo: shop@example.com / Demo@Shop2026! (đổi sau khi setup)
        </p>
      </div>
    </div>
  );
}

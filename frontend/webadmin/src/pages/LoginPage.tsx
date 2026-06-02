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
    <div className="min-h-screen flex items-center justify-center bg-gradient-to-br from-orange-50 via-white to-orange-100 px-4 py-8">
      <div className="w-full max-w-md bg-white rounded-2xl shadow-xl p-8 animate-fade-in">
        {/* Brand header */}
        <div className="flex items-center gap-3 mb-7">
          <div className="w-12 h-12 rounded-xl bg-gradient-to-br from-orange-500 to-red-600 flex items-center justify-center text-white font-bold text-2xl shadow-md">
            S
          </div>
          <div>
            <h1 className="text-2xl font-bold text-gray-900 leading-tight">Shop Admin</h1>
            <p className="text-xs text-gray-500 leading-tight">Giao hàng nội thành</p>
          </div>
        </div>

        <div className="mb-6">
          <h2 className="text-lg font-semibold text-gray-800">Đăng nhập</h2>
          <p className="text-sm text-gray-500 mt-0.5">Truy cập bảng điều khiển quản trị</p>
        </div>

        {error && (
          <div
            role="alert"
            className="bg-red-50 border border-red-200 text-red-700 text-sm rounded-lg p-3 mb-4 flex items-start gap-2 animate-fade-in"
          >
            <svg xmlns="http://www.w3.org/2000/svg" className="w-5 h-5 flex-shrink-0 mt-0.5" fill="none"
                 viewBox="0 0 24 24" stroke="currentColor" strokeWidth="2"
                 strokeLinecap="round" strokeLinejoin="round">
              <circle cx="12" cy="12" r="10" />
              <line x1="12" y1="8" x2="12" y2="12" />
              <line x1="12" y1="16" x2="12.01" y2="16" />
            </svg>
            <span>{error}</span>
          </div>
        )}

        <form onSubmit={onSubmit} className="space-y-4">
          <label className="block">
            <span className="text-sm font-medium text-gray-700">Email</span>
            <input
              type="email"
              required
              value={email}
              onChange={e => setEmail(e.target.value)}
              placeholder="ban@shopcuaban.vn"
              className="mt-1.5 w-full px-3.5 py-2.5 border border-gray-300 rounded-lg text-sm focus:ring-2 focus:ring-orange-500 focus:border-orange-500 outline-none transition"
              autoComplete="email"
            />
          </label>

          <label className="block">
            <span className="text-sm font-medium text-gray-700">Mật khẩu</span>
            <input
              type="password"
              required
              value={password}
              onChange={e => setPassword(e.target.value)}
              placeholder="••••••••"
              className="mt-1.5 w-full px-3.5 py-2.5 border border-gray-300 rounded-lg text-sm focus:ring-2 focus:ring-orange-500 focus:border-orange-500 outline-none transition"
              autoComplete="current-password"
            />
          </label>

          <button
            type="submit"
            disabled={loginMut.isPending}
            className="w-full py-2.5 bg-gradient-to-r from-orange-500 to-red-600 text-white rounded-lg font-medium shadow-sm hover:shadow-md active:scale-[0.98] disabled:opacity-60 disabled:cursor-not-allowed disabled:active:scale-100 transition flex items-center justify-center gap-2"
          >
            {loginMut.isPending ? (
              <>
                <svg className="animate-spin w-4 h-4" xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24">
                  <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" />
                  <path className="opacity-75" fill="currentColor"
                        d="M4 12a8 8 0 018-8v4a4 4 0 00-4 4H4z" />
                </svg>
                <span>Đang đăng nhập…</span>
              </>
            ) : (
              <span>Đăng nhập</span>
            )}
          </button>
        </form>

        {/* Demo credentials hint */}
        <div className="mt-6 bg-orange-50/50 border border-orange-100 rounded-lg p-3 flex gap-2.5 text-xs">
          <svg xmlns="http://www.w3.org/2000/svg" className="w-4 h-4 flex-shrink-0 text-orange-500 mt-0.5" fill="none"
               viewBox="0 0 24 24" stroke="currentColor" strokeWidth="2"
               strokeLinecap="round" strokeLinejoin="round">
            <circle cx="12" cy="12" r="10" />
            <line x1="12" y1="16" x2="12" y2="12" />
            <line x1="12" y1="8" x2="12.01" y2="8" />
          </svg>
          <div className="text-gray-700">
            <p className="font-medium text-orange-900 mb-0.5">Tài khoản demo</p>
            <p className="text-gray-600">
              <code className="bg-white/60 px-1 py-0.5 rounded">shop@example.com</code>
              {' / '}
              <code className="bg-white/60 px-1 py-0.5 rounded">Demo@Shop2026!</code>
            </p>
            <p className="text-gray-500 mt-0.5 text-[11px]">Nhớ đổi mật khẩu sau khi setup.</p>
          </div>
        </div>
      </div>
    </div>
  );
}

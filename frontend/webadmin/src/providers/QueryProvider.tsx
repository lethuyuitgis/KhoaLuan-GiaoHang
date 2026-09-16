import { QueryClient, QueryClientProvider, MutationCache } from '@tanstack/react-query';
import { useState, type ReactNode } from 'react';

export function QueryProvider({ children }: { children: ReactNode }) {
  const [client] = useState(() => {
    // eslint-disable-next-line prefer-const
    let qc: QueryClient;
    qc = new QueryClient({
      defaultOptions: {
        queries: { staleTime: 30_000, retry: 1, refetchOnWindowFocus: false },
        mutations: { retry: 0 },
      },
      // Sau MỖI mutation thành công (tạo/sửa/xoá/duyệt…), làm mới toàn bộ dữ liệu
      // admin → mọi danh sách tự cập nhật, KHÔNG phải reload trang thủ công.
      mutationCache: new MutationCache({
        onSuccess: () => qc.invalidateQueries({ queryKey: ['admin'] }),
      }),
    });
    return qc;
  });
  return <QueryClientProvider client={client}>{children}</QueryClientProvider>;
}

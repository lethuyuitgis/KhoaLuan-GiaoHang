import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { render, screen, cleanup, waitFor, fireEvent } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import '@/i18n';

vi.mock('@shop/shared', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@shop/shared')>();
  return { ...actual, validateVoucher: vi.fn() };
});
vi.mock('@/lib/api', () => ({ api: {} }));

import { validateVoucher } from '@shop/shared';
import { VoucherInput } from './VoucherInput';

function renderVoucher(onApplied = vi.fn(), onRemoved = vi.fn()) {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } });
  render(
    <QueryClientProvider client={qc}>
      <VoucherInput label="Mã giảm tiền hàng" target="PRODUCTS" subtotal={120000} deliveryFee={0}
        onApplied={onApplied} onRemoved={onRemoved} />
    </QueryClientProvider>,
  );
  return { onApplied, onRemoved };
}

describe('VoucherInput (zaloapp)', () => {
  beforeEach(() => vi.clearAllMocks());
  afterEach(cleanup);

  it('applies a valid voucher and reports it to the parent', async () => {
    vi.mocked(validateVoucher).mockResolvedValue({
      code: 'SALE10', target: 'PRODUCTS', discountAmount: 10000,
    } as any);
    const { onApplied } = renderVoucher();
    fireEvent.change(screen.getByTestId('voucher-input-PRODUCTS'), { target: { value: 'sale10' } });
    fireEvent.click(screen.getByRole('button'));
    await waitFor(() => expect(onApplied).toHaveBeenCalledWith(expect.objectContaining({ code: 'SALE10' })));
    // sends the code upper-cased
    expect(validateVoucher).toHaveBeenCalledWith({}, expect.objectContaining({ code: 'SALE10', target: 'PRODUCTS' }));
  });

  it('shows an error on an invalid voucher', async () => {
    vi.mocked(validateVoucher).mockRejectedValue(new Error('bad'));
    renderVoucher();
    fireEvent.change(screen.getByTestId('voucher-input-PRODUCTS'), { target: { value: 'nope' } });
    fireEvent.click(screen.getByRole('button'));
    await waitFor(() => expect(screen.getByText(/không hợp lệ|Invalid/i)).toBeInTheDocument());
  });
});

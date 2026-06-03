import { describe, it, expect, vi, afterEach } from 'vitest';
import { render, screen, cleanup } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { VoucherInput } from './VoucherInput';

const wrap = (ui: React.ReactElement) => {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(<QueryClientProvider client={client}>{ui}</QueryClientProvider>);
};

describe('VoucherInput', () => {
  afterEach(cleanup);

  it('renders input + apply button + initial empty state', () => {
    wrap(<VoucherInput label="Mã giảm tiền hàng" target="PRODUCTS"
                       subtotal={120000} deliveryFee={30000}
                       onApplied={vi.fn()} onRemoved={vi.fn()} />);
    expect(screen.getByText(/mã giảm tiền hàng/i)).toBeInTheDocument();
    expect(screen.getByPlaceholderText(/nhập mã/i)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /áp/i })).toBeInTheDocument();
  });

  it('apply button is disabled when input is empty', () => {
    wrap(<VoucherInput label="X" target="PRODUCTS" subtotal={120000} deliveryFee={30000}
                       onApplied={vi.fn()} onRemoved={vi.fn()} />);
    expect(screen.getByRole('button', { name: /áp/i })).toBeDisabled();
  });
});

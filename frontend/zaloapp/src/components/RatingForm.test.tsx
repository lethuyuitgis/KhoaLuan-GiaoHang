import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { render, screen, cleanup, waitFor, fireEvent } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import '@/i18n';

vi.mock('@shop/shared', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@shop/shared')>();
  return { ...actual, rateOrder: vi.fn() };
});
vi.mock('@/lib/api', () => ({ api: {} }));

import { rateOrder } from '@shop/shared';
import { RatingForm } from './RatingForm';

function renderForm() {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } });
  render(
    <QueryClientProvider client={qc}>
      <RatingForm orderId="o1" />
    </QueryClientProvider>,
  );
}

describe('RatingForm (zaloapp)', () => {
  beforeEach(() => vi.clearAllMocks());
  afterEach(cleanup);

  it('disables submit until a star is picked', () => {
    vi.mocked(rateOrder).mockResolvedValue({} as any);
    renderForm();
    expect(screen.getByTestId('rating-submit')).toBeDisabled();
    fireEvent.click(screen.getByTestId('star-4'));
    expect(screen.getByTestId('rating-submit')).not.toBeDisabled();
  });

  it('submits the picked stars + comment via rateOrder', async () => {
    vi.mocked(rateOrder).mockResolvedValue({} as any);
    renderForm();
    fireEvent.click(screen.getByTestId('star-5'));
    fireEvent.change(screen.getByTestId('rating-comment'), { target: { value: 'giao nhanh' } });
    fireEvent.click(screen.getByTestId('rating-submit'));
    await waitFor(() => expect(rateOrder).toHaveBeenCalledWith({}, 'o1', 5, 'giao nhanh'));
  });

  it('shows a thank-you after a successful rating', async () => {
    vi.mocked(rateOrder).mockResolvedValue({} as any);
    renderForm();
    fireEvent.click(screen.getByTestId('star-3'));
    fireEvent.click(screen.getByTestId('rating-submit'));
    await waitFor(() => expect(screen.queryByTestId('rating-submit')).not.toBeInTheDocument());
  });
});

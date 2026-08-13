import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { render, screen, cleanup, within, waitFor, fireEvent } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import type { OrderChatMessage } from '@shop/shared';
import '@/i18n';

vi.mock('@shop/shared', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@shop/shared')>();
  return {
    ...actual,
    getOrderChat: vi.fn(),
    sendOrderChat: vi.fn().mockResolvedValue({}),
  };
});
vi.mock('@/lib/api', () => ({ api: {} }));

import { getOrderChat, sendOrderChat } from '@shop/shared';
import { OrderChat } from './OrderChat';

function renderChat() {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={qc}>
      <OrderChat orderId="o1" active />
    </QueryClientProvider>,
  );
}

describe('OrderChat (zaloapp)', () => {
  beforeEach(() => vi.clearAllMocks());
  afterEach(cleanup);

  it('renders customer and shipper messages (by their text)', async () => {
    const msgs: OrderChatMessage[] = [
      { id: 1, senderRole: 'CUSTOMER', body: 'shipper ơi tới chưa', createdAt: '2026-08-01T00:00:00Z' },
      { id: 2, senderRole: 'SHIPPER', body: 'sắp tới rồi', createdAt: '2026-08-01T00:01:00Z' },
    ];
    vi.mocked(getOrderChat).mockResolvedValue(msgs);
    renderChat();
    const box = await screen.findByTestId('chat-messages');
    expect(await within(box).findByText('shipper ơi tới chưa')).toBeInTheDocument();
    expect(within(box).getByText('sắp tới rồi')).toBeInTheDocument();
  });

  it('shows an empty state when there are no messages', async () => {
    vi.mocked(getOrderChat).mockResolvedValue([]);
    renderChat();
    expect(await screen.findByTestId('chat-empty')).toBeInTheDocument();
  });

  it('sends a typed message via sendOrderChat', async () => {
    vi.mocked(getOrderChat).mockResolvedValue([]);
    renderChat();
    await screen.findByTestId('chat-input');
    fireEvent.change(screen.getByTestId('chat-input'), { target: { value: 'alo shipper' } });
    fireEvent.click(screen.getByTestId('chat-send'));
    await waitFor(() => expect(sendOrderChat).toHaveBeenCalledWith({}, 'o1', 'alo shipper'));
  });

  it('does not send a blank message', async () => {
    vi.mocked(getOrderChat).mockResolvedValue([]);
    renderChat();
    await screen.findByTestId('chat-input');
    fireEvent.change(screen.getByTestId('chat-input'), { target: { value: '   ' } });
    fireEvent.click(screen.getByTestId('chat-send'));
    expect(sendOrderChat).not.toHaveBeenCalled();
  });
});

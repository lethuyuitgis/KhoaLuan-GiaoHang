import { describe, expect, it, vi, beforeEach, afterEach } from 'vitest';
import { render, screen, waitFor, within, cleanup } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { MemoryRouter } from 'react-router-dom';
import type { ShipperResponse } from '@shop/shared';

// Keep formatVnd real; stub only the network calls.
vi.mock('@shop/shared', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@shop/shared')>();
  return {
    ...actual,
    listShippers: vi.fn(),
    approveShipper: vi.fn().mockResolvedValue(undefined),
    rejectShipper: vi.fn().mockResolvedValue(undefined),
    fetchAdminShipperBalance: vi.fn().mockResolvedValue({ balance: 0 }),
    fetchAdminShipperEarnings: vi.fn().mockResolvedValue([]),
  };
});

vi.mock('@/lib/api', () => ({ api: {} }));

import { listShippers, approveShipper, rejectShipper } from '@shop/shared';
import { ShippersPage } from './ShippersPage';

const shipper = (over: Partial<ShipperResponse>): ShipperResponse => ({
  userId: 0, firstName: null, lastName: null, username: null,
  vehicleType: 'MOTORBIKE', licensePlate: null, currentState: 'OFFLINE',
  approvalStatus: 'ACTIVE', ratingAvg: 0, ratingCount: 0, totalDeliveries: 0,
  ...over,
});

function renderPage() {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={qc}>
      <MemoryRouter>
        <ShippersPage />
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

describe('ShippersPage — shipper approval', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(listShippers).mockResolvedValue([
      shipper({ userId: 100, firstName: 'Tài', lastName: 'Xế', approvalStatus: 'PENDING', licensePlate: '29A-12345' }),
      shipper({ userId: 200, firstName: 'Đang', lastName: 'Chạy', approvalStatus: 'ACTIVE' }),
    ]);
  });

  afterEach(() => cleanup());

  it('shows a "Chờ duyệt" panel listing only PENDING shippers', async () => {
    renderPage();
    const heading = await screen.findByText(/Chờ duyệt \(1\)/);
    const panel = heading.closest('div')!.parentElement!;
    expect(within(panel).getByText('Tài Xế')).toBeInTheDocument();
    // The active shipper must NOT be in the pending panel.
    expect(within(panel).queryByText('Đang Chạy')).not.toBeInTheDocument();
  });

  it('calls approveShipper with the shipper id when "Duyệt" is clicked', async () => {
    renderPage();
    await screen.findByText(/Chờ duyệt \(1\)/);
    await userEvent.click(screen.getByRole('button', { name: 'Duyệt' }));
    await waitFor(() => expect(approveShipper).toHaveBeenCalledWith({}, 100));
  });

  it('calls rejectShipper only after the confirm dialog is accepted', async () => {
    const confirmSpy = vi.spyOn(window, 'confirm').mockReturnValue(true);
    renderPage();
    await screen.findByText(/Chờ duyệt \(1\)/);
    await userEvent.click(screen.getByRole('button', { name: 'Từ chối' }));
    expect(confirmSpy).toHaveBeenCalled();
    await waitFor(() => expect(rejectShipper).toHaveBeenCalledWith({}, 100));
    confirmSpy.mockRestore();
  });
});

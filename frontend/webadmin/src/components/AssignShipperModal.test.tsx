import { describe, expect, it, vi, beforeEach, afterEach } from 'vitest';
import { render, screen, waitFor, cleanup } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import type { ShipperCandidateResponse } from '@shop/shared';

vi.mock('@shop/shared', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@shop/shared')>();
  return {
    ...actual,
    getCandidateShippers: vi.fn(),
    assignShipper: vi.fn().mockResolvedValue({}),
  };
});
vi.mock('@/lib/api', () => ({ api: {} }));

import { getCandidateShippers, assignShipper } from '@shop/shared';
import { AssignShipperModal } from './AssignShipperModal';

const cand = (over: Partial<ShipperCandidateResponse>): ShipperCandidateResponse => ({
  userId: 0, firstName: null, lastName: null, username: null,
  vehicleType: 'MOTORBIKE', licensePlate: null,
  ratingAvg: 0, ratingCount: 0, totalDeliveries: 0,
  distanceKm: null, lastLocationAt: null, ...over,
});

function renderModal() {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={qc}>
      <AssignShipperModal orderId="o1" orderCode="DH001" onClose={() => {}} />
    </QueryClientProvider>,
  );
}

describe('AssignShipperModal — distance & rating', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(getCandidateShippers).mockResolvedValue([
      cand({ userId: 1, firstName: 'Gần', ratingAvg: 4.9, ratingCount: 30, distanceKm: 1.2, lastLocationAt: new Date().toISOString() }),
      cand({ userId: 2, firstName: 'Xa', ratingAvg: 3.2, ratingCount: 5, distanceKm: 8.4, lastLocationAt: new Date().toISOString() }),
      cand({ userId: 3, firstName: 'Chưa', ratingAvg: 5.0, ratingCount: 2, distanceKm: null }),
    ]);
  });
  afterEach(() => cleanup());

  it('shows rating stars and a distance label per candidate', async () => {
    renderModal();
    expect(await screen.findByText('Gần')).toBeInTheDocument();
    expect(screen.getByText(/★ 4\.9/)).toBeInTheDocument();
    expect(screen.getByText(/~1\.2 km/)).toBeInTheDocument();
  });

  it('shows "Chưa rõ vị trí" when the shipper has no location', async () => {
    renderModal();
    await screen.findByText('Chưa');
    expect(screen.getByText('Chưa rõ vị trí')).toBeInTheDocument();
  });

  it('defaults to nearest-first ordering', async () => {
    renderModal();
    await screen.findByText('Gần');
    const names = screen.getAllByText(/^(Gần|Xa|Chưa)$/).map(el => el.textContent);
    expect(names).toEqual(['Gần', 'Xa', 'Chưa']); // 1.2km, 8.4km, unknown-last
  });

  it('re-sorts by rating when "Đánh giá cao" is chosen', async () => {
    renderModal();
    await screen.findByText('Gần');
    await userEvent.click(screen.getByRole('button', { name: 'Đánh giá cao' }));
    const names = screen.getAllByText(/^(Gần|Xa|Chưa)$/).map(el => el.textContent);
    expect(names).toEqual(['Chưa', 'Gần', 'Xa']); // 5.0, 4.9, 3.2
  });

  it('assigns the selected shipper', async () => {
    renderModal();
    await userEvent.click(await screen.findByText('Gần'));
    await userEvent.click(screen.getByRole('button', { name: 'Gán shipper' }));
    await waitFor(() => expect(assignShipper).toHaveBeenCalledWith({}, 'o1', 1));
  });
});

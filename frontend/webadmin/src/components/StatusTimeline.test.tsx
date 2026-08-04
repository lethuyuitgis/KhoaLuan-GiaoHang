import { describe, expect, it, afterEach } from 'vitest';
import { render, screen, cleanup, within } from '@testing-library/react';
import type { StatusHistoryResponse } from '@shop/shared';
import { StatusTimeline } from './StatusTimeline';

const entry = (over: Partial<StatusHistoryResponse>): StatusHistoryResponse => ({
  id: 0, fromStatus: null, toStatus: 'PENDING', changedByUserId: null,
  changedAt: '2026-08-04T01:00:00Z', note: null, ...over,
});

describe('StatusTimeline', () => {
  afterEach(() => cleanup());

  it('shows an empty-state message when there are no entries', () => {
    render(<StatusTimeline entries={[]} />);
    expect(screen.getByText(/Chưa có lịch sử/)).toBeInTheDocument();
  });

  it('renders newest entry first (reverses the stored ascending order)', () => {
    render(<StatusTimeline entries={[
      entry({ id: 1, fromStatus: null, toStatus: 'PENDING' }),
      entry({ id: 2, fromStatus: 'PENDING', toStatus: 'CONFIRMED' }),
      entry({ id: 3, fromStatus: 'CONFIRMED', toStatus: 'ASSIGNED' }),
    ]} />);
    const items = screen.getAllByRole('listitem');
    // Newest (ASSIGNED) must be first.
    expect(within(items[0]).getByText('Đã gán shipper')).toBeInTheDocument();
    expect(within(items[2]).getByText('Chờ xác nhận')).toBeInTheDocument();
  });

  it('labels the actor role coarsely from the target status', () => {
    render(<StatusTimeline entries={[
      entry({ id: 1, toStatus: 'CONFIRMED' }),
      entry({ id: 2, fromStatus: 'DELIVERING', toStatus: 'DELIVERED' }),
    ]} />);
    const items = screen.getAllByRole('listitem');
    // Newest first: DELIVERED → Shipper, then CONFIRMED → Chủ shop.
    expect(within(items[0]).getByText('Shipper')).toBeInTheDocument();
    expect(within(items[1]).getByText('Chủ shop')).toBeInTheDocument();
  });

  it('renders the note when present', () => {
    render(<StatusTimeline entries={[entry({ id: 1, toStatus: 'CANCELLED', note: 'Admin hủy' })]} />);
    expect(screen.getByText('Admin hủy')).toBeInTheDocument();
  });
});

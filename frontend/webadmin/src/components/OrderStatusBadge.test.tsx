import { describe, expect, it } from 'vitest';
import { render, screen } from '@testing-library/react';
import { OrderStatusBadge } from './OrderStatusBadge';

describe('OrderStatusBadge', () => {
  it('renders Vietnamese label and amber color for PENDING', () => {
    render(<OrderStatusBadge status="PENDING" />);
    const badge = screen.getByText('Chờ xác nhận');
    expect(badge).toBeInTheDocument();
    expect(badge.className).toContain('bg-yellow-100');
    expect(badge.className).toContain('text-yellow-800');
  });

  it('renders green color and "Đã giao" label for DELIVERED', () => {
    render(<OrderStatusBadge status="DELIVERED" />);
    const badge = screen.getByText('Đã giao');
    expect(badge.className).toContain('bg-green-100');
  });

  it('renders red color for the RETURNED status', () => {
    render(<OrderStatusBadge status="RETURNED" />);
    const badge = screen.getByText('Hoàn hàng');
    expect(badge.className).toContain('bg-red-100');
  });
});

import { describe, expect, it, beforeEach, afterEach } from 'vitest';
import { render, screen, cleanup, within } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { BottomNav } from './BottomNav';
import { useCartStore } from '@/features/cart/cart-store';

function renderAt(pathname: string) {
  return render(
    <MemoryRouter initialEntries={[pathname]}>
      <BottomNav />
    </MemoryRouter>,
  );
}

describe('BottomNav (Zalo)', () => {
  beforeEach(() => {
    useCartStore.setState({ items: [] });
  });

  afterEach(() => {
    cleanup();
  });

  it('does not render on the splash page', () => {
    renderAt('/');
    expect(screen.queryByRole('navigation')).toBeNull();
  });

  it('renders 3 customer tabs on the catalog page', () => {
    renderAt('/customer/shop');
    expect(screen.getByRole('navigation')).toBeInTheDocument();
    expect(screen.getAllByRole('link')).toHaveLength(3);
  });

  it('marks the active tab via aria-current', () => {
    renderAt('/customer/orders');
    const ordersLink = screen.getAllByRole('link').find(a => a.getAttribute('href') === '/customer/orders');
    expect(ordersLink?.getAttribute('aria-current')).toBe('page');
  });

  it('shows a quantity badge when the cart has items', () => {
    useCartStore.setState({
      items: [{
        product: { id: 1, name: 'Trà sữa', price: 40000, stock: 10, description: null, imageUrl: null } as any,
        quantity: 3,
      }],
    });
    renderAt('/customer/shop');
    const nav = screen.getByRole('navigation');
    expect(within(nav).getByText('3')).toBeInTheDocument();
  });
});

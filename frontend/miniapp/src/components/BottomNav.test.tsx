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

describe('BottomNav', () => {
  beforeEach(() => {
    useCartStore.setState({ items: [] });
  });

  afterEach(() => {
    // Vitest's RTL integration doesn't auto-cleanup like Jest's does.
    cleanup();
  });

  it('does not render on the splash page', () => {
    renderAt('/');
    expect(screen.queryByRole('navigation')).toBeNull();
  });

  it('does not render on shipper routes', () => {
    renderAt('/shipper/assignments');
    expect(screen.queryByRole('navigation')).toBeNull();
  });

  it('renders 3 customer tabs on the catalog page', () => {
    renderAt('/customer/shop');
    expect(screen.getByRole('navigation')).toBeInTheDocument();
    // home / cart / orders
    expect(screen.getAllByRole('link')).toHaveLength(3);
  });

  it('marks the active tab via aria-current', () => {
    renderAt('/customer/orders');
    const ordersLink = screen.getAllByRole('link').find(a => a.getAttribute('href') === '/customer/orders');
    expect(ordersLink?.getAttribute('aria-current')).toBe('page');
  });

  it('treats the checkout route as the cart tab being active', () => {
    renderAt('/customer/checkout');
    const cartLink = screen.getAllByRole('link').find(a => a.getAttribute('href') === '/customer/cart');
    expect(cartLink?.getAttribute('aria-current')).toBe('page');
  });

  it('shows a quantity badge when the cart has items', () => {
    useCartStore.setState({
      items: [{
        product: { id: 1, name: 'Cà phê sữa', price: 35000, stock: 10, description: null, imageUrl: null } as any,
        quantity: 2,
      }],
    });
    renderAt('/customer/shop');
    const nav = screen.getByRole('navigation');
    expect(within(nav).getByText('2')).toBeInTheDocument();
  });

  it('caps the badge at "99+" for huge carts', () => {
    useCartStore.setState({
      items: [{
        product: { id: 1, name: 'X', price: 1, stock: 999, description: null, imageUrl: null } as any,
        quantity: 150,
      }],
    });
    renderAt('/customer/shop');
    const nav = screen.getByRole('navigation');
    expect(within(nav).getByText('99+')).toBeInTheDocument();
  });
});

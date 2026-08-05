import { beforeEach, describe, expect, it } from 'vitest';
import type { Product } from '@shop/shared';
import { useCartStore } from './cart-store';

const productA: Product = {
  id: 1,
  name: 'Phở bò tái',
  description: '',
  price: 55_000,
  imageUrl: '',
  category: 'food',
  stock: 100,
  active: true,
  createdAt: '2026-01-01T00:00:00Z',
};

const productB: Product = {
  id: 2,
  name: 'Bún chả',
  description: '',
  price: 60_000,
  imageUrl: '',
  category: 'food',
  stock: 100,
  active: true,
  createdAt: '2026-01-01T00:00:00Z',
};

describe('cart-store', () => {
  beforeEach(() => {
    // Persist middleware writes to localStorage — reset both store + storage.
    localStorage.clear();
    useCartStore.setState({ items: [] });
  });

  it('add() inserts a new line item', () => {
    useCartStore.getState().add(productA, 2);
    const state = useCartStore.getState();
    expect(state.items).toHaveLength(1);
    expect(state.items[0]).toMatchObject({ product: productA, quantity: 2 });
  });

  it('add() merges quantity when the same product is added twice', () => {
    const { add } = useCartStore.getState();
    add(productA, 1);
    add(productA, 3);
    expect(useCartStore.getState().items).toHaveLength(1);
    expect(useCartStore.getState().items[0].quantity).toBe(4);
  });

  it('remove() removes a product by id', () => {
    const { add, remove } = useCartStore.getState();
    add(productA, 1);
    add(productB, 2);
    remove(productA.id);
    const items = useCartStore.getState().items;
    expect(items).toHaveLength(1);
    expect(items[0].product.id).toBe(productB.id);
  });

  it('setQuantity() to 0 removes the line', () => {
    useCartStore.getState().add(productA, 5);
    useCartStore.getState().setQuantity(productA.id, 0);
    expect(useCartStore.getState().items).toHaveLength(0);
  });

  it('subtotal() and totalItems() compute correctly', () => {
    const { add, subtotal, totalItems } = useCartStore.getState();
    add(productA, 2); // 110_000
    add(productB, 3); // 180_000
    expect(subtotal()).toBe(290_000);
    expect(totalItems()).toBe(5);
  });

  it('clear() empties the cart', () => {
    useCartStore.getState().add(productA, 1);
    useCartStore.getState().clear();
    expect(useCartStore.getState().items).toEqual([]);
  });
});

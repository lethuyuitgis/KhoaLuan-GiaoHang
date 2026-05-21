import { create } from 'zustand';
import { persist, createJSONStorage } from 'zustand/middleware';
import type { Product } from '@shop/shared';

export interface CartItem {
  product: Product;
  quantity: number;
}

interface CartState {
  items: CartItem[];
  add: (product: Product, quantity: number) => void;
  remove: (productId: number) => void;
  setQuantity: (productId: number, quantity: number) => void;
  clear: () => void;
  getQuantity: (productId: number) => number;
  subtotal: () => number;
  totalItems: () => number;
}

export const useCartStore = create<CartState>()(
  persist(
    (set, get) => ({
      items: [],

      add: (product, quantity) => set(state => {
        const existing = state.items.find(i => i.product.id === product.id);
        if (existing) {
          return {
            items: state.items.map(i =>
              i.product.id === product.id
                ? { ...i, quantity: i.quantity + quantity }
                : i
            ),
          };
        }
        return { items: [...state.items, { product, quantity }] };
      }),

      remove: productId => set(state => ({
        items: state.items.filter(i => i.product.id !== productId),
      })),

      setQuantity: (productId, quantity) => set(state => {
        if (quantity <= 0) {
          return { items: state.items.filter(i => i.product.id !== productId) };
        }
        return {
          items: state.items.map(i =>
            i.product.id === productId ? { ...i, quantity } : i
          ),
        };
      }),

      clear: () => set({ items: [] }),

      getQuantity: productId =>
        get().items.find(i => i.product.id === productId)?.quantity ?? 0,

      subtotal: () =>
        get().items.reduce((sum, i) => sum + i.product.price * i.quantity, 0),

      totalItems: () =>
        get().items.reduce((sum, i) => sum + i.quantity, 0),
    }),
    {
      name: 'shop-cart',
      storage: createJSONStorage(() => localStorage),
    }
  )
);

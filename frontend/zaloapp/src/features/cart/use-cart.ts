import { useCartStore } from './cart-store';

export function useCart() {
  return useCartStore();
}

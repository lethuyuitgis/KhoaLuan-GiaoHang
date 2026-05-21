import { create } from 'zustand';
import { authStorage, type StoredAuth } from '@/lib/auth-storage';

interface AuthState {
  auth: StoredAuth | null;
  isAuthenticated: boolean;
  setAuth: (auth: StoredAuth) => void;
  updateAccessToken: (newAccessToken: string) => void;
  logout: () => void;
}

export const useAuthStore = create<AuthState>((set) => ({
  auth: authStorage.load(),
  isAuthenticated: authStorage.load() !== null,

  setAuth: (auth) => {
    authStorage.save(auth);
    set({ auth, isAuthenticated: true });
  },

  updateAccessToken: (accessToken) => set(state => {
    if (!state.auth) return state;
    const next = { ...state.auth, accessToken };
    authStorage.save(next);
    return { auth: next };
  }),

  logout: () => {
    authStorage.clear();
    set({ auth: null, isAuthenticated: false });
  },
}));

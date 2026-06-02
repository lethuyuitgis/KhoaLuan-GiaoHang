import { beforeEach, describe, expect, it } from 'vitest';
import { useAuthStore } from './auth-store';

const FRESH_AUTH = {
  accessToken: 'access-token-1',
  refreshToken: 'refresh-token-1',
  adminUserId: 42,
  email: 'shop@example.com',
};

describe('auth-store', () => {
  beforeEach(() => {
    localStorage.clear();
    // Reset Zustand state — store hydrated on import, so reapply explicit clean slate.
    useAuthStore.setState({ auth: null, isAuthenticated: false });
  });

  it('setAuth() persists to localStorage and flips isAuthenticated', () => {
    useAuthStore.getState().setAuth(FRESH_AUTH);
    const state = useAuthStore.getState();
    expect(state.auth).toEqual(FRESH_AUTH);
    expect(state.isAuthenticated).toBe(true);
    expect(localStorage.getItem('webadmin.accessToken')).toBe('access-token-1');
    expect(localStorage.getItem('webadmin.refreshToken')).toBe('refresh-token-1');
  });

  it('updateAccessToken() only swaps the access token, keeps refresh + user', () => {
    useAuthStore.getState().setAuth(FRESH_AUTH);
    useAuthStore.getState().updateAccessToken('access-token-2');
    const state = useAuthStore.getState();
    expect(state.auth?.accessToken).toBe('access-token-2');
    expect(state.auth?.refreshToken).toBe('refresh-token-1');
    expect(state.auth?.email).toBe('shop@example.com');
    expect(localStorage.getItem('webadmin.accessToken')).toBe('access-token-2');
  });

  it('logout() clears state and localStorage', () => {
    useAuthStore.getState().setAuth(FRESH_AUTH);
    useAuthStore.getState().logout();
    const state = useAuthStore.getState();
    expect(state.auth).toBeNull();
    expect(state.isAuthenticated).toBe(false);
    expect(localStorage.getItem('webadmin.accessToken')).toBeNull();
    expect(localStorage.getItem('webadmin.refreshToken')).toBeNull();
    expect(localStorage.getItem('webadmin.user')).toBeNull();
  });
});

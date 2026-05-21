const ACCESS_KEY = 'webadmin.accessToken';
const REFRESH_KEY = 'webadmin.refreshToken';
const USER_KEY = 'webadmin.user';

export interface StoredAuth {
  accessToken: string;
  refreshToken: string;
  adminUserId: number;
  email: string;
}

export const authStorage = {
  load(): StoredAuth | null {
    const at = localStorage.getItem(ACCESS_KEY);
    const rt = localStorage.getItem(REFRESH_KEY);
    const u = localStorage.getItem(USER_KEY);
    if (!at || !rt || !u) return null;
    try {
      const user = JSON.parse(u);
      return { accessToken: at, refreshToken: rt, ...user };
    } catch {
      return null;
    }
  },

  save(auth: StoredAuth) {
    localStorage.setItem(ACCESS_KEY, auth.accessToken);
    localStorage.setItem(REFRESH_KEY, auth.refreshToken);
    localStorage.setItem(USER_KEY, JSON.stringify({
      adminUserId: auth.adminUserId,
      email: auth.email,
    }));
  },

  clear() {
    localStorage.removeItem(ACCESS_KEY);
    localStorage.removeItem(REFRESH_KEY);
    localStorage.removeItem(USER_KEY);
  },
};

/**
 * Nominatim (OpenStreetMap) geocoding helpers.
 *
 * Free, no API key needed, but usage policy mandates:
 *   - Maximum 1 request per second per client
 *   - Include a meaningful User-Agent (cannot be sent from browser; OSM still wants one)
 *   - Include `accept-language` for localized results
 *
 * https://operations.osmfoundation.org/policies/nominatim/
 */

export interface NominatimResult {
  /** Raw OSM display string ("123 Lê Lợi, Hoàn Kiếm, Hà Nội, Việt Nam") */
  displayName: string;
  /** Compact label for dropdown UI (first 2-3 parts) */
  label: string;
  lat: number;
  lng: number;
  /** Structured address parts (countrycodes=vn ⇒ country = "Việt Nam") */
  country?: string;
  countryCode?: string;
}

// Proxy qua domain mình (/osm/*) — webview Zalo chặn domain ngoài + Nominatim
// cấm request thiếu User-Agent. Zalo chạy origin khác → dùng URL TUYỆT ĐỐI từ API base.
const OSM_BASE = (import.meta.env.VITE_API_BASE_URL || '') + '/osm';
const NOMINATIM_SEARCH = OSM_BASE + '/search';
const NOMINATIM_REVERSE = OSM_BASE + '/reverse';

/**
 * Forward geocoding. Returns at most `limit` results inside Vietnam.
 * Throws on network/HTTP failure — caller can swallow to show "no results".
 */
export async function searchAddress(
  query: string,
  options: { signal?: AbortSignal; limit?: number } = {},
): Promise<NominatimResult[]> {
  const trimmed = query.trim();
  if (trimmed.length < 3) return [];

  const url = new URL(NOMINATIM_SEARCH);
  url.searchParams.set('q', trimmed);
  url.searchParams.set('format', 'jsonv2');
  url.searchParams.set('addressdetails', '1');
  url.searchParams.set('countrycodes', 'vn');
  url.searchParams.set('accept-language', 'vi');
  url.searchParams.set('limit', String(options.limit ?? 5));

  const res = await fetch(url.toString(), {
    signal: options.signal,
    headers: {
      // Browsers strip User-Agent overrides — Referer is the next best signal of origin.
      'Accept': 'application/json',
    },
  });
  if (!res.ok) throw new Error(`Nominatim search HTTP ${res.status}`);
  const json = (await res.json()) as Array<Record<string, unknown>>;
  return json.map(parseResult);
}

export async function reverseGeocode(
  lat: number,
  lng: number,
  options: { signal?: AbortSignal } = {},
): Promise<NominatimResult | null> {
  const url = new URL(NOMINATIM_REVERSE);
  url.searchParams.set('lat', String(lat));
  url.searchParams.set('lon', String(lng));
  url.searchParams.set('format', 'jsonv2');
  url.searchParams.set('addressdetails', '1');
  url.searchParams.set('accept-language', 'vi');

  const res = await fetch(url.toString(), {
    signal: options.signal,
    headers: { 'Accept': 'application/json' },
  });
  if (!res.ok) throw new Error(`Nominatim reverse HTTP ${res.status}`);
  const json = (await res.json()) as Record<string, unknown> | null;
  if (!json || typeof json !== 'object') return null;
  // reverse returns single object — re-use parser
  return parseResult(json);
}

function parseResult(raw: Record<string, unknown>): NominatimResult {
  const displayName = String(raw.display_name ?? '');
  const lat = Number(raw.lat);
  const lng = Number(raw.lon);
  const address = (raw.address ?? {}) as Record<string, string | undefined>;
  const country = address.country;
  const countryCode = (raw.address as { country_code?: string } | undefined)?.country_code;

  // Build compact label: prefer "house_number road, suburb, city"
  const parts = [
    [address.house_number, address.road].filter(Boolean).join(' '),
    address.suburb ?? address.neighbourhood ?? address.quarter,
    address.city ?? address.town ?? address.county,
  ].filter(Boolean);
  const label = parts.length ? parts.join(', ') : displayName.split(',').slice(0, 3).join(',').trim();

  return {
    displayName,
    label,
    lat: Number.isFinite(lat) ? lat : NaN,
    lng: Number.isFinite(lng) ? lng : NaN,
    country,
    countryCode,
  };
}

// ---------------------------------------------------------------------------
// Validation helpers — exported for unit tests + checkout submit-time guards.
// ---------------------------------------------------------------------------

/** Vietnam bbox (loose, includes offshore islands). */
export const VN_BBOX = {
  minLat: 8.0,
  maxLat: 24.0,
  minLng: 102.0,
  maxLng: 110.0,
};

/** Hà Nội bbox (covers central + outer districts). */
export const HANOI_BBOX = {
  minLat: 20.85,
  maxLat: 21.4,
  minLng: 105.3,
  maxLng: 106.1,
};

export function isInVietnam(lat: number, lng: number): boolean {
  return (
    Number.isFinite(lat) && Number.isFinite(lng) &&
    lat >= VN_BBOX.minLat && lat <= VN_BBOX.maxLat &&
    lng >= VN_BBOX.minLng && lng <= VN_BBOX.maxLng
  );
}

export function isInHanoi(lat: number, lng: number): boolean {
  return (
    Number.isFinite(lat) && Number.isFinite(lng) &&
    lat >= HANOI_BBOX.minLat && lat <= HANOI_BBOX.maxLat &&
    lng >= HANOI_BBOX.minLng && lng <= HANOI_BBOX.maxLng
  );
}

/**
 * Address shape validator — used by both AddressPicker (after geocode) and
 * CheckoutPage (before submit). Returns null when valid, otherwise an i18n message.
 *
 * Rules:
 *   - At least 10 chars after trim
 *   - Must look like a real address — either starts with a number (street number)
 *     OR contains at least 2 commas (house, street, ward, district...)
 */
export function validateAddressString(address: string): string | null {
  const trimmed = address.trim();
  if (trimmed.length < 10) {
    return 'Địa chỉ quá ngắn — cần đủ số nhà, đường, phường/xã';
  }
  if (trimmed.length > 500) {
    return 'Địa chỉ quá dài (tối đa 500 ký tự)';
  }
  const startsWithNumber = /^\d/.test(trimmed);
  const commaCount = (trimmed.match(/,/g) ?? []).length;
  if (!startsWithNumber && commaCount < 2) {
    return 'Địa chỉ chưa đầy đủ — cần số nhà hoặc tối thiểu phường + quận';
  }
  return null;
}

/**
 * Simple client-side rate limiter — guarantees ≥ `intervalMs` between calls,
 * so we comply with Nominatim's 1-req/sec policy even if React re-renders fast.
 */
export function createRateLimiter(intervalMs: number) {
  let lastRun = 0;
  return async function runRateLimited<T>(fn: () => Promise<T>): Promise<T> {
    const now = Date.now();
    const wait = Math.max(0, lastRun + intervalMs - now);
    if (wait > 0) await new Promise(r => setTimeout(r, wait));
    lastRun = Date.now();
    return fn();
  };
}

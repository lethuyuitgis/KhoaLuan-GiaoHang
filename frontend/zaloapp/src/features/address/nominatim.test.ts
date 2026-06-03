import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import {
  createRateLimiter,
  isInHanoi,
  isInVietnam,
  reverseGeocode,
  searchAddress,
  validateAddressString,
} from './nominatim';

describe('validateAddressString', () => {
  it('rejects strings under 10 chars', () => {
    expect(validateAddressString('Hà Nội')).toMatch(/quá ngắn/i);
  });

  it('rejects vague address without number and without two commas', () => {
    expect(validateAddressString('Phố Lê Lợi gần ngã tư')).toMatch(/chưa đầy đủ/i);
  });

  it('accepts addresses starting with a street number', () => {
    expect(validateAddressString('123 Lê Lợi, Hoàn Kiếm, Hà Nội')).toBeNull();
  });

  it('accepts addresses with at least 2 commas (ward, district, city)', () => {
    expect(validateAddressString('Số nhà X, Phường A, Quận B, Hà Nội')).toBeNull();
  });

  it('rejects strings over 500 chars', () => {
    const long = '123 ' + 'Lê Lợi, Hoàn Kiếm, '.repeat(40);
    expect(validateAddressString(long)).toMatch(/quá dài/i);
  });
});

describe('bbox helpers', () => {
  it('isInVietnam accepts Hà Nội and TP HCM, rejects Bangkok and out-of-range NaN', () => {
    expect(isInVietnam(21.0285, 105.8542)).toBe(true); // Hoàn Kiếm
    expect(isInVietnam(10.7769, 106.7009)).toBe(true); // Q1 HCMC
    expect(isInVietnam(13.7563, 100.5018)).toBe(false); // Bangkok
    expect(isInVietnam(Number.NaN, 105)).toBe(false);
  });

  it('isInHanoi rejects HCM coordinates', () => {
    expect(isInHanoi(21.0285, 105.8542)).toBe(true);
    expect(isInHanoi(10.7769, 106.7009)).toBe(false);
  });
});

describe('createRateLimiter', () => {
  beforeEach(() => {
    vi.useFakeTimers();
  });
  afterEach(() => {
    vi.useRealTimers();
  });

  it('delays second call until interval elapses', async () => {
    const limiter = createRateLimiter(1000);
    const fn = vi.fn(async () => 'ok');

    const p1 = limiter(fn);
    await vi.advanceTimersByTimeAsync(0);
    await p1;
    expect(fn).toHaveBeenCalledTimes(1);

    const p2 = limiter(fn);
    // Should NOT resolve before 1000ms have passed since previous call
    await vi.advanceTimersByTimeAsync(500);
    expect(fn).toHaveBeenCalledTimes(1);

    await vi.advanceTimersByTimeAsync(600);
    await p2;
    expect(fn).toHaveBeenCalledTimes(2);
  });
});

describe('searchAddress', () => {
  const originalFetch = global.fetch;
  afterEach(() => {
    global.fetch = originalFetch;
  });

  it('returns empty list for query under 3 chars without calling fetch', async () => {
    const fetchMock = vi.fn();
    global.fetch = fetchMock as never;
    expect(await searchAddress('AB')).toEqual([]);
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it('parses Nominatim JSON into NominatimResult shape', async () => {
    const payload = [
      {
        display_name: '123, Lê Lợi, Hoàn Kiếm, Hà Nội, Việt Nam',
        lat: '21.0285',
        lon: '105.8542',
        address: {
          house_number: '123',
          road: 'Lê Lợi',
          suburb: 'Hoàn Kiếm',
          city: 'Hà Nội',
          country: 'Việt Nam',
          country_code: 'vn',
        },
      },
    ];
    global.fetch = vi.fn(async () => ({
      ok: true,
      json: async () => payload,
    })) as never;

    const results = await searchAddress('123 Lê Lợi');
    expect(results).toHaveLength(1);
    expect(results[0].lat).toBeCloseTo(21.0285, 4);
    expect(results[0].lng).toBeCloseTo(105.8542, 4);
    expect(results[0].label).toContain('123');
    expect(results[0].label).toContain('Lê Lợi');
    expect(results[0].countryCode).toBe('vn');
  });

  it('appends countrycodes=vn and accept-language=vi to URL', async () => {
    const fetchMock = vi.fn<(input: RequestInfo | URL, init?: RequestInit) => Promise<Response>>(
      async () => ({ ok: true, json: async () => [] }) as unknown as Response,
    );
    global.fetch = fetchMock as never;
    await searchAddress('Hoàn Kiếm');
    const calledUrl = String(fetchMock.mock.calls[0][0]);
    expect(calledUrl).toContain('countrycodes=vn');
    expect(calledUrl).toContain('accept-language=vi');
    expect(calledUrl).toContain('addressdetails=1');
  });

  it('throws on non-OK status', async () => {
    global.fetch = vi.fn(async () => ({ ok: false, status: 500 })) as never;
    await expect(searchAddress('foo bar baz')).rejects.toThrow(/500/);
  });
});

describe('reverseGeocode', () => {
  const originalFetch = global.fetch;
  afterEach(() => { global.fetch = originalFetch; });

  it('returns parsed result for a valid coordinate', async () => {
    global.fetch = vi.fn(async () => ({
      ok: true,
      json: async () => ({
        display_name: 'Hồ Hoàn Kiếm, Hoàn Kiếm, Hà Nội',
        lat: '21.0287',
        lon: '105.8524',
        address: { suburb: 'Hoàn Kiếm', city: 'Hà Nội' },
      }),
    })) as never;

    const r = await reverseGeocode(21.0287, 105.8524);
    expect(r).not.toBeNull();
    expect(r?.displayName).toContain('Hồ Hoàn Kiếm');
  });
});

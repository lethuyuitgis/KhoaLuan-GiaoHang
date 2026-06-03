import { describe, expect, it } from 'vitest';
import i18n, { normalizeLang } from './index';

describe('normalizeLang', () => {
  it('maps Telegram zh-Hans / zh-Hant to zh', () => {
    expect(normalizeLang('zh-Hans')).toBe('zh');
    expect(normalizeLang('zh-Hant')).toBe('zh');
    expect(normalizeLang('zh-CN')).toBe('zh');
    expect(normalizeLang('zh-TW')).toBe('zh');
  });

  it('maps en-US / en-GB to en', () => {
    expect(normalizeLang('en')).toBe('en');
    expect(normalizeLang('en-US')).toBe('en');
    expect(normalizeLang('en-GB')).toBe('en');
  });

  it('maps ko-KR to ko and ja-JP to ja', () => {
    expect(normalizeLang('ko')).toBe('ko');
    expect(normalizeLang('ko-KR')).toBe('ko');
    expect(normalizeLang('ja')).toBe('ja');
    expect(normalizeLang('ja-JP')).toBe('ja');
  });

  it('falls back to vi for unknown / empty input', () => {
    expect(normalizeLang('')).toBe('vi');
    expect(normalizeLang(undefined)).toBe('vi');
    expect(normalizeLang(null)).toBe('vi');
    expect(normalizeLang('de-DE')).toBe('vi');
    expect(normalizeLang('fr')).toBe('vi');
  });
});

describe('i18n resource resolution', () => {
  it('Vietnamese catalog.greeting matches the original Vietnamese copy', async () => {
    await i18n.changeLanguage('vi');
    expect(i18n.t('catalog.greeting')).toBe('Hôm nay ăn gì?');
  });

  it('switches to English on changeLanguage("en")', async () => {
    await i18n.changeLanguage('en');
    expect(i18n.t('catalog.greeting')).toBe('What to eat today?');
    expect(i18n.t('cart.placeOrder')).toBe('Place order');
  });

  it('renders Korean correctly with interpolation', async () => {
    await i18n.changeLanguage('ko');
    expect(i18n.t('catalog.greeting')).toBe('오늘 뭐 드실까요?');
    expect(i18n.t('cart.subtotal', { count: 3 })).toContain('3');
  });

  it('renders Japanese correctly', async () => {
    await i18n.changeLanguage('ja');
    expect(i18n.t('catalog.greeting')).toBe('今日は何を食べますか?');
  });

  it('renders Simplified Chinese correctly', async () => {
    await i18n.changeLanguage('zh');
    expect(i18n.t('catalog.greeting')).toBe('今天吃什么?');
  });

  it('falls back to Vietnamese for unsupported language', async () => {
    await i18n.changeLanguage('de');
    // Detector / supportedLngs should leave us on `de` but resolve from `vi` fallback
    expect(i18n.t('catalog.greeting')).toBe('Hôm nay ăn gì?');
  });
});

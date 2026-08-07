import { describe, it, expect, vi, afterEach } from 'vitest';
import { render, screen, cleanup, within, fireEvent } from '@testing-library/react';
import type { SavedAddress } from '@shop/shared';
import '@/i18n';

// Leaflet map is irrelevant to saved-address logic — stub it out.
vi.mock('react-leaflet', () => ({
  MapContainer: ({ children }: any) => <div>{children}</div>,
  TileLayer: () => null,
  Marker: () => null,
  useMap: () => ({ setView: () => {}, getZoom: () => 13 }),
  useMapEvents: () => null,
}));

vi.mock('./nominatim', () => ({
  createRateLimiter: () => (fn: () => unknown) => fn(),
  isInVietnam: () => true,
  isInHanoi: () => true,
  searchAddress: vi.fn().mockResolvedValue([]),
  reverseGeocode: vi.fn().mockResolvedValue(null),
  validateAddressString: () => null,
}));

import { AddressPicker } from './AddressPicker';

const saved: SavedAddress[] = [
  { id: 1, address: '12 Hàng Đào, Hoàn Kiếm', lat: 21.03, lng: 105.85, lastUsedAt: '2026-08-01T00:00:00Z' },
  { id: 2, address: '45 Cầu Giấy', lat: 21.03, lng: 105.79, lastUsedAt: '2026-07-01T00:00:00Z' },
];

function renderPicker() {
  const onChange = vi.fn();
  const onDeleteSaved = vi.fn();
  render(
    <AddressPicker address="" lat={null} lng={null}
      onChange={onChange} savedAddresses={saved} onDeleteSaved={onDeleteSaved} />,
  );
  return { onChange, onDeleteSaved };
}

describe('AddressPicker (zaloapp) — saved addresses', () => {
  afterEach(cleanup);

  it('shows saved addresses when the search field is focused', () => {
    renderPicker();
    fireEvent.focus(screen.getByTestId('address-search-input'));
    const list = screen.getByTestId('address-suggestions');
    expect(within(list).getByText(/12 Hàng Đào/)).toBeInTheDocument();
    expect(within(list).getByText(/45 Cầu Giấy/)).toBeInTheDocument();
  });

  it('filters saved addresses by the typed query', () => {
    renderPicker();
    const input = screen.getByTestId('address-search-input');
    fireEvent.focus(input);
    fireEvent.change(input, { target: { value: 'Cầu' } });
    const list = screen.getByTestId('address-suggestions');
    expect(within(list).getByText(/45 Cầu Giấy/)).toBeInTheDocument();
    expect(within(list).queryByText(/Hàng Đào/)).not.toBeInTheDocument();
  });

  it('fills the address directly (no geocode) when a saved one is picked', () => {
    const { onChange } = renderPicker();
    fireEvent.focus(screen.getByTestId('address-search-input'));
    fireEvent.click(screen.getByText(/12 Hàng Đào/));
    expect(onChange).toHaveBeenCalledWith({ address: '12 Hàng Đào, Hoàn Kiếm', lat: 21.03, lng: 105.85 });
  });

  it('calls onDeleteSaved when the ✕ is clicked', () => {
    const { onDeleteSaved } = renderPicker();
    fireEvent.focus(screen.getByTestId('address-search-input'));
    fireEvent.click(screen.getAllByTestId('delete-saved')[0]);
    expect(onDeleteSaved).toHaveBeenCalledWith(1);
  });
});

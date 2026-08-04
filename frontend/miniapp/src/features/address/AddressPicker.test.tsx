import { describe, it, expect, vi, afterEach } from 'vitest';
import { render, screen, cleanup, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
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

// Keep geocoding out of the test; saved-address picking never calls it.
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

function renderPicker(extra?: Partial<React.ComponentProps<typeof AddressPicker>>) {
  const onChange = vi.fn();
  const onDeleteSaved = vi.fn();
  render(
    <AddressPicker address="" lat={null} lng={null}
      onChange={onChange} savedAddresses={saved} onDeleteSaved={onDeleteSaved} {...extra} />,
  );
  return { onChange, onDeleteSaved };
}

describe('AddressPicker — saved addresses', () => {
  afterEach(cleanup);

  it('shows saved addresses when the search field is focused (empty query)', async () => {
    renderPicker();
    await userEvent.click(screen.getByTestId('address-search-input'));
    const list = screen.getByTestId('address-suggestions');
    expect(within(list).getByText(/12 Hàng Đào/)).toBeInTheDocument();
    expect(within(list).getByText(/45 Cầu Giấy/)).toBeInTheDocument();
  });

  it('filters saved addresses by the typed query', async () => {
    renderPicker();
    await userEvent.type(screen.getByTestId('address-search-input'), 'Cầu');
    const list = screen.getByTestId('address-suggestions');
    expect(within(list).getByText(/45 Cầu Giấy/)).toBeInTheDocument();
    expect(within(list).queryByText(/Hàng Đào/)).not.toBeInTheDocument();
  });

  it('fills the address directly (no geocode) when a saved one is picked', async () => {
    const { onChange } = renderPicker();
    await userEvent.click(screen.getByTestId('address-search-input'));
    await userEvent.click(screen.getByText(/12 Hàng Đào/));
    expect(onChange).toHaveBeenCalledWith({
      address: '12 Hàng Đào, Hoàn Kiếm', lat: 21.03, lng: 105.85,
    });
  });

  it('calls onDeleteSaved when the ✕ on a saved suggestion is clicked', async () => {
    const { onDeleteSaved } = renderPicker();
    await userEvent.click(screen.getByTestId('address-search-input'));
    const rows = screen.getAllByTestId('saved-suggestion');
    const delButtons = screen.getAllByTestId('delete-saved');
    await userEvent.click(delButtons[0]);
    expect(onDeleteSaved).toHaveBeenCalledWith(1);
    expect(rows.length).toBe(2);
  });
});

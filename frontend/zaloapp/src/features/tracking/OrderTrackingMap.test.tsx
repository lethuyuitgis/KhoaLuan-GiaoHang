import { describe, it, expect, vi, afterEach } from 'vitest';
import { render, screen, cleanup } from '@testing-library/react';
import '@/i18n';

vi.mock('react-leaflet', () => ({
  MapContainer: ({ children }: any) => <div>{children}</div>,
  TileLayer: () => null,
  Marker: () => null,
  Polyline: () => null,
  useMap: () => ({ fitBounds: () => {} }),
}));

vi.mock('./use-live-location', () => ({
  useLiveLocation: vi.fn(),
}));

import { useLiveLocation } from './use-live-location';
import { OrderTrackingMap } from './OrderTrackingMap';

const props = {
  orderId: 'o1', pickupLat: '21.0285', pickupLng: '105.8542',
  deliveryLat: '21.03', deliveryLng: '105.85',
};

describe('OrderTrackingMap (zaloapp)', () => {
  afterEach(cleanup);

  it('renders the map when coordinates are valid', () => {
    vi.mocked(useLiveLocation).mockReturnValue({ location: null, isLoading: false });
    render(<OrderTrackingMap {...props} />);
    expect(screen.getByTestId('tracking-map')).toBeInTheDocument();
  });

  it('shows a fallback (no map) when coordinates are invalid', () => {
    vi.mocked(useLiveLocation).mockReturnValue({ location: null, isLoading: false });
    render(<OrderTrackingMap {...props} pickupLat="abc" pickupLng="xyz" />);
    expect(screen.queryByTestId('tracking-map')).not.toBeInTheDocument();
  });

  it('renders the shipper position when a location is available', () => {
    vi.mocked(useLiveLocation).mockReturnValue({
      location: { lat: 21.028, lng: 105.854, recordedAt: '2026-08-01T00:00:00Z' },
      isLoading: false,
    });
    render(<OrderTrackingMap {...props} />);
    expect(screen.getByTestId('tracking-map')).toBeInTheDocument();
  });
});

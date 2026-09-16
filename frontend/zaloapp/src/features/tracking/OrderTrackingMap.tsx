import { useEffect } from 'react';
import { MapContainer, TileLayer, Marker, Polyline, useMap } from 'react-leaflet';
import { useTranslation } from 'react-i18next';
import L from 'leaflet';
import { useLiveLocation } from './use-live-location';
import 'leaflet/dist/leaflet.css';
import '@/styles/leaflet-overrides.css';
import markerIcon2x from 'leaflet/dist/images/marker-icon-2x.png';
import markerIcon from 'leaflet/dist/images/marker-icon.png';
import markerShadow from 'leaflet/dist/images/marker-shadow.png';

delete (L.Icon.Default.prototype as { _getIconUrl?: unknown })._getIconUrl;
L.Icon.Default.mergeOptions({ iconRetinaUrl: markerIcon2x, iconUrl: markerIcon, shadowUrl: markerShadow });

// Tile proxy tuyệt đối (Zalo origin khác) + pin màu SVG inline (không ảnh ngoài).
const OSM_BASE = (import.meta.env.VITE_API_BASE_URL || '') + '/osm';
const OSM_TILES = `${OSM_BASE}/tiles/{z}/{x}/{y}.png`;
const HEX: Record<string, string> = { green: '#16a34a', red: '#dc2626', blue: '#2563eb' };
const mk = (color: string) => L.divIcon({
  className: 'track-pin-marker',
  html:
    `<svg width="28" height="40" viewBox="0 0 24 36" xmlns="http://www.w3.org/2000/svg">` +
    `<path d="M12 0C5.37 0 0 5.37 0 12c0 9 12 24 12 24s12-15 12-24C24 5.37 18.63 0 12 0z" fill="${HEX[color] ?? '#2563eb'}"/>` +
    `<circle cx="12" cy="12" r="5" fill="#fff"/></svg>`,
  iconSize: [28, 40], iconAnchor: [14, 40], popupAnchor: [0, -36],
});
const shopIcon = mk('green');
const shipperIcon = mk('red');
const destIcon = mk('blue');

interface Props {
  orderId: string;
  pickupLat: string;
  pickupLng: string;
  deliveryLat: string;
  deliveryLng: string;
  enabled?: boolean;
}

export function OrderTrackingMap({
  orderId, pickupLat, pickupLng, deliveryLat, deliveryLng, enabled = true,
}: Props) {
  const { t } = useTranslation();
  const { location, isLoading } = useLiveLocation(orderId, enabled);

  const pickup: [number, number] = [Number(pickupLat), Number(pickupLng)];
  const destination: [number, number] = [Number(deliveryLat), Number(deliveryLng)];
  const shipper: [number, number] | null = location ? [location.lat, location.lng] : null;

  const coordsValid =
    Number.isFinite(pickup[0]) && Number.isFinite(pickup[1]) &&
    Number.isFinite(destination[0]) && Number.isFinite(destination[1]);
  if (!coordsValid) {
    return (
      <div className="w-full h-64 rounded-2xl overflow-hidden bg-brand-50 flex items-center justify-center text-sm text-brand-400 italic">
        {t('tracking.noMap')}
      </div>
    );
  }

  const center: [number, number] = [
    (pickup[0] + destination[0]) / 2,
    (pickup[1] + destination[1]) / 2,
  ];

  return (
    <div className="w-full h-64 rounded-2xl overflow-hidden relative z-0" data-testid="tracking-map">
      <MapContainer center={center} zoom={13} style={{ height: '100%', width: '100%' }}>
        <TileLayer
          attribution='&copy; OpenStreetMap'
          url={OSM_TILES}
        />
        <Marker position={pickup} icon={shopIcon} />
        <Marker position={destination} icon={destIcon} />
        {shipper && <Marker position={shipper} icon={shipperIcon} />}
        {shipper && (
          <Polyline
            positions={[shipper, destination]}
            pathOptions={{ color: '#9333ea', weight: 3, dashArray: '5, 8' }}
          />
        )}
        <FitBounds pickup={pickup} destination={destination} shipper={shipper} />
      </MapContainer>

      <div className="absolute top-2 right-2 z-[1000] bg-white/90 px-2 py-1 rounded-md text-xs shadow">
        {isLoading && `⏳ ${t('tracking.loading')}`}
        {!isLoading && location && <span className="text-green-600">🟢 {t('tracking.live')}</span>}
        {!isLoading && !location && <span className="text-gray-500">📍 {t('tracking.waiting')}</span>}
      </div>
    </div>
  );
}

function FitBounds({
  pickup, destination, shipper,
}: {
  pickup: [number, number];
  destination: [number, number];
  shipper: [number, number] | null;
}) {
  const map = useMap();
  useEffect(() => {
    const points: [number, number][] = [pickup, destination];
    if (shipper) points.push(shipper);
    map.fitBounds(points, { padding: [40, 40] });
  }, [map, pickup, destination, shipper]);
  return null;
}

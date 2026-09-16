import { useEffect } from 'react';
import { MapContainer, TileLayer, Marker, Polyline, useMap } from 'react-leaflet';
import L from 'leaflet';
import { useLiveLocation } from './use-live-location';
import 'leaflet/dist/leaflet.css';
import '@/styles/leaflet-overrides.css';
// Icon bundle local (Vite) — KHÔNG dùng unpkg/github (bị chặn trong webview Zalo / mạng lọc).
import markerIcon2x from 'leaflet/dist/images/marker-icon-2x.png';
import markerIcon from 'leaflet/dist/images/marker-icon.png';
import markerShadow from 'leaflet/dist/images/marker-shadow.png';

// Fix default marker icons (bundled)
delete (L.Icon.Default.prototype as any)._getIconUrl;
L.Icon.Default.mergeOptions({ iconRetinaUrl: markerIcon2x, iconUrl: markerIcon, shadowUrl: markerShadow });

// Pin màu vẽ bằng SVG inline (không tải ảnh ngoài): shop=xanh lá, shipper=đỏ, điểm giao=xanh dương.
const pin = (color: string) => L.divIcon({
  className: 'track-pin-marker',
  html:
    `<svg width="28" height="40" viewBox="0 0 24 36" xmlns="http://www.w3.org/2000/svg">` +
    `<path d="M12 0C5.37 0 0 5.37 0 12c0 9 12 24 12 24s12-15 12-24C24 5.37 18.63 0 12 0z" fill="${color}"/>` +
    `<circle cx="12" cy="12" r="5" fill="#fff"/></svg>`,
  iconSize: [28, 40], iconAnchor: [14, 40], popupAnchor: [0, -36],
});
const shopIcon = pin('#16a34a');
const shipperIcon = pin('#dc2626');
const destIcon = pin('#2563eb');

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
  const { location, isConnected, isLoading } = useLiveLocation(orderId, enabled);

  const pickup: [number, number] = [Number(pickupLat), Number(pickupLng)];
  const destination: [number, number] = [Number(deliveryLat), Number(deliveryLng)];
  const shipper: [number, number] | null = location ? [location.lat, location.lng] : null;

  // Leaflet throws "Invalid LatLng object" on NaN — bail out before mounting MapContainer.
  const coordsValid =
    Number.isFinite(pickup[0]) && Number.isFinite(pickup[1]) &&
    Number.isFinite(destination[0]) && Number.isFinite(destination[1]);
  if (!coordsValid) {
    return (
      <div className="w-full h-64 rounded-lg overflow-hidden mb-3 bg-tg-secondaryBg flex items-center justify-center text-sm text-tg-hint italic">
        Không có dữ liệu bản đồ.
      </div>
    );
  }

  const center: [number, number] = [
    (pickup[0] + destination[0]) / 2,
    (pickup[1] + destination[1]) / 2,
  ];

  return (
    <div className="w-full h-64 rounded-lg overflow-hidden mb-3 relative z-0">
      <MapContainer center={center} zoom={13} style={{ height: '100%', width: '100%' }}>
        <TileLayer
          attribution='&copy; <a href="https://www.openstreetmap.org/copyright">OSM</a>'
          url="/osm/tiles/{z}/{x}/{y}.png"
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
        {isLoading && '⏳ Đang tải...'}
        {!isLoading && location && (
          <span className={isConnected ? 'text-green-600' : 'text-yellow-600'}>
            {isConnected ? '🟢 Live' : '🟡 Chờ kết nối'}
          </span>
        )}
        {!isLoading && !location && (
          <span className="text-gray-500">📍 Chờ shipper share location</span>
        )}
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

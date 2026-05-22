import { useEffect } from 'react';
import { MapContainer, TileLayer, Marker, Polyline, useMap } from 'react-leaflet';
import L from 'leaflet';
import { useLiveLocation } from './use-live-location';
import 'leaflet/dist/leaflet.css';
import '@/styles/leaflet-overrides.css';

// Fix default marker icons
delete (L.Icon.Default.prototype as any)._getIconUrl;
L.Icon.Default.mergeOptions({
  iconRetinaUrl: 'https://unpkg.com/leaflet@1.9.4/dist/images/marker-icon-2x.png',
  iconUrl: 'https://unpkg.com/leaflet@1.9.4/dist/images/marker-icon.png',
  shadowUrl: 'https://unpkg.com/leaflet@1.9.4/dist/images/marker-shadow.png',
});

const shopIcon = new L.Icon({
  iconUrl: 'https://raw.githubusercontent.com/pointhi/leaflet-color-markers/master/img/marker-icon-2x-green.png',
  iconSize: [25, 41], iconAnchor: [12, 41], popupAnchor: [1, -34], shadowSize: [41, 41],
  shadowUrl: 'https://unpkg.com/leaflet@1.9.4/dist/images/marker-shadow.png',
});

const shipperIcon = new L.Icon({
  iconUrl: 'https://raw.githubusercontent.com/pointhi/leaflet-color-markers/master/img/marker-icon-2x-red.png',
  iconSize: [25, 41], iconAnchor: [12, 41], popupAnchor: [1, -34], shadowSize: [41, 41],
  shadowUrl: 'https://unpkg.com/leaflet@1.9.4/dist/images/marker-shadow.png',
});

const destIcon = new L.Icon({
  iconUrl: 'https://raw.githubusercontent.com/pointhi/leaflet-color-markers/master/img/marker-icon-2x-blue.png',
  iconSize: [25, 41], iconAnchor: [12, 41], popupAnchor: [1, -34], shadowSize: [41, 41],
  shadowUrl: 'https://unpkg.com/leaflet@1.9.4/dist/images/marker-shadow.png',
});

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

  const center: [number, number] = [
    (pickup[0] + destination[0]) / 2,
    (pickup[1] + destination[1]) / 2,
  ];

  return (
    <div className="w-full h-64 rounded-lg overflow-hidden mb-3 relative">
      <MapContainer center={center} zoom={13} style={{ height: '100%', width: '100%' }}>
        <TileLayer
          attribution='&copy; <a href="https://www.openstreetmap.org/copyright">OSM</a>'
          url="https://{s}.tile.openstreetmap.org/{z}/{y}/{x}.png"
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

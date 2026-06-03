import { useEffect, useMemo, useRef, useState } from 'react';
import { MapContainer, TileLayer, Marker, useMap, useMapEvents } from 'react-leaflet';
import { useTranslation } from 'react-i18next';
import L from 'leaflet';
import 'leaflet/dist/leaflet.css';
import '@/styles/leaflet-overrides.css';
import {
  createRateLimiter,
  isInHanoi,
  isInVietnam,
  reverseGeocode,
  searchAddress,
  validateAddressString,
  type NominatimResult,
} from './nominatim';

// Fix default marker icons (Vite + Leaflet)
delete (L.Icon.Default.prototype as { _getIconUrl?: unknown })._getIconUrl;
L.Icon.Default.mergeOptions({
  iconRetinaUrl: 'https://unpkg.com/leaflet@1.9.4/dist/images/marker-icon-2x.png',
  iconUrl: 'https://unpkg.com/leaflet@1.9.4/dist/images/marker-icon.png',
  shadowUrl: 'https://unpkg.com/leaflet@1.9.4/dist/images/marker-shadow.png',
});

const pinIcon = new L.Icon({
  iconUrl: 'https://raw.githubusercontent.com/pointhi/leaflet-color-markers/master/img/marker-icon-2x-blue.png',
  iconSize: [25, 41],
  iconAnchor: [12, 41],
  popupAnchor: [1, -34],
  shadowSize: [41, 41],
  shadowUrl: 'https://unpkg.com/leaflet@1.9.4/dist/images/marker-shadow.png',
});

const DEFAULT_LAT = 21.0285;
const DEFAULT_LNG = 105.8542;
const SEARCH_DEBOUNCE_MS = 350;
const NOMINATIM_INTERVAL_MS = 1100;

interface Props {
  address: string;
  lat: number | null;
  lng: number | null;
  onChange: (next: { address: string; lat: number; lng: number }) => void;
  error?: string;
}

export function AddressPicker({ address, lat, lng, onChange, error }: Props) {
  const { t } = useTranslation();
  const [query, setQuery] = useState('');
  const [suggestions, setSuggestions] = useState<NominatimResult[]>([]);
  const [showSuggestions, setShowSuggestions] = useState(false);
  const [searching, setSearching] = useState(false);
  const [reverseLoading, setReverseLoading] = useState(false);
  const [geoError, setGeoError] = useState<string | null>(null);

  const limiter = useMemo(() => createRateLimiter(NOMINATIM_INTERVAL_MS), []);
  const abortRef = useRef<AbortController | null>(null);

  const initialCenter: [number, number] = [
    lat ?? DEFAULT_LAT,
    lng ?? DEFAULT_LNG,
  ];
  const hasPin = lat !== null && lng !== null;

  useEffect(() => {
    if (query.trim().length < 3) {
      setSuggestions([]);
      setShowSuggestions(false);
      return;
    }
    const handle = window.setTimeout(() => {
      abortRef.current?.abort();
      const ctrl = new AbortController();
      abortRef.current = ctrl;
      setSearching(true);
      limiter(() => searchAddress(query, { signal: ctrl.signal, limit: 5 }))
        .then(results => {
          if (!ctrl.signal.aborted) {
            setSuggestions(results);
            setShowSuggestions(true);
          }
        })
        .catch(err => {
          if (err?.name !== 'AbortError') setSuggestions([]);
        })
        .finally(() => {
          if (!ctrl.signal.aborted) setSearching(false);
        });
    }, SEARCH_DEBOUNCE_MS);
    return () => window.clearTimeout(handle);
  }, [query, limiter]);

  function pickResult(r: NominatimResult) {
    setShowSuggestions(false);
    setQuery(r.label);
    applyLocation(r.lat, r.lng, r.displayName);
  }

  function applyLocation(nextLat: number, nextLng: number, displayName?: string) {
    if (!isInVietnam(nextLat, nextLng)) {
      setGeoError(t('address.outsideVietnam'));
      return;
    }
    if (!isInHanoi(nextLat, nextLng)) {
      setGeoError(t('address.outsideHanoi'));
      return;
    }
    setGeoError(null);

    if (displayName) {
      onChange({ address: displayName, lat: nextLat, lng: nextLng });
      return;
    }

    setReverseLoading(true);
    abortRef.current?.abort();
    const ctrl = new AbortController();
    abortRef.current = ctrl;
    limiter(() => reverseGeocode(nextLat, nextLng, { signal: ctrl.signal }))
      .then(result => {
        if (ctrl.signal.aborted) return;
        const resolved = result?.displayName ?? `${nextLat.toFixed(6)}, ${nextLng.toFixed(6)}`;
        onChange({ address: resolved, lat: nextLat, lng: nextLng });
      })
      .catch(err => {
        if (err?.name === 'AbortError') return;
        onChange({
          address: t('address.coordsUnresolved', { lat: nextLat.toFixed(5), lng: nextLng.toFixed(5) }),
          lat: nextLat,
          lng: nextLng,
        });
      })
      .finally(() => {
        if (!ctrl.signal.aborted) setReverseLoading(false);
      });
  }

  const validation = address ? validateAddressString(address) : null;
  const displayedError = error ?? geoError ?? validation;

  return (
    <div className="space-y-2">
      <div className="relative">
        <div className="flex items-center gap-2 px-3 py-2 rounded-xl bg-gray-50 border border-gray-200 focus-within:ring-2 focus-within:ring-zalo focus-within:border-zalo">
          <span aria-hidden="true">🔍</span>
          <input
            type="text"
            value={query}
            onChange={e => setQuery(e.target.value)}
            onFocus={() => suggestions.length > 0 && setShowSuggestions(true)}
            placeholder={t('address.search')}
            className="flex-1 bg-transparent text-sm outline-none"
            aria-label={t('address.searchAria')}
            data-testid="address-search-input"
          />
          {searching && <span className="text-xs text-gray-400">…</span>}
        </div>
        {showSuggestions && suggestions.length > 0 && (
          <ul
            className="absolute z-[1100] mt-1 w-full bg-white border border-gray-200 rounded-xl shadow-lg overflow-hidden"
            role="listbox"
            data-testid="address-suggestions"
          >
            {suggestions.map((r, idx) => (
              <li key={`${r.lat}-${r.lng}-${idx}`} role="option" aria-selected="false">
                <button
                  type="button"
                  onClick={() => pickResult(r)}
                  className="block w-full text-left px-3 py-2 text-sm hover:bg-blue-50 active:bg-blue-100 border-b border-gray-100 last:border-b-0"
                >
                  <span className="font-medium block text-gray-800 truncate">{r.label || r.displayName}</span>
                  <span className="text-[11px] text-gray-500 line-clamp-1">{r.displayName}</span>
                </button>
              </li>
            ))}
          </ul>
        )}
        {showSuggestions && query.trim().length >= 3 && !searching && suggestions.length === 0 && (
          <div className="absolute z-[1100] mt-1 w-full bg-white border border-gray-200 rounded-xl shadow-lg px-3 py-2 text-xs text-gray-500">
            {t('address.notFound')}
          </div>
        )}
      </div>

      <div className="w-full h-[250px] rounded-xl overflow-hidden border border-gray-200 relative">
        <MapContainer
          center={initialCenter}
          zoom={hasPin ? 16 : 13}
          style={{ height: '100%', width: '100%' }}
          scrollWheelZoom={false}
        >
          <TileLayer
            attribution='&copy; OpenStreetMap'
            url="https://{s}.tile.openstreetmap.org/{z}/{y}/{x}.png"
          />
          {hasPin && (
            <Marker
              position={[lat as number, lng as number]}
              icon={pinIcon}
              draggable={true}
              eventHandlers={{
                dragend: e => {
                  const m = e.target as L.Marker;
                  const pos = m.getLatLng();
                  applyLocation(pos.lat, pos.lng);
                },
              }}
            />
          )}
          <PanOnPinChange lat={lat} lng={lng} />
          <ClickHandler onPick={applyLocation} />
        </MapContainer>
        <div className="absolute top-2 left-2 z-[1000] bg-white/95 px-2 py-1 rounded-md text-[10px] shadow text-gray-600">
          {t('address.tipDrag')}
        </div>
      </div>

      <div className="px-3 py-2 rounded-xl bg-blue-50 border border-blue-100 text-sm">
        <div className="text-[11px] uppercase tracking-wide font-semibold text-zalo mb-0.5">
          {t('address.selected')}
        </div>
        {reverseLoading ? (
          <span className="text-gray-500 italic">{t('address.loading')}</span>
        ) : address ? (
          <span className="text-gray-800 break-words" data-testid="resolved-address">{address}</span>
        ) : (
          <span className="text-gray-400 italic">{t('address.notSelected')}</span>
        )}
        {hasPin && (
          <div className="text-[11px] text-gray-500 mt-0.5">
            {t('address.coords', { lat: lat?.toFixed(6), lng: lng?.toFixed(6) })}
          </div>
        )}
      </div>

      {displayedError && (
        <p className="text-xs text-red-600" role="alert" data-testid="address-error">
          ⚠️ {displayedError}
        </p>
      )}
    </div>
  );
}

function ClickHandler({ onPick }: { onPick: (lat: number, lng: number) => void }) {
  useMapEvents({
    click: e => onPick(e.latlng.lat, e.latlng.lng),
  });
  return null;
}

function PanOnPinChange({ lat, lng }: { lat: number | null; lng: number | null }) {
  const map = useMap();
  useEffect(() => {
    if (lat !== null && lng !== null) {
      map.setView([lat, lng], Math.max(map.getZoom(), 15), { animate: true });
    }
  }, [lat, lng, map]);
  return null;
}

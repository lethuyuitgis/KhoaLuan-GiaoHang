import { useEffect, useMemo, useRef, useState } from 'react';
import { MapContainer, TileLayer, Marker, useMap, useMapEvents } from 'react-leaflet';
import { useTranslation } from 'react-i18next';
import L from 'leaflet';
import 'leaflet/dist/leaflet.css';
import '@/styles/leaflet-overrides.css';
// Icon bundle local (Vite) — KHÔNG unpkg/github (webview Zalo chặn domain ngoài).
import markerIcon2x from 'leaflet/dist/images/marker-icon-2x.png';
import markerIcon from 'leaflet/dist/images/marker-icon.png';
import markerShadow from 'leaflet/dist/images/marker-shadow.png';
import type { SavedAddress } from '@shop/shared';
import {
  createRateLimiter,
  isInHanoi,
  isInVietnam,
  reverseGeocode,
  searchAddress,
  validateAddressString,
  type NominatimResult,
} from './nominatim';

// Fix default marker icons (bundled)
delete (L.Icon.Default.prototype as { _getIconUrl?: unknown })._getIconUrl;
L.Icon.Default.mergeOptions({ iconRetinaUrl: markerIcon2x, iconUrl: markerIcon, shadowUrl: markerShadow });

// Tile bản đồ proxy qua domain mình (Zalo chạy trên origin khác → cần URL tuyệt đối).
const OSM_BASE = (import.meta.env.VITE_API_BASE_URL || '') + '/osm';
const OSM_TILES = `${OSM_BASE}/tiles/{z}/{x}/{y}.png`;

// Pin xanh vẽ bằng SVG inline (không tải ảnh ngoài).
const pinIcon = L.divIcon({
  className: 'address-pin-marker',
  html:
    '<svg width="30" height="42" viewBox="0 0 24 36" xmlns="http://www.w3.org/2000/svg">' +
    '<path d="M12 0C5.37 0 0 5.37 0 12c0 9 12 24 12 24s12-15 12-24C24 5.37 18.63 0 12 0z" fill="#2563eb"/>' +
    '<circle cx="12" cy="12" r="5" fill="#fff"/></svg>',
  iconSize: [30, 42],
  iconAnchor: [15, 42],
  popupAnchor: [0, -38],
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
  /** The customer's saved addresses, surfaced as autocomplete suggestions. */
  savedAddresses?: SavedAddress[];
  /** Remove a saved address (called from the ✕ on a saved suggestion). */
  onDeleteSaved?: (id: number) => void;
}

export function AddressPicker({ address, lat, lng, onChange, error, savedAddresses, onDeleteSaved }: Props) {
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

  // Saved-address suggestions: recent ones on empty focus, filtered while typing.
  const savedMatches = useMemo(() => {
    const list = savedAddresses ?? [];
    const q = query.trim().toLowerCase();
    const filtered = q.length === 0 ? list : list.filter(a => a.address.toLowerCase().includes(q));
    return filtered.slice(0, 5);
  }, [savedAddresses, query]);

  useEffect(() => {
    if (query.trim().length < 3) {
      // Clear Nominatim results, but keep dropdown open so saved addresses still show.
      setSuggestions([]);
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

  function pickSaved(a: SavedAddress) {
    setShowSuggestions(false);
    setQuery(a.address);
    // Saved addresses already carry a resolved label + coords — no geocoding.
    applyLocation(a.lat, a.lng, a.address);
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
            onFocus={() => setShowSuggestions(true)}
            onBlur={() => window.setTimeout(() => setShowSuggestions(false), 150)}
            placeholder={t('address.search')}
            className="flex-1 bg-transparent text-sm outline-none"
            aria-label={t('address.searchAria')}
            data-testid="address-search-input"
          />
          {searching && <span className="text-xs text-gray-400">…</span>}
        </div>
        {showSuggestions && (savedMatches.length > 0 || suggestions.length > 0) && (
          <ul
            className="absolute z-[1100] mt-1 w-full bg-white border border-gray-200 rounded-xl shadow-lg overflow-hidden"
            role="listbox"
            data-testid="address-suggestions"
          >
            {savedMatches.map(a => (
              <li key={`saved-${a.id}`} role="option" aria-selected="false"
                  className="flex items-stretch border-b border-gray-100">
                <button
                  type="button"
                  onClick={() => pickSaved(a)}
                  className="flex-1 min-w-0 text-left px-3 py-2 text-sm hover:bg-amber-50 active:bg-amber-100"
                  data-testid="saved-suggestion"
                >
                  <span className="font-medium block text-gray-800 truncate">⭐ {a.address}</span>
                  <span className="text-[11px] text-amber-600">{t('address.saved')}</span>
                </button>
                {onDeleteSaved && (
                  <button
                    type="button"
                    aria-label={t('address.deleteSaved')}
                    onClick={() => onDeleteSaved(a.id)}
                    className="px-3 text-gray-400 hover:text-red-500 hover:bg-red-50"
                    data-testid="delete-saved"
                  >
                    ✕
                  </button>
                )}
              </li>
            ))}
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
        {showSuggestions && query.trim().length >= 3 && !searching && suggestions.length === 0 && savedMatches.length === 0 && (
          <div className="absolute z-[1100] mt-1 w-full bg-white border border-gray-200 rounded-xl shadow-lg px-3 py-2 text-xs text-gray-500">
            {t('address.notFound')}
          </div>
        )}
      </div>

      {/* z-0: chặn stacking context — pane/control của Leaflet (z-index 400–1000)
          không được đè lên nút Đặt hàng dạng fixed bên ngoài. */}
      <div className="w-full h-[250px] rounded-xl overflow-hidden border border-gray-200 relative z-0">
        <MapContainer
          center={initialCenter}
          zoom={hasPin ? 16 : 13}
          style={{ height: '100%', width: '100%' }}
          scrollWheelZoom={false}
        >
          <TileLayer
            attribution='&copy; OpenStreetMap'
            url={OSM_TILES}
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

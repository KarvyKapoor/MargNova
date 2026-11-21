import {
  MapContainer,
  TileLayer,
  Polyline,
  Marker,
  Popup,
  useMap
} from 'react-leaflet';
import L from 'leaflet';
import type { Location, Route } from '../types';
import { useEffect } from 'react';
import { useMapEvents } from 'react-leaflet';
import type { CoolingPoint } from '../types';
const pinStart = L.divIcon({
  className: 'custom-pin-start',
  html: '<span></span>',
  iconSize: [16, 16],
  iconAnchor: [8, 8]
});

const pinDest = L.divIcon({
  className: 'custom-pin-dest',
  html: '<span></span>',
  iconSize: [16, 16],
  iconAnchor: [8, 8]
});

const pinUser = L.divIcon({
  className: 'custom-pin-user',
  html: '<span></span>',
  iconSize: [16, 16],
  iconAnchor: [8, 8]
});

const pinCool = L.divIcon({
  className: 'custom-pin-cool',
  html: '<span></span>',
  iconSize: [14, 14],
  iconAnchor: [7, 7]
});

const pinDemo = (color: string) =>
  L.divIcon({
    className: 'custom-pin',
    html: `<span style="background:${color}"></span>`,
    iconSize: [14, 14],
    iconAnchor: [7, 7]
  });

function FitBounds({
  route,
  center
}: {
  route?: Route;
  center?: [number, number];
}) {
  const map = useMap();
  useEffect(() => {
    if (route?.coordinates && route.coordinates.length > 0) {
      map.fitBounds(route.coordinates, { padding: [50, 50], maxZoom: 16 });
    } else if (center) {
      map.setView(center, 15);
    }
  }, [route, center, map]);
  return null;
}

type MapViewProps = {
  coolingPoints?: CoolingPoint[];
  addingCoolingPoint?: boolean;
  onMapPick?: (lat: number, lng: number) => void;
  locations?: Location[];
  routes?: Route[];
  activeRoute?: Route;
  route?: Route; // backwards compatibility
  onSelectRoute?: (id: string) => void;
  center?: [number, number];
  userLocation?: { lat: number; lng: number } | null;
  originLabel?: string;
  destLabel?: string;
};

function MapClickHandler({
  enabled,
  onPick
}: {
  enabled: boolean;
  onPick?: (lat: number, lng: number) => void;
}) {
  useMapEvents({
    click(event) {
      if (enabled && onPick) {
        onPick(
          event.latlng.lat,
          event.latlng.lng
        );
      }
    }
  });

  return null;
}

export default function MapView({
  coolingPoints = [],
  addingCoolingPoint = false,
  onMapPick,
  locations = [],
  routes = [],
  activeRoute,
  route,
  onSelectRoute,
  center = [23.2599, 77.4126],
  userLocation,
  originLabel,
  destLabel
}: MapViewProps) {
  const effectiveRoute = activeRoute || route;
  const startCoord = effectiveRoute?.coordinates?.[0];
  const endCoord = effectiveRoute?.coordinates?.[effectiveRoute.coordinates.length - 1];

  return (
    <MapContainer
      center={center}
      zoom={15}
      scrollWheelZoom
      className="map"
      
    >
{/* Community submitted cooling points */}
{coolingPoints.map(point => (
  <Marker
    key={point.id}
    position={[point.latitude, point.longitude]}
    icon={L.divIcon({
      className: 'community-cooling-pin',
      html: `<span>${point.category === 'TEA_STALL' ? '☕' : '🌳'}</span>`,
      iconSize: [28, 28],
      iconAnchor: [14, 14]
    })}
  >
    <Popup>
      <strong>{point.name}</strong>
      <br />
      Category: {point.category}
      <br />
      Shade: {point.shadeScore}/100
      <br />
      Seating: {point.seatingAvailable ? 'Available' : 'Not available'}
      <br />
      Water: {point.waterAvailable ? 'Available' : 'Not available'}
      <br />
      Verification: {point.verificationStatus}
      <br />
      <small>
        Community contribution
      </small>
    </Popup>
  </Marker>
))}


      <TileLayer
        attribution='&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors'
        url="https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png"
      />

      <FitBounds route={effectiveRoute} center={center} />
      <MapClickHandler
        enabled={addingCoolingPoint}
        onPick={onMapPick}
      />

      {/* Render inactive alternative routes */}
      {routes
        .filter((r) => r.id !== effectiveRoute?.id && r.coordinates?.length)
        .map((alt) => (
          <Polyline
            key={alt.id}
            positions={alt.coordinates}
            pathOptions={{
              color: '#386367',
              weight: 4,
              opacity: 0.65,
              dashArray: '6, 6'
            }}
            eventHandlers={{
              click: () => onSelectRoute?.(alt.id)
            }}
          >
            <Popup>
              <strong>{alt.name}</strong>
              <br />
              Distance: {(alt.metrics.distance_m / 1000).toFixed(1)} km · Duration:{' '}
              {Math.round(alt.metrics.duration_min)} min
              <br />
              Suitability score: {alt.score}/100
              <br />
              <button
                type="button"
                style={{
                  marginTop: 6,
                  padding: '4px 8px',
                  background: '#55e0b4',
                  border: 'none',
                  borderRadius: 4,
                  cursor: 'pointer',
                  fontWeight: 'bold',
                  fontSize: 11
                }}
                onClick={() => onSelectRoute?.(alt.id)}
              >
                Select this route
              </button>
            </Popup>
          </Polyline>
        ))}

      {/* Render active selected route */}
      {effectiveRoute?.coordinates && effectiveRoute.coordinates.length > 0 && (
        <Polyline
          positions={effectiveRoute.coordinates}
          pathOptions={{
            color: '#55e0b4',
            weight: 6,
            opacity: 0.95
          }}
        >
          <Popup>
            <strong>{effectiveRoute.name}</strong> (Selected)
            <br />
            Distance: {(effectiveRoute.metrics.distance_m / 1000).toFixed(1)} km ·{' '}
            {Math.round(effectiveRoute.metrics.duration_min)} min walk
            <br />
            Heat Burden: {Math.round(effectiveRoute.metrics.heat)}/100
          </Popup>
        </Polyline>
      )}

      {/* Start Marker */}
      {startCoord && (
        <Marker position={startCoord} icon={pinStart}>
          <Popup>
            <strong>Start Origin</strong>
            <br />
            {originLabel || `${startCoord[0].toFixed(5)}, ${startCoord[1].toFixed(5)}`}
          </Popup>
        </Marker>
      )}

      {/* Destination Marker */}
      {endCoord && (
        <Marker position={endCoord} icon={pinDest}>
          <Popup>
            <strong>Destination</strong>
            <br />
            {destLabel || `${endCoord[0].toFixed(5)}, ${endCoord[1].toFixed(5)}`}
          </Popup>
        </Marker>
      )}

      {/* User Current GPS Location */}
      {userLocation && (
        <Marker position={[userLocation.lat, userLocation.lng]} icon={pinUser}>
          <Popup>
            <strong>Your Location (GPS)</strong>
            <br />
            {userLocation.lat.toFixed(5)}, {userLocation.lng.toFixed(5)}
          </Popup>
        </Marker>
      )}

      {/* Cooling stops along active route */}
      {effectiveRoute?.cooling_stops?.map((cs) => (
        <Marker key={cs.id} position={[cs.lat, cs.lng]} icon={pinCool}>
          <Popup>
            <strong>Cooling / Rest Stop</strong>
            <br />
            {cs.cooling_name || cs.name}
          </Popup>
        </Marker>
      ))}

      {/* Demo Location nodes if no active route */}
      {(!effectiveRoute || !effectiveRoute.coordinates?.length) &&
        locations.map((p) => (
          <Marker
            key={p.id}
            position={[p.lat, p.lng]}
            icon={pinDemo('#f4b860')}
          >
            <Popup>{p.name}</Popup>
          </Marker>
        ))}
    </MapContainer>
  );
}

import type {
  Profile,
  RouteResult,
  Location,
  WeatherInfo,
  NearbyPlace,
  SearchResult,
  CoolingPoint,
  CreateCoolingPointPayload
} from './types';

const API = import.meta.env.VITE_API_URL || 'http://localhost:8080/api';

export async function getLocations(): Promise<Location[]> {
  const r = await fetch(`${API}/locations`);
  if (!r.ok) throw new Error('Could not load map locations');
  const d = await r.json();
  return d.nodes as Location[];
}

export async function searchPlaces(
  query: string,
  lat?: number,
  lng?: number
): Promise<SearchResult[]> {
  if (!query || query.trim().length < 2) return [];

  const params = new URLSearchParams({ q: query.trim() });

  if (lat !== undefined && lng !== undefined) {
    params.append('lat', lat.toString());
    params.append('lng', lng.toString());
  }

  const r = await fetch(`${API}/geocoding/search?${params.toString()}`);

  if (!r.ok) return [];

  return r.json();
}

export async function reverseGeocode(
  lat: number,
  lng: number
): Promise<SearchResult> {
  const r = await fetch(`${API}/geocoding/reverse?lat=${lat}&lng=${lng}`);

  if (!r.ok) {
    throw new Error('Could not resolve address for this location');
  }

  return r.json();
}

export async function findRoutes(payload: {
  origin: string;
  destination: string;
  profile: Profile;
  temperature_c: number;
  originLat?: number;
  originLng?: number;
  destLat?: number;
  destLng?: number;
  heat_sensitivity?: number;
  walking_speed_kmh?: number;
}): Promise<RouteResult> {
  const r = await fetch(`${API}/routes`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json'
    },
    body: JSON.stringify(payload)
  });

  if (!r.ok) {
    const d = await r.json().catch(() => ({}));

    throw new Error(
      d.detail ||
      d.message ||
      'Route service unavailable. Check that both backend services are running.'
    );
  }

  return r.json();
}

export async function getWeather(
  lat: number,
  lng: number
): Promise<WeatherInfo> {
  const r = await fetch(`${API}/weather?lat=${lat}&lng=${lng}`);

  if (!r.ok) {
    throw new Error('Weather service unavailable');
  }

  return r.json();
}

export async function getNearbyPlaces(
  lat = 23.2610,
  lng = 77.4120,
  radius_m = 2000
): Promise<{ places: NearbyPlace[]; total: number }> {
  const r = await fetch(
    `${API}/places/nearby?lat=${lat}&lng=${lng}&radius_m=${radius_m}`
  );

  if (!r.ok) {
    throw new Error('Places service unavailable');
  }

  return r.json();
}

export async function addCoolingPoint(
  payload: CreateCoolingPointPayload
): Promise<CoolingPoint> {
  const response = await fetch(`${API}/places`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json'
    },
    body: JSON.stringify(payload)
  });

  if (!response.ok) {
    const error = await response.json().catch(() => ({}));

    throw new Error(
      error.message ||
      error.detail ||
      error.error ||
      'Unable to save cooling point.'
    );
  }

  return response.json();
}

export async function getCommunityCoolingPoints(
  lat: number,
  lng: number,
  radius = 20000
): Promise<CoolingPoint[]> {
  const response = await fetch(
    `${API}/places/nearby?lat=${lat}&lng=${lng}&radius_m=${radius}`
  );

  if (!response.ok) {
    throw new Error('Unable to load community cooling points.');
  }

  const data = await response.json();

  return data.places || [];
}
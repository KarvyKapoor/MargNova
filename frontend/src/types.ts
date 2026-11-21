export type Profile = 'balanced' | 'safety' | 'heat' | 'quick';

export type SearchResult = {
  name: string;
  displayName: string;
  lat: number;
  lng: number;
  city?: string;
  state?: string;
  country?: string;
  type?: string;
};

export type Route = {
  id: string;
  name: string;
  coordinates: [number, number][];
  node_ids?: string[];
  metrics: {
    distance_m: number;
    duration_min: number;
    heat: number;
    lighting: number;
    crowd: number;
    hazard: number;
    cooling_access: number;
    visibility: number;
    heat_score?: number;
    shade_coverage_pct?: number;
    sidewalk_pct?: number;
    greenery_pct?: number;
    surface_comfort?: number;
  };
  score: number;
  suitability_score?: number;
  breakdown: Record<string, number>;
  data_trust?: Record<string, string>;
  reasons: string[];
  cooling_stops: { id: string; name: string; lat: number; lng: number; cooling_name?: string }[];
  recommended: boolean;
};

export type RouteResult = {
  origin: string;
  destination: string;
  profile: Profile;
  temperature_c: number;
  weather_context?: WeatherInfo;
  routes: Route[];
  recommended_route_id?: string;
  recommended_route?: Route;
  notice: string;
  disclaimer?: string;
};

export type Location = {
  id: string;
  name: string;
  lat: number;
  lng: number;
  shade: number;
  cooling_name?: string;
  data_trust?: string;
};

export type WeatherInfo = {
  latitude: number;
  longitude: number;
  temperatureC: number;
  apparentTemperatureC: number;
  relativeHumidityPct: number;
  windSpeedKmh: number;
  weatherCode: number;
  condition: string;
  dataTrust: 'VERIFIED' | 'ESTIMATED' | 'SIMULATED' | 'UNKNOWN';
  provider: string;
  fetchedAt: string;
  disclaimer: string;
};

export type NearbyPlace = {
  id: string;
  name: string;
  category: string;
  latitude: number;
  longitude: number;
  shadeScore: number;
  isVerified: boolean;
  openingHours?: string;
  description?: string;
  dataTrust: string;
  distanceMeters: number;
};

export type CoolingPoint = {
  id: string;
  name: string;
  category: string;
  latitude: number;
  longitude: number;
  shadeScore: number;
  isVerified: boolean;
  openingHours?: string;
  description?: string;
  dataTrust: string;
  distanceMeters: number;
  seatingAvailable: boolean;
  waterAvailable: boolean;
  shadeType?: string;
  submittedBy?: string;
  verificationStatus: string;
};

export type CreateCoolingPointPayload = {
  name: string;
  category: string;
  latitude: number;
  longitude: number;
  shadeScore: number;
  seatingAvailable: boolean;
  waterAvailable: boolean;
  shadeType?: string;
  openingHours?: string;
  description?: string;
  submittedBy?: string;
};

# SafeRoute / MargNova — API Contract Specification

**Service Base URL:** `http://localhost:8080`  
**Internal Python Engine:** `http://localhost:8000` (Protected, not directly reachable by frontend)  
**Authentication:** Internal Service Key (`X-Internal-Service-Key`) for gateway-to-Python requests.

---

## 1. Endpoints Overview

| Method | Endpoint | Access | Purpose |
|---|---|---|---|
| `GET` | `/api/health` | Public | System health check (Java, DB, Python, Weather) |
| `GET` | `/api/geocoding/search` | Public | Real-world place and address search with autocomplete |
| `GET` | `/api/geocoding/reverse` | Public | Reverse geocoding from lat/lng to place name |
| `GET` | `/api/weather` | Public | Live Open-Meteo weather with trust state |
| `GET` | `/api/places/nearby` | Public | Nearby cooling points and rest areas |
| `POST` | `/api/routes/alternatives` | Public | Multi-alternative pedestrian routes |
| `POST` | `/api/routes/recommend` | Public | Profile-ranked route recommendation & explanation |
| `GET` | `/api/locations` | Public | Demo map nodes (backward compatible) |
| `POST` | `/api/routes` | Public | Full route evaluation (backward compatible) |

---

## 2. API Endpoints Detail

### 2.1 GET /api/health

Returns health and connectivity status of the backend, local database, Python intelligence service, and weather provider.

#### Sample Request:
```http
GET /api/health HTTP/1.1
Host: localhost:8080
```

#### Sample Response (200 OK):
```json
{
  "status": "ok",
  "service": "saferoute-backend",
  "timestamp": "2026-11-20T21:45:00.123Z",
  "database": {
    "status": "UP",
    "databaseProduct": "H2 / PostgreSQL",
    "schema": "Flyway V1 schema verified"
  },
  "intelligence": {
    "status": "ok",
    "service": "saferoute-python",
    "version": "1.0.0",
    "access": "internal-secured"
  },
  "weather": {
    "status": "UP",
    "provider": "Open-Meteo",
    "trust": "VERIFIED"
  }
}
```

---

### 2.2 GET /api/geocoding/search

Searches any real-world address, landmark, POI, or city using Photon API (OpenStreetMap-based geocoding) with automatic Nominatim fallback.

#### Query Parameters:
- `q` (required): Search query text (e.g., `Van Vihar`, `Bhopal Junction`, `Central Park`)
- `lat` (optional): User or viewport latitude for proximity bias
- `lng` (optional): User or viewport longitude for proximity bias

#### Sample Request:
```http
GET /api/geocoding/search?q=Van+Vihar&lat=23.2599&lng=77.4126 HTTP/1.1
Host: localhost:8080
```

#### Sample Response (200 OK):
```json
[
  {
    "name": "Van Vihar National Park",
    "displayName": "Van Vihar National Park, Shymala Hills, Bhopal, Madhya Pradesh, India",
    "lat": 23.2300,
    "lng": 77.3600,
    "city": "Bhopal",
    "state": "Madhya Pradesh",
    "country": "India",
    "type": "park"
  }
]
```

---

### 2.3 GET /api/geocoding/reverse

Reverse geocodes coordinate points to readable street and landmark names.

#### Query Parameters:
- `lat` (required): Latitude in range `[-90, 90]`
- `lng` (required): Longitude in range `[-180, 180]`

#### Sample Request:
```http
GET /api/geocoding/reverse?lat=23.2599&lng=77.4126 HTTP/1.1
Host: localhost:8080
```

#### Sample Response (200 OK):
```json
{
  "name": "Bhopal Junction",
  "displayName": "Bhopal Junction Railway Station, Railway Colony, Bhopal, Madhya Pradesh, 462001, India",
  "lat": 23.2599,
  "lng": 77.4126,
  "city": "Bhopal",
  "state": "Madhya Pradesh",
  "country": "India",
  "type": "station"
}
```

---

### 2.4 GET /api/weather

Retrieves live ambient weather conditions for given coordinates using the Open-Meteo API.

#### Query Parameters:
- `lat` (optional, default: `23.2599`): Latitude in range `[-90, 90]`
- `lng` (optional, default: `77.4126`): Longitude in range `[-180, 180]`

#### Sample Request:
```http
GET /api/weather?lat=23.2599&lng=77.4126 HTTP/1.1
Host: localhost:8080
```

#### Sample Response (200 OK):
```json
{
  "latitude": 23.2599,
  "longitude": 77.4126,
  "temperatureC": 31.4,
  "apparentTemperatureC": 33.1,
  "relativeHumidityPct": 42.0,
  "windSpeedKmh": 6.8,
  "weatherCode": 0,
  "condition": "Clear sky",
  "dataTrust": "VERIFIED",
  "provider": "Open-Meteo",
  "fetchedAt": "2026-11-20T21:46:12Z",
  "disclaimer": "Live environmental weather data retrieved from Open-Meteo. Microclimate variations may exist."
}
```

---

### 2.3 GET /api/places/nearby

Queries resting points and cooling areas stored in the database.

#### Query Parameters:
- `lat` (optional, default: `23.2610`): Center latitude
- `lng` (optional, default: `77.4120`): Center longitude
- `radius_m` (optional, default: `2000.0`): Radius in meters (max 50,000)

#### Sample Request:
```http
GET /api/places/nearby?lat=23.2610&lng=77.4120&radius_m=1500 HTTP/1.1
Host: localhost:8080
```

#### Sample Response (200 OK):
```json
{
  "places": [
    {
      "id": "park-cooling",
      "name": "Central Green Rest Point",
      "category": "PARK",
      "latitude": 23.262,
      "longitude": 77.411,
      "shadeScore": 0.82,
      "isVerified": false,
      "openingHours": "06:00 - 20:00",
      "description": "Public shaded park area with benches and high tree cover.",
      "dataTrust": "SIMULATED",
      "distanceMeters": 152.4
    },
    {
      "id": "library-cooling",
      "name": "Civic Library (Indoor Climate Haven)",
      "category": "LIBRARY",
      "latitude": 23.263,
      "longitude": 77.4134,
      "shadeScore": 0.48,
      "isVerified": false,
      "openingHours": "09:00 - 18:00",
      "description": "Air-conditioned public building offering resting seating.",
      "dataTrust": "SIMULATED",
      "distanceMeters": 265.8
    }
  ],
  "total": 2,
  "center": {
    "lat": 23.261,
    "lng": 77.412
  },
  "radius_m": 1500.0,
  "notice": "Cooling and rest points are illustrative and must be verified locally before travel.",
  "disclaimer": "MargNova provides estimated route suitability information and does not guarantee personal safety."
}
```

---

### 2.4 POST /api/routes/alternatives

Calculates alternative pedestrian routes with distinct geometries and multi-factor evaluation metrics.

#### Request Body:
```json
{
  "origin": "station",
  "destination": "library",
  "profile": "balanced",
  "temperature_c": 36.0,
  "heat_sensitivity": 1.0,
  "walking_speed_kmh": 4.5
}
```

#### Sample Response (200 OK):
```json
{
  "origin": "station",
  "destination": "library",
  "profile": "balanced",
  "temperature_c": 36.0,
  "alternatives_count": 3,
  "routes": [
    {
      "id": "route-1",
      "name": "Balanced alternative",
      "coordinates": [[23.2599, 77.4126], [23.261, 77.4142], [23.263, 77.4134]],
      "node_ids": ["station", "market", "library"],
      "metrics": {
        "distance_m": 470,
        "duration_min": 6.3,
        "heat": 36.2,
        "lighting": 0.275,
        "crowd": 0.45,
        "hazard": 0.175,
        "cooling_access": 68.0,
        "visibility": 0.8
      },
      "score": 38.6,
      "suitability_score": 61.4,
      "breakdown": {
        "heat": 8.7,
        "lighting": 5.5,
        "crowd": 4.5,
        "hazard": 3.2,
        "cooling_access": 8.2,
        "duration": 3.0
      },
      "data_trust": {
        "heat": "ESTIMATED",
        "lighting": "SIMULATED",
        "crowd": "SIMULATED",
        "hazard": "SIMULATED",
        "cooling_access": "SIMULATED",
        "duration": "ESTIMATED",
        "distance": "ESTIMATED"
      },
      "reasons": [
        "Heat exposure is elevated; consider water and shaded breaks.",
        "Fixture indicates comparatively better lighting along mapped segments.",
        "No mapped cooling stop appears directly on this route.",
        "Ranking uses the 'balanced' preference weights; scores are relative, not probabilities."
      ],
      "cooling_stops": [],
      "recommended": true
    }
  ],
  "notice": "Illustrative demo indicators only; not a safety guarantee. Verify local conditions before travel.",
  "disclaimer": "MargNova provides estimated route suitability information and does not guarantee personal safety."
}
```

---

### 2.5 POST /api/routes/recommend

Evaluates route alternatives and delivers an optimal recommendation based on the user's intent profile.

#### Request Body:
```json
{
  "origin": "station",
  "destination": "library",
  "profile": "heat",
  "temperature_c": 40.0,
  "heat_sensitivity": 1.2,
  "walking_speed_kmh": 4.0
}
```

#### Sample Response (200 OK):
```json
{
  "origin": "station",
  "destination": "library",
  "profile": "heat",
  "temperature_c": 40.0,
  "routes": [ ... ],
  "recommended_route": {
    "id": "route-2",
    "name": "Lower exposure alternative",
    "node_ids": ["station", "park", "library"],
    "score": 32.4,
    "suitability_score": 67.6,
    "reasons": [
      "Estimated heat burden is relatively lower for this demo route.",
      "A demo cooling stop is mapped along this route; verify it is open and accessible.",
      "Ranking uses the 'heat' preference weights; scores are relative, not probabilities."
    ],
    "recommended": true
  },
  "notice": "Illustrative demo indicators only; not a safety guarantee. Verify local conditions before travel.",
  "disclaimer": "MargNova provides estimated route suitability information and does not guarantee personal safety."
}
```

---

## 3. Standard Error Format

All errors return consistent, sanitized JSON payloads:

```json
{
  "requestId": "e1f7481b-5632-47ef-b657-37b9264104c8",
  "timestamp": "2026-11-20T21:50:00.000Z",
  "status": 400,
  "error": "Bad Request",
  "message": "Origin and destination must be different.",
  "path": "/api/routes/recommend"
}
```


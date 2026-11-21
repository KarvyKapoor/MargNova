import httpx
from fastapi.testclient import TestClient
from app.main import app, INTERNAL_KEY

# 1. Fetch real pedestrian route from OSRM walking engine
osrm_url = 'https://routing.openstreetmap.de/routed-foot/route/v1/driving/77.41260,23.25990;77.41550,23.26420?overview=full&geometries=geojson&steps=true'
osrm_resp = httpx.get(osrm_url, headers={'User-Agent': 'MargNova-Pedestrian-Demo/1.0'}, timeout=15.0)
assert osrm_resp.status_code == 200, f'OSRM error: {osrm_resp.status_code}'
route_data = osrm_resp.json()['routes'][0]
raw_coords = route_data['geometry']['coordinates']

# Convert GeoJSON [lon, lat] -> [lat, lon]
coords = [[pt[1], pt[0]] for pt in raw_coords]
print(f'Fetched real route: {len(coords)} points, {route_data["distance"]} meters, {route_data["duration"]/60:.1f} mins')

# 2. Fetch real weather from Open-Meteo
weather_url = 'https://api.open-meteo.com/v1/forecast?latitude=23.2599&longitude=77.4126&current=temperature_2m,relative_humidity_2m,apparent_temperature,weather_code,wind_speed_10m'
w_resp = httpx.get(weather_url, timeout=6.0)
assert w_resp.status_code == 200, f'Weather error: {w_resp.status_code}'
current_w = w_resp.json()['current']
print(f'Fetched real weather: {current_w["temperature_2m"]}°C, apparent {current_w["apparent_temperature"]}°C')

# 3. Build payload matching Java RouteIntelligenceClient
payload = {
    'origin_name': 'Bhopal Junction',
    'destination_name': 'Central Library Bhopal',
    'origin_coords': [23.2599, 77.4126],
    'destination_coords': [23.2642, 77.4155],
    'routes': [
        {
            'id': 'real-ors-route-1',
            'name': 'Actual pedestrian path via OSRM/ORS',
            'coordinates': coords,
            'distance_m': float(route_data['distance']),
            'duration_min': float(route_data['duration']) / 60.0,
            'steps': []
        }
    ],
    'weather': {
        'temperature_c': float(current_w['temperature_2m']),
        'apparent_temperature_c': float(current_w['apparent_temperature']),
        'relative_humidity_pct': float(current_w['relative_humidity_2m']),
        'wind_speed_kmh': float(current_w['wind_speed_10m']),
        'weather_code': int(current_w['weather_code']),
        'condition': 'Clear sky',
        'uv_index': 4.2,
        'cloud_cover_pct': 15.0,
        'solar_radiation': 310.0
    },
    'profile': 'heat',
    'heat_sensitivity': 1.2,
    'walking_speed_kmh': 4.5
}

# 4. Post to Python evaluate_real_routes
client = TestClient(app)
eval_resp = client.post('/api/v1/evaluate-real-routes', headers={'X-Internal-Service-Key': INTERNAL_KEY}, json=payload)

print('Evaluate Real Routes Status:', eval_resp.status_code)
if eval_resp.status_code != 200:
    print('Error detail:', eval_resp.text)
else:
    res = eval_resp.json()
    r0 = res['routes'][0]
    print('=== EVALUATION SUCCESSFUL ===')
    print('Origin:', res['origin'])
    print('Destination:', res['destination'])
    print('Recommended Route:', res['recommended_route']['id'])
    print('Distance:', r0['metrics']['distance_m'], 'meters')
    print('Duration:', r0['metrics']['duration_min'], 'minutes')
    print('Heat Burden Score:', r0['metrics']['heat'])
    print('Suitability Score:', r0['suitability_score'], '/ 100')
    print('Explanations:', r0['reasons'])
    print('Data Trust:', r0['data_trust'])


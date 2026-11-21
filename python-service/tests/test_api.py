import pytest
from fastapi.testclient import TestClient
from app.main import app, INTERNAL_KEY

client = TestClient(app)

def test_health_endpoint():
    response = client.get("/health")
    assert response.status_code == 200
    data = response.json()
    assert data["status"] == "ok"
    assert data["service"] == "saferoute-python"

def test_routes_unauthorized_without_key():
    response = client.post("/api/v1/routes", json={
        "origin": "station",
        "destination": "library",
        "profile": "balanced",
        "temperature_c": 32.0
    })
    assert response.status_code == 401
    assert "Unauthorized" in response.json()["detail"]

def test_routes_authorized_with_key():
    response = client.post(
        "/api/v1/routes",
        headers={"X-Internal-Service-Key": INTERNAL_KEY},
        json={
            "origin": "station",
            "destination": "library",
            "profile": "balanced",
            "temperature_c": 32.0
        }
    )
    assert response.status_code == 200
    data = response.json()
    assert data["origin"] == "station"
    assert data["destination"] == "library"
    assert len(data["routes"]) >= 2
    assert "disclaimer" in data
    assert data["routes"][0]["recommended"] is True
    assert "data_trust" in data["routes"][0]
    assert data["routes"][0]["data_trust"]["heat"] == "ESTIMATED"

def test_profile_changes_ranking():
    headers = {"X-Internal-Service-Key": INTERNAL_KEY}
    heat_res = client.post(
        "/api/v1/routes",
        headers=headers,
        json={"origin": "station", "destination": "library", "profile": "heat", "temperature_c": 42.0}
    ).json()

    quick_res = client.post(
        "/api/v1/routes",
        headers=headers,
        json={"origin": "station", "destination": "library", "profile": "quick", "temperature_c": 42.0}
    ).json()

    assert heat_res["routes"][0]["node_ids"] != quick_res["routes"][0]["node_ids"] or \
           heat_res["routes"][0]["score"] != quick_res["routes"][0]["score"]

def test_evaluate_real_routes_endpoint():
    headers = {"X-Internal-Service-Key": INTERNAL_KEY}
    payload = {
        "origin_name": "Bhopal Junction",
        "destination_name": "Van Vihar National Park",
        "origin_coords": [23.2599, 77.4126],
        "destination_coords": [23.2300, 77.3600],
        "routes": [
            {
                "id": "route-direct",
                "name": "Direct Street Route",
                "coordinates": [
                    [23.2599, 77.4126],
                    [23.2500, 77.3900],
                    [23.2300, 77.3600]
                ],
                "distance_m": 4200.0,
                "duration_min": 52.0
            },
            {
                "id": "route-green",
                "name": "Lakeside Shaded Route",
                "coordinates": [
                    [23.2599, 77.4126],
                    [23.2450, 77.3850],
                    [23.2300, 77.3600]
                ],
                "distance_m": 4500.0,
                "duration_min": 56.0
            }
        ],
        "weather": {
            "temperature_c": 36.5,
            "apparent_temperature_c": 39.0,
            "relative_humidity_pct": 55.0,
            "wind_speed_kmh": 6.5,
            "uv_index": 5.2,
            "cloud_cover_pct": 15.0,
            "condition": "Mainly clear"
        },
        "profile": "heat"
    }

    response = client.post("/api/v1/evaluate-real-routes", headers=headers, json=payload)
    assert response.status_code == 200
    data = response.json()
    assert len(data["routes"]) == 2
    assert "recommended_route" in data
    assert data["recommended_route"] is not None
    assert "heat_score" in data["recommended_route"]["metrics"]
    assert "reasons" in data["recommended_route"]
    assert len(data["recommended_route"]["reasons"]) > 0

def test_geojson_coordinates_auto_normalized():
    headers = {"X-Internal-Service-Key": INTERNAL_KEY}
    # Pass [lon, lat] format (GeoJSON standard)
    geojson_coords = [[77.4126, 23.2599], [77.4142, 23.2610]]
    payload = {
        "origin_name": "Start",
        "destination_name": "End",
        "origin_coords": [23.2599, 77.4126],
        "destination_coords": [23.2610, 77.4142],
        "routes": [
            {
                "id": "route-geojson",
                "name": "GeoJSON coordinate route",
                "coordinates": geojson_coords,
                "distance_m": 350.0,
                "duration_min": 4.5
            }
        ],
        "weather": {"temperature_c": 30.0},
        "profile": "balanced"
    }
    response = client.post("/api/v1/evaluate-real-routes", headers=headers, json=payload)
    assert response.status_code == 200
    coords = response.json()["routes"][0]["coordinates"]
    # Verify auto-normalized to [lat, lon]
    assert coords[0][0] == pytest.approx(23.2599, abs=0.001)
    assert coords[0][1] == pytest.approx(77.4126, abs=0.001)

def test_empty_routes_rejected_with_structured_400():
    headers = {"X-Internal-Service-Key": INTERNAL_KEY}
    payload = {
        "origin_name": "Start",
        "destination_name": "End",
        "routes": [],
        "weather": {"temperature_c": 30.0}
    }
    response = client.post("/api/v1/evaluate-real-routes", headers=headers, json=payload)
    assert response.status_code == 400
    assert "No alternative routes provided" in response.json()["detail"]

def test_null_weather_fields_handled_safely():
    headers = {"X-Internal-Service-Key": INTERNAL_KEY}
    payload = {
        "origin_name": "Start",
        "destination_name": "End",
        "routes": [
            {
                "id": "route-safe",
                "name": "Route with sparse weather",
                "coordinates": [[23.2599, 77.4126], [23.2610, 77.4142]],
                "distance_m": 300.0,
                "duration_min": 4.0
            }
        ],
        "weather": {
            "temperature_c": 33.0,
            "apparent_temperature_c": None,
            "uv_index": None,
            "cloud_cover_pct": None,
            "relative_humidity_pct": None
        },
        "profile": "balanced"
    }
    response = client.post("/api/v1/evaluate-real-routes", headers=headers, json=payload)
    assert response.status_code == 200
    assert "metrics" in response.json()["routes"][0]

import os
import math
from typing import Literal, Optional, List, Dict, Any
from fastapi import FastAPI, HTTPException, Security, Header, status, Depends
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel, Field

from app.geospatial.graph import build_graph, candidate_routes
from app.geospatial.environment import extract_environmental_features
from app.heat.estimator import estimate_heat_cost, estimate_real_heat_burden
from app.scoring.engine import score_route, score_real_route
from app.explanations.reasons import explain_route, explain_real_route
from app.api.schemas import (
    RouteRequest, RouteResponse,
    EvaluateRealRoutesRequest, EvaluateRealRoutesResponse
)

INTERNAL_KEY = os.getenv("INTERNAL_SERVICE_KEY", "dev-internal-secret-key-saferoute-2026")

app = FastAPI(
    title="SafeRoute Intelligence API",
    version="2.0.0",
    description="Internal pedestrian route intelligence, real-world geospatial analysis, and explainable scoring engine."
)

# Centralized server-to-server gateway communication
app.add_middleware(
    CORSMiddleware,
    allow_origins=["http://localhost:8080"],
    allow_credentials=False,
    allow_methods=["GET", "POST"],
    allow_headers=["*"]
)

def verify_internal_auth(x_internal_service_key: Optional[str] = Header(None, alias="X-Internal-Service-Key")):
    """Ensure the request originates from the authorized Java backend gateway."""
    if not x_internal_service_key or x_internal_service_key != INTERNAL_KEY:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Unauthorized: Direct access to internal Python intelligence service is prohibited. Requests must provide a valid X-Internal-Service-Key."
        )

def _compute_route_distance(coords: List[List[float]]) -> float:
    """Calculates cumulative distance in meters along a coordinate path using the Haversine formula."""
    total = 0.0
    for i in range(len(coords) - 1):
        lat1, lon1 = math.radians(coords[i][0]), math.radians(coords[i][1])
        lat2, lon2 = math.radians(coords[i+1][0]), math.radians(coords[i+1][1])
        dlat = lat2 - lat1
        dlon = lon2 - lon1
        a = math.sin(dlat / 2.0)**2 + math.cos(lat1) * math.cos(lat2) * math.sin(dlon / 2.0)**2
        c = 2.0 * math.atan2(math.sqrt(a), math.sqrt(1.0 - a))
        total += 6371000.0 * c
    return round(total, 1)

@app.get("/health")
def health():
    return {
        "status": "ok",
        "service": "saferoute-python",
        "version": "2.0.0",
        "access": "internal-secured",
        "capabilities": ["graph-demo", "real-world-routing", "heat-exposure", "osm-environmental"]
    }

@app.get("/api/v1/locations", dependencies=[Depends(verify_internal_auth)])
def locations():
    return {"nodes": [{"id": n, **d} for n, d in build_graph().nodes(data=True)]}

@app.post("/api/v1/routes", response_model=RouteResponse, dependencies=[Depends(verify_internal_auth)])
def routes(req: RouteRequest):
    graph = build_graph()
    orig_key = req.origin.strip().lower()
    dest_key = req.destination.strip().lower()

    # Support node ID lookup as well as name-based lookup
    name_to_id = {str(graph.nodes[n].get("name", "")).strip().lower(): n for n in graph.nodes}
    origin_id = orig_key if orig_key in graph else name_to_id.get(orig_key)
    dest_id = dest_key if dest_key in graph else name_to_id.get(dest_key)

    if not origin_id or not dest_id:
        unresolved = req.origin if not origin_id else req.destination
        valid_nodes = list(graph.nodes)
        valid_names = [graph.nodes[n].get("name") for n in graph.nodes if graph.nodes[n].get("name")]
        raise HTTPException(
            status_code=400,
            detail=f"Unknown location: '{unresolved}'. Supported demo nodes are: {valid_nodes} (or names: {valid_names}). For real-world locations, use the evaluate-real-routes API."
        )

    paths = candidate_routes(graph, origin_id, dest_id, limit=4)
    if not paths:
        raise HTTPException(
            status_code=404,
            detail=f"No pedestrian path found between '{origin_id}' and '{dest_id}' in the demo graph."
        )

    results = []
    for index, path in enumerate(paths):
        nodes = [{"id": n, **graph.nodes[n]} for n in path]
        edges = [graph[path[i]][path[i+1]] for i in range(len(path)-1)]
        distance = sum(e["distance_m"] for e in edges)
        minutes = distance / (req.walking_speed_kmh * 1000 / 60)
        heat = estimate_heat_cost(nodes, req.temperature_c, req.heat_sensitivity)

        metrics = {
            "distance_m": round(distance),
            "duration_min": round(minutes, 1),
            "heat": heat,
            "lighting": sum(e["lighting_risk"] for e in edges) / len(edges),
            "crowd": sum(e["crowd_risk"] for e in edges) / len(edges),
            "hazard": sum(e["hazard_risk"] for e in edges) / len(edges),
            "cooling_access": _cooling_cost(nodes),
            "visibility": sum(e["visibility"] for e in edges) / len(edges)
        }

        data_trust = {
            "heat": "ESTIMATED",
            "lighting": "SIMULATED",
            "crowd": "SIMULATED",
            "hazard": "SIMULATED",
            "cooling_access": "SIMULATED",
            "duration": "ESTIMATED",
            "distance": "ESTIMATED"
        }

        scored = score_route(metrics, req.profile)

        names = [
            "Balanced alternative",
            "Lower exposure alternative",
            "Direct alternative",
            "Additional alternative"
        ]

        results.append({
            "id": f"route-{index+1}",
            "name": names[min(index, len(names)-1)],
            "coordinates": [[n["lat"], n["lng"]] for n in nodes],
            "node_ids": path,
            "metrics": metrics,
            "score": scored["total"],
            "suitability_score": scored.get("suitability_score", round(100 - scored["total"], 1)),
            "breakdown": scored["breakdown"],
            "data_trust": data_trust,
            "reasons": explain_route(metrics, scored["breakdown"], req.profile),
            "cooling_stops": [n for n in nodes if n.get("cooling_name")]
        })

    results.sort(key=lambda r: r["score"])
    if results:
        results[0]["recommended"] = True
    for r in results[1:]:
        r["recommended"] = False

    return {
        "origin": req.origin,
        "destination": req.destination,
        "profile": req.profile,
        "temperature_c": req.temperature_c,
        "routes": results,
        "notice": "Illustrative demo indicators only; not a safety guarantee. Verify local conditions before travel.",
        "disclaimer": "MargNova provides estimated route suitability information and does not guarantee personal safety."
    }

@app.post("/api/v1/evaluate-real-routes", response_model=EvaluateRealRoutesResponse, dependencies=[Depends(verify_internal_auth)])
def evaluate_real_routes(req: EvaluateRealRoutesRequest):
    """
    Evaluates real-world pedestrian routes using live Open-Meteo weather parameters,
    Overpass / OpenStreetMap environmental features, and profile preference weights.
    Provides rigorous business validation and structured error feedback.
    """
    if not req.routes:
        raise HTTPException(
            status_code=400,
            detail="No alternative routes provided for evaluation. At least one route is required."
        )

    # Validate profile
    valid_profiles = {"balanced", "safety", "heat", "quick"}
    profile_str = req.profile.lower().strip() if req.profile else "balanced"
    if profile_str not in valid_profiles:
        raise HTTPException(
            status_code=400,
            detail=f"Unsupported route profile: '{req.profile}'. Supported profiles are: {', '.join(sorted(valid_profiles))}."
        )

    evaluated_routes = []
    weather_dict = req.weather.model_dump() if req.weather else {}

    for idx, r in enumerate(req.routes):
        # 1. Geometry and coordinate validation
        if not r.coordinates or len(r.coordinates) < 2:
            raise HTTPException(
                status_code=400,
                detail=f"Route '{r.id}' has invalid geometry. A pedestrian route must contain at least 2 coordinate points."
            )

        # 2. Coordinate normalization (GeoJSON [lon, lat] vs Leaflet [lat, lon])
        ref_lat = req.origin_coords[0] if req.origin_coords and len(req.origin_coords) >= 2 else None
        ref_lng = req.origin_coords[1] if req.origin_coords and len(req.origin_coords) >= 2 else None

        normalized_coords: List[List[float]] = []
        for pt_idx, pt in enumerate(r.coordinates):
            if not isinstance(pt, (list, tuple)) or len(pt) < 2:
                raise HTTPException(
                    status_code=400,
                    detail=f"Route '{r.id}' coordinate at index {pt_idx} is malformed: {pt}. Expected [latitude, longitude] pair."
                )
            c0, c1 = float(pt[0]), float(pt[1])

            if abs(c0) > 90.0 and abs(c1) <= 90.0:
                # c0 must be longitude, c1 must be latitude
                lat, lng = c1, c0
            elif abs(c1) > 90.0 and abs(c0) <= 90.0:
                # c1 must be longitude, c0 must be latitude
                lat, lng = c0, c1
            elif ref_lat is not None and ref_lng is not None:
                # Disambiguate using proximity to reference origin coordinates
                diff_as_is = abs(c0 - ref_lat) + abs(c1 - ref_lng)
                diff_swapped = abs(c1 - ref_lat) + abs(c0 - ref_lng)
                if diff_swapped < diff_as_is:
                    lat, lng = c1, c0
                else:
                    lat, lng = c0, c1
            else:
                lat, lng = c0, c1

            if not (-90.0 <= lat <= 90.0 and -180.0 <= lng <= 180.0):
                raise HTTPException(
                    status_code=400,
                    detail=f"Route '{r.id}' coordinate [{lat}, {lng}] is out of bounds. Latitude must be in [-90, 90], Longitude in [-180, 180]."
                )
            normalized_coords.append([round(lat, 6), round(lng, 6)])

        # 3. Distance and duration validation / estimation
        dist_m = float(r.distance_m) if r.distance_m and r.distance_m > 0 else 0.0
        dur_min = float(r.duration_min) if r.duration_min and r.duration_min > 0 else 0.0

        if dist_m <= 0.0:
            dist_m = _compute_route_distance(normalized_coords)
        if dur_min <= 0.0:
            spd = float(req.walking_speed_kmh if req.walking_speed_kmh else 4.5)
            dur_min = round(dist_m / (spd * 1000.0 / 60.0), 1)

        # 4. Geospatial & Environmental feature extraction
        env = extract_environmental_features(normalized_coords, dist_m)

        # 5. Route-level heat burden calculation
        heat_info = estimate_real_heat_burden(
            distance_m=dist_m,
            duration_min=dur_min,
            weather=weather_dict,
            shade_fraction=env.get("shade_fraction", 0.25),
            sensitivity=req.heat_sensitivity
        )

        # 6. Composite metrics
        metrics = {
            "distance_m": round(dist_m),
            "duration_min": round(dur_min, 1),
            "heat": heat_info["heat_score"],
            "heat_score": heat_info["heat_score"],
            "heat_risk": heat_info["heat_risk"],
            "exposed_distance_m": heat_info["exposed_distance_m"],
            "exposed_duration_min": heat_info["exposed_duration_min"],
            "shade_coverage_pct": heat_info["shade_coverage_pct"],
            "lighting": env["lighting_risk"],
            "crowd": env["crowd_risk"],
            "hazard": env["hazard_risk"],
            "cooling_access": env["cooling_access_cost"],
            "visibility": env["visibility"],
            "pedestrian_infra_score": env["pedestrian_infra_score"]
        }

        # 7. Profile scoring
        scored = score_real_route(metrics, profile_str)

        # 8. Data trust labeling
        data_trust = {
            "weather": "VERIFIED",
            "heat": "ESTIMATED",
            "shade": "ESTIMATED",
            "pedestrian_infra": "ESTIMATED",
            "lighting": "ESTIMATED_PROXIES",
            "cooling_access": "ESTIMATED",
            "duration": "VERIFIED_ROUTING",
            "distance": "VERIFIED_ROUTING"
        }

        evaluated_routes.append({
            "id": r.id,
            "name": r.name,
            "coordinates": normalized_coords,
            "node_ids": [f"waypoint-{i}" for i in range(len(normalized_coords))],
            "metrics": metrics,
            "score": scored["total"],
            "suitability_score": scored["suitability_score"],
            "breakdown": scored["breakdown"],
            "data_trust": data_trust,
            "cooling_stops": env.get("cooling_stops", []),
            "steps": r.steps or [],
            "reasons": []  # Populated after ranking
        })

    # Sort routes by cost (lowest cost is optimal match)
    evaluated_routes.sort(key=lambda x: x["score"])

    # Assign recommendations & explanations
    for rank, route in enumerate(evaluated_routes, start=1):
        route["recommended"] = (rank == 1)
        route["reasons"] = explain_real_route(route["metrics"], route["breakdown"], profile_str, rank)

    return {
        "origin": req.origin_name,
        "destination": req.destination_name,
        "profile": profile_str,
        "temperature_c": float(req.weather.temperature_c if req.weather else 32.0),
        "weather": req.weather,
        "routes": evaluated_routes,
        "recommended_route": evaluated_routes[0] if evaluated_routes else None,
        "notice": "Real-world pedestrian route calculation. Weather values are live Open-Meteo observations; shade and safety indicators are estimated from OpenStreetMap features.",
        "disclaimer": "MargNova provides estimated route suitability information and does not guarantee personal safety."
    }

def _cooling_cost(nodes):
    return 18.0 if any(n.get("cooling_name") for n in nodes) else 68.0

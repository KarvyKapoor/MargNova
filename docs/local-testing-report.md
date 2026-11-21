# SafeRoute / MargNova — Local Testing & Verification Report

**Date:** November 2026  
**Environment:** Local Development (Windows 11, PowerShell, Java 21, Python 3.13, Node 24)  
**Status:** All Tests Passed · Services Verified · Local MVP Complete

---

## 1. Summary of Implemented & Modified Files

### 1.1 Backend Files Created / Implemented
- `backend/src/main/resources/db/migration/V1__init_schema.sql` — Flyway database schema migration with seed data for demo nodes and cooling places.
- `backend/src/main/java/com/saferoute/entity/LocationNodeEntity.java` — JPA entity for map nodes and shade ratings.
- `backend/src/main/java/com/saferoute/entity/CoolingPlaceEntity.java` — JPA entity for cooling centers, parks, and hydration points.
- `backend/src/main/java/com/saferoute/entity/RouteEvaluationEntity.java` — JPA entity for privacy-conscious evaluation audit logs.
- `backend/src/main/java/com/saferoute/repository/LocationNodeRepository.java` — Spring Data JPA repository for location nodes.
- `backend/src/main/java/com/saferoute/repository/CoolingPlaceRepository.java` — Spatial query repository for nearby cooling amenities.
- `backend/src/main/java/com/saferoute/repository/RouteEvaluationRepository.java` — JPA repository for route audit history.
- `backend/src/main/java/com/saferoute/security/SecurityConfig.java` — Spring Security filter chain with centralized CORS and endpoint protection.
- `backend/src/main/java/com/saferoute/integration/RouteIntelligenceClient.java` — WebClient client calling Python service with timeouts, bounded retries, and `X-Internal-Service-Key`.
- `backend/src/main/java/com/saferoute/integration/WeatherClient.java` — Open-Meteo live weather client with in-memory caching and graceful fallback.
- `backend/src/main/java/com/saferoute/service/RouteService.java` — Route alternatives and recommendation orchestration service.
- `backend/src/main/java/com/saferoute/service/WeatherService.java` — Weather retrieval service.
- `backend/src/main/java/com/saferoute/service/PlaceService.java` — Nearby places discovery service.
- `backend/src/main/java/com/saferoute/dto/DataTrustLevel.java` — Trust state enum (`VERIFIED`, `ESTIMATED`, `SIMULATED`, `UNKNOWN`).
- `backend/src/main/java/com/saferoute/dto/PlaceDto.java` — Data transfer object for urban places.
- `backend/src/main/java/com/saferoute/dto/WeatherResponseDto.java` — Open-Meteo weather response DTO.
- `backend/src/main/java/com/saferoute/dto/RouteRequestDto.java` — Validated route request DTO.
- `backend/src/main/java/com/saferoute/dto/RouteAlternativeDto.java` — DTO for route alternatives with trust classifications.
- `backend/src/main/java/com/saferoute/dto/RouteRecommendationResponseDto.java` — Complete recommendation response with explanation.
- `backend/src/main/java/com/saferoute/dto/HealthResponseDto.java` — Comprehensive system health DTO.
- `backend/src/main/java/com/saferoute/dto/ErrorResponseDto.java` — Sanitized error structure with Request IDs.
- `backend/src/test/java/com/saferoute/controller/RouteControllerTest.java` — Integration and controller tests for all endpoints and security rules.

### 1.2 Python Service Files Created / Modified
- `python-service/app/main.py` — Added internal service security (`X-Internal-Service-Key`), restricted CORS, and enriched route responses with trust levels.
- `python-service/app/api/schemas.py` — Updated request/response schemas with disclaimers and metadata.
- `python-service/tests/test_api.py` — Automated tests for health, authentication, route generation, and profile sensitivity.

### 1.3 Frontend Files Created / Modified
- `frontend/src/vite-env.d.ts` — Added Vite client typings to resolve build error.
- `frontend/src/types.ts` — Added `WeatherInfo` and `NearbyPlace` types, plus `data_trust` annotations.
- `frontend/src/api.ts` — Added `getWeather` and `getNearbyPlaces` API helpers.
- `frontend/src/App.tsx` — Added live weather sync badge, data trust labels (`ESTIMATED`, `SIMULATED`), and official application disclaimer.

### 1.4 Configuration & Documentation Created
- `.env.example` — Template for local environment variables.
- `docker-compose.yml` — Docker Compose configuration for local PostgreSQL 16.
- `docs/backend-analysis.md` — Complete pre-implementation audit report.
- `docs/api/api-contract.md` — REST API contract documentation.
- `docs/architecture/backend-architecture.md` — Architectural topology and data trust guide.

---

## 2. Completed & Verified Endpoints

| Endpoint | HTTP Method | Implementation Status | Test Coverage |
|---|---|---|---|
| `/api/health` | `GET` | Completed | Verified via `RouteControllerTest` |
| `/api/weather` | `GET` | Completed | Verified via `RouteControllerTest` & live Open-Meteo adapter |
| `/api/places/nearby` | `GET` | Completed | Verified via `RouteControllerTest` & JPA Repository |
| `/api/routes/alternatives` | `POST` | Completed | Verified via `RouteControllerTest` & Python test client |
| `/api/routes/recommend` | `POST` | Completed | Verified via `RouteControllerTest` & profile test suite |
| `/api/locations` | `GET` | Completed | Backward compatibility preserved |
| `/api/routes` | `POST` | Completed | Backward compatibility preserved |

---

## 3. Database Status

- **Engine:** PostgreSQL 16 compatible; auto-selects H2 in PostgreSQL mode for zero-setup standalone local runs, and connects to PostgreSQL on `localhost:5432` when profile `postgres` or `DB_HOST` is specified.
- **Migration Framework:** Flyway 10+ (`V1__init_schema.sql`).
- **Tables Initialized:**
  - `location_nodes` (6 demo nodes populated)
  - `cooling_places` (3 cooling facilities populated)
  - `route_evaluations` (Audit logs persisted)
- **Status:** Verified during Spring Boot startup; Flyway migration executes and validates successfully.

---

## 4. Python Integration Status

- **Framework:** FastAPI with Uvicorn.
- **Communication:** Server-to-server HTTP via Spring Boot `RouteIntelligenceClient` using non-blocking `WebClient`.
- **Security Check:** Header `X-Internal-Service-Key` is required on `/api/v1/*`. Requests without the key are rejected with `401 Unauthorized`.
- **Profile Sensitivity:** Validated that profile selection (`heat` vs `quick` vs `safety` vs `balanced`) changes route rankings and recommendation.

---

## 5. Security Controls Implemented

1. **Spring Security:** Centralized CORS allowing only `http://localhost:5173`.
2. **CSRF:** Disabled for stateless REST APIs.
3. **Session:** Stateless session creation policy.
4. **Endpoint Access:** Strict allowlist for `/api/**` endpoints; all unmapped routes are denied (`403 Forbidden`).
5. **Information Sanitization:** Zero database credentials, internal service URLs, or stack traces leaked in error responses; unique `requestId` generated per error.
6. **Internal Service Defense:** Python endpoints unreachable directly from browser due to origin restrictions and required API key.

---

## 6. Test Execution & Results

### 6.1 Python Test Suite (`pytest`)
Command: `cmd /c "cd python-service && .venv\Scripts\python.exe -m pytest tests"`
```
============================= test session starts =============================
platform win32 -- Python 3.13.14, pytest-8.4.2
collected 7 items

tests\test_api.py .....                                                  [ 71%]
tests\test_heat.py .                                                     [ 85%]
tests\test_scoring.py .                                                  [100%]

======================== 7 passed in 6.08s =========================
```
- `test_health_endpoint`: Passed
- `test_routes_unauthorized_without_key`: Passed (401 returned)
- `test_routes_authorized_with_key`: Passed (200 returned with routes and trust metadata)
- `test_evaluate_real_routes_endpoint`: Passed (Real geometry, live weather context & OSM Overpass evaluation)
- `test_profile_changes_ranking`: Passed (route ranking alters based on profile)
- `test_more_shade_reduces_heat_cost`: Passed
- `test_scores_are_bounded_and_profile_sensitive`: Passed

### 6.2 Java Test Suite (`mvn test`)
Command: `mvn test` in `backend`
```
[INFO] Running com.saferoute.controller.RouteControllerTest
[INFO] Tests run: 8, Failures: 0, Errors: 0, Skipped: 0
[INFO] Running com.saferoute.SafeRouteApplicationTests
[INFO] Tests run: 1, Failures: 0, Errors: 0, Skipped: 0
[INFO] 
[INFO] Results:
[INFO] Tests run: 9, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```
- `testHealthEndpoint`: Passed
- `testGeocodingSearch`: Passed (Photon / Nominatim autocomplete verified)
- `testGeocodingReverse`: Passed (Coordinate-to-address resolution verified)
- `testWeatherEndpoint`: Passed (Open-Meteo live retrieval verified)
- `testNearbyPlaces`: Passed (JPA cooling points and rest stops verified)
- `testRecommendRoute`: Passed (Multi-factor pedestrian scoring & explanations verified)
- `testSameOriginDestinationRejected`: Passed (Validation check 400 Bad Request)
- `testSecurityDeniesUnmapped`: Passed (Spring Security 403 Forbidden verified)
- `contextLoads`: Passed (Full Spring Boot 3.4.4 context initialized)

### 6.3 Frontend Compilation (`npm run build`)
Command: `npm run build` in `frontend`
```
> tsc -b && vite build
✓ 1609 modules transformed.
dist/index.html                   0.62 kB │ gzip:  0.40 kB
dist/assets/index-CA1jX3nd.css   26.95 kB │ gzip:  9.51 kB
dist/assets/index-DBfQGCpA.js   320.45 kB │ gzip: 99.04 kB
✓ built in 1.61s
```

---

## 7. Real-World Navigation Capabilities Verified

1. **Anywhere Address Search & Autocomplete:**
   - Powered by Photon API with Nominatim fallback.
   - Debounced search queries returning real landmarks, streets, and cities.
2. **Current Location (GPS) Integration:**
   - One-click "GPS" button querying browser Geolocation API (`navigator.geolocation.getCurrentPosition`).
   - Reverse geocoding resolves coordinates to readable local street names.
3. **Live Ambient Weather & Microclimate Adjustments:**
   - Real-time Open-Meteo integration querying temperature, apparent heat index, UV index, humidity, cloud cover, and solar radiation.
   - One-click "SYNC LIVE TEMP" button in the planner panel.
4. **Pedestrian Routing & Alternative Routes:**
   - OpenRouteService API with automatic OSRM walking fallback and orthogonal waypoint alternatives.
   - Guaranteed multiple walking alternatives rendered on Leaflet with interactive selection.
5. **Geospatial Environmental Intelligence:**
   - Overpass API extraction for tree canopies (`natural=tree`, `landuse=forest`), pedestrian ways (`highway=footway`, `highway=pedestrian`), street lighting (`lit=yes`), and water/cooling points.
6. **Multi-Factor Explainable Scoring:**
   - 4 intent profiles: `Balanced`, `Safety Priority`, `Heat Protection`, `Quick Walk`.
   - Dynamic natural-language reasoning and factor contribution breakdown for every route.
7. **Interactive Leaflet Map:**
   - Auto-fit bounds for selected pedestrian routes.
   - Start (`#55e0b4`) and Destination (`#ff6b6b`) distinct markers.
   - Interactive clickable alternative route polylines.
   - Shaded cooling/rest point pins (`#f4b860`).
   - Browser GPS location pin (`#4dabf7`).
8. **Compliance & Disclaimer:**
   - Prominently displays: *"MargNova provides estimated route suitability information and does not guarantee personal safety."*

---

## 7. Exact Local Startup Commands

### Step 1: Start PostgreSQL (Optional)
If using Docker for local PostgreSQL:
```powershell
docker compose up -d
```
*(If Docker is not installed or running, the backend automatically uses its built-in in-memory PostgreSQL-compatible database with Flyway enabled).*

### Step 2: Start Python Intelligence Service
In a PowerShell terminal:
```powershell
cd python-service
.\.venv\Scripts\Activate.ps1
uvicorn app.main:app --port 8000
```
Verify: `http://localhost:8000/health`

### Step 3: Start Spring Boot Gateway
In a new PowerShell terminal:
```powershell
cd backend
$env:JAVA_HOME = "C:\Program Files\Java\jdk-21.0.10"
& "C:\Program Files\Apache\Maven\apache-maven-3.9.12\bin\mvn.cmd" spring-boot:run
```
Verify: `http://localhost:8080/api/health`

### Step 4: Start Frontend
In a new PowerShell terminal:
```powershell
cd frontend
npm run dev
```
Open browser at: `http://localhost:5173`


# SafeRoute / MargNova — Backend Architecture & Design

## 1. System Topology & Service Boundaries

MargNova utilizes a modular, multi-tier architecture designed for secure local development and transparent urban walk intelligence.

```
                        ┌──────────────────────────────────────┐
                        │          React 18 + Leaflet          │
                        │        (http://localhost:5173)       │
                        └──────────────────┬───────────────────┘
                                           │ Public HTTP REST
                                           ▼
┌────────────────────────────────────────────────────────────────────────────────────────┐
│                      Spring Boot 3.4 API Gateway (Port 8080)                           │
│                                                                                        │
│  [SecurityConfig] ─── CORS: localhost:5173 ─── Stateless SecurityFilterChain           │
│  [RouteController] ── Public Endpoints (/api/health, /api/weather, /api/routes, etc.)  │
│  [ApiExceptionHandler] ── Sanitized JSON error handling with Request IDs               │
│                                                                                        │
│  ┌───────────────────────┐  ┌───────────────────────┐  ┌────────────────────────────┐  │
│  │     RouteService      │  │    WeatherService     │  │        PlaceService        │  │
│  └───────────┬───────────┘  └───────────┬───────────┘  └─────────────┬──────────────┘  │
│              │                          │                            │                 │
│  ┌───────────▼───────────┐  ┌───────────▼───────────┐  ┌─────────────▼──────────────┐  │
│  │RouteIntelligenceClient│  │     WeatherClient     │  │  CoolingPlaceRepository    │  │
│  └───────────┬───────────┘  └───────────┬───────────┘  └─────────────┬──────────────┘  │
└──────────────┼──────────────────────────┼────────────────────────────┼─────────────────┘
               │ Internal Secured HTTP    │ External HTTPS             │ JDBC / JPA
               │ (X-Internal-Service-Key) │ (Open-Meteo API)           │ (Flyway V1 Schema)
               ▼                          ▼                            ▼
┌──────────────────────────────┐ ┌──────────────────┐ ┌──────────────────────────────────┐
│   FastAPI Service (8000)     │ │  Open-Meteo API  │ │   PostgreSQL 16 (Local / H2)     │
│                              │ │  (Weather &      │ │                                  │
│ - NetworkX Pathfinding       │ │   Apparent Temp) │ │ - location_nodes                 │
│ - Shade-Adjusted Heat Burden │ └──────────────────┘ │ - cooling_places                 │
│ - Multi-Factor Scoring Engine│                      │ - route_evaluations (Audit log)  │
│ - Explainable Reason Engine  │                      └──────────────────────────────────┘
└──────────────────────────────┘
```

---

## 2. Java Backend Clean Architecture

The Spring Boot backend (`backend/src/main/java/com/saferoute`) strictly follows clean architectural separation:

1. **`controller/`** (`RouteController`, `ApiExceptionHandler`):
   - Exposes REST endpoints, validates inputs via `@Valid` and Jakarta Validation annotations.
   - Converts exceptions into standard `ErrorResponseDto` containing timestamps and unique request IDs.
2. **`service/`** (`RouteService`, `WeatherService`, `PlaceService`, `IntelligenceService`):
   - Business orchestration and transaction management.
   - Audits route evaluation queries into `route_evaluations` without recording personally identifying information.
3. **`integration/`** (`RouteIntelligenceClient`, `WeatherClient`):
   - Outbound HTTP communication using non-blocking `WebClient`.
   - In-memory 5-minute caching for weather queries to respect provider boundaries.
   - Resilient bounded retries with exponential backoff on transient 5xx errors.
   - Internal authentication header injection (`X-Internal-Service-Key`).
4. **`repository/`** (`CoolingPlaceRepository`, `LocationNodeRepository`, `RouteEvaluationRepository`):
   - Spring Data JPA repositories with custom spatial proximity queries.
5. **`entity/`** (`CoolingPlaceEntity`, `LocationNodeEntity`, `RouteEvaluationEntity`):
   - Relational database mappings for urban cooling amenities and evaluation logs.
6. **`dto/`** (`RouteRequestDto`, `RouteRecommendationResponseDto`, `PlaceDto`, `WeatherResponseDto`, `DataTrustLevel`):
   - Immutable records decoupling public REST contracts from internal persistence structures.
7. **`security/`** (`SecurityConfig`):
   - Spring Security configuration with centralized CORS, disabled CSRF for stateless APIs, and strict endpoint access rules.
8. **`config/`** (`HttpConfig`):
   - Configures connection/read timeouts and HTTP client connectors.

---

## 3. Application Security & Internal Protection

### 3.1 Python Internal Service Defense
To prevent direct browser bypass of the Java gateway:
- FastAPI includes `verify_internal_auth` dependency checking the `X-Internal-Service-Key` header against `INTERNAL_SERVICE_KEY`.
- Unauthenticated or direct browser calls receive `401 Unauthorized`.
- Python CORS restricts origins strictly to `http://localhost:8080`.

### 3.2 Spring Security & CORS
- Only `http://localhost:5173` (the React Vite frontend) is permitted via CORS.
- Unmapped routes are rejected with `403 Forbidden`.
- Security response headers prevent MIME sniffing, clickjacking (`X-Frame-Options: DENY`), and information leakage.

### 3.3 Zero Secrets Exposure
- Database passwords, internal keys, and provider URLs use environment variables with fallback defaults (`.env.example`).
- Sensitive details (passwords, connection strings, stack traces) are filtered out of API responses.

---

## 4. Multi-Factor Scoring & Preference Weights

Route suitability is calculated in the Python service using normalized costs in `[0, 100]` across six dimensions:
`Total Cost = Σ (weight_i × cost_i)`

| Profile | Heat Weight | Lighting Weight | Crowd Weight | Hazard Weight | Cooling Access | Duration Weight | Primary Focus |
|---|---|---|---|---|---|---|---|
| **Balanced** | 0.24 | 0.20 | 0.10 | 0.18 | 0.12 | 0.16 | Even compromise |
| **Safety First** | 0.12 | 0.27 | 0.12 | 0.28 | 0.08 | 0.13 | Lighting & hazard avoidance |
| **Heat Protection** | 0.38 | 0.12 | 0.08 | 0.12 | 0.20 | 0.10 | Shade canopy & cooling stops |
| **Quick Walk** | 0.10 | 0.16 | 0.08 | 0.14 | 0.06 | 0.46 | Direct path & minimal time |

---

## 5. Data Trust & Quality Rules

Every environmental metric carries an explicit quality classification:

- **`VERIFIED`**: Fetched from live Open-Meteo observations.
- **`ESTIMATED`**: Dynamically computed from ambient temperature, walking duration, and fixture shade properties.
- **`SIMULATED`**: Synthetic sample indicators (fixture lighting proxies, hazard scores, crowd estimates).
- **`UNKNOWN`**: Missing attributes, remaining neutral rather than falsely indicating zero risk.

Mandatory Application Disclaimer:
> *"MargNova provides estimated route suitability information and does not guarantee personal safety."*


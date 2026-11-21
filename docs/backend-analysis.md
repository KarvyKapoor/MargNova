# SafeRoute / MargNova — Backend Architecture & Repository Analysis

**Date:** November 2026  
**Role:** Senior Full-Stack Engineer, Software Architect, Application Security Engineer  
**Document Status:** Complete Pre-Implementation & Architectural Audit

---

## 1. Executive Summary

SafeRoute (MargNova) is an AI-assisted, safety-aware and heat-aware pedestrian navigation system. Unlike conventional navigation platforms that compute solely the shortest or quickest path, MargNova evaluates pedestrian route alternatives across multi-dimensional environmental and contextual dimensions:
- Ambient temperature and heat burden
- Tree canopy and shade exposure
- Pedestrian lighting and visibility proxies
- Crowd congestion estimates
- Incident and hazard indicators
- Proximity to resting and cooling points

This document analyzes the existing codebase, identifies functional gaps, defines API contracts and data-trust principles, and establishes the implementation plan to bring the backend to a fully functional, secure, hackathon-ready state running locally on `localhost`.

---

## 2. Existing Architecture & Repository Audit

### 2.1 Folder Structure
```
MargNova/
├── backend/                  # Java 21 Spring Boot REST API Gateway
│   ├── src/main/java/com/saferoute/
│   │   ├── config/HttpConfig.java
│   │   ├── controller/RouteController.java
│   │   ├── controller/ApiExceptionHandler.java
│   │   ├── service/IntelligenceService.java
│   │   ├── service/IntelligenceServiceException.java
│   │   └── SafeRouteApplication.java
│   ├── src/main/resources/application.yml
│   ├── src/test/java/com/saferoute/SafeRouteApplicationTests.java
│   └── pom.xml
├── python-service/           # FastAPI Route Intelligence Engine
│   ├── app/
│   │   ├── api/schemas.py
│   │   ├── explanations/reasons.py
│   │   ├── geospatial/graph.py
│   │   ├── heat/estimator.py
│   │   ├── scoring/engine.py
│   │   └── main.py
│   ├── tests/
│   │   ├── test_heat.py
│   │   └── test_scoring.py
│   ├── requirements.txt
│   └── .venv/
├── frontend/                 # React 18 + TypeScript + Vite + Leaflet
│   ├── src/
│   │   ├── components/MapView.tsx
│   │   ├── api.ts
│   │   ├── App.tsx
│   │   ├── main.tsx
│   │   ├── styles.css
│   │   └── types.ts
│   ├── package.json
│   ├── tsconfig.json
│   └── vite.config.ts
├── data/
│   ├── sample/pedestrian_graph.json
│   └── cooling-locations/README.md
├── docs/                     # Architectural and algorithm specifications
└── start-dev.ps1
```

### 2.2 Component Technologies & Versions
| Component | Technology | Version | Port |
|---|---|---|---|
| **Frontend** | React, TypeScript, Vite, React-Leaflet | React 18.3.1, Node v24.12, Vite 8.3 | `http://localhost:5173` |
| **Java Gateway** | Spring Boot, Java 21, Maven | Spring Boot 3.4.4, Java 21.0.10 | `http://localhost:8080` |
| **Python Service** | Python, FastAPI, NetworkX, Uvicorn | Python 3.13.14, FastAPI 0.162.5 | `http://localhost:8000` |
| **Database** | PostgreSQL / Flyway / Spring Data JPA | PostgreSQL 16 compatible / Flyway 10+ | `localhost:5432` |

---

## 3. Existing Implementation & Completed Modules

### What Is Already Working:
1. **Python Intelligence Service:**
   - NetworkX graph construction from `pedestrian_graph.json` (`app/geospatial/graph.py`).
   - Multiple route pathfinding using `nx.shortest_simple_paths`.
   - Shade-adjusted heat burden calculation (`app/heat/estimator.py`).
   - Multi-factor scoring across 4 profiles (`app/scoring/engine.py`).
   - Human-readable explanation generation (`app/explanations/reasons.py`).
   - Automated unit tests (`test_heat.py`, `test_scoring.py`) pass.

2. **Frontend UI:**
   - Interactive React dashboard with origin/destination dropdowns, temperature slider, and profile selector buttons.
   - Leaflet map rendering route polylines and demo markers.
   - Route comparison list and score tracks.
   - Detailed metric display (Heat burden, lighting, crowd, cooling access).

3. **Baseline Java Gateway:**
   - Basic Spring Boot app with `RouteController` and `IntelligenceService` using `RestClient`.
   - Global exception handling for validation errors and upstream connection failures.

---

## 4. Missing Backend Modules & Technical Issues

### 4.1 Identified Gaps:
1. **Missing Required Endpoints:**
   - `GET /api/weather` — Live weather integration with Open-Meteo API is absent.
   - `GET /api/places/nearby` — Missing endpoint to discover cooling points and rest areas.
   - `POST /api/routes/alternatives` — Distinct endpoint for querying all alternatives without forced recommendation.
   - `POST /api/routes/recommend` — Explicit recommendation endpoint with profile-weighted rankings and explanations.
2. **Database & Persistence Layer:**
   - No Spring Data JPA repositories or entities existed.
   - Flyway database migration scripts were absent.
   - PostgreSQL configuration and schema definitions were not yet integrated into `application.yml`.
3. **Application Security & Internal Service Protection:**
   - Spring Security was not configured.
   - Python FastAPI endpoints were directly open without internal service authentication (e.g. `X-Internal-Service-Key`).
   - Python allowed CORS from browser, exposing internal scoring directly.
   - Secrets and environment configuration lacked `.env.example` templates.
4. **Data Trust & Reliability Annotations:**
   - Responses did not explicitly label data quality states (`VERIFIED`, `ESTIMATED`, `SIMULATED`, `UNKNOWN`).
   - Missing data was not cleanly distinguished from low risk.
5. **Frontend Typings Bug:**
   - `frontend/src/api.ts` failed TypeScript compilation due to missing `vite-env.d.ts` reference for `import.meta.env`.

---

## 5. Existing API Contracts & Integration Mapping

### 5.1 Gateway Public APIs (`http://localhost:8080`)
| Method | Endpoint | Consumer | Purpose |
|---|---|---|---|
| `GET` | `/api/health` | Monitoring / Ops | Gateway, DB, Python and Weather status |
| `GET` | `/api/weather` | Frontend / Client | Live Open-Meteo weather for coordinates |
| `GET` | `/api/places/nearby` | Frontend / Map | Nearby cooling centers, drinking water, parks |
| `POST` | `/api/routes/alternatives` | Client / Integrator | Multiple pedestrian route alternatives |
| `POST` | `/api/routes/recommend` | Frontend / Client | Profile-ranked route recommendation & explanation |
| `GET` | `/api/locations` | Frontend (Legacy) | Demo graph nodes for origin/dest pickers |
| `POST` | `/api/routes` | Frontend (Legacy) | Full route evaluation preserving existing UI contract |

### 5.2 Python Internal APIs (`http://localhost:8000`) — Protected
| Method | Endpoint | Auth Required | Purpose |
|---|---|---|---|
| `GET` | `/health` | No | Python service health |
| `GET` | `/api/v1/locations` | Yes (`X-Internal-Service-Key`) | Internal node catalogue |
| `POST` | `/api/v1/routes` | Yes (`X-Internal-Service-Key`) | Multi-route scoring, heat estimation, explanations |

---

## 6. Security & Data Trust Strategy

1. **Spring Security Configuration:**
   - Centralized CORS configuration explicitly bound to `http://localhost:5173`.
   - Stateless session management.
   - Security headers (CSP, HSTS, X-Content-Type-Options, Frame-Options).
   - Public access to decision-support `/api/**` endpoints while preventing access to internal actuators or management endpoints.
2. **Internal Service Protection:**
   - Secret key exchange via `X-Internal-Service-Key` header between Java WebClient/RestClient and Python FastAPI.
   - Python FastAPI denies requests without this key.
   - Python CORS restricts browser origins.
3. **Database Security:**
   - PostgreSQL runs on localhost.
   - Schema managed via versioned Flyway migrations.
   - Entities never exposed directly in REST contracts; strictly mapped to DTOs.
   - No user movement history or personal identity stored.
4. **Data Quality States:**
   - `VERIFIED`: Live Open-Meteo weather readings.
   - `ESTIMATED`: Temperature-and-shade heat burden calculation.
   - `SIMULATED`: Illustrative lighting, hazard, crowd, and demo cooling points.
   - `UNKNOWN`: Missing parameters, explicitly flagged.
   - Mandatory disclaimer: *"MargNova provides estimated route suitability information and does not guarantee personal safety."*

---

## 7. Recommended Implementation Sequence

1. **Step 1: Frontend Fixes & Compatibility:** Ensure `vite-env.d.ts` is in place and `npm run build` succeeds cleanly.
2. **Step 2: Python Service Security & Protection:** Add `X-Internal-Service-Key` authentication dependency to FastAPI endpoints and tighten CORS.
3. **Step 3: Java Dependencies & Architecture:** Update `pom.xml` with Spring Security, Spring Data JPA, PostgreSQL, Flyway, H2 (test/fallback), and WebFlux.
4. **Step 4: Database Entities, Migrations & Repositories:** Create Flyway `V1__init_schema.sql`, JPA entities (`LocationNodeEntity`, `CoolingPlaceEntity`, `RouteEvaluationEntity`), and Spring Data repositories.
5. **Step 5: Weather Integration (Open-Meteo):** Implement `WeatherClient` with timeouts, in-memory caching, graceful offline fallback, and DTOs.
6. **Step 6: Route Intelligence Client & Service:** Implement `RouteIntelligenceClient` using WebClient/RestClient with internal authentication, timeout handling, retries, and data trust labeling.
7. **Step 7: REST Controllers & DTOs:** Implement `GET /api/weather`, `GET /api/places/nearby`, `POST /api/routes/alternatives`, `POST /api/routes/recommend`, preserving `/api/locations` and `/api/routes`.
8. **Step 8: Automated Testing:** Comprehensive JUnit 5 and Mockito tests covering all controllers, security, validation, error scenarios, and service mock integrations.
9. **Step 9: Documentation & Local Run Verification:** Deliver `.env.example`, `application.yml`, `docker-compose.yml`, architecture docs, and testing reports.


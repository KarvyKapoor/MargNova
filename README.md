# SafeRoute (MargNova) — Explainable, Heat-Aware Pedestrian Navigation

A full-stack urban walk intelligence prototype that compares pedestrian route alternatives using transparent, multi-dimensional environmental indicators: walking time, heat exposure, tree canopy / shade, pedestrian lighting, crowd activity, reported hazards, and access to cooling points.

> **Disclaimer:** *MargNova provides estimated route suitability information and decision support, not an emergency service or a guarantee of personal safety.*

---

## 1. System Architecture

- **Frontend (`http://localhost:5173`):** React 18 + TypeScript + Vite + Leaflet dashboard with live weather sync, profile selection, route comparison, and factor breakdown.
- **Java API Gateway (`http://localhost:8080`):** Spring Boot 3.4 + Java 21 REST API gateway with Spring Security, Flyway schema migrations, PostgreSQL/H2 persistence, and Open-Meteo live weather client.
- **Python Intelligence Engine (`http://localhost:8000`):** FastAPI internal service protected by `X-Internal-Service-Key`, performing NetworkX graph pathfinding, heat burden calculations, multi-factor scoring, and human-readable explanation generation.
- **Database (`localhost:5432` / In-Memory H2):** PostgreSQL 16 compatible schema managed by Flyway migrations (`location_nodes`, `cooling_places`, `route_evaluations`).

---

## 2. Differentiating Capabilities

1. **Multiple Alternative Routes:** Computes distinct walking paths with genuine geometric differences.
2. **Live Weather Integration (Open-Meteo):** Fetches real-time temperature, apparent temperature, humidity, wind, and conditions via backend adapter without exposing API keys.
3. **Route-Specific Heat Burden:** Distinguishes between ambient temperature and shade-adjusted route exposure.
4. **Four Personalized Route Profiles:**
   - **Balanced:** Harmonious compromise between duration, safety, and heat.
   - **Safety First:** Prioritizes street lighting and lower hazard proxies.
   - **Heat Protection:** Prioritizes tree canopy shade, lower heat burden, and cooling stops.
   - **Quick Walk:** Prioritizes direct distance and shortest walking time.
5. **Cooling & Rest Accessibility:** Surfaces nearby public cooling locations and hydration points from database.
6. **Data Trust Transparency:** Explicitly labels data quality as `VERIFIED`, `ESTIMATED`, `SIMULATED`, or `UNKNOWN`.
7. **Explainable Recommendations:** Articulates clear reasons why a route was selected.

---

## 3. Quick Start (Local Development)

### Prerequisites
- Java 21+
- Python 3.11+
- Node.js 20+

### Step 1: Start Python Intelligence Service
In a terminal:
```powershell
cd python-service
.\.venv\Scripts\Activate.ps1
uvicorn app.main:app --port 8000
```
- Health Check: `http://localhost:8000/health`
- Note: Internal endpoints require `X-Internal-Service-Key` header.

### Step 2: Start Spring Boot Gateway
In a second terminal:
```powershell
cd backend
$env:JAVA_HOME = "C:\Program Files\Java\jdk-21.0.10"
& "C:\Program Files\Apache\Maven\apache-maven-3.9.12\bin\mvn.cmd" spring-boot:run
```
- Gateway Health: `http://localhost:8080/api/health`

### Step 3: Start React Frontend
In a third terminal:
```powershell
cd frontend
npm run dev
```
- Open browser at `http://localhost:5173`.

---

## 4. API Endpoints

| Method | Endpoint | Description |
|---|---|---|
| `GET` | `/api/health` | Comprehensive gateway, DB, Python, and weather health |
| `GET` | `/api/weather` | Live Open-Meteo weather for coordinates (`lat`, `lng`) |
| `GET` | `/api/places/nearby` | Nearby cooling centers, drinking points, and parks |
| `POST` | `/api/routes/alternatives` | Multiple pedestrian route alternatives with metrics |
| `POST` | `/api/routes/recommend` | Optimal route recommendation based on profile |
| `GET` | `/api/locations` | Available map nodes (frontend compatibility) |
| `POST` | `/api/routes` | Route evaluation (frontend compatibility) |

Full contract details: [docs/api/api-contract.md](docs/api/api-contract.md).

---

## 5. Automated Tests

- **Java Spring Boot Tests:**
  ```powershell
  cd backend
  & "C:\Program Files\Apache\Maven\apache-maven-3.9.12\bin\mvn.cmd" test
  ```
- **Python Unit & Security Tests:**
  ```powershell
  cd python-service
  .\.venv\Scripts\Activate.ps1
  pytest
  ```
- **Frontend Typecheck & Build:**
  ```powershell
  cd frontend
  npm run build
  ```

---

## 6. Documentation Directory

- [docs/backend-analysis.md](docs/backend-analysis.md) — Pre-implementation audit and gap analysis.
- [docs/architecture/backend-architecture.md](docs/architecture/backend-architecture.md) — System design and security topology.
- [docs/api/api-contract.md](docs/api/api-contract.md) — API contract with sample requests and responses.
- [docs/local-testing-report.md](docs/local-testing-report.md) — Local testing and verification report.

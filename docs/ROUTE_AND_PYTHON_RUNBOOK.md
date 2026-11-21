# MargNova route and Python service runbook

This patch keeps the existing React frontend and its visual components unchanged. It fixes
the default pedestrian routing provider and improves the OSM environmental query used by
the Python intelligence service.

## What was wrong

The Java routing client defaulted to `https://router.project-osrm.org` and called
`/route/v1/walking/...`. The public OSRM demo is not a pedestrian profile service. The
routing profile is baked into the server's prepared graph, so changing the path segment
to `walking` does not turn a car router into a pedestrian router.

The fallback now uses the FOSSGIS hosted pedestrian graph:
`https://routing.openstreetmap.de/routed-foot`

The request path uses OSRM's `driving` API token because that is the route API endpoint
token expected by this hosted OSRM-compatible instance. The `routed-foot` prefix selects
the pedestrian graph. This public service is for light demo/testing use, not production SLA.

The Python environmental query also used an Overpass output clause that was not appropriate
for retrieving way centers. It now requests `out center;`, so way features can contribute
center coordinates, and it allows a little more time before falling back to documented
geometric estimates.

## Windows PowerShell setup

Use three separate terminals. Run commands from the project root.

### Terminal 1: Python intelligence service

```powershell
cd python-service
python --version
python -m venv .venv
.\.venv\Scripts\Activate.ps1
python -m pip install --upgrade pip
pip install -r requirements.txt
python -m pytest -q
uvicorn app.main:app --host 127.0.0.1 --port 8000 --reload
```

If activation is blocked in PowerShell:

```powershell
Set-ExecutionPolicy -Scope Process -ExecutionPolicy Bypass
.\.venv\Scripts\Activate.ps1
```

If `python` is not recognized, install Python 3.11 or 3.12 and select
"Add python.exe to PATH" during installation. Then close and reopen PowerShell.
Do not reuse a `.venv` created on another computer.

Check:
`http://127.0.0.1:8000/health`

### Terminal 2: Java API gateway

Install Java 21 and Maven 3.9+. Verify:

```powershell
java -version
mvn -version
```

Then:

```powershell
cd backend
mvn clean spring-boot:run
```

The default local profile uses in-memory H2. PostgreSQL and Docker are not required for
the local demo.

Check:
`http://localhost:8080/api/health`

The Python and Java services must use the same internal key. The source defaults match:
`dev-internal-secret-key-saferoute-2026`

For a local demo, do not change only one side of this key.

### Terminal 3: React frontend

```powershell
cd frontend
npm ci
npm run build
npm run dev
```

Open:
`http://localhost:5173`

The frontend calls `http://localhost:8080/api` by default. If your Java gateway uses a
different address, create `frontend/.env.local`:

```env
VITE_API_URL=http://localhost:8080/api
```

Restart Vite after changing the environment file.

## Test the actual route pipeline

Python unit tests validate the scoring and evaluation logic. They do not test the external
pedestrian router. The Java controller tests mock parts of the route flow. Therefore, use
this real request after all three services are running.

```powershell
$body = @{
  origin = "Bhopal Junction"
  destination = "Central Library Bhopal"
  profile = "balanced"
  temperature_c = 36
  originLat = 23.2599
  originLng = 77.4126
  destLat = 23.2642
  destLng = 77.4155
} | ConvertTo-Json

Invoke-RestMethod -Uri "http://localhost:8080/api/routes" `
  -Method Post -ContentType "application/json" -Body $body
```

A successful response must contain:
- `routes` with at least one item
- each route has a `coordinates` array with at least two points
- coordinates are Leaflet ordered `[latitude, longitude]`
- `metrics.distance_m` and `metrics.duration_min` are positive numbers

## Diagnose failures by HTTP status

- `400`: inspect the response `message`. The address may not geocode, coordinates may be invalid,
  or the pedestrian router may have no connected walking path.
- `401` from Python: Java and Python have different `INTERNAL_SERVICE_KEY` values.
- `503` from Java: Python may not be running at `http://localhost:8000`, or the route provider
  connection may have failed earlier in the chain.
- `500`: inspect the Java terminal stack trace and Python terminal log.
- `200` with `routes: []`: inspect the routing provider response and `OSRM_URL`.
- `200` with route coordinates but a blank map: inspect browser DevTools Console and Network.
  Confirm that `coordinates` are `[lat, lng]`, not `[lng, lat]`, and that the map container has
  a non-zero height.

## API health checks

Python:
`http://localhost:8000/health`

Java:
`http://localhost:8080/api/health`

The Java health endpoint reports database, Python intelligence, and weather status. A weather
fallback can still allow route evaluation, but it should be shown as simulated/degraded data.

## Important demo accuracy note

Route geometry and distance come from the pedestrian routing provider when it responds.
Weather comes from Open-Meteo when available. Shade, lighting, crowd, hazard, visibility,
and cooling-access scores are estimates or OSM-derived proxies. They are not verified
real-time personal-safety measurements and must not be presented as safety guarantees.

# API endpoints
## Spring Boot gateway (port 8080)
- `GET /api/health` — gateway health.
- `GET /api/locations` — available demo nodes.
- `POST /api/routes` — forwards validated route request to FastAPI.
## Python intelligence (port 8000)
- `GET /health`
- `GET /api/v1/locations`
- `POST /api/v1/routes` body: `{ "origin":"station", "destination":"library", "profile":"balanced", "temperature_c":36, "heat_sensitivity":1.0, "walking_speed_kmh":4.5 }`.
Interactive OpenAPI docs at `/docs`.

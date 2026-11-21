from pydantic import BaseModel, Field
from typing import Literal, Optional, List, Dict, Any

class RouteRequest(BaseModel):
    origin: str = Field(min_length=1)
    destination: str = Field(min_length=1)
    profile: Literal["balanced", "safety", "heat", "quick"] = "balanced"
    temperature_c: float = Field(default=36, ge=-10, le=60)
    heat_sensitivity: float = Field(default=1.0, ge=0.5, le=2.0)
    walking_speed_kmh: float = Field(default=4.5, ge=2.0, le=7.0)

class RouteResponse(BaseModel):
    origin: str
    destination: str
    profile: str
    temperature_c: float
    routes: list[dict]
    notice: str
    disclaimer: Optional[str] = "MargNova provides estimated route suitability information and does not guarantee personal safety."

class RealRouteItem(BaseModel):
    id: str
    name: str
    coordinates: List[List[float]]  # [[lat, lng], ...]
    distance_m: float
    duration_min: float
    steps: Optional[List[Dict[str, Any]]] = None

class WeatherContext(BaseModel):
    temperature_c: float = 32.0
    apparent_temperature_c: Optional[float] = None
    relative_humidity_pct: Optional[float] = 50.0
    wind_speed_kmh: Optional[float] = 5.0
    weather_code: Optional[int] = 0
    condition: Optional[str] = "Clear sky"
    uv_index: Optional[float] = 3.5
    cloud_cover_pct: Optional[float] = 20.0
    solar_radiation: Optional[float] = 250.0

class EvaluateRealRoutesRequest(BaseModel):
    origin_name: str
    destination_name: str
    origin_coords: List[float] = [23.2599, 77.4126]
    destination_coords: List[float] = [23.2630, 77.4134]
    routes: List[RealRouteItem]
    weather: WeatherContext
    profile: Literal["balanced", "safety", "heat", "quick"] = "balanced"
    heat_sensitivity: float = Field(default=1.0, ge=0.5, le=2.0)
    walking_speed_kmh: float = Field(default=4.5, ge=2.0, le=7.0)

class EvaluateRealRoutesResponse(BaseModel):
    origin: str
    destination: str
    profile: str
    temperature_c: float
    weather: WeatherContext
    routes: List[Dict[str, Any]]
    recommended_route: Optional[Dict[str, Any]] = None
    notice: str
    disclaimer: str

from typing import Dict, Any, List

def estimate_heat_cost(nodes: List[Dict[str, Any]], temperature_c: float, sensitivity: float = 1.0) -> float:
    """0-100 relative heat burden proxy for demo graph nodes."""
    shade = sum(float(n.get("shade", 0.25)) for n in nodes) / max(len(nodes), 1)
    thermal = max(0.0, min(100.0, (temperature_c - 20.0) * 3.0))
    exposure = thermal * (1.0 - shade) * sensitivity
    return round(max(0.0, min(100.0, exposure)), 1)

def estimate_real_heat_burden(
    distance_m: float,
    duration_min: float,
    weather: Dict[str, Any],
    shade_fraction: float = 0.25,
    sensitivity: float = 1.0
) -> Dict[str, Any]:
    """
    Computes route-specific heat burden using ambient temperature, feels-like/apparent temperature,
    solar radiation, UV index, cloud cover, and shade canopy coverage.
    Safely handles missing or null values in weather dictionary.
    """
    # Safe value extractions avoiding TypeError on None values
    temp_val = weather.get("temperature_c")
    temp_c = float(temp_val if temp_val is not None else 32.0)

    app_val = weather.get("apparent_temperature_c")
    apparent_temp_c = float(app_val if app_val is not None else temp_c)

    uv_val = weather.get("uv_index")
    uv_index = float(uv_val if uv_val is not None else 3.5)

    cloud_val = weather.get("cloud_cover_pct")
    cloud_cover = float(cloud_val if cloud_val is not None else 20.0)

    # Base thermal index from effective apparent temperature
    eff_temp = max(temp_c, apparent_temp_c)
    if eff_temp <= 20.0:
        thermal_base = 5.0
    else:
        thermal_base = min(100.0, (eff_temp - 20.0) * 3.2)

    # Solar radiation factor (UV index and cloud cover modulation)
    uv_factor = 1.0 + min(0.40, (uv_index / 12.0) * 0.40)
    cloud_reduction = (cloud_cover / 100.0) * 0.20
    solar_multiplier = max(0.75, uv_factor * (1.0 - cloud_reduction))

    # Shade shielding factor: Shade reduces radiant heat exposure
    safe_shade = max(0.0, min(1.0, float(shade_fraction if shade_fraction is not None else 0.25)))
    exposed_ratio = max(0.10, min(1.0, 1.0 - safe_shade))

    # Duration fatigue factor: longer walks in heat compound thermal stress
    safe_duration = max(0.0, float(duration_min if duration_min is not None else 5.0))
    duration_factor = 1.0 + min(0.35, max(0.0, (safe_duration - 10.0) * 0.02))

    safe_sens = max(0.5, min(2.0, float(sensitivity if sensitivity is not None else 1.0)))
    raw_heat_score = thermal_base * solar_multiplier * exposed_ratio * safe_sens * duration_factor
    heat_score = round(max(5.0, min(100.0, raw_heat_score)), 1)

    if heat_score < 28:
        risk_class = "Low"
    elif heat_score < 52:
        risk_class = "Moderate"
    elif heat_score < 75:
        risk_class = "High"
    else:
        risk_class = "Extreme"

    safe_distance = max(0.0, float(distance_m if distance_m is not None else 0.0))
    exposed_distance_m = round(safe_distance * exposed_ratio)
    exposed_duration_min = round(safe_duration * exposed_ratio, 1)

    return {
        "heat_score": heat_score,
        "heat_risk": risk_class,
        "exposed_distance_m": exposed_distance_m,
        "exposed_duration_min": exposed_duration_min,
        "shade_coverage_pct": round(safe_shade * 100),
        "apparent_temp_c": round(apparent_temp_c, 1),
        "uv_index": uv_index,
        "data_trust": "ESTIMATED"
    }

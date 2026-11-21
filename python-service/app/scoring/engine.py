PROFILES = {
    "balanced": {"heat": 0.24, "lighting": 0.20, "crowd": 0.10, "hazard": 0.18, "cooling_access": 0.12, "duration": 0.16},
    "safety": {"heat": 0.12, "lighting": 0.27, "crowd": 0.12, "hazard": 0.28, "cooling_access": 0.08, "duration": 0.13},
    "heat": {"heat": 0.38, "lighting": 0.12, "crowd": 0.08, "hazard": 0.12, "cooling_access": 0.20, "duration": 0.10},
    "quick": {"heat": 0.10, "lighting": 0.16, "crowd": 0.08, "hazard": 0.14, "cooling_access": 0.06, "duration": 0.46}
}

def score_route(m, profile="balanced"):
    """Evaluates route cost using normalized weights. Lower total cost is preferred match."""
    weights = PROFILES.get(profile, PROFILES["balanced"])
    costs = {
        "heat": m["heat"],
        "lighting": m["lighting"] * 100,
        "crowd": m["crowd"] * 100,
        "hazard": m["hazard"] * 100,
        "cooling_access": m["cooling_access"],
        "duration": min(100.0, m["duration_min"] * 3.0)
    }
    breakdown = {k: round(costs[k] * weights[k], 1) for k in weights}
    total_cost = round(sum(breakdown.values()), 1)
    suitability = round(max(0.0, min(100.0, 100.0 - total_cost)), 1)
    return {"total": total_cost, "suitability_score": suitability, "breakdown": breakdown}

def score_real_route(metrics, profile="balanced"):
    """
    Evaluates real-world pedestrian routes consuming actual API environmental features.
    """
    weights = PROFILES.get(profile, PROFILES["balanced"])
    
    # Normalized penalty costs (0 to 100, where 0 is best, 100 is worst)
    duration_cost = min(100.0, max(5.0, metrics["duration_min"] * 3.2))
    heat_cost = metrics.get("heat", 30.0)
    lighting_cost = metrics.get("lighting", 0.35) * 100.0
    crowd_cost = metrics.get("crowd", 0.30) * 100.0
    hazard_cost = metrics.get("hazard", 0.15) * 100.0
    cooling_cost = metrics.get("cooling_access", 55.0)

    costs = {
        "heat": heat_cost,
        "lighting": lighting_cost,
        "crowd": crowd_cost,
        "hazard": hazard_cost,
        "cooling_access": cooling_cost,
        "duration": duration_cost
    }

    breakdown = {k: round(costs[k] * weights[k], 1) for k in weights}
    total_cost = round(sum(breakdown.values()), 1)
    suitability = round(max(5.0, min(98.0, 100.0 - total_cost)), 1)
    return {"total": total_cost, "suitability_score": suitability, "breakdown": breakdown}

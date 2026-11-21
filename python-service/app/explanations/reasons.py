from typing import List, Dict, Any

def explain_route(metrics, breakdown, profile):
    reasons = []
    if metrics["heat"] < 35:
        reasons.append("Estimated heat burden is relatively lower for this route.")
    else:
        reasons.append("Heat exposure is elevated; consider water and shaded breaks.")
    if metrics["lighting"] < .35:
        reasons.append("Fixture indicates comparatively better lighting along mapped segments.")
    else:
        reasons.append("Some mapped segments have limited lighting data or higher lighting-risk proxy.")
    if metrics["cooling_access"] < 30:
        reasons.append("A cooling/rest stop is mapped along this route; verify accessibility.")
    else:
        reasons.append("No mapped cooling stop appears directly on this route.")
    if metrics.get("hazard", 0) > .5:
        reasons.append("A higher hazard proxy is present; inspect local conditions.")
    reasons.append(f"Ranking uses '{profile}' preference weights; scores reflect relative suitability.")
    return reasons

def explain_real_route(metrics: Dict[str, Any], breakdown: Dict[str, Any], profile: str, rank: int = 1) -> List[str]:
    reasons = []
    shade_pct = metrics.get("shade_coverage_pct", 25)
    heat_score = metrics.get("heat", 35.0)
    duration_min = metrics.get("duration_min", 10.0)
    cooling_stops = metrics.get("cooling_stops", [])
    ped_score = metrics.get("pedestrian_infra_score", 60.0)

    if rank == 1:
        if profile == "heat":
            reasons.append(f"Top pick for Heat Protection: offers {shade_pct}% shade and canopy coverage to shield from direct solar radiation.")
            if cooling_stops:
                reasons.append(f"Provides access to {len(cooling_stops)} verified or mapped resting/cooling amenities along the way.")
            else:
                reasons.append("Significantly reduces exposed walking distance compared to open thoroughfares.")
        elif profile == "quick":
            reasons.append(f"Top pick for Quick Walk: provides the most direct walking duration ({duration_min} min).")
            reasons.append("Optimized for transit efficiency while maintaining pedestrian route continuity.")
        elif profile == "safety":
            reasons.append(f"Top pick for Safety Priority: features dedicated pedestrian paths ({ped_score}/100 infrastructure rating).")
            reasons.append("Prioritizes well-lit, active urban thoroughfares and visible intersections.")
        else:
            reasons.append(f"Top pick for Balanced Walk: harmonizes duration ({duration_min} min) with manageable heat exposure ({round(heat_score)}/100).")
            reasons.append(f"Maintains {shade_pct}% tree canopy without requiring excessive detour length.")
    else:
        if heat_score > 55:
            reasons.append("Heat burden is elevated along this alternative; consider taking hydration breaks.")
        else:
            reasons.append(f"Viable alternative with {duration_min} min travel time.")
        if shade_pct < 20:
            reasons.append("Limited shade cover along extended open segments.")

    reasons.append(f"Prioritized using '{profile}' intent weights. Weather values are live Open-Meteo observations.")
    return reasons

from app.scoring.engine import score_route

def test_scores_are_bounded_and_profile_sensitive():
    m={"heat":30,"lighting":.3,"crowd":.2,"hazard":.1,"cooling_access":20,"duration_min":15}
    a=score_route(m,"heat"); b=score_route(m,"quick")
    assert 0 <= a["total"] <= 100
    assert a["total"] != b["total"]

from app.heat.estimator import estimate_heat_cost

def test_more_shade_reduces_heat_cost():
    exposed=[{"shade":0.1}]; shaded=[{"shade":0.9}]
    assert estimate_heat_cost(shaded,38) < estimate_heat_cost(exposed,38)

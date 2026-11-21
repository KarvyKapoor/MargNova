# Multi-factor route scoring
For each route, estimate normalized costs in [0,100]: heat, lighting risk, crowd congestion, hazard proxy, cooling access penalty, and duration. Compute `C(route) = Σ weight_i × cost_i`. Profile weights sum to 1. Lower is preferred. The engine ranks candidate paths and returns each weighted contribution.

Weights (heat, lighting, crowd, hazard, cooling access, duration): balanced (.24,.20,.10,.18,.12,.16); safety (.12,.27,.12,.28,.08,.13); heat (.38,.12,.08,.12,.20,.10); quick (.10,.16,.08,.14,.06,.46).

All indicator values in the bundled fixture are synthetic. This score is not a crime probability, risk certification, or guarantee. Unknown values should be represented explicitly and handled neutrally with confidence penalties in a real system.

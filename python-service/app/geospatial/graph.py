import json
from pathlib import Path
import networkx as nx

DATA = Path(__file__).resolve().parents[3] / "data" / "sample" / "pedestrian_graph.json"

def build_graph():
    raw=json.loads(DATA.read_text(encoding="utf-8"))
    g=nx.Graph()
    for n in raw["nodes"]: g.add_node(n["id"], **{k:v for k,v in n.items() if k!="id"})
    for e in raw["edges"]: g.add_edge(e["from"],e["to"],**{k:v for k,v in e.items() if k not in ("from","to")})
    return g

def candidate_routes(graph, origin, destination, limit=4):
    try:
        paths=list(nx.shortest_simple_paths(graph, origin, destination, weight="distance_m"))
        return paths[:limit]
    except (nx.NetworkXNoPath, nx.NodeNotFound):
        return []

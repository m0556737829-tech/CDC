#!/usr/bin/env python3
"""Build a compact local search and routing database from an OSM PBF extract."""
import math
import sqlite3
import sys
import osmium

PBF, OUT = sys.argv[1], sys.argv[2]
db = sqlite3.connect(OUT)
db.execute("PRAGMA journal_mode=OFF")
db.execute("PRAGMA synchronous=OFF")
db.execute("PRAGMA temp_store=MEMORY")
db.executescript("""
DROP TABLE IF EXISTS places;
DROP TABLE IF EXISTS roads;
DROP TABLE IF EXISTS road_nodes;
DROP TABLE IF EXISTS places_fts;
CREATE TABLE places(id INTEGER PRIMARY KEY, name TEXT NOT NULL, aliases TEXT, lat REAL NOT NULL, lon REAL NOT NULL, kind TEXT);
CREATE VIRTUAL TABLE places_fts USING fts4(name, aliases, kind, tokenize=unicode61);
CREATE TABLE road_nodes(id INTEGER PRIMARY KEY, lat REAL NOT NULL, lon REAL NOT NULL);
CREATE TABLE roads(from_id INTEGER NOT NULL, to_id INTEGER NOT NULL, distance_m REAL NOT NULL, car INTEGER NOT NULL, bike INTEGER NOT NULL, foot INTEGER NOT NULL);
""")
place_rows, node_rows, edge_rows = [], [], []
counts = {"places": 0, "nodes": 0, "edges": 0}

def tags_to_dict(tags):
    return {t.k: t.v for t in tags}

class Extract(osmium.SimpleHandler):
    def node(self, n):
        if not n.location.valid():
            return
        t = tags_to_dict(n.tags)
        names = [t.get(k, "") for k in ("name:he", "name", "name:en", "official_name", "alt_name")]
        names = [x.strip() for x in names if x and x.strip()]
        if names and (t.get("place") or t.get("amenity") or t.get("shop") or t.get("tourism") or t.get("leisure") or t.get("railway") or t.get("public_transport") or t.get("highway") == "bus_stop"):
            name = names[0]
            aliases = " ".join(dict.fromkeys(names))
            kind = t.get("place") or t.get("amenity") or t.get("shop") or t.get("tourism") or t.get("leisure") or t.get("railway") or "point"
            place_rows.append((name, aliases, float(n.location.lat), float(n.location.lon), kind))
            if len(place_rows) >= 5000:
                flush_places()
    def way(self, w):
        t = tags_to_dict(w.tags)
        highway = t.get("highway")
        coords = []
        for nd in w.nodes:
            if nd.location.valid():
                coords.append((nd.ref, float(nd.location.lat), float(nd.location.lon)))
        if not coords:
            return
        if t.get("name") or t.get("name:he") or t.get("name:en"):
            names = [t.get(k, "") for k in ("name:he", "name", "name:en", "official_name", "alt_name")]
            names = [x.strip() for x in names if x and x.strip()]
            if names:
                lat = sum(p[1] for p in coords) / len(coords)
                lon = sum(p[2] for p in coords) / len(coords)
                place_rows.append((names[0], " ".join(dict.fromkeys(names)), lat, lon, highway or "road"))
                if len(place_rows) >= 5000:
                    flush_places()
        if not highway:
            return
        excluded_car = {"footway","path","pedestrian","steps","cycleway","bridleway","corridor","platform"}
        excluded_bike = {"motorway","motorway_link","steps"}
        excluded_foot = {"motorway","motorway_link"}
        access = t.get("access", "")
        car = int(highway not in excluded_car and access not in {"no","private"} and t.get("motor_vehicle") not in {"no","private"} and t.get("vehicle") not in {"no","private"})
        bike = int(highway not in excluded_bike and access not in {"no","private"} and t.get("bicycle") not in {"no","private"})
        foot = int(highway not in excluded_foot and access not in {"no","private"} and t.get("foot") not in {"no","private"})
        if not (car or bike or foot):
            return
        oneway = t.get("oneway", "").lower() in {"yes","1","true"}
        reverse = t.get("oneway", "").lower() == "-1"
        for ref, lat, lon in coords:
            node_rows.append((ref, lat, lon))
        for a, b in zip(coords, coords[1:]):
            dist = haversine(a[1], a[2], b[1], b[2])
            if dist <= 0:
                continue
            if not reverse:
                edge_rows.append((a[0], b[0], dist, car, bike, foot))
            if not oneway:
                edge_rows.append((b[0], a[0], dist, car, bike, foot))
            if len(edge_rows) >= 20000:
                flush_graph()

def haversine(lat1, lon1, lat2, lon2):
    r = 6371000.0
    p1, p2 = math.radians(lat1), math.radians(lat2)
    dp, dl = math.radians(lat2-lat1), math.radians(lon2-lon1)
    a = math.sin(dp/2)**2 + math.cos(p1)*math.cos(p2)*math.sin(dl/2)**2
    return 2*r*math.asin(math.sqrt(a))

def flush_places():
    if not place_rows:
        return
    db.executemany("INSERT INTO places(name,aliases,lat,lon,kind) VALUES(?,?,?,?,?)", place_rows)
    start = db.execute("SELECT last_insert_rowid()").fetchone()[0] - len(place_rows) + 1
    # Re-read inserted rows to preserve rowid alignment for the FTS table.
    rows = db.execute("SELECT id,name,aliases,kind FROM places WHERE id>=? ORDER BY id", (start,)).fetchall()
    db.executemany("INSERT INTO places_fts(rowid,name,aliases,kind) VALUES(?,?,?,?)", rows)
    counts["places"] += len(place_rows)
    place_rows.clear()

def flush_graph():
    if node_rows:
        db.executemany("INSERT OR IGNORE INTO road_nodes(id,lat,lon) VALUES(?,?,?)", node_rows)
        counts["nodes"] += len(node_rows)
        node_rows.clear()
    if edge_rows:
        db.executemany("INSERT INTO roads(from_id,to_id,distance_m,car,bike,foot) VALUES(?,?,?,?,?,?)", edge_rows)
        counts["edges"] += len(edge_rows)
        edge_rows.clear()
    db.commit()

handler = Extract()
handler.apply_file(PBF, locations=True, idx="flex_mem")
flush_places()
flush_graph()
db.executescript("""
CREATE INDEX idx_roads_from ON roads(from_id);
CREATE INDEX idx_roads_to ON roads(to_id);
CREATE INDEX idx_road_nodes_lat_lon ON road_nodes(lat,lon);
CREATE INDEX idx_places_name ON places(name);
ANALYZE;
""")
db.commit()
print("Built local database:", counts)
db.close()

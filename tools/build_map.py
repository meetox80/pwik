import struct
import sys
from pathlib import Path

import osmium

BBOX = (22.53, 49.93, 22.83, 50.18)
Q = 1e-6

ROAD_CLASS = {
    "motorway": 0, "motorway_link": 0, "trunk": 0, "trunk_link": 0,
    "primary": 1, "primary_link": 1,
    "secondary": 2, "secondary_link": 2,
    "tertiary": 3, "tertiary_link": 3,
    "unclassified": 4, "residential": 4, "road": 4,
    "living_street": 5,
    "service": 6,
    "pedestrian": 7,
    "footway": 8, "path": 8, "steps": 8,
    "cycleway": 9,
    "track": 10,
}
WALKABLE = {1, 2, 3, 4, 5, 6, 7, 8, 9, 10}
DRIVABLE = {0, 1, 2, 3, 4, 5, 6}

RESIDENTIAL, INDUSTRIAL, GREEN, FOREST, CEMETERY, PITCH, WATER, WETLAND, BUILDING, WATERWAY, RAIL, ROAD, PLACE = range(13)
LAYER_ORDER = [RESIDENTIAL, INDUSTRIAL, GREEN, FOREST, CEMETERY, PITCH, WETLAND, WATER, BUILDING, WATERWAY, RAIL, ROAD, PLACE]


def area_kind(tags):
    if "building" in tags and tags.get("building") != "no":
        return BUILDING, 0
    landuse = tags.get("landuse")
    natural = tags.get("natural")
    leisure = tags.get("leisure")
    if natural == "water" or tags.get("waterway") in ("riverbank", "dock") or landuse in ("reservoir", "basin"):
        return WATER, 0
    if natural == "wetland":
        return WETLAND, 0
    if landuse == "forest" or natural in ("wood", "scrub"):
        return FOREST, 0
    if landuse == "cemetery" or tags.get("amenity") == "grave_yard":
        return CEMETERY, 0
    if leisure in ("pitch", "sports_centre", "playground", "track", "stadium"):
        return PITCH, 0
    if leisure in ("park", "garden", "nature_reserve") or landuse in (
            "grass", "meadow", "village_green", "recreation_ground", "allotments", "orchard") or natural == "grassland":
        return GREEN, 1 if leisure in ("park", "garden") else 0
    if landuse == "residential":
        return RESIDENTIAL, 0
    if landuse in ("industrial", "commercial", "retail", "railway", "garages", "construction"):
        return INDUSTRIAL, 0
    return None


def inside(pts):
    minx, miny, maxx, maxy = BBOX
    return any(minx <= x <= maxx and miny <= y <= maxy for x, y in pts)


def quant(pts):
    out = []
    for x, y in pts:
        q = (round(x / Q), round(y / Q))
        if not out or out[-1] != q:
            out.append(q)
    return out


class Collector:
    def __init__(self):
        self.features = {k: [] for k in LAYER_ORDER}
        self.names = {}

    def name(self, n):
        if not n:
            return 0
        if n not in self.names:
            self.names[n] = len(self.names) + 1
        return self.names[n]

    def add(self, layer, cls, flags, name, rings):
        rings = [quant(r) for r in rings]
        rings = [r for r in rings if len(r) >= (1 if layer == PLACE else 2)]
        if rings:
            self.features[layer].append((cls, flags, self.name(name), rings))


def collect(path):
    c = Collector()
    fp = (osmium.FileProcessor(path)
          .with_locations()
          .with_areas()
          .with_filter(osmium.filter.KeyFilter(
              "building", "landuse", "natural", "leisure", "waterway", "railway",
              "highway", "place", "amenity")))
    for obj in fp:
        tags = obj.tags
        if obj.is_area():
            kind = area_kind(tags)
            if not kind:
                continue
            rings = []
            for outer in obj.outer_rings():
                o = [(n.lon, n.lat) for n in outer]
                if not inside(o):
                    continue
                rings.append(o)
                for inner in obj.inner_rings(outer):
                    rings.append([(n.lon, n.lat) for n in inner])
            if rings:
                c.add(kind[0], kind[1], 0, None, rings)
        elif obj.is_way():
            try:
                pts = [(n.lon, n.lat) for n in obj.nodes]
            except osmium.InvalidLocationError:
                continue
            if len(pts) < 2 or not inside(pts):
                continue
            hw = tags.get("highway")
            if hw in ROAD_CLASS and tags.get("area") != "yes":
                cls = ROAD_CLASS[hw]
                if tags.get("access") in ("private", "no") and cls >= 6:
                    continue
                flags = 0
                if cls in WALKABLE and tags.get("foot") != "no":
                    flags |= 1
                if cls in DRIVABLE:
                    flags |= 2
                if tags.get("bridge") in ("yes", "viaduct"):
                    flags |= 4
                if tags.get("tunnel") == "yes":
                    flags |= 8
                oneway = tags.get("oneway")
                if oneway in ("yes", "1", "true") or (tags.get("junction") in ("roundabout", "circular") and oneway != "no"):
                    flags |= 16
                elif oneway == "-1":
                    flags |= 32
                c.add(ROAD, cls, flags, tags.get("name"), [pts])
                continue
            ww = tags.get("waterway")
            if ww in ("river", "canal", "stream"):
                c.add(WATERWAY, {"river": 0, "canal": 1, "stream": 2}[ww], 0, tags.get("name"), [pts])
                continue
            rw = tags.get("railway")
            if rw in ("rail", "light_rail", "narrow_gauge") and tags.get("service") not in ("yard", "siding", "spur"):
                c.add(RAIL, 0 if rw == "rail" else 1, 0, None, [pts])
        elif obj.is_node():
            place = tags.get("place")
            name = tags.get("name")
            if place and name:
                pt = (obj.location.lon, obj.location.lat)
                if not inside([pt]):
                    continue
                cls = {"city": 0, "town": 0, "village": 1, "suburb": 1, "quarter": 2,
                       "neighbourhood": 2, "hamlet": 2}.get(place)
                if cls is not None:
                    c.add(PLACE, cls, 0, name, [[pt]])
    return c


def zigzag(n):
    return (n << 1) ^ (n >> 63)


def varint(n):
    out = bytearray()
    while True:
        b = n & 0x7F
        n >>= 7
        if n:
            out.append(b | 0x80)
        else:
            out.append(b)
            return bytes(out)


def write(c, path):
    minx, miny, maxx, maxy = BBOX
    ox, oy = round(minx / Q), round(miny / Q)
    names = sorted(c.names.items(), key=lambda kv: kv[1])
    body = bytearray()
    body += struct.pack("<H", len(names))
    for n, _ in names:
        b = n.encode("utf-8")[:255]
        body += bytes([len(b)]) + b
    body.append(len(LAYER_ORDER))
    for layer in LAYER_ORDER:
        feats = c.features[layer]
        if layer == ROAD:
            feats.sort(key=lambda f: -f[0])
        body.append(layer)
        body += varint(len(feats))
        for cls, flags, name, rings in feats:
            body.append(cls)
            body.append(flags)
            body += varint(name)
            body += varint(len(rings))
            for ring in rings:
                body += varint(len(ring))
                px, py = ox, oy
                for x, y in ring:
                    body += varint(zigzag(x - px))
                    body += varint(zigzag(y - py))
                    px, py = x, y
    head = b"PWMP" + struct.pack("<Bdddd", 1, minx, miny, maxx, maxy)
    Path(path).write_bytes(head + bytes(body))


def main():
    c = collect(sys.argv[1])
    write(c, sys.argv[2])
    labels = {RESIDENTIAL: "residential", INDUSTRIAL: "industrial", GREEN: "green", FOREST: "forest",
              CEMETERY: "cemetery", PITCH: "pitch", WATER: "water", WETLAND: "wetland",
              BUILDING: "building", WATERWAY: "waterway", RAIL: "rail", ROAD: "road", PLACE: "place"}
    for k in LAYER_ORDER:
        print(f"  {labels[k]:12s} {len(c.features[k]):7d}")
    print(f"names={len(c.names)} size={Path(sys.argv[2]).stat().st_size / 1024:.0f} KB")


main()

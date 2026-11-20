import json
import math
import re
import sys
import unicodedata
from pathlib import Path

CENTER = (22.6786, 50.0167)
MAX_KM = 15.0
ROMAN = {"i": "1", "ii": "2", "iii": "3", "iv": "4", "v": "5", "vi": "6"}
LOWER_KEEP = {"do", "n/ż", "k-c", "i", "w", "na", "nad"}
EXTRA = {"Osada świetlica": "Leżachów Osada Świetlica"}


def fold(s):
    s = unicodedata.normalize("NFKD", s.lower())
    s = "".join(c for c in s if not unicodedata.combining(c)).replace("ł", "l")
    s = re.sub(r"\bn\s*/\s*z\b", "nz", s)
    s = re.sub(r"[^a-z0-9]+", " ", s)
    return " ".join(s.split())


def group_key(s):
    toks = fold(s).split()
    if len(toks) > 1 and re.fullmatch(r"0\d|\d\d", toks[-1]):
        toks = toks[:-1]
    return " ".join(ROMAN.get(t, t) for t in toks)


def slug(s):
    return fold(s).replace(" ", "-")


def km(lon, lat):
    return math.hypot(
        (lon - CENTER[0]) * math.cos(math.radians(lat)) * 111.32,
        (lat - CENTER[1]) * 111.32,
    )


def split_ref(name):
    m = re.match(r"^(.*?)\s+(\d\d)$", name.strip())
    return (m.group(1), m.group(2)) if m else (name.strip(), "")


def clean_display(raw):
    s = re.sub(r"\(\s*do miasta\s*\)", "", raw, flags=re.I)
    s = re.sub(r"-\s*słupek", "", s, flags=re.I)
    s = re.sub(r"\s+-\s+", " ", s)
    s = re.sub(r"(\D)(\d)$", r"\1 \2", s.strip())
    words = []
    for w in s.split():
        if w.lower() in LOWER_KEEP or len(w) < 3 or re.fullmatch(r"[IVX]+", w):
            words.append(w)
        else:
            words.append(w[0].upper() + w[1:])
    return " ".join(words)


def pick_display(raws):
    cleaned = sorted({clean_display(r) for r in raws}, key=lambda n: (-len(n.split()), -len(n), n))
    return cleaned[0]


def main():
    stops = json.loads(Path(sys.argv[1]).read_text())["stops"]
    osm_raw = json.loads(Path(sys.argv[2]).read_text())["elements"]
    out_path = Path(sys.argv[3])

    groups = {}
    for s in stops:
        if not s.get("platforms"):
            continue
        sig = tuple(sorted((round(p["lat"], 6), round(p["lon"], 6)) for p in s["platforms"]))
        g = groups.setdefault(sig, {"raws": [], "platforms": s["platforms"], "how": s["how"]})
        g["raws"].append(s["pdf"])
        if s["how"] != "placed":
            g["how"] = s["how"]

    osm_by_name = {}
    for e in osm_raw:
        name = e.get("tags", {}).get("name")
        if name and "lat" in e:
            osm_by_name.setdefault(split_ref(name)[0], []).append(
                {"name": name, "lat": e["lat"], "lon": e["lon"]})
    for raw, osm_name in EXTRA.items():
        pls = osm_by_name.get(osm_name)
        if not pls:
            raise SystemExit(f"brak w OSM: {osm_name}")
        sig = tuple(sorted((round(p["lat"], 6), round(p["lon"], 6)) for p in pls))
        g = groups.setdefault(sig, {"raws": [], "platforms": pls, "how": "alias"})
        g["raws"].append(raw)

    entries = []
    used_keys = set()
    for sig, g in groups.items():
        name = pick_display(g["raws"])
        key = slug(name)
        while key in used_keys:
            key += "-x"
        used_keys.add(key)
        placed = g["how"] == "placed"
        platforms = []
        osm_names = set()
        for p in g["platforms"]:
            base, ref = split_ref(p["name"])
            if not placed:
                osm_names.add(base)
            platforms.append({"code": ref, "lat": round(p["lat"], 6), "lon": round(p["lon"], 6)})
        platforms.sort(key=lambda p: (p["code"], p["lat"], p["lon"]))
        entries.append({
            "key": key,
            "name": name,
            "names": sorted(set(g["raws"])),
            "osm": sorted(osm_names - {name}),
            "placed": placed,
            "platforms": platforms,
        })
    entries.sort(key=lambda e: fold(e["name"]))

    osm_groups = {}
    for e in osm_raw:
        name = e.get("tags", {}).get("name")
        if not name or "lat" not in e:
            continue
        if km(e["lon"], e["lat"]) > MAX_KM:
            continue
        base, ref = split_ref(name)
        g = osm_groups.setdefault(group_key(name), {"name": base, "platforms": []})
        pt = {"code": ref, "lat": round(e["lat"], 6), "lon": round(e["lon"], 6)}
        if pt not in g["platforms"]:
            g["platforms"].append(pt)
    osm = sorted(osm_groups.values(), key=lambda g: fold(g["name"]))
    for g in osm:
        g["platforms"].sort(key=lambda p: (p["code"], p["lat"], p["lon"]))

    out_path.parent.mkdir(parents=True, exist_ok=True)
    out_path.write_text(json.dumps({"version": 1, "stops": entries, "osm": osm},
                                   ensure_ascii=False, separators=(",", ":")))
    print(f"stops={len(entries)} (z {sum(len(e['names']) for e in entries)} nazw w PDF) "
          f"osm_groups={len(osm)} -> {out_path} {out_path.stat().st_size / 1024:.1f} KB")
    for e in entries:
        if len(e["names"]) > 1:
            print(f"  {e['name']:28s} <- {e['names']}")


main()

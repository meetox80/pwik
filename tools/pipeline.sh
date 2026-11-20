#!/bin/bash
set -euo pipefail
cd "$(dirname "$0")/.."

PBF=${1:?użycie: tools/pipeline.sh podkarpackie-latest.osm.pbf}

echo "== mapa (map.bin) =="
python3 tools/build_map.py "$PBF" app/src/main/assets/seed/map.bin

echo "== przystanki (gazetteer.json) =="
python3 tools/build_gazetteer.py data/stops/stops.json data/map/osm-stops-wide.json app/src/main/assets/seed/gazetteer.json | head -1

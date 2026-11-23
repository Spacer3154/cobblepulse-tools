#!/usr/bin/env python3
"""CI : versions Modrinth des mods du serveur de test (Fabric 1.21.1)."""
import json, os, sys, urllib.request

OUT = sys.argv[1] if len(sys.argv) > 1 else "out/discover"
os.makedirs(OUT, exist_ok=True)
UA = {"User-Agent": "cobblepulse-tools-ci/1.0 (github actions)"}


def get(url):
    req = urllib.request.Request(url, headers=UA)
    with urllib.request.urlopen(req, timeout=300) as r:
        return r.read()


modrinth = {}
lines = []
for slug in ["cobblemon", "fabric-api", "fabric-language-kotlin", "luckperms"]:
    try:
        vs = json.loads(get(f"https://api.modrinth.com/v2/project/{slug}/version?loaders=%5B%22fabric%22%5D&game_versions=%5B%221.21.1%22%5D"))
        modrinth[slug] = [{"version_number": v["version_number"], "files": v["files"]} for v in vs[:15]]
        lines.append(f"{slug}: {[v['version_number'] for v in vs[:5]]}")
    except Exception as e:
        lines.append(f"{slug}: ERR {e}")
json.dump(modrinth, open(os.path.join(OUT, "modrinth.json"), "w"), indent=1)
print("\n".join(lines))

#!/usr/bin/env python3
"""Append the product audience without replacing existing platform audiences.

Changing an app setting restarts liliplanet-auth, the login service every LiliPlanet product shares, so this asks
first (or pass --yes). Updating the platform repository's source defaults is optional: pass its checkout with
--platform-repo (or LILIPLANET_PLATFORM_REPO); nothing outside this repository is touched otherwise.
"""
import argparse
import json
import os
import sys
from pathlib import Path
from bootstrap import az, ROOT, RG

parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
parser.add_argument("--yes", action="store_true", help="do not ask before restarting the shared liliplanet-auth app")
parser.add_argument("--platform-repo", default=os.environ.get("LILIPLANET_PLATFORM_REPO"),
                    help="checkout of liliplanet-platform whose auth-api/platform-api defaults should list junseo-api")
args = parser.parse_args()

settings = az("webapp", "config", "appsettings", "list", "-g", RG, "-n", "liliplanet-auth")
existing = next(x["value"] for x in settings if x["name"] == "JWT_ALLOWED_AUDIENCES")
audiences = [x.strip() for x in existing.split(",") if x.strip()]
if "junseo-api" not in audiences:
    updated = existing + ",junseo-api"
    print(f"liliplanet-auth JWT_ALLOWED_AUDIENCES gains junseo-api ({len(audiences)} existing audiences kept).")
    print("Saving the setting restarts liliplanet-auth: every LiliPlanet product's login pauses briefly.")
    if not args.yes and input("Continue? [y/N] ").strip().lower() != "y":
        sys.exit("Nothing changed.")
    (ROOT / "docs/azure-auth-audience-change.json").write_text(json.dumps(
        {"app": "liliplanet-auth", "setting": "JWT_ALLOWED_AUDIENCES", "before": existing, "after": updated}, indent=2) + "\n")
    az("webapp", "config", "appsettings", "set", "-g", RG, "-n", "liliplanet-auth",
       "--settings", "JWT_ALLOWED_AUDIENCES=" + updated)
    print("Appended junseo-api; retained all existing audiences.")
else:
    print("junseo-api is already registered.")

if not args.platform_repo:
    print("Platform source defaults not touched (no --platform-repo).")
    sys.exit(0)
platform = Path(args.platform_repo).expanduser() / "backend/apps"
targets = [platform / application / "src/main/resources/application.yml" for application in ["auth-api", "platform-api"]]
missing = [str(path) for path in targets if not path.is_file()]
if missing:
    sys.exit("Not a liliplanet-platform checkout, nothing changed: " + ", ".join(missing))
for path in targets:
    lines = path.read_text().splitlines(keepends=True)
    for i, line in enumerate(lines):
        if "allowed-audiences:" in line and "JWT_ALLOWED_AUDIENCES:" in line and "junseo-api" not in line:
            lines[i] = line.replace("lilitour-api}", "lilitour-api,junseo-api}")
    path.write_text("".join(lines))
print("Canonical source defaults updated in the platform checkout (commit and deploy them there); no shared platform code deployment.")

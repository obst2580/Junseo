#!/usr/bin/env python3
"""Append the product audience without replacing existing platform audiences."""
import json
from pathlib import Path
from bootstrap import az, ROOT, RG

settings = az("webapp", "config", "appsettings", "list", "-g", RG, "-n", "liliplanet-auth")
existing = next(x["value"] for x in settings if x["name"] == "JWT_ALLOWED_AUDIENCES")
audiences = [x.strip() for x in existing.split(",") if x.strip()]
if "junseo-api" not in audiences:
    updated = existing + ",junseo-api"
    (ROOT / "docs/azure-auth-audience-change.json").write_text(json.dumps(
        {"app": "liliplanet-auth", "setting": "JWT_ALLOWED_AUDIENCES", "before": existing, "after": updated}, indent=2) + "\n")
    az("webapp", "config", "appsettings", "set", "-g", RG, "-n", "liliplanet-auth",
       "--settings", "JWT_ALLOWED_AUDIENCES=" + updated)
    print("Appended junseo-api; retained all existing audiences.")
else:
    print("junseo-api is already registered.")

platform = Path("/Users/obst/personal_project/liliplanet-platform/backend/apps")
for application in ["auth-api", "platform-api"]:
    path = platform / application / "src/main/resources/application.yml"
    source = path.read_text()
    lines = source.splitlines(keepends=True)
    for i, line in enumerate(lines):
        if "allowed-audiences:" in line and "JWT_ALLOWED_AUDIENCES:" in line and "junseo-api" not in line:
            lines[i] = line.replace("lilitour-api}", "lilitour-api,junseo-api}")
    path.write_text("".join(lines))
print("Canonical source defaults updated; no shared platform code deployment.")

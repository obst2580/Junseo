#!/usr/bin/env python3
"""Public deployment checks, without obtaining an end-user token."""
import base64
import hashlib
import json
from datetime import datetime, timezone
from urllib.parse import urlparse, parse_qs
import requests
from bootstrap import ROOT, DOMAIN

base = "https://" + DOMAIN
session = requests.Session()
checks = {}


def check(name, condition):
    checks[name] = bool(condition)
    if not condition:
        raise RuntimeError("Deployment check failed: " + name)
    print("PASS", name, flush=True)


r = session.get(base + "/actuator/health", timeout=30)
check("runtime_database_blob_health", r.status_code == 200 and r.json().get("status") == "UP")
r = session.get(base + "/api/auth/config", timeout=30)
check("platform_auth_config", r.status_code == 200 and r.json().get("audience") == "junseo-api" and r.json().get("mode") == "platform")
r = session.get(base + "/login", timeout=30)
check("login_page", r.status_code == 200 and "리리플레닛으로 로그인" in r.text)
verifier = "live-smoke-verifier-" + "a" * 48
challenge = base64.urlsafe_b64encode(hashlib.sha256(verifier.encode()).digest()).decode().rstrip("=")
r = session.post(base + "/api/auth/start", json={"codeChallenge": challenge, "returnUri": base + "/login"},
                 headers={"Origin": base}, timeout=30)
check("same_origin_login_start", r.status_code == 200)
flow = r.json()
r = session.get(flow["launchUrl"], allow_redirects=False, timeout=30)
location = urlparse(r.headers.get("Location", ""))
query = parse_qs(location.query)
check("central_login_redirect", r.status_code == 302 and location.netloc == "login.liliplanet.net" and query.get("client") == ["junseo-api"])
check("browser_cookie_security", all(x in r.headers.get("Set-Cookie", "") for x in ["Secure", "HttpOnly", "SameSite=Lax"]))
r = session.get(base + "/auth/callback", params={"state": flow["state"], "token": "invalid-smoke-token"}, allow_redirects=False, timeout=30)
check("invalid_central_token_rejected", r.status_code == 401 and "Location" not in r.headers)
r = session.post(base + "/api/auth/start", json={"codeChallenge": challenge, "returnUri": "https://example.com"}, timeout=30)
check("external_redirect_rejected", r.status_code == 401)
r = session.get(base + "/api/me", timeout=30)
check("anonymous_profile_rejected", r.status_code == 401)
r = session.post(base + "/api/auth/login", json={"email": "unused@example.com", "password": "unused"}, timeout=30)
check("product_password_login_disabled", r.status_code == 404)
for name, path in [("central_login_available", "https://login.liliplanet.net"), ("central_jwks_available", "https://auth.liliplanet.net/.well-known/jwks.json")]:
    r = requests.get(path, timeout=30)
    check(name, r.status_code == 200)
report = {"checkedAt": datetime.now(timezone.utc).isoformat(), "baseUrl": base, "checks": checks,
          "realAccountLogin": "pending user sign-in", "iOSDeviceLogin": "not performed"}
(ROOT / "docs/azure-smoke-results.json").write_text(json.dumps(report, indent=2) + "\n")
print("Public deployment checks passed; real-account sign-in is a separate user check.", flush=True)

#!/usr/bin/env python3
"""Bind the dedicated product domain with an App Service managed certificate."""
import json
from bootstrap import az, ROOT, RG, APP, DOMAIN

metadata = json.loads((ROOT / "docs/azure-resources.json").read_text())
zone, record = "liliplanet.net", "junseo-api"
cname = az("network", "dns", "record-set", "cname", "show", "-g", RG, "-z", zone, "-n", record, optional=True)
if cname:
    value = cname.get("cnameRecord", cname.get("cname_record", {})).get("cname")
    if value != metadata["defaultHostname"]:
        raise RuntimeError("Refusing to replace an unrelated DNS record")
else:
    az("network", "dns", "record-set", "cname", "create", "-g", RG, "-z", zone, "-n", record, "--ttl", "300")
    az("network", "dns", "record-set", "cname", "set-record", "-g", RG, "-z", zone, "-n", record,
       "-c", metadata["defaultHostname"])
az("network", "dns", "record-set", "txt", "add-record", "-g", RG, "-z", zone, "-n", "asuid." + record,
   "--value", metadata["customDomainVerificationId"])
print("Junseo CNAME and ownership TXT configured.", flush=True)
bindings = az("webapp", "config", "hostname", "list", "-g", RG, "--webapp-name", APP)
if not any(x.get("name", "").endswith("/" + DOMAIN) or x.get("name") == DOMAIN for x in bindings):
    az("webapp", "config", "hostname", "add", "-g", RG, "--webapp-name", APP, "--hostname", DOMAIN)
print("Hostname bound; requesting managed TLS certificate.", flush=True)
certificate_id = f"/subscriptions/{metadata['subscription']}/resourceGroups/{RG}/providers/Microsoft.Web/certificates/{DOMAIN}"
certificate = az("resource", "show", "--ids", certificate_id, "--api-version", "2022-03-01", optional=True)
if not certificate:
    az("webapp", "config", "ssl", "create", "-g", RG, "-n", APP, "--hostname", DOMAIN, "--certificate-name", DOMAIN)
    certificate = az("resource", "show", "--ids", certificate_id, "--api-version", "2022-03-01")
thumbprint = certificate.get("thumbprint")
if not thumbprint:
    raise RuntimeError("Managed certificate has not finished provisioning")
az("webapp", "config", "ssl", "bind", "-g", RG, "-n", APP, "--certificate-thumbprint", thumbprint, "--ssl-type", "SNI")
print("Managed TLS bound: https://" + DOMAIN, flush=True)

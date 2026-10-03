#!/usr/bin/env python3
"""Provision only Junseo resources. Requires an authenticated Azure CLI operator.

Secrets pass through memory or mode-0600 temporary files, never command arguments,
stdout or source control. The existing PostgreSQL firewall and app plan stay intact.
"""
import json
import os
from pathlib import Path
import secrets
import subprocess
import tempfile

RG = "studylog-rg"
LOCATION = "koreacentral"
APP = "junseo-api"
PLAN = "junseo-plan"
SUFFIX = "f14e91"
VAULT = f"junseo-kv-{SUFFIX}"
STORAGE = f"junseomedia{SUFFIX}"
HOST = "studylog-db.postgres.database.azure.com"
DOMAIN = "junseo-api.liliplanet.net"
ROOT = Path(__file__).resolve().parents[2]


def az(*args, optional=False):
    result = subprocess.run(["az", *args, "--only-show-errors", "-o", "json"], capture_output=True, text=True)
    if result.returncode:
        if optional:
            return None
        # CLI errors can repeat secret payloads; never print them.
        raise RuntimeError(f"Azure operation failed: {' '.join(args[:3])}")
    value = json.loads(result.stdout) if result.stdout.strip() else None
    if isinstance(value, dict) and isinstance(value.get("properties"), dict):
        value = {**value["properties"], **value}
    return value


def step(message):
    print(message, flush=True)


def vault_secret(name):
    existing = az("keyvault", "secret", "show", "--vault-name", VAULT, "--name", name, optional=True)
    if existing:
        return existing["value"]
    value = secrets.token_urlsafe(48)
    with tempfile.NamedTemporaryFile(mode="w", delete=False) as f:
        os.chmod(f.name, 0o600)
        f.write(value)
        path = f.name
    try:
        az("keyvault", "secret", "set", "--vault-name", VAULT, "--name", name, "--file", path)
    finally:
        os.unlink(path)
    return value


def psql(database, sql, username, password):
    env = dict(os.environ, PGHOST=HOST, PGPORT="5432", PGDATABASE=database,
               PGUSER=username, PGPASSWORD=password, PGSSLMODE="verify-full",
               PGSSLROOTCERT="/opt/homebrew/etc/ca-certificates/cert.pem", PGCONNECT_TIMEOUT="15")
    result = subprocess.run(["/opt/homebrew/opt/postgresql@16/bin/psql", "-X", "-q", "-t", "-A",
                             "-v", "ON_ERROR_STOP=1"], input=sql, text=True, capture_output=True, env=env)
    if result.returncode:
        raise RuntimeError("Isolated Junseo database operation failed (secret-bearing diagnostics suppressed)")
    return result.stdout.strip()


def main():
    account = az("account", "show")
    if account["id"] != "f14e91e2-b819-4cd6-ac39-e4a3909c17b9":
        raise RuntimeError("Unexpected subscription")
    tags = ["service=junseo", "managedBy=junseo-repository"]
    step("Creating/checking dedicated Junseo B1 plan")
    plan = az("appservice", "plan", "show", "-g", RG, "-n", PLAN, optional=True)
    if not plan:
        plan = az("appservice", "plan", "create", "-g", RG, "-n", PLAN, "--is-linux", "--sku", "B1",
                  "--location", LOCATION, "--tags", *tags)
    if plan["sku"]["name"] != "B1" or not plan["reserved"]:
        raise RuntimeError("Unexpected Junseo plan")
    app = az("webapp", "show", "-g", RG, "-n", APP, optional=True)
    if not app:
        app = az("webapp", "create", "-g", RG, "-n", APP, "--plan", PLAN, "--runtime", "JAVA:21-java21", "--tags", *tags)
    if (app.get("serverFarmId") or app.get("appServicePlanId", "")).lower() != plan["id"].lower():
        raise RuntimeError("Refusing to modify an app outside the dedicated plan")
    az("webapp", "update", "-g", RG, "-n", APP, "--https-only", "true")
    az("webapp", "config", "set", "-g", RG, "-n", APP, "--always-on", "true", "--web-sockets-enabled", "true",
       "--min-tls-version", "1.2", "--http20-enabled", "true", "--generic-configurations",
       json.dumps({"healthCheckPath": "/actuator/health", "scmMinTlsVersion": "1.2", "ftpsState": "Disabled"}))
    identity = az("webapp", "identity", "assign", "-g", RG, "-n", APP)
    principal = identity["principalId"]
    step("Creating/checking private Blob storage and Junseo Key Vault")
    storage = az("storage", "account", "show", "-g", RG, "-n", STORAGE, optional=True)
    if not storage:
        storage = az("storage", "account", "create", "-g", RG, "-n", STORAGE, "--location", LOCATION,
                     "--sku", "Standard_LRS", "--kind", "StorageV2", "--https-only", "true",
                     "--min-tls-version", "TLS1_2", "--allow-blob-public-access", "false",
                     "--allow-shared-key-access", "false", "--tags", *tags)
    az("storage", "container-rm", "create", "--storage-account", STORAGE, "-g", RG, "-n", "media",
       "--public-access", "off")
    container_id = storage["id"] + "/blobServices/default/containers/media"
    assignments = az("role", "assignment", "list", "--assignee", principal, "--scope", container_id)
    if not any(x["roleDefinitionName"] == "Storage Blob Data Contributor" for x in assignments):
        az("role", "assignment", "create", "--assignee-object-id", principal, "--assignee-principal-type", "ServicePrincipal",
           "--role", "Storage Blob Data Contributor", "--scope", container_id)
    vault = az("keyvault", "show", "-g", RG, "-n", VAULT, optional=True)
    if not vault:
        vault = az("keyvault", "create", "-g", RG, "-n", VAULT, "--location", LOCATION,
                   "--sku", "standard", "--enable-rbac-authorization", "false", "--tags", *tags)
    az("keyvault", "set-policy", "-g", RG, "-n", VAULT, "--object-id", principal, "--secret-permissions", "get")
    runtime_password = vault_secret("db-runtime-password")
    migration_password = vault_secret("db-migration-password")
    vault_secret("media-signing-secret")
    step("Creating/checking isolated Junseo database and least-privilege roles")
    auth_settings = {x["name"]: x["value"] for x in az("webapp", "config", "appsettings", "list", "-g", RG, "-n", "liliplanet-auth")}
    admin = auth_settings["AUTH_DB_USERNAME"]
    admin_password = auth_settings["AUTH_DB_PASSWORD"]
    if admin != "studylogadmin":
        raise RuntimeError("Unexpected PostgreSQL administrator")
    for role, password, limit in [("junseo_migrator", migration_password, 2), ("junseo_runtime", runtime_password, 5)]:
        # Identifiers are constants and passwords are generated URL-safe values.
        psql("postgres", f"""DO $$ BEGIN IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname='{role}') THEN
          CREATE ROLE {role} LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION; END IF; END $$;
          ALTER ROLE {role} PASSWORD '{password}' CONNECTION LIMIT {limit};""", admin, admin_password)
    psql("postgres", "GRANT junseo_migrator TO studylogadmin;", admin, admin_password)
    exists = psql("postgres", "SELECT EXISTS(SELECT FROM pg_database WHERE datname='junseo');", admin, admin_password)
    if exists == "f":
        psql("postgres", "CREATE DATABASE junseo OWNER junseo_migrator;", admin, admin_password)
    owner = psql("postgres", "SELECT pg_get_userbyid(datdba) FROM pg_database WHERE datname='junseo';", admin, admin_password)
    if owner != "junseo_migrator":
        raise RuntimeError("Refusing to take over an existing database")
    psql("junseo", """REVOKE ALL ON DATABASE junseo FROM PUBLIC; GRANT CONNECT ON DATABASE junseo TO junseo_runtime;
      REVOKE CREATE ON SCHEMA public FROM PUBLIC; GRANT USAGE ON SCHEMA public TO junseo_runtime;
      GRANT USAGE,CREATE ON SCHEMA public TO junseo_migrator;
      GRANT SELECT,INSERT,UPDATE,DELETE ON ALL TABLES IN SCHEMA public TO junseo_runtime;
      GRANT USAGE,SELECT ON ALL SEQUENCES IN SCHEMA public TO junseo_runtime;
      ALTER DEFAULT PRIVILEGES FOR ROLE junseo_migrator IN SCHEMA public GRANT SELECT,INSERT,UPDATE,DELETE ON TABLES TO junseo_runtime;
      ALTER DEFAULT PRIVILEGES FOR ROLE junseo_migrator IN SCHEMA public GRANT USAGE,SELECT ON SEQUENCES TO junseo_runtime;
      DO $$ BEGIN IF to_regclass('public.flyway_schema_history') IS NOT NULL THEN
        REVOKE ALL ON TABLE public.flyway_schema_history FROM junseo_runtime;
      END IF; END $$;
      """, admin, admin_password)
    # Do not let a default PUBLIC database CONNECT grant expose the other service databases.
    # Product runtime gets only Junseo credentials; its own table privileges are scoped above.
    psql("junseo", "SELECT 1;", "junseo_runtime", runtime_password)
    psql("junseo", "SELECT 1;", "junseo_migrator", migration_password)
    step("Configuring production settings through Key Vault references")
    def ref(name):
        return f"@Microsoft.KeyVault(SecretUri=https://{VAULT}.vault.azure.net/secrets/{name})"
    settings = {
        "SPRING_PROFILES_ACTIVE": "prod", "SERVER_PORT": "80",
        "JAVA_OPTS": "-Xms128m -Xmx768m -XX:MaxMetaspaceSize=256m",
        "JUNSEO_DB_URL": f"jdbc:postgresql://{HOST}:5432/junseo?sslmode=verify-full&sslfactory=org.postgresql.ssl.DefaultJavaSSLFactory",
        "JUNSEO_DB_USER": "junseo_runtime", "JUNSEO_DB_PASSWORD": ref("db-runtime-password"),
        "JUNSEO_MIGRATION_USER": "junseo_migrator", "JUNSEO_MIGRATION_PASSWORD": ref("db-migration-password"),
        "JUNSEO_MEDIA_SECRET": ref("media-signing-secret"), "JUNSEO_PUBLIC_BASE_URL": "https://" + DOMAIN,
        "JUNSEO_BLOB_ENDPOINT": storage["primaryEndpoints"]["blob"].rstrip("/"),
        "WEBSITE_SKIP_AUTOCONFIGURE_DATABASE": "true", "WEBSITES_CONTAINER_START_TIME_LIMIT": "600"
    }
    with tempfile.NamedTemporaryFile(mode="w", suffix=".json", delete=False) as f:
        os.chmod(f.name, 0o600)
        json.dump(settings, f)
        path = f.name
    try:
        az("webapp", "config", "appsettings", "set", "-g", RG, "-n", APP, "--settings", "@" + path)
    finally:
        os.unlink(path)
    az("webapp", "log", "config", "-g", RG, "-n", APP, "--web-server-logging", "off")
    metadata = {"resourceGroup": RG, "app": APP, "plan": PLAN, "sku": "B1", "storage": STORAGE,
                "vault": VAULT, "containerScope": container_id, "principalId": principal,
                "defaultHostname": app["defaultHostName"], "domain": DOMAIN,
                "customDomainVerificationId": app["customDomainVerificationId"], "database": "junseo",
                "subscription": account["id"]}
    path = ROOT / "docs/azure-resources.json"
    path.write_text(json.dumps(metadata, indent=2) + "\n")
    step(f"Junseo resources ready; non-secret inventory: {path}")


if __name__ == "__main__":
    main()

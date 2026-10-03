#!/usr/bin/env python3
"""Build an internal APK and sign it with a private, persistent Junseo preview key."""
import argparse
import json
import os
from pathlib import Path
import secrets
import shutil
import subprocess

ROOT = Path(__file__).resolve().parents[2]
MOBILE = ROOT / "mobile"


def run(command, env, cwd=MOBILE):
    subprocess.run(command, cwd=cwd, env=env, check=True)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--skip-build", action="store_true", help="Sign the already verified release APK")
    args = parser.parse_args()
    env = dict(os.environ)
    env.setdefault("ANDROID_HOME", str(Path.home() / "Library/Android/sdk"))
    env.setdefault("JAVA_HOME", subprocess.check_output(["/usr/libexec/java_home", "-v", "17"], text=True).strip())
    env["CI"] = "1"
    if not args.skip_build:
        run(["npx", "expo", "prebuild", "--platform", "android", "--no-install"], env)
        run(["./android/gradlew", "-p", "android", ":app:assembleRelease", "-PreactNativeArchitectures=arm64-v8a",
             "--max-workers=4", "--console=plain"], env)
    credentials = MOBILE / ".credentials"
    credentials.mkdir(mode=0o700, exist_ok=True)
    keystore = credentials / "junseo-preview.jks"
    settings = credentials / "android-preview.json"
    if keystore.exists() != settings.exists():
        raise RuntimeError("Preview key and password file must both exist; refusing to replace an existing key")
    keytool = Path(env["JAVA_HOME"]) / "bin/keytool"
    if not keystore.exists():
        password = secrets.token_urlsafe(32)
        env["JUNSEO_PREVIEW_KEY_PASSWORD"] = password
        run([str(keytool), "-genkeypair", "-keystore", str(keystore), "-alias", "junseo-preview", "-keyalg", "RSA",
             "-keysize", "3072", "-validity", "3650", "-storepass:env", "JUNSEO_PREVIEW_KEY_PASSWORD",
             "-keypass:env", "JUNSEO_PREVIEW_KEY_PASSWORD", "-dname", "CN=Junseo Internal Preview, O=Junseo, C=KR"], env)
        fd = os.open(settings, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
        with os.fdopen(fd, "w") as f:
            json.dump({"alias": "junseo-preview", "password": password}, f)
        os.chmod(keystore, 0o600)
    config = json.loads(settings.read_text())
    env["JUNSEO_PREVIEW_KEY_PASSWORD"] = config["password"]
    artifacts = MOBILE / "artifacts"
    artifacts.mkdir(exist_ok=True)
    version = json.loads((MOBILE / "package.json").read_text())["version"]
    destination = artifacts / f"junseo-{version}-android-arm64.apk"
    shutil.copyfile(MOBILE / "android/app/build/outputs/apk/release/app-release.apk", destination)
    sdk = Path(env["ANDROID_HOME"])
    tools = sorted((sdk / "build-tools").iterdir(), key=lambda p: tuple(int(n) for n in p.name.split('.')))[-1]
    run([str(tools / "apksigner"), "sign", "--ks", str(keystore), "--ks-key-alias", config["alias"],
         "--ks-pass", "env:JUNSEO_PREVIEW_KEY_PASSWORD", "--key-pass", "env:JUNSEO_PREVIEW_KEY_PASSWORD", str(destination)], env)
    run([str(tools / "apksigner"), "verify", "--print-certs", str(destination)], env)
    print("Internal preview APK:", destination)


if __name__ == "__main__":
    main()

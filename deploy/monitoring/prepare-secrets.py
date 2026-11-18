#!/usr/bin/env python3
"""Prepare local monitoring secrets from the existing read-only Nacos account.

Run on homeserver as root. Requires Debian's python3-yaml package. Secret values
are never printed or written to the repository.
"""
import json
import os
from pathlib import Path
import secrets
import subprocess
import urllib.parse
import urllib.request

import yaml

ROOT = Path(__file__).resolve().parent


def inspect(name):
    return json.loads(subprocess.check_output(["docker", "inspect", name], text=True))[0]


def write_private(path, value, uid):
    path.write_text(value)
    os.chown(path, uid, uid)
    path.chmod(0o400)


def main():
    names = subprocess.check_output(["docker", "ps", "--format", "{{.Names}}"], text=True).splitlines()
    backend_names = [name for name in names if name.startswith("shoe-factory-backend-")]
    if len(backend_names) != 1:
        raise RuntimeError("Expected exactly one running production backend")
    backend = inspect(backend_names[0])
    env = dict(value.split("=", 1) for value in backend["Config"]["Env"])
    nacos = inspect("shoe-factory-config-nacos-1")
    address = nacos["NetworkSettings"]["Networks"]["shoe-factory-config_default"]["IPAddress"]
    base = f"http://{address}:8848/nacos"
    credentials = urllib.parse.urlencode({"username": env["NACOS_USERNAME"], "password": env["NACOS_PASSWORD"]})
    request = urllib.request.Request(base + "/v3/auth/user/login", data=credentials.encode())
    with urllib.request.urlopen(request, timeout=10) as response:
        token = json.load(response)["accessToken"]
    query = urllib.parse.urlencode({"namespaceId": env["NACOS_NAMESPACE"], "groupName": "SHOE_FACTORY", "dataId": "shoe-factory-backend.yml"})
    request = urllib.request.Request(base + "/v3/client/cs/config?" + query, headers={"accessToken": token})
    with urllib.request.urlopen(request, timeout=10) as response:
        result = json.load(response)
    if result.get("code") != 0:
        raise RuntimeError("Nacos configuration read failed")
    config = yaml.safe_load(result["data"]["content"])
    mail = config["spring"]["mail"]
    username = mail["username"]
    password = mail["password"]
    recipient = config.get("app", {}).get("delivery-report", {}).get("recipient") or username
    if not all(isinstance(value, str) and value.strip() and "\n" not in value for value in [username, password, recipient]):
        raise RuntimeError("Mail configuration is incomplete")
    if recipient != username:
        raise RuntimeError("Recipient differs from the mailbox owner; confirm the monitoring recipient first")
    private = ROOT / "private"
    private.mkdir(mode=0o700, exist_ok=True)
    private.chmod(0o700)
    write_private(private / "smtp-password", password, 65534)
    grafana_secret = private / "grafana-admin-password"
    if not grafana_secret.exists():
        write_private(grafana_secret, secrets.token_urlsafe(24), 472)
    template = (ROOT / "alertmanager.example.yml").read_text()
    template = template.replace("__SMTP_USERNAME__", username).replace("__SMTP_RECIPIENT__", recipient)
    write_private(private / "alertmanager.yml", template, 65534)
    print("Prepared private SMTP configuration and Grafana admin password; no secret values displayed.")
    print("Alert recipient:", recipient)


if __name__ == "__main__":
    main()

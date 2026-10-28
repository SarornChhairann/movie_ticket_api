#!/usr/bin/env python3
"""Derive deploy/keycloak/realm-prod.json from the dev keycloak/realm-export.json.

Production differences: no demo users (the API's AdminSeeder creates the admin from
.env.prod), HTTP allowed (this server is reached by IP, with no DNS name), brute-force protection on, no
wildcard redirect URIs or web origins, and no fixed ticket-backend secret, so
Keycloak generates a random one on import (copy it into KEYCLOAK_ADMIN_CLIENT_SECRET).

Re-run after changing the dev realm:  python3 deploy/keycloak/make-prod-realm.py
"""
import json
import pathlib

root = pathlib.Path(__file__).resolve().parents[2]
realm = json.loads((root / "keycloak" / "realm-export.json").read_text())

# Keep only the backend's service account (it carries the manage-users roles).
realm["users"] = [u for u in realm.get("users", []) if u.get("username", "").startswith("service-account-")]
realm["sslRequired"] = "none"
realm["bruteForceProtected"] = True
realm["failureFactor"] = 5
realm["permanentLockout"] = False
realm["waitIncrementSeconds"] = 60
realm["maxFailureWaitSeconds"] = 900

for client in realm.get("clients", []):
    client["redirectUris"] = []
    client["webOrigins"] = []
    client.pop("secret", None)

out = root / "deploy" / "keycloak" / "realm-prod.json"
out.write_text(json.dumps(realm, indent=2) + "\n")
print(f"wrote {out.relative_to(root)}: users={[u['username'] for u in realm['users']]}")

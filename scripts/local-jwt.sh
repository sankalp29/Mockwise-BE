#!/usr/bin/env bash
# Print a fake JWT accepted by local SupabaseAuthService (no signature verification).
# Usage: export TOKEN=$(./scripts/local-jwt.sh)
set -euo pipefail
python3 - <<'PY'
import base64, json

def b64url(data: bytes) -> str:
    return base64.urlsafe_b64encode(data).rstrip(b"=").decode()

header = b64url(json.dumps({"alg": "none", "typ": "JWT"}).encode())
payload = b64url(json.dumps({
    "sub": "local-user-001",
    "email": "local@test.com",
}).encode())
sig = b64url(b"local")
print(f"{header}.{payload}.{sig}")
PY

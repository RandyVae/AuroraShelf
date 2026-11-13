#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${AUTH_API_BASE_URL:-https://bananarey.us/auth-api}"
health="$(curl -fsS "${BASE_URL}/api/health")"
printf 'health=%s\n' "${health}"

status="$(curl -sS -o /tmp/netgrey-auth-invalid.json -w '%{http_code}' \
  -X POST "${BASE_URL}/api/collections/users/records" \
  -H 'Content-Type: application/json' -d '{}')"
if [ "${status}" != "400" ]; then
  echo "unexpected registration validation status: ${status}" >&2
  exit 1
fi

python3 - <<'PY'
import json
with open('/tmp/netgrey-auth-invalid.json', encoding='utf-8') as handle:
    payload = json.load(handle)
assert payload.get('status') == 400
assert 'password' in payload.get('data', {})
print('registration_contract=ok')
PY

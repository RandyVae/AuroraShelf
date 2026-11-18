#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${AUTH_API_BASE_URL:-https://bananarey.us/auth-api}"
TMP_DIR="$(mktemp -d)"
cleanup() { rm -rf "${TMP_DIR}"; }
trap cleanup EXIT

INVALID_REG_FILE="${TMP_DIR}/invalid-register.json"
LIST_USERS_FILE="${TMP_DIR}/list-users.json"

health="$(curl -fsS "${BASE_URL}/api/health")"
printf 'health=%s\n' "${health}"

status="$(curl -sS -o "${INVALID_REG_FILE}" -w '%{http_code}' \
  -X POST "${BASE_URL}/api/collections/users/records" \
  -H 'Content-Type: application/json' -d '{}')"
if [ "${status}" != "400" ]; then
  echo "unexpected registration validation status: ${status}" >&2
  exit 1
fi

python3 - "${INVALID_REG_FILE}" <<'PY'
import json
import sys

with open(sys.argv[1], encoding='utf-8') as handle:
    payload = json.load(handle)
assert payload.get('status') == 400
assert 'password' in payload.get('data', {})
print('registration_contract=ok')
PY

# Verify unauthenticated GET /api/collections/users/records does NOT leak user records.
list_status="$(curl -sS -o "${LIST_USERS_FILE}" -w '%{http_code}' \
  "${BASE_URL}/api/collections/users/records")"
python3 - "${list_status}" "${LIST_USERS_FILE}" <<'PY'
import json
import sys

status_code = int(sys.argv[1])
with open(sys.argv[2], encoding='utf-8') as handle:
    raw = handle.read()

if status_code in (401, 403):
    print(f'users_list_locked=ok (status={status_code})')
elif status_code == 200:
    payload = json.loads(raw)
    items = payload.get('items')
    if not isinstance(items, list) or len(items) != 0:
        raise SystemExit(f'CRITICAL: unauthenticated users list leaked {len(items or [])} records!')
    print('users_list_locked=ok (empty_items)')
else:
    raise SystemExit(f'unexpected users list status: {status_code}')
PY

# Verify PocketBase verification request endpoint returns 204 without mailer errors.
verif_status="$(curl -sS -o /dev/null -w '%{http_code}' \
  -X POST "${BASE_URL}/api/collections/users/request-verification" \
  -H 'Content-Type: application/json' \
  -d '{"email":"verify-probe@bananarey.us"}')"
if [ "${verif_status}" != "204" ]; then
  echo "unexpected request-verification status: ${verif_status}" >&2
  exit 1
fi
echo "request_verification_endpoint=ok"

# Verify PocketBase admin UI (/auth-api/_ and /auth-api/_/) is blocked with 404.
for blocked_path in "/_" "/_/" "/api/collections/_superusers" "/api/admins" "/api/backups" "/api/settings" "/api/logs"; do
  b_status="$(curl -sS -o /dev/null -w '%{http_code}' "${BASE_URL}${blocked_path}")"
  if [ "${b_status}" != "404" ]; then
    echo "expected 404 for ${BASE_URL}${blocked_path}, got ${b_status}" >&2
    exit 1
  fi
done
echo "admin_and_internal_routes_blocked=ok"

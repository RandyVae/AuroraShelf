import collections
import concurrent.futures
import hashlib
import hmac
import http.server
import json
import os
import plistlib
import re
import secrets
import socketserver
import subprocess
import tempfile
import threading
import time
import urllib.error
import urllib.parse
import urllib.request
from datetime import datetime

try:
    import jwt
except ImportError:
    jwt = None

PORT = 5000
STATE_DIR = '/var/lib/netgrey-ota'
LEGACY_WEB_DIR = '/var/www/netgrey'
DEVICES_FILE = f'{STATE_DIR}/registered_devices.json'
INVITES_FILE = f'{STATE_DIR}/testflight_invites.json'
LEGACY_DEVICES_FILE = f'{LEGACY_WEB_DIR}/registered_devices.json'
LEGACY_INVITES_FILE = f'{LEGACY_WEB_DIR}/testflight_invites.json'
PROVISIONED_UDIDS_FILE = f'{LEGACY_WEB_DIR}/provisioned_udids.json'
CONFIG_FILE = '/etc/netgrey-ota/api_config.json'
TF_CONFIG_FILE = '/etc/netgrey-ota/testflight_invite_config.json'
ALLOWED_ORIGIN = 'https://bananarey.us'

EMAIL_RE = re.compile(r'^[A-Za-z0-9._%+\-]+@[A-Za-z0-9.\-]+\.[A-Za-z]{2,}$')
UDID_RE = re.compile(r'^(?:[0-9A-Fa-f]{40}|[0-9A-Fa-f]{8}-[0-9A-Fa-f]{16})$')
CONTROL_CHARS_RE = re.compile(r'[\x00-\x1f\x7f]+')

STATE_LOCK = threading.Lock()
INVITE_OP_LOCK = threading.Lock()
SYNC_LOCK = threading.Lock()
LAST_SYNC_AT = 0.0

RATE_LIMIT_LOCK = threading.Lock()
RATE_LIMIT_BUCKETS = collections.OrderedDict()
RATE_LIMIT_MAX_BUCKETS = 4096
GLOBAL_FAILED_INVITE_TIMESTAMPS = collections.deque()
GLOBAL_INVITE_FAIL_LIMIT = 60
GLOBAL_INVITE_FAIL_WINDOW = 600

ENROLL_TOKEN_TTL = 600
ENROLL_NONCE_LOCK = threading.Lock()
USED_ENROLL_NONCES = collections.OrderedDict()
MAX_ENROLL_NONCES = 4096
BOOT_SECRET = secrets.token_bytes(32)

MAX_POLLER_RETRIES = 15

APPLE_JWT_LOCK = threading.Lock()
APPLE_JWT_CACHE = {'token': None, 'exp': 0}


class BoundedThreadingTCPServer(socketserver.TCPServer):
    allow_reuse_address = True
    request_queue_size = 64

    def __init__(self, server_address, RequestHandlerClass, max_workers=16):
        super().__init__(server_address, RequestHandlerClass)
        self._executor = concurrent.futures.ThreadPoolExecutor(
            max_workers=max_workers,
            thread_name_prefix='ota-worker',
        )

    def get_request(self):
        conn, addr = super().get_request()
        conn.settimeout(30)
        return conn, addr

    def process_request_thread(self, request, client_address):
        try:
            self.finish_request(request, client_address)
        except Exception:
            self.handle_error(request, client_address)
        finally:
            self.shutdown_request(request)

    def process_request(self, request, client_address):
        try:
            self._executor.submit(self.process_request_thread, request, client_address)
        except RuntimeError:
            self.shutdown_request(request)

    def server_close(self):
        super().server_close()
        self._executor.shutdown(wait=False, cancel_futures=True)


def load_json_file(path, default):
    if not os.path.exists(path):
        return default
    try:
        with open(path, 'r', encoding='utf-8') as f:
            return json.load(f)
    except Exception:
        return default


def save_json_file(path, data):
    target_dir = os.path.dirname(path) or '.'
    os.makedirs(target_dir, mode=0o700, exist_ok=True)
    fd, tmp_path = tempfile.mkstemp(prefix='.tmp_', suffix='.json', dir=target_dir)
    try:
        with os.fdopen(fd, 'w', encoding='utf-8') as f:
            json.dump(data, f, ensure_ascii=False, indent=2)
        os.chmod(tmp_path, 0o600)
        os.replace(tmp_path, path)
    except Exception:
        if os.path.exists(tmp_path):
            os.unlink(tmp_path)
        raise


def _merge_records_by_key(primary_list, legacy_list, key_name):
    merged = []
    seen = set()
    for item in (primary_list if isinstance(primary_list, list) else []):
        if isinstance(item, dict):
            k = str(item.get(key_name) or '').strip().lower()
            if k:
                seen.add(k)
            merged.append(item)
    for item in (legacy_list if isinstance(legacy_list, list) else []):
        if isinstance(item, dict):
            k = str(item.get(key_name) or '').strip().lower()
            if k and k in seen:
                continue
            if k:
                seen.add(k)
            merged.append(item)
    return merged


def migrate_legacy_state_files():
    try:
        os.makedirs(STATE_DIR, mode=0o700, exist_ok=True)
        os.chmod(STATE_DIR, 0o700)
    except OSError as e:
        print(f'[State Init Warning] Could not prepare {STATE_DIR}: {e}')
        return

    with STATE_LOCK:
        migrations = [
            (LEGACY_DEVICES_FILE, DEVICES_FILE, 'udid'),
            (LEGACY_INVITES_FILE, INVITES_FILE, 'email'),
        ]
        for legacy_path, new_path, key_name in migrations:
            if os.path.exists(legacy_path):
                try:
                    legacy_data = load_json_file(legacy_path, [])
                    current_data = load_json_file(new_path, []) if os.path.exists(new_path) else []
                    merged = _merge_records_by_key(current_data, legacy_data, key_name)
                    save_json_file(new_path, merged)
                    os.unlink(legacy_path)
                    print(f'[State Migration] Moved {legacy_path} -> {new_path} and deleted public copy')
                except Exception as e:
                    print(f'[State Migration Error] {legacy_path} -> {new_path}: {e}')
            elif os.path.exists(new_path):
                try:
                    os.chmod(new_path, 0o600)
                except OSError:
                    pass


def is_udid_provisioned_in_current_ipa(udid):
    candidate = (udid or '').strip().lower()
    if not candidate or not UDID_RE.match(candidate):
        return False
    data = load_json_file(PROVISIONED_UDIDS_FILE, {})
    if isinstance(data, dict):
        if data.get('provisions_all_devices') is True:
            return True
        raw_list = data.get('udids') or []
    elif isinstance(data, list):
        raw_list = data
    else:
        return False
    return any(isinstance(item, str) and item.strip().lower() == candidate for item in raw_list)


def sanitize_device_field(value, default='', max_len=64):
    cleaned = CONTROL_CHARS_RE.sub(' ', str(value or '')).strip()
    cleaned = re.sub(r'\s+', ' ', cleaned)
    cleaned = ''.join(ch for ch in cleaned if ch.isprintable())
    if not cleaned:
        return default[:max_len]
    return cleaned[:max_len]


def get_tf_config():
    return load_json_file(TF_CONFIG_FILE, {})


def get_enrollment_secret():
    cfg = get_tf_config()
    seed = (
        os.environ.get('OTA_ENROLL_SECRET')
        or cfg.get('enroll_secret')
        or cfg.get('admin_token')
        or ''
    ).strip()
    if seed:
        return hashlib.sha256(f'netgrey-udid-enroll-v1:{seed}'.encode('utf-8')).digest()
    return BOOT_SECRET


def create_enrollment_token(ttl_seconds=ENROLL_TOKEN_TTL):
    exp = int(time.time()) + int(ttl_seconds)
    nonce = secrets.token_urlsafe(16)
    msg = f'{exp}.{nonce}'.encode('ascii')
    sig = hmac.new(get_enrollment_secret(), msg, hashlib.sha256).hexdigest()
    return f'{exp}.{nonce}.{sig}'


def _prune_enroll_nonces_locked(now):
    while USED_ENROLL_NONCES:
        oldest_nonce, oldest_exp = next(iter(USED_ENROLL_NONCES.items()))
        if oldest_exp < now or len(USED_ENROLL_NONCES) > MAX_ENROLL_NONCES:
            USED_ENROLL_NONCES.popitem(last=False)
        else:
            break


def verify_enrollment_token(token, consume=False):
    raw = (token or '').strip()
    parts = raw.split('.')
    if len(parts) != 3:
        return False
    exp_str, nonce, sig_hex = parts
    if not nonce or len(nonce) > 64 or not re.fullmatch(r'[A-Za-z0-9_\-]+', nonce):
        return False
    if not re.fullmatch(r'[0-9a-f]{64}', sig_hex):
        return False
    try:
        exp = int(exp_str)
    except ValueError:
        return False

    now = int(time.time())
    if now > exp:
        return False

    expected_sig = hmac.new(
        get_enrollment_secret(),
        f'{exp}.{nonce}'.encode('ascii'),
        hashlib.sha256,
    ).hexdigest()
    if not hmac.compare_digest(sig_hex, expected_sig):
        return False

    with ENROLL_NONCE_LOCK:
        _prune_enroll_nonces_locked(now)
        if nonce in USED_ENROLL_NONCES:
            return False
        if consume:
            USED_ENROLL_NONCES[nonce] = exp
            _prune_enroll_nonces_locked(now)
    return True


def build_enrollment_mobileconfig(token):
    callback_url = f'{ALLOWED_ORIGIN}/api/receive-udid?token={urllib.parse.quote(token, safe="")}'
    profile = {
        'PayloadContent': {
            'URL': callback_url,
            'DeviceAttributes': [
                'UDID',
                'VERSION',
                'PRODUCT',
                'DEVICE_NAME',
            ],
        },
        'PayloadOrganization': 'Netgrey',
        'PayloadDisplayName': 'Netgrey 设备授权登记 (UDID)',
        'PayloadVersion': 1,
        'PayloadUUID': '8C7E5B24-6A41-4D93-9F1E-2B5D8E4A1001',
        'PayloadIdentifier': 'com.aurorashelf.app.udid-enroll',
        'PayloadDescription': '此描述文件仅用于一次性读取设备 UDID 以登记开发者测试设备名录，完成后自动移除。',
        'PayloadType': 'Profile Service',
    }
    return plistlib.dumps(profile, fmt=plistlib.FMT_XML)


def get_apple_token():
    if jwt is None or not os.path.exists(CONFIG_FILE):
        return None
    now = int(time.time())
    with APPLE_JWT_LOCK:
        cached = APPLE_JWT_CACHE['token']
        if cached and now + 300 < APPLE_JWT_CACHE['exp']:
            return cached
        try:
            with open(CONFIG_FILE, 'r', encoding='utf-8') as f:
                cfg = json.load(f)
            with open(cfg['private_key_path'], 'r', encoding='utf-8') as f:
                key_data = f.read()

            exp = now + 1200
            payload = {
                'iss': cfg['issuer_id'],
                'exp': exp,
                'aud': 'appstoreconnect-v1',
            }
            headers = {
                'kid': cfg['key_id'],
                'alg': 'ES256',
                'typ': 'JWT',
            }
            token = jwt.encode(payload, key_data, algorithm='ES256', headers=headers)
            APPLE_JWT_CACHE['token'] = token
            APPLE_JWT_CACHE['exp'] = exp
            return token
        except Exception as e:
            print('[Apple API Token Error]:', e)
            return None


def asc_request(method, path, payload=None):
    token = get_apple_token()
    if not token:
        return 500, {'error': 'Apple API token unavailable'}
    if path.startswith('http://') or path.startswith('https://'):
        url = path
    else:
        url = f'https://api.appstoreconnect.apple.com{path}'
    body = json.dumps(payload).encode('utf-8') if payload is not None else None
    headers = {
        'Authorization': f'Bearer {token}',
        'Content-Type': 'application/json',
    }
    req = urllib.request.Request(url, data=body, headers=headers, method=method)
    try:
        with urllib.request.urlopen(req, timeout=20) as resp:
            raw = resp.read().decode('utf-8', errors='ignore')
            return resp.status, (json.loads(raw) if raw else {})
    except urllib.error.HTTPError as e:
        raw = e.read().decode('utf-8', errors='ignore')
        try:
            return e.code, json.loads(raw)
        except Exception:
            return e.code, {'raw': raw}
    except Exception as e:
        return 500, {'error': str(e)}


def asc_list_all_users():
    users = []
    next_path = '/v1/users?limit=200'
    pages = 0
    while next_path and pages < 20:
        pages += 1
        status, data = asc_request('GET', next_path)
        if status != 200 or not isinstance(data, dict):
            return status, data, users
        users.extend(data.get('data', []))
        next_link = (data.get('links') or {}).get('next')
        next_path = (
            next_link
            if (isinstance(next_link, str) and next_link.startswith('https://api.appstoreconnect.apple.com/'))
            else None
        )
    return 200, {}, users


def extract_asc_error(data):
    if isinstance(data, dict):
        errors = data.get('errors')
        if isinstance(errors, list) and errors:
            first = errors[0]
            return first.get('detail') or first.get('title') or str(first)
        return data.get('error') or data.get('raw') or json.dumps(data, ensure_ascii=False)
    return str(data)


def is_rate_limited(client_key, limit=10, window_seconds=600):
    now = time.time()
    with RATE_LIMIT_LOCK:
        history = [ts for ts in RATE_LIMIT_BUCKETS.get(client_key, ()) if now - ts < window_seconds]
        if client_key in RATE_LIMIT_BUCKETS:
            RATE_LIMIT_BUCKETS.move_to_end(client_key)

        if len(history) >= limit:
            RATE_LIMIT_BUCKETS[client_key] = history
            return True

        history.append(now)
        RATE_LIMIT_BUCKETS[client_key] = history
        RATE_LIMIT_BUCKETS.move_to_end(client_key)

        if len(RATE_LIMIT_BUCKETS) > RATE_LIMIT_MAX_BUCKETS:
            stale_keys = [
                k for k, vals in RATE_LIMIT_BUCKETS.items()
                if not any(now - ts < window_seconds for ts in vals)
            ]
            for k in stale_keys:
                RATE_LIMIT_BUCKETS.pop(k, None)
            while len(RATE_LIMIT_BUCKETS) > RATE_LIMIT_MAX_BUCKETS:
                RATE_LIMIT_BUCKETS.popitem(last=False)

        return False


def verify_invite_code(code):
    cfg = get_tf_config()
    codes = cfg.get('invite_codes') or []
    candidate = (code or '').strip()
    if not candidate or not codes:
        return False
    return any(hmac.compare_digest(candidate, str(valid).strip()) for valid in codes)


def check_invite_code_with_global_throttle(code):
    """
    Returns:
      'ok'        -> valid invite code
      'throttled' -> global cross-IP failed invite-code throttle tripped
      'invalid'   -> invalid invite code (recorded in global failure window)
    """
    now = time.time()
    with RATE_LIMIT_LOCK:
        while GLOBAL_FAILED_INVITE_TIMESTAMPS and (now - GLOBAL_FAILED_INVITE_TIMESTAMPS[0]) >= GLOBAL_INVITE_FAIL_WINDOW:
            GLOBAL_FAILED_INVITE_TIMESTAMPS.popleft()
        if len(GLOBAL_FAILED_INVITE_TIMESTAMPS) >= GLOBAL_INVITE_FAIL_LIMIT:
            return 'throttled'

    if verify_invite_code(code):
        return 'ok'

    now = time.time()
    with RATE_LIMIT_LOCK:
        while GLOBAL_FAILED_INVITE_TIMESTAMPS and (now - GLOBAL_FAILED_INVITE_TIMESTAMPS[0]) >= GLOBAL_INVITE_FAIL_WINDOW:
            GLOBAL_FAILED_INVITE_TIMESTAMPS.popleft()
        GLOBAL_FAILED_INVITE_TIMESTAMPS.append(now)
    return 'invalid'


def verify_admin_token(candidate):
    token = (candidate or '').strip()
    if not token:
        return False
    expected = (os.environ.get('OTA_ADMIN_TOKEN') or get_tf_config().get('admin_token') or '').strip()
    if not expected:
        return False
    return hmac.compare_digest(token, expected)


def ensure_user_in_beta_group(email, first_name, last_name, beta_group_id):
    payload = {
        'data': {
            'type': 'betaTesters',
            'attributes': {
                'email': email,
                'firstName': first_name,
                'lastName': last_name,
            },
            'relationships': {
                'betaGroups': {
                    'data': [{'type': 'betaGroups', 'id': beta_group_id}],
                },
            },
        },
    }
    status, data = asc_request('POST', '/v1/betaTesters', payload)
    if status in (200, 201):
        return True, 'added_to_group', '已自动加入 TestFlight Internal 群组，请查收 TestFlight 邮件或打开 TestFlight App 安装。'

    if status == 409:
        q_email = urllib.parse.quote(email)
        q_status, q_data = asc_request('GET', f'/v1/betaTesters?filter[email]={q_email}')
        testers = q_data.get('data', []) if q_status == 200 and isinstance(q_data, dict) else []
        if testers:
            tester_id = testers[0]['id']
            rel_payload = {'data': [{'type': 'betaTesters', 'id': tester_id}]}
            rel_status, rel_data = asc_request(
                'POST',
                f'/v1/betaGroups/{beta_group_id}/relationships/betaTesters',
                rel_payload,
            )
            if rel_status in (200, 201, 204):
                return True, 'added_to_group', '已自动加入 TestFlight Internal 群组，请打开 TestFlight App 查看并安装。'
            return False, 'accepted_pending_group', f'加入群组失败: {extract_asc_error(rel_data)}'

    return False, 'accepted_pending_group', f'添加测试员失败: {extract_asc_error(data)}'


def _build_user_invitation_payload(email, first_name, last_name, app_id):
    return {
        'data': {
            'type': 'userInvitations',
            'attributes': {
                'email': email,
                'firstName': first_name,
                'lastName': last_name,
                'roles': ['MARKETING'],
                'allAppsVisible': False,
                'provisioningAllowed': False,
            },
            'relationships': {
                'visibleApps': {
                    'data': [{'type': 'apps', 'id': app_id}],
                },
            },
        },
    }


def upsert_invite_record(email, status, message, retry_count=None, next_retry_at=None):
    now = datetime.now().strftime('%Y-%m-%d %H:%M:%S')
    with STATE_LOCK:
        records = load_json_file(INVITES_FILE, [])
        existing = next((r for r in records if r.get('email') == email), None)
        if existing:
            existing['status'] = status
            existing['message'] = message
            existing['updated_at'] = now
            if retry_count is not None:
                existing['retry_count'] = int(retry_count)
            if next_retry_at is not None:
                existing['next_retry_at'] = float(next_retry_at)
        else:
            existing = {
                'email': email,
                'status': status,
                'message': message,
                'retry_count': int(retry_count or 0),
                'next_retry_at': float(next_retry_at or 0.0),
                'created_at': now,
                'updated_at': now,
            }
            records.append(existing)
        save_json_file(INVITES_FILE, records)
        return dict(existing)


def get_invite_record(email):
    with STATE_LOCK:
        records = load_json_file(INVITES_FILE, [])
        existing = next((r for r in records if r.get('email') == email), None)
        return dict(existing) if existing else None


def process_testflight_invite(email, resend=False):
    cfg = get_tf_config()
    app_id = cfg.get('app_id')
    beta_group_id = cfg.get('beta_group_id')
    max_users = int(cfg.get('max_internal_users', 100))
    if not app_id or not beta_group_id:
        return 500, {'ok': False, 'error': '服务器未配置 app_id 或 beta_group_id'}

    email = email.strip().lower()
    first_name = 'Tester'
    last_name = re.sub(r'[^A-Za-z0-9_.\-]', '', email.split('@')[0])[:20] or 'User'

    with INVITE_OP_LOCK:
        # 1. 检查当前团队成员（支持分页，同时确认 Apple API 连通性）
        u_status, u_data, users = asc_list_all_users()
        if u_status != 200:
            return 502, {'ok': False, 'error': f'查询团队成员失败: {extract_asc_error(u_data)}'}

        matched_user = next(
            (u for u in users if (u.get('attributes', {}).get('username') or '').strip().lower() == email),
            None,
        )

        if matched_user:
            ok, tf_status, msg = ensure_user_in_beta_group(email, first_name, last_name, beta_group_id)
            rec = upsert_invite_record(email, tf_status, msg, retry_count=0, next_retry_at=0.0)
            return (200 if ok else 502), {'ok': ok, 'record': rec}

        if len(users) >= max_users:
            return 409, {'ok': False, 'error': f'内部测试团队名额已满（{len(users)}/{max_users}）'}

        # 2. 检查是否已在待接受邀请列表；仅在确认 Apple API 正常返回 200 后才允许在重发模式下撤销旧邀请
        q_email = urllib.parse.quote(email)
        inv_status, inv_data = asc_request('GET', f'/v1/userInvitations?filter[email]={q_email}')
        if inv_status != 200 or not isinstance(inv_data, dict):
            return 502, {'ok': False, 'error': f'查询待接受邀请失败: {extract_asc_error(inv_data)}'}

        existing_invs = inv_data.get('data', [])
        deleted_old_invite = False
        if existing_invs:
            if resend:
                for item in existing_invs:
                    inv_id = item.get('id')
                    if inv_id:
                        del_status, del_data = asc_request('DELETE', f'/v1/userInvitations/{inv_id}')
                        if del_status not in (200, 204, 404):
                            return 502, {'ok': False, 'error': f'撤销旧邀请失败: {extract_asc_error(del_data)}'}
                        deleted_old_invite = True
            else:
                rec = upsert_invite_record(
                    email,
                    'invited',
                    'Apple 团队邀请已处于待接受状态。若未在收件箱看到（163/QQ 邮箱较易拦截），请检查「垃圾邮件/广告箱」，或点击下方「重发团队邀请邮件」。',
                    retry_count=0,
                    next_retry_at=0.0,
                )
                return 200, {'ok': True, 'record': rec}

        # 3. 发送 App Store Connect 团队邀请（最小 MARKETING 权限，仅限 Netgrey）
        invite_payload = _build_user_invitation_payload(email, first_name, last_name, app_id)
        post_status, post_data = asc_request('POST', '/v1/userInvitations', invite_payload)
        if post_status in (200, 201, 409):
            action_word = '重新发送' if resend else '发送'
            rec = upsert_invite_record(
                email,
                'invited',
                f'第 1 步团队邀请邮件已{action_word}（发件人 no_reply@email.apple.com）。请查收邮箱并检查「垃圾邮件/广告箱」，点击「Accept invitation」确认后系统会自动拉群。',
                retry_count=0,
                next_retry_at=0.0,
            )
            return 200, {'ok': True, 'record': rec}

        err_msg = extract_asc_error(post_data)
        if resend or deleted_old_invite:
            rec = upsert_invite_record(
                email,
                'retry_invite',
                f'重发团队邀请暂未完成（{err_msg}），后台将自动重试，也可稍后再次点击重发。',
                retry_count=1,
                next_retry_at=time.time() + 20.0,
            )
            return 502, {'ok': False, 'error': err_msg, 'record': rec}

        rec = upsert_invite_record(email, 'error', f'发送邀请失败: {err_msg}', retry_count=0, next_retry_at=0.0)
        return 502, {'ok': False, 'error': err_msg, 'record': rec}


def sync_pending_invites_once(force=False):
    global LAST_SYNC_AT
    with SYNC_LOCK:
        now = time.time()
        if not force and (now - LAST_SYNC_AT) < 10.0:
            return
        cfg = get_tf_config()
        app_id = cfg.get('app_id')
        beta_group_id = cfg.get('beta_group_id')
        if not beta_group_id:
            return

        with STATE_LOCK:
            records = load_json_file(INVITES_FILE, [])
            pending = [
                dict(r) for r in records
                if r.get('status') in ('invited', 'accepted_pending_group', 'retry_invite')
                and int(r.get('retry_count', 0)) <= MAX_POLLER_RETRIES
                and now >= float(r.get('next_retry_at', 0.0))
            ]

        if not pending:
            LAST_SYNC_AT = time.time()
            return

        u_status, _, users = asc_list_all_users()
        if u_status != 200:
            return

        usernames = {
            (u.get('attributes', {}).get('username') or '').strip().lower()
            for u in users
        }

        for entry in pending:
            email = (entry.get('email') or '').strip().lower()
            if not email:
                continue
            last_name = re.sub(r'[^A-Za-z0-9_.\-]', '', email.split('@')[0])[:20] or 'User'
            prev_retries = int(entry.get('retry_count', 0))

            if email in usernames:
                ok, tf_status, msg = ensure_user_in_beta_group(email, 'Tester', last_name, beta_group_id)
                if ok:
                    upsert_invite_record(email, tf_status, msg, retry_count=0, next_retry_at=0.0)
                else:
                    next_count = prev_retries + 1
                    backoff = min(20.0 * (2 ** min(next_count - 1, 5)), 600.0)
                    if next_count > MAX_POLLER_RETRIES:
                        upsert_invite_record(
                            email,
                            'error',
                            f'{msg}（已达自动重试上限，请检查账号状态或重新提交）',
                            retry_count=next_count,
                            next_retry_at=0.0,
                        )
                    else:
                        upsert_invite_record(
                            email,
                            tf_status,
                            msg,
                            retry_count=next_count,
                            next_retry_at=time.time() + backoff,
                        )
                print(f'[TestFlight Poller] {email} -> {tf_status} (ok={ok}, retries={prev_retries})')
            elif entry.get('status') == 'retry_invite' and app_id:
                invite_payload = _build_user_invitation_payload(email, 'Tester', last_name, app_id)
                post_status, post_data = asc_request('POST', '/v1/userInvitations', invite_payload)
                if post_status in (200, 201, 409):
                    upsert_invite_record(
                        email,
                        'invited',
                        '团队邀请邮件已自动补发成功（发件人 no_reply@email.apple.com），请查收邮箱并点击「Accept invitation」。',
                        retry_count=0,
                        next_retry_at=0.0,
                    )
                    print(f'[TestFlight Poller] {email} -> retry_invite succeeded')
                else:
                    err_msg = extract_asc_error(post_data)
                    next_count = prev_retries + 1
                    backoff = min(20.0 * (2 ** min(next_count - 1, 5)), 600.0)
                    if next_count > MAX_POLLER_RETRIES:
                        upsert_invite_record(
                            email,
                            'error',
                            f'自动重发邀请超过上限: {err_msg}',
                            retry_count=next_count,
                            next_retry_at=0.0,
                        )
                    else:
                        upsert_invite_record(
                            email,
                            'retry_invite',
                            f'后台重试发送团队邀请未完成（{err_msg}），将继续按退避策略重试。',
                            retry_count=next_count,
                            next_retry_at=time.time() + backoff,
                        )

        LAST_SYNC_AT = time.time()


def testflight_poller_loop():
    while True:
        try:
            sync_pending_invites_once(force=True)
        except Exception as e:
            print('[TestFlight Poller Error]:', e)
        time.sleep(20)


def register_device_with_apple(udid, device_name):
    token = get_apple_token()
    if not token:
        print('[Apple API] Skipped: No token available')
        return False

    url = 'https://api.appstoreconnect.apple.com/v1/devices'
    safe_name = sanitize_device_field(device_name, default='iPhone', max_len=64)
    data = {
        'data': {
            'type': 'devices',
            'attributes': {
                'name': safe_name,
                'platform': 'IOS',
                'udid': udid,
            },
        },
    }
    req = urllib.request.Request(
        url,
        data=json.dumps(data).encode('utf-8'),
        headers={
            'Authorization': f'Bearer {token}',
            'Content-Type': 'application/json',
        },
        method='POST',
    )
    try:
        with urllib.request.urlopen(req, timeout=20) as resp:
            if resp.status in (200, 201):
                print(f'[Apple API] SUCCESS: Registered device {udid} ({safe_name}) to Apple Developer Portal')
                return True
    except urllib.error.HTTPError as e:
        if e.code == 409:
            print(f'[Apple API] ALREADY REGISTERED: Device {udid} is already in Apple Developer Portal')
            return True
        err = e.read().decode('utf-8', errors='ignore')
        print(f'[Apple API Error] Code {e.code}: {err}')
    except Exception as e:
        print('[Apple API Exception]:', e)
    return False


def load_devices():
    with STATE_LOCK:
        return list(load_json_file(DEVICES_FILE, []))


def save_device(udid, product, name, version, apple_registered=False):
    safe_product = sanitize_device_field(product, default='iPhone', max_len=64)
    safe_name = sanitize_device_field(name, default='iOS Device', max_len=64)
    safe_version = sanitize_device_field(version, default='', max_len=64)
    with STATE_LOCK:
        devices = load_json_file(DEVICES_FILE, [])
        now = datetime.now().strftime('%Y-%m-%d %H:%M:%S')
        existing = next((d for d in devices if d.get('udid') == udid), None)
        if existing:
            existing['last_seen'] = now
            existing['product'] = safe_product or existing.get('product', '')
            existing['device_name'] = safe_name or existing.get('device_name', '')
            existing['version'] = safe_version or existing.get('version', '')
            if apple_registered:
                existing['apple_registered'] = True
        else:
            devices.append({
                'udid': udid,
                'product': safe_product,
                'device_name': safe_name,
                'version': safe_version,
                'apple_registered': apple_registered,
                'registered_at': now,
                'last_seen': now,
            })
        save_json_file(DEVICES_FILE, devices)


def _fields_from_plist_dict(parsed):
    raw_udid = str(parsed.get('UDID') or '').strip()
    udid = raw_udid if UDID_RE.match(raw_udid) else ''
    return (
        udid,
        sanitize_device_field(parsed.get('PRODUCT'), default='iPhone', max_len=64),
        sanitize_device_field(parsed.get('DEVICE_NAME'), default='iOS Device', max_len=64),
        sanitize_device_field(parsed.get('VERSION'), default='', max_len=64),
    )


def extract_plist_fields(raw_body):
    bounded = (raw_body or b'')[:262144]
    if not bounded:
        return '', 'iPhone', 'iOS Device', ''

    stripped = bounded.lstrip()
    if stripped.startswith((b'<?xml', b'<plist', b'<!DOCTYPE')):
        try:
            parsed = plistlib.loads(stripped)
            if isinstance(parsed, dict):
                return _fields_from_plist_dict(parsed)
        except Exception:
            pass

    # iOS Profile Service 回传标准 DER CMS/PKCS#7 签名包：优先使用 openssl cms 提取内部 plist
    try:
        proc = subprocess.run(
            ['openssl', 'cms', '-verify', '-inform', 'DER', '-noverify'],
            input=bounded,
            capture_output=True,
            timeout=5,
            check=False,
        )
        if proc.returncode == 0 and proc.stdout:
            parsed = plistlib.loads(proc.stdout)
            if isinstance(parsed, dict):
                return _fields_from_plist_dict(parsed)
    except Exception:
        pass

    # 有界 XML 切片兜底（避免在任意二进制包上运行回溯正则）
    plist_start = bounded.find(b'<plist')
    plist_end = bounded.find(b'</plist>', plist_start if plist_start >= 0 else 0)
    if 0 <= plist_start < plist_end and (plist_end - plist_start) <= 65536:
        xml_slice = bounded[plist_start:plist_end + len(b'</plist>')]
        try:
            parsed = plistlib.loads(xml_slice)
            if isinstance(parsed, dict):
                return _fields_from_plist_dict(parsed)
        except Exception:
            pass

    body_text = bounded[:65536].decode('utf-8', errors='ignore')
    udid_match = re.search(r'<key>UDID</key>\s*<string>([^<]{1,64})</string>', body_text)
    product_match = re.search(r'<key>PRODUCT</key>\s*<string>([^<]{1,128})</string>', body_text)
    name_match = re.search(r'<key>DEVICE_NAME</key>\s*<string>([^<]{1,128})</string>', body_text)
    version_match = re.search(r'<key>VERSION</key>\s*<string>([^<]{1,64})</string>', body_text)
    raw_udid = udid_match.group(1).strip() if udid_match else ''
    return (
        raw_udid if UDID_RE.match(raw_udid) else '',
        sanitize_device_field(product_match.group(1) if product_match else '', default='iPhone', max_len=64),
        sanitize_device_field(name_match.group(1) if name_match else '', default='iOS Device', max_len=64),
        sanitize_device_field(version_match.group(1) if version_match else '', default='', max_len=64),
    )


class OTAHandler(http.server.BaseHTTPRequestHandler):
    def setup(self):
        super().setup()
        self.request.settimeout(30)

    def get_client_ip(self):
        peer_ip = self.client_address[0]
        if peer_ip in ('127.0.0.1', '::1'):
            real_ip = (self.headers.get('X-Real-IP') or '').strip()
            if real_ip:
                return real_ip
        return peer_ip

    def read_bounded_body(self, max_bytes):
        raw_len = self.headers.get('Content-Length')
        if raw_len is None:
            return 200, b''
        try:
            content_length = int(raw_len)
        except ValueError:
            return 400, None
        if content_length < 0:
            return 400, None
        if content_length > max_bytes:
            return 413, None
        if content_length == 0:
            return 200, b''
        return 200, self.rfile.read(content_length)

    def send_json(self, status_code, payload):
        body = json.dumps(payload, ensure_ascii=False).encode('utf-8')
        self.send_response(status_code)
        self.send_header('Content-Type', 'application/json; charset=utf-8')
        self.send_header('Cache-Control', 'no-store')
        self.send_header('Access-Control-Allow-Origin', ALLOWED_ORIGIN)
        self.send_header('Vary', 'Origin')
        self.send_header('Content-Length', str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def do_OPTIONS(self):
        self.send_response(204)
        self.send_header('Access-Control-Allow-Origin', ALLOWED_ORIGIN)
        self.send_header('Access-Control-Allow-Methods', 'GET, POST, OPTIONS')
        self.send_header('Access-Control-Allow-Headers', 'Content-Type, X-Admin-Token')
        self.send_header('Vary', 'Origin')
        self.end_headers()

    def do_POST(self):
        parsed = urllib.parse.urlparse(self.path)
        client_ip = self.get_client_ip()

        if parsed.path == '/api/udid/enroll':
            if is_rate_limited(f'udid_enroll:{client_ip}', limit=10, window_seconds=600):
                self.send_json(429, {'ok': False, 'error': '请求过于频繁，请 10 分钟后再试'})
                return

            status, raw_body = self.read_bounded_body(8192)
            if status == 413:
                self.send_json(413, {'ok': False, 'error': '请求体过大'})
                return
            if status != 200 or raw_body is None:
                self.send_json(400, {'ok': False, 'error': '无效的 Content-Length'})
                return

            try:
                body = json.loads(raw_body.decode('utf-8'))
            except Exception:
                self.send_json(400, {'ok': False, 'error': '请求体必须是有效的 JSON'})
                return

            invite_code = (body.get('invite_code') or '').strip() if isinstance(body, dict) else ''
            verdict = check_invite_code_with_global_throttle(invite_code)
            if verdict == 'throttled':
                self.send_json(429, {'ok': False, 'error': '口令错误次数过多，系统已触发全局冷却，请 10 分钟后再试'})
                return
            if verdict != 'ok':
                self.send_json(403, {'ok': False, 'error': '邀请口令无效，请向管理员索取'})
                return

            enroll_token = create_enrollment_token(ttl_seconds=ENROLL_TOKEN_TTL)
            mobileconfig_url = f'/api/udid/enroll.mobileconfig?token={urllib.parse.quote(enroll_token, safe="")}'
            self.send_json(200, {
                'ok': True,
                'mobileconfig_url': mobileconfig_url,
                'expires_in': ENROLL_TOKEN_TTL,
            })
            return

        if parsed.path == '/api/receive-udid':
            if is_rate_limited(f'udid:{client_ip}', limit=15, window_seconds=600):
                self.send_json(429, {'ok': False, 'error': '请求过于频繁，请稍后再试'})
                return

            qs = urllib.parse.parse_qs(parsed.query)
            enroll_token = (qs.get('token', [''])[0] or '').strip()
            if not verify_enrollment_token(enroll_token, consume=True):
                self.send_json(403, {'ok': False, 'error': '设备授权令牌无效或已过期，请返回网页重新输入口令获取描述文件'})
                return

            status, raw_body = self.read_bounded_body(262144)
            if status == 413:
                self.send_json(413, {'ok': False, 'error': 'Payload too large'})
                return
            if status != 200 or raw_body is None:
                self.send_json(400, {'ok': False, 'error': 'Invalid Content-Length'})
                return

            udid, product, device_name, version = extract_plist_fields(raw_body)
            apple_reg = False
            provisioned = False
            if udid and UDID_RE.match(udid):
                apple_reg = register_device_with_apple(udid, device_name)
                save_device(udid, product, device_name, version, apple_registered=apple_reg)
                provisioned = is_udid_provisioned_in_current_ipa(udid)
            else:
                udid = ''

            redirect_params = urllib.parse.urlencode({
                'udid': udid,
                'name': sanitize_device_field(device_name, default='iOS Device', max_len=64),
                'model': sanitize_device_field(product, default='iPhone', max_len=64),
                'registered': 'true' if apple_reg else 'false',
                'provisioned': '1' if provisioned else '0',
            })
            redirect_url = f'{ALLOWED_ORIGIN}/?{redirect_params}'

            self.send_response(301)
            self.send_header('Location', redirect_url)
            self.send_header('Cache-Control', 'no-store')
            self.end_headers()
            return

        if parsed.path == '/api/testflight/invite':
            if is_rate_limited(f'tf_invite:{client_ip}', limit=10, window_seconds=600):
                self.send_json(429, {'ok': False, 'error': '请求过于频繁，请 10 分钟后再试'})
                return

            status, raw_body = self.read_bounded_body(16384)
            if status == 413:
                self.send_json(413, {'ok': False, 'error': '请求体过大'})
                return
            if status != 200 or raw_body is None:
                self.send_json(400, {'ok': False, 'error': '无效的 Content-Length'})
                return

            try:
                body = json.loads(raw_body.decode('utf-8'))
            except Exception:
                self.send_json(400, {'ok': False, 'error': '请求体必须是有效的 JSON'})
                return

            if not isinstance(body, dict):
                self.send_json(400, {'ok': False, 'error': '请求体必须是 JSON 对象'})
                return

            email = (body.get('email') or '').strip().lower()
            invite_code = (body.get('invite_code') or '').strip()
            resend = bool(body.get('resend', False))

            verdict = check_invite_code_with_global_throttle(invite_code)
            if verdict == 'throttled':
                self.send_json(429, {'ok': False, 'error': '口令错误次数过多，系统已触发全局冷却，请 10 分钟后再试'})
                return
            if verdict != 'ok':
                self.send_json(403, {'ok': False, 'error': '邀请口令无效，请向管理员索取'})
                return

            if not EMAIL_RE.match(email):
                self.send_json(400, {'ok': False, 'error': '请输入有效的 Apple ID 邮箱地址'})
                return

            code, result = process_testflight_invite(email, resend=resend)
            self.send_json(code, result)
            return

        self.send_response(404)
        self.end_headers()

    def do_GET(self):
        parsed = urllib.parse.urlparse(self.path)
        client_ip = self.get_client_ip()

        if parsed.path == '/api/udid/enroll.mobileconfig':
            if is_rate_limited(f'udid_mobileconfig:{client_ip}', limit=20, window_seconds=600):
                self.send_json(429, {'ok': False, 'error': '请求过于频繁，请稍后再试'})
                return
            qs = urllib.parse.parse_qs(parsed.query)
            enroll_token = (qs.get('token', [''])[0] or '').strip()
            if not verify_enrollment_token(enroll_token, consume=False):
                self.send_json(403, {'ok': False, 'error': '描述文件链接已失效或未授权，请返回网页输入邀请口令重新获取'})
                return
            body = build_enrollment_mobileconfig(enroll_token)
            self.send_response(200)
            self.send_header('Content-Type', 'application/x-apple-aspen-config')
            self.send_header('Content-Disposition', 'attachment; filename="Netgrey-UDID-Enroll.mobileconfig"')
            self.send_header('Cache-Control', 'no-store')
            self.send_header('Content-Length', str(len(body)))
            self.end_headers()
            self.wfile.write(body)
            return

        if parsed.path == '/api/udid/status':
            if is_rate_limited(f'udid_status:{client_ip}', limit=60, window_seconds=300):
                self.send_json(429, {'ok': False, 'error': '查询过于频繁，请稍后再试'})
                return
            qs = urllib.parse.parse_qs(parsed.query)
            udid = (qs.get('udid', [''])[0] or '').strip()
            if not UDID_RE.match(udid):
                self.send_json(400, {'ok': False, 'error': 'UDID 格式无效'})
                return
            self.send_json(200, {
                'ok': True,
                'udid': udid,
                'provisioned': is_udid_provisioned_in_current_ipa(udid),
            })
            return

        if parsed.path == '/api/devices':
            if not verify_admin_token(self.headers.get('X-Admin-Token')):
                self.send_json(403, {'ok': False, 'error': 'Forbidden'})
                return
            self.send_json(200, load_devices())
            return

        if parsed.path == '/api/latest-udid':
            if not verify_admin_token(self.headers.get('X-Admin-Token')):
                self.send_json(403, {'ok': False, 'error': 'Forbidden'})
                return
            devices = load_devices()
            self.send_json(200, devices[-1] if devices else {})
            return

        if parsed.path == '/api/testflight/status':
            if is_rate_limited(f'tf_status:{client_ip}', limit=30, window_seconds=300):
                self.send_json(429, {'ok': False, 'error': '查询过于频繁，请稍后再试'})
                return
            qs = urllib.parse.parse_qs(parsed.query)
            email = (qs.get('email', [''])[0] or '').strip().lower()
            invite_code = (qs.get('invite_code', [''])[0] or '').strip()
            verdict = check_invite_code_with_global_throttle(invite_code)
            if verdict == 'throttled':
                self.send_json(429, {'ok': False, 'error': '口令错误次数过多，系统已触发全局冷却，请 10 分钟后再试'})
                return
            if verdict != 'ok':
                self.send_json(403, {'ok': False, 'error': '邀请口令无效'})
                return
            if not EMAIL_RE.match(email):
                self.send_json(400, {'ok': False, 'error': '邮箱格式无效'})
                return
            rec = get_invite_record(email)
            if not rec:
                self.send_json(404, {'ok': False, 'error': '未找到该邮箱的邀请记录'})
                return
            if rec.get('status') in ('invited', 'accepted_pending_group', 'retry_invite'):
                try:
                    sync_pending_invites_once(force=False)
                except Exception as e:
                    print('[TestFlight On-Demand Sync Error]:', e)
                rec = get_invite_record(email) or rec
            self.send_json(200, {'ok': True, 'record': rec})
            return

        self.send_response(404)
        self.end_headers()


if __name__ == '__main__':
    migrate_legacy_state_files()
    poller = threading.Thread(target=testflight_poller_loop, daemon=True)
    poller.start()
    with BoundedThreadingTCPServer(('127.0.0.1', PORT), OTAHandler, max_workers=16) as httpd:
        print(f'OTA & TestFlight Helper Server running on 127.0.0.1:{PORT}')
        httpd.serve_forever()

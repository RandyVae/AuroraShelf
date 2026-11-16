import http.server
import socketserver
import re
import json
import os
import time
import hmac
import plistlib
import tempfile
import threading
import urllib.parse
import urllib.request
import urllib.error
from datetime import datetime
import jwt

PORT = 5000
DEVICES_FILE = '/var/www/netgrey/registered_devices.json'
INVITES_FILE = '/var/www/netgrey/testflight_invites.json'
CONFIG_FILE = '/etc/netgrey-ota/api_config.json'
TF_CONFIG_FILE = '/etc/netgrey-ota/testflight_invite_config.json'
ALLOWED_ORIGIN = 'https://bananarey.us'

EMAIL_RE = re.compile(r'^[A-Za-z0-9._%+\-]+@[A-Za-z0-9.\-]+\.[A-Za-z]{2,}$')
UDID_RE = re.compile(r'^(?:[0-9A-Fa-f]{40}|[0-9A-Fa-f]{8}-[0-9A-Fa-f]{16})$')
STATE_LOCK = threading.Lock()
SYNC_LOCK = threading.Lock()
LAST_SYNC_AT = 0.0
RATE_LIMIT_LOCK = threading.Lock()
RATE_LIMIT_BUCKETS = {}
RATE_LIMIT_MAX_BUCKETS = 4096
APPLE_JWT_LOCK = threading.Lock()
APPLE_JWT_CACHE = {'token': None, 'exp': 0}


class ThreadingTCPServer(socketserver.ThreadingMixIn, socketserver.TCPServer):
    allow_reuse_address = True
    daemon_threads = True
    request_queue_size = 64


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
    fd, tmp_path = tempfile.mkstemp(prefix='.tmp_', suffix='.json', dir=target_dir)
    try:
        with os.fdopen(fd, 'w', encoding='utf-8') as f:
            json.dump(data, f, ensure_ascii=False, indent=2)
        os.chmod(tmp_path, 0o644)
        os.replace(tmp_path, path)
    except Exception:
        if os.path.exists(tmp_path):
            os.unlink(tmp_path)
        raise


def get_apple_token():
    if not os.path.exists(CONFIG_FILE):
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
                'aud': 'appstoreconnect-v1'
            }
            headers = {
                'kid': cfg['key_id'],
                'alg': 'ES256',
                'typ': 'JWT'
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
        'Content-Type': 'application/json'
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
        next_path = next_link if (isinstance(next_link, str) and next_link.startswith('https://api.appstoreconnect.apple.com/')) else None
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
        # 周期性清理过期桶，防止恶意请求导致内存无限增长
        if len(RATE_LIMIT_BUCKETS) > RATE_LIMIT_MAX_BUCKETS:
            stale = [
                k for k, vals in RATE_LIMIT_BUCKETS.items()
                if not any(now - ts < window_seconds for ts in vals)
            ]
            for k in stale:
                del RATE_LIMIT_BUCKETS[k]
            if len(RATE_LIMIT_BUCKETS) > RATE_LIMIT_MAX_BUCKETS:
                for k in list(RATE_LIMIT_BUCKETS.keys())[:len(RATE_LIMIT_BUCKETS) // 2]:
                    del RATE_LIMIT_BUCKETS[k]

        history = [ts for ts in RATE_LIMIT_BUCKETS.get(client_key, []) if now - ts < window_seconds]
        if len(history) >= limit:
            RATE_LIMIT_BUCKETS[client_key] = history
            return True
        history.append(now)
        RATE_LIMIT_BUCKETS[client_key] = history
        return False


def get_tf_config():
    return load_json_file(TF_CONFIG_FILE, {})


def verify_invite_code(code):
    cfg = get_tf_config()
    codes = cfg.get('invite_codes') or []
    candidate = (code or '').strip()
    if not candidate or not codes:
        return False
    return any(hmac.compare_digest(candidate, str(valid).strip()) for valid in codes)


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
                'lastName': last_name
            },
            'relationships': {
                'betaGroups': {
                    'data': [{'type': 'betaGroups', 'id': beta_group_id}]
                }
            }
        }
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
                rel_payload
            )
            if rel_status in (200, 201, 204):
                return True, 'added_to_group', '已自动加入 TestFlight Internal 群组，请打开 TestFlight App 查看并安装。'
            return False, 'accepted_pending_group', f'加入群组失败: {extract_asc_error(rel_data)}'

    return False, 'accepted_pending_group', f'添加测试员失败: {extract_asc_error(data)}'


def upsert_invite_record(email, status, message):
    now = datetime.now().strftime('%Y-%m-%d %H:%M:%S')
    with STATE_LOCK:
        records = load_json_file(INVITES_FILE, [])
        existing = next((r for r in records if r.get('email') == email), None)
        if existing:
            existing['status'] = status
            existing['message'] = message
            existing['updated_at'] = now
        else:
            existing = {
                'email': email,
                'status': status,
                'message': message,
                'created_at': now,
                'updated_at': now
            }
            records.append(existing)
        save_json_file(INVITES_FILE, records)
        return dict(existing)


def get_invite_record(email):
    with STATE_LOCK:
        records = load_json_file(INVITES_FILE, [])
        existing = next((r for r in records if r.get('email') == email), None)
        return dict(existing) if existing else None


def process_testflight_invite(email):
    cfg = get_tf_config()
    app_id = cfg.get('app_id')
    beta_group_id = cfg.get('beta_group_id')
    max_users = int(cfg.get('max_internal_users', 100))
    if not app_id or not beta_group_id:
        return 500, {'ok': False, 'error': '服务器未配置 app_id 或 beta_group_id'}

    email = email.strip().lower()
    first_name = 'Tester'
    last_name = re.sub(r'[^A-Za-z0-9_.\-]', '', email.split('@')[0])[:20] or 'User'

    # 1. 检查当前团队成员（支持分页，判断是否已接受邀请或满员）
    u_status, u_data, users = asc_list_all_users()
    if u_status != 200:
        return 502, {'ok': False, 'error': f'查询团队成员失败: {extract_asc_error(u_data)}'}

    matched_user = next(
        (u for u in users if (u.get('attributes', {}).get('username') or '').strip().lower() == email),
        None
    )

    if matched_user:
        ok, tf_status, msg = ensure_user_in_beta_group(email, first_name, last_name, beta_group_id)
        rec = upsert_invite_record(email, tf_status, msg)
        return (200 if ok else 502), {'ok': ok, 'record': rec}

    if len(users) >= max_users:
        return 409, {'ok': False, 'error': f'内部测试团队名额已满（{len(users)}/{max_users}）'}

    # 2. 检查是否已在待接受邀请列表
    q_email = urllib.parse.quote(email)
    inv_status, inv_data = asc_request('GET', f'/v1/userInvitations?filter[email]={q_email}')
    if inv_status == 200 and inv_data.get('data'):
        rec = upsert_invite_record(
            email,
            'invited',
            '团队邀请已发出，请前往邮箱点击「Accept invitation」，系统确认后将自动拉入 TestFlight 群组。'
        )
        return 200, {'ok': True, 'record': rec}

    # 3. 发送 App Store Connect 团队邀请（最小 MARKETING 权限，仅限 Netgrey）
    invite_payload = {
        'data': {
            'type': 'userInvitations',
            'attributes': {
                'email': email,
                'firstName': first_name,
                'lastName': last_name,
                'roles': ['MARKETING'],
                'allAppsVisible': False,
                'provisioningAllowed': False
            },
            'relationships': {
                'visibleApps': {
                    'data': [{'type': 'apps', 'id': app_id}]
                }
            }
        }
    }
    post_status, post_data = asc_request('POST', '/v1/userInvitations', invite_payload)
    if post_status in (200, 201, 409):
        rec = upsert_invite_record(
            email,
            'invited',
            '第 1 步团队邀请邮件已发送，请打开邮箱点击「Accept invitation」并用 Apple ID 登录确认；系统会自动拉你进 TestFlight 群组。'
        )
        return 200, {'ok': True, 'record': rec}

    err_msg = extract_asc_error(post_data)
    rec = upsert_invite_record(email, 'error', f'发送邀请失败: {err_msg}')
    return 502, {'ok': False, 'error': err_msg, 'record': rec}


def sync_pending_invites_once(force=False):
    global LAST_SYNC_AT
    with SYNC_LOCK:
        now = time.time()
        if not force and (now - LAST_SYNC_AT) < 10.0:
            return
        cfg = get_tf_config()
        beta_group_id = cfg.get('beta_group_id')
        if not beta_group_id:
            return

        with STATE_LOCK:
            records = load_json_file(INVITES_FILE, [])
            pending = [r for r in records if r.get('status') in ('invited', 'accepted_pending_group')]

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
            if email in usernames:
                last_name = re.sub(r'[^A-Za-z0-9_.\-]', '', email.split('@')[0])[:20] or 'User'
                ok, tf_status, msg = ensure_user_in_beta_group(email, 'Tester', last_name, beta_group_id)
                upsert_invite_record(email, tf_status, msg)
                print(f'[TestFlight Poller] {email} -> {tf_status} (ok={ok})')

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
    data = {
        'data': {
            'type': 'devices',
            'attributes': {
                'name': device_name or 'iPhone',
                'platform': 'IOS',
                'udid': udid
            }
        }
    }
    req = urllib.request.Request(
        url,
        data=json.dumps(data).encode('utf-8'),
        headers={
            'Authorization': f'Bearer {token}',
            'Content-Type': 'application/json'
        },
        method='POST'
    )
    try:
        with urllib.request.urlopen(req, timeout=20) as resp:
            if resp.status in (200, 201):
                print(f'[Apple API] SUCCESS: Registered device {udid} ({device_name}) to Apple Developer Portal')
                return True
    except urllib.error.HTTPError as e:
        if e.code == 409:
            print(f'[Apple API] ALREADY REGISTERED: Device {udid} is already in Apple Developer Portal')
            return True
        else:
            err = e.read().decode('utf-8', errors='ignore')
            print(f'[Apple API Error] Code {e.code}: {err}')
    except Exception as e:
        print('[Apple API Exception]:', e)
    return False


def load_devices():
    with STATE_LOCK:
        return list(load_json_file(DEVICES_FILE, []))


def save_device(udid, product, name, version, apple_registered=False):
    with STATE_LOCK:
        devices = load_json_file(DEVICES_FILE, [])
        now = datetime.now().strftime('%Y-%m-%d %H:%M:%S')
        existing = next((d for d in devices if d['udid'] == udid), None)
        if existing:
            existing['last_seen'] = now
            existing['product'] = product or existing.get('product', '')
            existing['device_name'] = name or existing.get('device_name', '')
            existing['version'] = version or existing.get('version', '')
            if apple_registered:
                existing['apple_registered'] = True
        else:
            devices.append({
                'udid': udid,
                'product': product,
                'device_name': name,
                'version': version,
                'apple_registered': apple_registered,
                'registered_at': now,
                'last_seen': now
            })
        save_json_file(DEVICES_FILE, devices)


def extract_plist_fields(raw_body):
    # iOS MDM/ota mobileconfig 回传为 CMS 签名包，内部包含 <plist>...</plist> 明文 XML
    xml_match = re.search(rb'<\?xml[\s\S]*?</plist>|<plist[\s\S]*?</plist>', raw_body)
    if xml_match:
        try:
            parsed = plistlib.loads(xml_match.group(0))
            if isinstance(parsed, dict):
                return (
                    str(parsed.get('UDID') or '').strip(),
                    str(parsed.get('PRODUCT') or 'iPhone').strip(),
                    str(parsed.get('DEVICE_NAME') or 'iOS Device').strip(),
                    str(parsed.get('VERSION') or '').strip(),
                )
        except Exception:
            pass
    body_text = raw_body.decode('utf-8', errors='ignore')
    udid_match = re.search(r'<key>UDID</key>\s*<string>([^<]+)</string>', body_text)
    product_match = re.search(r'<key>PRODUCT</key>\s*<string>([^<]+)</string>', body_text)
    name_match = re.search(r'<key>DEVICE_NAME</key>\s*<string>([^<]+)</string>', body_text)
    version_match = re.search(r'<key>VERSION</key>\s*<string>([^<]+)</string>', body_text)
    return (
        udid_match.group(1).strip() if udid_match else '',
        product_match.group(1).strip() if product_match else 'iPhone',
        name_match.group(1).strip() if name_match else 'iOS Device',
        version_match.group(1).strip() if version_match else '',
    )


class OTAHandler(http.server.BaseHTTPRequestHandler):
    def setup(self):
        super().setup()
        self.request.settimeout(25)

    def get_client_ip(self):
        real_ip = (self.headers.get('X-Real-IP') or '').strip()
        if real_ip:
            return real_ip
        forwarded = self.headers.get('X-Forwarded-For', '')
        if forwarded:
            return forwarded.split(',')[-1].strip()
        return self.client_address[0]

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

        if parsed.path == '/api/receive-udid':
            if is_rate_limited(f'udid:{client_ip}', limit=15, window_seconds=600):
                self.send_json(429, {'ok': False, 'error': '请求过于频繁，请稍后再试'})
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
            if udid and UDID_RE.match(udid):
                apple_reg = register_device_with_apple(udid, device_name)
                save_device(udid, product, device_name, version, apple_registered=apple_reg)
            else:
                udid = ''

            redirect_params = urllib.parse.urlencode({
                'udid': udid,
                'name': device_name,
                'model': product,
                'registered': 'true' if apple_reg else 'false'
            })
            redirect_url = f'https://bananarey.us/?{redirect_params}'

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

            email = (body.get('email') or '').strip().lower()
            invite_code = (body.get('invite_code') or '').strip()

            if not verify_invite_code(invite_code):
                self.send_json(403, {'ok': False, 'error': '邀请口令无效，请向管理员索取'})
                return

            if not EMAIL_RE.match(email):
                self.send_json(400, {'ok': False, 'error': '请输入有效的 Apple ID 邮箱地址'})
                return

            code, result = process_testflight_invite(email)
            self.send_json(code, result)
            return

        self.send_response(404)
        self.end_headers()

    def do_GET(self):
        parsed = urllib.parse.urlparse(self.path)
        client_ip = self.get_client_ip()

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
            if not verify_invite_code(invite_code):
                self.send_json(403, {'ok': False, 'error': '邀请口令无效'})
                return
            if not EMAIL_RE.match(email):
                self.send_json(400, {'ok': False, 'error': '邮箱格式无效'})
                return
            rec = get_invite_record(email)
            if not rec:
                self.send_json(404, {'ok': False, 'error': '未找到该邮箱的邀请记录'})
                return
            # 仅当当前邮箱仍处于待确认状态时才尝试同步（受 SYNC_LOCK 与 10 秒冷却保护）
            if rec.get('status') in ('invited', 'accepted_pending_group'):
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
    poller = threading.Thread(target=testflight_poller_loop, daemon=True)
    poller.start()
    with ThreadingTCPServer(('127.0.0.1', PORT), OTAHandler) as httpd:
        print(f'OTA & TestFlight Helper Server running on 127.0.0.1:{PORT}')
        httpd.serve_forever()

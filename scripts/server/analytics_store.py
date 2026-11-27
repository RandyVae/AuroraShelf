import csv
import hashlib
import hmac
import io
import json
import os
import plistlib
import re
import secrets
import sqlite3
import tempfile
import threading
import time
from datetime import datetime, timedelta, timezone

HKT = timezone(timedelta(hours=8))

DEFAULT_DB_PATH = '/var/lib/netgrey-ota/analytics.db'
DEFAULT_DEVICES_FILE = '/var/lib/netgrey-ota/registered_devices.json'
DEFAULT_INVITES_FILE = '/var/lib/netgrey-ota/testflight_invites.json'
DEFAULT_PROVISIONED_UDIDS_FILE = '/var/www/netgrey/provisioned_udids.json'
DEFAULT_PB_DB_PATH = '/var/lib/netgrey-pocketbase/pb_data/data.db'
DEFAULT_ADMIN_AUTH_FILE = '/etc/netgrey-ota/admin_auth.json'
DEFAULT_MANIFEST_PLIST_PATH = '/var/www/netgrey/manifest.plist'

DB_PATH = os.environ.get('NETGREY_ANALYTICS_DB', DEFAULT_DB_PATH)
DEVICES_FILE = os.environ.get('NETGREY_DEVICES_FILE', DEFAULT_DEVICES_FILE)
INVITES_FILE = os.environ.get('NETGREY_INVITES_FILE', DEFAULT_INVITES_FILE)
PROVISIONED_UDIDS_FILE = os.environ.get('NETGREY_PROVISIONED_UDIDS_FILE', DEFAULT_PROVISIONED_UDIDS_FILE)
PB_DB_PATH = os.environ.get('NETGREY_PB_DB', DEFAULT_PB_DB_PATH)
ADMIN_AUTH_FILE = os.environ.get('NETGREY_ADMIN_AUTH_FILE', DEFAULT_ADMIN_AUTH_FILE)
MANIFEST_PLIST_PATH = os.environ.get('NETGREY_MANIFEST_PLIST', DEFAULT_MANIFEST_PLIST_PATH)

ALLOWED_SECTIONS = frozenset({'video', 'comics', 'live', 'forum', 'game', 'search', 'library'})
ALLOWED_PLATFORMS = frozenset({'ios', 'ipados', 'android'})
ALLOWED_FORMS = frozenset({'phone', 'tablet', 'foldable', 'desktop', 'unknown'})
ALLOWED_CHANNELS = frozenset({'testflight', 'ota', 'apk', 'simulator', 'debug', 'unknown'})
TEST_CHANNELS = frozenset({'simulator', 'debug'})
ALLOWED_EVENT_TYPES = frozenset({'session_start', 'heartbeat', 'section_dwell', 'session_end'})
ALLOWED_DOWNLOAD_EVENTS = frozenset({
    'page_view',
    'android_apk_click',
    'ios_ota_install_click',
    'ios_udid_enroll_click',
    'ios_testflight_submit',
})

SAFE_ID_RE = re.compile(r'^[A-Za-z0-9._\-]{4,128}$')
SAFE_SUB_RE = re.compile(r'^[A-Za-z0-9._\-]{1,48}$')
CONTROL_CHARS_RE = re.compile(r'[\x00-\x1f\x7f]+')

MAX_EVENTS_PER_BATCH = 32
MAX_DWELL_PER_EVENT_SEC = 3600
ONLINE_WINDOW_SEC = 300
ADMIN_SESSION_TTL_SEC = 7 * 24 * 3600
DEDUP_RETENTION_SEC = 7 * 24 * 3600
PBKDF2_ITERATIONS = 310000

DB_LOCK = threading.RLock()


def _sanitize_text(value, default='', max_len=128):
    cleaned = CONTROL_CHARS_RE.sub(' ', str(value or '')).strip()
    cleaned = re.sub(r'\s+', ' ', cleaned)
    cleaned = ''.join(ch for ch in cleaned if ch.isprintable())
    if not cleaned:
        return default[:max_len]
    return cleaned[:max_len]


def _safe_int(value, default=0, min_val=0, max_val=None):
    try:
        num = int(float(value))
    except (TypeError, ValueError):
        return default
    if num < min_val:
        return min_val
    if max_val is not None and num > max_val:
        return max_val
    return num


def _hkt_dt(ts):
    return datetime.fromtimestamp(float(ts), tz=HKT)


def _hkt_day(ts):
    return _hkt_dt(ts).strftime('%Y-%m-%d')


def _hkt_hour(ts):
    return _hkt_dt(ts).hour


def _hkt_day_bounds(range_key, now_ts=None):
    now = float(now_ts if now_ts is not None else time.time())
    now_dt = _hkt_dt(now)
    today_str = now_dt.strftime('%Y-%m-%d')
    key = (range_key or '7d').strip().lower()
    if key == 'today':
        start_dt = now_dt.replace(hour=0, minute=0, second=0, microsecond=0)
        return today_str, today_str, start_dt.timestamp(), now
    if key == '7d':
        start_dt = (now_dt - timedelta(days=6)).replace(hour=0, minute=0, second=0, microsecond=0)
        return start_dt.strftime('%Y-%m-%d'), today_str, start_dt.timestamp(), now
    if key == '14d':
        start_dt = (now_dt - timedelta(days=13)).replace(hour=0, minute=0, second=0, microsecond=0)
        return start_dt.strftime('%Y-%m-%d'), today_str, start_dt.timestamp(), now
    if key == '30d':
        start_dt = (now_dt - timedelta(days=29)).replace(hour=0, minute=0, second=0, microsecond=0)
        return start_dt.strftime('%Y-%m-%d'), today_str, start_dt.timestamp(), now
    # 'all'
    return '1970-01-01', today_str, 0.0, now


def _connect(db_path=None):
    target = db_path or DB_PATH
    parent = os.path.dirname(target) or '.'
    os.makedirs(parent, mode=0o700, exist_ok=True)
    conn = sqlite3.connect(target, timeout=15.0, check_same_thread=False)
    conn.row_factory = sqlite3.Row
    conn.execute('PRAGMA journal_mode=WAL')
    conn.execute('PRAGMA synchronous=NORMAL')
    conn.execute('PRAGMA foreign_keys=ON')
    return conn


def init_db(db_path=None):
    target = db_path or DB_PATH
    with DB_LOCK:
        conn = _connect(target)
        try:
            conn.executescript(
                '''
                CREATE TABLE IF NOT EXISTS installations (
                    install_id TEXT PRIMARY KEY,
                    platform TEXT NOT NULL,
                    device_form TEXT NOT NULL DEFAULT 'unknown',
                    device_model TEXT NOT NULL DEFAULT '',
                    os_version TEXT NOT NULL DEFAULT '',
                    app_version TEXT NOT NULL DEFAULT '',
                    app_build INTEGER NOT NULL DEFAULT 0,
                    install_channel TEXT NOT NULL DEFAULT 'unknown',
                    locale TEXT NOT NULL DEFAULT '',
                    timezone TEXT NOT NULL DEFAULT '',
                    account_id TEXT NOT NULL DEFAULT '',
                    account_email TEXT NOT NULL DEFAULT '',
                    account_name TEXT NOT NULL DEFAULT '',
                    alias TEXT NOT NULL DEFAULT '',
                    notes TEXT NOT NULL DEFAULT '',
                    first_seen_at REAL NOT NULL,
                    last_seen_at REAL NOT NULL,
                    last_ip TEXT NOT NULL DEFAULT '',
                    total_sessions INTEGER NOT NULL DEFAULT 0,
                    total_dwell_sec INTEGER NOT NULL DEFAULT 0
                );

                CREATE INDEX IF NOT EXISTS idx_installations_last_seen
                    ON installations(last_seen_at DESC);
                CREATE INDEX IF NOT EXISTS idx_installations_platform
                    ON installations(platform);
                CREATE INDEX IF NOT EXISTS idx_installations_account_email
                    ON installations(account_email);

                CREATE TABLE IF NOT EXISTS sessions (
                    session_id TEXT PRIMARY KEY,
                    install_id TEXT NOT NULL,
                    platform TEXT NOT NULL,
                    app_version TEXT NOT NULL DEFAULT '',
                    app_build INTEGER NOT NULL DEFAULT 0,
                    account_email TEXT NOT NULL DEFAULT '',
                    started_at REAL NOT NULL,
                    ended_at REAL NOT NULL,
                    dwell_sec INTEGER NOT NULL DEFAULT 0,
                    primary_section TEXT NOT NULL DEFAULT 'video',
                    sections_json TEXT NOT NULL DEFAULT '{}',
                    is_active INTEGER NOT NULL DEFAULT 1
                );

                CREATE INDEX IF NOT EXISTS idx_sessions_install_started
                    ON sessions(install_id, started_at DESC);
                CREATE INDEX IF NOT EXISTS idx_sessions_started
                    ON sessions(started_at DESC);

                CREATE TABLE IF NOT EXISTS section_daily (
                    day TEXT NOT NULL,
                    install_id TEXT NOT NULL,
                    section TEXT NOT NULL,
                    sub_section TEXT NOT NULL DEFAULT '',
                    dwell_sec INTEGER NOT NULL DEFAULT 0,
                    visits INTEGER NOT NULL DEFAULT 0,
                    PRIMARY KEY (day, install_id, section, sub_section)
                );

                CREATE INDEX IF NOT EXISTS idx_section_daily_install
                    ON section_daily(install_id, day);
                CREATE INDEX IF NOT EXISTS idx_section_daily_section
                    ON section_daily(section, day);

                CREATE TABLE IF NOT EXISTS hourly_activity (
                    day TEXT NOT NULL,
                    hour INTEGER NOT NULL,
                    install_id TEXT NOT NULL,
                    dwell_sec INTEGER NOT NULL DEFAULT 0,
                    sessions INTEGER NOT NULL DEFAULT 0,
                    PRIMARY KEY (day, hour, install_id)
                );

                CREATE INDEX IF NOT EXISTS idx_hourly_activity_day
                    ON hourly_activity(day, hour);

                CREATE TABLE IF NOT EXISTS download_events (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    event_type TEXT NOT NULL,
                    platform TEXT NOT NULL DEFAULT '',
                    app_version TEXT NOT NULL DEFAULT '',
                    client_ip TEXT NOT NULL DEFAULT '',
                    user_agent TEXT NOT NULL DEFAULT '',
                    created_at REAL NOT NULL
                );

                CREATE INDEX IF NOT EXISTS idx_download_events_created
                    ON download_events(created_at DESC);

                CREATE TABLE IF NOT EXISTS telemetry_dedup (
                    event_id TEXT PRIMARY KEY,
                    created_at REAL NOT NULL
                );

                CREATE INDEX IF NOT EXISTS idx_telemetry_dedup_created
                    ON telemetry_dedup(created_at);

                CREATE TABLE IF NOT EXISTS admin_sessions (
                    token_hash TEXT PRIMARY KEY,
                    username TEXT NOT NULL,
                    created_at REAL NOT NULL,
                    expires_at REAL NOT NULL,
                    last_seen_at REAL NOT NULL,
                    client_ip TEXT NOT NULL DEFAULT ''
                );
                '''
            )
            conn.commit()
        finally:
            conn.close()
        try:
            if os.path.exists(target):
                os.chmod(target, 0o600)
        except OSError:
            pass


def _load_json_safe(path, default):
    if not path or not os.path.exists(path):
        return default
    try:
        with open(path, 'r', encoding='utf-8') as fh:
            return json.load(fh)
    except Exception:
        return default


def _save_json_secret(path, data):
    target_dir = os.path.dirname(path) or '.'
    os.makedirs(target_dir, mode=0o700, exist_ok=True)
    fd, tmp_path = tempfile.mkstemp(prefix='.tmp_auth_', suffix='.json', dir=target_dir)
    try:
        with os.fdopen(fd, 'w', encoding='utf-8') as fh:
            json.dump(data, fh, ensure_ascii=False, indent=2)
        os.chmod(tmp_path, 0o600)
        os.replace(tmp_path, path)
    except Exception:
        if os.path.exists(tmp_path):
            os.unlink(tmp_path)
        raise


def hash_password(password, salt_hex=None):
    salt = bytes.fromhex(salt_hex) if salt_hex else secrets.token_bytes(16)
    digest = hashlib.pbkdf2_hmac('sha256', password.encode('utf-8'), salt, PBKDF2_ITERATIONS)
    return salt.hex(), digest.hex()


def ensure_admin_auth_initialized(initial_password=None, username='admin', auth_file=None):
    target = auth_file or ADMIN_AUTH_FILE
    existing = _load_json_safe(target, None)
    if isinstance(existing, dict) and existing.get('password_hash') and existing.get('salt'):
        return False, None
    pwd = initial_password or secrets.token_urlsafe(15)
    salt_hex, pwd_hash = hash_password(pwd)
    _save_json_secret(target, {
        'username': _sanitize_text(username, default='admin', max_len=32) or 'admin',
        'salt': salt_hex,
        'password_hash': pwd_hash,
        'iterations': PBKDF2_ITERATIONS,
        'updated_at': datetime.now(HKT).strftime('%Y-%m-%d %H:%M:%S %z'),
    })
    return True, pwd


def verify_admin_credentials(username, password, auth_file=None):
    target = auth_file or ADMIN_AUTH_FILE
    cfg = _load_json_safe(target, None)
    if not isinstance(cfg, dict):
        return False
    expected_user = str(cfg.get('username') or 'admin').strip()
    salt_hex = str(cfg.get('salt') or '').strip()
    expected_hash = str(cfg.get('password_hash') or '').strip()
    iterations = _safe_int(cfg.get('iterations'), default=PBKDF2_ITERATIONS, min_val=10000, max_val=2000000)
    if not expected_user or not salt_hex or not expected_hash:
        return False
    if not hmac.compare_digest((username or '').strip(), expected_user):
        return False
    try:
        salt = bytes.fromhex(salt_hex)
    except ValueError:
        return False
    candidate_hash = hashlib.pbkdf2_hmac('sha256', (password or '').encode('utf-8'), salt, iterations).hex()
    return hmac.compare_digest(candidate_hash, expected_hash)


def change_admin_password(old_password, new_password, current_token=None, auth_file=None, db_path=None):
    target = auth_file or ADMIN_AUTH_FILE
    cfg = _load_json_safe(target, None)
    if not isinstance(cfg, dict):
        return False, '管理员账号尚未初始化'
    username = str(cfg.get('username') or 'admin').strip()
    if not verify_admin_credentials(username, old_password, auth_file=target):
        return False, '当前密码不正确'
    if not isinstance(new_password, str) or len(new_password) < 10 or len(new_password) > 128:
        return False, '新密码长度须为 10 至 128 个字符'
    if old_password == new_password:
        return False, '新密码不能与当前密码相同'
    salt_hex, pwd_hash = hash_password(new_password)
    _save_json_secret(target, {
        'username': username,
        'salt': salt_hex,
        'password_hash': pwd_hash,
        'iterations': PBKDF2_ITERATIONS,
        'updated_at': datetime.now(HKT).strftime('%Y-%m-%d %H:%M:%S %z'),
    })
    keep_hash = hashlib.sha256(current_token.encode('utf-8')).hexdigest() if current_token else None
    with DB_LOCK:
        conn = _connect(db_path)
        try:
            if keep_hash:
                conn.execute('DELETE FROM admin_sessions WHERE token_hash != ?', (keep_hash,))
            else:
                conn.execute('DELETE FROM admin_sessions')
            conn.commit()
        finally:
            conn.close()
    return True, '管理员密码已更新'


def create_admin_session(username, client_ip='', db_path=None, now_ts=None):
    now = float(now_ts if now_ts is not None else time.time())
    raw_token = secrets.token_urlsafe(32)
    token_hash = hashlib.sha256(raw_token.encode('utf-8')).hexdigest()
    expires_at = now + ADMIN_SESSION_TTL_SEC
    with DB_LOCK:
        conn = _connect(db_path)
        try:
            conn.execute('DELETE FROM admin_sessions WHERE expires_at < ?', (now,))
            conn.execute(
                '''
                INSERT INTO admin_sessions (token_hash, username, created_at, expires_at, last_seen_at, client_ip)
                VALUES (?, ?, ?, ?, ?, ?)
                ''',
                (token_hash, _sanitize_text(username, 'admin', 32), now, expires_at, now, _sanitize_text(client_ip, '', 64)),
            )
            conn.commit()
        finally:
            conn.close()
    return raw_token, int(expires_at)


def verify_admin_session(raw_token, db_path=None, now_ts=None):
    token = (raw_token or '').strip()
    if not token or len(token) > 256:
        return None
    now = float(now_ts if now_ts is not None else time.time())
    token_hash = hashlib.sha256(token.encode('utf-8')).hexdigest()
    with DB_LOCK:
        conn = _connect(db_path)
        try:
            row = conn.execute(
                'SELECT username, expires_at FROM admin_sessions WHERE token_hash = ?',
                (token_hash,),
            ).fetchone()
            if not row:
                return None
            if float(row['expires_at']) < now:
                conn.execute('DELETE FROM admin_sessions WHERE token_hash = ?', (token_hash,))
                conn.commit()
                return None
            conn.execute(
                'UPDATE admin_sessions SET last_seen_at = ? WHERE token_hash = ?',
                (now, token_hash),
            )
            conn.commit()
            return {'username': row['username']}
        finally:
            conn.close()


def revoke_admin_session(raw_token, db_path=None):
    token = (raw_token or '').strip()
    if not token:
        return
    token_hash = hashlib.sha256(token.encode('utf-8')).hexdigest()
    with DB_LOCK:
        conn = _connect(db_path)
        try:
            conn.execute('DELETE FROM admin_sessions WHERE token_hash = ?', (token_hash,))
            conn.commit()
        finally:
            conn.close()


def _normalize_platform(raw):
    val = _sanitize_text(raw, '', 16).lower()
    return val if val in ALLOWED_PLATFORMS else ''


def _normalize_form(raw, platform=''):
    val = _sanitize_text(raw, '', 16).lower()
    if val in ALLOWED_FORMS:
        return val
    if platform == 'ipados':
        return 'tablet'
    if platform in ('ios', 'android'):
        return 'phone'
    return 'unknown'


def _normalize_channel(raw):
    val = _sanitize_text(raw, 'unknown', 24).lower()
    return val if val in ALLOWED_CHANNELS else 'unknown'


def _normalize_section(raw):
    val = _sanitize_text(raw, '', 24).lower()
    return val if val in ALLOWED_SECTIONS else ''


def _normalize_sub_section(raw, section):
    val = _sanitize_text(raw, '', 48).lower()
    if val and SAFE_SUB_RE.match(val):
        return val
    return section


def _parse_sections_json(raw):
    if not raw:
        return {}
    try:
        obj = json.loads(raw)
        if isinstance(obj, dict):
            return {str(k): _safe_int(v, 0, 0) for k, v in obj.items() if str(k) in ALLOWED_SECTIONS}
    except Exception:
        pass
    return {}


def ingest_telemetry_batch(payload, client_ip='', db_path=None, now_ts=None):
    if not isinstance(payload, dict):
        return False, {'error': '请求体必须是 JSON 对象'}, 400

    install_id = _sanitize_text(payload.get('install_id'), '', 128)
    if not install_id or not SAFE_ID_RE.match(install_id):
        return False, {'error': 'install_id 格式无效'}, 400

    platform = _normalize_platform(payload.get('platform'))
    if not platform:
        return False, {'error': 'platform 必须为 ios / ipados / android'}, 400

    events = payload.get('events')
    if not isinstance(events, list) or not events:
        return False, {'error': 'events 必须是非空数组'}, 400
    if len(events) > MAX_EVENTS_PER_BATCH:
        return False, {'error': f'单批最多允许 {MAX_EVENTS_PER_BATCH} 条事件'}, 400

    now = float(now_ts if now_ts is not None else time.time())
    device_form = _normalize_form(payload.get('device_form'), platform)
    device_model = _sanitize_text(payload.get('device_model'), '', 96)
    os_version = _sanitize_text(payload.get('os_version'), '', 64)
    app_version = _sanitize_text(payload.get('app_version'), '', 32)
    app_build = _safe_int(payload.get('app_build'), default=0, min_val=0, max_val=99999999)
    install_channel = _normalize_channel(payload.get('install_channel'))
    locale = _sanitize_text(payload.get('locale'), '', 32)
    tz_name = _sanitize_text(payload.get('timezone'), '', 64)
    account_id = _sanitize_text(payload.get('account_id'), '', 64)
    account_email = _sanitize_text(payload.get('account_email'), '', 128).lower()
    account_name = _sanitize_text(payload.get('account_name'), '', 64)
    clean_ip = _sanitize_text(client_ip, '', 64)

    accepted = 0
    deduped = 0
    rejected = 0

    with DB_LOCK:
        conn = _connect(db_path)
        try:
            conn.execute('BEGIN IMMEDIATE')
            conn.execute('DELETE FROM telemetry_dedup WHERE created_at < ?', (now - DEDUP_RETENTION_SEC,))

            existing_inst = conn.execute(
                'SELECT install_id, account_id, account_email, account_name FROM installations WHERE install_id = ?',
                (install_id,),
            ).fetchone()
            if existing_inst is None:
                conn.execute(
                    '''
                    INSERT INTO installations (
                        install_id, platform, device_form, device_model, os_version,
                        app_version, app_build, install_channel, locale, timezone,
                        account_id, account_email, account_name, alias, notes,
                        first_seen_at, last_seen_at, last_ip, total_sessions, total_dwell_sec
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, '', '', ?, ?, ?, 0, 0)
                    ''',
                    (
                        install_id, platform, device_form, device_model, os_version,
                        app_version, app_build, install_channel, locale, tz_name,
                        account_id, account_email, account_name, now, now, clean_ip,
                    ),
                )
            else:
                conn.execute(
                    '''
                    UPDATE installations SET
                        platform = ?,
                        device_form = ?,
                        device_model = CASE WHEN ? != '' THEN ? ELSE device_model END,
                        os_version = CASE WHEN ? != '' THEN ? ELSE os_version END,
                        app_version = CASE WHEN ? != '' THEN ? ELSE app_version END,
                        app_build = CASE WHEN ? > 0 THEN ? ELSE app_build END,
                        install_channel = CASE WHEN ? != 'unknown' THEN ? ELSE install_channel END,
                        locale = CASE WHEN ? != '' THEN ? ELSE locale END,
                        timezone = CASE WHEN ? != '' THEN ? ELSE timezone END,
                        account_id = CASE WHEN ? != '' THEN ? ELSE account_id END,
                        account_email = CASE WHEN ? != '' THEN ? ELSE account_email END,
                        account_name = CASE WHEN ? != '' THEN ? ELSE account_name END,
                        last_seen_at = MAX(last_seen_at, ?),
                        last_ip = CASE WHEN ? != '' THEN ? ELSE last_ip END
                    WHERE install_id = ?
                    ''',
                    (
                        platform, device_form,
                        device_model, device_model,
                        os_version, os_version,
                        app_version, app_version,
                        app_build, app_build,
                        install_channel, install_channel,
                        locale, locale,
                        tz_name, tz_name,
                        account_id, account_id,
                        account_email, account_email,
                        account_name, account_name,
                        now,
                        clean_ip, clean_ip,
                        install_id,
                    ),
                )

            for item in events:
                if not isinstance(item, dict):
                    rejected += 1
                    continue
                event_id = _sanitize_text(item.get('event_id'), '', 128)
                event_type = _sanitize_text(item.get('type'), '', 32).lower()
                session_id = _sanitize_text(item.get('session_id'), '', 128)
                if (
                    not event_id or not SAFE_ID_RE.match(event_id)
                    or event_type not in ALLOWED_EVENT_TYPES
                    or not session_id or not SAFE_ID_RE.match(session_id)
                ):
                    rejected += 1
                    continue

                raw_section = item.get('section')
                section = _normalize_section(raw_section)
                if event_type == 'section_dwell' and not section:
                    rejected += 1
                    continue
                if raw_section not in (None, '') and not section:
                    rejected += 1
                    continue

                cur = conn.execute(
                    'INSERT OR IGNORE INTO telemetry_dedup (event_id, created_at) VALUES (?, ?)',
                    (event_id, now),
                )
                if cur.rowcount == 0:
                    deduped += 1
                    continue

                ev_ts = float(item.get('ts') or now)
                if ev_ts < 1577836800 or ev_ts > now + 86400:
                    ev_ts = now

                sub_section = _normalize_sub_section(item.get('sub_section'), section) if section else ''
                duration_sec = _safe_int(item.get('duration_sec'), default=0, min_val=0, max_val=MAX_DWELL_PER_EVENT_SEC)

                day_str = _hkt_day(ev_ts)
                hour_val = _hkt_hour(ev_ts)

                sess_row = conn.execute(
                    'SELECT session_id, started_at, ended_at, dwell_sec, primary_section, sections_json, is_active FROM sessions WHERE session_id = ?',
                    (session_id,),
                ).fetchone()

                is_new_session = sess_row is None
                if is_new_session:
                    init_section = section or 'video'
                    sections_map = {init_section: duration_sec} if (section and duration_sec > 0) else {}
                    conn.execute(
                        '''
                        INSERT INTO sessions (
                            session_id, install_id, platform, app_version, app_build,
                            account_email, started_at, ended_at, dwell_sec,
                            primary_section, sections_json, is_active
                        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        ''',
                        (
                            session_id, install_id, platform, app_version, app_build,
                            account_email, ev_ts, ev_ts, duration_sec,
                            init_section, json.dumps(sections_map, ensure_ascii=False),
                            0 if event_type == 'session_end' else 1,
                        ),
                    )
                    conn.execute(
                        '''
                        UPDATE installations
                        SET total_sessions = total_sessions + 1,
                            total_dwell_sec = total_dwell_sec + ?
                        WHERE install_id = ?
                        ''',
                        (duration_sec, install_id),
                    )
                    conn.execute(
                        '''
                        INSERT INTO hourly_activity (day, hour, install_id, dwell_sec, sessions)
                        VALUES (?, ?, ?, ?, 1)
                        ON CONFLICT(day, hour, install_id) DO UPDATE SET
                            dwell_sec = hourly_activity.dwell_sec + excluded.dwell_sec,
                            sessions = hourly_activity.sessions + 1
                        ''',
                        (day_str, hour_val, install_id, duration_sec),
                    )
                else:
                    sections_map = _parse_sections_json(sess_row['sections_json'])
                    if section and duration_sec > 0:
                        sections_map[section] = sections_map.get(section, 0) + duration_sec
                    new_dwell = _safe_int(sess_row['dwell_sec'], 0, 0) + duration_sec
                    primary_section = sess_row['primary_section'] or section or 'video'
                    if sections_map:
                        primary_section = max(sections_map.items(), key=lambda kv: kv[1])[0]
                    new_ended = max(float(sess_row['ended_at']), ev_ts)
                    new_started = min(float(sess_row['started_at']), ev_ts)
                    still_active = 0 if event_type == 'session_end' else int(sess_row['is_active'])
                    conn.execute(
                        '''
                        UPDATE sessions SET
                            app_version = CASE WHEN ? != '' THEN ? ELSE app_version END,
                            app_build = CASE WHEN ? > 0 THEN ? ELSE app_build END,
                            account_email = CASE WHEN ? != '' THEN ? ELSE account_email END,
                            started_at = ?,
                            ended_at = ?,
                            dwell_sec = ?,
                            primary_section = ?,
                            sections_json = ?,
                            is_active = ?
                        WHERE session_id = ?
                        ''',
                        (
                            app_version, app_version,
                            app_build, app_build,
                            account_email, account_email,
                            new_started, new_ended, new_dwell,
                            primary_section, json.dumps(sections_map, ensure_ascii=False),
                            still_active, session_id,
                        ),
                    )
                    if duration_sec > 0:
                        conn.execute(
                            'UPDATE installations SET total_dwell_sec = total_dwell_sec + ? WHERE install_id = ?',
                            (duration_sec, install_id),
                        )
                        conn.execute(
                            '''
                            INSERT INTO hourly_activity (day, hour, install_id, dwell_sec, sessions)
                            VALUES (?, ?, ?, ?, 0)
                            ON CONFLICT(day, hour, install_id) DO UPDATE SET
                                dwell_sec = hourly_activity.dwell_sec + excluded.dwell_sec
                            ''',
                            (day_str, hour_val, install_id, duration_sec),
                        )

                if section and (duration_sec > 0 or event_type == 'section_dwell'):
                    visit_inc = 1 if event_type == 'section_dwell' else 0
                    conn.execute(
                        '''
                        INSERT INTO section_daily (day, install_id, section, sub_section, dwell_sec, visits)
                        VALUES (?, ?, ?, ?, ?, ?)
                        ON CONFLICT(day, install_id, section, sub_section) DO UPDATE SET
                            dwell_sec = section_daily.dwell_sec + excluded.dwell_sec,
                            visits = section_daily.visits + excluded.visits
                        ''',
                        (day_str, install_id, section, sub_section, duration_sec, visit_inc),
                    )

                accepted += 1

            conn.commit()
        finally:
            conn.close()

    return True, {
        'ok': True,
        'accepted': accepted,
        'deduped': deduped,
        'rejected': rejected,
    }, 200


def record_download_event(event_type, platform='', app_version='', client_ip='', user_agent='', db_path=None, now_ts=None):
    ev = _sanitize_text(event_type, '', 48).lower()
    if ev not in ALLOWED_DOWNLOAD_EVENTS:
        return False, 'Unsupported download event'
    now = float(now_ts if now_ts is not None else time.time())
    with DB_LOCK:
        conn = _connect(db_path)
        try:
            conn.execute(
                '''
                INSERT INTO download_events (event_type, platform, app_version, client_ip, user_agent, created_at)
                VALUES (?, ?, ?, ?, ?, ?)
                ''',
                (
                    ev,
                    _sanitize_text(platform, '', 24).lower(),
                    _sanitize_text(app_version, '', 32),
                    _sanitize_text(client_ip, '', 64),
                    _sanitize_text(user_agent, '', 180),
                    now,
                ),
            )
            conn.commit()
        finally:
            conn.close()
    return True, 'recorded'


def update_installation_meta(install_id, alias=None, notes=None, db_path=None):
    clean_id = _sanitize_text(install_id, '', 128)
    if not clean_id or not SAFE_ID_RE.match(clean_id):
        return False, {'error': 'install_id 格式无效'}, 400
    clean_alias = _sanitize_text(alias, '', 64) if alias is not None else None
    clean_notes = _sanitize_text(notes, '', 500) if notes is not None else None
    with DB_LOCK:
        conn = _connect(db_path)
        try:
            row = conn.execute('SELECT install_id FROM installations WHERE install_id = ?', (clean_id,)).fetchone()
            if not row:
                return False, {'error': '未找到该设备记录'}, 404
            if clean_alias is not None and clean_notes is not None:
                conn.execute(
                    'UPDATE installations SET alias = ?, notes = ? WHERE install_id = ?',
                    (clean_alias, clean_notes, clean_id),
                )
            elif clean_alias is not None:
                conn.execute('UPDATE installations SET alias = ? WHERE install_id = ?', (clean_alias, clean_id))
            elif clean_notes is not None:
                conn.execute('UPDATE installations SET notes = ? WHERE install_id = ?', (clean_notes, clean_id))
            conn.commit()
            updated = conn.execute('SELECT * FROM installations WHERE install_id = ?', (clean_id,)).fetchone()
            return True, {'ok': True, 'installation': dict(updated)}, 200
        finally:
            conn.close()


def _install_where_clause(platform_filter='all', channel_filter='all', include_sim=True, alias_prefix='i'):
    clauses = ['1=1']
    params = []
    pf = (platform_filter or 'all').strip().lower()
    if pf in ALLOWED_PLATFORMS:
        clauses.append(f'{alias_prefix}.platform = ?')
        params.append(pf)

    cf = (channel_filter or 'all').strip().lower()
    if cf in ALLOWED_CHANNELS:
        clauses.append(f'{alias_prefix}.install_channel = ?')
        params.append(cf)
    elif cf == 'prod_only' or not include_sim:
        clauses.append(f"{alias_prefix}.install_channel NOT IN ('simulator', 'debug')")
    return ' AND '.join(clauses), params


def _read_latest_release_info(manifest_path=None):
    path = manifest_path or MANIFEST_PLIST_PATH
    if not path or not os.path.exists(path):
        return {'version': '', 'bundle_id': ''}
    try:
        with open(path, 'rb') as fh:
            plist = plistlib.load(fh)
        meta = plist['items'][0]['metadata']
        return {
            'version': str(meta.get('bundle-version') or ''),
            'bundle_id': str(meta.get('bundle-identifier') or ''),
        }
    except Exception:
        return {'version': '', 'bundle_id': ''}


def _read_ota_devices_and_invites(devices_path=None, invites_path=None, provisioned_path=None):
    d_path = devices_path or DEVICES_FILE
    i_path = invites_path or INVITES_FILE
    p_path = provisioned_path or PROVISIONED_UDIDS_FILE

    raw_devices = _load_json_safe(d_path, [])
    raw_invites = _load_json_safe(i_path, [])
    prov_data = _load_json_safe(p_path, {})

    prov_all = False
    prov_udids = set()
    if isinstance(prov_data, dict):
        prov_all = prov_data.get('provisions_all_devices') is True
        prov_udids = {str(u).strip().lower() for u in (prov_data.get('udids') or []) if str(u).strip()}
    elif isinstance(prov_data, list):
        prov_udids = {str(u).strip().lower() for u in prov_data if str(u).strip()}

    ota_devices = []
    if isinstance(raw_devices, list):
        for item in raw_devices:
            if not isinstance(item, dict):
                continue
            udid = str(item.get('udid') or '').strip()
            ota_devices.append({
                'udid': udid,
                'product': str(item.get('product') or ''),
                'device_name': str(item.get('device_name') or ''),
                'version': str(item.get('version') or ''),
                'apple_registered': bool(item.get('apple_registered')),
                'provisioned_in_ipa': prov_all or (udid.lower() in prov_udids),
                'registered_at': str(item.get('registered_at') or ''),
                'last_seen': str(item.get('last_seen') or ''),
            })

    tf_invites = []
    if isinstance(raw_invites, list):
        for item in raw_invites:
            if not isinstance(item, dict):
                continue
            tf_invites.append({
                'email': str(item.get('email') or ''),
                'status': str(item.get('status') or ''),
                'message': str(item.get('message') or ''),
                'retry_count': _safe_int(item.get('retry_count'), 0, 0),
                'created_at': str(item.get('created_at') or ''),
                'updated_at': str(item.get('updated_at') or ''),
            })

    ota_devices.sort(key=lambda x: x.get('last_seen') or x.get('registered_at') or '', reverse=True)
    tf_invites.sort(key=lambda x: x.get('updated_at') or x.get('created_at') or '', reverse=True)
    return ota_devices, tf_invites


def _read_pocketbase_users(pb_db_path=None):
    path = pb_db_path or PB_DB_PATH
    if not path or not os.path.exists(path):
        return []
    try:
        conn = sqlite3.connect(f'file:{path}?mode=ro', uri=True, timeout=5.0)
        conn.row_factory = sqlite3.Row
        try:
            cols = {row['name'] for row in conn.execute('PRAGMA table_info(users)').fetchall()}
            if not cols:
                return []
            sel_cols = [c for c in ('id', 'email', 'name', 'verified', 'created', 'updated') if c in cols]
            order_col = 'created DESC' if 'created' in cols else 'rowid DESC'
            rows = conn.execute(f'SELECT {", ".join(sel_cols)} FROM users ORDER BY {order_col}').fetchall()
            return [
                {
                    'id': str(r['id']) if 'id' in cols else '',
                    'email': str(r['email'] or '').lower() if 'email' in cols else '',
                    'name': str(r['name'] or '') if 'name' in cols else '',
                    'verified': bool(r['verified']) if 'verified' in cols else False,
                    'created': str(r['created'] or '') if 'created' in cols else '',
                    'updated': str(r['updated'] or '') if 'updated' in cols else '',
                }
                for r in rows
            ]
        finally:
            conn.close()
    except Exception:
        return []


def get_overview(range_key='7d', platform_filter='all', include_sim=True, db_path=None, now_ts=None):
    start_day, end_day, start_ts, now = _hkt_day_bounds(range_key, now_ts)
    where_sql, where_params = _install_where_clause(platform_filter, 'all', include_sim, alias_prefix='i')
    online_cutoff = now - ONLINE_WINDOW_SEC

    ota_devices, tf_invites = _read_ota_devices_and_invites()
    pb_users = _read_pocketbase_users()
    latest_release = _read_latest_release_info()

    with DB_LOCK:
        conn = _connect(db_path)
        try:
            all_installs = conn.execute(
                f'SELECT * FROM installations i WHERE {where_sql}',
                where_params,
            ).fetchall()

            total_installs = len(all_installs)
            online_now = sum(1 for r in all_installs if float(r['last_seen_at']) >= online_cutoff)
            new_installs = sum(1 for r in all_installs if float(r['first_seen_at']) >= start_ts)

            platform_counts = {'ios': 0, 'ipados': 0, 'android': 0}
            form_counts = {}
            version_counts = {}
            for r in all_installs:
                p = r['platform']
                platform_counts[p] = platform_counts.get(p, 0) + 1
                f = r['device_form'] or 'unknown'
                form_counts[f] = form_counts.get(f, 0) + 1
                ver = f"v{r['app_version'] or '?'} ({r['app_build'] or 0})"
                version_counts[ver] = version_counts.get(ver, 0) + 1

            # DAU / WAU / MAU anchored at HKT today
            today_str = end_day
            wau_start = (_hkt_dt(now) - timedelta(days=6)).strftime('%Y-%m-%d')
            mau_start = (_hkt_dt(now) - timedelta(days=29)).strftime('%Y-%m-%d')

            dau = conn.execute(
                f'''
                SELECT COUNT(DISTINCT s.install_id) AS cnt
                FROM sessions s
                JOIN installations i ON i.install_id = s.install_id
                WHERE {where_sql} AND s.started_at >= ?
                ''',
                where_params + [_hkt_dt(now).replace(hour=0, minute=0, second=0, microsecond=0).timestamp()],
            ).fetchone()['cnt']

            wau = conn.execute(
                f'''
                SELECT COUNT(DISTINCT s.install_id) AS cnt
                FROM sessions s
                JOIN installations i ON i.install_id = s.install_id
                WHERE {where_sql} AND s.started_at >= ?
                ''',
                where_params + [(_hkt_dt(now) - timedelta(days=6)).replace(hour=0, minute=0, second=0, microsecond=0).timestamp()],
            ).fetchone()['cnt']

            mau = conn.execute(
                f'''
                SELECT COUNT(DISTINCT s.install_id) AS cnt
                FROM sessions s
                JOIN installations i ON i.install_id = s.install_id
                WHERE {where_sql} AND s.started_at >= ?
                ''',
                where_params + [(_hkt_dt(now) - timedelta(days=29)).replace(hour=0, minute=0, second=0, microsecond=0).timestamp()],
            ).fetchone()['cnt']

            range_sess = conn.execute(
                f'''
                SELECT
                    COUNT(DISTINCT s.install_id) AS active_devices,
                    COUNT(*) AS session_count,
                    COALESCE(SUM(s.dwell_sec), 0) AS total_dwell_sec
                FROM sessions s
                JOIN installations i ON i.install_id = s.install_id
                WHERE {where_sql} AND s.started_at >= ?
                ''',
                where_params + [start_ts],
            ).fetchone()

            active_devices = _safe_int(range_sess['active_devices'], 0, 0)
            session_count = _safe_int(range_sess['session_count'], 0, 0)
            total_dwell_sec = _safe_int(range_sess['total_dwell_sec'], 0, 0)
            avg_session_sec = int(round(total_dwell_sec / session_count)) if session_count > 0 else 0

            # Daily trend from section_daily + hourly_activity
            daily_rows = conn.execute(
                f'''
                SELECT
                    h.day AS day,
                    COUNT(DISTINCT h.install_id) AS active_devices,
                    COALESCE(SUM(h.dwell_sec), 0) AS dwell_sec,
                    COALESCE(SUM(h.sessions), 0) AS sessions
                FROM hourly_activity h
                JOIN installations i ON i.install_id = h.install_id
                WHERE {where_sql} AND h.day >= ? AND h.day <= ?
                GROUP BY h.day
                ORDER BY h.day ASC
                ''',
                where_params + [start_day, end_day],
            ).fetchall()

            daily_map = {
                r['day']: {
                    'day': r['day'],
                    'active_devices': _safe_int(r['active_devices'], 0, 0),
                    'dwell_sec': _safe_int(r['dwell_sec'], 0, 0),
                    'sessions': _safe_int(r['sessions'], 0, 0),
                }
                for r in daily_rows
            }

            if range_key in ('today', '7d', '14d', '30d'):
                days_span = {'today': 1, '7d': 7, '14d': 14, '30d': 30}.get(range_key, 7)
                daily_trend = []
                base_dt = _hkt_dt(now).replace(hour=0, minute=0, second=0, microsecond=0)
                for offset in range(days_span - 1, -1, -1):
                    d_str = (base_dt - timedelta(days=offset)).strftime('%Y-%m-%d')
                    daily_trend.append(daily_map.get(d_str, {
                        'day': d_str,
                        'active_devices': 0,
                        'dwell_sec': 0,
                        'sessions': 0,
                    }))
            else:
                daily_trend = list(daily_map.values())

            sec_rows = conn.execute(
                f'''
                SELECT
                    sd.section AS section,
                    COALESCE(SUM(sd.dwell_sec), 0) AS dwell_sec,
                    COALESCE(SUM(sd.visits), 0) AS visits,
                    COUNT(DISTINCT sd.install_id) AS active_devices
                FROM section_daily sd
                JOIN installations i ON i.install_id = sd.install_id
                WHERE {where_sql} AND sd.day >= ? AND sd.day <= ?
                GROUP BY sd.section
                ''',
                where_params + [start_day, end_day],
            ).fetchall()

            sec_map = {r['section']: dict(r) for r in sec_rows}
            total_sec_dwell = sum(_safe_int(v.get('dwell_sec'), 0, 0) for v in sec_map.values())
            sections_breakdown = []
            for sec in ('video', 'comics', 'live', 'forum', 'game', 'search', 'library'):
                rec = sec_map.get(sec, {})
                dwell = _safe_int(rec.get('dwell_sec'), 0, 0)
                sections_breakdown.append({
                    'section': sec,
                    'dwell_sec': dwell,
                    'visits': _safe_int(rec.get('visits'), 0, 0),
                    'active_devices': _safe_int(rec.get('active_devices'), 0, 0),
                    'share_pct': round((dwell / total_sec_dwell) * 100, 1) if total_sec_dwell > 0 else 0.0,
                })
            sections_breakdown.sort(key=lambda x: x['dwell_sec'], reverse=True)

            hourly_rows = conn.execute(
                f'''
                SELECT
                    h.hour AS hour,
                    COALESCE(SUM(h.dwell_sec), 0) AS dwell_sec,
                    COUNT(DISTINCT h.install_id) AS active_devices
                FROM hourly_activity h
                JOIN installations i ON i.install_id = h.install_id
                WHERE {where_sql} AND h.day >= ? AND h.day <= ?
                GROUP BY h.hour
                ORDER BY h.hour ASC
                ''',
                where_params + [start_day, end_day],
            ).fetchall()
            hourly_map = {int(r['hour']): dict(r) for r in hourly_rows}
            hourly_distribution = [
                {
                    'hour': hr,
                    'dwell_sec': _safe_int((hourly_map.get(hr) or {}).get('dwell_sec'), 0, 0),
                    'active_devices': _safe_int((hourly_map.get(hr) or {}).get('active_devices'), 0, 0),
                }
                for hr in range(24)
            ]

            version_distribution = sorted(
                [{'version': k, 'count': v} for k, v in version_counts.items()],
                key=lambda x: x['count'],
                reverse=True,
            )

            return {
                'ok': True,
                'range': range_key,
                'generated_at': _hkt_dt(now).strftime('%Y-%m-%d %H:%M:%S HKT'),
                'latest_release': latest_release,
                'kpis': {
                    'total_installs': total_installs,
                    'new_installs': new_installs,
                    'online_now': online_now,
                    'active_devices': active_devices,
                    'dau': dau,
                    'wau': wau,
                    'mau': mau,
                    'session_count': session_count,
                    'total_dwell_sec': total_dwell_sec,
                    'avg_session_sec': avg_session_sec,
                    'ota_udid_devices': len(ota_devices),
                    'testflight_invites': len(tf_invites),
                    'registered_accounts': len(pb_users),
                },
                'platform_counts': platform_counts,
                'form_counts': form_counts,
                'version_distribution': version_distribution,
                'daily_trend': daily_trend,
                'sections_breakdown': sections_breakdown,
                'hourly_distribution': hourly_distribution,
                'legacy_anchors': {'wau_start': wau_start, 'mau_start': mau_start, 'today': today_str},
            }
        finally:
            conn.close()


def list_installations(
    range_key='7d',
    platform_filter='all',
    channel_filter='all',
    search='',
    sort_by='last_seen',
    order='desc',
    include_sim=True,
    db_path=None,
    now_ts=None,
):
    start_day, end_day, start_ts, now = _hkt_day_bounds(range_key, now_ts)
    where_sql, where_params = _install_where_clause(platform_filter, channel_filter, include_sim, alias_prefix='i')
    online_cutoff = now - ONLINE_WINDOW_SEC

    q = _sanitize_text(search, '', 64).lower()
    if q:
        where_sql += (
            ' AND (LOWER(i.install_id) LIKE ? OR LOWER(i.alias) LIKE ? '
            'OR LOWER(i.device_model) LIKE ? OR LOWER(i.account_email) LIKE ? '
            'OR LOWER(i.account_name) LIKE ? OR LOWER(i.app_version) LIKE ?)'
        )
        like_pat = f'%{q}%'
        where_params.extend([like_pat] * 6)

    with DB_LOCK:
        conn = _connect(db_path)
        try:
            rows = conn.execute(
                f'SELECT * FROM installations i WHERE {where_sql}',
                where_params,
            ).fetchall()

            sess_rows = conn.execute(
                '''
                SELECT install_id, COUNT(*) AS range_sessions, COALESCE(SUM(dwell_sec), 0) AS range_dwell_sec
                FROM sessions
                WHERE started_at >= ?
                GROUP BY install_id
                ''',
                (start_ts,),
            ).fetchall()
            sess_by_id = {r['install_id']: dict(r) for r in sess_rows}

            sec_rows = conn.execute(
                '''
                SELECT install_id, section, COALESCE(SUM(dwell_sec), 0) AS dwell_sec
                FROM section_daily
                WHERE day >= ? AND day <= ?
                GROUP BY install_id, section
                ''',
                (start_day, end_day),
            ).fetchall()
            sec_by_id = {}
            for r in sec_rows:
                iid = r['install_id']
                sec_by_id.setdefault(iid, {})[r['section']] = _safe_int(r['dwell_sec'], 0, 0)

            items = []
            for r in rows:
                iid = r['install_id']
                s_info = sess_by_id.get(iid, {})
                sec_map = sec_by_id.get(iid, {})
                top_section = max(sec_map.items(), key=lambda kv: kv[1])[0] if sec_map else ''
                last_seen = float(r['last_seen_at'])
                first_seen = float(r['first_seen_at'])
                items.append({
                    'install_id': iid,
                    'platform': r['platform'],
                    'device_form': r['device_form'],
                    'device_model': r['device_model'],
                    'os_version': r['os_version'],
                    'app_version': r['app_version'],
                    'app_build': int(r['app_build']),
                    'install_channel': r['install_channel'],
                    'locale': r['locale'],
                    'timezone': r['timezone'],
                    'account_id': r['account_id'],
                    'account_email': r['account_email'],
                    'account_name': r['account_name'],
                    'alias': r['alias'],
                    'notes': r['notes'],
                    'is_online': last_seen >= online_cutoff,
                    'first_seen_at': first_seen,
                    'first_seen_str': _hkt_dt(first_seen).strftime('%Y-%m-%d %H:%M'),
                    'last_seen_at': last_seen,
                    'last_seen_str': _hkt_dt(last_seen).strftime('%Y-%m-%d %H:%M'),
                    'last_ip': r['last_ip'],
                    'total_sessions': int(r['total_sessions']),
                    'total_dwell_sec': int(r['total_dwell_sec']),
                    'range_sessions': _safe_int(s_info.get('range_sessions'), 0, 0),
                    'range_dwell_sec': _safe_int(s_info.get('range_dwell_sec'), 0, 0),
                    'top_section': top_section,
                    'section_dwell': sec_map,
                })

            reverse = (order or 'desc').lower() != 'asc'
            sort_key_map = {
                'last_seen': lambda x: x['last_seen_at'],
                'first_seen': lambda x: x['first_seen_at'],
                'total_dwell': lambda x: x['total_dwell_sec'],
                'range_dwell': lambda x: x['range_dwell_sec'],
                'sessions': lambda x: x['range_sessions'],
            }
            key_fn = sort_key_map.get(sort_by or 'last_seen', sort_key_map['last_seen'])
            items.sort(key=key_fn, reverse=reverse)
            return {'ok': True, 'total': len(items), 'items': items}
        finally:
            conn.close()


def get_installation_detail(install_id, range_key='14d', db_path=None, now_ts=None):
    clean_id = _sanitize_text(install_id, '', 128)
    if not clean_id or not SAFE_ID_RE.match(clean_id):
        return False, {'error': 'install_id 格式无效'}, 400
    start_day, end_day, _, now = _hkt_day_bounds(range_key, now_ts)
    online_cutoff = now - ONLINE_WINDOW_SEC

    with DB_LOCK:
        conn = _connect(db_path)
        try:
            row = conn.execute('SELECT * FROM installations WHERE install_id = ?', (clean_id,)).fetchone()
            if not row:
                return False, {'error': '未找到该设备记录'}, 404

            sec_rows = conn.execute(
                '''
                SELECT section, sub_section, COALESCE(SUM(dwell_sec), 0) AS dwell_sec, COALESCE(SUM(visits), 0) AS visits
                FROM section_daily
                WHERE install_id = ? AND day >= ? AND day <= ?
                GROUP BY section, sub_section
                ORDER BY dwell_sec DESC
                ''',
                (clean_id, start_day, end_day),
            ).fetchall()

            sec_totals = {}
            sub_breakdown = []
            for r in sec_rows:
                sec = r['section']
                dwell = _safe_int(r['dwell_sec'], 0, 0)
                sec_totals[sec] = sec_totals.get(sec, 0) + dwell
                sub_breakdown.append({
                    'section': sec,
                    'sub_section': r['sub_section'],
                    'dwell_sec': dwell,
                    'visits': _safe_int(r['visits'], 0, 0),
                })

            daily_rows = conn.execute(
                '''
                SELECT day, COALESCE(SUM(dwell_sec), 0) AS dwell_sec, COALESCE(SUM(sessions), 0) AS sessions
                FROM hourly_activity
                WHERE install_id = ? AND day >= ? AND day <= ?
                GROUP BY day
                ORDER BY day ASC
                ''',
                (clean_id, start_day, end_day),
            ).fetchall()

            session_rows = conn.execute(
                '''
                SELECT * FROM sessions
                WHERE install_id = ?
                ORDER BY started_at DESC
                LIMIT 50
                ''',
                (clean_id,),
            ).fetchall()

            sessions = []
            for s in session_rows:
                s_start = float(s['started_at'])
                s_end = float(s['ended_at'])
                sessions.append({
                    'session_id': s['session_id'],
                    'started_at': s_start,
                    'started_str': _hkt_dt(s_start).strftime('%Y-%m-%d %H:%M:%S'),
                    'ended_at': s_end,
                    'ended_str': _hkt_dt(s_end).strftime('%H:%M:%S'),
                    'dwell_sec': int(s['dwell_sec']),
                    'app_version': s['app_version'],
                    'app_build': int(s['app_build']),
                    'primary_section': s['primary_section'],
                    'sections': _parse_sections_json(s['sections_json']),
                    'is_active': bool(s['is_active']) and (s_end >= online_cutoff),
                })

            inst = dict(row)
            inst['is_online'] = float(row['last_seen_at']) >= online_cutoff
            inst['first_seen_str'] = _hkt_dt(float(row['first_seen_at'])).strftime('%Y-%m-%d %H:%M:%S')
            inst['last_seen_str'] = _hkt_dt(float(row['last_seen_at'])).strftime('%Y-%m-%d %H:%M:%S')

            return True, {
                'ok': True,
                'installation': inst,
                'section_totals': [
                    {'section': k, 'dwell_sec': v}
                    for k, v in sorted(sec_totals.items(), key=lambda kv: kv[1], reverse=True)
                ],
                'sub_sections': sub_breakdown,
                'daily_trend': [dict(r) for r in daily_rows],
                'sessions': sessions,
            }, 200
        finally:
            conn.close()


def get_sections_report(range_key='7d', platform_filter='all', include_sim=True, db_path=None, now_ts=None):
    start_day, end_day, _, now = _hkt_day_bounds(range_key, now_ts)
    where_sql, where_params = _install_where_clause(platform_filter, 'all', include_sim, alias_prefix='i')

    with DB_LOCK:
        conn = _connect(db_path)
        try:
            rows = conn.execute(
                f'''
                SELECT
                    sd.section AS section,
                    sd.sub_section AS sub_section,
                    COALESCE(SUM(sd.dwell_sec), 0) AS dwell_sec,
                    COALESCE(SUM(sd.visits), 0) AS visits,
                    COUNT(DISTINCT sd.install_id) AS active_devices
                FROM section_daily sd
                JOIN installations i ON i.install_id = sd.install_id
                WHERE {where_sql} AND sd.day >= ? AND sd.day <= ?
                GROUP BY sd.section, sd.sub_section
                ''',
                where_params + [start_day, end_day],
            ).fetchall()

            sec_totals = {}
            sub_by_sec = {}
            for r in rows:
                sec = r['section']
                dwell = _safe_int(r['dwell_sec'], 0, 0)
                visits = _safe_int(r['visits'], 0, 0)
                sec_totals.setdefault(sec, {'dwell_sec': 0, 'visits': 0})
                sec_totals[sec]['dwell_sec'] += dwell
                sec_totals[sec]['visits'] += visits
                sub_by_sec.setdefault(sec, []).append({
                    'sub_section': r['sub_section'],
                    'dwell_sec': dwell,
                    'visits': visits,
                    'active_devices': _safe_int(r['active_devices'], 0, 0),
                })

            uniq_rows = conn.execute(
                f'''
                SELECT sd.section AS section, COUNT(DISTINCT sd.install_id) AS active_devices
                FROM section_daily sd
                JOIN installations i ON i.install_id = sd.install_id
                WHERE {where_sql} AND sd.day >= ? AND sd.day <= ?
                GROUP BY sd.section
                ''',
                where_params + [start_day, end_day],
            ).fetchall()
            uniq_map = {r['section']: _safe_int(r['active_devices'], 0, 0) for r in uniq_rows}

            daily_rows = conn.execute(
                f'''
                SELECT sd.day AS day, sd.section AS section, COALESCE(SUM(sd.dwell_sec), 0) AS dwell_sec
                FROM section_daily sd
                JOIN installations i ON i.install_id = sd.install_id
                WHERE {where_sql} AND sd.day >= ? AND sd.day <= ?
                GROUP BY sd.day, sd.section
                ORDER BY sd.day ASC
                ''',
                where_params + [start_day, end_day],
            ).fetchall()
            daily_by_sec = {}
            for r in daily_rows:
                daily_by_sec.setdefault(r['section'], []).append({
                    'day': r['day'],
                    'dwell_sec': _safe_int(r['dwell_sec'], 0, 0),
                })

            top_rows = conn.execute(
                f'''
                SELECT
                    sd.section AS section,
                    i.install_id AS install_id,
                    i.alias AS alias,
                    i.device_model AS device_model,
                    i.platform AS platform,
                    i.account_email AS account_email,
                    COALESCE(SUM(sd.dwell_sec), 0) AS dwell_sec,
                    COALESCE(SUM(sd.visits), 0) AS visits
                FROM section_daily sd
                JOIN installations i ON i.install_id = sd.install_id
                WHERE {where_sql} AND sd.day >= ? AND sd.day <= ?
                GROUP BY sd.section, i.install_id
                ORDER BY dwell_sec DESC
                ''',
                where_params + [start_day, end_day],
            ).fetchall()
            top_by_sec = {}
            for r in top_rows:
                sec = r['section']
                lst = top_by_sec.setdefault(sec, [])
                if len(lst) < 15:
                    lst.append(dict(r))

            grand_total = sum(v['dwell_sec'] for v in sec_totals.values())
            sections = []
            for sec in ('video', 'comics', 'live', 'forum', 'game', 'search', 'library'):
                totals = sec_totals.get(sec, {'dwell_sec': 0, 'visits': 0})
                dwell = totals['dwell_sec']
                active = uniq_map.get(sec, 0)
                subs = sorted(sub_by_sec.get(sec, []), key=lambda x: x['dwell_sec'], reverse=True)
                sections.append({
                    'section': sec,
                    'dwell_sec': dwell,
                    'visits': totals['visits'],
                    'active_devices': active,
                    'avg_per_device_sec': int(round(dwell / active)) if active > 0 else 0,
                    'share_pct': round((dwell / grand_total) * 100, 1) if grand_total > 0 else 0.0,
                    'sub_sections': subs,
                    'daily_trend': daily_by_sec.get(sec, []),
                    'top_devices': top_by_sec.get(sec, []),
                })

            sections.sort(key=lambda x: x['dwell_sec'], reverse=True)
            return {
                'ok': True,
                'range': range_key,
                'total_dwell_sec': grand_total,
                'sections': sections,
            }
        finally:
            conn.close()


def get_distribution_summary(db_path=None, now_ts=None):
    now = float(now_ts if now_ts is not None else time.time())
    ota_devices, tf_invites = _read_ota_devices_and_invites()
    pb_users = _read_pocketbase_users()

    with DB_LOCK:
        conn = _connect(db_path)
        try:
            inst_rows = conn.execute(
                'SELECT install_id, account_email, account_id, total_dwell_sec, platform, device_model, alias FROM installations',
            ).fetchall()

            by_email = {}
            for r in inst_rows:
                em = (r['account_email'] or '').strip().lower()
                if em:
                    by_email.setdefault(em, []).append(dict(r))

            accounts = []
            for u in pb_users:
                em = u['email']
                linked = by_email.get(em, [])
                accounts.append({
                    **u,
                    'device_count': len(linked),
                    'total_dwell_sec': sum(_safe_int(d.get('total_dwell_sec'), 0, 0) for d in linked),
                    'devices': [
                        {
                            'install_id': d['install_id'],
                            'platform': d['platform'],
                            'device_model': d['device_model'],
                            'alias': d['alias'],
                        }
                        for d in linked
                    ],
                })

            dl_rows = conn.execute(
                '''
                SELECT event_type, platform, COUNT(*) AS cnt
                FROM download_events
                WHERE created_at >= ?
                GROUP BY event_type, platform
                ORDER BY cnt DESC
                ''',
                (now - 30 * 24 * 3600,),
            ).fetchall()

            recent_dl = conn.execute(
                '''
                SELECT event_type, platform, app_version, client_ip, created_at
                FROM download_events
                ORDER BY created_at DESC
                LIMIT 30
                ''',
            ).fetchall()

            return {
                'ok': True,
                'accounts': accounts,
                'ota_devices': ota_devices,
                'testflight_invites': tf_invites,
                'download_summary_30d': [dict(r) for r in dl_rows],
                'recent_download_events': [
                    {
                        'event_type': r['event_type'],
                        'platform': r['platform'],
                        'app_version': r['app_version'],
                        'client_ip': r['client_ip'],
                        'created_str': _hkt_dt(float(r['created_at'])).strftime('%Y-%m-%d %H:%M:%S'),
                    }
                    for r in recent_dl
                ],
            }
        finally:
            conn.close()


def export_csv(range_key='30d', include_sim=True, db_path=None, now_ts=None):
    data = list_installations(
        range_key=range_key,
        platform_filter='all',
        channel_filter='all',
        search='',
        sort_by='last_seen',
        order='desc',
        include_sim=include_sim,
        db_path=db_path,
        now_ts=now_ts,
    )
    buf = io.StringIO()
    buf.write('\ufeff')
    writer = csv.writer(buf)
    writer.writerow([
        'install_id', 'alias', 'platform', 'device_form', 'device_model',
        'os_version', 'app_version', 'app_build', 'install_channel',
        'account_email', 'account_name', 'is_online',
        'first_seen_hkt', 'last_seen_hkt', 'range_sessions', 'range_dwell_sec',
        'total_sessions', 'total_dwell_sec',
        'video_sec', 'comics_sec', 'live_sec', 'forum_sec', 'game_sec', 'search_sec', 'library_sec',
        'notes',
    ])
    for item in data.get('items', []):
        sec = item.get('section_dwell', {})
        writer.writerow([
            item['install_id'],
            item['alias'],
            item['platform'],
            item['device_form'],
            item['device_model'],
            item['os_version'],
            item['app_version'],
            item['app_build'],
            item['install_channel'],
            item['account_email'],
            item['account_name'],
            'online' if item['is_online'] else 'offline',
            item['first_seen_str'],
            item['last_seen_str'],
            item['range_sessions'],
            item['range_dwell_sec'],
            item['total_sessions'],
            item['total_dwell_sec'],
            sec.get('video', 0),
            sec.get('comics', 0),
            sec.get('live', 0),
            sec.get('forum', 0),
            sec.get('game', 0),
            sec.get('search', 0),
            sec.get('library', 0),
            item['notes'],
        ])
    return buf.getvalue().encode('utf-8')

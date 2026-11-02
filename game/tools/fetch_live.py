#!/usr/bin/env python3
"""Fetch live config tables using caller-supplied, short-lived session values."""

import argparse
import datetime as dt
import hashlib
import json
import os
import time
import urllib.error
import urllib.request
import urllib.parse
import re
import uuid
from pathlib import Path


def parse_args():
    parser = argparse.ArgumentParser()
    parser.add_argument("--map", default="data/live_config_map.json")
    parser.add_argument("--output-dir", default="net/live_configs")
    parser.add_argument("--report", default="net/live_fetch_report.json")
    parser.add_argument("--tables", nargs="*", help="Fetch only these tables; default is all manifest entries")
    parser.add_argument("--delay", type=float, default=0.1, help="Delay between requests in seconds")
    parser.add_argument("--timeout", type=float, default=90)
    parser.add_argument("--base-url", default="https://configs.chickgoddess.com")
    parser.add_argument("--platform", default=os.environ.get("PNK_PLATFORM", "WEBSITE"))
    parser.add_argument("--version", default=os.environ.get("PNK_VERSION", "23.0.0"))
    parser.add_argument("--ab-group", default=os.environ.get("PNK_ABTEST_GROUP", "Gadget_group"))
    parser.add_argument("--country", default=os.environ.get("PNK_COUNTRY", "HK"))
    return parser.parse_args()


def required_env(name):
    value = os.environ.get(name)
    if not value:
        raise SystemExit(f"Missing required environment variable: {name}")
    return value


def request_headers(args, context):
    now = dt.datetime.now(dt.timezone.utc)
    local = now.astimezone()
    utc = now.isoformat(timespec="microseconds").replace("+00:00", "Z")
    local_text = local.isoformat(timespec="microseconds")
    return {
        "PNK-Env": "WebGLPlayer",
        "PNK-Platform": args.platform,
        "PNK-Player-ID": required_env("PNK_PLAYER_ID"),
        "PNK-Session-Id": required_env("PNK_SESSION_ID"),
        "PNK-Config-Ctx": context,
        "PNK-Version": args.version,
        "PNK-Device-ID": required_env("PNK_DEVICE_ID"),
        "PNK-Request-Id": str(uuid.uuid4()),
        "PNK-Retry": "1",
        "PNK-request-client-system-time-utc": utc,
        "PNK-request-client-system-time": local_text,
        "PNK-request-client-start-time": utc,
        "PNK-ABTest-Group": args.ab_group,
        "X-Country": args.country,
        "Content-Type": "application/x-msgpack",
    }


def context_from_url(url):
    marker = "/configs_msgpack/"
    if marker not in url:
        raise ValueError(f"Cannot derive config context from URL: {url}")
    return url.split(marker, 1)[1].split("/", 1)[0]


class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        return None


def main():
    args = parse_args()
    base = urllib.parse.urlsplit(args.base_url)
    allowed = (base.scheme == "https" and base.netloc == "configs.chickgoddess.com") or (
        base.scheme == "http" and base.hostname in ("127.0.0.1", "localhost", "::1")
        and base.username is None and base.password is None)
    if not allowed or base.path not in ("", "/") or base.query or base.fragment:
        raise SystemExit("Base URL must be the config HTTPS host or a loopback HTTP fixture")
    if args.delay < 0 or args.timeout <= 0:
        raise SystemExit("Delay must be nonnegative and timeout must be positive")
    opener = urllib.request.build_opener(NoRedirect())
    config_data = json.loads(Path(args.map).read_text(encoding="utf-8"))["config_data"]
    targets = args.tables or sorted(config_data)
    unknown = sorted(set(targets) - set(config_data))
    if unknown:
        raise SystemExit(f"Unknown table names: {', '.join(unknown)}")

    for table in targets:
        if not re.fullmatch(r"[A-Za-z0-9_.-]+", table) or table in (".", ".."):
            raise SystemExit("Unsafe table name in manifest")
        if not config_data[table]["url"].startswith("/cc_api/configs_msgpack/"):
            raise SystemExit("Unexpected config path in manifest")
    for name in ("PNK_PLAYER_ID", "PNK_SESSION_ID", "PNK_DEVICE_ID"):
        required_env(name)
    output_dir = Path(args.output_dir)
    output_dir.mkdir(parents=True, exist_ok=True)
    report = {}

    for index, table in enumerate(targets):
        entry = config_data[table]
        url = args.base_url.rstrip("/") + "/" + entry["url"].lstrip("/")
        request = urllib.request.Request(
            url,
            headers=request_headers(args, context_from_url(entry["url"])),
            method="GET",
        )
        started = time.monotonic()
        try:
            with opener.open(request, timeout=args.timeout) as response:
                body = response.read()
                status = response.status
            md5 = hashlib.md5(body).hexdigest()
            match = md5 == entry["hash"]
            if match:
                (output_dir / f"{table}.bin").write_bytes(body)
            report[table] = {
                "http": status,
                "bytes": len(body),
                "md5": md5,
                "server_hash": entry["hash"],
                "hash_match": match,
                "saved": match,
                "elapsed_ms": round((time.monotonic() - started) * 1000),
            }
        except urllib.error.HTTPError as error:
            body = error.read()
            report[table] = {
                "http": error.code,
                "bytes": len(body),
                "server_hash": entry["hash"],
                "hash_match": False,
                "saved": False,
                "error": "HTTP request failed; response body omitted",
                "elapsed_ms": round((time.monotonic() - started) * 1000),
            }
        except (urllib.error.URLError, TimeoutError) as error:
            report[table] = {
                "http": None,
                "bytes": 0,
                "server_hash": entry["hash"],
                "hash_match": False,
                "saved": False,
                "error": str(error),
                "elapsed_ms": round((time.monotonic() - started) * 1000),
            }

        item = report[table]
        print(
            f"[{index + 1}/{len(targets)}] {table}: "
            f"http={item['http']} bytes={item['bytes']} match={item['hash_match']}"
        )
        Path(args.report).parent.mkdir(parents=True, exist_ok=True)
        Path(args.report).write_text(
            json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8"
        )
        if not item["saved"]:
            raise SystemExit("Fetch stopped after failure; inspect the report before retrying")
        if index + 1 < len(targets) and args.delay > 0:
            time.sleep(args.delay)


if __name__ == "__main__":
    main()

#!/usr/bin/env python3
"""Minimal CDP driver: open a URL in Chrome, collect console/errors, screenshot.

Usage: cdp_shot.py <url> <out.png> [--wait 2] [--eval "expr"] [--width 1280] [--height 800]
"""
from __future__ import annotations

import argparse
import asyncio
import base64
import json
import os
import shutil
import subprocess
import tempfile
import urllib.request

import websockets

CHROME = "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome"


async def run(url: str, out: str, wait: float, eval_expr: str | None, width: int, height: int) -> int:
    profile = tempfile.mkdtemp(prefix="cdp-profile-")
    port = 9400 + (os.getpid() % 90)
    proc = subprocess.Popen([
        CHROME, f"--user-data-dir={profile}", f"--remote-debugging-port={port}",
        f"--window-size={width},{height}", "--headless=new", "--disable-gpu",
        "--use-gl=swiftshader", "--enable-unsafe-swiftshader", "--hide-scrollbars",
        "--no-first-run", "--no-default-browser-check", "--disable-extensions",
        url,
    ], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    ws_url = None
    try:
        for _ in range(100):
            try:
                data = json.load(urllib.request.urlopen(f"http://127.0.0.1:{port}/json/list", timeout=0.5))
                pages = [t for t in data if t.get("type") == "page"]
                if pages:
                    ws_url = pages[0]["webSocketDebuggerUrl"]
                    break
            except Exception:
                pass
            await asyncio.sleep(0.1)
        if not ws_url:
            print("ERROR: no CDP target")
            return 2
        mid = 0
        logs: list[str] = []

        async def send(ws, method, params=None):
            nonlocal mid
            mid += 1
            my = mid
            await ws.send(json.dumps({"id": my, "method": method, "params": params or {}}))
            while True:
                raw = json.loads(await ws.recv())
                if raw.get("id") == my:
                    return raw
                ev = raw.get("method", "")
                if ev == "Runtime.consoleAPICalled":
                    args = [a.get("value", a.get("description", "")) for a in raw["params"].get("args", [])]
                    logs.append(f"[console.{raw['params']['type']}] " + " ".join(map(str, args)))
                elif ev == "Runtime.exceptionThrown":
                    logs.append("[exception] " + json.dumps(raw["params"].get("exceptionDetails", {}))[:800])
                elif ev == "Log.entryAdded":
                    e = raw["params"]["entry"]
                    logs.append(f"[log.{e['level']}] {e['text'][:400]}")

        async with websockets.connect(ws_url, max_size=64 * 1024 * 1024) as ws:
            await send(ws, "Runtime.enable")
            await send(ws, "Log.enable")
            await send(ws, "Page.enable")
            await asyncio.sleep(wait)
            if eval_expr:
                res = await send(ws, "Runtime.evaluate", {"expression": eval_expr, "awaitPromise": True, "returnByValue": True})
                print("eval:", json.dumps(res.get("result", {}).get("result", {}), ensure_ascii=False)[:2000])
            shot = await send(ws, "Page.captureScreenshot", {"format": "png"})
            data = shot.get("result", {}).get("data")
            if data:
                with open(out, "wb") as fh:
                    fh.write(base64.b64decode(data))
        for line in logs[-40:]:
            print(line)
        print("shot:", out, os.path.getsize(out) if os.path.exists(out) else "missing")
        return 0
    finally:
        proc.terminate()
        try:
            proc.wait(timeout=8)
        except Exception:
            proc.kill()
        shutil.rmtree(profile, ignore_errors=True)


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("url")
    ap.add_argument("out")
    ap.add_argument("--wait", type=float, default=2.0)
    ap.add_argument("--eval", default=None)
    ap.add_argument("--width", type=int, default=1280)
    ap.add_argument("--height", type=int, default=800)
    a = ap.parse_args()
    return asyncio.run(run(a.url, a.out, a.wait, a.eval, a.width, a.height))


if __name__ == "__main__":
    raise SystemExit(main())

#!/usr/bin/env python3
"""Launch the original WebGL client from the warm profile and dump wasm memory.

The dump is used to recover the IL2CPP code registration (methodPointers) from
the running process, which is not present in global-metadata.dat.
"""
from __future__ import annotations

import asyncio
import base64
import json
import shutil
import subprocess
import sys
import tempfile
import time
import urllib.request
from pathlib import Path

import websockets

CHROME = "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome"
URL = "https://www.lust-goddess.com/play"
PROFILE_SRC = Path("/Users/itou/Documents/project-hgame/replica/warm_profile_20261029_lobby")
OUT = Path("/tmp/unity_mem.bin")
HOOK = """
(function () {
  const orig = window.createUnityInstance;
  if (!orig) return;
  window.createUnityInstance = async function () {
    const inst = await orig.apply(this, arguments);
    window.__unity = inst;
    try { window.__module = inst.Module || inst.module || null; } catch (e) {}
    return inst;
  };
})();
"""


async def main() -> int:
    profile = Path(tempfile.mkdtemp(prefix="lg-mem-profile-"))
    shutil.rmtree(profile)
    print("copying profile ->", profile, flush=True)
    subprocess.run(["ditto", str(PROFILE_SRC), str(profile)], check=True)
    port = 9600
    proc = subprocess.Popen([
        CHROME, f"--user-data-dir={profile}", f"--remote-debugging-port={port}",
        "--window-size=1280,800", "--headless=new", "--disable-gpu",
        "--use-gl=swiftshader", "--enable-unsafe-swiftshader", "--hide-scrollbars",
        "--no-first-run", "--no-default-browser-check", "--mute-audio",
        URL,
    ], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    try:
        ws_url = None
        for _ in range(200):
            try:
                data = json.load(urllib.request.urlopen(f"http://127.0.0.1:{port}/json/list", timeout=0.5))
                pages = [t for t in data if t.get("type") == "page"]
                if pages:
                    ws_url = pages[0]["webSocketDebuggerUrl"]
                    break
            except Exception:
                pass
            await asyncio.sleep(0.2)
        if not ws_url:
            print("no target")
            return 2
        async with websockets.connect(ws_url, max_size=256 * 1024 * 1024) as ws:
            mid = 0
            logs = []

            async def send(method, params=None, timeout=120):
                nonlocal mid
                mid += 1
                my = mid
                await ws.send(json.dumps({"id": my, "method": method, "params": params or {}}))
                while True:
                    raw = json.loads(await asyncio.wait_for(ws.recv(), timeout=timeout))
                    if raw.get("id") == my:
                        return raw.get("result", {})
                    ev = raw.get("method")
                    if ev == "Runtime.consoleAPICalled":
                        logs.append(" ".join(str(a.get("value", a.get("description"))) for a in raw["params"].get("args", [])))
                    elif ev == "Runtime.exceptionThrown":
                        logs.append("[exc] " + json.dumps(raw["params"].get("exceptionDetails", {}).get("exception", {}))[:300])

            async def js(expr, timeout=120):
                r = await send("Runtime.evaluate", {"expression": expr, "awaitPromise": True, "returnByValue": True}, timeout)
                return r.get("result", {}).get("value")

            await send("Runtime.enable")
            await send("Page.enable")
            await send("Page.addScriptToEvaluateOnNewDocument", {"source": HOOK})
            await asyncio.sleep(3)
            print("title:", await js("document.title"), flush=True)
            # click the PLAY button (site uses #playButton or a .play button)
            for sel in ["#playButton", ".play-button", "[data-action=play]", "button"]:
                ok = await js("(function(){const n=document.querySelector(%s); if(!n) return false; n.click(); return n.textContent.trim().slice(0,20);})()" % json.dumps(sel))
                if ok:
                    print("clicked", sel, ok, flush=True)
                    break
            # wait for Unity instance
            for i in range(240):
                info = await js("JSON.stringify({u:!!window.__unity, m:!!(window.__unity&&window.__unity.Module), len:(window.__unity&&window.__unity.Module&&window.__unity.Module.HEAPU8)?window.__unity.Module.HEAPU8.length:0, txt:document.body.innerText.slice(0,80)})")
                d = json.loads(info or "{}")
                if d.get("len"):
                    print("unity module ready", d, flush=True)
                    break
                if i % 20 == 0:
                    print("waiting", i, d, flush=True)
                await asyncio.sleep(1)
            else:
                print("unity instance not detected; logs:", logs[-10:])
                return 3
            total = int(d["len"])
            print("dumping", total, "bytes", flush=True)
            chunk = 4 * 1024 * 1024
            with open(OUT, "wb") as fh:
                off = 0
                while off < total:
                    n = min(chunk, total - off)
                    b64 = await js(
                        "(function(){const u=window.__unity.Module.HEAPU8;const s=u.subarray(%d,%d);let t='';const C=0x8000;for(let i=0;i<s.length;i+=C){t+=String.fromCharCode.apply(null,s.subarray(i,i+C));}return btoa(t);})()" % (off, off + n),
                        timeout=300,
                    )
                    if not b64:
                        print("chunk failed at", off)
                        return 4
                    fh.write(base64.b64decode(b64))
                    off += n
                    if off % (32 * 1024 * 1024) == 0:
                        print("dumped", off, flush=True)
            print("saved", OUT, OUT.stat().st_size, flush=True)
            return 0
    finally:
        proc.terminate()
        try:
            proc.wait(timeout=10)
        except Exception:
            proc.kill()
        shutil.rmtree(profile, ignore_errors=True)


if __name__ == "__main__":
    raise SystemExit(asyncio.run(main()))

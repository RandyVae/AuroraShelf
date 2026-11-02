#!/usr/bin/env python3
"""Dump the Unity WebGL heap from the sandboxed game iframe via CDP contexts.

The play page embeds the client in a sandboxed iframe, so window.__unity lives
in a separate JavaScript execution context. CDP can evaluate there by contextId.
"""
from __future__ import annotations

import asyncio
import base64
import json
import subprocess
import time
import urllib.request
from pathlib import Path

import websockets

CHROME = "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome"
PROFILE = "/Users/itou/Documents/project-hgame/replica/warm_profile_20261029_lobby"
URL = "https://www.lust-goddess.com/play"
OUT = Path("/tmp/unity_mem.bin")
PORT = 9610


async def main() -> int:
    chrome = subprocess.Popen([
        CHROME, f"--user-data-dir=/tmp/lg-mem-profile-2", f"--remote-debugging-port={PORT}",
        "--window-size=1280,800", "--headless=new", "--disable-gpu",
        "--use-gl=swiftshader", "--enable-unsafe-swiftshader", "--hide-scrollbars",
        "--no-first-run", "--no-default-browser-check", "--mute-audio", URL,
    ], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    try:
        ws_url = None
        for _ in range(150):
            try:
                data = json.load(urllib.request.urlopen(f"http://127.0.0.1:{PORT}/json/list", timeout=0.5))
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
        async with websockets.connect(ws_url, max_size=512 * 1024 * 1024) as ws:
            mid = 0
            contexts = {}
            seen = set()

            async def pump_until_id(my, timeout=180):
                while True:
                    raw = json.loads(await asyncio.wait_for(ws.recv(), timeout=timeout))
                    if raw.get("id") == my:
                        return raw.get("result", {})
                    if raw.get("method") == "Runtime.executionContextCreated":
                        ctx = raw["params"]["context"]
                        contexts[ctx["id"]] = ctx
                        print("ctx", ctx["id"], ctx.get("origin"), ctx.get("name"), flush=True)

            async def send(method, params=None, timeout=180):
                nonlocal mid
                mid += 1
                my = mid
                await ws.send(json.dumps({"id": my, "method": method, "params": params or {}}))
                return await pump_until_id(my, timeout)

            async def js(expr, ctx=None, timeout=180):
                params = {"expression": expr, "awaitPromise": True, "returnByValue": True}
                if ctx:
                    params["contextId"] = ctx
                r = await send("Runtime.evaluate", params, timeout)
                res = r.get("result", {})
                if res.get("subtype") == "error":
                    return {"__error": res.get("description")}
                return res.get("value")

            await send("Runtime.enable")
            await send("Page.enable")
            # wait for the sandboxed game iframe context and find the PLAY control
            target_ctx = None
            for attempt in range(60):
                await asyncio.sleep(1)
                for cid, ctx in list(contexts.items()):
                    if cid in seen:
                        continue
                    seen.add(cid)
                    info = await js("JSON.stringify({html:document.documentElement?document.documentElement.outerHTML.length:0, text:document.body?document.body.innerText.slice(0,120):'', canv:document.querySelectorAll?document.querySelectorAll('canvas').length:0})", cid)
                    print("probe", cid, ctx.get("origin"), info, flush=True)
                if contexts:
                    # probe every context for the PLAY text (case-insensitive, any element)
                    for cid in list(contexts.keys()):
                        has_play = await js("!!([...document.querySelectorAll('*')].find(b=>b.children.length===0 && /^PLAY$/i.test((b.textContent||'').trim())))", cid)
                        if has_play:
                            target_ctx = cid
                            break
                if target_ctx:
                    break
            if not target_ctx:
                print("no play button context found")
                return 3
            # click PLAY in that context
            clicked = await js("(function(){const b=document.querySelector('#playButton')||[...document.querySelectorAll('button,div,a')].find(x=>/^PLAY$/i.test(x.textContent.trim())); if(!b) return 'nobtn'; b.click(); return 'clicked';})()", target_ctx)
            print("play:", clicked, flush=True)
            # wait for unity instance in that context
            info = None
            for i in range(300):
                info = await js(
                    "JSON.stringify({u:!!(window.__unity||window.unityInstance),m:!!((window.__unity||window.unityInstance)&&(window.__unity||window.unityInstance).Module),len:((window.__unity||window.unityInstance)&&(window.__unity||window.unityInstance).Module&&(window.__unity||window.unityInstance).Module.HEAPU8)?(window.__unity||window.unityInstance).Module.HEAPU8.length:0, txt:document.body?document.body.innerText.slice(0,60):''})",
                    target_ctx,
                )
                try:
                    d = json.loads(info) if isinstance(info, str) else {}
                except Exception:
                    d = {}
                if d.get("len"):
                    print("ready", d, flush=True)
                    break
                if i % 15 == 0:
                    print("wait", i, d, flush=True)
                await asyncio.sleep(1)
            if not d.get("len"):
                print("unity not ready", flush=True)
                return 4
            total = int(d["len"])
            print("heap", total, flush=True)
            chunk = 4 * 1024 * 1024
            with open(OUT, "wb") as fh:
                off = 0
                while off < total:
                    n = min(chunk, total - off)
                    b64 = await js(
                        "(function(){const M=(window.__unity||window.unityInstance).Module;const u=M.HEAPU8;const s=u.subarray(%d,%d);let t='';const C=0x8000;for(let i=0;i<s.length;i+=C){t+=String.fromCharCode.apply(null,s.subarray(i,i+C));}return btoa(t);})()" % (off, off + n),
                        target_ctx,
                        timeout=300,
                    )
                    if not isinstance(b64, str) or not b64:
                        print("chunk fail", off, b64, flush=True)
                        return 5
                    fh.write(base64.b64decode(b64))
                    off += n
                    if off % (64 * 1024 * 1024) == 0:
                        print("dumped", off, flush=True)
            print("saved", OUT, OUT.stat().st_size, flush=True)
            return 0
    finally:
        chrome.terminate()
        try:
            chrome.wait(timeout=10)
        except Exception:
            chrome.kill()


if __name__ == "__main__":
    raise SystemExit(asyncio.run(main()))

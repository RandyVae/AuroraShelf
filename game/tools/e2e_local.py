#!/usr/bin/env python3
"""End-to-end driver for the local edition (headless Chrome + CDP).

Runs a comprehensive player journey against http://127.0.0.1:8111/index.html and
writes screenshots to /tmp/lg-e2e.
"""
from __future__ import annotations

import asyncio
import base64
import json
import os
import shutil
import socket
import subprocess
import sys
import tempfile
import time
import urllib.request
from pathlib import Path

import websockets

CHROME = "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome"
OUT = Path("/tmp/lg-e2e")


class Cdp:
    def __init__(self, ws, mid=0):
        self.ws = ws
        self.mid = mid
        self.logs = []
        self.reqs = []
        self.fails = []

    async def send(self, method, params=None):
        self.mid += 1
        my = self.mid
        await self.ws.send(json.dumps({"id": my, "method": method, "params": params or {}}))
        while True:
            raw = json.loads(await self.ws.recv())
            if raw.get("id") == my:
                if "error" in raw:
                    raise RuntimeError(raw["error"])
                return raw.get("result", {})
            ev = raw.get("method")
            if ev == "Network.requestWillBeSent":
                self.reqs.append(raw["params"]["request"]["url"])
            elif ev == "Network.loadingFailed":
                self.fails.append(raw["params"].get("errorText", "?"))
            if ev == "Runtime.consoleAPICalled":
                args = [a.get("value", a.get("description", "")) for a in raw["params"].get("args", [])]
                self.logs.append("[console] " + " ".join(map(str, args)))
            elif ev == "Runtime.exceptionThrown":
                d = raw["params"].get("exceptionDetails", {})
                self.logs.append("[exception] " + json.dumps(d.get("exception", {}), ensure_ascii=False)[:500])

    async def eval(self, expr):
        r = await self.send("Runtime.evaluate", {"expression": expr, "awaitPromise": True, "returnByValue": True})
        return r.get("result", {}).get("value")

    async def shot(self, name):
        r = await self.send("Page.captureScreenshot", {"format": "png"})
        data = r.get("data")
        if data:
            OUT.mkdir(parents=True, exist_ok=True)
            (OUT / (name + ".png")).write_bytes(base64.b64decode(data))

    async def tap(self, selector):
        ok = await self.eval("(function(){const n=document.querySelector(%s); if(!n) return false; n.click(); return true;})()" % json.dumps(selector))
        await asyncio.sleep(0.35)
        return ok


async def run():
    OUT.mkdir(parents=True, exist_ok=True)
    for f in OUT.glob("*.png"):
        f.unlink()
    profile = tempfile.mkdtemp(prefix="lg-e2e-profile-")
    with socket.socket() as sock:
        sock.bind(("127.0.0.1", 0))
        http_port = sock.getsockname()[1]
    with socket.socket() as sock:
        sock.bind(("127.0.0.1", 0))
        cdp_port = sock.getsockname()[1]
    base = f"http://127.0.0.1:{http_port}/index.html"
    server = subprocess.Popen(
        [sys.executable, "web/serve.py", str(http_port)], cwd=Path(__file__).resolve().parents[1],
        stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL,
    )
    for _ in range(60):
        try:
            with urllib.request.urlopen(base, timeout=0.5):
                break
        except Exception:
            await asyncio.sleep(0.1)
    else:
        server.terminate()
        raise RuntimeError("local game server did not become ready")
    proc = subprocess.Popen([
        CHROME, f"--user-data-dir={profile}", f"--remote-debugging-port={cdp_port}",
        "--window-size=420,880", "--headless=new", "--disable-gpu",
        "--use-gl=swiftshader", "--enable-unsafe-swiftshader", "--hide-scrollbars",
        "--no-first-run", "--no-default-browser-check", base,
    ], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    error = None
    try:
        ws_url = None
        for _ in range(120):
            try:
                data = json.load(urllib.request.urlopen(f"http://127.0.0.1:{cdp_port}/json/list", timeout=0.5))
                pages = [t for t in data if t.get("type") == "page"]
                if pages:
                    ws_url = pages[0]["webSocketDebuggerUrl"]
                    break
            except Exception:
                pass
            await asyncio.sleep(0.1)
        if not ws_url:
            raise RuntimeError("no CDP target")
        async with websockets.connect(ws_url, max_size=64 * 1024 * 1024) as ws:
            c = Cdp(ws)
            await c.send("Runtime.enable")
            await c.send("Page.enable")
            await c.send("Network.enable")
            await c.send("Network.setBlockedURLs", {"urls": ["https://*", "wss://*"]})
            await asyncio.sleep(2.2)
            await c.shot("01-title")

            steps = []
            steps.append(("start", await c.tap("#btn-start")))
            await asyncio.sleep(0.6)
            await c.shot("02-hub")

            # Go to Campaign
            steps.append(("campaign", await c.tap("#nav-campaign")))
            await asyncio.sleep(0.5)
            await c.shot("03-campaign")

            steps.append(("chapter1", await c.tap(".chapter-card")))
            await asyncio.sleep(0.4)
            await c.shot("04-stages")

            # Tap Stage 1 -> Opens stage action sheet
            steps.append(("stage1_sheet", await c.tap(".stage-btn")))
            await asyncio.sleep(0.4)
            await c.shot("04b-stage-sheet")

            # Tap Launch
            steps.append(("stage1_launch", await c.tap("#btn-stage-launch")))
            await asyncio.sleep(0.8)
            if not all(value for _, value in steps if isinstance(value, bool)):
                raise AssertionError(f"required journey navigation failed: {steps}")
            await c.shot("05-story")

            # Tap through story
            for _ in range(12):
                await c.eval("document.querySelector('#screen-story').click()")
                await asyncio.sleep(0.12)
            await asyncio.sleep(1.0)
            await c.shot("06-battle-start")
            await c.eval("(function(){const b=document.querySelector('#btn-battle-speed'); if(b) b.click();})()")

            # Let battle run to completion
            for _ in range(50):
                await asyncio.sleep(1.0)
                scr = await c.eval("({screen:document.querySelector('.screen.active').id,cg:!document.querySelector('#cg-cinema-overlay').hidden,victory:!document.querySelector('#battle-victory-cutin').hidden})")
                if scr["screen"] == "screen-result":
                    break
                if scr["cg"]:
                    await c.tap("#btn-cg-skip")
            await c.shot("07-result")
            state = await c.eval("JSON.stringify({scr:document.querySelector('.screen.active').id,stars:APP.state.stars,cleared:Object.keys(APP.state.cleared),gold:APP.state.gold,energy:APP.state.energy})")
            state_data = json.loads(state)
            if state_data["scr"] != "screen-result" or not state_data["cleared"]:
                raise AssertionError(f"first battle did not complete: {state_data}")
            await c.tap("#btn-result-next")
            await asyncio.sleep(0.6)
            await c.shot("08-after-result")

            # Now Stage 1 has 3 stars! Test SWEEP feature
            await c.tap(".stage-btn")
            await asyncio.sleep(0.4)
            await c.shot("08b-stage-sweep-sheet")
            sweep_visible = await c.eval("document.querySelector('#stage-sweep-group').style.display !== 'none'")
            steps.append(("sweep_group_visible", sweep_visible))
            await c.tap("#btn-stage-sweep1")
            await asyncio.sleep(0.5)
            await c.tap("#btn-stage-action-close")
            await asyncio.sleep(0.3)

            # Return to Hub & Test Missions
            await c.eval("document.querySelector('[data-back]').click()")
            await asyncio.sleep(0.4)
            await c.tap("#btn-missions")
            await asyncio.sleep(0.5)
            await c.shot("08c-missions")
            # Claim daily mission if ready
            await c.eval("(function(){ const btn = document.querySelector('#mission-list .btn.primary'); if(btn) btn.click(); })()")
            await asyncio.sleep(0.4)
            await c.tap("#btn-mission-close")
            await asyncio.sleep(0.3)

            # Squad View
            await c.tap("#nav-squad")
            await asyncio.sleep(0.5)
            await c.shot("09-squad")

            # Roster View & Hero Progression (Level up + Equip)
            await c.eval("document.querySelector('[data-back]').click()")
            await asyncio.sleep(0.3)
            await c.tap("#nav-roster")
            await asyncio.sleep(0.6)
            await c.shot("10-roster")
            await c.tap("#btn-cg")
            await asyncio.sleep(0.8)
            await c.shot("10b-cg")
            await c.eval("document.querySelector('#btn-cg-close').click()")
            await asyncio.sleep(0.2)

            # Open Hero Detail (Owned Hero)
            await c.tap("#roster-grid .hero-card:not(.locked)")
            await asyncio.sleep(0.5)
            await c.shot("11-detail")

            # Test Level Up
            lvl_before = await c.eval("document.querySelector('#detail-lvl').textContent")
            await c.tap("#btn-hero-upgrade")
            await asyncio.sleep(0.5)
            lvl_after = await c.eval("document.querySelector('#detail-lvl').textContent")
            steps.append(("hero_upgrade", f"{lvl_before} -> {lvl_after}"))
            await c.shot("11b-detail-upgraded")

            # Test Equip Weapon
            await c.eval("document.querySelector('#detail-equip-slots .equip-slot').click()")
            await asyncio.sleep(0.5)
            await c.shot("11c-equip-picker")
            await c.eval("(function(){ const b = document.querySelector('#equip-picker-list .btn.primary'); if(b) b.click(); })()")
            await asyncio.sleep(0.5)
            await c.eval("document.querySelector('#btn-detail-close').click()")

            # Gacha
            await c.eval("document.querySelector('[data-back]').click()")
            await asyncio.sleep(0.3)
            await c.tap("#nav-gacha")
            await asyncio.sleep(0.5)
            await c.shot("12-gacha")
            await c.tap("#btn-pull1")
            await asyncio.sleep(0.9)
            await c.shot("13-gacha-reveal")
            await c.eval("document.querySelector('#btn-reveal-ok').click()")
            await asyncio.sleep(0.3)
            await c.tap("#btn-pull10")
            await asyncio.sleep(0.6)
            await c.shot("13b-gacha-ten")
            owned = await c.eval("JSON.stringify({owned:APP.state.owned.length,pity:APP.state.pity,keys:APP.state.keys,gold:APP.state.gold})")
            await c.eval("document.querySelector('#btn-reveal-ok').click()")

            # Shop
            await c.eval("document.querySelector('[data-back]').click()")
            await asyncio.sleep(0.3)
            await c.tap("#nav-shop")
            await asyncio.sleep(0.4)
            await c.shot("14-shop")
            await c.eval("document.querySelector('.shop-row .btn').click()")
            await asyncio.sleep(0.4)

            # Settings
            await c.eval("document.querySelector('[data-back]').click()")
            await asyncio.sleep(0.3)
            await c.tap("#btn-settings")
            await asyncio.sleep(0.4)
            await c.shot("15-settings")
            await c.eval("document.querySelector('#set-adult').click()")
            await asyncio.sleep(0.2)

            # Reload to verify persistence
            await c.send("Page.reload")
            await asyncio.sleep(3.0)
            await c.shot("16-reload")
            persisted = await c.eval("JSON.stringify({v:APP.state.v,scr:document.querySelector('.screen.active').id, owned:APP.data?APP.state.owned.length:null, adult:APP.state&&APP.state.settings.adult, gold:APP.state&&APP.state.gold, cleared:APP.state?Object.keys(APP.state.cleared):[], kittyLvl:APP.state&&APP.state.roster&&APP.state.roster.kitty&&APP.state.roster.kitty.level})")

            print("steps:", steps)
            print("battle_state:", state)
            print("after_pull:", owned)
            print("persisted:", persisted)
            print("screen:", await c.eval("document.querySelector('.screen.active').id"))
            print("errors:", [l for l in c.logs if "exception" in l][:10])
            print("screenshots:", sorted(p.name for p in OUT.glob("*.png")))

            # Fixed external check: exclude data: and blob: URIs
            external = sorted({
                u for u in c.reqs
                if not u.startswith(base.rsplit("/index.html", 1)[0])
                and (u.startswith("http:") or u.startswith("https:") or u.startswith("ws:") or u.startswith("wss:"))
            })
            print("requests:", len(c.reqs), "external_http:", external)
            print("blocked/failed:", len(c.fails), c.fails[:5])
            print("audio_requests:", len([u for u in c.reqs if ".m4a" in u]))
    except Exception as exc:  # noqa: BLE001
        error = exc
        print("E2E FAILED:", type(exc).__name__, exc)
    finally:
        proc.terminate()
        try:
            proc.wait(timeout=8)
        except Exception:
            proc.kill()
        shutil.rmtree(profile, ignore_errors=True)
        server.terminate()
        try:
            server.wait(timeout=3)
        except Exception:
            server.kill()
    return 1 if error else 0


if __name__ == "__main__":
    raise SystemExit(asyncio.run(run()))

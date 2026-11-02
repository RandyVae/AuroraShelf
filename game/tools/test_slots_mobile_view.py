#!/usr/bin/env python3
"""Mobile viewport check for slots cabinet using exact device metrics emulation."""
import asyncio
import base64
import json
import shutil
import socket
import subprocess
import tempfile
import urllib.request
from pathlib import Path
import websockets
from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
CHROME = "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome"
OUT = ROOT / "qa" / "slots_mobile"

async def check_viewport(width, height, name):
    OUT.mkdir(parents=True, exist_ok=True)
    with socket.socket() as sock:
        sock.bind(("127.0.0.1", 0))
        http_port = sock.getsockname()[1]
    with socket.socket() as sock:
        sock.bind(("127.0.0.1", 0))
        cdp_port = sock.getsockname()[1]

    server = subprocess.Popen(["python3", "web/serve.py", str(http_port)], cwd=ROOT, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    profile = tempfile.mkdtemp(prefix="lg-slots-mobile-")
    chrome = subprocess.Popen([
        CHROME,
        f"--user-data-dir={profile}",
        f"--remote-debugging-port={cdp_port}",
        "--headless=new",
        "--disable-gpu",
        "--use-gl=swiftshader",
        "--hide-scrollbars",
        "--no-first-run",
        "--no-default-browser-check",
        f"http://127.0.0.1:{http_port}/index.html"
    ], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)

    try:
        ws_url = None
        for _ in range(60):
            try:
                pages = json.load(urllib.request.urlopen(f"http://127.0.0.1:{cdp_port}/json/list", timeout=0.5))
                ws_url = next((p["webSocketDebuggerUrl"] for p in pages if p.get("type") == "page"), None)
                if ws_url:
                    break
            except Exception:
                pass
            await asyncio.sleep(0.1)
        if not ws_url:
            raise RuntimeError("no CDP target")

        async with websockets.connect(ws_url, max_size=32 * 1024 * 1024) as ws:
            counter = 0
            async def send(method, params=None):
                nonlocal counter
                counter += 1
                mid = counter
                await ws.send(json.dumps({"id": mid, "method": method, "params": params or {}}))
                while True:
                    msg = json.loads(await ws.recv())
                    if msg.get("id") == mid:
                        if "error" in msg:
                            raise RuntimeError(msg["error"])
                        return msg.get("result", {})

            async def evaluate(expr):
                r = await send("Runtime.evaluate", {"expression": expr, "awaitPromise": True, "returnByValue": True})
                return r.get("result", {}).get("value")

            async def screenshot(fn):
                r = await send("Page.captureScreenshot", {"format": "png"})
                data = r.get("data")
                (OUT / fn).write_bytes(base64.b64decode(data))

            await send("Emulation.setDeviceMetricsOverride", {
                "width": width,
                "height": height,
                "deviceScaleFactor": 1,
                "mobile": True
            })

            await asyncio.sleep(1.5)
            await evaluate("document.querySelector('#btn-start').click()")
            await asyncio.sleep(0.5)
            await evaluate("document.querySelector('#nav-slots').click()")
            await asyncio.sleep(0.5)

            # Check bounding boxes and layout
            layout = await evaluate("""(() => {
                const cab = document.querySelector('.slots-cabinet');
                const reels = document.querySelector('#slots-reel-window');
                const controls = document.querySelector('.slots-controls');
                const lever = document.querySelector('#slots-lever');
                const tray = document.querySelector('#slots-coin-tray');
                const grid = document.querySelector('#slots-grid');
                return {
                    viewport: { w: window.innerWidth, h: window.innerHeight },
                    cabinet: cab ? { w: cab.offsetWidth, h: cab.offsetHeight, scrollW: cab.scrollWidth } : null,
                    reels: reels ? { w: reels.offsetWidth, h: reels.offsetHeight } : null,
                    grid: grid ? { w: grid.offsetWidth, h: grid.offsetHeight, cells: grid.querySelectorAll('.slot-symbol').length } : null,
                    controls: controls ? { w: controls.offsetWidth, h: controls.offsetHeight } : null,
                    leverVisible: lever ? (window.getComputedStyle(lever).display !== 'none') : false,
                    trayVisible: tray ? (window.getComputedStyle(tray).display !== 'none') : false
                };
            })()""")

            await screenshot(f"{name}_cabinet.png")

            # Click Spin
            await evaluate("document.querySelector('#btn-slots-spin').click()")
            await asyncio.sleep(0.8)
            await screenshot(f"{name}_spun.png")

            return layout
    finally:
        chrome.terminate()
        server.terminate()
        chrome.wait(timeout=3)
        server.wait(timeout=3)
        shutil.rmtree(profile, ignore_errors=True)

async def main():
    m = await check_viewport(390, 844, "mobile_390x844")
    print("Mobile 390x844 layout:", json.dumps(m, indent=2))
    d = await check_viewport(412, 915, "mobile_412x915")
    print("Mobile 412x915 layout:", json.dumps(d, indent=2))

if __name__ == "__main__":
    asyncio.run(main())

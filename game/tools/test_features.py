#!/usr/bin/env python3
"""Current first-minute flow smoke test (story timeline and skip path)."""
import asyncio
import json
import shutil
import socket
import subprocess
import tempfile
import urllib.request
from pathlib import Path

import websockets

ROOT = Path(__file__).resolve().parents[1]
CHROME = "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome"


def free_port():
    with socket.socket() as sock:
        sock.bind(("127.0.0.1", 0))
        return sock.getsockname()[1]


async def main():
    http_port, cdp_port = free_port(), free_port()
    url = f"http://127.0.0.1:{http_port}/index.html"
    profile = tempfile.mkdtemp(prefix="lg-first-minute-")
    server = subprocess.Popen(["python3", "web/serve.py", str(http_port)], cwd=ROOT,
                              stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    chrome = None
    try:
        for _ in range(60):
            try:
                with urllib.request.urlopen(url, timeout=0.5):
                    break
            except Exception:
                await asyncio.sleep(0.1)
        else:
            raise RuntimeError("local game server did not become ready")

        chrome = subprocess.Popen([
            CHROME, f"--user-data-dir={profile}", f"--remote-debugging-port={cdp_port}",
            "--window-size=420,880", "--headless=new", "--disable-gpu",
            "--use-gl=swiftshader", "--no-first-run", "--no-default-browser-check", url,
        ], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
        target = None
        for _ in range(100):
            try:
                pages = json.load(urllib.request.urlopen(f"http://127.0.0.1:{cdp_port}/json/list", timeout=0.5))
                target = next((page["webSocketDebuggerUrl"] for page in pages if page.get("type") == "page"), None)
                if target:
                    break
            except Exception:
                await asyncio.sleep(0.1)
        if not target:
            raise RuntimeError("Chrome DevTools target did not become ready")

        async with websockets.connect(target, max_size=32 * 1024 * 1024) as ws:
            request_id = 0

            async def evaluate(expression):
                nonlocal request_id
                request_id += 1
                current_id = request_id
                await ws.send(json.dumps({"id": current_id, "method": "Runtime.evaluate", "params": {
                    "expression": expression, "awaitPromise": True, "returnByValue": True,
                }}))
                while True:
                    message = json.loads(await ws.recv())
                    if message.get("id") == current_id:
                        result = message.get("result", {}).get("result", {})
                        if "exceptionDetails" in message.get("result", {}):
                            raise AssertionError(message["result"]["exceptionDetails"])
                        return result.get("value")

            await asyncio.sleep(1.5)
            await evaluate("document.querySelector('#btn-start').click()")
            await evaluate("document.querySelector('#nav-campaign').click()")
            await evaluate("document.querySelector('.chapter-card').click()")
            await evaluate("document.querySelector('.stage-btn').click()")
            await evaluate("document.querySelector('#btn-stage-launch').click()")
            await asyncio.sleep(1.0)
            story = await evaluate("({screen:document.querySelector('.screen.active').id,active:IntroTimeline.active,event:PrologueLog[0]&&PrologueLog[0].id,dialogue:document.querySelector('#story-text').textContent})")
            assert story["screen"] == "screen-story" and story["active"] is True, story
            assert story["event"] and story["dialogue"], story
            await evaluate("document.querySelector('#btn-story-skip').click()")
            await asyncio.sleep(0.5)
            result = await evaluate("({screen:document.querySelector('.screen.active').id,active:IntroTimeline.active,skipped:PrologueLog.some(event=>event.skipped)})")
            assert result["screen"] == "screen-battle" and not result["active"] and result["skipped"], result
            print("PASS: first-minute timeline starts, logs dialogue, and skip enters battle.")
    finally:
        if chrome:
            chrome.terminate()
            chrome.wait(timeout=5)
        server.terminate()
        server.wait(timeout=5)
        shutil.rmtree(profile, ignore_errors=True)


if __name__ == "__main__":
    asyncio.run(main())

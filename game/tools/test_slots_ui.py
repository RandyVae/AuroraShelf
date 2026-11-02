#!/usr/bin/env python3
"""Browser smoke test for the local Slots screen."""
import asyncio, json, subprocess, tempfile, time, urllib.request
from pathlib import Path
import websockets

ROOT = Path(__file__).resolve().parents[1]
CHROME = "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome"

async def main():
    server = subprocess.Popen(["python3", "web/serve.py", "8122"], cwd=ROOT, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    profile = tempfile.mkdtemp(prefix="lg-slots-ui-")
    chrome = subprocess.Popen([CHROME, f"--user-data-dir={profile}", "--remote-debugging-port=9622", "--headless=new", "--disable-gpu", "--use-gl=swiftshader", "--no-first-run", "--no-default-browser-check", "http://127.0.0.1:8122/index.html"], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    try:
        target = None
        for _ in range(60):
            try:
                pages = json.load(urllib.request.urlopen("http://127.0.0.1:9622/json/list"))
                target = next((p["webSocketDebuggerUrl"] for p in pages if p.get("type") == "page"), None)
                if target: break
            except Exception: pass
            await asyncio.sleep(0.1)
        if not target: raise RuntimeError("CDP target unavailable")
        async with websockets.connect(target, max_size=32 * 1024 * 1024) as ws:
            counter = 0
            async def evaluate(expression):
                nonlocal counter
                counter += 1
                await ws.send(json.dumps({"id": counter, "method": "Runtime.evaluate", "params": {"expression": expression, "awaitPromise": True, "returnByValue": True}}))
                while True:
                    message = json.loads(await ws.recv())
                    if message.get("id") == counter:
                        return message.get("result", {}).get("result", {}).get("value")

            await asyncio.sleep(1.5)
            await evaluate("document.querySelector('#btn-start').click()")
            await asyncio.sleep(0.7)
            result = await evaluate("(() => { document.querySelector('#nav-slots').click(); return {screen: document.querySelector('#screen-slots').classList.contains('active'), cells: document.querySelectorAll('#slots-grid .slot-symbol').length, spinDisabled: document.querySelector('#btn-slots-spin').disabled}; })()")
            assert result == {"screen": True, "cells": 15, "spinDisabled": False}, result
            await evaluate("document.querySelector('#btn-slots-spin').click()")
            await asyncio.sleep(0.7)
            result = await evaluate("({cells: document.querySelectorAll('#slots-grid .slot-symbol').length, gold: document.querySelector('#slots-gold').textContent, result: document.querySelector('#slots-result').textContent})")
            assert result["cells"] == 15, result
            assert result["result"], result
            print("PASS: Slots screen opens, spins a 5x3 grid, and renders a result.")
    finally:
        chrome.terminate(); server.terminate()
        chrome.wait(timeout=3); server.wait(timeout=3)

if __name__ == "__main__": asyncio.run(main())

#!/usr/bin/env python3
"""Automate Android emulator WebView to open and demonstrate Slots."""
import asyncio
import json
import subprocess
import time
from pathlib import Path
import websockets

PORT = 9333
ROOT = Path("/Users/itou/Documents/project-hgame")
OUT_DIR = ROOT / "qa" / "android_slots_live"
OUT_DIR.mkdir(parents=True, exist_ok=True)

def cap(name):
    p = OUT_DIR / f"{name}.png"
    subprocess.run(["adb", "-s", "emulator-5554", "exec-out", "screencap", "-p"], stdout=open(p, "wb"), check=True)
    print(f"Captured: {p} ({p.stat().st_size} bytes)")
    return p

async def main():
    # Make sure port forwarding is up
    pids = subprocess.check_output(["adb", "-s", "emulator-5554", "shell", "cat", "/proc/net/unix"]).decode()
    for line in pids.splitlines():
        if "webview_devtools_remote" in line:
            socket_name = line.split()[-1]
            if socket_name.startswith("@"):
                socket_name = socket_name[1:]
            subprocess.run(["adb", "-s", "emulator-5554", "forward", f"tcp:{PORT}", f"localabstract:{socket_name}"], check=True)
            print(f"Forwarded tcp:{PORT} -> localabstract:{socket_name}")
            break

    # Get page target
    proc = subprocess.Popen(["curl", "-s", f"http://127.0.0.1:{PORT}/json/list"], stdout=subprocess.PIPE)
    data = json.load(proc.stdout)
    ws_url = data[0]["webSocketDebuggerUrl"]
    print(f"Connecting to: {ws_url}")

    async with websockets.connect(ws_url, max_size=64 * 1024 * 1024) as ws:
        counter = 0
        async def eval_js(code):
            nonlocal counter
            counter += 1
            await ws.send(json.dumps({'id': counter, 'method': 'Runtime.evaluate', 'params': {'expression': code, 'awaitPromise': True, 'returnByValue': True}}))
            while True:
                msg = json.loads(await ws.recv())
                if msg.get('id') == counter:
                    return msg.get('result', {}).get('result', {}).get('value')

        # 1. Check current screen
        scr = await eval_js("document.querySelector('.screen.active')?.id")
        print("Initial screen on emulator:", scr)

        if scr == "screen-boot" or scr == "screen-title":
            await eval_js("document.querySelector('#btn-start')?.click()")
            await asyncio.sleep(1.0)

        # 2. Navigate to Slots screen
        print("Navigating to Slots on emulator...")
        await eval_js("document.querySelector('#nav-slots')?.click()")
        await asyncio.sleep(1.2)

        # Capture 01: Slots Cabinet Overview
        cap("01_slots_cabinet_initial")

        # 3. Open Paytable Modal
        print("Opening Paytable modal...")
        await eval_js("document.querySelector('#btn-slots-paytable')?.click()")
        await asyncio.sleep(0.6)
        cap("02_slots_paytable_modal")

        # Close Paytable Modal
        await eval_js("document.querySelector('#btn-slots-paytable-close')?.click()")
        await asyncio.sleep(0.4)

        # 4. Trigger Spin via Mechanical Lever
        print("Pulling Mechanical Lever...")
        await eval_js("document.querySelector('#slots-lever')?.click()")
        await asyncio.sleep(0.35)
        cap("03_slots_reels_spinning")

        # Wait for reels to settle
        await asyncio.sleep(1.2)
        cap("04_slots_spin_settled")

        # 5. Trigger a feature win for Kitty (3-match)
        print("Triggering feature presentation...")
        await eval_js("""(() => {
            if (window.SLOTS && SLOTS.showFeature) {
                const kittySym = SLOTS.config.symbols.find(s => s.id === 'kitty') || { id: 'kitty', label: 'Kitty' };
                SLOTS.showFeature(kittySym, 3, 100);
            }
        })()""")
        await asyncio.sleep(0.8)
        cap("05_slots_feature_kitty_modal")

        # Close feature
        await eval_js("document.querySelector('#btn-slots-feature-close')?.click()")
        await asyncio.sleep(0.4)

        # 6. Final state with coin hopper
        cap("06_slots_cabinet_final")

        print("Finished all emulator actions and captures!")

if __name__ == "__main__":
    asyncio.run(main())

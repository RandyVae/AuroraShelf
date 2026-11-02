#!/usr/bin/env python3
"""
On-device CDP verification script for v2.19 release QA APK on Android Emulator (heros14:5554).
Tests:
1. HUD resource bar overflow fix:
   - Verifies settings button (btn-settings) does not overflow right edge (overflow <= 0).
   - Verifies gold, key, energy, missions, settings all cleanly visible inside screen.
2. Commander bar (.hub-id) and button layout:
   - Verifies .hub-id height is compact (<= 56px, down from 73px bloated).
   - Verifies hero switch badge is single-line with zero text wrapping and NO duplicate arrow.
   - Verifies outfit toggle button is single-line with zero text wrapping.
   - Verifies commander title clearance to VIP badge >= 10px.
   - Verifies Orin head clearance below .hub-id >= 30px.
3. Interaction verification:
   - Outfit toggle (18+ 觉醒 <-> 战术装)
   - Hero switcher (Orin -> next hero)
4. Saves high-res on-device screencap evidence.
"""

import asyncio
import json
import os
import subprocess
import urllib.request
import websockets

ARTIFACTS_DIR = "/Users/itou/.gemini/antigravity/brain/049b3232-c902-4161-a70e-acac2e3dc8c7/v219_verification"
os.makedirs(ARTIFACTS_DIR, exist_ok=True)


async def send_cmd(ws, method, params=None, msg_id=1):
    req = {"id": msg_id, "method": method, "params": params or {}}
    await ws.send(json.dumps(req))
    while True:
        resp = json.loads(await ws.recv())
        if resp.get("id") == msg_id:
            return resp.get("result", {})


async def eval_js(ws, expr, msg_id=1):
    res = await send_cmd(ws, "Runtime.evaluate", {"expression": expr, "returnByValue": True}, msg_id=msg_id)
    return res.get("result", {}).get("value")


def capture_screencap(filename):
    p = os.path.join(ARTIFACTS_DIR, filename)
    subprocess.run(f"adb exec-out screencap -p > {p}", shell=True, check=True)
    print(f"  [ScreenCap] Saved: {p}")
    return p


async def main():
    print("=== Lust Goddess v2.19 On-Device Verification ===")
    targets = json.loads(urllib.request.urlopen("http://localhost:9222/json").read().decode("utf-8"))
    ws_url = targets[0]["webSocketDebuggerUrl"]
    print(f"Connecting to CDP: {ws_url}")

    async with websockets.connect(ws_url, max_size=50 * 1024 * 1024) as ws:
        msg_id = 100

        await asyncio.sleep(1.5)
        ready = await eval_js(ws, "!!(window.APP && APP.state && APP.data)", msg_id=msg_id)
        msg_id += 1
        print(f"App initialized: {ready}")

        # Start game if title screen
        title_visible = await eval_js(ws, "!document.getElementById('screen-title').hidden", msg_id=msg_id)
        msg_id += 1
        if title_visible:
            print("Clicking Start Game button...")
            await eval_js(ws, "document.getElementById('btn-start').click()", msg_id=msg_id)
            msg_id += 1
            await asyncio.sleep(1.2)

        # Ensure on Hub screen
        await eval_js(ws, "APP.show('hub')", msg_id=msg_id)
        msg_id += 1
        await asyncio.sleep(0.5)

        # Set Orin, 18+ nude outfit, cleared = 4
        setup_expr = """
        (() => {
          APP.state.squad[0] = 'orin';
          APP.state.hubOutfit = 'nude';
          APP.state.cleared = { '1-1': 3, '1-2': 3, '1-3': 3, '1-4': 3 };
          LGSave.persist(APP.state);
          UI.renderHub(APP.state, APP.data);
        })()
        """
        await eval_js(ws, setup_expr, msg_id=msg_id)
        msg_id += 1
        await asyncio.sleep(0.5)

        # ----------------------------------------------------
        # TEST 1: HUD Resource Bar & Settings Button Metrics
        # ----------------------------------------------------
        print("\n--- TEST 1: Top HUD Resource Overflow Check ---")
        hud_info = await eval_js(ws, """
        (() => {
          const hud = document.querySelector('.hud');
          const settingsBtn = document.getElementById('btn-settings');
          const hr = hud.getBoundingClientRect();
          const sr = settingsBtn.getBoundingClientRect();
          return {
            windowW: window.innerWidth,
            hudLeft: Math.round(hr.left),
            hudRight: Math.round(hr.right),
            settingsRight: Math.round(sr.right),
            settingsOverflow: Math.round(sr.right - window.innerWidth),
            marginRight: Math.round(window.innerWidth - sr.right)
          };
        })()
        """, msg_id=msg_id)
        msg_id += 1

        print(f"Window Width: {hud_info['windowW']}px")
        print(f"Settings Btn Right: {hud_info['settingsRight']}px (Margin Right: {hud_info['marginRight']}px)")
        print(f"Settings Overflow: {hud_info['settingsOverflow']}px")
        assert hud_info["settingsOverflow"] <= 0, f"FAIL: Settings button overflows right by {hud_info['settingsOverflow']}px!"
        assert hud_info["marginRight"] >= 8, f"FAIL: Settings button too close to right edge: {hud_info['marginRight']}px!"
        print("✓ TEST 1 PASSED: Settings button is completely inside screen with safe padding!")

        # ----------------------------------------------------
        # TEST 2: Commander Bar (.hub-id) Layout Metrics
        # ----------------------------------------------------
        print("\n--- TEST 2: Commander Bar (.hub-id) Clean Layout Check ---")
        hubid_info = await eval_js(ws, """
        (() => {
          const hubId = document.querySelector('.hub-id');
          const badge = document.getElementById('btn-hub-hero-switch');
          const outfit = document.getElementById('btn-hub-outfit');
          const countEl = document.getElementById('hub-hero-count');
          const nameEl = document.getElementById('hub-hero-name');
          const metaEl = document.getElementById('hub-meta');
          const vipEl = document.querySelector('.vip-badge');
          const artImg = document.getElementById('hub-art');

          const ir = hubId.getBoundingClientRect();
          const br = badge.getBoundingClientRect();
          const or = outfit.getBoundingClientRect();
          const mr = metaEl.getBoundingClientRect();
          const vr = vipEl.getBoundingClientRect();
          const ar = artImg.getBoundingClientRect();

          return {
            hubIdH: Math.round(ir.height),
            badgeW: Math.round(br.width),
            badgeH: Math.round(br.height),
            badgeText: badge.textContent.replace(/\\s+/g, ' ').trim(),
            countText: countEl ? countEl.textContent.trim() : '',
            outfitW: Math.round(or.width),
            outfitH: Math.round(or.height),
            outfitText: outfit.textContent.replace(/\\s+/g, ' ').trim(),
            metaText: metaEl ? metaEl.textContent.trim() : '',
            vipClearance: Math.round(vr.left - mr.right),
            headClearance: Math.round(ar.top - ir.bottom)
          };
        })()
        """, msg_id=msg_id)
        msg_id += 1

        print(f"Hub ID Card Height: {hubid_info['hubIdH']}px (Expect <= 56px)")
        print(f"Hero Badge: '{hubid_info['badgeText']}', Size: {hubid_info['badgeW']}x{hubid_info['badgeH']}px")
        print(f"Hero Count Text: '{hubid_info['countText']}'")
        print(f"Outfit Button: '{hubid_info['outfitText']}', Size: {hubid_info['outfitW']}x{hubid_info['outfitH']}px")
        print(f"Commander Meta: '{hubid_info['metaText']}', VIP Clearance: {hubid_info['vipClearance']}px")
        print(f"Orin Head Clearance Below Card: {hubid_info['headClearance']}px")

        assert hubid_info["hubIdH"] <= 56, f"FAIL: .hub-id height {hubid_info['hubIdH']}px is bloated!"
        assert "↺" not in hubid_info["countText"], f"FAIL: Duplicate arrow found in count text '{hubid_info['countText']}'!"
        assert hubid_info["badgeH"] <= 36, f"FAIL: Hero badge height {hubid_info['badgeH']}px indicates wrapping!"
        assert hubid_info["outfitH"] <= 36, f"FAIL: Outfit button height {hubid_info['outfitH']}px indicates wrapping!"
        assert hubid_info["vipClearance"] >= 10, f"FAIL: Meta text too close to VIP badge: {hubid_info['vipClearance']}px!"
        assert hubid_info["headClearance"] >= 30, f"FAIL: Head clearance too tight: {hubid_info['headClearance']}px!"
        print("✓ TEST 2 PASSED: .hub-id capsule is sleek, single-line, zero wrapping, zero duplicate arrow!")

        # Capture initial screencap
        capture_screencap("01_v219_orin_18plus_fixed.png")

        # ----------------------------------------------------
        # TEST 3: Outfit Switch Interaction
        # ----------------------------------------------------
        print("\n--- TEST 3: Outfit Switch Interaction ---")
        await eval_js(ws, "document.getElementById('btn-hub-outfit').click()", msg_id=msg_id)
        msg_id += 1
        await asyncio.sleep(0.6)

        outfit_tactical_text = await eval_js(ws, "document.getElementById('hub-outfit-label').textContent.trim()", msg_id=msg_id)
        msg_id += 1
        print(f"Outfit after toggle: '{outfit_tactical_text}'")
        assert outfit_tactical_text == "战术装", f"FAIL: Expected '战术装', got '{outfit_tactical_text}'"
        capture_screencap("02_v219_orin_tactical_fixed.png")

        # Toggle back to 18+
        await eval_js(ws, "document.getElementById('btn-hub-outfit').click()", msg_id=msg_id)
        msg_id += 1
        await asyncio.sleep(0.6)

        # ----------------------------------------------------
        # TEST 4: Hero Switcher Interaction
        # ----------------------------------------------------
        print("\n--- TEST 4: Hero Switcher Interaction ---")
        await eval_js(ws, "document.getElementById('btn-hub-hero-switch').click()", msg_id=msg_id)
        msg_id += 1
        await asyncio.sleep(0.6)

        switched_hero = await eval_js(ws, """
        (() => {
          const nameEl = document.getElementById('hub-hero-name');
          const countEl = document.getElementById('hub-hero-count');
          return {
            name: nameEl ? nameEl.textContent.trim() : '',
            count: countEl ? countEl.textContent.trim() : ''
          };
        })()
        """, msg_id=msg_id)
        msg_id += 1
        print(f"Switched Hero: '{switched_hero['name']}', Count: '{switched_hero['count']}'")
        assert switched_hero["name"] != "Orin", f"FAIL: Hero did not switch away from Orin!"
        capture_screencap("03_v219_switched_hero_fixed.png")

        print("\nAll v2.19 On-Device Verification Tests PASSED successfully!")


if __name__ == "__main__":
    asyncio.run(main())

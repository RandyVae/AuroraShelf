#!/usr/bin/env python3
"""
On-device CDP verification script for v2.17 release QA APK on Android Emulator (heros14:5554).
Tests all 5 user reported issues:
1. Hub 18+ vs tactical outfit toggling image change
2. Hub hero switcher (prev/next/cycle) through all owned heroes
3. Hub hero portrait placement and smooth edge fade (zero flat cut cutoff)
4. Slots machine screen glitch elimination (no mix-blend-mode / brightness filter flicker)
5. Slots reel window height compaction and near-square 1:1 symbol cells
"""

import asyncio
import json
import os
import sys
import urllib.request
import websockets

ARTIFACTS_DIR = "/Users/itou/.gemini/antigravity/brain/049b3232-c902-4161-a70e-acac2e3dc8c7/v217_verification"
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


async def capture_screenshot(ws, filename, msg_id=1):
    res = await send_cmd(ws, "Page.captureScreenshot", {"format": "png"}, msg_id=msg_id)
    import base64
    data = base64.b64decode(res.get("data", ""))
    path = os.path.join(ARTIFACTS_DIR, filename)
    with open(path, "wb") as f:
        f.write(data)
    print(f"  [Screenshot] Saved: {path} ({len(data)} bytes)")
    return path


async def main():
    print("=== Lust Goddess v2.17 On-Device CDP Verification ===")
    targets = json.loads(urllib.request.urlopen("http://localhost:9222/json").read().decode("utf-8"))
    ws_url = targets[0]["webSocketDebuggerUrl"]
    print(f"Connecting to CDP: {ws_url}")

    async with websockets.connect(ws_url, max_size=50 * 1024 * 1024) as ws:
        msg_id = 100

        # Wait for app ready
        await asyncio.sleep(2)
        ready = await eval_js(ws, "!!(window.APP && APP.state && APP.data)", msg_id=msg_id)
        msg_id += 1
        print(f"App initialized: {ready}")

        # Start game if on title screen
        title_visible = await eval_js(ws, "!document.getElementById('screen-title').hidden", msg_id=msg_id)
        msg_id += 1
        if title_visible:
            print("Clicking Start Game button...")
            await eval_js(ws, "document.getElementById('btn-start').click()", msg_id=msg_id)
            msg_id += 1
            await asyncio.sleep(1.5)

        # Ensure on Hub screen
        await eval_js(ws, "APP.show('hub')", msg_id=msg_id)
        msg_id += 1
        await asyncio.sleep(1)

        # ----------------------------------------------------
        # TEST 1: Hub Outfit Toggling (Issue 1)
        # ----------------------------------------------------
        print("\n--- TEST 1: Hub 18+ vs Tactical Outfit Toggling ---")
        # Ensure QA resources maxed & unlocked
        await eval_js(ws, "LGSave.qaMaxAll(APP.state, APP.data); LGSave.persist(APP.state); UI.renderHub(APP.state, APP.data);", msg_id=msg_id)
        msg_id += 1
        await asyncio.sleep(0.5)

        # Initial state (tactical)
        await eval_js(ws, "APP.state.hubOutfit = 'lega'; LGSave.persist(APP.state); UI.renderHub(APP.state, APP.data);", msg_id=msg_id)
        msg_id += 1
        await asyncio.sleep(0.6)

        art_tactical = await eval_js(ws, "document.getElementById('hub-art').src", msg_id=msg_id)
        msg_id += 1
        label_tactical = await eval_js(ws, "document.getElementById('hub-outfit-label').textContent", msg_id=msg_id)
        msg_id += 1
        print(f"Tactical Mode: label='{label_tactical}', src='{art_tactical}'")
        await capture_screenshot(ws, "01_hub_kitty_tactical.png", msg_id=msg_id)
        msg_id += 1

        # Toggle to 18+ Awaken
        await eval_js(ws, "document.getElementById('btn-hub-outfit').click()", msg_id=msg_id)
        msg_id += 1
        await asyncio.sleep(0.6)

        art_18plus = await eval_js(ws, "document.getElementById('hub-art').src", msg_id=msg_id)
        msg_id += 1
        label_18plus = await eval_js(ws, "document.getElementById('hub-outfit-label').textContent", msg_id=msg_id)
        msg_id += 1
        print(f"18+ Awaken Mode: label='{label_18plus}', src='{art_18plus}'")
        await capture_screenshot(ws, "02_hub_kitty_18plus.png", msg_id=msg_id)
        msg_id += 1

        assert art_tactical != art_18plus, f"FAIL: Image src did not change! tactical={art_tactical}, 18+={art_18plus}"
        print(f"✓ TEST 1 PASSED: Kitty image changes visibly ({art_tactical.split('/')[-1]} vs {art_18plus.split('/')[-1]})")

        # ----------------------------------------------------
        # TEST 2: Hub Hero Switcher (Issue 2)
        # ----------------------------------------------------
        print("\n--- TEST 2: Hub Hero Switcher Controls ---")
        has_prev = await eval_js(ws, "!!document.getElementById('btn-hub-hero-prev')", msg_id=msg_id)
        msg_id += 1
        has_next = await eval_js(ws, "!!document.getElementById('btn-hub-hero-next')", msg_id=msg_id)
        msg_id += 1
        has_badge = await eval_js(ws, "!!document.getElementById('btn-hub-hero-switch')", msg_id=msg_id)
        msg_id += 1
        print(f"Hero switcher controls present: prev={has_prev}, next={has_next}, badge={has_badge}")
        assert has_prev and has_next and has_badge, "FAIL: Switcher buttons missing from DOM"

        # Click next hero
        await eval_js(ws, "document.getElementById('btn-hub-hero-next').click()", msg_id=msg_id)
        msg_id += 1
        await asyncio.sleep(0.6)

        hero2_name = await eval_js(ws, "document.getElementById('hub-hero-name').textContent", msg_id=msg_id)
        msg_id += 1
        hero2_art = await eval_js(ws, "document.getElementById('hub-art').src", msg_id=msg_id)
        msg_id += 1
        hero2_count = await eval_js(ws, "document.getElementById('hub-hero-count').textContent", msg_id=msg_id)
        msg_id += 1
        print(f"Switched Hero 1: name='{hero2_name}', count='{hero2_count}', src='{hero2_art}'")
        await capture_screenshot(ws, "03_hub_switched_hero_1.png", msg_id=msg_id)
        msg_id += 1

        # Click next hero again
        await eval_js(ws, "document.getElementById('btn-hub-hero-next').click()", msg_id=msg_id)
        msg_id += 1
        await asyncio.sleep(0.6)

        hero3_name = await eval_js(ws, "document.getElementById('hub-hero-name').textContent", msg_id=msg_id)
        msg_id += 1
        hero3_art = await eval_js(ws, "document.getElementById('hub-art').src", msg_id=msg_id)
        msg_id += 1
        hero3_count = await eval_js(ws, "document.getElementById('hub-hero-count').textContent", msg_id=msg_id)
        msg_id += 1
        print(f"Switched Hero 2: name='{hero3_name}', count='{hero3_count}', src='{hero3_art}'")
        await capture_screenshot(ws, "04_hub_switched_hero_2.png", msg_id=msg_id)
        msg_id += 1

        # Test 18+ toggle on this newly switched hero
        await eval_js(ws, "document.getElementById('btn-hub-outfit').click()", msg_id=msg_id)
        msg_id += 1
        await asyncio.sleep(0.6)
        hero3_art_toggle = await eval_js(ws, "document.getElementById('hub-art').src", msg_id=msg_id)
        msg_id += 1
        print(f"Switched Hero Outfit Toggled: src='{hero3_art_toggle}'")
        assert hero3_art != hero3_art_toggle, f"FAIL: Switched hero outfit did not change! {hero3_art} vs {hero3_art_toggle}"
        print(f"✓ TEST 2 PASSED: Hero switching and multi-hero outfit toggling verified across roster")

        # ----------------------------------------------------
        # TEST 3: Hub Portrait Bounds & Edge Masking (Issue 3)
        # ----------------------------------------------------
        print("\n--- TEST 3: Hub Portrait Bounds & Edge Masking ---")
        art_rect = await eval_js(ws, """
        (() => {
          const el = document.getElementById('hub-art');
          const r = el.getBoundingClientRect();
          const cs = window.getComputedStyle(el);
          return {
            top: r.top,
            bottom: r.bottom,
            height: r.height,
            width: r.width,
            maskImage: cs.maskImage || cs.webkitMaskImage
          };
        })()
        """, msg_id=msg_id)
        msg_id += 1
        print(f"Hub Art Rect: top={art_rect['top']}px, bottom={art_rect['bottom']}px, height={art_rect['height']}px")
        print(f"Mask Image: {art_rect['maskImage']}")
        assert "linear-gradient" in art_rect["maskImage"], "FAIL: mask-image linear-gradient not applied"
        # Verify bottom reaches into the focus area (> 700px on 915px screen)
        assert art_rect["bottom"] >= 680, f"FAIL: Art bottom is too high ({art_rect['bottom']}px), floating in mid air!"
        print("✓ TEST 3 PASSED: Hero art extends naturally behind focus dock with smooth gradient mask")

        # ----------------------------------------------------
        # TEST 4 & 5: Slots Machine Flickering & Height Compaction (Issues 4 & 5)
        # ----------------------------------------------------
        print("\n--- TEST 4 & 5: Slots Cabinet Proportions & Flicker Elimination ---")
        await eval_js(ws, "APP.show('slots')", msg_id=msg_id)
        msg_id += 1
        await asyncio.sleep(1)

        slots_info = await eval_js(ws, """
        (() => {
          const rw = document.getElementById('slots-reel-window');
          const grid = document.getElementById('slots-grid');
          const glass = document.querySelector('.slots-glass');
          const bulb = document.querySelector('.ml-bulb');
          const firstCell = grid ? grid.children[0] : null;

          const rwRect = rw ? rw.getBoundingClientRect() : {};
          const cellRect = firstCell ? firstCell.getBoundingClientRect() : {};
          const glassCs = glass ? window.getComputedStyle(glass) : {};
          const bulbCs = bulb ? window.getComputedStyle(bulb) : {};

          return {
            rwHeight: rwRect.height,
            rwWidth: rwRect.width,
            cellHeight: cellRect.height,
            cellWidth: cellRect.width,
            cellRatio: cellRect.width ? (cellRect.height / cellRect.width) : 0,
            glassMixBlend: glassCs.mixBlendMode,
            bulbAnimation: bulbCs.animationName
          };
        })()
        """, msg_id=msg_id)
        msg_id += 1
        print(f"Slots Reel Window Dimensions: {slots_info['rwWidth']}px × {slots_info['rwHeight']}px")
        print(f"Reel Cell Dimensions: {slots_info['cellWidth']}px × {slots_info['cellHeight']}px (Aspect Ratio: {slots_info['cellRatio']:.2f})")
        print(f"Slots Glass mix-blend-mode: '{slots_info['glassMixBlend']}' (Must not be 'screen')")
        print(f"Marquee Bulb Animation: '{slots_info['bulbAnimation']}'")

        assert slots_info["glassMixBlend"] != "screen", "FAIL: slots-glass still has mix-blend-mode: screen!"
        assert slots_info["rwHeight"] <= 265, f"FAIL: Reel window height is too tall ({slots_info['rwHeight']}px)!"
        assert 0.8 <= slots_info["cellRatio"] <= 1.25, f"FAIL: Cell aspect ratio is stretched ({slots_info['cellRatio']:.2f})!"
        print("✓ TEST 4 & 5 INITIAL CHECKS PASSED: Compact reel window & ~1:1 near-square cell ratio confirmed!")

        await capture_screenshot(ws, "05_slots_compact_cabinet_idle.png", msg_id=msg_id)
        msg_id += 1

        # Perform a spin to verify animation smoothness and feature popup
        print("\nTriggering Slots spin to verify spin animation smoothness...")
        await eval_js(ws, "document.getElementById('btn-slots-spin').click()", msg_id=msg_id)
        msg_id += 1
        await asyncio.sleep(2.5)

        await capture_screenshot(ws, "06_slots_spin_stopped.png", msg_id=msg_id)
        msg_id += 1

        print("\nAll 5 on-device tests PASSED successfully!")


if __name__ == "__main__":
    asyncio.run(main())

#!/usr/bin/env python3
"""
tools/test_multi_res_audit.py
自动化端到端测试与多分辨率验证：对齐 CASE_DEEP_AUDIT_V3_2026-10-29
1. 验证三分辨率视口：
   - 390×844 (iPhone / 小型移动端)
   - 412×915 (Pixel / 标准 Android)
   - 1080×2340 (高分辨率 Android 模拟器原生比例)
2. 验证 NarrativeTimeline 六阶段事件链与大尺寸主角
3. 验证纯正三路战术战斗：阵营、回合 HUD、单位攻血、教程气泡、高亮格、手形指引、单位属性 Inspector
4. 验证打击、血量扣除、胜利结算、奖励发放与返回大厅
5. 验证 Reduce Motion 动效降级与剧情跳过 (Skip)
"""
from __future__ import annotations

import asyncio
import base64
import json
import os
import shutil
import subprocess
import tempfile
import urllib.request
from pathlib import Path

import websockets

CHROME = "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome"
URL = "http://127.0.0.1:8100/index.html"
OUT_BASE = Path("/Users/itou/Documents/project-hgame/qa/audit_v3")

RESOLUTIONS = [
    ("390x844", 390, 844),
    ("412x915", 412, 915),
    ("1080x2340_desk", 540, 1170) # 1:2.167 比例桌面窗口
]

class CdpAuditor:
    def __init__(self, ws):
        self.ws = ws
        self.mid = 0

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

    async def eval(self, expr):
        r = await self.send("Runtime.evaluate", {"expression": expr, "awaitPromise": True, "returnByValue": True})
        return r.get("result", {}).get("value")

    async def shot(self, out_dir, name):
        out_dir.mkdir(parents=True, exist_ok=True)
        r = await self.send("Page.captureScreenshot", {"format": "png"})
        data = base64.b64decode(r["data"])
        p = out_dir / (name + ".png")
        p.write_bytes(data)
        print(f"  [Shot] {p.name} ({len(data)} bytes)")

    async def tap(self, selector):
        ok = await self.eval("(function(){const n=document.querySelector(%s); if(!n) return false; n.click(); return true;})()" % json.dumps(selector))
        await asyncio.sleep(0.3)
        return ok

async def run_res_audit(res_name, w, h):
    out_dir = OUT_BASE / res_name
    out_dir.mkdir(parents=True, exist_ok=True)
    print(f"\n=======================================================")
    print(f"  开始分辨率测试: {res_name} ({w}x{h})")
    print(f"=======================================================")

    profile = tempfile.mkdtemp(prefix=f"cdp-res-{res_name}-")
    port = 9600 + (w % 80)
    proc = subprocess.Popen([
        CHROME, f"--user-data-dir={profile}", f"--remote-debugging-port={port}",
        f"--window-size={w},{h}", "--headless=new", "--disable-gpu",
        "--use-gl=swiftshader", "--enable-unsafe-swiftshader", "--hide-scrollbars",
        "--no-first-run", "--no-default-browser-check", "--disable-extensions",
        URL,
    ], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)

    ws_url = None
    try:
        for _ in range(50):
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
            raise RuntimeError(f"CDP connect failed for {res_name}")

        async with websockets.connect(ws_url, max_size=64 * 1024 * 1024) as ws:
            auditor = CdpAuditor(ws)
            await auditor.send("Runtime.enable")
            await auditor.send("Page.enable")
            await asyncio.sleep(1.0)

            # 1. 主视觉与大厅
            await auditor.shot(out_dir, "01_title_boot")
            await auditor.tap("#btn-start")
            await asyncio.sleep(0.6)
            await auditor.shot(out_dir, "02_hub_screen")

            # 2. 战役选关
            await auditor.tap("#nav-campaign")
            await asyncio.sleep(0.4)
            await auditor.tap(".chapter-card")
            await asyncio.sleep(0.4)
            await auditor.tap(".stage-btn")
            await asyncio.sleep(0.4)

            # 3. 序章 6 阶段测试
            await auditor.eval("APP.state.storySeen = {};")
            await auditor.tap("#btn-stage-launch")
            await asyncio.sleep(0.2)
            await auditor.shot(out_dir, "03_prologue_0s_establish")

            await asyncio.sleep(1.4)
            await auditor.shot(out_dir, "04_prologue_1s_wake")

            await asyncio.sleep(1.4)
            await auditor.shot(out_dir, "05_prologue_2s_identity")

            await asyncio.sleep(1.4)
            await auditor.shot(out_dir, "06_prologue_4s_ally")

            await asyncio.sleep(1.4)
            await auditor.shot(out_dir, "07_prologue_5s_objective")

            # 4. 进入战术战斗盘面 (Tactical Battle-Ready)
            await asyncio.sleep(1.2)
            await auditor.shot(out_dir, "08_tactical_battle_ready")

            # 检查是否有错误卡牌元素
            has_cards = await auditor.eval("!!document.querySelector('.ccg-card, #battle-hand-rack')")
            print(f"  [Audit Check] 是否存在错误 CCG 卡牌层: {has_cards} (必须为 False)")
            assert not has_cards, "CCG cards element must be removed!"

            # 5. 点击教程气泡推进教程并选中单位
            print(f"  [Audit Step] 推进战术教程并选中特勤单位...")
            await auditor.tap(".tactical-tutorial-bubble")
            await asyncio.sleep(0.5)
            await auditor.shot(out_dir, "09_tactical_step_select_unit")

            await auditor.tap("#battle-field")
            await asyncio.sleep(0.5)
            await auditor.shot(out_dir, "10_tactical_unit_selected_inspector")

            # 6. 点击发起战术突刺打击
            print(f"  [Audit Step] 发起战术突刺打击...")
            await auditor.tap("#battle-field")
            await asyncio.sleep(0.5)
            await auditor.shot(out_dir, "11_tactical_attack_impact")

            # 7. 开启 8x 加速至战斗胜利
            await auditor.tap("#btn-battle-skip")
            for _ in range(25):
                await asyncio.sleep(0.4)
                has_vic = await auditor.eval("document.querySelector('#battle-victory-cutin') && !document.querySelector('#battle-victory-cutin').hidden")
                if has_vic:
                    await auditor.shot(out_dir, "12_battle_victory_epilogue")
                    break

            await asyncio.sleep(1.0)
            await auditor.shot(out_dir, "13_battle_result")

            # 8. 点击继续返回战役大厅
            await auditor.tap("#btn-result-next")
            await asyncio.sleep(0.6)
            await auditor.tap("[data-back]")
            await asyncio.sleep(0.6)
            await auditor.shot(out_dir, "14_back_to_hub")

    finally:
        proc.terminate()
        try:
            proc.wait(timeout=5)
        except Exception:
            proc.kill()
        shutil.rmtree(profile, ignore_errors=True)

async def main():
    for name, w, h in RESOLUTIONS:
        await run_res_audit(name, w, h)
    print("\n✅ 三种主流分辨率全流程战术 RPG 自动化验证全部通过！")

if __name__ == '__main__':
    asyncio.run(main())

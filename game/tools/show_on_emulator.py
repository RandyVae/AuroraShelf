import asyncio
import json
import os
import subprocess
import time
from pathlib import Path
import websockets

PORT = 9333
MEDIA_DIR = Path("/Users/itou/.gemini/antigravity/brain/e63e129d-a1f4-47f9-847b-4817fdd8cd9b/media")
MEDIA_DIR.mkdir(parents=True, exist_ok=True)

def cap(name):
    p = MEDIA_DIR / f"{name}.png"
    subprocess.run(["adb", "exec-out", "screencap", "-p"], stdout=open(p, "wb"), check=True)
    print(f"Captured: {p} ({p.stat().st_size} bytes)")
    return p

async def main():
    uri = 'ws://127.0.0.1:9333/devtools/page/47E79F2195780AF5C7B1AD5B01981C45'
    async with websockets.connect(uri) as ws:
        async def eval_js(code):
            await ws.send(json.dumps({'id': 1, 'method': 'Runtime.evaluate', 'params': {'expression': code, 'awaitPromise': True}}))
            return json.loads(await ws.recv())

        # 1. 回到主大厅 (Hub) 并展示
        print("Navigating to Hub...")
        await eval_js("if (window.APP && window.UI) { UI.renderHub(APP.state, APP.data); const s = ['boot','title','hub','campaign','story','battle','result','squad','gacha','roster','shop','settings']; s.forEach(x => { const n = document.getElementById('screen-' + x); if(n) n.classList.toggle('active', x==='hub'); }); APP.screen = 'hub'; }")
        await asyncio.sleep(1.0)
        cap("01_emulator_hub")

        # 2. 进入战役 (Campaign) 选关
        print("Opening Campaign...")
        await eval_js("if (window.UI) { UI.renderCampaign(APP.state, APP.data); const s = ['boot','title','hub','campaign','story','battle','result','squad','gacha','roster','shop','settings']; s.forEach(x => { const n = document.getElementById('screen-' + x); if(n) n.classList.toggle('active', x==='campaign'); }); APP.screen = 'campaign'; }")
        await asyncio.sleep(0.8)
        cap("02_emulator_campaign")

        # 3. 启动第一章第一关进入 NarrativeTimeline 序章
        print("Launching Stage 1-1 Prologue...")
        await eval_js("APP.state.storySeen = {};")
        await eval_js("document.querySelector('.chapter-card')?.click();")
        await asyncio.sleep(0.4)
        await eval_js("document.querySelector('.stage-btn')?.click();")
        await asyncio.sleep(0.4)
        await eval_js("document.getElementById('btn-stage-launch')?.click();")
        await asyncio.sleep(0.5)
        cap("03_emulator_prologue_establish")

        # 4. 序章唤醒与大尺寸角色登场
        await asyncio.sleep(2.5)
        cap("04_emulator_prologue_hero_reveal")

        # 5. 序章双角色同框
        await asyncio.sleep(1.8)
        cap("05_emulator_prologue_ally_response")

        # 6. 进入战斗首屏 (卡牌 5手牌 + 能量池 + 敌方意图)
        await asyncio.sleep(2.0)
        cap("06_emulator_battle_ready_cards")

        # 7. 打出第一张牌
        print("Playing a card on emulator...")
        await eval_js("document.querySelector('#battle-hand-rack .ccg-card')?.click();")
        await asyncio.sleep(0.8)
        cap("07_emulator_card_played")

if __name__ == '__main__':
    asyncio.run(main())

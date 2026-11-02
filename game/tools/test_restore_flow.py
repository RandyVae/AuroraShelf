import asyncio
import json
import subprocess
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
    uri = 'ws://127.0.0.1:9333/devtools/page/8A38182F89DE9E91B383B87246188A85'
    async with websockets.connect(uri) as ws:
        async def eval_js(code):
            await ws.send(json.dumps({'id': 1, 'method': 'Runtime.evaluate', 'params': {'expression': code, 'awaitPromise': True}}))
            return json.loads(await ws.recv())

        print("1. 点击开始游戏进入大厅...")
        await eval_js("document.getElementById('btn-start')?.click();")
        await asyncio.sleep(1.0)
        cap("restored_01_hub")

        print("2. 点击战役进入地图...")
        await eval_js("document.getElementById('nav-campaign')?.click();")
        await asyncio.sleep(0.8)
        cap("restored_02_campaign")

        print("3. 选择 1-1 并出征 (触发原汁原味战前序幕)...")
        await eval_js("document.querySelector('.chapter-card')?.click();")
        await asyncio.sleep(0.4)
        await eval_js("document.querySelector('.stage-btn')?.click();")
        await asyncio.sleep(0.4)
        await eval_js("APP.state.storySeen = {};")
        await eval_js("document.getElementById('btn-stage-launch')?.click();")
        await asyncio.sleep(1.2)
        cap("restored_03_prologue")

        print("4. 等待切入战术战斗盘面 (纯正原版三路战斗，无外挂手牌)...")
        await asyncio.sleep(5.8)
        cap("restored_04_battle_tactical")

if __name__ == '__main__':
    asyncio.run(main())

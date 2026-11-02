import asyncio
import json
import subprocess
from pathlib import Path
import websockets

PORT = 9333
OUT_DIR = Path("/Users/itou/Documents/project-hgame/qa/audit_v3/android_1080x2340")
OUT_DIR.mkdir(parents=True, exist_ok=True)

def cap(name):
    p = OUT_DIR / f"{name}.png"
    subprocess.run(["adb", "exec-out", "screencap", "-p"], stdout=open(p, "wb"), check=True)
    print(f"  [Android 1080x2340 Shot] {p.name} ({p.stat().st_size} bytes)")
    return p

async def main():
    pids = subprocess.check_output(["adb", "shell", "cat", "/proc/net/unix"]).decode()
    for line in pids.splitlines():
        if "webview_devtools_remote" in line:
            socket_name = line.split()[-1]
            if socket_name.startswith("@"): socket_name = socket_name[1:]
            subprocess.run(["adb", "forward", f"tcp:{PORT}", f"localabstract:{socket_name}"], check=True)
            print(f"Forwarded tcp:{PORT} -> localabstract:{socket_name}")
            break

    data = json.load(subprocess.Popen(["curl", "-s", f"http://127.0.0.1:{PORT}/json/list"], stdout=subprocess.PIPE).stdout)
    ws_url = data[0]["webSocketDebuggerUrl"]
    print(f"Connecting to Android WebView: {ws_url}")

    async with websockets.connect(ws_url, max_size=64*1024*1024) as ws:
        async def eval_js(code):
            await ws.send(json.dumps({'id': 1, 'method': 'Runtime.evaluate', 'params': {'expression': code, 'awaitPromise': True}}))
            return json.loads(await ws.recv())

        # 1. 大厅与战役选关
        await eval_js("document.getElementById('btn-start')?.click();")
        await asyncio.sleep(0.8)
        cap("01_android_hub")

        await eval_js("document.getElementById('nav-campaign')?.click();")
        await asyncio.sleep(0.5)
        cap("02_android_campaign")

        # 2. 序章 6 阶段
        await eval_js("document.querySelector('.chapter-card')?.click();")
        await asyncio.sleep(0.3)
        await eval_js("document.querySelector('.stage-btn')?.click();")
        await asyncio.sleep(0.3)
        await eval_js("APP.state.storySeen = {};")
        await eval_js("document.getElementById('btn-stage-launch')?.click();")
        await asyncio.sleep(0.2)
        cap("03_android_prologue_0s_establish")

        await asyncio.sleep(1.4)
        cap("04_android_prologue_1s_wake")

        await asyncio.sleep(1.4)
        cap("05_android_prologue_2s_identity")

        await asyncio.sleep(1.4)
        cap("06_android_prologue_4s_ally")

        await asyncio.sleep(1.4)
        cap("07_android_prologue_5s_objective")

        # 3. 战术战斗首屏 (Tactical Battle-Ready)
        await asyncio.sleep(1.2)
        cap("08_android_tactical_battle_ready")

        # 4. 战术教程反馈：选中单位与属性面板
        await eval_js("document.querySelector('.tactical-tutorial-bubble')?.click();")
        await asyncio.sleep(0.4)
        cap("09_android_tactical_step_select")

        await eval_js("const ally = APP.battle.units.find(u => u.side==='ally'); if(ally) APP.tacticalHud.selectUnit(ally);")
        await asyncio.sleep(0.4)
        cap("10_android_tactical_inspector")

        # 5. 发起战术突刺打击
        await eval_js("const ally = APP.battle.units.find(u => u.side==='ally'); const enemy = APP.battle.units.find(u => u.side==='enemy'); if(ally && enemy) { APP.battle.damage(ally, enemy, ally.atk * 1.5); LGVFX.slash({x: enemy.x, y: enemy.footY}); }")
        await asyncio.sleep(0.4)
        cap("11_android_tactical_impact")

        # 6. 加速战胜至结算与回大厅
        await eval_js("document.getElementById('btn-battle-skip')?.click();")
        for _ in range(25):
            await asyncio.sleep(0.4)
            has_vic = await eval_js("document.querySelector('#battle-victory-cutin') && !document.querySelector('#battle-victory-cutin').hidden")
            if has_vic.get('result',{}).get('result',{}).get('value'):
                cap("12_android_victory_epilogue")
                break

        await asyncio.sleep(1.0)
        cap("13_android_result")

        await eval_js("document.getElementById('btn-result-next')?.click();")
        await asyncio.sleep(0.6)
        await eval_js("document.querySelector('[data-back]')?.click();")
        await asyncio.sleep(0.6)
        cap("14_android_back_to_hub")

    print("\n✅ Android API 36 原生 1080x2340 战术 RPG 全流程捕获完成！")

if __name__ == '__main__':
    asyncio.run(main())

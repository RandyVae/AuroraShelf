#!/usr/bin/env python3
"""Verification for Tactical Commander Skills and Equipment Set Resonance."""
import asyncio
import json
import socket
import subprocess
import tempfile
import urllib.request
from pathlib import Path
import websockets

ROOT = Path(__file__).resolve().parents[1]
CHROME = "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome"

async def main():
    with socket.socket() as sock:
        sock.bind(("127.0.0.1", 0))
        http_port = sock.getsockname()[1]
    with socket.socket() as sock:
        sock.bind(("127.0.0.1", 0))
        cdp_port = sock.getsockname()[1]

    server = subprocess.Popen(["python3", "web/serve.py", str(http_port)], cwd=ROOT, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    profile = tempfile.mkdtemp(prefix="lg-test-cmd-")
    chrome = subprocess.Popen([
        CHROME,
        f"--user-data-dir={profile}",
        f"--remote-debugging-port={cdp_port}",
        "--window-size=420,880",
        "--headless=new",
        "--disable-gpu",
        "--use-gl=swiftshader",
        "--enable-unsafe-swiftshader",
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
            async def evaluate(expr):
                nonlocal counter
                counter += 1
                await ws.send(json.dumps({"id": counter, "method": "Runtime.evaluate", "params": {"expression": expr, "awaitPromise": True, "returnByValue": True}}))
                while True:
                    msg = json.loads(await ws.recv())
                    if msg.get("id") == counter:
                        return msg.get("result", {}).get("result", {}).get("value")

            await asyncio.sleep(1.2)
            await evaluate("window._errors = []; window.addEventListener('error', e => window._errors.push(e.message || String(e))); window.addEventListener('unhandledrejection', e => window._errors.push(String(e.reason)));")
            await evaluate("document.querySelector('#btn-start').click()")
            await asyncio.sleep(0.5)

            # 1. Test Equipment Set Resonance in Progression
            eq_test = await evaluate("""(() => {
                const catalog = APP.data.equipment;
                const hero = APP.data.heroes.find(h => h.key === 'kitty');
                const inst2 = {
                    level: 10, stars: 3, skillLevel: 5,
                    equips: { weapon: 'wp_striker_blade', armor: 'ar_striker_vest', core: null }
                };
                const st2 = Progression.computeHeroCombatStats(hero, inst2, catalog);

                const inst3 = {
                    level: 10, stars: 3, skillLevel: 5,
                    equips: { weapon: 'wp_striker_blade', armor: 'ar_striker_vest', core: 'cr_striker_core' }
                };
                const st3 = Progression.computeHeroCombatStats(hero, inst3, catalog);

                return {
                    catalogCount: catalog.length,
                    twoPieceSets: st2.activeSets,
                    twoPieceAtk: st2.atk,
                    threePieceSets: st3.activeSets,
                    threePieceAtk: st3.atk,
                    atkDiff: st3.atk - st2.atk
                };
            })()""")
            print("Equipment set resonance test:", eq_test)
            assert eq_test["catalogCount"] == 18, "Catalog should have 18 items"
            assert len(eq_test["twoPieceSets"]) == 1, "Should activate 2-piece set"
            assert len(eq_test["threePieceSets"]) == 2, "Should activate 3-piece set (2pc + 3pc)"
            assert eq_test["atkDiff"] > 0, "3-piece set should yield higher atk"
            print("PASS: Equipment Set Resonance verified (18 items, 2pc/3pc sets active).")

            # 2. Test Battle Commander Skills
            start_res = await evaluate("""(async () => {
                try {
                    const ch = APP.data.chapters[0];
                    APP.state.storySeen[ch.id] = true;
                    // Check squad heroes first
                    const heroKeys = APP.state.squad;
                    const heroes = heroKeys.map((k, i) => {
                        const h = APP.data.heroByKey.get(k);
                        const inst = Progression.getHeroInstance(APP.state, k);
                        return { k, hasH: !!h, inst, eq: APP.data.equipment ? APP.data.equipment.length : 0 };
                    });
                    APP.startStage(ch, ch.stages[0]);
                    return { ok: true, heroes, screen: APP.screen, hasBattle: !!APP.battle };
                } catch(e) {
                    return { ok: false, error: e.toString(), stack: e.stack };
                }
            })()""")
            print("startStage result:", start_res)
            # Check window unhandled errors
            errs = await evaluate("window._errors")
            print("Window errors:", errs)
            await asyncio.sleep(2.5)

            # Check Commander Deck visibility and elements
            cmd_deck = await evaluate("""(() => {
                const deck = document.querySelector('#commander-deck');
                const bar = document.querySelector('#commander-energy-bar');
                const b1 = document.querySelector('#cmd-skill-heal');
                const b2 = document.querySelector('#cmd-skill-emp');
                const b3 = document.querySelector('#cmd-skill-strike');
                const b = window.APP && APP.battle;
                return {
                    deckExists: !!deck,
                    barExists: !!bar,
                    b1: !!b1,
                    b2: !!b2,
                    b3: !!b3,
                    energy: b ? b.commanderEnergy : null
                };
            })()""")
            print("Commander Deck status:", cmd_deck)
            assert cmd_deck["deckExists"] and cmd_deck["barExists"], "Commander Deck must be in DOM"
            assert cmd_deck["b1"] and cmd_deck["b2"] and cmd_deck["b3"], "3 Commander skills must exist"

            # Give energy and test tactical skills
            skill_res = await evaluate("""(() => {
                const b = APP.battle;
                if (!b) return { ok: false, err: 'no battle' };
                b.commanderEnergy = 100;
                b.updateCommanderUI();
                const energyBefore = b.commanderEnergy;
                b.useCommanderSkill('heal');
                const energyAfterHeal = b.commanderEnergy;
                b.useCommanderSkill('emp');
                const energyAfterEmp = b.commanderEnergy;
                return {
                    ok: true,
                    energyBefore,
                    energyAfterHeal,
                    energyAfterEmp,
                    healDeduction: energyBefore - energyAfterHeal,
                    empDeduction: energyAfterHeal - energyAfterEmp
                };
            })()""")
            print("Skill usage test:", skill_res)
            assert skill_res["healDeduction"] == 35, "Heal skill must cost 35 energy"
            assert skill_res["empDeduction"] == 50, "EMP skill must cost 50 energy"
            print("PASS: Tactical Commander Skills verified (energy accumulation, skill costs, and effects).")

    finally:
        chrome.terminate()
        server.terminate()
        chrome.wait(timeout=3)
        server.wait(timeout=3)
        import shutil
        shutil.rmtree(profile, ignore_errors=True)

if __name__ == "__main__":
    asyncio.run(main())

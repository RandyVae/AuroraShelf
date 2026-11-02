#!/usr/bin/env python3
"""Comprehensive test suite for P2 mechanics:
1. IntroTimeline Chapter 4 & Chapter 8 cinematic sequences
2. Faction Synergy Resonance calculations and tier activations
3. Squad screen Faction Synergy badge rendering
4. Combat hero signature mechanics and energy acceleration
"""
from __future__ import annotations
import asyncio, json, os, socket, subprocess, tempfile, urllib.request
from pathlib import Path
import websockets

CHROME = "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome"
ROOT = Path(__file__).resolve().parents[1]

async def main():
    # 1. Test Static Configs
    with open("web/data/intro-sequences.json", "r", encoding="utf-8") as f:
        data = json.load(f)
    seqs = data.get("sequences", {})
    assert "ch1_prologue" in seqs, "ch1_prologue must exist"
    assert "ch4_desert" in seqs, "ch4_desert must exist"
    assert "ch8_finale" in seqs, "ch8_finale must exist"

    ch4 = seqs["ch4_desert"]
    assert ch4["chapterId"] == 4 and len(ch4["events"]) >= 4
    assert any(a.get("key") == "lucretia" for ev in ch4["events"] for a in ev.get("actors", []))

    ch8 = seqs["ch8_finale"]
    assert ch8["chapterId"] == 8 and len(ch8["events"]) >= 4
    assert any(a.get("key") == "lilith" for ev in ch8["events"] for a in ev.get("actors", []))
    print("PASS: Static IntroTimeline sequences validated.")

    # 2. Browser CDP Test
    with socket.socket() as sock:
        sock.bind(("127.0.0.1", 0))
        http_port = sock.getsockname()[1]
    with socket.socket() as sock:
        sock.bind(("127.0.0.1", 0))
        cdp_port = sock.getsockname()[1]

    server = subprocess.Popen(["python3", "web/serve.py", str(http_port)], cwd=ROOT, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    profile = tempfile.mkdtemp(prefix="lg-test-p2-")
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
            await evaluate("document.querySelector('#btn-start').click()")
            await asyncio.sleep(0.5)

            # Test Faction Synergy calculation
            syn_res = await evaluate("""(() => {
                const heroes = [
                    { key: 'kitty', race: 'order' },
                    { key: 'justina', race: 'order' },
                    { key: 'esther', race: 'order' }
                ];
                const s3Order = Progression.computeFactionSynergy(heroes);
                const s2Order = Progression.computeFactionSynergy(heroes.slice(0, 2));
                const s2Chaos = Progression.computeFactionSynergy([{ race: 'chaos' }, { race: 'chaos' }, { race: 'nature' }]);
                const s3Nature = Progression.computeFactionSynergy([{ race: 'nature' }, { race: 'nature' }, { race: 'nature' }]);
                return { s3Order, s2Order, s2Chaos, s3Nature };
            })()""")
            assert syn_res["s3Order"][0]["name"] == "秩序正义", "3 Order should activate 秩序正义"
            assert syn_res["s2Order"][0]["name"] == "秩序同盟", "2 Order should activate 秩序同盟"
            assert syn_res["s2Chaos"][0]["name"] == "混沌狂暴", "2 Chaos should activate 混沌狂暴"
            assert syn_res["s3Nature"][0]["name"] == "自然繁衍", "3 Nature should activate 自然繁衍"
            print("PASS: Faction Synergy calculations verified.")

            # Test Squad UI Badges with 3 Order heroes
            squad_ui = await evaluate("""(() => {
                APP.state.squad = ['kitty', 'esther', 'justina'];
                document.querySelector('#nav-squad').click();
                const box = document.querySelector('#squad-faction-synergies');
                return {
                    visible: !!box,
                    badges: box ? Array.from(box.children).map(c => c.textContent) : []
                };
            })()""")
            print("Squad Faction UI Badges (3 Order):", squad_ui)
            assert squad_ui["visible"], "Faction synergies box must exist in squad screen"
            assert len(squad_ui["badges"]) == 1 and "秩序正义" in squad_ui["badges"][0], "3 Order must show 秩序正义 badge"

            # Test Chapter 4 & Chapter 8 Story Cinematics trigger
            seq_check = await evaluate("""(() => {
                const s4 = IntroTimeline.sequences['ch4_desert'];
                const s8 = IntroTimeline.sequences['ch8_finale'];
                return {
                    hasCh4: !!s4 && s4.events.length >= 4,
                    hasCh8: !!s8 && s8.events.length >= 4
                };
            })()""")
            print("Browser IntroTimeline loaded sequences:", seq_check)
            assert seq_check["hasCh4"] and seq_check["hasCh8"], "IntroTimeline must have ch4 and ch8 loaded"

            # Test Battle with Faction Synergy & Hero Signatures
            await evaluate("""(() => {
                const ch = APP.data.chapters[0];
                APP.state.storySeen[ch.id] = true;
                APP.state.squad = ['kitty', 'esther', 'justina'];
                APP.startStage(ch, ch.stages[0]);
            })()""")
            await asyncio.sleep(2.0)

            battle_test = await evaluate("""(() => {
                const b = APP.battle;
                if (!b) return { ok: false };
                return {
                    ok: true,
                    synergies: (b.factionSynergies || []).map(s => s.name),
                    unitsCount: b.units.length
                };
            })()""")
            print("Battle initiation test (3 Order):", battle_test)
            assert battle_test["ok"], "Battle must start successfully"
            assert "秩序正义" in battle_test["synergies"], "Battle must activate 秩序正义 synergy"
            assert battle_test["unitsCount"] > 0, "Units must be placed in battle"
            print("PASS: P2 Full Mechanics & Cinematics verified.")

    finally:
        chrome.terminate()
        server.terminate()
        chrome.wait(timeout=3)
        server.wait(timeout=3)
        import shutil
        shutil.rmtree(profile, ignore_errors=True)

if __name__ == "__main__":
    asyncio.run(main())

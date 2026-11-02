#!/usr/bin/env python3
"""Drive the Android WebView over adb-forwarded DevTools."""
import asyncio, base64, json, sys, urllib.request
import websockets

PORT = 9333


async def main(expr=None, shot=None):
    data = json.load(urllib.request.urlopen(f"http://127.0.0.1:{PORT}/json/list"))
    page = [t for t in data if t.get("type") == "page"][0]
    ws = await websockets.connect(page["webSocketDebuggerUrl"], max_size=64 * 1024 * 1024)
    mid = 0
    logs = []

    async def send(method, params=None):
        nonlocal mid
        mid += 1
        my = mid
        await ws.send(json.dumps({"id": my, "method": method, "params": params or {}}))
        while True:
            raw = json.loads(await ws.recv())
            if raw.get("id") == my:
                return raw.get("result", {})
            if raw.get("method") == "Runtime.consoleAPICalled":
                logs.append("[console] " + " ".join(str(a.get("value", a.get("description"))) for a in raw["params"].get("args", [])))
            if raw.get("method") == "Runtime.exceptionThrown":
                logs.append("[exception] " + json.dumps(raw["params"].get("exceptionDetails", {}).get("exception", {}))[:400])

    await send("Runtime.enable")
    if expr:
        r = await send("Runtime.evaluate", {"expression": expr, "awaitPromise": True, "returnByValue": True})
        print("eval:", json.dumps(r.get("result", {}), ensure_ascii=False)[:3000])
    if shot:
        r = await send("Page.captureScreenshot", {"format": "png"})
        if r.get("data"):
            open(shot, "wb").write(base64.b64decode(r["data"]))
            print("shot:", shot)
    for line in logs[-20:]:
        print(line)
    await ws.close()


if __name__ == "__main__":
    expr = sys.argv[1] if len(sys.argv) > 1 else None
    shot = sys.argv[2] if len(sys.argv) > 2 else None
    asyncio.run(main(expr, shot))

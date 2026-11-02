import asyncio, json, os, sys, time, urllib.request
import websockets

PORT = int(os.environ.get("CDP_PORT", "9333"))
DURATION = int(os.environ.get("CAPTURE_SECONDS", "300"))
OUT = os.environ.get("CAPTURE_OUT", "net/capture.jsonl")
HOSTS_KEEP = ("lust-goddess.com", "chickgoddess.com", "lustgoddess.com")

def http_json(path):
    with urllib.request.urlopen(f"http://127.0.0.1:{PORT}{path}", timeout=5) as r:
        return json.loads(r.read().decode())

async def main():
    deadline = time.time() + 60
    target = None
    while time.time() < deadline:
        try:
            targets = http_json("/json")
            pages = [t for t in targets if t.get("type") == "page" and t.get("webSocketDebuggerUrl")]
            if pages:
                target = pages[0]
                break
        except Exception:
            pass
        await asyncio.sleep(1)
    if not target:
        print("NO_TARGET"); return
    print("target:", target["url"][:120])
    fh = open(OUT, "w")
    async with websockets.connect(target["webSocketDebuggerUrl"], max_size=64*1024*1024,
                                  open_timeout=20, ping_interval=None) as ws:
        mid = 0
        async def send(method, params=None):
            nonlocal mid
            mid += 1
            await ws.send(json.dumps({"id": mid, "method": method, "params": params or {}}))
            return mid
        await send("Network.enable", {"maxTotalBufferSize": 256*1024*1024,
                                      "maxResourceBufferSize": 64*1024*1024,
                                      "maxPostDataSize": 4*1024*1024})
        await send("Page.enable"); await send("Runtime.enable"); await send("Log.enable")
        if os.environ.get("RELOAD") == "1":
            await asyncio.sleep(1.5)
            await send("Page.reload", {"ignoreCache": True})
            print("reloaded")
        pending_bodies = {}
        start = time.time()
        clicked = False
        while time.time() - start < DURATION:
            try:
                raw = await asyncio.wait_for(ws.recv(), timeout=5)
            except asyncio.TimeoutError:
                if not clicked and time.time() - start > 45:
                    clicked = True
                    await send("Runtime.evaluate", {"expression": "(()=>{const b=document.getElementById('agree-button'); if(b){b.click(); return 'clicked'} const c=document.getElementById('age-verification-container'); if(c){c.innerHTML=''; return 'cleared'} return 'none'})()"})
                continue
            msg = json.loads(raw)
            method = msg.get("method")
            if method == "Network.requestWillBeSent":
                p = msg["params"]; req = p["request"]
                url = req["url"]
                if any(h in url for h in HOSTS_KEEP) or not url.startswith("data:"):
                    rec = {"ev": "request", "ts": p["timestamp"], "id": p["requestId"],
                           "method": req["method"], "url": url,
                           "type": p.get("type"), "headers": req.get("headers", {}),
                           "postDataLen": len(req.get("postData") or ""),
                           "postData": (req.get("postData") or "")[:200000]}
                    fh.write(json.dumps(rec, ensure_ascii=False) + "\n")
            elif method == "Network.responseReceived":
                p = msg["params"]; r = p["response"]
                rec = {"ev": "response", "ts": p["timestamp"], "id": p["requestId"],
                       "status": r["status"], "mime": r.get("mimeType"), "url": r["url"],
                       "fromCache": r.get("fromDiskCache", False),
                       "headers": r.get("headers", {}), "type": p.get("type")}
                fh.write(json.dumps(rec, ensure_ascii=False) + "\n")
                if p.get("type") in ("XHR", "Fetch") and "gs_api" in r["url"]:
                    pending_bodies[p["requestId"]] = r["url"]
            elif method == "Network.loadingFinished":
                rid = msg["params"]["requestId"]
                if rid in pending_bodies:
                    await send("Network.getResponseBody", {"requestId": rid})
            elif method == "Network.loadingFailed":
                p = msg["params"]
                fh.write(json.dumps({"ev": "failed", "id": p["requestId"], "err": p.get("errorText"), "type": p.get("type")}, ensure_ascii=False) + "\n")
            elif msg.get("id") and "result" in msg:
                body = msg["result"].get("body")
                if body is not None:
                    fh.write(json.dumps({"ev": "body", "len": len(body),
                                         "b64": msg["result"].get("base64Encoded", False),
                                         "body": body[:400000]}, ensure_ascii=False) + "\n")
            elif method == "Runtime.consoleAPICalled":
                args = [a.get("value") if isinstance(a.get("value"), (str, int, float)) else a.get("description", "")[:300] for a in msg["params"]["args"]]
                line = " ".join(str(a)[:300] for a in args)
                if line.strip():
                    fh.write(json.dumps({"ev": "console", "text": line[:2000]}, ensure_ascii=False) + "\n")
        fh.close()
    print("DONE seconds:", round(time.time()-start,1))
asyncio.run(main())

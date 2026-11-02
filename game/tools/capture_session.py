import asyncio, json, os, time, urllib.request
import websockets

PORT = int(os.environ.get("CDP_PORT", "9333"))
DURATION = int(os.environ.get("CAPTURE_SECONDS", "300"))
OUT = os.environ.get("CAPTURE_OUT", "net/session.jsonl")
INTEREST = os.environ.get("INTEREST", "assetservice,gs_api,assets,get_assets")

def http_json(path):
    with urllib.request.urlopen(f"http://127.0.0.1:{PORT}{path}", timeout=5) as r:
        return json.loads(r.read().decode())

async def main():
    pages=[t for t in http_json('/json') if t.get('type')=='page']
    if not pages: print('NO_TARGET'); return
    target=pages[0]
    out=open(OUT,'w')
    async with websockets.connect(target['webSocketDebuggerUrl'], max_size=128*1024*1024, open_timeout=20, ping_interval=None) as ws:
        mid=0
        async def send(method, params=None):
            nonlocal mid
            mid+=1
            await ws.send(json.dumps({"id":mid,"method":method,"params":params or {}}))
            return mid
        # handle responses for Runtime.evaluate
        pending={}
        await send("Network.enable", {"maxTotalBufferSize":512*1024*1024,"maxResourceBufferSize":128*1024*1024,"maxPostDataSize":16*1024*1024})
        await send("Page.enable"); await send("Runtime.enable"); await send("Log.enable")
        body_wanted={}   # requestId -> url
        body_seen=set()
        async def maybe_body(rid, url):
            if rid in body_seen: return
            body_seen.add(rid)
            await send("Network.getResponseBody", {"requestId": rid})
        start=time.time(); last_click=0
        while time.time()-start < DURATION:
            try:
                raw=await asyncio.wait_for(ws.recv(), timeout=2)
            except asyncio.TimeoutError:
                # click age verification if visible
                if time.time()-last_click>3:
                    last_click=time.time()
                    await send("Runtime.evaluate", {"expression": "(()=>{const b=document.getElementById('agree-button'); if(b && b.getBoundingClientRect().width>0){b.click(); return 'clicked'}; return 'none'})()"})
                continue
            msg=json.loads(raw)
            m=msg.get('method')
            if m=='Network.requestWillBeSent':
                p=msg['params']; req=p['request']; url=req['url']
                rec={"ev":"request","ts":p['timestamp'],"id":p['requestId'],"method":req['method'],"url":url,
                     "type":p.get('type'),"headers":req.get('headers',{}),
                     "postData":(req.get('postData') or '')[:500000]}
                out.write(json.dumps(rec,ensure_ascii=False)+"\n"); out.flush()
            elif m=='Network.responseReceived':
                p=msg['params']; r=p['response']; url=r['url']
                rec={"ev":"response","ts":p['timestamp'],"id":p['requestId'],"status":r['status'],"mime":r.get('mimeType'),
                     "url":url,"fromCache":r.get('fromDiskCache',False),"headers":r.get('headers',{}),"type":p.get('type'),
                     "encodedDataLength":p.get('encodedDataLength')}
                out.write(json.dumps(rec,ensure_ascii=False)+"\n"); out.flush()
                if p.get('type') in ('XHR','Fetch') and any(k in url for k in INTEREST.split(',')):
                    body_wanted[p['requestId']]=url
            elif m=='Network.loadingFinished':
                rid=msg['params']['requestId']
                if rid in body_wanted:
                    await maybe_body(rid, body_wanted[rid])
            elif m=='Network.loadingFailed':
                p=msg['params']
                out.write(json.dumps({"ev":"failed","id":p['requestId'],"err":p.get('errorText'),"type":p.get('type')},ensure_ascii=False)+"\n"); out.flush()
            elif msg.get('id') and 'result' in msg:
                body=msg['result'].get('body')
                if body is not None:
                    out.write(json.dumps({"ev":"body","len":len(body),"b64":msg['result'].get('base64Encoded',False),"body":body[:2000000]},ensure_ascii=False)+"\n"); out.flush()
                elif 'result' in msg['result'] and isinstance(msg['result']['result'], dict):
                    val=msg['result']['result'].get('value')
                    if isinstance(val,str) and val!='none':
                        out.write(json.dumps({"ev":"eval","value":val},ensure_ascii=False)+"\n"); out.flush()
            elif m=='Runtime.consoleAPICalled':
                args=[]
                for a in msg['params']['args']:
                    if 'value' in a: args.append(a['value'])
                    else: args.append(a.get('description','')[:300])
                line=' '.join(str(a)[:400] for a in args if a is not None)
                if line.strip():
                    out.write(json.dumps({"ev":"console","text":line[:3000]},ensure_ascii=False)+"\n"); out.flush()
        out.close()
    print("DONE", round(time.time()-start,1))

asyncio.run(main())

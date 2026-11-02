import asyncio, json, os, time, urllib.request
import websockets

PORT=9333
DURATION=int(os.environ.get('CAPTURE_SECONDS','240'))
OUT=os.environ.get('CAPTURE_OUT','/tmp/lg-als.jsonl')
ALS_SAVE=os.environ.get('ALS_SAVE','runtime/assets_api/als_response.json')
BUNDLES_SAVE=os.environ.get('BUNDLES_SAVE','runtime/assets_api/bundle_urls.txt')

def http_json(path):
    with urllib.request.urlopen(f"http://127.0.0.1:{PORT}{path}", timeout=5) as r:
        return json.loads(r.read().decode())

async def main():
    pages=[t for t in http_json('/json') if t.get('type')=='page']
    if not pages: print('NO_TARGET'); return
    tgt=pages[0]
    out=open(OUT,'w')
    bundle_fh=open(BUNDLES_SAVE,'w')
    als_ids=set()
    async with websockets.connect(tgt['webSocketDebuggerUrl'], max_size=256*1024*1024, open_timeout=20, ping_interval=None) as ws:
        mid=0
        async def send(method,params=None):
            nonlocal mid
            mid+=1
            await ws.send(json.dumps({"id":mid,"method":method,"params":params or {}}))
            return mid
        await send("Network.enable", {"maxTotalBufferSize":1024*1024*1024,"maxResourceBufferSize":256*1024*1024,"maxPostDataSize":64*1024*1024})
        await send("Page.enable"); await send("Runtime.enable"); await send("Log.enable")
        await asyncio.sleep(1.0)
        await send("Page.reload", {"ignoreCache": True})
        print('reloaded')
        start=time.time(); last_click=0
        while time.time()-start < DURATION:
            try:
                raw=await asyncio.wait_for(ws.recv(), timeout=2)
            except asyncio.TimeoutError:
                if time.time()-last_click>2:
                    last_click=time.time()
                    await send("Runtime.evaluate", {"expression":"(()=>{const b=document.getElementById('agree-button'); if(b&&b.getBoundingClientRect().width>0){b.click();return 'clicked'} return 'none'})()"})
                    if time.time()-start>20 and time.time()-last_click<3:
                        await send("Input.dispatchMouseEvent", {"type":"mouseMoved","x":717,"y":513})
                        await send("Input.dispatchMouseEvent", {"type":"mousePressed","x":717,"y":513,"button":"left","clickCount":1})
                        await send("Input.dispatchMouseEvent", {"type":"mouseReleased","x":717,"y":513,"button":"left","clickCount":1})
                continue
            msg=json.loads(raw); m=msg.get('method')
            if m=='Network.requestWillBeSent':
                p=msg['params']; req=p['request']; url=req['url']
                out.write(json.dumps({"ev":"request","ts":p['timestamp'],"id":p['requestId'],"method":req['method'],"url":url,"type":p.get('type'),"hasPostData":p.get('hasPostData'),"headers":req.get('headers',{})},ensure_ascii=False)+"\n"); out.flush()
                if 'get_assets_with_als' in url and req['method']=='POST':
                    als_ids.add(p['requestId'])
                    await send("Network.getRequestPostData", {"requestId":p['requestId']})
                if 'assets.chickgoddess.com' in url:
                    bundle_fh.write(url+"\n"); bundle_fh.flush()
            elif m=='Network.responseReceived':
                p=msg['params']; r=p['response']; url=r['url']
                out.write(json.dumps({"ev":"response","ts":p['timestamp'],"id":p['requestId'],"status":r['status'],"mime":r.get('mimeType'),"url":url,"headers":r.get('headers',{}),"encodedDataLength":p.get('encodedDataLength')},ensure_ascii=False)+"\n"); out.flush()
            elif m=='Network.loadingFinished':
                rid=msg['params']['requestId']
                if rid in als_ids:
                    await send("Network.getResponseBody", {"requestId":rid})
            elif m=='Network.loadingFailed':
                p=msg['params']; out.write(json.dumps({"ev":"failed","id":p['requestId'],"err":p.get('errorText')},ensure_ascii=False)+"\n"); out.flush()
            elif msg.get('id') and 'result' in msg:
                res=msg['result']
                if 'postData' in res:
                    open('runtime/assets_api/als_request_post.json','w').write(res['postData'])
                    print('postData saved len', len(res['postData']))
                if 'body' in res:
                    body=res['body']; b64=res.get('base64Encoded',False)
                    open(ALS_SAVE,'w').write(body)
                    print('ALS body saved len', len(body), 'b64', b64)
                if isinstance(res.get('result'),dict) and res['result'].get('value')=='clicked':
                    print('clicked agree')
            elif m=='Runtime.consoleAPICalled':
                args=[a.get('value') if 'value' in a else a.get('description','')[:200] for a in msg['params']['args']]
                line=' '.join(str(a)[:300] for a in args if a is not None)
                if line.strip(): out.write(json.dumps({"ev":"console","text":line[:2000]},ensure_ascii=False)+"\n"); out.flush()
        out.close(); bundle_fh.close()
    print('DONE', round(time.time()-start,1))
asyncio.run(main())

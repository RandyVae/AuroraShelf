#!/usr/bin/env python3
import asyncio
import base64
import json
import os
import urllib.request
import websockets

ARTIFACTS_DIR = '/Users/itou/.gemini/antigravity/brain/049b3232-c902-4161-a70e-acac2e3dc8c7/test_hub_adaptation'
os.makedirs(ARTIFACTS_DIR, exist_ok=True)

async def test_heroes():
    targets = json.loads(urllib.request.urlopen('http://localhost:9222/json').read().decode('utf-8'))
    ws_url = targets[0]['webSocketDebuggerUrl']
    async with websockets.connect(ws_url, max_size=50*1024*1024) as ws:
        # Inject dynamic test style
        style_inject = """
        (() => {
          if (!document.getElementById('screen-title').hidden) {
            document.getElementById('btn-start').click();
          }
          APP.show('hub');
          let style = document.getElementById('dynamic-hub-test');
          if (!style) {
            style = document.createElement('style');
            style.id = 'dynamic-hub-test';
            document.head.appendChild(style);
          }
          style.textContent = `
            @media (max-width: 600px) {
              .hub-art {
                top: auto !important;
                bottom: calc(var(--safe-b) + 72px) !important;
                left: 50% !important;
                right: auto !important;
                width: auto !important;
                max-width: none !important;
                height: clamp(540px, 68vh, 620px) !important;
                max-height: none !important;
                object-position: bottom center !important;
                transform: translateX(-50%) !important;
                mask-image: linear-gradient(180deg, #000 78%, rgba(0,0,0,0.8) 90%, transparent 100%) !important;
                -webkit-mask-image: linear-gradient(180deg, #000 78%, rgba(0,0,0,0.8) 90%, transparent 100%) !important;
              }
            }
          `;
        })()
        """
        await ws.send(json.dumps({'id': 1, 'method': 'Runtime.evaluate', 'params': {'expression': style_inject}}))
        await ws.recv()
        await asyncio.sleep(0.5)

        test_keys = ['lucretia', 'regina', 'ravenna', 'kitty', 'esther', 'dominica']
        for idx, k in enumerate(test_keys):
            expr = f"""
            (() => {{
              APP.state.squad[0] = '{k}';
              APP.state.hubOutfit = 'nude';
              LGSave.persist(APP.state);
              UI.renderHub(APP.state, APP.data);
            }})()
            """
            await ws.send(json.dumps({'id': 10 + idx, 'method': 'Runtime.evaluate', 'params': {'expression': expr}}))
            await ws.recv()
            await asyncio.sleep(0.6)

            await ws.send(json.dumps({'id': 100 + idx, 'method': 'Page.captureScreenshot', 'params': {'format': 'png'}}))
            msg = json.loads(await ws.recv())
            img_data = base64.b64decode(msg.get('result', {}).get('data', ''))
            out_p = os.path.join(ARTIFACTS_DIR, f'test_{k}_18plus.png')
            with open(out_p, 'wb') as f:
                f.write(img_data)
            print(f'Captured: {out_p} ({len(img_data)} bytes)')

if __name__ == '__main__':
    asyncio.run(test_heroes())

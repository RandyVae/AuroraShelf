import urllib.request
import json
import base64
import os
from PIL import Image

# 1. Fetch reference states from replica/
ref_files = {
    'hub': 'replica/warm_profile_20261029_lobby.state-hub.png',
    'battle': 'replica/warm_profile_20261029_lobby.state-battle-room.png',
    'captains_path': 'replica/warm_profile_20261029_lobby.state-captains-path.png',
    'collection': 'replica/warm_profile_20261029_lobby.state-collection.png'
}

print("=== REFERENCE SCREEN METRICS ===")
for name, path in ref_files.items():
    if os.path.exists(path):
        im = Image.open(path)
        print(f"Ref {name:15}: size={im.size}, aspect={im.size[0]/im.size[1]:.3f}, bytes={os.path.getsize(path)}")


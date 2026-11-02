import os
from PIL import Image

def build_crops():
    ref = Image.open('replica/warm_profile_20261029_lobby.state-hub.png')
    w, h = ref.size # 2880, 1574
    # Top-right currency bar
    top_bar = ref.crop((int(w * 0.45), 0, w, int(h * 0.12)))
    top_bar.save('/tmp/ref_crop_topbar.png')
    
    # Bottom nav bar
    bottom_nav = ref.crop((0, int(h * 0.88), w, h))
    bottom_nav.save('/tmp/ref_crop_bottomnav.png')
    
    # Right-hand action buttons (FIGHT / Journey / Quests)
    right_action = ref.crop((int(w * 0.75), int(h * 0.40), w, int(h * 0.88)))
    right_action.save('/tmp/ref_crop_rightaction.png')
    
    # Left player info
    left_player = ref.crop((0, 0, int(w * 0.35), int(h * 0.18)))
    left_player.save('/tmp/ref_crop_leftplayer.png')
    
    print("Crops saved to /tmp/ref_crop_*.png")

if __name__ == '__main__':
    build_crops()

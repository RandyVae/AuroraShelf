import csv, os, sys, collections
from PIL import Image, ImageDraw
MAN=sys.argv[1] if len(sys.argv)>1 else 'assets/art/manifest.csv'
OUT=sys.argv[2] if len(sys.argv)>2 else 'assets/art/_qa'
os.makedirs(OUT, exist_ok=True)
rows=[r for r in csv.DictReader(open(MAN)) if r.get('file') and os.path.exists(r['file']) and not r.get('dup_of')]
by=collections.defaultdict(list)
for r in rows:
    by[r['group']].append(r)
for g, rs in sorted(by.items()):
    rs.sort(key=lambda r: -(int(r['width'] or 0)*int(r['height'] or 0)))
    sample=rs[:36]
    cols=6; rows_n=(len(sample)+cols-1)//cols; cell=220
    sheet=Image.new('RGB',(cols*cell, rows_n*cell),(24,24,28))
    d=ImageDraw.Draw(sheet)
    for i,r in enumerate(sample):
        try:
            im=Image.open(r['file']).convert('RGBA')
            im.thumbnail((cell-10,cell-24))
            x=(i%cols)*cell+5; y=(i//cols)*cell+5
            bg=Image.new('RGBA', im.size, (40,40,46,255)); bg.alpha_composite(im)
            sheet.paste(bg.convert('RGB'),(x,y))
            d.text((x, y+im.height+2), (r['asset'] or '')[:30], fill=(200,200,200))
        except Exception:
            pass
    p=os.path.join(OUT, f'{g}.jpg')
    sheet.save(p, quality=80)
    print(g, len(rs), '->', p)


from PIL import Image
im = Image.open(r"C:\Users\caendeith\.dsh\attachments\v1\objects\ec\ec13b079435bd0e067e2c1fb88a53b9a13fe198bb50abf2b0f740634f01486a1").convert("RGB")
W,H=im.size; px=im.load()
for y in range(0,16):
    row=[px[x,y] for x in range(0,W,1)]
    mx=max(sum(p)/3 for p in row)
    # find positions where brighter than bg
    hits=[x for x in range(W) if sum(px[x,y])/3>45]
    print(y, round(mx,1), (min(hits),max(hits),len(hits)) if hits else None)

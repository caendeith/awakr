
from PIL import Image
im = Image.open(r"C:\Users\caendeith\.dsh\attachments\v1\objects\ec\ec13b079435bd0e067e2c1fb88a53b9a13fe198bb50abf2b0f740634f01486a1").convert("RGB")
W,H=im.size; px=im.load()
for y in [4,6,8,10]:
    runs=[]; start=None
    for x in range(W):
        r,g,b=px[x,y]
        bright=(r+g+b)/3>70
        if bright and start is None: start=x
        if not bright and start is not None:
            if x-start>5: runs.append((start,x-1))
            start=None
    print("row",y,runs[:8])
print("bg samples", px[50,50], px[1000,20], px[2000,1200])
# group red clusters
reds=[]
for y in range(0,H,1):
    for x in range(0,W,1):
        r,g,b=px[x,y]
        if r>150 and g<95 and b<95:
            reds.append((x,y))
# simple clustering by y band
band={}
for x,y in reds:
    k=(y//25, x//120)
    band.setdefault(k,[]).append((x,y))
for k,v in sorted(band.items(), key=lambda kv: min(p[1] for p in kv[1])):
    xs=[p[0] for p in v]; ys=[p[1] for p in v]
    print("cluster x",min(xs),max(xs),"y",min(ys),max(ys),"n",len(v))

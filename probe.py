
from PIL import Image
im = Image.open(r"C:\Users\caendeith\.dsh\attachments\v1\objects\ec\ec13b079435bd0e067e2c1fb88a53b9a13fe198bb50abf2b0f740634f01486a1").convert("RGB")
W,H = im.size
px = im.load()
# top rows bright runs
for y in [0,2,4,6,8,10,12]:
    runs=[]; start=None
    for x in range(W):
        r,g,b = px[x,y]
        bright = (r+g+b)/3 > 120
        if bright and start is None: start=x
        if not bright and start is not None:
            if x-start>5: runs.append((start,x-1))
            start=None
    print("row",y,runs[:6])
# right edge columns 2140-2165 sample of colors at few rows
for x in [2145,2150,2155,2160,2164]:
    print("col",x,[px[x,y] for y in (300,700,1200)])
# red-ish pixels
red=[]
for y in range(0,H,3):
    for x in range(0,W,3):
        r,g,b=px[x,y]
        if r>140 and g<90 and b<90:
            red.append((x,y,(r,g,b)))
print("red count", len(red), red[:15])

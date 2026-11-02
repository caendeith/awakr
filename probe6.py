
from PIL import Image
im = Image.open(r"C:\Users\caendeith\.dsh\attachments\v1\objects\ec\ec13b079435bd0e067e2c1fb88a53b9a13fe198bb50abf2b0f740634f01486a1").convert("RGB"); px=im.load()
W,H=im.size
# find rows with bright pixels in x 260..600 (left column text)
rows=[]
for y in range(20,400):
    n=sum(1 for x in range(260,600) if sum(px[x,y])/3>100)
    rows.append((y,n))
for y,n in rows:
    if n>0: print(y,n)

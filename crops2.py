
from PIL import Image
im = Image.open(r"C:\Users\caendeith\.dsh\attachments\v1\objects\ec\ec13b079435bd0e067e2c1fb88a53b9a13fe198bb50abf2b0f740634f01486a1").convert("RGB")
crops = {
 "mid1": (200,470,1620,760),
 "mid2": (200,750,1620,1090),
 "topleft": (100,0,500,120),
 "rightedge": (2050,0,2166,1317),
}
for k,box in crops.items():
    c = im.crop(box); w,h=c.size
    c = c.resize((int(w*2.2),int(h*2.2)), Image.LANCZOS)
    c.save(rf"D:\hack zone\fuck_alarmy\crops\{k}.png")
    print(k, c.size)

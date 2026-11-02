
from PIL import Image
im = Image.open(r"C:\Users\caendeith\.dsh\attachments\v1\objects\ec\ec13b079435bd0e067e2c1fb88a53b9a13fe198bb50abf2b0f740634f01486a1")
print(im.size, im.mode)
crops = {
 "top": (0,0,2166,180),
 "sidebar": (1550,0,2166,660),
 "bottom": (200,1080,1620,1317),
 "left": (200,100,1620,480),
}
import os
os.makedirs(r"D:\hack zone\fuck_alarmy\crops", exist_ok=True)
for k,box in crops.items():
    c = im.crop(box)
    w,h = c.size
    c = c.resize((w*2,h*2), Image.LANCZOS)
    p = rf"D:\hack zone\fuck_alarmy\crops\{k}.png"
    c.save(p)
    print(k, box, c.size, p)

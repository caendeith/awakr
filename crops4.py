
from PIL import Image
im = Image.open(r"C:\Users\caendeith\.dsh\attachments\v1\objects\ec\ec13b079435bd0e067e2c1fb88a53b9a13fe198bb50abf2b0f740634f01486a1").convert("RGB")
for k,box,sc in [("f2",(230,690,1620,860),3),("f3",(230,860,1620,1010),3),("f4",(230,1010,1620,1180),3)]:
    c=im.crop(box); w,h=c.size
    c.resize((int(w*sc),int(h*sc)), Image.LANCZOS).save(rf"D:\hack zone\fuck_alarmy\crops\{k}.png")
print("ok")

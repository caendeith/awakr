
from PIL import Image
im = Image.open(r"C:\Users\caendeith\.dsh\attachments\v1\objects\ec\ec13b079435bd0e067e2c1fb88a53b9a13fe198bb50abf2b0f740634f01486a1").convert("RGB")
c = im.crop((230,1200,1620,1317)); w,h=c.size
c.resize((int(w*2.6),int(h*2.6)), Image.LANCZOS).save(r"D:\hack zone\fuck_alarmy\crops\tail.png")
c2 = im.crop((1600,20,2100,200)); w,h=c2.size
c2.resize((int(w*3),int(h*3)), Image.LANCZOS).save(r"D:\hack zone\fuck_alarmy\crops\sidebar_top.png")
print("done")

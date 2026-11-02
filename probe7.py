
from PIL import Image
from collections import Counter
im = Image.open(r"C:\Users\caendeith\.dsh\attachments\v1\objects\ec\ec13b079435bd0e067e2c1fb88a53b9a13fe198bb50abf2b0f740634f01486a1").convert("RGB"); px=im.load()
def modal(x0,y0,x1,y1,name):
    c=Counter()
    for y in range(y0,y1):
        for x in range(x0,x1):
            p=px[x,y]
            if sum(p)/3>110: c[p]+=1
    print(name, c.most_common(3))
modal(262,82,600,102,"title text")
modal(250,145,340,166,"Guidelines heading")
modal(262,795,520,820,"github placeholder")
modal(1660,80,1990,120,"sidebar placeholder")
im.crop((1530,660,1600,700)).resize((70*8,40*8), Image.LANCZOS).save(r"D:\hack zone\fuck_alarmy\crops\icon.png")

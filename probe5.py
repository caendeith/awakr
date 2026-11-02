
from PIL import Image
im = Image.open(r"C:\Users\caendeith\.dsh\attachments\v1\objects\ec\ec13b079435bd0e067e2c1fb88a53b9a13fe198bb50abf2b0f740634f01486a1").convert("RGB"); px=im.load()
def bright(x0,y0,x1,y1,name):
    vals=[]
    for y in range(y0,y1):
        for x in range(x0,x1):
            v=sum(px[x,y])/3
            if v>90: vals.append(v)
    vals.sort()
    print(name, "n",len(vals), "p90", round(vals[int(len(vals)*0.9)],1) if vals else None, "max", round(vals[-1],1) if vals else None)
bright(265,75,520,105,"title placeholder")
bright(250,255,400,285,"'Guidelines' heading")
bright(265,795,470,825,"github placeholder")
bright(265,1090,700,1120,"categories helper")
bright(1660,80,1980,120,"sidebar placeholder")
c=im.crop((1500,655,1620,700)); w,h=c.size; c.resize((w*6,h*6), Image.LANCZOS).save(r"D:\hack zone\fuck_alarmy\crops\icon.png")
c=im.crop((1480,760,1540,800))
print("done")


from PIL import Image
from collections import Counter
im = Image.open(r"C:\Users\caendeith\.dsh\attachments\v1\objects\ec\ec13b079435bd0e067e2c1fb88a53b9a13fe198bb50abf2b0f740634f01486a1").convert("RGB"); px=im.load()
print("tag1 bg", px[1650,216], px[1700,210])
print("tag1 text", Counter([px[x,y] for y in range(206,222) for x in range(1635,1750)]).most_common(3))
print("tag2 bg", px[1830,216])
print("tag2 text", Counter([px[x,y] for y in range(206,222) for x in range(1800,1920)]).most_common(3))
# card borders: scan for lighter vertical lines
for x in range(230,260):
    print("x",x, px[x,400])

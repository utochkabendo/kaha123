import sys, os, time
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from meshlib import *
COL = {'metal_dark':(58,60,64),'metal':(120,122,128),'wood':(150,78,38),'wood_dark':(110,55,28),'bakelite':(90,40,25),
 'mag_orange':(170,70,30),'polymer':(40,40,42),'polymer_od':(88,96,60),'polymer_tan':(170,145,105),'polymer_clear':(120,110,80),
 'rubber':(25,25,25),'bore':(10,10,10),'brass':(190,150,60),'lens':(40,60,110),'white':(230,230,230),'chrome':(190,190,195)}
OUT = '/tmp/claude-0/-home-user-kaha123/3fe93671-e0b9-5212-ab2a-d04b13350ccd/scratchpad/prev'
def prev(W, suffix=''):
    t=time.time()
    render_preview(W, f'{OUT}/{W.name}{suffix}.png', colors=COL, yaw=20, pitch=12)
    return time.time()-t
if __name__ == '__main__':
    import weapons_rifles as R
    names = sys.argv[1:]
    for fn in R.RIFLES:
        if names and fn.__name__ not in names: continue
        W = fn()
        n = sum(len(g.f) for g in W.parts.values())
        print(fn.__name__, n, 'tris', round(prev(W),2))

def sheet(names, out, cols=2, w=600, h=280):
    from PIL import Image, ImageDraw
    rows = (len(names) + cols - 1) // cols
    S = Image.new('RGB', (cols * w, rows * h), (20, 20, 20))
    d = ImageDraw.Draw(S)
    for i, n in enumerate(names):
        im = Image.open(f'{OUT}/{n}.png').resize((w, h))
        S.paste(im, ((i % cols) * w, (i // cols) * h))
        d.text(((i % cols) * w + 8, (i // cols) * h + 6), n, fill=(255, 255, 0))
    S.save(out)

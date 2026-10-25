import numpy as np
def down(a,f):
    if f<=1: return a
    h,w=a.shape; return a[:h//f*f,:w//f*f].reshape(h//f,f,w//f,f).mean((1,3))
def blur3(a):
    p=np.pad(a,1,mode='edge'); return sum(p[j:j+a.shape[0],i:i+a.shape[1]] for j in range(3) for i in range(3))/9
def grad(l):
    g=np.zeros_like(l); g[1:-1,1:-1]=abs(l[1:-1,2:]-l[1:-1,:-2])+abs(l[2:,1:-1]-l[:-2,1:-1]); return g
def ncc(a,b,dx,dy,x0,y0,x1,y1):
    h,w=a.shape
    xs=max(x0,max(0,-dx)); xe=min(x1,min(w,w-dx)); ys=max(y0,max(0,-dy)); ye=min(y1,min(h,h-dy))
    if xe<=xs or ye<=ys or (xe-xs)*(ye-ys)<64: return -1
    A=a[ys:ye,xs:xe].astype(float); B=b[ys+dy:ye+dy,xs+dx:xe+dx].astype(float)
    n=A.size; va=(A*A).sum()-A.sum()**2/n; vb=(B*B).sum()-B.sum()**2/n
    if va<1e-6 or vb<1e-6: return -1
    return ((A*B).sum()-A.sum()*B.sum()/n)/np.sqrt(va*vb)
def search(a,b,cdx,cdy,r,reg):
    sc={}; best=-2
    for j in range(-r,r+1):
        for i in range(-r,r+1):
            s=ncc(a,b,cdx+i,cdy+j,*reg); sc[(cdx+i,cdy+j)]=s
            if s>best: best=s; bp=(cdx+i,cdy+j)
    sec=max([s for k,s in sc.items() if max(abs(k[0]-bp[0]),abs(k[1]-bp[1]))>=2]+[-2])
    return bp,best,sec
def estimate(base,mov,maxfrac=.03):
    h,w=base.shape; f=max(1,w//256)
    b=down(base,f); m=down(mov,f); gb=grad(b); gm=grad(m); r=max(2,round(maxfrac*b.shape[1]))
    bp,s,sec=search(gb,gm,0,0,r,(2,2,b.shape[1]-2,b.shape[0]-2))
    if s<.2 or s-sec<.02: return (0,0,False,s)
    dx,dy=bp[0]*f,bp[1]*f
    if f>1:
        gbf=grad(blur3(base)); gmf=grad(blur3(mov)); cw=min(w//2,640); ch=min(h//2,640); x0=(w-cw)//2; y0=(h-ch)//2
        q,qs,_=search(gbf,gmf,dx,dy,f,(x0,y0,x0+cw,y0+ch))
        if qs>=.5*s: dx,dy=q
    return (dx,dy,True,s)
rng=np.random.default_rng(3)
H,W=576,1024
yy,xx=np.mgrid[0:H,0:W]
scene=20+10*rng.random((H//8,W//8)).repeat(8,0).repeat(8,1)
scene=down(scene,1)
sc=np.where(yy>420,60+30*np.sin(xx/9.0)*np.cos(yy/7.0),scene)  # ground w/ texture
# trees: vertical edges
for k in range(40): x=rng.integers(0,W); sc[300:420,x:x+3]+=50
sc=sc+rng.normal(0,1.0,sc.shape)
def shifted(img,dx,dy): # aligned(x,y)=mov(x+dx,y+dy) => mov(x,y)=img(x-dx,y-dy)
    return np.roll(np.roll(img,dx,1),dy,0)
for (dx,dy) in [(0,0),(5,-3),(-9,6),(14,2)]:
    mov=shifted(sc,dx,dy).copy()
    # add lightning line + sky brightening on mov
    mov[:300,:]+=15; mov[50:400,500:502]+=150
    # mov(x,y)=sc(x-dx,y-dy) so aligned(x,y)=mov(x+dx',y+dy') with dx'=dx? aligned(x,y)=sc(x,y)=mov(x+dx,y+dy) -> expect (dx,dy)
    print((dx,dy),'->',estimate(sc,mov))
print('night noise only:',estimate(20+rng.normal(0,2,(H,W)),20+rng.normal(0,2,(H,W))))

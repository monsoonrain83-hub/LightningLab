import cv2, numpy as np
from load import load
def grad(a):
    g=np.zeros_like(a); g[1:-1,1:-1]=np.abs(a[1:-1,2:]-a[1:-1,:-2])+np.abs(a[2:,1:-1]-a[:-2,1:-1]); return g
def ncc_shift(a,b,R):
    """shift (dx,dy) s.t. b(x+dx,y+dy) ~ a(x,y); subpixel by parabola. a,b gradient images."""
    h,w=a.shape; best=-2; sc=np.full((2*R+1,2*R+1),-2.0)
    for dy in range(-R,R+1):
        for dx in range(-R,R+1):
            xs=max(R,-dx+R); xe=w-R-max(0,dx) ; ys=R; ye=h-R
            A=a[R:h-R,R:w-R]; B=b[R+dy:h-R+dy,R+dx:w-R+dx]
            va=A-A.mean(); vb=B-B.mean(); d=np.sqrt((va*va).sum()*(vb*vb).sum())
            s=(va*vb).sum()/d if d>1e-6 else -1
            sc[dy+R,dx+R]=s
    iy,ix=np.unravel_index(np.argmax(sc),sc.shape); best=sc[iy,ix]
    def sub(m,c,p):
        return 0.5*(m-p)/(m-2*c+p) if (m-2*c+p)!=0 else 0.0
    fx=fy=0.0
    if 0<ix<2*R: fx=sub(sc[iy,ix-1],sc[iy,ix],sc[iy,ix+1])
    if 0<iy<2*R: fy=sub(sc[iy-1,ix],sc[iy,ix],sc[iy+1,ix])
    return ix-R+fx, iy-R+fy, best
def track(F,R=5):
    """cumulative position p[i] (in 320-px units): frame i content is shifted by p[i] relative to frame 0."""
    n=len(F); p=np.zeros((n,2)); small=[grad(cv2.resize(f.astype(np.float32),None,fx=0.5,fy=0.5,interpolation=cv2.INTER_AREA)) for f in F]
    for i in range(1,n):
        dx,dy,s=ncc_shift(small[i-1],small[i],R)   # b=i shifted relative to a=i-1 by (dx,dy) at half-res
        p[i]=p[i-1]+2*np.array([dx,dy])
    return p
def shift_img(img,dx,dy):
    M=np.float32([[1,0,-dx],[0,1,-dy]])   # out(x,y)=img(x+dx,y+dy)
    return cv2.warpAffine(img,M,(img.shape[1],img.shape[0]),flags=cv2.INTER_LINEAR,borderMode=cv2.BORDER_REPLICATE)
def pick(ix,k=5):
    if len(ix)<=k: return ix
    return ix[np.round(np.linspace(0,len(ix)-1,k)).astype(int)]
def transient(F,T,p,W1=120,W2=600,low=0.25):
    n=len(F); out=[]; Ff=F.astype(np.float32)
    for i in range(n):
        t=T[i]; b=np.where((T>=t-W2)&(T<=t-W1))[0]; a=np.where((T>=t+W1)&(T<=t+W2))[0]
        bases=[]
        for ix in (b,a):
            if len(ix)==0: continue
            ix=pick(ix); stack=np.stack([shift_img(Ff[j],*(p[i]-p[j])*-1) if False else shift_img(Ff[j],*(p[j]-p[i])) for j in ix])
            k=max(0,int(np.floor((len(ix)-1)*low))); bases.append(np.sort(stack,axis=0)[k])
        if not bases: out.append(None); continue
        base=np.maximum.reduce(bases) if len(bases)==2 else bases[0]+0
        out.append(np.clip(Ff[i]-base,0,None))
    return out

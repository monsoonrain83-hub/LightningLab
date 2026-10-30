import numpy as np, cv2
from v2 import shift_img
def near_transient(F,T,p,win_ms=100,minfr=1,border=8):
    n=len(F); Ff=F.astype(np.float32); out=np.zeros((n,6),np.float32); frames_per=None
    masks={}
    for i in range(n):
        t=T[i]
        b=np.where((T>=t-win_ms)&(T<t))[0]; a=np.where((T>t)&(T<=t+win_ms))[0]
        bases=[]
        for ix in (b,a):
            if len(ix)==0: continue
            st=np.stack([shift_img(Ff[j],*(p[j]-p[i])) for j in ix]); bases.append(st.min(0))
        if not bases: continue
        base=np.maximum(bases[0],bases[1]) if len(bases)==2 else bases[0]
        d=np.clip(Ff[i]-base,0,None); d[:border,:]=0;d[-border:,:]=0;d[:,:border]=0;d[:,-border:]=0
        out[i]=[(d>20).sum(),(d>35).sum(),np.percentile(d,99.9),d.max(),len(b),len(a)]
    return out

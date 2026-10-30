import numpy as np, cv2
from v2 import shift_img
K3=np.ones((7,7),np.uint8)
def feats(F,T,p,win_ms=100,border=8,T1=20):
    n=len(F); Ff=F.astype(np.float32); out=np.zeros((n,5),np.float32)
    for i in range(n):
        t=T[i]
        b=np.where((T>=t-win_ms)&(T<t))[0]; a=np.where((T>t)&(T<=t+win_ms))[0]
        bases=[]
        for ix in (b,a):
            if len(ix)==0: continue
            st=np.stack([shift_img(Ff[j],*(p[j]-p[i])) for j in ix]); bases.append(st)
        if not bases: continue
        mins=[s.min(0) for s in bases]; meds=[np.median(s,axis=0) for s in bases]
        base=np.maximum(mins[0],mins[1]) if len(mins)==2 else mins[0]
        d=np.clip(Ff[i]-base,0,None)
        ref=0.5*(meds[0]+meds[1]) if len(meds)==2 else meds[0]
        s=Ff[i]-ref                                # signed residual vs aligned neighbours
        neg=(s<-T1).astype(np.uint8)
        negd=cv2.dilate(neg,K3)>0
        pos=(d>T1)
        pos[:border,:]=False;pos[-border:,:]=False;pos[:,:border]=False;pos[:,-border:]=False
        unp=pos&~negd
        # largest elongated component among unpaired positives
        nl,lab,stats,_=cv2.connectedComponentsWithStats(unp.astype(np.uint8),connectivity=8)
        longest=0
        for c in range(1,nl):
            w=stats[c,cv2.CC_STAT_WIDTH];h=stats[c,cv2.CC_STAT_HEIGHT];ar=stats[c,cv2.CC_STAT_AREA]
            if ar>=6: longest=max(longest,max(w,h))
        out[i]=[pos.sum(),unp.sum(),neg.sum(),longest,d.max()]
    return out

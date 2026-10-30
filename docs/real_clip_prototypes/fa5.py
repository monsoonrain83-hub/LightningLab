import numpy as np
from fa import pick, strong, events
def score5(T,C,sens=0.6,lowq=0.25,NEAR=100,FAR=1000):
    n=len(T); st=strong(sens); wk=0.45*st; dead=0.25*wk
    fl=np.zeros(n,bool); E=np.zeros(n); R=np.zeros(n)
    for i in range(n):
        t=T[i]; lows=[];meds=[]
        for lo,hi in ((t-FAR,t-NEAR),(t+NEAR,t+FAR)):
            ix=np.where((T>=lo)&(T<=hi))[0]
            if len(ix)==0: continue
            ix=pick(ix); s=np.sort(C[ix],axis=0); k=len(ix)
            lows.append(s[int(np.floor((k-1)*lowq))]); meds.append(s[k//2])
        if not lows: continue
        two=len(lows)==2
        low=np.maximum(lows[0],lows[1]) if two else lows[0]+0.5*st
        if two:
            dB=C[i]-meds[0]; dA=C[i]-meds[1]
            rm=np.where((dB>0)&(dA>0),np.minimum(dB,dA),np.where((dB<0)&(dA<0),np.maximum(dB,dA),0))
        else: rm=C[i]-meds[0]
        dpos=C[i]-low
        pos=rm[rm>dead].sum(); neg=-rm[rm<-dead].sum()
        ratio=pos/(pos+neg) if pos+neg>0 else 1.0
        best=dpos.max(); weak=(dpos>wk).sum(); E[i]=np.clip(dpos,0,None).mean(); R[i]=ratio
        fl[i]= ratio>=0.7 and (best>st or weak>=6)
    return fl,E

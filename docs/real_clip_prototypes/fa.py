import numpy as np
EDGE=150; NEAR=150; FAR=1000; POS=0.7; WEAK=6; DEAD=0.25; MAXP=12
def strong(s): return 6+14*(1-s)
def pick(ix):
    cnt=len(ix); k=min(MAXP,cnt)
    return ix[[int(j*(cnt-1)//max(1,k-1)) for j in range(k)]]
def med(a): 
    k=len(a); return np.sort(a,axis=0)[k//2]
def score(T,C,S,sens=0.6):
    n=len(T); st=strong(sens); wk=0.45*st; dead=DEAD*wk
    fl=np.zeros(n,bool); rise=np.zeros(n); mrise=np.zeros(n); sr=np.full(n,np.nan); cellflag=np.zeros(n,bool)
    for i in range(n):
        t=T[i]
        if t-T[0]<EDGE or T[-1]-t<EDGE: continue
        b=np.where((T>=t-FAR)&(T<=t-NEAR))[0]; a=np.where((T>=t+NEAR)&(T<=t+FAR))[0]
        if len(b)==0 or len(a)==0: continue
        mb=med(C[pick(b)]); ma=med(C[pick(a)])
        dB=C[i]-mb; dA=C[i]-ma
        d=np.where((dB>0)&(dA>0),np.minimum(dB,dA),np.where((dB<0)&(dA<0),np.maximum(dB,dA),0))
        best=d.max(); weak=(d>wk).sum()
        pos=d[d>dead].sum(); neg=-d[d<-dead].sum()
        ratio=pos/(pos+neg) if pos+neg>0 else 0
        cellflag[i]= ratio>=POS and (best>st or weak>=WEAK)
        rise[i]=best; mrise[i]=pos/len(d)
        sr[i]=min(S[i]-np.median(S[pick(b)]),S[i]-np.median(S[pick(a)]))
    v=sr[~np.isnan(sr)]
    if len(v):
        m=np.median(v); mad=np.median(abs(v-m)); s=min(1,max(0,sens))
        thr=max(0.0008+0.002*(1-s), m+(3+3*(1-s))*1.4826*mad)
    else: thr=1e9
    sflag=np.nan_to_num(sr,nan=-1)>thr
    return cellflag,sflag,rise,sr,thr
def events(T,flag,minsep=250):
    idx=np.where(flag)[0]; ev=[]; g=0
    while g<len(idx):
        s0=idx[g]; e0=s0; j=g+1
        while j<len(idx) and T[idx[j]]-T[e0]<=minsep: e0=idx[j]; j+=1
        ev.append((int(s0),int(e0))); g=j
    return ev

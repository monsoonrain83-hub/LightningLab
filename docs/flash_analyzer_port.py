import numpy as np
GX,GY=16,9; CN=GX*GY
def strong(s): return 6+14*(1-min(1,max(0,s)))
def run(T, C, sens=0.6, minsep=250):
    n=len(T); st=strong(sens); wk=.45*st; flagged=np.zeros(n,bool)
    for i in range(n):
        t=T[i]
        if t-T[0]<150 or T[-1]-t<150: continue
        b=np.where((T>=t-1000)&(T<=t-150))[0]; a=np.where((T>=t+150)&(T<=t+1000))[0]
        if len(b)==0 or len(a)==0: continue
        def pick(ix):
            k=min(12,len(ix)); return ix[[int(j*(len(ix)-1)//max(1,k-1)) for j in range(k)]]
        mb=np.sort(C[pick(b)],axis=0)[min(12,len(b))//2]; ma=np.sort(C[pick(a)],axis=0)[min(12,len(a))//2]
        dB=C[i]-mb; dA=C[i]-ma
        d=np.where((dB>0)&(dA>0),np.minimum(dB,dA),np.where((dB<0)&(dA<0),np.maximum(dB,dA),0))
        pos=d[d>.25*wk].sum(); neg=-d[d<-.25*wk].sum(); best=d.max()
        r=pos/(pos+neg) if pos+neg>0 else 0
        flagged[i]= r>=.7 and (best>st or (d>wk).sum()>=6)
    idx=np.where(flagged)[0]; ev=[]; g=0
    while g<len(idx):
        s0=idx[g]; e0=s0; j=g+1
        while j<len(idx) and T[idx[j]]-T[e0]<=minsep: e0=idx[j]; j+=1
        ev.append((T[s0],T[e0])); g=j
    return ev
rng=np.random.default_rng(1)
def base(sec,fps=30,noise=0.7,level=60):
    T=np.arange(0,int(sec*1000),1000//fps); C=level+rng.normal(0,noise,(len(T),CN)); return T,C
def flash(T,C,t,amp,cells=3,dur=1):
    i=np.argmin(abs(T-t)); 
    for k in range(dur): C[i+k,:cells]+=amp
res={}
# 1 night, two flashes 0.5s apart
T,C=base(20); flash(T,C,5000,60); flash(T,C,5500,80); res['night two flashes (expect 2)']=run(T,C)
# 2 faint day: 8 cells +5
T,C=base(20,level=170); flash(T,C,8000,8,cells=10); res['faint day (expect 1)']=run(T,C)
# 3 scene cut
T,C=base(20); C[300:]+=50; res['scene cut (expect 0)']=run(T,C)
# 4 camera shake: mixed-sign big changes on random frames
T,C=base(20)
for i in range(100,500,3): C[i]+=rng.normal(0,25,CN)
res['shake (expect 0)']=run(T,C)
# 5 exposure ramp
T,C=base(20); C+= (np.arange(len(T))/len(T))[:,None]*60; res['slow ramp (expect 0)']=run(T,C)
# 6 flash at video start (title) 
T,C=base(10); C[0:2,:4]+=80; res['edge flash ignored (expect 0)']=run(T,C)
# 7 noisy video w/ 1 weak flash
T,C=base(30,noise=1.5); flash(T,C,12000,40); res['noisy+flash (expect 1)']=run(T,C)
# 8 pure noise
T,C=base(60,noise=1.0); res['pure noise 60s (expect 0)']=run(T,C)
for k,v in res.items(): print(k,'->',len(v),v[:4])

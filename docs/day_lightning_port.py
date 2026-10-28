import numpy as np
rng=np.random.default_rng(7)
def run(S, sens=0.6, fps=30):
    n=len(S); T=np.arange(n)*1000/fps
    rise=np.full(n,np.nan)
    for i in range(n):
        t=T[i]
        if t-T[0]<150 or T[-1]-t<150: continue
        b=np.where((T>=t-1000)&(T<=t-150))[0]; a=np.where((T>=t+150)&(T<=t+1000))[0]
        if len(b)==0 or len(a)==0: continue
        rise[i]=min(S[i]-np.median(S[b]),S[i]-np.median(S[a]))
    v=rise[~np.isnan(rise)]
    med=np.median(v); mad=np.median(abs(v-med))
    floor=0.0008+0.002*(1-sens); k=3+3*(1-sens)
    thr=max(floor, med+k*1.4826*mad)
    fl=np.nan_to_num(rise,nan=-1)>thr
    idx=np.where(fl)[0]; ev=[];g=0
    while g<len(idx):
        s0=idx[g];e0=s0;j=g+1
        while j<len(idx) and T[idx[j]]-T[e0]<=250: e0=idx[j];j+=1
        ev.append(round(T[s0]));g=j
    return ev,thr
def clip(sec,base=0.05,noise=0.0007,fps=30): return base+rng.normal(0,noise,sec*fps)
# 1 daytime faint flashes: +0.004 (single frame) and +0.003 (2 frames)
S=clip(30); S[300]+=0.004; S[600:602]+=0.003; print('2 day flashes',run(S))
# 2 scene cut
S=clip(30); S[450:]+=0.03; print('cut',run(S))
# 3 slow drift
S=clip(30)+np.linspace(0,0.02,900); print('drift',run(S))
# 4 shaky: bigger noise
S=clip(30,noise=0.0015); print('shaky noise 0.0015',run(S))
# 5 shaky noise, flash 0.006
S=clip(30,noise=0.0015); S[420]+=0.006; print('shaky+flash',run(S))
# 6 pure noise 120s FPs
S=clip(120); print('pure noise 120s',run(S))
# 7 faint flash 0.0018 (borderline)
S=clip(30); S[300]+=0.0018; print('borderline',run(S))

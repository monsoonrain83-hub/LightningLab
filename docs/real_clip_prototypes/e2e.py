import numpy as np
from fa import events
import fa5
def strokes(T,fl,E,s0,e0,dip=0.6,pk=66):
    idx=[i for i in range(s0,e0+1) if fl[i]]
    peaks=[i for i in idx if E[i]>0 and all(abs(T[j]-T[i])>pk or E[j]<=E[i] for j in range(s0,e0+1))]
    kept=[]
    for p in peaks:
        if not kept: kept.append(p); continue
        q=kept[-1]; v=E[q:p+1].min()
        if v<dip*min(E[q],E[p]): kept.append(p)
        elif E[p]>E[q]: kept[-1]=p
    if not kept: kept=[max(idx,key=lambda i:E[i])]
    cuts=[int(kept[k-1]+np.argmin(E[kept[k-1]:kept[k]+1])) for k in range(1,len(kept))]
    out=[]
    for k,p in enumerate(kept):
        st=s0 if k==0 else cuts[k-1]; en=max(st, e0 if k==len(kept)-1 else cuts[k]-1); out.append((st,en,p))
    return out
def analyze(k,sens):
    z=np.load(f'app_{k}.npz'); T,C=z['T'],z['C']; n=len(T)
    f3=np.load('A_f3.npy' if k=='A' else f'{k}_f3.npy'); f4=np.load(f'{k}_f4.npy')
    npx=(180*320) if k in 'AB' else (568*320)
    area=f3[:,0]/npx; unp=f4[:,1]*57600/npx; lon=f4[:,3]
    fl,E=fa5.score5(T,C,sens=sens)
    out=[]; covered=[]
    for s0,e0 in events(T,fl,250):
        edge=(T[s0]-T[0]<=120) or (T[-1]-T[e0]<=120)
        if not (edge or area[s0:e0+1].max()>=0.003): continue
        covered.append((T[s0],T[e0])); out+= [('cell',a,b,p) for a,b,p in strokes(T,fl,E,s0,e0)]
    L=16+24*(1-sens); U=120+300*(1-sens)
    di=[i for i in range(n) if (lon[i]>=L or unp[i]>=U) and not any(T[i]>=a-150 and T[i]<=b+150 for a,b in covered)]
    g=0
    while g<len(di):
        s0=di[g]; e0=s0; j=g+1
        while j<len(di) and T[di[j]]-T[e0]<=50: e0=di[j]; j+=1
        g=j; out.append(('day',s0,e0,max(range(s0,e0+1),key=lambda i:lon[i]+unp[i]/20)))
    return sorted(out,key=lambda x:x[1])
if __name__=='__main__':
    for sens in (0.6,0.7):
        print('=== sensitivity',sens)
        for k in 'ABCD':
            ev=analyze(k,sens); print(k,len(ev),'events:',[(t,a,b,p) for t,a,b,p in ev])

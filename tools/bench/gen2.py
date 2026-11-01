import cv2, numpy as np, json, sys
from gen import decode, bolt, write
F0,fps=decode('/home/claude/lightninglab/app/src/main/res/raw/lightning_hero.mp4')
F0=F0[:176]; n,h,w=F0.shape
AMPS=[6,9,12,16,22,30,45]
POS=[12,36,60,84,108,132,156]   # frame indices, >=0.8s apart
def variant(name,shake,noise,seed):
    rng=np.random.default_rng(seed); F=F0.copy()
    if shake>0:
        for i in range(n):
            dx,dy=rng.normal(0,shake,2); M=np.float32([[1,0,dx],[0,1,dy]])
            F[i]=cv2.warpAffine(F[i],M,(w,h),borderMode=cv2.BORDER_REPLICATE)
    if noise>0: F+=rng.normal(0,noise,F.shape).astype(np.float32)
    write(f'{name}_clean.bin',F,fps)
    G=F.copy(); truth=[]
    order=list(rng.permutation(len(AMPS)))
    for k,p in enumerate(POS):
        a=AMPS[order[k]]; m=bolt(h,w,rng,length_frac=rng.uniform(0.3,0.6))
        glow=cv2.GaussianBlur(m,(0,0),10)*8
        for d in range(1 if rng.random()<0.6 else 2):
            G[p+d]+=a*m+0.1*a*glow
        truth.append([int(round(p*1000/fps)),a])
    write(f'{name}_bolts.bin',G,fps); json.dump(truth,open(f'{name}_truth.json','w'))
for nm,sh,no in [('stable',0,0),('noisy',0,1.5),('shaky',1.0,1.5)]:
    for seed in (1,2):
        variant(f'{nm}{seed}',sh,no,seed)

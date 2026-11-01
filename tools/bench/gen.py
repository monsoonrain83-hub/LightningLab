import cv2, numpy as np, subprocess, sys, struct
def decode(path, w=320):
    cap=cv2.VideoCapture(path); fps=cap.get(cv2.CAP_PROP_FPS); F=[]
    while True:
        ok,f=cap.read()
        if not ok: break
        h0,w0=f.shape[:2]; h=max(2,w*h0//w0)
        # emulate block average like Detector
        g=cv2.cvtColor(f,cv2.COLOR_BGR2GRAY)
        F.append(cv2.resize(g,(w,h),interpolation=cv2.INTER_AREA).astype(np.float32))
    return np.array(F),fps
def bolt(h,w,rng,length_frac=0.45,width=1.0):
    m=np.zeros((h,w),np.float32)
    x=rng.uniform(0.25,0.75)*w; y=rng.uniform(0.05,0.3)*h; L=length_frac*h
    pts=[(x,y)]; ang=np.pi/2
    while y<pts[0][1]+L:
        ang=np.pi/2+rng.normal(0,0.5); x+=np.cos(ang)*4; y+=np.sin(ang)*4; pts.append((x,y))
    cv2.polylines(m,[np.array(pts,np.int32)],False,1.0,int(max(1,round(width))),cv2.LINE_AA)
    return m
def write(path,F,fps,tag=''):
    n,h,w=F.shape
    with open(path,'wb') as f:
        f.write(struct.pack('<iii',n,w,h))
        for i in range(n):
            f.write(struct.pack('<q',int(round(i*1000/fps)))); f.write(np.clip(F[i],0,255).astype('<f4').tobytes())
if __name__=='__main__':
    F,fps=decode('/home/claude/lightninglab/app/src/main/res/raw/lightning_hero.mp4')
    n,h,w=F.shape; print('hero',F.shape,fps)
    write('hero_clean.bin',F,fps)
    rng=np.random.default_rng(3)
    amps=[8,12,16,22,30,45]
    G=F.copy(); truth=[]
    for k,a in enumerate(amps):
        i=40+k*55; m=bolt(h,w,rng)
        for d in (0,1 if k%2 else 0):   # some are 2-frame
            G[i+d]+=a*m + (a*0.12)*cv2.GaussianBlur(m,(0,0),12)*10  # bolt + faint glow
        truth.append((int(round(i*1000/fps)),a))
    write('hero_bolts.bin',G,fps)
    open('truth.txt','w').write('\n'.join(f'{t} {a}' for t,a in truth))
    print(truth)
    H,fps2=decode('/home/claude/lightninglab/app/src/main/res/raw/lightning_header.mp4'); write('header.bin',H,fps2); print('header',H.shape,fps2)

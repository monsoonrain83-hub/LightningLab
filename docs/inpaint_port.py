import numpy as np
def avg(px,unk,w,h,i):
    x,y=i%w,i//w; acc=np.zeros(3); c=0
    for dy in (-1,0,1):
        for dx in (-1,0,1):
            if dx==0 and dy==0: continue
            xx,yy=x+dx,y+dy
            if 0<=xx<w and 0<=yy<h and not unk[yy*w+xx]: acc+=px[yy*w+xx]; c+=1
    return None if c==0 else acc/c
def fill(px,w,h,unk):
    n=w*h; q=np.zeros(n,bool); cur=[i for i in range(n) if unk[i] and avg(px,unk,w,h,i) is not None]
    for i in cur:q[i]=True
    while cur:
        col=[avg(px,unk,w,h,i) for i in cur]
        for i,c in zip(cur,col): px[i]=c; unk[i]=False
        nxt=[]
        for i in cur:
            x,y=i%w,i//w
            for dy in (-1,0,1):
                for dx in (-1,0,1):
                    xx,yy=x+dx,y+dy
                    if 0<=xx<w and 0<=yy<h:
                        j=yy*w+xx
                        if unk[j] and not q[j]: q[j]=True; nxt.append(j)
        cur=nxt
w,h=20,20; px=np.tile(np.array([10.,20,30]),(w*h,1)); unk=np.zeros(w*h,bool)
for y in range(6,14):
    for x in range(6,14): unk[y*w+x]=True; px[y*w+x]=255
fill(px,w,h,unk); print('flat ok',np.allclose(px,[10,20,30]),'unk left',unk.sum())
w,h=30,10; px=np.array([[(i%w)*8.,0,0] for i in range(w*h)]); unk=np.zeros(w*h,bool)
for y in range(3,7):
    for x in range(12,18): unk[y*w+x]=True
fill(px,w,h,unk); r=[px[y*w+x][0] for y in range(3,7) for x in range(12,18)]
print('gradient range',min(r),max(r),'bounds',8*11,8*18)

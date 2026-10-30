import cv2, numpy as np, sys, os
U='/mnt/user-data/uploads/'
NAMES={'A':'4_5922558656196385774.mp4','B':'VID_20261025_211156_828.mp4','C':'VID_20261028_224219_182.mp4','D':'VID_20261028_225358_128.mp4'}
def load(key, width=640):
    cap=cv2.VideoCapture(U+NAMES[key]); fr=[];ts=[]
    fps=cap.get(cv2.CAP_PROP_FPS)
    i=0
    while True:
        ok,f=cap.read()
        if not ok: break
        h,w=f.shape[:2]; s=width/w
        g=cv2.cvtColor(cv2.resize(f,(width,int(h*s)),interpolation=cv2.INTER_AREA),cv2.COLOR_BGR2GRAY)
        fr.append(g); ts.append(i*1000.0/fps); i+=1
    return np.array(fr),np.array(ts),fps
if __name__=='__main__':
    for k in NAMES:
        F,T,fps=load(k)
        m=F.reshape(len(F),-1).mean(1)
        print(k,NAMES[k],F.shape,fps,'mean luma min/med/max',m.min().round(1),np.median(m).round(1),m.max().round(1))

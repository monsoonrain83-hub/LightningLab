import cv2, numpy as np
from load import NAMES, U
GX,GY=16,9
def app_sample(G):
    H,W=G.shape; tw=min(320,W); th=max(2,tw*H//W); bw=max(1,W//tw); bh=max(1,H//th)
    Gs=G[:th*bh,:tw*bw].astype(np.float32).reshape(th,bh,tw,bw)[:,::2,:,::2].mean((1,3))
    cy=(np.arange(th)*GY//th)[:,None]; cx=(np.arange(tw)*GX//tw)[None,:]
    cid=(cy*GX+cx).ravel()
    cells=np.bincount(cid,weights=Gs.ravel(),minlength=GX*GY)/np.maximum(1,np.bincount(cid,minlength=GX*GY))
    blur=cv2.blur(Gs,(7,7),borderType=cv2.BORDER_REPLICATE)
    st=float(((Gs-blur)>18).mean())
    return Gs.mean(),cells.astype(np.float32),st
def extract(key):
    cap=cv2.VideoCapture(U+NAMES[key]); fps=cap.get(cv2.CAP_PROP_FPS)
    M=[];C=[];S=[];i=0
    while True:
        ok,f=cap.read()
        if not ok: break
        g=cv2.cvtColor(f,cv2.COLOR_BGR2GRAY)
        m,c,s=app_sample(g); M.append(m);C.append(c);S.append(s); i+=1
    T=np.arange(len(M))*1000.0/fps
    return T,np.array(M),np.array(C),np.array(S),fps
if __name__=='__main__':
    for k in NAMES:
        T,M,C,S,fps=extract(k); np.savez(f'app_{k}.npz',T=T,M=M,C=C,S=S,fps=fps)
        print(k,len(T),'struct med/max',np.median(S).round(5),S.max().round(5))

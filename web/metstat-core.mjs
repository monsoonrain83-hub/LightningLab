/* SPDX-License-Identifier: GPL-3.0-or-later
 * Browser adaptation of FlashAnalyzer.kt (cell path only), PhotoToneCurves.kt,
 * CloudAnalysis.kt and SkyFrameQuality.kt in LightningLab.
 * No DayLine motion compensation or TFLite verification: candidates only.
 */
export const clamp=(x,a=0,b=1)=>Math.max(a,Math.min(b,x));
export function toneLut(points){
 if(points.length!==5||points.some(x=>!Number.isFinite(x)))throw Error("five finite points required");
 return Uint8Array.from({length:256},(_,i)=>{const x=i/255*4,k=Math.min(3,Math.floor(x));return Math.round(255*clamp(points[k]+(points[k+1]-points[k])*(x-k)))});
}
export function grid(rgba,w,h){
 if(!Number.isInteger(w)||!Number.isInteger(h)||w<16||h<9||rgba.length!==w*h*4)throw Error("invalid frame");
 const cells=new Float32Array(144);
 for(let gy=0;gy<9;gy++)for(let gx=0;gx<16;gx++){
 let sum=0,n=0;
 for(let y=Math.floor(gy*h/9);y<Math.floor((gy+1)*h/9);y++)for(let x=Math.floor(gx*w/16);x<Math.floor((gx+1)*w/16);x++){
 const k=(y*w+x)*4;sum+=.299*rgba[k]+.587*rgba[k+1]+.114*rgba[k+2];n++;
 }cells[gy*16+gx]=sum/n;
 }return cells;
}
function pick(rows,c){const n=Math.min(12,rows.length);const a=Array.from({length:n},(_,j)=>rows[Math.floor(j*(rows.length-1)/Math.max(1,n-1))].cells[c]).sort((a,b)=>a-b);return [a[Math.floor((n-1)*.25)],a[Math.floor(n/2)]]}
export function flashCandidates(samples,sensitivity=.7,gap=150){
 if(samples.length>3000||!Number.isFinite(sensitivity)||!Number.isFinite(gap)||gap<0||samples.some((s,i)=>!Number.isFinite(s.t)||s.cells.length!==144||Array.from(s.cells).some(v=>!Number.isFinite(v)||v<0||v>255)||(i&&s.t<=samples[i-1].t)))throw Error("invalid samples");
 if(samples.length<5)return [];
 const strong=6+14*(1-clamp(sensitivity)),weak=.45*strong,dead=.25*weak,flagged=[];
 for(let i=0;i<samples.length;i++){
 const s=samples[i],before=[],after=[];
 for(let j=i-1;j>=0&&samples[j].t>=s.t-1000;j--)if(samples[j].t<=s.t-100)before.unshift(samples[j]);
 for(let j=i+1;j<samples.length&&samples[j].t<=s.t+1000;j++)if(samples[j].t>=s.t+100)after.push(samples[j]);
 if(!before.length&&!after.length)continue;
 let best=-Infinity,pos=0,neg=0,count=0,energy=0;
 for(let c=0;c<144;c++){
 const v=s.cells[c],b=before.length?pick(before,c):null,a=after.length?pick(after,c):null;
 let low,rm;
 if(a&&b){low=Math.max(a[0],b[0]);const db=v-b[1],da=v-a[1];rm=db>0&&da>0?Math.min(db,da):db<0&&da<0?Math.max(db,da):0}
 else{const side=a||b;low=side[0]+.5*strong;rm=v-side[1]}
 const d=v-low;best=Math.max(best,d);if(d>weak)count++;if(d>0)energy+=d;if(rm>dead)pos+=rm;else if(rm<-dead)neg-=rm;
 }
 if((pos+neg?pos/(pos+neg):1)>=.7&&(best>strong||count>=6))flagged.push({t:s.t,best,energy:energy/144,spread:count/144});
 }
 const groups=[];
 for(const s of flagged){let g=groups.at(-1);if(!g||s.t-g.end>gap){g={start:s.t,end:s.t,bestMs:s.t,energy:s.energy,spread:s.spread};groups.push(g)}else{g.end=s.t;if(s.energy>g.energy){g.bestMs=s.t;g.energy=s.energy;g.spread=s.spread}}}
 return groups;
}
export function skyEvidence(rgba,w,h){
 if(!Number.isInteger(w)||!Number.isInteger(h)||w<1||h<1||rgba.length!==w*h*4)throw Error("invalid image");
 let total=0,valid=0,cloud=0,clip=0,dark=0,warm=0;
 const stride=Math.max(1,Math.ceil(w*h/65536));
 for(let i=0;i<w*h;i+=stride){const k=i*4,r=rgba[k],g=rgba[k+1],b=rgba[k+2];if(rgba[k+3]<128)continue;total++;if(Math.min(r,g,b)>=248)clip++;if(Math.max(r,g,b)<=20)dark++;if(r-b>=45&&r-g>=20)warm++;if(r+b<20)continue;valid++;if((b-r)/(b+r)<.2||Math.min(r,g,b)>=225)cloud++}
 const pct=n=>total?Math.round(100*n/total):0,flags=[];if(pct(clip)>=10)flags.push("clipped");if(pct(dark)>=30)flags.push("dark");if(pct(warm)>=30)flags.push("warm");if(total<128||valid<128)flags.push("insufficient");
 const cover=valid>=128?cloud/valid:null;
 return {cover,oktas:cover===null?null:cover<.02?0:cover>.98?8:clamp(Math.round(8*cover),1,7),clipped:pct(clip),dark:pct(dark),warm:pct(warm),flags,valid};
}

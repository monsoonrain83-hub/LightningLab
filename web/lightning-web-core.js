/* MetStat / LightningLab Web: independent browser-safe prototype kernel.
 * Not a port of Android Detector/FlashAnalyzer nor validated lightning detection.
 * Deterministic luminance jump utility; no fetch, file IO or global state changes.
 */
(function(root,factory){'use strict';const api=Object.freeze(factory());if(typeof module==='object'&&module.exports)module.exports=api;if(root)root.LightningLabWebCore=api;})(typeof window!=='undefined'?window:null,function(){
 'use strict';
 function meanLuma(rgba){if(!rgba||rgba.length%4!==0||rgba.length===0)throw new TypeError('RGBA required');let y=0;for(let j=0;j<rgba.length;j+=4)y+=.2126*rgba[j]+.7152*rgba[j+1]+.0722*rgba[j+2];return y/(rgba.length/4)}
 function detectJumps(samples,{threshold=25,minSeparationSeconds=.25}={}){
  if(!Array.isArray(samples)||!Number.isFinite(threshold)||threshold<0||threshold>255||!Number.isFinite(minSeparationSeconds)||minSeparationSeconds<0)throw new TypeError('invalid scan parameters');
  const hits=[];let lastTime=-Infinity;for(let i=1;i<samples.length;i++){
   const a=samples[i-1],b=samples[i];if(!a||!b||!Number.isFinite(a.time)||!Number.isFinite(b.time)||!Number.isFinite(a.y)||!Number.isFinite(b.y)||b.time<=a.time)continue;
   const delta=b.y-a.y;if(delta>threshold&&b.time-lastTime>=minSeparationSeconds){hits.push({time:b.time,delta,heuristic:true});lastTime=b.time}
  }return hits;
 }
 return {meanLuma,detectJumps,scientificValidation:false};
});
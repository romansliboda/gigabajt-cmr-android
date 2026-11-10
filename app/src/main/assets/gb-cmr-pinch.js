(()=>{
'use strict';

const modal=document.getElementById('gbcmr-pinch-modal');
const surface=document.getElementById('gbcmr-pinch-surface');
const paper=document.getElementById('gbcmr-pinch-paper');
if(!modal||!surface||!paper)return;

const tabs=[...modal.querySelectorAll('[data-gbcmr-copy]')];
const points=new Map();
let chosen=0,scale=1,x=0,y=0,gesture=null;
let oldOverflow='',oldFocus=null;

const clamp=(n,a,b)=>Math.max(a,Math.min(b,n));

function dimensions(){
 return {
  w:paper.offsetWidth||794,
  h:paper.offsetHeight||1123,
  vw:surface.clientWidth,
  vh:surface.clientHeight
 };
}

function paint(){
 const d=dimensions(),m=15;
 if(d.w*scale<=d.vw)x=(d.vw-d.w*scale)/2;
 else x=clamp(x,d.vw-d.w*scale-m,m);
 if(d.h*scale<=d.vh)y=(d.vh-d.h*scale)/2;
 else y=clamp(y,d.vh-d.h*scale-m,m);
 paper.style.transform=`translate3d(${x}px,${y}px,0) scale(${scale})`;
}

function fit(){
 const d=dimensions();
 if(!d.vw||!d.vh)return;
 scale=Math.min((d.vw-24)/d.w,(d.vh-24)/d.h);
 x=(d.vw-d.w*scale)/2;
 y=(d.vh-d.h*scale)/2;
 paint();
}

function render(){
 paper.innerHTML=cmrSheet(getData(),chosen);
 fitCMR(paper);
 tabs.forEach(b=>b.classList.toggle(
  'selected',Number(b.dataset.gbcmrCopy)===chosen
 ));
 requestAnimationFrame(fit);
}

function startPinch(){
 if(points.size<2){gesture=null;return;}
 const p=[...points.values()].slice(0,2);
 const r=surface.getBoundingClientRect();
 const mx=(p[0].x+p[1].x)/2-r.left;
 const my=(p[0].y+p[1].y)/2-r.top;
 gesture={
  dist:Math.max(1,Math.hypot(p[1].x-p[0].x,p[1].y-p[0].y)),
  scale,
  anchorX:(mx-x)/scale,
  anchorY:(my-y)/scale
 };
}

surface.addEventListener('pointerdown',e=>{
 if(e.pointerType==='mouse'&&e.button!==0)return;
 e.preventDefault();
 surface.setPointerCapture(e.pointerId);
 points.set(e.pointerId,{x:e.clientX,y:e.clientY});
 if(points.size>=2)startPinch();
});

surface.addEventListener('pointermove',e=>{
 const old=points.get(e.pointerId);
 if(!old)return;
 e.preventDefault();
 points.set(e.pointerId,{x:e.clientX,y:e.clientY});

 if(points.size===1){
  x+=e.clientX-old.x;
  y+=e.clientY-old.y;
 }else if(points.size>=2){
  if(!gesture)startPinch();
  const p=[...points.values()].slice(0,2);
  const r=surface.getBoundingClientRect();
  const mx=(p[0].x+p[1].x)/2-r.left;
  const my=(p[0].y+p[1].y)/2-r.top;
  const dist=Math.max(1,Math.hypot(
   p[1].x-p[0].x,p[1].y-p[0].y
  ));
  scale=clamp(gesture.scale*dist/gesture.dist,.16,3.2);
  x=mx-gesture.anchorX*scale;
  y=my-gesture.anchorY*scale;
 }
 paint();
});

function end(e){
 points.delete(e.pointerId);
 try{
  if(surface.hasPointerCapture(e.pointerId))
   surface.releasePointerCapture(e.pointerId);
 }catch(_){}
 if(points.size>=2)startPinch();
 else gesture=null;
}

surface.addEventListener('pointerup',end);
surface.addEventListener('pointercancel',end);
surface.addEventListener('lostpointercapture',e=>{
 points.delete(e.pointerId);
 gesture=null;
});

window.gbCMROpenPinchPreview=()=>{
 oldFocus=document.activeElement;
 oldOverflow=document.body.style.overflow;
 document.body.style.overflow='hidden';
 modal.hidden=false;
 points.clear();
 chosen=0;
 render();
};

window.gbCMRClosePinchPreview=()=>{
 modal.hidden=true;
 document.body.style.overflow=oldOverflow;
 points.clear();
 gesture=null;
 if(oldFocus&&oldFocus.focus)oldFocus.focus();
};

tabs.forEach(b=>b.addEventListener('click',()=>{
 chosen=Number(b.dataset.gbcmrCopy);
 render();
}));

document.getElementById('gbcmr-pinch-close')
 .addEventListener('click',window.gbCMRClosePinchPreview);
document.getElementById('gbcmr-pinch-fit')
 .addEventListener('click',fit);

document.addEventListener('keydown',e=>{
 if(e.key==='Escape'&&!modal.hidden)
  window.gbCMRClosePinchPreview();
});

window.addEventListener('resize',()=>{
 if(!modal.hidden)requestAnimationFrame(fit);
});
})();

/* GB_CMR_ARCHIVE_PINCH_133 */
(()=>{
'use strict';

const clamp=(v,a,b)=>Math.max(a,Math.min(b,v));

window.gbCMRInitArchivePinch=()=>{
 const root=document.getElementById('archiveData');
 const frame=root&&root.querySelector('.paper-scroll');
 const holder=root&&root.querySelector('.paper-holder');
 if(!frame||!holder)return;

 // Zachowaj oryginalna wysokosc pola podgladu.
 // Dopasowanie do stalej ramki Historii CMR.

 frame.style.height='min(50vh, 430px)';
 frame.style.padding='0';
 frame.style.position='relative';
 frame.style.overflow='hidden';
 frame.style.touchAction='none';

 holder.style.zoom='1';
 holder.style.position='absolute';
 holder.style.left='0';
 holder.style.top='0';
 holder.style.margin='0';
 holder.style.transformOrigin='0 0';
 holder.style.willChange='transform';

 let scale=1;
 let x=0,y=0,pinch=null;
 const fingers=new Map();

 function paint(){
  const w=frame.clientWidth;
  const h=frame.clientHeight;
  const sw=holder.offsetWidth*scale;
  const sh=holder.offsetHeight*scale;
  const edge=12;

  x=sw<=w-2*edge
   ?(w-sw)/2
   :clamp(x,w-sw-edge,edge);

  y=sh<=h-2*edge
   ?(h-sh)/2
   :clamp(y,h-sh-edge,edge);

  holder.style.transform=
   `translate3d(${x}px,${y}px,0) scale(${scale})`;
 }

 function pinchStart(){
  const p=[...fingers.values()].slice(0,2);
  if(p.length!==2){
   pinch=null;
   return;
  }

  const r=frame.getBoundingClientRect();
  const mx=(p[0].x+p[1].x)/2-r.left;
  const my=(p[0].y+p[1].y)/2-r.top;

  pinch={
   dist:Math.max(1,Math.hypot(
    p[1].x-p[0].x,p[1].y-p[0].y
   )),
   scale,
   ax:(mx-x)/scale,
   ay:(my-y)/scale
  };
 }

 const dw=holder.offsetWidth||794;
 const dh=holder.offsetHeight||1123;

 scale=clamp(Math.min(
  (frame.clientWidth-24)/dw,
  (frame.clientHeight-24)/dh
 ),.16,3.2);

 x=(frame.clientWidth-dw*scale)/2;
 y=(frame.clientHeight-dh*scale)/2;
 paint();

 frame.addEventListener('pointerdown',e=>{
  if(e.pointerType==='mouse'&&e.button!==0)return;

  if(e.cancelable)e.preventDefault();

  try{
   frame.setPointerCapture(e.pointerId);
  }catch(_){}

  fingers.set(e.pointerId,{
   x:e.clientX,y:e.clientY
  });

  if(fingers.size>=2)pinchStart();
 });

 frame.addEventListener('pointermove',e=>{
  const old=fingers.get(e.pointerId);
  if(!old)return;

  if(e.cancelable)e.preventDefault();

  fingers.set(e.pointerId,{
   x:e.clientX,y:e.clientY
  });

  if(fingers.size===1){
   x+=e.clientX-old.x;
   y+=e.clientY-old.y;
  }else{
   if(!pinch)pinchStart();

   const p=[...fingers.values()].slice(0,2);
   const r=frame.getBoundingClientRect();
   const mx=(p[0].x+p[1].x)/2-r.left;
   const my=(p[0].y+p[1].y)/2-r.top;

   const dist=Math.max(1,Math.hypot(
    p[1].x-p[0].x,p[1].y-p[0].y
   ));

   scale=clamp(
    pinch.scale*dist/pinch.dist,.16,3.2
   );

   x=mx-pinch.ax*scale;
   y=my-pinch.ay*scale;
  }

  paint();
 });

 function finish(e){
  fingers.delete(e.pointerId);

  if(fingers.size>=2)pinchStart();
  else pinch=null;
 }

 frame.addEventListener('pointerup',finish);
 frame.addEventListener('pointercancel',finish);
 frame.addEventListener('lostpointercapture',finish);
};
})();

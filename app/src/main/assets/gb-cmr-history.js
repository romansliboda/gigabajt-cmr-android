
(()=>{
'use strict';

const section=document.querySelector(
 '#settingsAbout .about-history'
);
const versionLabel=document.getElementById(
 'gbcmr-version-label'
);

if(!section||!versionLabel)return;

const version=versionLabel.textContent.trim();
const key='gbcmr-history-first-open-v137';
const duration=48*60*60*1000;
const now=Date.now();

let firstSeen=now;

try{
 const saved=JSON.parse(localStorage.getItem(key)||'null');

 if(saved &&
    saved.version===version &&
    Number.isFinite(saved.firstSeen) &&
    saved.firstSeen>0 &&
    saved.firstSeen<=now){
  firstSeen=saved.firstSeen;
 }else{
  localStorage.setItem(
   key,
   JSON.stringify({version,firstSeen:now})
  );
 }
}catch(e){}

// Pokazuj tylko piec najnowszych wpisow.
const entries=[...section.children].filter(
 element=>element.tagName==='P'
);

entries.forEach((entry,index)=>{
 entry.hidden=index>=5;
});

// Cala historia znika po 48 godzinach.
function refresh(){
 const expired=Date.now()-firstSeen>=duration;
 section.style.display=expired?'none':'';
}

window.addEventListener('pageshow',refresh);
window.addEventListener('focus',refresh);

document.addEventListener('visibilitychange',()=>{
 if(!document.hidden)refresh();
});

const remaining=duration-(now-firstSeen);

if(remaining>0){
 setTimeout(refresh,remaining+250);
}

refresh();
})();

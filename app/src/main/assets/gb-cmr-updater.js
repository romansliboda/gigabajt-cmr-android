(()=>{
'use strict';
const api='https://api.github.com/repos/romansliboda/gigabajt-cmr-android/releases/latest';
const btn=document.getElementById('gbcmr-update-badge');
const label=document.getElementById('gbcmr-version-label');
const status=document.getElementById('gbcmr-update-status');
const news=document.getElementById('gbcmr-update-news');
const track=document.getElementById('gbcmr-update-track');
const bar=document.getElementById('gbcmr-update-fill');
if(!btn)return;

const bridge=window.GBUpdater;
const installed=bridge&&typeof bridge.getVersionName==='function'
 ?bridge.getVersionName():'13.0';
label.textContent='Wersja '+installed;
let release=null,asset=null,checking=false,downloading=false;

function ver(v){
 const m=String(v||'').match(/^v?(\d+)\.(\d+)(?:\.(\d+))?$/);
 return m?[+m[1],+m[2],+(m[3]||0)]:null;
}
function newer(a,b){
 if(!a||!b)return false;
 for(let i=0;i<3;i++){
  if(a[i]!==b[i])return a[i]>b[i];
 }
 return false;
}

async function check(silent){
 if(checking||downloading)return;
 checking=true;
 if(!silent)status.textContent='Sprawdzanie aktualizacji...';
 try{
  if(!bridge||typeof bridge.start!=='function')
   throw Error('Dostepne tylko w Androidzie');
  const r=await fetch(api,{cache:'no-store'});
  if(r.status===404){
   release=null;asset=null;
   btn.classList.remove('update-ready');
   if(!silent)status.textContent='Brak nowej aktualizacji';
   return;
  }
  if(!r.ok)throw Error('HTTP '+r.status);
  const v=await r.json();
  if(!newer(ver(v.tag_name),ver(installed))){
   release=null;asset=null;
   btn.classList.remove('update-ready');
   if(!silent)status.textContent='Masz aktualna wersje';
   return;
  }
  const apk=(v.assets||[]).find(x=>
   /\.apk$/i.test(x.name||'') &&
   String(x.browser_download_url||'').startsWith(
    'https://github.com/romansliboda/gigabajt-cmr-android/releases/download/')
  );
  if(!apk)throw Error('Brak APK w wydaniu');
  release=v;asset=apk;
  btn.classList.add('update-ready');
  status.textContent='Dostepna aktualizacja '+v.tag_name;
 }catch(e){
  if(!silent)status.textContent='Blad: '+e.message;
 }finally{checking=false;}
}

window.gbCMRUpdateClick=async()=>{
 if(downloading)return;
 if(!asset){await check(false);return;}
 downloading=true;
 btn.disabled=true;
 track.hidden=false;
 bar.style.width='0%';
 news.textContent='Co nowego: '+
  String(release.body||'Nowa wersja')
    .replace(/[#*`]/g,'').trim().slice(0,350);
 status.textContent='Pobieranie: 0%';
 try{
  bridge.start(asset.browser_download_url,release.tag_name);
 }catch(e){
  status.textContent='Blad: '+e.message;
  downloading=false;btn.disabled=false;
 }
};

window.addEventListener('gb-updater-native',e=>{
 const d=e.detail||{};
 if(d.state==='downloading'){
  const p=Number(d.percent);
  if(Number.isFinite(p)&&p>=0){
   bar.style.width=Math.min(100,p)+'%';
   status.textContent='Pobieranie: '+p+'%';
  }else status.textContent='Pobieranie...';
 }else if(d.state==='downloaded'){
  bar.style.width='100%';
  status.textContent='Pobrano 100%. Przygotowanie instalacji...';
 }else if(d.state==='installing'){
  status.textContent='Potwierdz instalacje w Androidzie';
 }else if(d.state==='error'){
  status.textContent=d.message||'Blad aktualizacji';
  downloading=false;btn.disabled=false;
 }
});
check(true);
})();
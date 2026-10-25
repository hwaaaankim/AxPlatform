// Shared task lifecycle: paint before CPU work, retain overlapping tasks, release in finally.
const tasks=new Map();
let layer,title,detail,bar,dismiss,clock,hideTimer,paintPromise,shownAt=0,nextId=0,guards=false,navigationEnd=null,previousBusy=null;
const make=(tag,className,text='')=>{const n=document.createElement(tag);n.className=className;n.textContent=text;return n;};
function ensure(){
 if(layer?.isConnected)return;
 layer=make('div','ax-loading-overlay');layer.id='ax-loading';layer.hidden=true;layer.setAttribute('popover','manual');layer.setAttribute('role','status');layer.setAttribute('aria-live','polite');layer.setAttribute('aria-atomic','true');
 const card=make('div','ax-loading-card'),icon=make('span','ax-loading-icon','↻');icon.setAttribute('aria-hidden','true');
 title=make('strong','ax-loading-title');title.id='ax-loading-title';detail=make('span','ax-loading-detail');
 bar=make('div','ax-loading-track');bar.setAttribute('role','progressbar');bar.setAttribute('aria-label','처리 중');bar.append(make('span','ax-loading-bar'));
 dismiss=make('button','ax-loading-dismiss','로딩 표시 닫기');dismiss.type='button';dismiss.hidden=true;dismiss.addEventListener('click',()=>{navigationEnd?.();navigationEnd=null;});
 card.append(icon,title,bar,detail,dismiss);layer.append(card);document.body.append(layer);
 if(!guards){guards=true;
  const shield=e=>{if(!e.isTrusted||!blocking()||layer?.contains(e.target))return;if(e.type==='keydown'&&(e.ctrlKey||e.metaKey)&&['r','l','t','w','c'].includes(e.key.toLowerCase()))return;e.preventDefault();e.stopImmediatePropagation();};
  for(const event of ['click','keydown','beforeinput','submit','dragstart','drop'])document.addEventListener(event,shield,true);
  for(const event of ['wheel','touchmove'])document.addEventListener(event,e=>{if(blocking())e.preventDefault();},{capture:true,passive:false});
 }
}
function blocking(){return [...tasks.values()].some(t=>t.blocking);}
function paint(){
 if(!paintPromise)paintPromise=new Promise(resolve=>{let done=false;const finish=()=>{if(done)return;done=true;clearTimeout(fallback);resolve();};const fallback=setTimeout(finish,64);const raf=globalThis.requestAnimationFrame||globalThis.window?.requestAnimationFrame?.bind(window);if(raf)raf(()=>raf(finish));else setTimeout(finish,0);});
 return paintPromise;
}
function update(){
 if(!tasks.size)return;
 const all=[...tasks.values()],modal=blocking(),task=[...all].reverse().find(t=>t.blocking)||all.at(-1),seconds=Math.floor((Date.now()-task.started)/1000);
 layer.classList.toggle('is-background',!modal);document.documentElement.classList.toggle('ax-is-loading',modal);title.textContent=task.label;
 detail.textContent=seconds>=8?`계속 처리 중입니다 · ${seconds}초 경과`:(modal?'완료되면 화면이 자동으로 열립니다.':'입력하면서 기다리실 수 있습니다.');
 dismiss.hidden=!task.navigation||seconds<8;
 const main=document.querySelector('main');if(main&&previousBusy===null)previousBusy={node:main,value:main.getAttribute('aria-busy')};if(main)main.setAttribute('aria-busy','true');
}
function conceal(){
 if(tasks.size)return;
 clearInterval(clock);clock=null;
 try{layer?.hidePopover?.();}catch{}
 if(layer)layer.hidden=true;
 document.documentElement.classList.remove('ax-is-loading');
 if(previousBusy){const {node,value}=previousBusy;if(value===null)node.removeAttribute('aria-busy');else node.setAttribute('aria-busy',value);previousBusy=null;}
 paintPromise=null;shownAt=0;
}
export function beginLoading(label='처리하고 있습니다…',{blocking:modal=true,navigation=false}={}){
 if(typeof document==='undefined'||!document.body)return Object.assign(()=>{},{update:()=>{},paint:()=>Promise.resolve()});
 ensure();clearTimeout(hideTimer);const id=++nextId;tasks.set(id,{label,blocking:modal,navigation,started:Date.now()});
 if(layer.hidden){shownAt=Date.now();layer.hidden=false;paintPromise=null;}
 // A manual popover stays above an already-open dialog without moving input focus.
 let promoted=false;try{if(layer.showPopover){if(!layer.matches(':popover-open'))layer.showPopover();promoted=true;}}catch{}
 if(!promoted){const dialog=[...document.querySelectorAll('dialog[open]')].at(-1);(dialog||document.body).append(layer);}
 update();if(!clock)clock=setInterval(update,1000);
 let ended=false;
 const end=()=>{if(ended)return;ended=true;tasks.delete(id);if(tasks.size)update();else{clearInterval(clock);clock=null;hideTimer=setTimeout(conceal,Math.max(32,160-(Date.now()-shownAt)));}};
 end.update=text=>{const t=tasks.get(id);if(t){t.label=text;update();}};end.paint=paint;
 return end;
}
export async function withLoading(label,action,options={}){
 const end=beginLoading(label,options);
 try{await end.paint();return await action(end);}finally{end();}
}
export function requestLabel(url,method='GET'){
 const path=String(url).split('?')[0];
 if(path.endsWith('/workbook-preview'))return '엑셀 시트와 수식을 읽고 있습니다…';
 if(path.endsWith('/workbook-calculation')||path.endsWith('/calculated-column'))return '수식을 계산하고 있습니다…';
 if(path.endsWith('/workbook-structure'))return '시트와 수식 참조를 정리하고 있습니다…';
 if(path.endsWith('/sheet/data'))return '저장 데이터를 조회·정렬하고 있습니다…';
 if(path.includes('export')||path.endsWith('.xlsx'))return '다운로드 파일을 만들고 있습니다…';
 if(/\/assets(?:\/[^/]+\/replace)?$/.test(path)&&method==='POST')return '파일을 올리고 있습니다…';
 if(path.includes('/ai-runs')&&!['GET','HEAD'].includes(method))return 'AI 작업 요청을 처리하고 있습니다…';
 if(path.endsWith('/workbook')&&method==='PUT')return '전체 시트와 수식을 저장하고 있습니다…';
 return ['GET','HEAD'].includes(method)?'데이터를 불러오고 있습니다…':'변경사항을 처리하고 있습니다…';
}
export function initPageLoading(){
 if(document.documentElement.dataset.axPageLoading)return;document.documentElement.dataset.axPageLoading='ready';
 const start=()=>{navigationEnd?.();navigationEnd=beginLoading('화면을 이동하고 있습니다…',{navigation:true});};
 const stop=()=>{navigationEnd?.();navigationEnd=null;};
 document.addEventListener('click',e=>{
  const a=e.target.closest?.('a[href]');if(!a||e.button!==0||e.ctrlKey||e.metaKey||e.shiftKey||e.altKey||a.hasAttribute('download')||a.hasAttribute('data-no-loading')||(a.target&&a.target!=='_self'))return;
  const u=new URL(a.href,location.href);if(u.origin!==location.origin||!['http:','https:'].includes(u.protocol)||u.pathname.startsWith('/api/')||u.pathname===location.pathname&&u.search===location.search)return;
  queueMicrotask(()=>{if(!e.defaultPrevented)start();});
 });
 document.addEventListener('submit',e=>{const form=e.target;if(form.method==='dialog'||form.hasAttribute('data-no-loading')||(form.target&&form.target!=='_self'))return;queueMicrotask(()=>{if(!e.defaultPrevented)start();});});
 window.addEventListener('pageshow',stop);window.addEventListener('pagehide',stop);
 window.addEventListener('beforeunload',e=>queueMicrotask(()=>{if(e.defaultPrevented)stop();}));
}

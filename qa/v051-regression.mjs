// Local fixture tests: no production database or provider requests.
import {Window} from 'happy-dom';
import fs from 'node:fs';
import assert from 'node:assert/strict';
import {pathToFileURL} from 'node:url';
const mode=process.argv[2]||'ai',base=process.cwd(),entry=base+'/src/main/resources/static/',fixtures=base+'/target/ui-rendered/';
const fixture=mode==='admin'?'_admin_system.html':fs.readdirSync(fixtures).find(n=>n.includes('work_new')&&n.includes('EXCEL'));
const w=new Window({url:'http://localhost'+(mode==='admin'?'/admin/system':'/work/new?businessId=1&workflow=EXCEL'),settings:{disableJavaScriptFileLoading:true,disableCSSFileLoading:true,disableJavaScriptEvaluation:true}});
w.document.write(fs.readFileSync(fixtures+fixture,'utf8'));
for(const key of ['window','document','navigator','location','history','sessionStorage','localStorage','Event','CustomEvent','FormData','File','Element','HTMLElement','HTMLDialogElement','MutationObserver','CSS'])Object.defineProperty(globalThis,key,{value:key==='window'?w:w[key],configurable:true,writable:true});
w.HTMLElement.prototype.scrollIntoView=function(){};
for(const name of ['app','workspace','form-controls','operations','layout']){const style=document.createElement('style');style.textContent=fs.readFileSync(entry+'css/'+name+'.css','utf8');document.head.append(style);}
const checks=[];function check(name,fn){fn();checks.push(name);}
const tick=()=>new Promise(resolve=>setTimeout(resolve,10));
async function wait(fn){for(let i=0;i<100;i++){if(fn())return;await tick();}throw Error('DOM condition timeout');}
function response(data,status=200){return{ok:status<400,status,headers:new Headers({'content-type':'application/json'}),json:async()=>structuredClone(data)};}
const calls=[],diagnostic={stage:'INPUT_TOKENS',httpStatus:401,providerCode:'invalid_api_key',providerType:'invalid_request_error',requestId:'req-fixture-401',model:'gpt-6-astra',endpoint:'/v1/responses/input_tokens',generationRequests:0};
const failed={id:'failed',status:'FAILED',phase:'중단됨',message:'현재 키가 유효하지 않습니다.',errorCode:'AI_PROVIDER_INVALID_KEY',diagnostic,baseDraftVersion:0,baseSheetVersion:-1,inputTokens:0,outputTokens:0,model:'gpt-6-astra',createdAt:'2026-10-20T00:00:00Z'};
globalThis.fetch=async(url,options={})=>{calls.push({url,options});
 if(url==='/api/ai/settings')return response({available:true,administrator:true,limits:{maxAssets:3}});
 if(url==='/api/assets/selected')return response([]);
 if(url.endsWith('/ai-runs'))return response([failed,{...failed,id:'legacy',diagnostic:undefined,createdAt:'2026-10-19T00:00:00Z'}]);
 if(url==='/api/admin/ai/diagnostics')return response({ok:false,errorCode:failed.errorCode,diagnostic,message:failed.message,model:'gpt-6-astra',generationRequests:0});
 throw Error('Unexpected network request: '+url);
};w.fetch=fetch;
const caps=new Set(['workspace.ai.read','workspace.ai.run','workspace.asset.list','workspace.asset.download',...['read','update','schema.update','rows.create','rows.delete','cells.update','formula.update'].map(a=>'workspace.sheet.'+a)]);
const context={workflow:()=> 'EXCEL',caps:()=>caps,active:()=>true,id:()=> 'fixture',business:()=>1,account:()=>1,currentInput:()=>({assetIds:[]})};
if(mode==='ai'){
 const {createAiPanel}=await import(pathToFileURL(entry+'js/guided-ai.js'));
 const host=document.getElementById('guided-ai');const ai=createAiPanel(host,context);await ai.init();
 check('provider failure retains readable cause and request reference',()=>assert.ok(host.textContent.includes('invalid_api_key')&&host.textContent.includes('req-fixture-401')));
 check('zero generation is only shown with recorded evidence',()=>assert.ok(host.textContent.includes('AI 생성 요청 0건')&&!host.textContent.includes('토큰 사용량 미확인')));
 check('unsaved sheet is named without negative version',()=>assert.ok(host.textContent.includes('저장된 표 없음')&&!host.textContent.includes('표 버전 -1')));
 check('admin can navigate directly to connection check',()=>assert.equal(host.querySelector('.ai-result a').getAttribute('href'),'/admin/system'));
 check('form result and warning all fill parent without caps',()=>{for(const selector of ['.ai-request-form','.ai-result','.ai-result>.notice','.help-line','.ai-source-list']){const style=w.getComputedStyle(host.querySelector(selector));assert.equal(style.width,'100%',selector);assert.equal(style.maxWidth,'none',selector);}});
 const text=host.querySelector('textarea');text.value='내 입력 유지';
 const history=[...host.querySelectorAll('details')].find(d=>d.querySelector('summary')?.textContent==='이전 제안·작업 이어 보기');history.querySelectorAll('button')[1].click();
 check('legacy unknown usage is not relabeled zero',()=>assert.ok(host.textContent.includes('토큰 사용량 미확인')&&!host.textContent.includes('AI 생성 요청 0건')));
 check('reviewing failure does not discard request input',()=>assert.equal(text.value,'내 입력 유지'));
 check('history and diagnostic display sends no generation request',()=>assert.equal(calls.filter(c=>c.url.includes('/ai-runs')&&c.options.method==='POST').length,0));
 const {diagnosticDetails}=await import(pathToFileURL(entry+'js/ai-diagnostic.js'));
 check('diagnostic metadata is always text',()=>{const detail=diagnosticDetails({providerCode:'<img src=x onerror=alert(1)>'},'TEST');assert.equal(detail.querySelector('img'),null);assert.ok(detail.textContent.includes('<img'));});
 ai.destroy();
}else if(mode==='sheet'){
 const {createSheetEditor}=await import(pathToFileURL(entry+'js/sheet-editor.js'));
 const host=document.createElement('section');host.className='panel';document.querySelector('main').append(host);
 const editor=createSheetEditor(host,{...context,id:()=>''});
 editor.replaceWorking({columns:[{id:'total',label:'합계',type:'NUMBER',width:160,formula:'[qty]*2'},{id:'qty',label:'수량',type:'NUMBER',width:120}],rows:[['4','2']],rowIds:['r1']});
 const formula=host.querySelector('.sheet-properties input[data-formula-control]'),code=host.querySelector('.sheet-properties input[readonly]');
 check('formula and immutable ID each span full properties row',()=>{for(const input of [formula,code])assert.ok(input.closest('.field').classList.contains('field-span-full'));});
 check('help text belongs to corresponding field',()=>{for(const input of [formula,code,host.querySelector('input[type=number]')]){const help=document.getElementById(input.getAttribute('aria-describedby'));assert.ok(help);assert.equal(help.parentElement,input.parentElement);}});
 check('numeric reference inserts into live formula',()=>{[...host.querySelectorAll('button')].find(b=>b.textContent==='수량 넣기').click();assert.ok(editor.snapshot().columns[0].formula.endsWith('[qty]'));});
 check('shared properties container has no fixed width cap',()=>assert.equal(w.getComputedStyle(host.querySelector('.sheet-properties>div')).maxWidth,'none'));
 w.happyDOM.setWindowSize({width:390,height:844});
 // Refresh parsed style sheets after resizing; Happy DOM caches computed media styles.
 for(const style of document.querySelectorAll('style'))style.textContent+='\n';
 check('mobile properties collapse to one column',()=>assert.equal(w.getComputedStyle(host.querySelector('.sheet-properties>div')).gridTemplateColumns,'1fr'));
}else if(mode==='admin'){
 await import(pathToFileURL(entry+'js/admin-system.js'));
 check('opening system page does not call OpenAI automatically',()=>assert.equal(calls.length,0));
 document.getElementById('ai-connection-check').click();await wait(()=>document.getElementById('ai-connection-result').querySelector('details'));
 check('admin diagnostic posts with CSRF and no user payload',()=>{assert.equal(calls.length,1);assert.equal(calls[0].options.method,'POST');assert.equal(calls[0].options.body,'{}');assert.ok(calls[0].options.headers[document.querySelector('meta[name=_csrf_header]').content]);});
 check('admin failure shows reference and no generation',()=>{const text=document.getElementById('ai-connection-result').textContent;assert.ok(text.includes('req-fixture-401')&&text.includes('생성 요청 0건'));});
 check('check button prevents immediate duplicate request',()=>{document.getElementById('ai-connection-check').click();assert.equal(calls.length,1);assert.equal(document.getElementById('ai-connection-check').disabled,true);});
 window.dispatchEvent(new Event('pagehide'));
}else if(mode==='pages'){
 const rendered=process.argv[3];if(!rendered)throw Error('Pass LayoutRenderCheck output directory');
 const styles=['app','workspace','form-controls','operations','layout'].map(n=>fs.readFileSync(entry+'css/'+n+'.css','utf8')).join('\n');
 for(const file of fs.readdirSync(rendered).filter(n=>n.endsWith('.html'))){
  const page=new Window({settings:{disableJavaScriptFileLoading:true,disableCSSFileLoading:true,disableJavaScriptEvaluation:true}});
  page.document.write(fs.readFileSync(rendered+'/'+file,'utf8'));
  const style=page.document.createElement('style');style.textContent=styles;page.document.head.append(style);
  check('shared layout on '+file,()=>{
   const links=[...page.document.querySelectorAll('link[rel=stylesheet]')];
   assert.ok(links.at(-1)?.getAttribute('href').includes('/css/layout.css?v=0.6.0'));
   const main=page.document.querySelector('.app-frame+main');if(main)assert.equal(page.getComputedStyle(main).maxWidth,'none');
   for(const element of page.document.querySelectorAll('main.container .panel,main.container .notice,main.container .card'))assert.equal(page.getComputedStyle(element).width,'100%');
  });
  await page.happyDOM.abort();page.close();
 }
}else throw Error('Unknown mode');
console.log(JSON.stringify({mode,passed:checks.length,checks}));await w.happyDOM.abort();w.close();process.exit(0);

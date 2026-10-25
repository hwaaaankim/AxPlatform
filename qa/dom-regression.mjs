import {Window} from 'happy-dom';
import fs from 'node:fs';import assert from 'node:assert/strict';import {pathToFileURL} from 'node:url';
const base=process.cwd(),dir=base+'/target/ui-rendered/',page=process.argv[2]||'draft';
const files=fs.readdirSync(dir),fixture=page==='draft'?files.find(x=>x.includes('work_new')&&x.includes('EXCEL')):page==='dashboard'?'_dashboard.html':page==='admin'?'_admin.html':'_login.html';
const w=new Window({url:'http://localhost'+(page==='draft'?'/work/new?businessId=1&workflow=EXCEL':page==='dashboard'?'/dashboard':page==='admin'?'/admin':'/login'),settings:{disableJavaScriptFileLoading:true,disableCSSFileLoading:true,disableJavaScriptEvaluation:true}});
w.document.write(fs.readFileSync(dir+fixture,'utf8'));
for(const key of ['window','document','navigator','location','history','sessionStorage','localStorage','Event','CustomEvent','KeyboardEvent','MouseEvent','FormData','File','DataTransfer','HTMLElement','HTMLDialogElement','Element','MutationObserver','CSS'])Object.defineProperty(globalThis,key,{value:key==='window'?w:w[key],configurable:true,writable:true});
globalThis.innerWidth=1440;globalThis.innerHeight=1000;globalThis.requestAnimationFrame=cb=>setTimeout(cb,0);globalThis.cancelAnimationFrame=clearTimeout;globalThis.confirm=()=>true;globalThis.prompt=()=>null;w.confirm=globalThis.confirm;
w.HTMLElement.prototype.scrollIntoView=function(){};globalThis.EventSource=class{addEventListener(){}close(){}};
const caps=JSON.parse(fs.readFileSync(dir+'_api_workspaces_1_access.json')).capabilities,requests=[];
let record={id:'dom-test',version:0,status:'DRAFT',title:'엑셀 업무',answers:{},assetIds:[]},sheet={columns:[],rows:[],rowIds:[]},sheetVersion=-1,views=[];
function response(data,status=200){return{ok:status<400,status,headers:new Headers({'content-type':'application/json'}),json:async()=>structuredClone(data)};}
globalThis.fetch=async(url,options={})=>{url=String(url);const method=options.method||'GET';requests.push([method,url]);const input=typeof options.body==='string'?JSON.parse(options.body):options.body;
 if(url==='/api/me')return response({accountId:1});if(url.includes('/access'))return response({capabilities:caps,tier:'ADMIN'});
 if(url==='/api/ai/settings')return response({available:false,reason:'QA AI disabled'});if(url.includes('/ai-runs'))return response([]);
 if(url.startsWith('/api/assets'))return response([]);
 if(url.startsWith('/api/business-scopes'))return response([{id:1,name:'업무 공간',workspace_id:1,version:0}]);
 if(url.endsWith('/dashboard'))return response({widgets:['EXCEL','WORD','HWP','PPT','CRM','CALENDAR','APP'],version:0});
 if(url.startsWith('/api/drafts?'))return response([{id:'saved',title:'매출 기록',workflow:'EXCEL',status:'SAVED',version:1,updated_at:'2026-10-20T01:00:00'},{id:'word',title:'제안서 준비',workflow:'WORD',status:'DRAFT',version:1,updated_at:'2026-10-19T00:00:00'}]);
 if(url==='/api/drafts'&&method==='POST'){record.title=input.title;return response(record);}
 if(url.endsWith('/sheet/views')){if(method==='POST'){views.push({id:'view1',name:input.config.name,config:input.config,version:0});return response({id:'view1'});}return response(views);}
 if(url.endsWith('/sheet/analysis'))return response({sheetVersion,rowCount:sheet.rows.length,blankValues:0,unit:'건',points:[{label:'전체',value:String(sheet.rows.length),rows:sheet.rows.length,numericCount:0}],config:input});
 if(url.endsWith('/workbook')){sheet=input.workbook.sheets.find(s=>s.id===input.workbook.activeSheetId).data;sheetVersion++;record.version++;return response({sheet,workbook:input.workbook,draftVersion:record.version,version:sheetVersion,canUndo:sheetVersion>0});}
 if(url.endsWith('/sheet')){if(method==='PATCH'){sheet=input.sheet;sheetVersion++;record.version++;}return response({sheet,draftVersion:record.version,version:sheetVersion,canUndo:sheetVersion>0});}
 if(url.endsWith('/save')){record.version++;record.status='SAVED';return response(record);}
 if(url.startsWith('/api/drafts/')){if(method==='PATCH'){Object.assign(record,input);record.version++;}return response(record);}
 throw new Error('Unexpected QA request '+method+' '+url);
};w.fetch=globalThis.fetch;
const tick=()=>new Promise(r=>setTimeout(r,15));const waitFor=async check=>{for(let i=0;i<100;i++){if(check())return;await tick();}throw new Error('Condition timeout');};
function clickText(text,root=document){const node=[...root.querySelectorAll('button,a')].find(n=>n.textContent===text);assert.ok(node,'Missing '+text);assert.equal(node.disabled,false,'Disabled '+text);node.click();return node;}
const entry=base+'/src/main/resources/static/js/';
const checks=[];function check(name,action){action();checks.push(name);}
if(page==='draft'){
 await import(pathToFileURL(entry+'draft.js'));await waitFor(()=>document.getElementById('prepare-tools').children.length);
 check('first view contains only source choices',()=>assert.equal(document.querySelector('[data-stage-panel="edit"]').hidden,true));
 document.querySelector('[data-mode="direct"]').click();check('direct route opens preparation',()=>assert.equal(document.querySelector('[data-stage-panel="prepare"]').hidden,false));
 clickText('매출 기록');clickText('정리 내용 확인 →');clickText('확인하고 편집 →');
 check('only table tab visible',()=>{assert.equal(document.getElementById('sheet-section').hidden,false);assert.equal(document.getElementById('ai-section').hidden,true);assert.equal(document.querySelector('[data-stage-panel="prepare"]').hidden,true);});
 clickText('행 추가');const cells=[...document.querySelectorAll('input[data-cell]')];check('manual row is editable',()=>assert.equal(cells.length,5));
 for(const [i,value]of ['2026-10-20','거래처 A','상품', '2','3000'].entries()){cells[i].value=value;cells[i].dispatchEvent(new Event('input'));}
 clickText('현재 페이지 선택');check('sheet row selection count and clear preserve data',()=>{assert.ok(document.querySelector('.sheet-toolbar').textContent.includes('선택 1 / 1행'));});clickText('행 선택 해제');check('row clear disables deletion',()=>assert.equal([...document.querySelectorAll('button')].find(b=>b.textContent==='선택한 행 삭제').disabled,true));
 document.querySelector('[data-editor-tab="ai"]').click();check('AI tab preserves manual cells',()=>assert.equal(cells[1].value,'거래처 A'));
 document.querySelector('[data-editor-tab="table"]').click();clickText('저장하고 데이터 보기 →');
 await waitFor(()=>document.querySelector('[data-stage-panel="finish"]').hidden===false);
 check('save calls real client mutation routes and preserves input',()=>{assert.equal(sheet.rows[0][1],'거래처 A');assert.ok(requests.some(r=>r[0]==='PATCH'&&r[1].endsWith('/workbook')));assert.equal(record.status,'SAVED');});
 await waitFor(()=>document.querySelector('#analysis-group option'));
 document.querySelector('#analysis-panel form').dispatchEvent(new Event('submit',{cancelable:true,bubbles:true}));await waitFor(()=>document.querySelector('.bar-row')&&[...document.querySelectorAll('#analysis-panel button')].some(b=>b.textContent==='이 보기 저장'&&!b.disabled));
 clickText('이 보기 저장');await waitFor(()=>views.length);
 check('saved analysis request sent only after aggregate preview',()=>assert.equal(views[0].config.operation,'COUNT'));
 clickText('다시 편집');check('edit after save preserves data',()=>assert.equal(document.querySelectorAll('input[data-cell]')[1].value,'거래처 A'));
 // Explicitly exercise modal guide without invoking mutations.
 const {initTutorial}=await import(pathToFileURL(entry+'tutorial.js'));await initTutorial();document.querySelector('.tutorial-launch').click();await tick();
 check('tutorial opens modal',()=>assert.equal(document.querySelector('.tour-layer').open,true));
 clickText('다음',document.querySelector('.tour-layer'));await tick();check('tutorial progresses',()=>assert.ok(document.querySelector('.tour-bubble .eyebrow').textContent.includes('2 /')));
 clickText('다시 보지 않기',document.querySelector('.tour-layer'));check('tour closes and restores original editor',()=>{assert.equal(document.querySelector('.tour-layer'),null);assert.equal(document.querySelector('[data-stage-panel="edit"]').hidden,false);assert.ok([...Array(localStorage.length)].map((_,i)=>localStorage.key(i)).some(k=>localStorage.getItem(k)==='hidden'));});
 document.querySelector('.tutorial-launch').click();check('tutorial can reopen after opt out',()=>assert.ok(document.querySelector('.tour-layer')));clickText('나중에 보기',document.querySelector('.tour-layer'));
}else if(page==='dashboard'){
 await import(pathToFileURL(entry+'dashboard.js'));await waitFor(()=>document.querySelectorAll('.workflow-card').length===7);
 check('dashboard uses fetched counts',()=>assert.equal(document.getElementById('metric-total').textContent,'2'));
 const f=document.getElementById('draft-workflow');f.value='EXCEL';f.dispatchEvent(new Event('change'));check('workflow filter changes overview and list',()=>{assert.equal(document.querySelectorAll('.workflow-card').length,1);assert.equal(document.getElementById('metric-total').textContent,'1');assert.ok(document.getElementById('draft-list').textContent.includes('매출 기록'));});
 const search=document.getElementById('draft-search');search.value='없는 자료';search.dispatchEvent(new Event('input'));check('empty search state has reason',()=>assert.ok(document.getElementById('draft-list').textContent.includes('검색 결과가 없습니다')));
 check('filters preserved in URL',()=>assert.ok(location.search.includes('q=')));
}else if(page==='admin'){
 w.happyDOM.setWindowSize({width:375,height:667});
 await import(pathToFileURL(entry+'app-shell.js'));await tick();
 const menu=document.querySelector('[data-menu-toggle]'),backdrop=document.querySelector('.sidebar-backdrop');
 check('admin has exactly one header and brand',()=>{assert.equal(document.querySelectorAll('.topbar').length,1);assert.equal(document.querySelectorAll('.brand').length,1);});
 menu.click();
 check('mobile menu opens and focuses navigation',()=>{assert.ok(document.body.classList.contains('menu-open'));assert.equal(menu.getAttribute('aria-expanded'),'true');assert.ok(document.querySelector('.sidebar').contains(document.activeElement));});
 document.dispatchEvent(new KeyboardEvent('keydown',{key:'Escape'}));
 check('Escape closes menu and restores toggle focus',()=>{assert.equal(document.body.classList.contains('menu-open'),false);assert.equal(document.activeElement,menu);assert.equal(backdrop.hidden,true);});
 menu.click();backdrop.click();
 check('backdrop closes mobile menu',()=>assert.equal(menu.getAttribute('aria-expanded'),'false'));
 menu.click();w.happyDOM.setWindowSize({width:1440,height:900});await tick();
 check('desktop resize releases mobile scroll lock',()=>{assert.equal(document.body.classList.contains('menu-open'),false);assert.equal(backdrop.hidden,true);});
}else{
 await import(pathToFileURL(entry+'app-shell.js'));await tick();
 const input=document.getElementById('password'),toggle=document.querySelector('.password-toggle');assert.ok(toggle);toggle.click();check('password toggle',()=>assert.equal(input.type,'text'));toggle.click();check('password remasked',()=>assert.equal(input.type,'password'));
 check('single brand',()=>assert.equal(document.querySelectorAll('.brand').length,1));
}
fs.writeFileSync(base+'/target/dom-'+page+'.json',JSON.stringify({page,checks,requestCount:requests.length},null,2));console.log(JSON.stringify({page,passed:checks.length}));await w.happyDOM.abort();process.exit(0);

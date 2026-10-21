// Workbook interaction regression. All API responses are local fixtures; no provider calls.
import {Window} from 'happy-dom';
import assert from 'node:assert/strict';
import {pathToFileURL} from 'node:url';
const base=process.cwd()+'/src/main/resources/static/js/';
const w=new Window({url:'http://localhost/work/drafts/test',settings:{disableJavaScriptFileLoading:true,disableCSSFileLoading:true,disableJavaScriptEvaluation:true}});
w.document.write('<html><head><meta name="_csrf" content="fixture"><meta name="_csrf_header" content="X-CSRF-TOKEN"></head><body><main></main></body></html>');
for(const key of ['window','document','navigator','location','history','sessionStorage','localStorage','Event','CustomEvent','FormData','File','Element','HTMLElement','HTMLDialogElement','MutationObserver','CSS'])Object.defineProperty(globalThis,key,{value:key==='window'?w:w[key],configurable:true,writable:true});
w.HTMLElement.prototype.scrollIntoView=function(){};globalThis.confirm=()=>true;w.confirm=()=>true;
const checks=[];const check=(name,fn)=>{fn();checks.push(name);};const button=(host,text)=>[...host.querySelectorAll('button')].find(b=>b.textContent===text);
const tick=()=>new Promise(r=>setTimeout(r,5));async function wait(fn){for(let i=0;i<150;i++){if(fn())return;await tick();}throw Error('timeout');}
const copy=v=>structuredClone(v),calls=[];const data=v=>({columns:[{id:'code',label:'코드',type:'TEXT',unit:'',width:160}],rows:[[v]],rowIds:['r1']});
let saved={version:0,draftVersion:1,sheet:data('001'),workbook:{format:'AX_WORKBOOK_1',activeSheetId:'sheet1',sheets:[{id:'sheet1',name:'매출',data:data('001'),sourceAssetIds:[]}]},canUndo:false};
const assets=[1,2].map(i=>({id:`00000000-0000-0000-0000-00000000000${i}`,original_name:'사진'+i+'.png',media_type:'image/png',byte_size:200}));
let run=null;
const response=data=>({ok:true,status:200,headers:new Headers({'content-type':'application/json'}),json:async()=>copy(data)});
globalThis.fetch=async(url,options={})=>{const body=options.body?JSON.parse(options.body):null;calls.push({url,options,body});
 if(url==='/api/drafts/test/sheet')return response(saved);
 if(url==='/api/drafts/test/workbook'){const previous=copy(saved.workbook);saved={...saved,workbook:copy(body.workbook),version:saved.version+1,draftVersion:saved.draftVersion+1,canUndo:true,previous};saved.sheet=copy(saved.workbook.sheets.find(s=>s.id===saved.workbook.activeSheetId).data);return response(saved);}
 if(url==='/api/drafts/test/sheet/undo'){saved={...saved,workbook:saved.previous,version:saved.version+1,draftVersion:saved.draftVersion+1,canUndo:false};saved.sheet=saved.workbook.sheets.find(s=>s.id===saved.workbook.activeSheetId).data;return response(saved);}
 if(url==='/api/assets/selected')return response(assets);
 if(url==='/api/ai/settings')return response({available:true,limits:{maxAssets:3,maxAssetBytes:6291456}});
 if(url==='/api/drafts/test/ai-runs'&&options.method==='POST'){run={id:'batch-run',status:'READY',phase:'검토 완료',baseDraftVersion:saved.draftVersion,baseSheetVersion:saved.version,photoSheetIds:body.photoSheetIds,sheetResults:body.photoSheetIds.map((id,i)=>({sheetId:id,sheetName:'사진'+(i+1),assetId:assets[i].id,status:i?'FAILED':'READY',message:i?'사진 오른쪽이 잘렸습니다. 전체 표를 다시 촬영하세요.':undefined,proposal:i?undefined:{summary:'사진 표',confirmed:[],assumptions:[],questions:[],warnings:[],ready:true,sheet:data('PHOTO')}}))};return response(run);}
 if(url==='/api/drafts/test/ai-runs')return response(run?[run]:[]);
 if(url==='/api/ai-runs/batch-run/apply-sheets'){run={...run,status:'APPLIED'};return response(run);}
 if(url==='/api/drafts/test/sheet/views')return response([]);
 if(url==='/api/drafts/test/sheet/analysis')return response({sheetVersion:saved.version,rowCount:1,blankValues:0,unit:'건',points:[],config:body});
 throw Error('Unexpected fixture request '+url);
};w.fetch=fetch;
const caps=new Set(['read','update','schema.update','rows.create','rows.delete','cells.update','formula.update','analysis.read','analysis.manage'].map(a=>'workspace.sheet.'+a));['read','run','apply'].forEach(a=>caps.add('workspace.ai.'+a));caps.add('workspace.asset.list');caps.add('workspace.asset.download');
const context={workflow:()=> 'EXCEL',caps:()=>caps,active:()=>true,id:()=> 'test',business:()=>1,account:()=>7,draftVersion:()=>saved.draftVersion,ensureDraft:async()=>{},currentInput:()=>({assetIds:assets.map(a=>a.id),title:'검증',answers:{}})};
const {createSheetEditor}=await import(pathToFileURL(base+'sheet-editor.js'));const host=document.createElement('section');document.querySelector('main').append(host);const editor=createSheetEditor(host,context);await editor.load();
check('legacy-compatible saved sheet loads',()=>assert.equal(editor.snapshot().rows[0][0],'001'));
editor.replaceWorking(data('CHANGED'));const added=editor.addSheets([{name:'재고',data:data('002')}],{replaceEmpty:false});editor.selectSheet('sheet1');
check('switching tabs preserves unsaved cells',()=>assert.equal(editor.snapshot().rows[0][0],'CHANGED'));
editor.selectSheet(added[0]);button(host,'시트 복제').click();
check('duplicate has a distinct sheet ID and preserves values',()=>{assert.equal(editor.workbook().sheets.length,3);assert.equal(editor.snapshot().rows[0][0],'002');assert.equal(new Set(editor.workbook().sheets.map(s=>s.id)).size,3);});
button(host,'← 이동').click();const ids=editor.workbook().sheets.map(s=>s.id);check('reorder changes sheet order without moving values between IDs',()=>{assert.equal(ids[1],editor.activeSheetId());assert.equal(editor.workbook().sheets[0].data.rows[0][0],'CHANGED');});
await editor.save();check('save sends the complete workbook and correct optimistic version',()=>{const c=calls.find(c=>c.url.endsWith('/workbook'));assert.equal(c.body.workbook.sheets.length,3);assert.equal(c.body.version,0);});
button(host,'시트 삭제').click();check('deletion keeps other sheet contents',()=>{assert.equal(editor.workbook().sheets.length,2);assert.equal(editor.workbook().sheets[0].data.rows[0][0],'CHANGED');});
button(host,'전체 시트 변경 취소').click();check('discard restores all saved tabs',()=>assert.equal(editor.workbook().sheets.length,3));
// Import report excludes failed and hidden sheets by default, with no destructive replacement.
const {renderWorkbookImport}=await import(pathToFileURL(base+'workbook-import.js'));const importHost=document.createElement('div');document.body.append(importHost);
const source=(name,status='READY',visibility='visible')=>({sheetName:name,status,visibility,headers:['코드'],rows:[['0008']],warnings:[],issues:status==='BLOCKED'?[{code:'FORMULA_NO_CACHE',severity:'ERROR',message:'A2 계산값 없음',remedy:'Excel에서 계산 후 저장'}]:[]});
renderWorkbookImport(importHost,{sheets:[source('매출'),source('오류','BLOCKED'),source('숨김','READY','hidden')]},{editor,assetId:assets[0].id,done:()=>{}});
check('import lists every sheet, blocking only unreadable ones',()=>{assert.equal(importHost.querySelectorAll('.import-sheet').length,3);assert.equal(importHost.querySelectorAll('.import-sheet input:checked').length,1);assert.equal(importHost.querySelectorAll('.import-sheet input:disabled').length,1);assert.ok(importHost.textContent.includes('A2 계산값 없음'));});
[...importHost.children].find(n=>n.tagName==='LABEL').querySelector('input').checked=true;button(importHost,'선택한 시트를 프로젝트에 추가').click();check('import appends a uniquely named sheet and preserves leading zeros',()=>{assert.equal(editor.workbook().sheets.length,4);assert.equal(editor.snapshot().rows[0][0],'0008');assert.equal(editor.workbook().sheets.at(-1).name,'매출 (2)');});
await editor.save();
const photoIds=editor.addSheets(assets.map((a,i)=>({name:'사진'+(i+1),sourceAssetIds:[a.id]})));await editor.save();
const {createAiPanel}=await import(pathToFileURL(base+'guided-ai.js'));const aiHost=document.createElement('section');document.body.append(aiHost);
const ai=createAiPanel(aiHost,{...context,sheet:()=>editor,prepareAi:async()=>({draftVersion:saved.draftVersion,sheetVersion:saved.version}),hasLocalChanges:()=>editor.dirty(),versions:()=>({draftVersion:saved.draftVersion,sheetVersion:saved.version})});await ai.init();await ai.preparePhotoSheets(photoIds);
check('photo setup selects matching sheets and explains per-photo usage',()=>{assert.equal(aiHost.querySelectorAll('.ai-photo-targets input:checked').length,2);assert.ok(aiHost.textContent.includes('별도 AI 호출'));});
aiHost.querySelector('.ai-request-form > .check input').checked=true;aiHost.querySelector('form').dispatchEvent(new Event('submit',{bubbles:true,cancelable:true}));await wait(()=>aiHost.querySelector('.ai-photo-result'));
check('one batch request binds each photo to its sheet',()=>{const c=calls.find(c=>c.url.endsWith('/ai-runs')&&c.options.method==='POST');assert.deepEqual(c.body.photoSheetIds,photoIds);assert.deepEqual(c.body.assetIds,assets.map(a=>a.id));assert.equal(c.body.previousRunId,null);});
check('partial failure displays actionable reason and only ready sheet is applicable',()=>{assert.equal(aiHost.querySelectorAll('.ai-photo-result').length,2);assert.equal(aiHost.querySelectorAll('[data-batch-sheet]').length,1);assert.ok(aiHost.textContent.includes('전체 표를 다시 촬영'));});
aiHost.querySelector('.ai-result > .check input').checked=true;button(aiHost,'선택 시트 한 번에 적용').click();await wait(()=>calls.some(c=>c.url.endsWith('/apply-sheets')));
check('selected ready sheet applies once with versions and idempotency key',()=>{const c=calls.find(c=>c.url.endsWith('/apply-sheets'));assert.deepEqual(c.body.sheetIds,[photoIds[0]]);assert.equal(c.body.sheetVersion,saved.version);assert.ok(c.body.requestKey);});
await wait(()=>run.status==='APPLIED');ai.destroy();
const {createAnalysis}=await import(pathToFileURL(base+'sheet-analysis.js'));const analysisHost=document.createElement('section');document.body.append(analysisHost);const analysis=createAnalysis(analysisHost,{...context,dirty:()=>false,activeSheetId:()=>added[0]});await analysis.load();
check('analysis requests explicitly selected sheet',()=>{assert.equal(analysisHost.querySelector('#analysis-sheet').value,added[0]);const c=calls.filter(c=>c.url.endsWith('/sheet/analysis')).at(-1);assert.equal(c.body.sheetId,added[0]);});
console.log(JSON.stringify({passed:checks.length,checks},null,2));await w.happyDOM.abort();w.close();process.exit(0);

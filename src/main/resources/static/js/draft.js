import {replaceAsset,locallyMissing,localLabel} from './asset-replace.js?v=0.10.1';
import {chooseSource} from './source-chooser.js?v=0.10.1';
import {fileSize} from './file-picker.js?v=0.10.1';
import {createWorkflowFlow} from './workflow-flow.js?v=0.10.1';
import {
  request
} from './request.js?v=0.10.1';
import {
  el,button,message
} from './ui.js?v=0.10.1';
import {
  watch
} from './events.js?v=0.10.1';
import {
  editState
} from './edit-state.js?v=0.10.1';
import {
  createGuide,fields
} from './workflow-guide.js?v=0.10.1';
import {
  createAiPanel
} from './guided-ai.js?v=0.10.1';
import {
  createSheetEditor
} from './sheet-editor.js?v=0.10.1';
const root=document.getElementById('draft-root'),business=Number(root.dataset.businessId),workspace=Number(root.dataset.workspaceId),workflow=root.dataset.workflow,form=document.getElementById('draft-form'),status=document.getElementById('draft-status');
let id=root.dataset.draftId,record={
  version:0,status:'DRAFT',answers:{
  },assetIds:[]
},dirty=false,caps=new Set(),cache,account,stopEvents,remote=false,mutating=false,externalWrites=0,loading=false,inputRevision=0,mode='guided',sheet=null,ai=null,flow=null;
const can=action=>caps.has('workspace.draft.'+action),getInput=()=>({
  title:document.getElementById('draft-title').value,answers:guide.values(),assetIds:[...document.querySelectorAll('#draft-assets input:checked')].map(x=>x.value)
});
const guide=createGuide(document.getElementById('questions'),workflow,{
  onChange:changed,onSummary:()=>flow?.basicsNext()
});
const label=guide.guide.name;
const hasChanges=()=>dirty||!!sheet?.dirty();
const isBusy=()=>mutating||externalWrites>0||loading;
function summary(){
  const host=document.getElementById('draft-summary');
  host.replaceChildren();
  for(const [key,name]of fields){
    const section=el('div','','summary-entry');
    if(!document.getElementById('answer-'+key).value.trim())continue;section.append(button('✎ '+name+' 수정',()=>{flow?.go('basics');document.getElementById('flow-question-options').open=true;guide.open(key);},'button small quiet'),el('p',document.getElementById('answer-'+key).value||'미확인 · 나중에 작성할 수 있습니다.','muted'));
    host.append(section);
  }
}
function changed(){
  dirty=true;
  inputRevision++;
  summary();
  cache?.write({
    input:getInput(),version:record.version
  });
  message(status,'입력 중 · 현재 탭에 보관되며 서버 저장 전입니다.');
  ai?.notifyVersion();
}
function remoteNotice(){
  remote=true;
  document.getElementById('remote-notice').hidden=false;
}
function fail(error){
  message(status,error.status===409?'다른 화면의 변경으로 저장 기준이 달라졌습니다. 입력은 유지했습니다. 최신 내용을 확인하고 다시 저장하세요.':error.message,true);
  if(error.status===409)remoteNotice();
}
function controls(){
  const archived=record.status==='ARCHIVED',blocked=isBusy(),protectedIds=new Set(ai?.protectedSourceIds?.()||[]);
  root.querySelectorAll('#draft-form input,#draft-form textarea,#draft-assets input').forEach(x=>x.disabled=blocked||archived||!can('update')||x.closest('#draft-assets')&&protectedIds.has(x.value));
  root.querySelectorAll('#questions button,#draft-assets button').forEach(x=>x.disabled=blocked||archived||!can('update')||protectedIds.has(x.dataset.sourceAssetId)||x.dataset.clearSources==='true'&&![...root.querySelectorAll('#draft-assets input:checked')].some(cb=>!protectedIds.has(cb.value)));
  document.getElementById('draft-update').disabled=blocked||archived||(!id?!can('create')||!can('update'):!can('update'));
  document.getElementById('draft-save').disabled=blocked||archived||!can('save')||(!id&&(!can('create')||!can('update')));
  document.getElementById('draft-cancel').disabled=blocked||archived||!id||!can('cancel');
  document.getElementById('draft-clone').disabled=blocked||archived||!id||!can('clone')||!can('read')||!can('create');
  document.getElementById('draft-archive').disabled=blocked||!id||!can(archived?'restore':'archive');
  document.getElementById('draft-archive').textContent=archived?'복원':'보관';
  document.getElementById('draft-refresh').disabled=blocked;
  document.getElementById('draft-meta').textContent=`${label} · ${id?'업무 버전 '+record.version:'새 초안'} · ${archived?'보관됨':record.status==='SAVED'?'정리 내용 확정 저장됨':'작업 중'}`;
  sheet?.refreshControls();
  ai?.refreshControls();
  flow?.refresh();
}
async function capabilities(requestLoading){
  const value=await request(`/api/workspaces/${workspace}/access`,{loading:requestLoading});
  caps=new Set(value.capabilities);
  controls();
}
async function assetOptions(selected=[],requestLoading=true){
  const host=document.getElementById('draft-assets');
  // Keep the current selection intact until a list response has arrived.
  if(!caps.has('workspace.asset.list')){
    host.replaceChildren(el('p','첨부 자료 조회 권한이 없습니다. 기존 첨부 선택은 유지합니다.','muted'));
    selected.forEach(value=>{const cb=el('input');cb.type='checkbox';cb.value=value;cb.checked=true;cb.hidden=true;host.append(cb);});return;
  }
  const list=await request('/api/assets/selected',{method:'POST',loading:requestLoading,body:{businessId:business,projectId:id||null,ids:selected}}),seen=new Set(),checks=[];
  host.replaceChildren();const toolbar=el('div','','attachment-toolbar'),counter=el('span','','muted'),chips=el('div','','attachment-chips'),options=el('div','','attachment-options');
  const editable=()=>!isBusy()&&record.status!=='ARCHIVED'&&can('update');
  const protectedSource=value=>ai?.protectedSourceIds?.().includes(value);
  const all=button('내 자료에서 추가',async()=>{if(!editable())return;chooseSource({business,project:id,workflow,onChoose:async items=>{const ids=[...new Set([...getInput().assetIds,...items.map(a=>a.id)])];if(ids.length>20)throw new Error('작업 자료는 최대 20개입니다. 기존 연결을 해제하고 선택하세요.');await assetOptions(ids);changed();}});},'button small');
  const clear=button('완료된 첨부 해제',()=>{if(!editable())return;checks.forEach(x=>{if(!protectedSource(x.cb.value))x.cb.checked=false;});refresh();changed();},'button small quiet');
  clear.dataset.clearSources='true';
  const reload=button('등록 목록 새로고침',()=>assetOptions(getInput().assetIds).then(controls).catch(fail),'button small');
  function refresh(){const chosen=checks.filter(x=>x.cb.checked);counter.textContent=`이 작업의 자료 ${chosen.length} / 20개`;chips.replaceChildren();for(const {cb,name,asset} of chosen){const chip=el('span','','attachment-chip'),remove=button('×',()=>{if(!editable()||protectedSource(cb.value))return;cb.checked=false;refresh();changed();},'file-remove');remove.dataset.sourceAssetId=cb.value;remove.setAttribute('aria-label',name+' 업무 첨부 해제');remove.disabled=!editable()||protectedSource(cb.value);if(protectedSource(cb.value))remove.title='연속 작업에서 사용 중입니다. 작업 완료 또는 이어 처리 종료 후 연결을 해제하세요.';chip.append(el('span',name));if(asset&&locallyMissing(asset)){chip.append(el('small',localLabel(asset),'missing-file-badge'));if(caps.has('workspace.asset.upload')&&caps.has('workspace.asset.archive')){const replace=button('파일 교체',()=>replaceAsset(asset,{onReplaced:async()=>{await assetOptions(getInput().assetIds);await ai?.refreshAssets();controls();}}),'button small');replace.disabled=!editable();chip.append(replace);}}chip.append(remove);chips.append(chip);}all.disabled=!editable();clear.disabled=!editable()||!chosen.some(x=>!protectedSource(x.cb.value));}
  function item(id,name,archived=false,bytes=null,asset=null){const label=el('label','','check'),cb=el('input');cb.type='checkbox';cb.value=id;cb.checked=selected.includes(id);cb.addEventListener('change',()=>{if(!cb.checked&&protectedSource(cb.value)){cb.checked=true;message(status,'연속 작업에서 사용하는 원본입니다. 작업 완료 또는 이어 처리 종료 후 연결을 해제하세요.',true);}else if(checks.filter(x=>x.cb.checked).length>20){cb.checked=false;message(status,'업무 첨부는 최대 20개입니다. 기존 첨부를 해제하고 선택하세요.',true);}else changed();refresh();});label.append(cb,el('span',name+(bytes!==null?' · '+fileSize(Number(bytes)):'')+(archived?' · 삭제됨 · 복원하거나 첨부를 해제하세요.':'')));checks.push({cb,name,archived,asset});label.hidden=true;options.append(label);}
  for(const a of list){if(a.archived_at&&!selected.includes(a.id))continue;seen.add(a.id);item(a.id,a.original_name,!!a.archived_at,a.byte_size,a);}
  for(const value of selected.filter(x=>!seen.has(x)))item(value,'조회되지 않는 기존 첨부 · 목록에서 확인하세요.');
  toolbar.append(counter,all,clear,reload);host.append(toolbar,chips,options,el('small','×와 완료된 첨부 해제는 이 업무의 첨부 선택만 바꿉니다. 이어 처리 중인 원본은 작업을 완료하거나 종료하면 해제할 수 있습니다. 저장 시 반영되며, 원본 파일 삭제·복원은 내 자료에서 처리합니다.','muted'));
  if(!checks.length)options.append(el('p','등록된 자료가 없습니다. 위에서 자료를 등록한 뒤 첨부하세요.','muted'));refresh();
}
function openAttachmentManager(){
  if(isBusy()||!can('update'))return message(status,'처리 중인 요청이 끝난 뒤 원본 연결을 관리하세요.',true);
  const protectedIds=new Set(ai?.protectedSourceIds?.()||[]),current=[...document.querySelectorAll('#draft-assets input:checked')];
  const dialog=el('dialog','','workbook-dialog'),form=el('form'),info=el('p','','help-line'),list=el('div','','attachment-options'),checks=[];
  dialog.style.maxWidth='min(720px, calc(100vw - 32px))';dialog.style.maxHeight='85vh';dialog.style.overflow='auto';dialog.setAttribute('aria-label','작업 원본 연결 관리');
  dialog.append(el('h2','작업 원본 연결 관리'),el('p','체크한 원본을 이 작업의 현재 첨부로 유지합니다. 연결을 해제해도 이미 만든 표와 보관 원본 파일은 삭제되지 않습니다. 다음 사진을 등록할 자리가 필요하면 처리가 끝난 원본의 연결을 해제하세요.','muted'));
  for(const original of current){const label=el('label','','check'),cb=el('input');cb.type='checkbox';cb.value=original.value;cb.checked=true;cb.disabled=protectedIds.has(cb.value);const name=original.closest('label')?.querySelector('span')?.textContent||original.value;label.append(cb,el('span',name+(cb.disabled?' · 연속 작업에서 사용 중':'')));checks.push(cb);list.append(label);}
  if(!current.length)list.append(el('p','현재 연결된 원본이 없습니다. 자료 준비에서 다음 사진을 등록하세요.','muted'));
  const clear=button('연속 작업에서 사용하지 않는 원본 해제',()=>checks.filter(cb=>!cb.disabled).forEach(cb=>cb.checked=false),'button quiet'),close=button('닫기',()=>dialog.close(),'button'),submit=el('button','연결 상태 저장','button primary');submit.type='submit';submit.disabled=!current.length;const actions=el('div','','actions');actions.append(clear,close,submit);form.append(list,info,actions);dialog.append(form);
  form.addEventListener('submit',async event=>{event.preventDefault();submit.disabled=true;close.disabled=true;clear.disabled=true;checks.forEach(cb=>cb.disabled=true);try{await mutate(async()=>{await assetOptions(checks.filter(cb=>cb.checked).map(cb=>cb.value));changed();await ensureDraft();await ai?.refreshAssets();});dialog.close();message(status,'원본 연결을 저장했습니다. 표 데이터와 보관 파일은 유지됩니다. 남은 사진을 등록할 수 있습니다.');}catch(error){message(info,error.message,true);submit.disabled=false;close.disabled=false;clear.disabled=false;checks.forEach(cb=>cb.disabled=protectedIds.has(cb.value));}});
  dialog.addEventListener('close',()=>dialog.remove(),{once:true});document.body.append(dialog);dialog.showModal();
}
async function fill(input,requestLoading=true){
  document.getElementById('draft-title').value=input.title||'';
  for(const[key]of fields)document.getElementById('answer-'+key).value=input.answers?.[key]||'';
  await assetOptions(input.assetIds||[],requestLoading);
  summary();
  guide.resume();
  if(mode==='direct')guide.setMode('direct');
  controls();
}
async function reload({
  forceSheet=false,background=false
}
={
}){
  if(loading)return;
  loading=true;
  controls();
  try{
    await capabilities(!background);
    if(id){
      record=await request('/api/drafts/'+encodeURIComponent(id),{loading:!background});
      await fill(record,!background);
    }
    else await fill({
      title:'',answers:{
      },assetIds:[]
    });
    dirty=false;
    remote=false;
    document.getElementById('remote-notice').hidden=true;
    if(sheet)await sheet.load({
      force:forceSheet,loading:!background
    });
  }
  finally{
    loading=false;
    controls();
  }
}
async function update(){
  if(!can('update'))throw new Error('업무 입력 수정 권한이 없습니다.');
  const input=getInput();
  if(!input.title.trim())throw new Error('초안 이름을 입력해 주세요.');
  if(!id){
    const created=await request('/api/drafts',{
      method:'POST',body:{
        businessId:business,workflow,title:input.title
      }
    });
    id=created.id;
    root.dataset.draftId=id;
    record.version=created.version;
    cache?.clear();
    cache=editState(account,business,id);
    history.replaceState({
    },'', '/work/drafts/'+encodeURIComponent(id)+location.search);
  }
  record=await request('/api/drafts/'+encodeURIComponent(id),{
    method:'PATCH',body:{
      ...input,version:record.version
    }
  });
  dirty=false;
  remote=false;
  cache?.clear();
  document.getElementById('remote-notice').hidden=true;
  controls();
  message(status,'업무 입력을 서버 초안으로 저장했습니다.');
  await ai?.attachDraft();
  return record;
}
async function mutate(action){
  if(isBusy())throw new Error('저장·적용 요청이 진행 중입니다.');
  mutating=true;
  controls();
  try{
    return await action();
  }
  finally{
    mutating=false;
    controls();
  }
}
function externalBusy(value){
  externalWrites=Math.max(0,externalWrites+(value?1:-1));
  controls();
}
async function ensureDraft(){
  if(dirty||!id)await update();
}
async function readVersionAfterWrite(){
  const next=await request('/api/drafts/'+encodeURIComponent(id));
  record=next;
  remote=false;
  document.getElementById('remote-notice').hidden=true;
  controls();
  ai?.notifyVersion();
}
async function prepareAi(assetIds=[]){
  if(assetIds.some(value=>!getInput().assetIds.includes(value)))throw new Error('AI에게 보낼 원본을 먼저 업무 첨부에 연결하세요. 선택은 유지했습니다.');
  if(remote)throw new Error('다른 화면에서 바뀐 내용이 있습니다. 현재 입력을 확인한 뒤 최신 상태를 불러오세요.');
  if(sheet?.dirty()){
    if(!confirm('AI는 서버에 저장된 표를 읽습니다. 현재 표의 미적용 변경을 먼저 저장한 뒤 검토를 요청할까요?'))throw new Error('AI 요청을 보내지 않았습니다. 현재 표 입력은 유지했습니다.');
    await sheet.save();
  }
  else await ensureDraft();
  if(sheet&&sheet.version()<0)await sheet.load();
  return {
    draftVersion:record.version,sheetVersion:sheet?sheet.version():-1
  };
}
async function afterAiApply({keepAi=false}={}){
  if(hasChanges()){
    remoteNotice();
    message(status,'AI 적용은 완료되었지만 새로 입력한 수동 내용이 있어 화면에 덮어쓰지 않았습니다. 현재 입력을 먼저 확인하세요.');
    return;
  }
  await reload({
    forceSheet:true
  });
  cache?.clear();
  message(status,'선택한 AI 제안만 서버에 적용했습니다. 실제 내용을 확인하세요.');
  flow?.afterAi({keepAi});
}
function configureModules(){
  const common={
    id:()=>id,caps:()=>caps,active:()=>record.status!=='ARCHIVED'&&!isBusy(),draftVersion:()=>record.version,account:()=>account,business:()=>business,workflow:()=>workflow,setBusy:externalBusy,onConflict:remoteNotice
  };
  if(workflow==='EXCEL'){
    document.getElementById('sheet-section').hidden=false;
    sheet=createSheetEditor(document.getElementById('sheet-editor'),{
      ...common,ensureDraft,onSaved:readVersionAfterWrite,onAddSheet:mode=>mode==='guided'?flow?.showAi():flow?.select(mode),onSheetChange:()=>{ai?.refreshSheetTargets();flow?.refresh();},onLocalChange:()=>{
        ai?.notifyVersion();
      }
    });
  }
  ai=createAiPanel(document.getElementById('guided-ai'),{
    ...common,prepareAi,sheet:()=>sheet,linkAsset:value=>{
      if(!can('update'))throw new Error('새 원본을 업무에 연결하려면 업무 수정 권한이 필요합니다.');
      const option=[...document.querySelectorAll('#draft-assets input')].find(n=>n.value===value);
      if(!option)throw new Error('원본을 업무 첨부에서 확인한 뒤 다시 선택하세요.');
      if(!option.checked){
        option.checked=true;
        changed();
      }
    },currentInput:getInput,hasLocalChanges:hasChanges,versions:()=>({
      draftVersion:record.version,sheetVersion:sheet?sheet.version():-1
    }),onApplied:afterAiApply
  });
}
const modeHost=document.getElementById('workflow-modes');
document.getElementById('workflow-intro').textContent=guide.guide.intro;
const modes=workflow==='EXCEL'?[['photo','사진에서 시작','전표·표 사진을 AI와 함께 확인합니다.','▧'],['guided','대화로 만들기','필요한 업무를 선택지와 짧은 대화로 정리합니다.','✧'],['upload','엑셀·CSV 가져오기','기존 파일의 열과 데이터를 확인해 연결합니다.','↥'],['direct','직접 표 만들기','빈 표나 기본 열 구성으로 바로 편집합니다.','⊞']]:[['guided','안내에 따라 정리','선택지로 목적과 필요한 조건부터 정합니다.','✧'],['direct','직접 작성·수정','요약 항목을 직접 고르고 구체적으로 작성합니다.','↗']];
modes.forEach(([value,name,copy,symbol])=>{const b=button('',()=>flow?.select(value),'mode-card');b.dataset.mode=value;b.setAttribute('aria-pressed','false');b.append(el('span',symbol,'mode-symbol'),el('strong',name),el('span',copy));modeHost.append(b);});
document.getElementById('workflow-scope').textContent=workflow==='EXCEL'?'지원: 프로젝트당 20시트, 시트별 100열·50,000행·100만 셀 편집, 사진별 시트·대화 AI 제안, 다중 시트 XLSX·CSV 가져오기, 원본 Excel 수식·계산 열, 데이터 센터·AND 필터·다중 집계·저장한 보기, 데이터/빈 양식 XLSX. PDF·XLS·임의 Excel 함수·다단계 복구는 지원 범위 밖입니다.':'현재는 요구사항 정리·첨부·AI 제안·저장·재사용을 지원합니다. 실제 문서 출력, CRM 운영, 일정·발송, 실행 프로그램 생성 엔진은 아직 준비 중입니다.';
for(const[key,name]of[['title','초안 이름'],...fields,['assets','첨부 선택']]){
  const l=el('label','','check'),cb=el('input');
  cb.type='checkbox';
  cb.value=key;
  l.append(cb,el('span',name));
  document.getElementById('cancel-fields').append(l);
}
document.getElementById('draft-title').addEventListener('input',changed);
form.addEventListener('submit',event=>{
  event.preventDefault();
  mutate(update).catch(fail);
});
document.getElementById('draft-save').addEventListener('click',()=>mutate(async()=>{
  await ensureDraft();
  record=await request(`/api/drafts/${encodeURIComponent(id)}/save`,{
    method:'POST',body:{
      version:record.version
    }
  });
  await fill(record);
  dirty=false;
  cache?.clear();
  message(status,'업무 답변의 확정 저장을 마쳤습니다. 수정 일부 취소의 기준이 됩니다. 표 저장은 별도입니다.');
}).catch(fail));
document.getElementById('draft-cancel').addEventListener('click',()=>mutate(async()=>{
  const selected=[...document.querySelectorAll('#cancel-fields input:checked')].map(n=>n.value);
  if(!selected.length)throw new Error('취소할 항목을 선택해 주세요.');
  if(!confirm('선택 항목만 마지막 확정 저장 값으로 되돌릴까요? 나머지 답변과 표 입력은 유지합니다.'))return;
  if(dirty)await update();
  record=await request(`/api/drafts/${encodeURIComponent(id)}/cancel`,{
    method:'POST',body:{
      version:record.version,fields:selected
    }
  });
  await fill(record);
  dirty=false;
  cache?.clear();
  message(status,'선택한 항목만 되돌렸습니다. 다른 답변과 표 입력은 유지했습니다.');
}).catch(fail));
document.getElementById('draft-clone').addEventListener('click',()=>mutate(async()=>{
  if(hasChanges()&&!confirm('서버에 저장된 내용을 재사용합니다. 미저장 답변·표는 복사되지 않으며 현재 탭에 남습니다. 계속할까요?'))return;
  const title=prompt('새 업무 이름 · 엑셀은 표 구조만 복사하고 행 데이터는 제외합니다.',record.title+' 재사용');
  if(!title?.trim())return;
  const result=await request(`/api/drafts/${encodeURIComponent(id)}/clone`,{
    method:'POST',body:{
      title
    }
  });
  if(!dirty)cache?.clear();
  location.assign('/work/drafts/'+encodeURIComponent(result.id));
}).catch(fail));
document.getElementById('draft-archive').addEventListener('click',()=>mutate(async()=>{
  const restore=record.status==='ARCHIVED';
  if(!confirm(restore?'이 업무를 복원할까요?':'서버 업무를 보관할까요? 저장 전 입력은 현재 탭에 유지합니다.'))return;
  record=await request(`/api/drafts/${encodeURIComponent(id)}/${restore?'restore':'archive'}`,{
    method:'POST',body:{
      version:record.version
    }
  });
  if(!dirty)await fill(record);
  message(status,restore?'업무를 복원했습니다.':'업무를 보관했습니다.');
}).catch(fail));
document.getElementById('draft-refresh').addEventListener('click',async()=>{
  if(hasChanges()&&!confirm('현재 탭의 저장 전 업무 답변과 표 변경을 버리고 서버의 최신 내용을 불러올까요?'))return;
  try{
    await reload({
      forceSheet:true
    });
    cache?.clear();
    sheet?.clearDraft();
    await ai?.refreshAssets();
    message(status,'서버의 최신 내용을 불러왔습니다.');
  }
  catch(error){
    fail(error);
  }
});
async function onPolicy(){
  try{
    await capabilities(false);
    if(!can('read')&&id){
      remoteNotice();
      message(status,'업무 읽기 권한이 변경되었습니다. 현재 입력을 유지하고 다음 요청은 서버에서 권한을 다시 확인합니다.',true);
    }
  }
  catch(error){
    caps.clear();
    controls();
    fail(error);
  }
}
controls();
request('/api/me').then(async me=>{
  account=me.accountId;
  cache=editState(account,business,id||'new-'+workflow);
  configureModules();
  await reload();
  flow=createWorkflowFlow(root,{
    id:()=>id,ensureDraft:async()=>{if(!id&&!document.getElementById('draft-title').value.trim())document.getElementById('draft-title').value=workflow==='EXCEL'?'새 엑셀 작업':'새 업무 작업';await ensureDraft();return id;},sheet:()=>sheet,assetIds:()=>getInput().assetIds,photoTargets:(ids,options)=>ai?.preparePhotoSheets(ids,options).catch(error=>message(status,error.message,true)),caps:()=>caps,dirty:hasChanges,active:()=>record.status!=='ARCHIVED'&&!isBusy(),setBusy:externalBusy,
    manageAssets:openAttachmentManager,resumeGuide:()=>guide.resume(),setAiRequest:text=>ai?.setRequest(text,sheet?.activeSheetId()),setMode:value=>{mode=value;guide.setMode(value);ai?.setMode?.(value);},
    async refreshAssets(assetIds){const selected=getInput().assetIds;for(const assetId of Array.isArray(assetIds)?assetIds:[assetIds])if(assetId&&!selected.includes(assetId)&&selected.length<20)selected.push(assetId);await assetOptions(selected);changed();await ai.refreshAssets();if(selected.length===20)message(status,'업무 첨부 한도 20개입니다. 추가 등록 원본은 내 자료에 보관되며 필요할 때 바꿔 선택하세요.');},
    async refreshSheet(){await readVersionAfterWrite();await sheet.load({force:true});ai?.refreshSheetTargets();},askAi:(text,sheetId)=>{sheet?.selectSheet(sheetId);flow?.showAi();ai?.setRequest(text,sheetId);},
    async saveAll(){if(workflow==='EXCEL'&&!sheet?.hasColumns())throw new Error('먼저 열을 한 개 이상 추가해 주세요. 데이터가 없는 빈 양식도 저장할 수 있습니다.');if(sheet?.dirty())await sheet.save();await mutate(async()=>{await ensureDraft();record=await request(`/api/drafts/${encodeURIComponent(id)}/save`,{method:'POST',body:{version:record.version}});dirty=false;cache?.clear();controls();});}
  });
  flow.ready();
  const stored=cache.read();
  if(stored){
    if(stored.version!==record.version){
      remoteNotice();
      message(status,'브라우저 입력과 서버 버전이 달라 자동 복구하지 않았습니다.');
    }
    else if(confirm('이 탭의 저장 전 업무 입력을 복구할까요?')){
      await fill(stored.input);
      dirty=true;
      flow.go('basics',{focus:true});message(status,'저장 전 입력을 복구했습니다. 서버 저장은 아직입니다.');
    }
    else cache.clear();
  }
  await ai.init();
  if(caps.has('workspace.events.read'))stopEvents=watch(business,{
    onStatus:text=>document.getElementById('event-status').textContent=text,onPolicy,onResync:()=>{
      if(hasChanges()||isBusy()||ai?.isPending())remoteNotice();
      else if(id)reload({background:true}).catch(fail);
    },onChange:event=>{
      if(String(event.object_id)===id&&Number(event.object_version)>record.version){
        if(hasChanges()||isBusy()||ai?.isPending())remoteNotice();
        else reload({background:true}).catch(fail);
      }
      else if(event.event_type==='tier.changed'||event.event_type.startsWith('scope.'))onPolicy();
    }
  });
  controls();
}).catch(fail);
window.addEventListener('beforeunload',event=>{
  if(hasChanges()||isBusy()||flow?.hasPendingFiles()){
    if(dirty)cache?.write({
      input:getInput(),version:record.version
    });
    event.preventDefault();
    event.returnValue='';
  }
});
window.addEventListener('pagehide',()=>{
  stopEvents?.();
  ai?.destroy();
});

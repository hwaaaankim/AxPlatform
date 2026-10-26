import {createContinuationOptions,renderContinuationProgress,continuationSummary,protectedContinuationSources} from './ai-continuation.js?v=0.10.1';
import {workbookCapacity,continuationSheet} from './continuation-capacity.js?v=0.10.1';
import {editExamples,describeOperation,conversationItems} from './ai-edit-presentation.js?v=0.10.1';
import {locallyMissing,localLabel} from './asset-replace.js?v=0.10.1';
import {fileSize} from './file-picker.js?v=0.10.1';
import {diagnosticDetails} from './ai-diagnostic.js?v=0.10.1';
import {
  request
} from './request.js?v=0.10.1';
import {
  el,button,message,whenText
} from './ui.js?v=0.10.1';
import {
  makePreviewTable
} from './sheet-editor.js?v=0.10.1';
import {
  fields
} from './workflow-guide.js?v=0.10.1';
const copy=value=>JSON.parse(JSON.stringify(value));
const activeStatuses=new Set(['QUEUED','RUNNING']);
const labels={
  QUEUED:'서버 작업 대기',RUNNING:'AI 검토 중',READY:'변경 제안 확인 필요',APPLIED:'선택 항목 적용됨',FAILED:'작업 실패',CANCELLED:'취소됨'
};
export function createAiPanel(host,context) {
  let settings=null,selectedRun=null,proposal=null,pollTimer=null,pollAbort=null,disposed=false,pending=false,lastRendered='',requestDraft=null,applyDraft=null,assets=[],answers=new Map(),historyRuns=[],startMode='direct',conversationSignature='',combinedOrder=[],continueDraft=null;
  const rolloverTargets=new Map();
  const connection=el('p','','notice'),intro=el('p','말로 요청하거나 직접 편집할 수 있습니다. AI의 변경 범위를 확인한 뒤 적용하고, 이어서 다시 요청하세요.','muted'),form=el('form','','ai-request-form'),promptLabel=el('label','다음에 무엇을 도와드릴까요?'),prompt=el('textarea'),examples=el('div','','guide-choices ai-edit-examples'),sources=el('fieldset','','ai-source-list'),sourceLegend=el('legend','이번 AI 검토에 보낼 원본 자료'),sourceList=el('div'),sendConfirm=el('label','','check'),consent=el('input'),start=el('button','요청 보내기','button primary'),runStatus=el('p','','help-line'),runMeta=el('p','','muted'),cancel=button('진행 중 작업 취소',()=>cancelRun().catch(fail),'button danger'),historyBox=el('details'),historyTitle=el('summary','이전 제안·작업 이어 보기'),historyList=el('div'),result=el('div','','ai-result');
  prompt.id='ai-prompt';
  prompt.maxLength=6000;
  prompt.rows=3;
  prompt.placeholder=context.workflow()==='EXCEL'?'예: 메모 열을 추가해 주세요 / A2 셀을 비워 주세요 / 수량 × 단가 계산열을 만들어 주세요.':'예: 지금 답변에서 빠진 조건을 확인하고 업무 요약을 더 구체적으로 정리해 주세요.';
  promptLabel.htmlFor=prompt.id;
  start.type='submit';
  const combinedOrderBox=el('div','','ai-combined-order');combinedOrderBox.hidden=true;
  const sourceRules=el('p','','muted'),sourceCount=el('span','','muted'),sourceTools=el('div','','actions');sourceCount.setAttribute('role','status');
  const clearSources=button('AI 원본 선택 해제',()=>{sourceList.querySelectorAll('input:checked').forEach(cb=>cb.checked=false);selectionSummary();},'button small');
  sourceTools.append(sourceCount,clearSources);
  sources.append(sourceLegend,sourceRules,sourceTools,sourceList,combinedOrderBox);
  function selectionSummary(){const selected=selectedSources(),bytes=assets.filter(a=>selected.includes(a.id)).reduce((sum,a)=>sum+Number(a.byte_size||0),0),max=settings?.limits?.maxAssets??3,maxBytes=settings?.limits?.maxAssetBytes??6*1024*1024;
    const queued=continuationOptions?.mode.value==='PHOTO';sourceRules.textContent=queued?`선택한 원본을 표시한 순서대로 한 개씩 보냅니다. 최대 20개를 대기 목록으로 준비할 수 있으며, 한 원본이 길면 다음 행부터 나누어 읽습니다. 개별 원본은 ${fileSize(maxBytes)} 이내여야 합니다.`:`고른 원본만 AI에게 보냅니다. 한 번에 최대 ${max}개 · 합계 ${fileSize(maxBytes)} 이내. 사진이 많거나 표가 길면 ‘사진을 끝까지 나누어 판독’을 선택하세요.`;
    sourceCount.textContent=queued?`처리 대기 ${selected.length} / 20개 · 전체 ${fileSize(bytes)} · 한 번에 원본 1개`:`전송 선택 ${selected.length} / ${max}개 · ${fileSize(bytes)}`;
    clearSources.disabled=pending||!context.active()||!selected.length;renderCombinedOrder(selected);
    targetHint.dataset.selection=String(selected.length);if(targetMode.value==='photos')targetHint.textContent=queued?`선택한 ${selected.length}개 원본을 각 시트에 순서대로 옮깁니다. 큰 원본은 여러 구간으로 나누며, 적용한 구간 뒤부터 이어갑니다.`:`선택 ${selected.length} / ${max}장 · ${fileSize(bytes)} / ${fileSize(maxBytes)}. 사진별 결과를 검토하세요.`;
  }

  consent.type='checkbox';
  consent.required=true;
  sendConfirm.append(consent,el('span','현재 업무의 제목·답변, 엑셀의 선택한 시트·행 범위, 아래에서 고른 원본 자료의 내용이 GPT API로 전달됨을 확인했습니다.'));
  const targetBox=el('fieldset','','ai-targets'),targetLegend=el('legend','검토할 시트'),targetMode=el('select'),targetSelect=el('select'),photoList=el('div','','ai-photo-targets'),targetHint=el('p','','muted');
  targetMode.setAttribute('aria-label','시트 검토 방식');targetSelect.setAttribute('aria-label','AI가 편집할 시트');
  [['single','선택한 시트 한 개'],['photos','사진마다 별도 시트'],['combined','이어지는 사진을 한 시트로']].forEach(([v,t])=>{const o=el('option',t);o.value=v;targetMode.append(o);});
  targetBox.append(targetLegend,targetMode,targetSelect,photoList,targetHint);targetBox.hidden=context.workflow()!=='EXCEL';
  targetMode.addEventListener('change',()=>{const ids=photoSelections();continuationOptions.setMode(['photos','combined'].includes(targetMode.value)?'PHOTO':'',{force:true});consent.checked=false;refreshSheetTargets();if(targetMode.value==='combined'){scopeMode.value='operations';chooseCombinedSources(ids.length?ids:[targetSelect.value]);prompt.value='선택한 사진은 같은 표의 이어지는 페이지입니다. 반복 제목행은 제외하고 표시한 사진 순서대로 옮겨 주세요. 기존 데이터가 있으면 APPEND_ROWS로 새 행에만 추가하고 기존 행은 교체하지 마세요. 이번 구간의 마지막 원본 행과 남은 내용 여부를 정확히 보고해 주세요. 중복이 의심되면 질문해 주세요. 판독할 수 없는 값은 임의로 채우지 마세요.';selectionSummary();}});targetSelect.addEventListener('change',()=>{consent.checked=false;});
  const rangeBox=el('div','','ai-row-range'),rangeStart=el('input'),rangeCount=el('input'),scopeBox=el('div','','ai-scope-choice'),scopeMode=el('select'),scopeHint=el('p','','muted'),contextSummary=el('p','','ai-context-summary');
  scopeMode.setAttribute('aria-label','대용량 데이터 AI 편집 범위');
  [['operations','전체 데이터에 편집 명령 적용'],['range','일부 행의 값을 읽고 수정']].forEach(([value,label])=>{const option=el('option',label);option.value=value;scopeMode.append(option);});
  scopeBox.append(el('label','데이터가 많은 표의 작업 범위'),scopeMode,scopeHint);scopeBox.hidden=true;
  rangeStart.type=rangeCount.type='number';rangeStart.min=rangeCount.min=1;rangeStart.value=1;rangeCount.value=100;rangeStart.setAttribute('aria-label','AI 시작 행');rangeCount.setAttribute('aria-label','AI 행 수');
  rangeBox.append(el('span','데이터 행 · 제목행 제외'),rangeStart,el('span','부터'),rangeCount,el('span','행만 검토 · 나머지 행 유지'));rangeBox.hidden=true;targetBox.append(scopeBox,rangeBox,contextSummary);
  function refreshRange(){
    const target=book().sheets.find(s=>s.id===targetSelect.value),rows=target?.data.rows.length||0,cols=target?.data.columns.length||1,large=rows>200||rows*cols>4000;
    if(targetMode.value==='combined'&&rows>0)scopeMode.value='operations';
    scopeBox.hidden=context.workflow()!=='EXCEL'||targetMode.value==='photos'||!large;
    rangeBox.hidden=scopeBox.hidden||scopeMode.value!=='range';rangeStart.max=rows;rangeCount.max=Math.min(200,Math.floor(4000/cols));rangeCount.value=Math.max(1,Math.min(Number(rangeCount.value)||100,Number(rangeCount.max),Math.max(rows,1)));rangeStart.value=Math.min(Math.max(1,Number(rangeStart.value)||1),Math.max(1,rows-Number(rangeCount.value)+1));
    continuationOptions?.refresh();scopeHint.textContent=scopeMode.value==='operations'?'열 구성과 일부 예시를 참고해 변경 명령을 제안합니다. 계산열 추가·오류 정리·지정 셀 수정은 서버가 전체 데이터에 적용하며, 실제 영향 건수를 먼저 보여 줍니다.':'지정한 행만 AI가 읽고 값을 수정합니다. 범위 밖 데이터는 유지됩니다. 전체 열 추가·계산에는 전체 데이터 작업을 선택하세요.';
    contextSummary.textContent=target?`${target.name} · ${rows.toLocaleString()}행 / ${(target.data.columns||[]).length}열 · ${target.data.columns.slice(0,8).map(c=>c.label).join(' · ')}${target.data.columns.length>8?' …':''}`:'표가 비어 있습니다. 기본정보의 목적과 지침을 바탕으로 표를 만들 수 있습니다.';
    refreshExamples();
  }
  targetSelect.addEventListener('change',refreshRange);scopeMode.addEventListener('change',()=>{consent.checked=false;refreshRange();updateControls();});[rangeStart,rangeCount].forEach(n=>n.addEventListener('change',()=>{consent.checked=false;}));
  const continuationOptions=createContinuationOptions({sheet:()=>book().sheets.find(s=>s.id===targetSelect.value),selection:()=>continuationSelection(),onChange:()=>{consent.checked=false;requestDraft=null;selectionSummary();}});continuationOptions.host.hidden=context.workflow()!=='EXCEL';
  const replaceInstructionsBox=el('div','','ai-continuation-instructions'),replaceInstructionsLabel=el('label','','check'),replaceInstructions=el('input');
  replaceInstructions.type='checkbox';replaceInstructions.id='ai-replace-continuation-instructions';replaceInstructions.checked=false;replaceInstructionsBox.hidden=true;
  replaceInstructionsLabel.append(replaceInstructions,el('span','이 답변으로 작업 AI 지침 전체 교체'));replaceInstructionsBox.append(replaceInstructionsLabel,el('p','앞선 지침을 새 답변으로 바꾸며 처리 위치와 적용 데이터는 유지합니다. 누적 지침이 길 때 핵심 조건을 모두 다시 적으세요.','muted'));
  replaceInstructions.addEventListener('change',()=>{consent.checked=false;requestDraft=null;});
  form.append(targetBox,continuationOptions.host,promptLabel,prompt,examples,replaceInstructionsBox,sources,sendConfirm,start);
  function continuationSelection(){const assetIds=selectedSources();if(targetMode.value==='photos'){const selected=new Set(photoSelections()),entries=book().sheets.filter(s=>selected.has(s.id));return {assetIds:entries.flatMap(s=>s.sourceAssetIds||[]),sourceSheetIds:entries.flatMap(s=>(s.sourceAssetIds||[]).map(()=>s.id))};}return {assetIds,sourceSheetIds:assetIds.map(()=>targetSelect.value)};}
  function book(){return context.sheet?.()?.workbook?.()||{activeSheetId:'sheet1',sheets:[]};}
  function photoSelections(){return [...photoList.querySelectorAll('input:checked')].map(n=>n.value);}
  function selectedSources(){if(context.workflow()==='EXCEL'&&targetMode.value==='photos'){const ids=new Set(photoSelections());return book().sheets.filter(s=>ids.has(s.id)).flatMap(s=>s.sourceAssetIds||[]);}const selected=[...sourceList.querySelectorAll('input:checked')].map(n=>n.value);return targetMode.value==='combined'?[...combinedOrder.filter(id=>selected.includes(id)),...selected.filter(id=>!combinedOrder.includes(id))]:selected;}
  function renderCombinedOrder(selected){
    combinedOrderBox.hidden=targetMode.value!=='combined';if(combinedOrderBox.hidden)return;combinedOrder=selected;combinedOrderBox.replaceChildren(el('strong','사진 읽는 순서'));
    selected.forEach((id,index)=>{const asset=assets.find(a=>a.id===id),row=el('div','','ai-photo-order-row');row.append(el('span',`${index+1}. ${asset?.original_name||id}`));for(const [delta,label] of [[-1,'위로'],[1,'아래로']]){const move=button(label,()=>{const next=index+delta;[combinedOrder[index],combinedOrder[next]]=[combinedOrder[next],combinedOrder[index]];consent.checked=false;selectionSummary();},'button small quiet');move.disabled=pending||!context.active()||index+delta<0||index+delta>=selected.length;move.setAttribute('aria-label',`${asset?.original_name||'사진'} ${label}`);row.append(move);}combinedOrderBox.append(row);});
    if(!selected.length)combinedOrderBox.append(el('p','합칠 사진을 위에서 선택하세요.','muted'));
  }
  function refreshSheetTargets(preferred){
    if(context.workflow()!=='EXCEL')return;
    const b=book(),selected=new Set(preferred||photoSelections()),single=targetSelect.value;
    targetSelect.replaceChildren();photoList.replaceChildren();
    b.sheets.forEach(s=>{const o=el('option',s.name);o.value=s.id;targetSelect.append(o);
      const source=s.sourceAssetIds?.length===1?assets.find(a=>a.id===s.sourceAssetIds[0]&&!a.archived_at&&['image/png','image/jpeg'].includes(a.media_type)):null;
      if(!source)return;const row=el('label','','check'),cb=el('input');cb.type='checkbox';cb.value=s.id;cb.dataset.localMissing=String(locallyMissing(source));cb.checked=selected.has(s.id)&&!locallyMissing(source);row.append(cb,el('span',s.name+' ← '+source.original_name+(locallyMissing(source)?' · '+localLabel(source):'')));photoList.append(row);cb.addEventListener('change',()=>{consent.checked=false;selectionSummary();});
    });
    targetSelect.value=b.sheets.some(s=>s.id===single)?single:b.activeSheetId;
    if(!photoList.children.length)photoList.append(el('p','사진으로 만들기에서 사진을 등록하고 사진별 시트로 준비하세요. 원본 연결이 해제되거나 삭제된 사진은 다시 선택해야 합니다.','muted'));
    refreshRange();const batch=targetMode.value==='photos';targetSelect.hidden=batch;photoList.hidden=!batch;sources.hidden=batch;
    targetHint.textContent=targetMode.value==='combined'?'같은 표의 이어지는 사진을 아래에서 고르세요. 선택한 시트가 비어 있으면 표를 만들고, 데이터가 있으면 새 행 추가를 제안합니다. 큰 원본은 여러 구간으로 나누어 끝까지 이어 읽습니다. 다른 표·기간이면 사진별 시트로 분리하세요.':batch?`사진 한 장당 한 시트에 제안합니다. 선택한 사진을 순서대로 처리합니다. 한 사진이 길면 나누어 계속 읽으며 호출별 사용량이 합산됩니다. 기존 셀은 제안을 적용할 때만 바뀝니다.`:'지정한 시트만 읽고 편집을 제안합니다. 다른 시트의 셀은 유지합니다.';
    updateControls();
  }
  function chooseCombinedSources(ids,explicitSourceIds){
    const targets=new Set(ids),sourceIds=new Set(explicitSourceIds||book().sheets.filter(s=>targets.has(s.id)).flatMap(s=>s.sourceAssetIds||[]));let count=0;
    if(sourceIds.size>20)throw Error('한 작업에는 원본 20개까지 준비할 수 있습니다. 나머지는 업로드 대기 목록에 보관하고, 현재 작업 완료 후 이어 추가하세요.');
    const inputs=[...sourceList.querySelectorAll('input')],unavailable=[...sourceIds].filter(id=>!inputs.some(input=>input.value===id)||!assets.some(asset=>asset.id===id&&!locallyMissing(asset)));
    if(unavailable.length)throw Error(`이어 읽을 원본 중 ${unavailable.length}개를 찾을 수 없습니다. 작업 첨부의 원본 연결·파일 상태를 확인한 뒤 사진을 다시 준비하세요. 원본을 임의로 건너뛰지 않았습니다.`);
    sourceList.querySelectorAll('input').forEach(cb=>cb.checked=false);combinedOrder=[];
    for(const id of sourceIds){const cb=inputs.find(input=>input.value===id);cb.checked=true;combinedOrder.push(id);count++;}
    if(ids.length&&book().sheets.some(s=>s.id===ids[0]))targetSelect.value=ids[0];refreshRange();return count;
  }
  async function preparePhotoSheets(ids,{combine=false,sourceAssetIds}={}){
    if(ids.length>20)throw Error('원본 20개씩 순서대로 준비하세요. 남은 사진은 업로드 대기 목록에서 이어 추가할 수 있습니다.');
    targetMode.value=combine?'combined':'photos';if(combine)scopeMode.value='operations';await refreshAssets();const chosen=[...ids];continuationOptions.setMode('PHOTO',{force:true});refreshSheetTargets(chosen);
    if(combine){const count=chooseCombinedSources(ids,sourceAssetIds);prompt.value='선택한 사진은 같은 표의 이어지는 페이지입니다. 표시한 사진 순서와 기본정보의 행·열 기준을 지키고 반복 제목행은 제외해 주세요. 기존 데이터가 있으면 APPEND_ROWS로 기존 행 다음에 추가하고 원본을 교체하지 마세요. 이번 구간의 마지막 원본 행과 남은 내용 여부를 정확히 보고해 주세요. 다음 구간은 저장된 위치부터 이어 읽습니다. 중복이 의심되거나 읽을 수 없는 값은 추측하지 말고 물어봐 주세요.';message(runStatus,`이어지는 사진 ${count}장을 한 시트로 읽을 준비가 되었습니다. 원본 선택과 순서를 확인하세요. 원본을 한 개씩 나누어 요청하고, 긴 원본도 다음 행부터 이어 추가합니다.`);}
    else{message(runStatus,`${ids.length}개 사진 시트를 준비했습니다. ${chosen.length}개 원본을 순서대로 처리할 대기 목록에 넣었습니다. 각 구간을 확인·적용한 뒤 다음 구간을 요청하세요.`);prompt.value='각 원본 사진을 해당 시트의 표로 옮겨 주세요. 흐릿하거나 잘린 값은 추측하지 말고 위치와 다시 촬영할 방법을 설명해 주세요.';}
    consent.checked=false;selectionSummary();
  }

  runStatus.setAttribute('role','status');
  runStatus.setAttribute('aria-live','polite');
  historyBox.append(historyTitle,historyList);
  const conversation=el('section','','ai-conversation'),conversationHeading=el('div','','ai-conversation-heading'),conversationLog=el('div','','ai-conversation-log');
  conversationLog.setAttribute('aria-label','AI와 나눈 대화');conversationLog.setAttribute('role','log');conversationLog.setAttribute('aria-live','polite');
  conversationHeading.append(el('h3','이 작업의 대화'),button('새 요청 작성',()=>{prompt.value='';prompt.focus();form.scrollIntoView({block:'start',behavior:'instant'});},'button small quiet'));
  conversation.append(conversationHeading,conversationLog);
  const continuationProgress=el('section','','ai-continuation-progress');continuationProgress.hidden=true;continuationProgress.setAttribute('aria-label','나누어 처리하는 작업 진행');
  host.append(connection,intro,conversation,form,runStatus,runMeta,cancel,continuationProgress,result,historyBox);
  cancel.hidden=true;
  function refreshExamples(){
    examples.replaceChildren();
    const values=context.workflow()==='EXCEL'?editExamples(book().sheets.find(s=>s.id===targetSelect.value),startMode):[['필요한 조건부터 확인','이미 작성한 답은 다시 묻지 말고, 지금 작업에 꼭 필요한 모호한 조건부터 확인해 주세요.'],['현재 내용 다듬기','현재 자료와 직접 입력을 보존하면서 더 편리한 구성을 제안해 주세요. 추정한 부분은 구분해 주세요.']];
    values.forEach(([title,value])=>examples.append(button(title,()=>{prompt.value=value;prompt.focus();},'button small')));
  }
  function renderConversation(){
    const runs=conversationItems(historyRuns,selectedRun),signature=JSON.stringify(runs.map(r=>[r.id,r.status,r.prompt,r.proposal?.summary,r.message,r.sheetResults?.length]));if(signature===conversationSignature)return;conversationSignature=signature;const follow=conversationLog.scrollTop+conversationLog.clientHeight>=conversationLog.scrollHeight-32;conversationLog.replaceChildren();
    if(!runs.length)conversationLog.append(el('p','필요한 내용을 말해 주세요. 요청과 답변이 이곳에 쌓이며 작업을 이어갈 수 있습니다.','ai-conversation-empty'));
    runs.forEach(run=>{
      if(run.prompt){const user=el('article','','ai-message user');user.append(el('strong','나'),el('p',run.prompt),el('small',whenText(run.createdAt)));conversationLog.append(user);}
      const reply=el('article','','ai-message assistant');reply.append(el('strong','AI'),el('p',run.proposal?.summary||run.message||(run.sheetResults?.length?`사진 ${run.sheetResults.length}개 검토 결과를 준비했습니다.`:labels[run.status]||run.status)),el('small',`${run.targetSheetName||'이 작업'} · ${labels[run.status]||run.status}`));
      if(run.proposal||run.sheetResults?.length)reply.append(button(run.id===selectedRun?.id?'현재 변경안 보기':'이 변경안 다시 보기',()=>{if(run.id!==selectedRun?.id)selectRun(run);result.scrollIntoView({block:'start',behavior:'instant'});},'button small quiet'));
      conversationLog.append(reply);
    });
    if(follow)conversationLog.scrollTop=conversationLog.scrollHeight;
  }
  refreshExamples();renderConversation();
  function has(action){
    return context.caps().has('workspace.ai.'+action);
  }
  function updateControls(){
    const enabled=!disposed&&!pending&&context.active();
    const needsInput=continuationSummary(selectedRun)?.needsInput===true;replaceInstructionsBox.hidden=!needsInput;replaceInstructions.disabled=!enabled||!needsInput;if(!needsInput)replaceInstructions.checked=false;
    continuationOptions.disabled(!enabled);renderContinuation();prompt.disabled=!enabled;consent.disabled=!enabled;examples.querySelectorAll('button').forEach(node=>node.disabled=!enabled);
    start.disabled=!enabled||!has('run')||!settings?.available;
    cancel.disabled=!enabled||!has('cancel');
    if(selectedRun&&activeStatuses.has(selectedRun.status))start.disabled=true;
    result.querySelectorAll('[data-ai-apply]').forEach(node=>node.disabled=!enabled||!has('apply')||selectedRun?.status!=='READY'||!proposal?.ready);
    result.querySelectorAll('input,textarea').forEach(node=>node.disabled=pending||!context.active()||selectedRun?.status!=='READY'||(['sheet','operations'].includes(node.dataset.aiField)&&!context.caps().has('workspace.sheet.update')));
    result.querySelectorAll('button:not([data-ai-apply]):not([data-ai-batch-apply])').forEach(node=>node.disabled=pending);
    sourceList.querySelectorAll('input').forEach(node=>node.disabled=!enabled||!has('run')||node.dataset.localMissing==='true'||!context.caps().has('workspace.asset.download'));
    result.querySelectorAll('[data-ai-batch-apply]').forEach(node=>node.disabled=!enabled||!has('apply')||!context.caps().has('workspace.sheet.update')||selectedRun?.status!=='READY');
    scopeMode.disabled=!enabled||(targetMode.value==='combined'&&(book().sheets.find(s=>s.id===targetSelect.value)?.data.rows.length||0)>0);rangeStart.disabled=rangeCount.disabled=rangeBox.hidden||!enabled;targetMode.disabled=!enabled;targetSelect.disabled=!enabled;photoList.querySelectorAll('input').forEach(n=>n.disabled=!enabled||!has('run')||n.dataset.localMissing==='true');
    selectionSummary();
  }
  function fail(error){
    const text=error.status===409?'AI가 읽은 기준 버전과 지금 서버 내용이 달라 적용하지 않았습니다. 수동 입력과 기존 제안은 그대로입니다. 최신 내용을 확인하고 다시 검토를 요청하세요.':error.message;
    message(runStatus,text,true);
    if(error.status===409)context.onConflict?.();
  }
  async function init(){
    try{
      settings=await request('/api/ai/settings');
      prompt.maxLength=settings.limits?.maxPromptLength||6000;
      connection.textContent=settings.available?'AI 검토를 요청할 수 있습니다. 연결 상태는 요청 시 확인하며, 제안은 직접 검토한 뒤 적용합니다.':`AI 연결 준비 필요 · ${settings.reason||'관리자가 개인 설정에서 API 연결을 준비해야 합니다.'} 직접 입력·표 저장·다운로드는 계속 사용할 수 있습니다.`;
      connection.classList.toggle('warning',!settings.available);
    }
    catch(error){
      settings={
        available:false
      };
      connection.textContent='AI 연결을 확인하지 못했습니다. 직접 입력으로 계속할 수 있습니다. '+error.message;
    }
    updateControls();
    await refreshAssets();
    refreshSheetTargets();
    await loadHistory(true);
  }
  async function refreshAssets({background=false}={}){
    if(!context.caps().has('workspace.asset.list')){
      sourceList.replaceChildren(el('p','첨부 조회 권한이 없어 원본 자료를 선택할 수 없습니다.','muted'));
      return;
    }
    const checked=new Set([...sourceList.querySelectorAll('input:checked')].map(n=>n.value));
    assets=await request('/api/assets/selected',{loading:!background,method:'POST',body:{businessId:context.business(),projectId:context.id()||null,ids:context.currentInput().assetIds||[]}});
    sourceList.replaceChildren();
    if(!context.caps().has('workspace.asset.download'))sourceList.append(el('p','원본 전송에는 첨부 다운로드 권한이 필요합니다. 현재는 업무 답변만으로 AI 검토할 수 있습니다.','muted'));
    assets.filter(a=>!a.archived_at&&['image/png','image/jpeg','text/plain'].includes(a.media_type)).forEach(asset=>{
      const row=el('div','','ai-source-row'),l=el('label','','check'),cb=el('input');
      cb.type='checkbox';
      cb.value=asset.id;
      cb.dataset.localMissing=String(locallyMissing(asset));
      cb.checked=checked.has(asset.id)&&!locallyMissing(asset);
      cb.addEventListener('change',()=>{
        if(cb.checked){
          try{
            const selected=[...sourceList.querySelectorAll('input:checked')].map(n=>n.value);
            if(selected.length>(continuationOptions.mode.value==='PHOTO'?20:(settings?.limits?.maxAssets??3)))throw new Error('사진을 여러 개 이어 읽으려면 작업 방식에서 ‘사진을 끝까지 나누어 판독’을 선택하세요. 이어 처리 대기 원본은 최대 20개입니다.');
            if(continuationOptions.mode.value!=='PHOTO'&&assets.filter(a=>selected.includes(a.id)).reduce((sum,a)=>sum+Number(a.byte_size||0),0)>(settings?.limits?.maxAssetBytes??6*1024*1024))throw new Error('AI 원본 합계 용량을 초과했습니다. 자료를 나누어 선택하세요.');
            context.linkAsset?.(asset.id);
          }
          catch(error){
            cb.checked=false;
            fail(error);
          }
        }
        selectionSummary();
      });
      l.append(cb,el('span',asset.original_name+' · '+fileSize(Number(asset.byte_size))+(locallyMissing(asset)?' · '+localLabel(asset)+' · 작업 첨부에서 교체하세요.':'')));
      const link=el('a','원본 내려받아 대조');
      link.href=`/api/assets/${encodeURIComponent(asset.id)}/download`;
      link.className='button small quiet';
      if(!context.caps().has('workspace.asset.download'))link.hidden=true;
      row.append(l);if(!locallyMissing(asset))row.append(link);
      sourceList.append(row);
      if(!locallyMissing(asset)&&context.caps().has('workspace.asset.download')&&['image/png','image/jpeg'].includes(asset.media_type)){
        const detail=el('details','','source-preview'),title=el('summary','원본 사진 펼쳐서 대조'),picture=el('img');picture.alt=asset.original_name+' 원본';picture.loading='lazy';
        detail.addEventListener('toggle',()=>{if(detail.open&&!picture.hasAttribute('src'))picture.src=`/api/my/files/${encodeURIComponent(asset.id)}/preview?v=${asset.version}`;});
        picture.addEventListener('error',()=>{picture.replaceWith(el('p','이 PC에서 원본을 불러올 수 없습니다. 작업 첨부에서 파일을 교체해 주세요.','missing-file-badge'));},{once:true});detail.append(title,picture);sourceList.append(detail);
      }
    });
    if(!sourceList.children.length)sourceList.append(el('p','이 작업에 연결된 AI용 사진·텍스트 자료가 없습니다. 자료 없이 답변만으로도 시작할 수 있습니다.','muted'));
    refreshSheetTargets();
    updateControls();
  }
  async function loadHistory(selectLatest=false){
    if(!context.id()||!has('read'))return;
    const list=await request(`/api/drafts/${encodeURIComponent(context.id())}/ai-runs`);
    historyRuns=list;renderConversation();historyList.replaceChildren();
    if(!list.length)historyList.append(el('p','아직 AI 작업이 없습니다.','muted'));
    list.forEach(run=>{const progress=continuationSummary(run),detail=progress?` · ${progress.title} · ${progress.processed.toLocaleString()}${progress.total?'/'+progress.total.toLocaleString():''}행${progress.complete?' 완료':progress.abandoned?' 종료':progress.canContinue?' · 이어하기 가능':''}`:'';historyList.append(button(`${labels[run.status]||run.status}${detail} · ${whenText(run.createdAt)}`,()=>selectRun(run),'button small quiet'));});
    if(selectLatest&&list.length&&!selectedRun)selectRun(list[0]);
  }
  function selectRun(run){
    selectedRun=run;
    replaceInstructions.checked=false;
    proposal=run.proposal?copy(run.proposal):null;
    answers=new Map();
    lastRendered='';
    applyDraft=null;
    renderRun();
    schedule();
  }
  function renderRun(){
    if(!selectedRun)return;
    const run=selectedRun;renderConversation();renderContinuation();
    message(runStatus,`${labels[run.status]||run.status} · ${run.phase||''}${run.message?' · '+run.message:''}`,run.status==='FAILED');
    const noGeneration=run.diagnostic?.generationRequests===0;
    const usage=run.inputTokens||run.outputTokens?`입력 ${run.inputTokens||0} / 출력 ${run.outputTokens||0} 토큰`:noGeneration?'생성 요청 전 중단 · AI 생성 요청 0건':activeStatuses.has(run.status)?'사용량은 응답을 받은 뒤 반영됩니다.':'토큰 사용량 미확인 · 과금이 없다는 의미는 아닙니다.';
    const sheetVersion=Number(run.baseSheetVersion)>=0?`표 버전 ${run.baseSheetVersion}`:'저장된 표 없음';
    runMeta.textContent=`${run.photoSheetIds?.length?'사진별 '+run.photoSheetIds.length+'개 시트':run.targetSheetName?'대상 시트: '+run.targetSheetName:''} · 요청 기준: 업무 버전 ${run.baseDraftVersion} / ${sheetVersion} · ${run.model||''} · ${usage}${run.toolNames?.length?' · 사용한 서버 기능: '+run.toolNames.join(', '):''}`;
    const cancellableProposal=run.status==='READY'&&!!continuationSummary(run)&&!continuationSummary(run).finished;
    cancel.hidden=!activeStatuses.has(run.status)&&!cancellableProposal;
    cancel.textContent=cancellableProposal?'현재 변경안 취소 · 처리 위치 유지':'진행 중 작업 취소';
    const signature=run.id+':'+run.status+':'+(run.sheetResults?.length||0)+':'+JSON.stringify(run.continuation||{});
    if(signature!==lastRendered){
      lastRendered=signature;
      renderProposal();
    }
    updateControls();
  }
  function infoList(title,lines,className=''){
    const section=el('section','','ai-facts '+className);
    section.append(el('h3',title));
    if(!lines?.length)section.append(el('p','없음','muted'));
    else{
      const ul=el('ul');
      lines.forEach(text=>ul.append(el('li',text)));
      section.append(ul);
    }
    return section;
  }
  function renderProposal(){
    result.replaceChildren();
    if(selectedRun?.sheetResults?.length||selectedRun?.photoSheetIds?.length){renderPhotoResults();return;}
    if(!proposal){
      if(selectedRun?.status==='FAILED'){
        const configFailure=['AI_PROVIDER_INVALID_KEY','AI_PROVIDER_AUTH','AI_PROVIDER_PERMISSION','AI_MODEL_UNAVAILABLE','AI_PROVIDER_BILLING'].includes(selectedRun.errorCode);
        result.append(el('p',configFailure?'작성한 내용은 유지됩니다. 관리자가 연결 설정을 확인한 뒤 다시 검토를 요청해 주세요.':'작성한 내용은 유지됩니다. 오류 원인을 확인한 뒤 직접 다시 요청해 주세요. 자동 재시도하지 않았습니다.','notice warning'));
        result.append(diagnosticDetails(selectedRun.diagnostic,selectedRun.errorCode));
        if(settings?.administrator){const link=el('a','시스템 연결에서 AI 연결 확인','button');link.href='/admin/system';result.append(link);}
      }
      return;
    }
    const p=proposal,header=el('div','','ai-proposal-heading');
    header.append(el('h3','이렇게 이해했어요'),el('p',p.summary));
    result.append(header,infoList('확인한 내용',p.confirmed,'confirmed'),infoList('AI 추정 · 적용 전 확인',p.assumptions,'assumptions'));
    if(p.warnings?.length)result.append(infoList('주의·미확인 내용',p.warnings,'warnings'));
    if(p.questions?.length){
      const section=el('section','','ai-questions');
      section.append(el('h3','먼저 확인하면 더 정확해져요'));
      p.questions.forEach(question=>{
        const box=el('div','','ai-question'),label=el('label',question.title+(question.required?' · 필수 확인':'')),input=el('textarea'),choices=el('div','','guide-choices');
        input.rows=2;
        input.maxLength=2000;
        input.id='ai-question-'+question.id;
        label.htmlFor=input.id;
        input.placeholder=question.recommended||'직접 답하거나 다음 검토에서 다시 확인할 수 있습니다.';
        input.addEventListener('input',()=>answers.set(question.id,input.value));
        question.choices.forEach(value=>choices.append(button(value,()=>{
          input.value=value;
          answers.set(question.id,value);
          input.focus();
        })));
        choices.append(button('잘 모르겠어요',()=>{
          input.value='아직 정하지 못했습니다. 안전한 선택지를 비교하고 필요한 이유를 설명해 주세요.';
          answers.set(question.id,input.value);
        }));
        box.append(label,el('p',question.reason,'muted'),choices,input);
        section.append(box);
      });
      result.append(section);
    }
    const changed=el('section','','ai-change-preview'),selection=el('div','','ai-apply-fields'),current=context.currentInput(),editor=el('details'),editorTitle=el('summary','기본정보 제안을 직접 수정');
    const metadata=el('details'),metadataTitle=el('summary','기본정보 변경안 (이름·AI 지침)');metadata.append(metadataTitle);p.draft=p.draft||{title:current.title,answers:{...current.answers}};p.draft.answers=p.draft.answers||{};
    editor.append(editorTitle);
    changed.append(el('h3','적용할 변경 미리보기'),el('p','영향 범위와 값을 확인한 뒤 적용하세요. 선택한 변경만 저장되며, 표 변경은 원본 편집에서 직전 저장 한 번을 되돌릴 수 있습니다.','muted'));
    [['title','초안 이름'],...fields].forEach(([key,label])=>{
      const next=key==='title'?p.draft.title:p.draft.answers[key]||'',before=key==='title'?current.title:current.answers[key]||'',row=el('div','','ai-diff'),l=el('label','','check'),cb=el('input');
      cb.type='checkbox';
      cb.value=key;
      cb.dataset.aiField=key;
      cb.checked=next!==before;
      l.append(cb,el('strong',label+(next===before?' · 변경 없음':'')));
      const old=el('p','현재: '+(before||'비어 있음'),'muted'),after=el('p','제안: '+(next||'비어 있음'));
      row.append(l,old,after);
      metadata.append(row);
      const input=key==='title'?el('input'):el('textarea');
      input.value=next;
      input.maxLength=key==='title'?100:2000;
      input.id='proposal-'+key;
      const editLabel=el('label',label);
      editLabel.htmlFor=input.id;
      input.addEventListener('input',()=>{
        if(key==='title')p.draft.title=input.value;
        else p.draft.answers[key]=input.value;
        after.textContent='제안: '+(input.value||'비어 있음');
        applyDraft=null;
      });
      editor.append(editLabel,input);
    });
    selection.append(metadata);
    if(p.operations?.length)renderOperations(selection,p.operations);if(selectedRun.continuation?.requiredField==='continuation'){const label=el('label','','check'),cb=el('input');cb.type='checkbox';cb.value='continuation';cb.dataset.aiField='continuation';cb.checked=true;label.append(cb,el('strong','현재 원본의 끝을 확인하고 다음 원본으로 진행'));selection.append(label,el('p','이번 구간에서 새 행이 없다는 판독 결과입니다. 실제 원본의 끝과 누락 여부를 확인한 뒤 적용해야 다음 원본으로 넘어갑니다.','notice warning'));}
    if(p.sheet){
      const l=el('label','','check'),cb=el('input');
      cb.type='checkbox';
      cb.value='sheet';
      cb.dataset.aiField='sheet';
      cb.checked=context.caps().has('workspace.sheet.update');
      cb.disabled=!context.caps().has('workspace.sheet.update');
      l.append(cb,el('strong',`${selectedRun.targetSheetName||'대상 시트'} 변경 · ${p.sheet.columns.length}열 / ${p.sheet.rows.length}행`));
      const readPreview=el('div');
      readPreview.append(makePreviewTable(p.sheet,{showFormulas:true}));
      const scope=selectedRun.rowScope;selection.append(l,el('p',scope?.rowStart?`Excel ${scope.rowStart+1}~${scope.rowStart+scope.rowCount}행의 값·수식만 제안으로 바꿉니다. 그 밖의 행과 다른 시트는 유지합니다.`:'이 항목은 요청에 지정한 시트의 구조와 데이터 전체를 제안 표로 바꿉니다. 다른 시트는 유지합니다. 숫자·날짜·식별번호를 확인해 주세요.','notice warning'),readPreview);
      const tableDetails=el('details');
      tableDetails.append(el('summary','제안 표의 셀 직접 수정'),makePreviewTable(p.sheet,{
        editable:true,onChange:()=>{
          applyDraft=null;
          readPreview.replaceChildren(makePreviewTable(p.sheet,{showFormulas:true}));
        }
      }),el('p','열 구조의 세부 수정은 제안 적용 후 표 편집에서 진행할 수 있습니다. 이곳은 제안의 셀 값만 수정합니다.','muted'));
      editor.append(tableDetails);
    }
    changed.append(selection,editor);
    result.append(changed);
    const accept=el('label','','check'),acceptInput=el('input');
    acceptInput.type='checkbox';
    acceptInput.id='ai-accept-assumptions';
    accept.append(acceptInput,el('span','위 추정·주의 사항과 적용 범위를 확인했습니다.'));
    result.append(accept);
    const actions=el('div','','actions'),apply=button('선택 항목만 적용',()=>applyProposal(selection,acceptInput).catch(fail),'button primary'),more=button('더 이야기하기',()=>{
      const text=(p.questions||[]).map(q=>{
        const answer=answers.get(q.id);
        return answer?`${q.title}: ${answer}`:'';
      }).filter(Boolean).join('\n');
      if(text)prompt.value=text;
      else if(!prompt.value.trim())prompt.value='현재 제안은 유지하면서 다음 부분을 더 구체적으로 정리해 주세요: ';
      prompt.focus();
      form.scrollIntoView({
        block:'start',behavior:'instant'
      });
      message(runStatus,'현재 제안을 보존했습니다. 답변을 적고 새 검토를 요청하세요. 이전 제안은 아래 이력에서 다시 선택할 수 있습니다.');
    }),dismiss=button('이번 제안 닫기',()=>{
      result.replaceChildren(el('p','이번 제안만 닫았습니다. 서버의 업무·표와 수동 입력은 그대로입니다. 이전 제안에서 다시 열 수 있습니다.','muted'));
    });
    apply.dataset.aiApply='true';
    actions.append(more,apply,dismiss);
    result.append(actions);
    if(!p.ready)result.append(el('p','아직 결정에 필요한 정보가 부족해 적용을 열지 않았습니다. 질문에 답하고 ‘더 이야기하기’로 다시 검토하세요.','notice warning'));
    if(selectedRun.status==='APPLIED')result.append(el('p','이미 적용된 제안입니다. 다시 적용하지 않습니다. 수정은 현재 업무 또는 표 편집에서 진행하세요.','notice success'));
    if(context.workflow()!=='EXCEL')result.append(el('p','이번 결과는 검토된 업무 요약입니다. 실제 DOCX·HWP·PPT 파일, CRM, 일정 등록·알림, 실행 앱을 생성하지 않습니다.','muted'));
    updateControls();
  }

  function renderOperations(selection,operations){
    const chosen=el('label','','check'),cb=el('input');cb.type='checkbox';cb.value='operations';cb.dataset.aiField='operations';cb.checked=context.caps().has('workspace.sheet.update');
    chosen.append(cb,el('strong',`${selectedRun.targetSheetName||'현재 시트'} · 편집 명령 ${operations.length}개 적용`));
    selection.append(chosen,el('p','아래 명령을 표시 순서대로 한 번에 적용합니다. 열 추가 후 계산처럼 서로 연결될 수 있어 묶어서 검토합니다. 대상 시트의 데이터를 편집하며, 연결된 수식·결과는 함께 다시 계산합니다.','muted'));
    const list=el('div','','ai-operation-list'),previews=selectedRun.operationsPreview||[];
    operations.forEach((operation,index)=>{
      const card=el('section','','ai-operation-card'),description=describeOperation(operation),preview=Array.isArray(previews)?previews[index]:previews.operations?.[index];
      card.append(el('h4',`${index+1}. ${description.title}`),el('p',description.detail));
      if(preview){
        card.append(el('p',`영향 범위: ${Number(preview.affectedRows||0).toLocaleString()}행 · ${Number(preview.affectedCells||0).toLocaleString()}셀`,'muted'));
        if(preview.samples?.length){const wrap=el('div','','table-scroll'),table=el('table'),head=el('thead'),row=el('tr'),body=el('tbody');['셀','현재 값','변경 후'].forEach(value=>row.append(el('th',value)));head.append(row);
          preview.samples.forEach(sample=>{const tr=el('tr');[sample.address,sample.before===''?'(빈 값)':sample.before,sample.after===''?'(빈 값)':sample.after].forEach(value=>tr.append(el('td',String(value??''))));body.append(tr);});table.append(head,body);wrap.append(table);card.append(wrap,el('small','표시된 값은 영향 셀의 일부 예시입니다. 전체 적용 범위는 위 건수를 확인하세요.','muted'));}
        if(Array.isArray(preview.errors)&&preview.errors.length)card.append(infoList('적용 전 확인',preview.errors.map(error=>typeof error==='string'?error:JSON.stringify(error)),'warnings'));else if(Number(preview.errors)>0)card.append(el('p',`이 명령 적용 후 남는 계산 오류 ${Number(preview.errors).toLocaleString()}개 · 이어서 오류 위치를 확인하고 수정할 수 있습니다.`,'notice warning'));
      }else card.append(el('p','적용 전 서버에서 범위와 참조를 검증합니다.','muted'));
      list.append(card);
    });selection.append(list);
  }

  function renderPhotoResults(){
    const chosen=el('div','','ai-batch-results');result.append(el('h3','사진별 시트 검토 결과'),el('p','완료된 시트만 골라 한 번에 적용합니다. 미선택 시트와 실패한 시트는 현재 내용을 유지합니다. 적용 후 남은 시트는 최신 저장본으로 새 검토를 요청하세요.','notice'),chosen);
    const results=[...(selectedRun.sheetResults||[])];for(const id of selectedRun.photoSheetIds||[])if(!results.some(r=>r.sheetId===id)){const target=selectedRun.photoTargets?.find(s=>s.sheetId===id)||{sheetId:id,sheetName:book().sheets.find(s=>s.id===id)?.name||id};results.push({...target,status:activeStatuses.has(selectedRun.status)?'PENDING':'UNFINISHED',message:activeStatuses.has(selectedRun.status)?'처리 결과를 기다리고 있습니다.':'이 시트의 완료된 결과가 없어 적용하지 않았습니다. 중단 원인을 해결하고 이 시트만 다시 검토하세요. 사용량은 호출 기록을 확인하세요.'});}
    results.forEach(item=>{
      const card=el('section','','ai-photo-result'),heading=el('h4',item.sheetName+' · '+({READY:'적용 가능',NEEDS_INPUT:'추가 확인 필요',FAILED:'읽기 실패',SKIPPED:'요청하지 않음',PENDING:'결과 대기',UNFINISHED:'결과 미완료'}[item.status]||item.status));card.append(heading);
      if(item.sourceName)card.append(el('p','원본: '+item.sourceName,'muted'));
      if(item.assetId&&context.caps().has('workspace.asset.download')){const a=el('a','원본과 대조','button small quiet');a.href='/api/assets/'+encodeURIComponent(item.assetId)+'/download';a.target='_blank';a.rel='noopener';card.append(a);}
      const p=item.proposal;
      if(p){card.append(el('p',p.summary),infoList('판독·추정·주의 사항',[...(p.confirmed||[]),...(p.assumptions||[]),...(p.warnings||[])]));
        if(p.questions?.length)card.append(infoList('확인이 필요한 내용',p.questions.map(q=>q.title+' — '+q.reason+(q.recommended?' · '+q.recommended:''))));
        if(p.sheet)card.append(makePreviewTable(p.sheet,{showFormulas:true}));}
      if(item.message)card.append(el('p',item.message,'notice warning'));
      if(item.diagnostic)card.append(diagnosticDetails(item.diagnostic,item.errorCode));
      if(item.status==='READY'){const label=el('label','','check'),cb=el('input');cb.type='checkbox';cb.value=item.sheetId;cb.dataset.batchSheet='true';cb.checked=true;label.append(cb,el('span','이 시트에 제안 적용'));card.append(label);}
      const retry=button('이 시트만 다시 검토',()=>{if(!book().sheets.some(s=>s.id===item.sheetId)){message(runStatus,'해당 시트가 삭제되었습니다. 편집에서 새 시트를 만들고 원본을 다시 선택하세요.',true);return;}targetMode.value='single';refreshSheetTargets();targetSelect.value=item.sheetId;sourceList.querySelectorAll('input').forEach(n=>n.checked=n.value===item.assetId);prompt.value=(p?.questions?.length?p.questions.map(q=>q.title+': ').join('\n'):'원본의 읽기 어려운 부분을 확인하고 이 시트만 다시 검토해 주세요.');consent.checked=false;refreshSheetTargets();prompt.focus();form.scrollIntoView({block:'start',behavior:'instant'});},'button small');retry.hidden=activeStatuses.has(selectedRun.status);card.append(retry);chosen.append(card);
    });
    const ack=el('label','','check'),accept=el('input');accept.type='checkbox';ack.append(accept,el('span','원본 사진과 셀 값, 추정·질문·주의 사항 및 선택 시트의 교체 범위를 확인했습니다.'));
    const apply=button('선택 시트 한 번에 적용',()=>applyPhotoResults(chosen,accept).catch(fail),'button primary');apply.dataset.aiBatchApply='true';result.append(ack,apply);
    if(selectedRun.status==='APPLIED')result.append(el('p','선택한 시트를 적용했습니다. 나머지 시트는 새 검토로 이어갈 수 있습니다.','notice success'));
    if(selectedRun.message)result.append(el('p',selectedRun.message,selectedRun.status==='FAILED'?'notice warning':'muted'));
    if(selectedRun.diagnostic)result.append(diagnosticDetails(selectedRun.diagnostic,selectedRun.errorCode));
    updateControls();
  }
  async function applyPhotoResults(selection,accept){
    if(pending)return;
    if(context.hasLocalChanges())throw Error('저장 전 변경 내용이 있습니다. 먼저 저장하고 최신 기준으로 다시 검토하세요.');
    const sheetIds=[...selection.querySelectorAll('[data-batch-sheet]:checked')].map(n=>n.value);
    if(!sheetIds.length)throw Error('적용할 시트를 한 개 이상 선택하세요.');if(!accept.checked)throw Error('원본·추정·주의 사항과 교체 범위를 확인하세요.');
    const now=context.versions();if(now.draftVersion!==selectedRun.baseDraftVersion||now.sheetVersion!==selectedRun.baseSheetVersion)throw Error('검토 이후 저장본이 바뀌었습니다. 현재 내용으로 다시 검토하세요.');
    if(!confirm(`선택한 ${sheetIds.length}개 시트의 내용을 제안으로 교체할까요? 다른 시트는 유지됩니다.`))return;
    pending=true;context.setBusy?.(true);updateControls();
    try{const body={sheetIds,draftVersion:selectedRun.baseDraftVersion,sheetVersion:selectedRun.baseSheetVersion,acceptAssumptions:true},hash=JSON.stringify(body);
      if(!applyDraft||applyDraft.hash!==hash)applyDraft={hash,key:crypto.randomUUID()};
      selectedRun=await request('/api/ai-runs/'+encodeURIComponent(selectedRun.id)+'/apply-sheets',{method:'POST',body:{...body,requestKey:applyDraft.key},idempotencyKey:applyDraft.key});
      applyDraft=null;await context.onApplied?.({keepAi:!!continuationSummary(selectedRun)});lastRendered='';renderRun();await loadHistory();
    }finally{pending=false;context.setBusy?.(false);updateControls();}
  }

  form.addEventListener('submit',event=>{
    event.preventDefault();
    startRun().catch(fail);
  });
  function renderContinuation(){
    renderContinuationProgress(continuationProgress,selectedRun,{busy:pending||!context.active(),capacity:continuationCapacity(selectedRun),onContinue:run=>continueRun(run).catch(fail),onRetry:run=>continueRun(run).catch(fail),onNewSheet:context.caps().has('workspace.sheet.update')?run=>continueRun(run,{newSheet:true}).catch(fail):null,onAbandon:has('cancel')?run=>abandonContinuation(run).catch(fail):null,onResumeComposer:()=>{message(runStatus,'현재까지 적용한 구간과 이어갈 위치를 보관했습니다. 나중에 이 작업의 이전 제안에서 이어할 수 있습니다.');}});
  }
  function continuationCapacity(run){
    if(!continuationSummary(run)||!run.continuation.nextAllowed||run.continuation.mode==='RANGE')return null;
    const current=book(),targetId=rolloverTargets.get(run.id)||run.continuation.targetSheetId||run.targetSheetId;
    if(!current.sheets.some(sheet=>sheet.id===targetId))return null;
    return workbookCapacity(current,targetId);
  }
  async function continueRun(run,{newSheet=false}={}){
    if(pending||!run?.continuation)return;
    if(!has('run')||!settings?.available)throw Error('AI 연결과 작업 요청 권한을 확인해 주세요. 진행 위치는 유지되어 있습니다.');
    const state=run.continuation;
    if(!state.nextAllowed)throw Error('현재 구간을 확인·적용하거나 중단 원인을 해결한 뒤 이어가세요.');
    let targetSheetId=rolloverTargets.get(run.id);const capacity=continuationCapacity(run);
    if(newSheet&&!targetSheetId){
      if(state.mode==='RANGE')throw Error('기존 행 편집은 원본 시트의 해당 위치에서 이어갑니다.');
      if(!context.caps().has('workspace.sheet.update'))throw Error('새 시트를 만들 권한을 확인하세요.');
      const editor=context.sheet(),entry=continuationSheet(book(),state.targetSheetId||run.targetSheetId);
      if(!editor?.addSheets)throw Error('표 편집에서 새 시트를 만들 수 있는지 확인하세요. 진행 위치는 보존되어 있습니다.');
      [targetSheetId]=editor.addSheets([entry],{replaceEmpty:false});if(!targetSheetId)throw Error('이어갈 새 시트를 만들지 못했습니다.');rolloverTargets.set(run.id,targetSheetId);
    }else if(capacity?.columns>0&&!capacity.canContinueHere)throw Error(capacity.reasons.join(' '));
    pending=true;context.setBusy?.(true);updateControls();
    try{
      const prepared=await context.prepareAi([...new Set([...(state.sourceAssetIds||[]),...(state.referenceAssetIds||[])])]);
      const body={prompt:run.prompt||'저장된 작업의 다음 구간을 이어 처리해 주세요.',assetIds:[],photoSheetIds:[],continuationOf:run.id,draftVersion:prepared.draftVersion,sheetVersion:prepared.sheetVersion};
      if(targetSheetId)body.targetSheetId=targetSheetId;
      const fingerprint=JSON.stringify(body);if(!continueDraft||continueDraft.fingerprint!==fingerprint)continueDraft={fingerprint,body,key:crypto.randomUUID()};
      const next=await request(`/api/drafts/${encodeURIComponent(context.id())}/ai-runs`,{method:'POST',body:{...continueDraft.body,requestKey:continueDraft.key},idempotencyKey:continueDraft.key});continueDraft=null;rolloverTargets.delete(run.id);selectRun(next);await loadHistory();continuationProgress.scrollIntoView({block:'start',behavior:'instant'});
    }finally{pending=false;context.setBusy?.(false);updateControls();}
  }
  async function abandonContinuation(run){
    if(pending||!run?.continuation||continuationSummary(run)?.finished)return;
    if(!has('cancel'))throw Error('작업 종료 권한을 확인하세요.');
    if(!confirm('남은 이어 처리를 종료할까요? 이미 적용한 데이터와 이력은 유지됩니다. 남은 자료는 새 작업으로 요청해야 합니다.'))return;
    pending=true;context.setBusy?.(true);updateControls();
    try{selectedRun=await request(`/api/ai-runs/${encodeURIComponent(run.id)}/cancel`,{method:'POST',body:{abandonContinuation:true}});proposal=selectedRun.proposal?copy(selectedRun.proposal):null;lastRendered='';renderRun();await loadHistory();schedule();message(runStatus,'이어 처리를 종료했습니다. 이미 적용한 데이터와 작업 이력은 그대로 보관했습니다.');}
    finally{pending=false;context.setBusy?.(false);updateControls();}
  }
  async function startRun(){
    if(pending)return;
    if(!consent.checked)throw new Error('전송할 업무·자료 범위를 확인해 주세요.');
    if(!prompt.value.trim())throw new Error('무엇을 도와드릴지 짧게 입력하거나 위 예시를 선택해 주세요.');
    const clarification=continuationSummary(selectedRun)?.needsInput===true;
    const continuation=context.workflow()==='EXCEL'?(clarification?selectedRun.continuation:continuationOptions.value()):null;
    const selected=selectedSources(),batch=context.workflow()==='EXCEL'&&targetMode.value==='photos'&&!continuation,photoSheetIds=batch?photoSelections():[],targetSheetId=context.workflow()==='EXCEL'?(continuation?.sourceSheetIds?.[0]||targetSelect.value):null;
    const assetIds=continuation?.mode==='PHOTO'?continuation.sourceAssetIds:selected;
    if(batch&&!photoSheetIds.length)throw Error('사진별로 검토할 시트를 선택하세요.');
    if(new Set(assetIds).size!==assetIds.length)throw Error('같은 사진이 연결된 시트는 한 번에 하나만 선택하세요.');
    if(context.workflow()==='EXCEL'&&!batch&&!targetSheetId)throw Error('검토할 시트를 먼저 추가하세요.');
    if(assets.some(a=>assetIds.includes(a.id)&&locallyMissing(a)))throw new Error('이 PC에 없는 원본입니다. 작업 첨부에서 파일을 교체하세요. 처리 대기 선택은 유지했습니다.');
    if(continuation?.mode==='PHOTO'){
      if(assetIds.length>20)throw Error('이어 처리 대기 원본은 최대 20개입니다. 완료된 작업 다음에 원본을 추가해 새 대기 작업으로 이어가세요.');
      if(assetIds.some(id=>!['image/png','image/jpeg'].includes(assets.find(a=>a.id===id)?.media_type)))throw Error('사진 이어 읽기는 PNG/JPG 원본을 선택하세요. 텍스트 자료는 새 데이터 생성 요청에 연결할 수 있습니다.');
    }else{
      if(assetIds.length>(settings?.limits?.maxAssets??3))throw Error('사진이 많으면 ‘사진을 끝까지 나누어 판독’으로 바꾸세요. 선택한 원본과 입력은 유지했습니다.');
      if(assets.filter(a=>assetIds.includes(a.id)).reduce((sum,a)=>sum+Number(a.byte_size||0),0)>(settings?.limits?.maxAssetBytes??6*1024*1024))throw Error('한 요청의 원본 크기를 넘었습니다. 사진 이어 읽기로 나누거나 큰 원본을 줄여 다시 요청하세요. 선택은 유지했습니다.');
    }
    pending=true;context.setBusy?.(true);updateControls();
    try{
      const prepared=await context.prepareAi(assetIds);
      const body={prompt:prompt.value.trim(),assetIds:continuation?.mode==='PHOTO'?[]:assetIds,...(continuation?{continuation}:{}),...(!continuation&&(!scopeBox.hidden&&scopeMode.value==='operations'||targetMode.value==='combined'&&(book().sheets.find(s=>s.id===targetSheetId)?.data.rows.length||0)>0)?{operationsOnly:true}:{}),...(!continuation&&!batch&&!rangeBox.hidden?{rowStart:Number(rangeStart.value),rowCount:Number(rangeCount.value)}:{}),draftVersion:prepared.draftVersion,sheetVersion:prepared.sheetVersion,targetSheetId,photoSheetIds,previousRunId:!continuation&&!batch&&selectedRun&&['READY','APPLIED'].includes(selectedRun.status)&&selectedRun.proposal&&(selectedRun.rowScope?.rowStart??null)===(!rangeBox.hidden?Number(rangeStart.value):null)&&(selectedRun.rowScope?.rowCount??null)===(!rangeBox.hidden?Number(rangeCount.value):null)&&(context.workflow()!=='EXCEL'||(selectedRun.targetSheetId||'sheet1')===targetSheetId)?selectedRun.id:null};
      if(clarification){delete body.continuation;delete body.targetSheetId;body.assetIds=[];body.photoSheetIds=[];body.continuationOf=selectedRun.id;body.previousRunId=selectedRun.status==='READY'?selectedRun.id:null;if(replaceInstructions.checked)body.replaceContinuationInstructions=true;}
      const fingerprint=JSON.stringify(body);if(!requestDraft||requestDraft.fingerprint!==fingerprint)requestDraft={fingerprint,key:crypto.randomUUID(),body};
      const run=await request(`/api/drafts/${encodeURIComponent(context.id())}/ai-runs`,{method:'POST',body:{...requestDraft.body,requestKey:requestDraft.key},idempotencyKey:requestDraft.key});requestDraft=null;consent.checked=false;selectRun(run);prompt.value='';await loadHistory();
    }finally{pending=false;context.setBusy?.(false);updateControls();}
  }
  async function applyProposal(selection,accept){
    if(pending)return;
    if(context.hasLocalChanges())throw new Error('저장 전 수동 입력·표가 있습니다. 그대로 보존했습니다. 먼저 저장하거나 표 변경을 취소한 뒤 AI 기준 버전을 확인하세요. 수동 저장으로 기준이 달라졌다면 새 AI 검토가 필요합니다.');
    const selected=[...selection.querySelectorAll('input[data-ai-field]:checked')].map(n=>n.value);
    if(!selected.length)throw new Error('적용할 항목을 선택하세요.');
    if(!accept.checked)throw new Error('추정·주의 사항과 적용 범위를 확인해 주세요.');
    const now=context.versions();
    if(now.draftVersion!==selectedRun.baseDraftVersion||now.sheetVersion!==selectedRun.baseSheetVersion)throw new Error('이 제안 이후 업무 또는 표가 저장되었습니다. 현재 입력은 유지했습니다. 최신 상태를 기준으로 다시 검토하세요.');
    const scope=selectedRun.rowScope,extent=selected.includes('operations')?`${proposal.operations?.length||0}개의 편집 명령과 위에 표시된 범위`:selected.includes('continuation')?'현재 원본의 끝 확인 및 다음 위치 저장 · 기존 데이터 유지':scope?.rowStart?`Excel ${scope.rowStart+1}~${scope.rowStart+scope.rowCount}행의 값·수식`:'선택한 시트 전체';if(!confirm(`선택한 ${selected.length}개 항목을 적용할까요? 표 반영 범위: ${extent}.`))return;
    pending=true;
    context.setBusy?.(true);
    updateControls();
    try{
      const body={
        draftVersion:selectedRun.baseDraftVersion,sheetVersion:selectedRun.baseSheetVersion,fields:selected,editedProposal:proposal,acceptAssumptions:true
      };
      const hash=JSON.stringify(body);
      if(!applyDraft||applyDraft.hash!==hash)applyDraft={
        hash,key:crypto.randomUUID()
      };
      selectedRun=await request(`/api/ai-runs/${encodeURIComponent(selectedRun.id)}/apply`,{
        method:'POST',body:{
          ...body,requestKey:applyDraft.key
        },idempotencyKey:applyDraft.key
      });
      applyDraft=null;
      await context.onApplied?.({keepAi:!!continuationSummary(selectedRun)});
      lastRendered='';
      renderRun();
      await loadHistory();
      message(runStatus,selectedRun.continuation?.nextAllowed?'현재 구간을 적용했습니다. ‘다음 구간 요청’으로 남은 작업을 이어가세요.':'선택한 변경을 적용했습니다. 표를 확인하거나 아래 대화로 다음 편집을 이어가세요.');if(continuationSummary(selectedRun))continuationProgress.scrollIntoView({block:'start',behavior:'instant'});
      prompt.placeholder='다음으로 무엇을 수정할까요? 예: 계산한 금액을 기준으로 보고 싶은 항목을 알려 주세요.';
    }
    finally{
      pending=false;
      context.setBusy?.(false);
      updateControls();
    }
  }
  async function cancelRun(){
    if(!selectedRun||pending)return;
    if(!confirm('이 AI 작업의 중단을 요청할까요? 이미 제공자에 전달된 요청의 비용이 취소되거나 환불된다고 보장하지 않습니다.'))return;
    pending=true;
    updateControls();
    try{
      selectedRun=await request(`/api/ai-runs/${encodeURIComponent(selectedRun.id)}/cancel`,{
        method:'POST',body:{
        }
      });
      renderRun();
      schedule();
    }
    finally{
      pending=false;
      updateControls();
    }
  }
  function schedule(){
    clearTimeout(pollTimer);
    if(disposed||!selectedRun||!activeStatuses.has(selectedRun.status))return;
    pollTimer=setTimeout(poll,2000);
  }
  async function poll(){
    const id=selectedRun?.id;
    if(!id||disposed)return;
    pollAbort=new AbortController();
    try{
      const run=await request(`/api/ai-runs/${encodeURIComponent(id)}`,{
        signal:pollAbort.signal,loading:false
      });
      if(selectedRun?.id!==id||disposed)return;
      selectedRun=run;
      if(run.proposal&&!proposal)proposal=copy(run.proposal);
      renderRun();
      if(!activeStatuses.has(run.status))await loadHistory();
      schedule();
    }
    catch(error){
      if(error.name!=='AbortError'){
        message(runStatus,'작업 상태를 읽지 못했습니다. 입력은 보존했습니다. ‘상태 다시 확인’으로 이어 보세요. '+error.message,true);
        const retry=button('상태 다시 확인',()=>poll());
        runStatus.append(' ',retry);
      }
    }
  }
  return {
    init,setMode(value){startMode=value;if(context.workflow()==='EXCEL'&&['guided','direct'].includes(value)&&!(book().sheets.find(s=>s.id===targetSelect.value)?.data.rows.length))continuationOptions.setMode('GENERATE');refreshExamples();},setRequest(text,sheetId){continuationOptions.setMode('',{force:true});targetMode.value="single";scopeMode.value='operations';refreshSheetTargets();targetSelect.value=sheetId;refreshRange();prompt.value=text;consent.checked=false;prompt.focus();},refreshAssets,refreshSheetTargets:()=>{targetSelect.value=book().activeSheetId;refreshSheetTargets();},preparePhotoSheets,protectedSourceIds:()=>protectedContinuationSources(historyRuns,selectedRun),hasUnfinishedContinuation:()=>!!continuationSummary(selectedRun)&&!continuationSummary(selectedRun).finished,attachDraft:()=>loadHistory(true),refreshControls:updateControls,activeRun:()=>selectedRun&&activeStatuses.has(selectedRun.status),isPending:()=>pending,destroy(){
      disposed=true;
      clearTimeout(pollTimer);
      pollAbort?.abort();
    },notifyVersion(){
      if(selectedRun?.status==='READY')message(runStatus,'AI 제안이 준비되어 있습니다. 적용 전 현재 기준 버전·수동 입력 변경 여부를 다시 확인합니다.');
    }
  };
}

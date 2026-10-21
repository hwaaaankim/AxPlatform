import {fileSize} from './file-picker.js?v=0.6.0';
import {diagnosticDetails} from './ai-diagnostic.js?v=0.6.0';
import {
  request
} from './request.js?v=0.6.0';
import {
  el,button,message,whenText
} from './ui.js?v=0.6.0';
import {
  makePreviewTable
} from './sheet-editor.js?v=0.6.0';
import {
  fields
} from './workflow-guide.js?v=0.6.0';
const copy=value=>JSON.parse(JSON.stringify(value));
const activeStatuses=new Set(['QUEUED','RUNNING']);
const labels={
  QUEUED:'서버 작업 대기',RUNNING:'AI 검토 중',READY:'변경 제안 확인 필요',APPLIED:'선택 항목 적용됨',FAILED:'작업 실패',CANCELLED:'취소됨'
};
export function createAiPanel(host,context) {
  let settings=null,selectedRun=null,proposal=null,pollTimer=null,pollAbort=null,disposed=false,pending=false,lastRendered='',requestDraft=null,applyDraft=null,assets=[],answers=new Map();
  const connection=el('p','','notice'),intro=el('p','업무를 짧게 설명하면 서버가 현재 업무·선택 자료를 읽어 변경안을 제안합니다. 제안은 직접 확인하고 고른 항목만 적용하며, 준비가 덜 되면 필요한 질문부터 표시합니다.','muted'),form=el('form','','ai-request-form'),promptLabel=el('label','다음에 무엇을 도와드릴까요?'),prompt=el('textarea'),examples=el('div','','guide-choices'),sources=el('fieldset','','ai-source-list'),sourceLegend=el('legend','이번 AI 검토에 보낼 원본 자료'),sourceList=el('div'),sendConfirm=el('label','','check'),consent=el('input'),start=el('button','현재 입력 저장 후 AI 검토 요청','button primary'),runStatus=el('p','','help-line'),runMeta=el('p','','muted'),cancel=button('진행 중 작업 취소',()=>cancelRun().catch(fail),'button danger'),historyBox=el('details'),historyTitle=el('summary','이전 제안·작업 이어 보기'),historyList=el('div'),result=el('div','','ai-result');
  prompt.id='ai-prompt';
  prompt.maxLength=6000;
  prompt.rows=3;
  prompt.placeholder=context.workflow()==='EXCEL'?'예: 선택한 사진의 열과 실제 값을 표로 옮기고, 흐릿한 숫자는 질문해 주세요.':'예: 지금 답변에서 빠진 조건을 확인하고 업무 요약을 더 구체적으로 정리해 주세요.';
  promptLabel.htmlFor=prompt.id;
  start.type='submit';
  const sourceRules=el('p','','muted'),sourceCount=el('span','','muted'),sourceTools=el('div','','actions');sourceCount.setAttribute('role','status');
  const clearSources=button('AI 원본 선택 해제',()=>{sourceList.querySelectorAll('input:checked').forEach(cb=>cb.checked=false);selectionSummary();},'button small');
  sourceTools.append(sourceCount,clearSources);
  sources.append(sourceLegend,sourceRules,sourceTools,sourceList);
  function selectionSummary(){const selected=selectedSources(),bytes=assets.filter(a=>selected.includes(a.id)).reduce((sum,a)=>sum+Number(a.byte_size||0),0),max=settings?.limits?.maxAssets??3,maxBytes=settings?.limits?.maxAssetBytes??6*1024*1024;
    sourceRules.textContent=`고른 원본만 AI에게 보냅니다. 최대 ${max}개 · 합계 ${fileSize(maxBytes)} 이내. 원본을 고르면 업무 첨부에도 연결합니다. 여기서 선택을 해제해도 업무 첨부와 저장된 파일은 유지합니다.`;
    sourceCount.textContent=`전송 선택 ${selected.length} / ${max}개 · ${fileSize(bytes)}`;
    clearSources.disabled=pending||!context.active()||!selected.length;
    targetHint.dataset.selection=String(selected.length);if(targetMode.value==='photos')targetHint.textContent=`선택 ${selected.length} / ${max}장 · ${fileSize(bytes)} / ${fileSize(maxBytes)}. 사진 한 장당 별도 AI 호출과 시트 제안을 만듭니다. 적용 전 원본과 숫자를 대조하세요.`;
  }

  consent.type='checkbox';
  consent.required=true;
  sendConfirm.append(consent,el('span','현재 업무의 제목·답변, 엑셀의 저장된 표, 아래에서 고른 원본 자료의 내용이 GPT API로 전달됨을 확인했습니다.'));
  const targetBox=el('fieldset','','ai-targets'),targetLegend=el('legend','검토할 시트'),targetMode=el('select'),targetSelect=el('select'),photoList=el('div','','ai-photo-targets'),targetHint=el('p','','muted');
  targetMode.setAttribute('aria-label','시트 검토 방식');targetSelect.setAttribute('aria-label','AI가 편집할 시트');
  [['single','선택한 시트 한 개'],['photos','사진마다 별도 시트']].forEach(([v,t])=>{const o=el('option',t);o.value=v;targetMode.append(o);});
  targetBox.append(targetLegend,targetMode,targetSelect,photoList,targetHint);targetBox.hidden=context.workflow()!=='EXCEL';
  targetMode.addEventListener('change',()=>{consent.checked=false;refreshSheetTargets();});targetSelect.addEventListener('change',()=>{consent.checked=false;});
  form.append(targetBox,promptLabel,prompt,examples,sources,sendConfirm,start);
  function book(){return context.sheet?.()?.workbook?.()||{activeSheetId:'sheet1',sheets:[]};}
  function photoSelections(){return [...photoList.querySelectorAll('input:checked')].map(n=>n.value);}
  function selectedSources(){if(context.workflow()==='EXCEL'&&targetMode.value==='photos'){const ids=new Set(photoSelections());return book().sheets.filter(s=>ids.has(s.id)).flatMap(s=>s.sourceAssetIds||[]);}return [...sourceList.querySelectorAll('input:checked')].map(n=>n.value);}
  function refreshSheetTargets(preferred){
    if(context.workflow()!=='EXCEL')return;
    const b=book(),selected=new Set(preferred||photoSelections()),single=targetSelect.value;
    targetSelect.replaceChildren();photoList.replaceChildren();
    b.sheets.forEach(s=>{const o=el('option',s.name);o.value=s.id;targetSelect.append(o);
      const source=s.sourceAssetIds?.length===1?assets.find(a=>a.id===s.sourceAssetIds[0]&&!a.archived_at&&['image/png','image/jpeg'].includes(a.media_type)):null;
      if(!source)return;const row=el('label','','check'),cb=el('input');cb.type='checkbox';cb.value=s.id;cb.checked=selected.has(s.id);row.append(cb,el('span',s.name+' ← '+source.original_name));photoList.append(row);cb.addEventListener('change',()=>{consent.checked=false;selectionSummary();});
    });
    targetSelect.value=b.sheets.some(s=>s.id===single)?single:b.activeSheetId;
    if(!photoList.children.length)photoList.append(el('p','사진으로 만들기에서 사진을 등록하고 사진별 시트로 준비하세요. 원본 연결이 해제되거나 삭제된 사진은 다시 선택해야 합니다.','muted'));
    const batch=targetMode.value==='photos';targetSelect.hidden=batch;photoList.hidden=!batch;sources.hidden=batch;
    targetHint.textContent=batch?`사진 한 장당 한 시트에 제안합니다. 한 요청에 최대 ${settings?.limits?.maxAssets??3}장, 합계 6MB입니다. 여러 장은 사진별로 AI를 호출하므로 사용량이 합산됩니다. 기존 셀은 제안을 적용할 때만 바뀝니다.`:'지정한 시트만 읽고 편집을 제안합니다. 다른 시트의 셀은 유지합니다.';
    updateControls();
  }
  async function preparePhotoSheets(ids){targetMode.value='photos';await refreshAssets();const chosen=ids.slice(0,settings?.limits?.maxAssets??3);refreshSheetTargets(chosen);message(runStatus,`${ids.length}개 사진 시트를 준비했습니다. 이번 요청 대상으로 ${chosen.length}개를 선택했습니다. 나머지는 이곳에서 다음 요청에 선택할 수 있습니다.`);prompt.value='각 원본 사진을 해당 시트의 표로 옮겨 주세요. 흐릿하거나 잘린 값은 추측하지 말고 위치와 다시 촬영할 방법을 설명해 주세요.';consent.checked=false;}

  runStatus.setAttribute('role','status');
  runStatus.setAttribute('aria-live','polite');
  historyBox.append(historyTitle,historyList);
  host.append(connection,intro,form,runStatus,runMeta,cancel,result,historyBox);
  cancel.hidden=true;
  [['필요한 조건부터 확인','이미 작성한 답은 다시 묻지 말고, 지금 작업에 꼭 필요한 모호한 조건부터 확인해 주세요.'],['현재 내용 다듬기','현재 자료와 직접 입력을 보존하면서 더 편리한 구성을 제안해 주세요. 추정한 부분은 구분해 주세요.'],['사진·자료 검토','선택한 원본 자료를 읽고 실제 값과 확인이 필요한 부분을 구분해 주세요.']].forEach(([title,value])=>examples.append(button(title,()=>{
    prompt.value=value;
    prompt.focus();
  })));
  function has(action){
    return context.caps().has('workspace.ai.'+action);
  }
  function updateControls(){
    const enabled=!disposed&&!pending&&context.active();
    start.disabled=!enabled||!has('run')||!settings?.available;
    cancel.disabled=!enabled||!has('cancel');
    if(selectedRun&&activeStatuses.has(selectedRun.status))start.disabled=true;
    result.querySelectorAll('[data-ai-apply]').forEach(node=>node.disabled=!enabled||!has('apply')||selectedRun?.status!=='READY'||!proposal?.ready);
    result.querySelectorAll('input,textarea').forEach(node=>node.disabled=pending||!context.active()||selectedRun?.status!=='READY'||(node.dataset.aiField==='sheet'&&!context.caps().has('workspace.sheet.update')));
    result.querySelectorAll('button:not([data-ai-apply]):not([data-ai-batch-apply])').forEach(node=>node.disabled=pending);
    sourceList.querySelectorAll('input').forEach(node=>node.disabled=!enabled||!has('run')||!context.caps().has('workspace.asset.download'));
    result.querySelectorAll('[data-ai-batch-apply]').forEach(node=>node.disabled=!enabled||!has('apply')||!context.caps().has('workspace.sheet.update')||selectedRun?.status!=='READY');
    targetMode.disabled=!enabled;targetSelect.disabled=!enabled;photoList.querySelectorAll('input').forEach(n=>n.disabled=!enabled||!has('run'));
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
  async function refreshAssets(){
    if(!context.caps().has('workspace.asset.list')){
      sourceList.replaceChildren(el('p','첨부 조회 권한이 없어 원본 자료를 선택할 수 없습니다.','muted'));
      return;
    }
    const checked=new Set([...sourceList.querySelectorAll('input:checked')].map(n=>n.value));
    assets=await request('/api/assets/selected',{method:'POST',body:{businessId:context.business(),projectId:context.id()||null,ids:context.currentInput().assetIds||[]}});
    sourceList.replaceChildren();
    if(!context.caps().has('workspace.asset.download'))sourceList.append(el('p','원본 전송에는 첨부 다운로드 권한이 필요합니다. 현재는 업무 답변만으로 AI 검토할 수 있습니다.','muted'));
    assets.filter(a=>!a.archived_at&&['image/png','image/jpeg','text/plain'].includes(a.media_type)).forEach(asset=>{
      const row=el('div','','ai-source-row'),l=el('label','','check'),cb=el('input');
      cb.type='checkbox';
      cb.value=asset.id;
      cb.checked=checked.has(asset.id);
      cb.addEventListener('change',()=>{
        if(cb.checked){
          try{
            const selected=[...sourceList.querySelectorAll('input:checked')].map(n=>n.value);
            if(selected.length>(settings?.limits?.maxAssets??3))throw new Error('AI 원본 선택 개수 한도입니다. 기존 선택을 해제하세요.');
            if(assets.filter(a=>selected.includes(a.id)).reduce((sum,a)=>sum+Number(a.byte_size||0),0)>(settings?.limits?.maxAssetBytes??6*1024*1024))throw new Error('AI 원본 합계 용량을 초과했습니다. 자료를 나누어 선택하세요.');
            context.linkAsset?.(asset.id);
          }
          catch(error){
            cb.checked=false;
            fail(error);
          }
        }
        selectionSummary();
      });
      l.append(cb,el('span',asset.original_name+' · '+fileSize(Number(asset.byte_size))));
      const link=el('a','원본 내려받아 대조');
      link.href=`/api/assets/${encodeURIComponent(asset.id)}/download`;
      link.className='button small quiet';
      if(!context.caps().has('workspace.asset.download'))link.hidden=true;
      row.append(l,link);
      sourceList.append(row);
      if(context.caps().has('workspace.asset.download')&&['image/png','image/jpeg'].includes(asset.media_type)){
        const detail=el('details','','source-preview'),title=el('summary','원본 사진 펼쳐서 대조'),picture=el('img');picture.alt=asset.original_name+' 원본';picture.loading='lazy';
        detail.addEventListener('toggle',()=>{if(detail.open&&!picture.hasAttribute('src'))picture.src=`/api/assets/${encodeURIComponent(asset.id)}/download`;});
        detail.append(title,picture);sourceList.append(detail);
      }
    });
    if(!sourceList.children.length)sourceList.append(el('p','이 작업에 연결된 AI용 사진·텍스트 자료가 없습니다. 자료 없이 답변만으로도 시작할 수 있습니다.','muted'));
    refreshSheetTargets();
    updateControls();
  }
  async function loadHistory(selectLatest=false){
    if(!context.id()||!has('read'))return;
    const list=await request(`/api/drafts/${encodeURIComponent(context.id())}/ai-runs`);
    historyList.replaceChildren();
    if(!list.length)historyList.append(el('p','아직 AI 작업이 없습니다.','muted'));
    list.forEach(run=>historyList.append(button(`${labels[run.status]||run.status} · ${whenText(run.createdAt)}`,()=>selectRun(run),'button small quiet')));
    if(selectLatest&&list.length&&!selectedRun)selectRun(list[0]);
  }
  function selectRun(run){
    selectedRun=run;
    proposal=run.proposal?copy(run.proposal):null;
    answers=new Map();
    lastRendered='';
    applyDraft=null;
    renderRun();
    schedule();
  }
  function renderRun(){
    if(!selectedRun)return;
    const run=selectedRun;
    message(runStatus,`${labels[run.status]||run.status} · ${run.phase||''}${run.message?' · '+run.message:''}`,run.status==='FAILED');
    const noGeneration=run.diagnostic?.generationRequests===0;
    const usage=run.inputTokens||run.outputTokens?`입력 ${run.inputTokens||0} / 출력 ${run.outputTokens||0} 토큰`:noGeneration?'생성 요청 전 중단 · AI 생성 요청 0건':activeStatuses.has(run.status)?'사용량은 응답을 받은 뒤 반영됩니다.':'토큰 사용량 미확인 · 과금이 없다는 의미는 아닙니다.';
    const sheetVersion=Number(run.baseSheetVersion)>=0?`표 버전 ${run.baseSheetVersion}`:'저장된 표 없음';
    runMeta.textContent=`${run.photoSheetIds?.length?'사진별 '+run.photoSheetIds.length+'개 시트':run.targetSheetName?'대상 시트: '+run.targetSheetName:''} · 요청 기준: 업무 버전 ${run.baseDraftVersion} / ${sheetVersion} · ${run.model||''} · ${usage}${run.toolNames?.length?' · 사용한 서버 기능: '+run.toolNames.join(', '):''}`;
    cancel.hidden=!activeStatuses.has(run.status);
    const signature=run.id+':'+run.status+':'+(run.sheetResults?.length||0);
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
    const changed=el('section','','ai-change-preview'),selection=el('div','','ai-apply-fields'),current=context.currentInput(),editor=el('details'),editorTitle=el('summary','제안을 직접 수정');
    editor.append(editorTitle);
    changed.append(el('h3','적용할 변경 미리보기'),el('p','선택한 항목만 서버에 반영합니다. 선택하지 않은 제목·답변·첨부와 현재 수동 입력은 바뀌지 않습니다.','muted'));
    [['title','초안 이름'],...fields].forEach(([key,label])=>{
      const next=key==='title'?p.draft.title:p.draft.answers[key]||'',before=key==='title'?current.title:current.answers[key]||'',row=el('div','','ai-diff'),l=el('label','','check'),cb=el('input');
      cb.type='checkbox';
      cb.value=key;
      cb.dataset.aiField=key;
      cb.checked=next!==before;
      l.append(cb,el('strong',label+(next===before?' · 변경 없음':'')));
      const old=el('p','현재: '+(before||'비어 있음'),'muted'),after=el('p','제안: '+(next||'비어 있음'));
      row.append(l,old,after);
      selection.append(row);
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
    if(p.sheet){
      const l=el('label','','check'),cb=el('input');
      cb.type='checkbox';
      cb.value='sheet';
      cb.dataset.aiField='sheet';
      cb.checked=context.caps().has('workspace.sheet.update');
      cb.disabled=!context.caps().has('workspace.sheet.update');
      l.append(cb,el('strong',`${selectedRun.targetSheetName||'대상 시트'} 변경 · ${p.sheet.columns.length}열 / ${p.sheet.rows.length}행`));
      const readPreview=el('div');
      readPreview.append(makePreviewTable(p.sheet));
      selection.append(l,el('p','이 항목은 요청에 지정한 시트의 구조와 데이터 전체를 제안 표로 바꿉니다. 다른 시트는 유지합니다. 원본과 비교하고 적용할 때만 선택하세요. 숫자·날짜·식별번호를 확인해 주세요.','notice warning'),readPreview);
      const tableDetails=el('details');
      tableDetails.append(el('summary','제안 표의 셀 직접 수정'),makePreviewTable(p.sheet,{
        editable:true,onChange:()=>{
          applyDraft=null;
          readPreview.replaceChildren(makePreviewTable(p.sheet));
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
      const text=p.questions.map(q=>{
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
        if(p.sheet)card.append(makePreviewTable(p.sheet));}
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
      applyDraft=null;await context.onApplied?.();lastRendered='';renderRun();await loadHistory();
    }finally{pending=false;context.setBusy?.(false);updateControls();}
  }

  form.addEventListener('submit',event=>{
    event.preventDefault();
    startRun().catch(fail);
  });
  async function startRun(){
    if(pending)return;
    if(!consent.checked)throw new Error('전송할 업무·자료 범위를 확인해 주세요.');
    if(!prompt.value.trim())throw new Error('무엇을 도와드릴지 짧게 입력하거나 위 예시를 선택해 주세요.');
    const assetIds=selectedSources(),batch=context.workflow()==='EXCEL'&&targetMode.value==='photos',photoSheetIds=batch?photoSelections():[],targetSheetId=context.workflow()==='EXCEL'&&!batch?targetSelect.value:null;
    if(batch&&!photoSheetIds.length)throw Error('사진별로 검토할 시트를 선택하세요.');
    if(new Set(assetIds).size!==assetIds.length)throw Error('같은 사진이 연결된 시트는 한 번에 하나만 선택하세요.');
    if(context.workflow()==='EXCEL'&&!batch&&!targetSheetId)throw Error('검토할 시트를 먼저 추가하세요.');
    if(assetIds.length>(settings?.limits?.maxAssets??3))throw new Error('서버 설정에서 허용한 원본 자료 개수를 넘었습니다.');
    if(assets.filter(a=>assetIds.includes(a.id)).reduce((total,a)=>total+Number(a.byte_size||0),0)>(settings?.limits?.maxAssetBytes??6*1024*1024))throw new Error('이번 원본 자료의 합계 크기가 6MB를 넘습니다. 자료를 나누어 선택하세요.');
    pending=true;
    context.setBusy?.(true);
    updateControls();
    try{
      const prepared=await context.prepareAi(assetIds);
      const body={
        prompt:prompt.value.trim(),assetIds,draftVersion:prepared.draftVersion,sheetVersion:prepared.sheetVersion,targetSheetId,photoSheetIds,previousRunId:!batch&&selectedRun&&['READY','APPLIED'].includes(selectedRun.status)&&selectedRun.proposal&&(context.workflow()!=='EXCEL'||(selectedRun.targetSheetId||'sheet1')===targetSheetId)?selectedRun.id:null
      };
      const fingerprint=JSON.stringify(body);
      if(!requestDraft||requestDraft.fingerprint!==fingerprint)requestDraft={
        fingerprint,key:crypto.randomUUID(),body
      };
      const run=await request(`/api/drafts/${encodeURIComponent(context.id())}/ai-runs`,{
        method:'POST',body:{
          ...requestDraft.body,requestKey:requestDraft.key
        },idempotencyKey:requestDraft.key
      });
      requestDraft=null;
      consent.checked=false;
      selectRun(run);
      await loadHistory();
    }
    finally{
      pending=false;
      context.setBusy?.(false);
      updateControls();
    }
  }
  async function applyProposal(selection,accept){
    if(pending)return;
    if(context.hasLocalChanges())throw new Error('저장 전 수동 입력·표가 있습니다. 그대로 보존했습니다. 먼저 저장하거나 표 변경을 취소한 뒤 AI 기준 버전을 확인하세요. 수동 저장으로 기준이 달라졌다면 새 AI 검토가 필요합니다.');
    const selected=[...selection.querySelectorAll('input[data-ai-field]:checked')].map(n=>n.value);
    if(!selected.length)throw new Error('적용할 항목을 선택하세요.');
    if(!accept.checked)throw new Error('추정·주의 사항과 적용 범위를 확인해 주세요.');
    const now=context.versions();
    if(now.draftVersion!==selectedRun.baseDraftVersion||now.sheetVersion!==selectedRun.baseSheetVersion)throw new Error('이 제안 이후 업무 또는 표가 저장되었습니다. 현재 입력은 유지했습니다. 최신 상태를 기준으로 다시 검토하세요.');
    if(!confirm(`선택한 ${selected.length}개 항목만 적용할까요? 선택한 표는 표 전체를 교체합니다. 적용되지 않은 수동 입력은 변경하지 않습니다.`))return;
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
      await context.onApplied?.();
      lastRendered='';
      renderRun();
      await loadHistory();
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
        signal:pollAbort.signal
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
    init,refreshAssets,refreshSheetTargets:()=>{targetSelect.value=book().activeSheetId;refreshSheetTargets();},preparePhotoSheets,attachDraft:()=>loadHistory(true),refreshControls:updateControls,activeRun:()=>selectedRun&&activeStatuses.has(selectedRun.status),isPending:()=>pending,destroy(){
      disposed=true;
      clearTimeout(pollTimer);
      pollAbort?.abort();
    },notifyVersion(){
      if(selectedRun?.status==='READY')message(runStatus,'AI 제안이 준비되어 있습니다. 적용 전 현재 기준 버전·수동 입력 변경 여부를 다시 확인합니다.');
    }
  };
}

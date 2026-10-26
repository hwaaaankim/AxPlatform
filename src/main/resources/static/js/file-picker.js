import {el,button,message} from './ui.js?v=0.10.1';

const instances=new WeakMap();
export const FILE_BYTES=10*1024*1024;
export function fileSize(bytes){return bytes<1024?bytes+' B':bytes<1024*1024?(bytes/1024).toFixed(1)+' KB':(bytes/1024/1024).toFixed(1)+' MB';}
const identity=f=>[f.name,f.size,f.lastModified,f.type].join('\u0000');
export function enhanceFileInput(input,options={}){
  if(instances.has(input))return instances.get(input);
  const multiple=options.multiple??input.multiple,maxFiles=multiple?(options.maxFiles||10):1,maxBytes=options.maxBytes||FILE_BYTES;
  const preserveOverflow=multiple&&options.preserveOverflow===true;
  const positive=(value,fallback)=>Number.isSafeInteger(value)&&value>0?value:fallback;
  const batchSize=preserveOverflow?Math.min(positive(options.batchSize,maxFiles),1000):maxFiles;
  const queueLimit=preserveOverflow?Math.min(Math.max(batchSize,positive(options.maxQueuedFiles,200)),1000):maxFiles;
  input.multiple=multiple;
  const extensions=(input.accept||'').split(',').map(s=>s.trim().toLowerCase()).filter(Boolean);
  const host=el('div','','file-picker'),drop=el('div','','file-drop'),heading=el('div','','file-drop-copy');
  const select=button(multiple?'파일 선택 · 추가':'파일 선택',()=>{if(!blocked())input.click();},'button primary');
  const clear=button('전체 비우기',()=>api.clear(),'button quiet small');
  const count=el('span','','file-count'),list=el('ul','','file-selection-list'),status=el('p','','help-line file-picker-status');
  const title=el('strong',options.label||input.getAttribute('aria-label')||'파일 선택');
  const rules=el('p',`${multiple?(preserveOverflow?'한 번에 최대 '+batchSize+'개씩 등록 · 대기 목록 최대 '+queueLimit+'개':'여러 개 선택 · 대기 목록 최대 '+maxFiles+'개'):'1개만 선택 가능'} · 파일당 ${fileSize(maxBytes)}`,'file-rules');
  const hint=el('small',(extensions.length?extensions.join(' / ').toUpperCase()+' · ':'')+(preserveOverflow?'남은 파일은 이 화면의 대기 목록에 유지됩니다. 새로고침·종료하면 등록 전 파일은 다시 선택해야 합니다.':multiple?'추가 선택하거나 이곳에 끌어 놓으세요.':'새 파일을 고르면 현재 선택을 바꿉니다.'));
  const rejectedBox=el('details','','file-rejected'),rejectedTitle=el('summary'),rejectedList=el('ul');rejectedBox.append(rejectedTitle,rejectedList);rejectedBox.hidden=true;
  heading.append(title,rules,hint);drop.append(el('span','↑','file-drop-icon'),heading,select);
  const tools=el('div','','file-selection-tools');tools.append(count,clear);
  list.setAttribute('aria-label','업로드 전 선택한 파일');status.setAttribute('role','status');status.setAttribute('aria-live','polite');
  input.before(host);host.append(input,drop,tools,list,status,rejectedBox);input.hidden=true;input.tabIndex=-1;
  let entries=[],uploading=false,disabled=false,lastRejected=[];
  const blocked=()=>uploading||disabled||input.disabled||!!input.closest('fieldset:disabled');
  function changed(){input.dispatchEvent(new CustomEvent('ax:files-change',{bubbles:true,detail:{count:entries.length}}));}
  function render(){
    select.disabled=blocked();clear.disabled=blocked()||!entries.length;
    select.textContent=multiple?'파일 선택 · 추가':entries.length?'파일 바꾸기':'파일 선택';
    host.classList.toggle('is-disabled',blocked());host.setAttribute('aria-busy',String(uploading));
    count.textContent=preserveOverflow?`대기 ${entries.length} / ${queueLimit}개 · 다음 등록 최대 ${Math.min(entries.length,batchSize)}개${entries.length?' · '+fileSize(entries.reduce((sum,e)=>sum+e.file.size,0)):''}`:`선택 ${entries.length} / ${maxFiles}개${entries.length?' · '+fileSize(entries.reduce((sum,e)=>sum+e.file.size,0)):''}`;
    list.replaceChildren();
    if(!entries.length){const empty=el('li','선택한 파일이 없습니다. 파일을 선택하면 이름·용량과 제거 버튼이 표시됩니다.','file-selection-empty');list.append(empty);}
    for(const entry of entries){
      const row=el('li','','file-selection-row'),info=el('div','','file-selection-info');
      const name=el('strong',entry.file.name);name.title=entry.file.name;
      info.append(name,el('small',fileSize(entry.file.size)));
      const state=el('span',entry.state==='sending'?'업로드 중':entry.state==='stored'?'원본 등록 완료':entry.state==='error'?'실패 · 다시 시도 가능':'등록 대기','file-state '+entry.state);
      if(entry.error)info.append(el('span',entry.error,'file-error'));
      const remove=button('×',()=>api.remove(entry.file),'file-remove');
      remove.setAttribute('aria-label',entry.file.name+' 선택 해제');remove.title='이 파일만 선택 해제';remove.disabled=blocked();
      const ext=entry.file.name.split('.').pop().slice(0,5).toUpperCase();
      row.append(el('span',ext,'file-type'),info,state,remove);list.append(row);
    }
  }
  function add(files){
    if(blocked())return;
    const values=Array.from(files||[]),errors=[],rejected=[];
    const reject=(file,reason)=>{errors.push(file.name+': '+reason);rejected.push({fileName:file.name,reason});};
    if(!multiple&&values.length>1){message(status,'이 항목은 파일 1개만 선택할 수 있습니다. 기존 선택은 유지했습니다.',true);return;}
    let next=multiple?[...entries]:[];
    for(const file of values){
      const ext='.'+file.name.split('.').pop().toLowerCase();
      const accepted=!extensions.length||extensions.some(t=>t.startsWith('.')?t===ext:t.endsWith('/*')?file.type.startsWith(t.slice(0,-1)):file.type===t);
      if(!accepted){reject(file,'허용하지 않는 파일 형식입니다.');continue;}
      if(!file.size||file.size>maxBytes){reject(file,'빈 파일은 제외하며 파일당 '+fileSize(maxBytes)+'까지 가능합니다.');continue;}
      if(next.some(e=>identity(e.file)===identity(file))){reject(file,'이미 선택한 파일입니다.');continue;}
      if(next.length>=queueLimit){reject(file,'대기 목록 최대 '+queueLimit+'개에 도달했습니다. 먼저 대기 파일을 등록한 뒤 이 파일을 추가로 선택하세요.');continue;}
      next.push({file,state:'waiting',error:''});
    }
    // An invalid replacement never discards a valid selected file.
    if(multiple||next.length)entries=next;
    lastRejected=rejected;rejectedList.replaceChildren();rejectedBox.hidden=!rejected.length;rejectedTitle.textContent=`선택하지 못한 파일 ${rejected.length}개 · 이름과 이유 확인`;for(const item of rejected)rejectedList.append(el('li',item.fileName+': '+item.reason));
    render();changed();message(status,(preserveOverflow?`${entries.length}개를 대기 목록에 보관했습니다. 한 번에 최대 ${batchSize}개씩 등록합니다.`:errors.join('\n')||`${entries.length}개를 선택했습니다. 아직 서버에 등록되지 않았습니다.`)+(preserveOverflow&&rejected.length?` 선택하지 못한 ${rejected.length}개는 아래 이름과 이유를 확인하세요.`:''),!!errors.length);
    return {accepted:entries.length,rejected:[...lastRejected]};
  }
  const api={host,input,files:()=>entries.map(e=>e.file),isUploading:()=>uploading,rejected:()=>lastRejected.map(item=>({...item})),batchSize:()=>batchSize,nextBatchFiles:()=>entries.slice(0,preserveOverflow?batchSize:entries.length).map(e=>e.file),
    add,remove(file){if(blocked())return;entries=entries.filter(e=>e.file!==file);render();changed();message(status,'선택 목록에서 제거했습니다. 저장된 파일은 변경하지 않았습니다.');},
    clear(){if(blocked())return false;entries=[];lastRejected=[];rejectedBox.hidden=true;rejectedList.replaceChildren();input.value='';render();changed();message(status,'선택 목록을 비웠습니다.');return true;},
    setDisabled(value){disabled=!!value;render();},
    setFileState(file,state,note){const entry=entries.find(e=>e.file===file);if(!entry||!["waiting","sending","stored","error"].includes(state))return;entry.state=state;entry.error=state==="error"?note:"";render();message(status,note||"",state==="error");},
    async upload(send,{limit=preserveOverflow?batchSize:entries.length}={}){
      if(blocked())throw new Error('파일 처리 중이거나 업로드 권한이 없습니다.');
      if(!entries.length)throw new Error('등록할 파일을 먼저 선택하세요.');
      if(!Number.isSafeInteger(limit)||limit<0)throw new Error('이번 등록 파일 수는 0 이상의 정수로 지정하세요.');
      const capacity=Math.min(limit,preserveOverflow?batchSize:entries.length),batch=entries.slice(0,capacity);
      if(!capacity){message(status,'이번 작업에 연결할 여유가 없습니다. 현재 자료 처리를 마치거나 연결을 정리한 뒤 계속 등록하세요. 대기 파일은 유지했습니다.',true);return {succeeded:[],failed:[],remaining:entries.length,attempted:0,deferred:entries.length,hasMore:true,paused:true};}
      uploading=true;const succeeded=[],failed=[];let attempted=0;render();
      try{
        for(const entry of batch){
          attempted++;entry.state='sending';entry.error='';render();
          try{
            const result=await send(entry.file);
            succeeded.push({file:entry.file,result});entries=entries.filter(e=>e!==entry);
          }catch(error){
            entry.state='error';entry.error=error.message||'파일을 등록하지 못했습니다.';failed.push({file:entry.file,error});
            if(!error.status)entry.error+=' 응답이 끊겼다면 등록 목록을 확인한 뒤 재시도하세요.';
            if([401,403,409,429].includes(error.status)||!error.status)break;
          }
          render();
        }
      }finally{uploading=false;input.value='';render();changed();}
      message(status,`${succeeded.length}개 등록 완료${entries.length?' · 남은 '+entries.length+'개는 대기 목록에서 확인하세요.':''}`,failed.length>0);
      return {succeeded,failed,remaining:entries.length,attempted,deferred:Math.max(0,entries.length-failed.length),hasMore:entries.length>0,paused:false};
    }
  };
  instances.set(input,api);
  input.addEventListener('change',()=>{const files=Array.from(input.files||[]);input.value='';if(files.length)add(files);});
  drop.addEventListener('dragover',e=>{if(blocked())return;e.preventDefault();drop.classList.add('drag-over');});
  drop.addEventListener('dragleave',e=>{if(!drop.contains(e.relatedTarget))drop.classList.remove('drag-over');});
  drop.addEventListener('drop',e=>{e.preventDefault();drop.classList.remove('drag-over');if(!blocked())add(e.dataTransfer?.files);});
  input.form?.addEventListener('reset',e=>setTimeout(()=>{if(!e.defaultPrevented)api.clear();},0));
  new MutationObserver(render).observe(input,{attributes:true,attributeFilter:['disabled']});
  render();return api;
}

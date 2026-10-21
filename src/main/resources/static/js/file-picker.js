import {el,button,message} from './ui.js?v=0.6.0';

const instances=new WeakMap();
export const FILE_BYTES=10*1024*1024;
export function fileSize(bytes){return bytes<1024?bytes+' B':bytes<1024*1024?(bytes/1024).toFixed(1)+' KB':(bytes/1024/1024).toFixed(1)+' MB';}
const identity=f=>[f.name,f.size,f.lastModified,f.type].join('\u0000');
export function enhanceFileInput(input,options={}){
  if(instances.has(input))return instances.get(input);
  const multiple=options.multiple??input.multiple,maxFiles=multiple?(options.maxFiles||10):1,maxBytes=options.maxBytes||FILE_BYTES;
  input.multiple=multiple;
  const extensions=(input.accept||'').split(',').map(s=>s.trim().toLowerCase()).filter(Boolean);
  const host=el('div','','file-picker'),drop=el('div','','file-drop'),heading=el('div','','file-drop-copy');
  const select=button(multiple?'파일 선택 · 추가':'파일 선택',()=>{if(!blocked())input.click();},'button primary');
  const clear=button('전체 비우기',()=>api.clear(),'button quiet small');
  const count=el('span','','file-count'),list=el('ul','','file-selection-list'),status=el('p','','help-line file-picker-status');
  const title=el('strong',options.label||input.getAttribute('aria-label')||'파일 선택');
  const rules=el('p',`${multiple?'여러 개 선택 · 대기 목록 최대 '+maxFiles+'개':'1개만 선택 가능'} · 파일당 ${fileSize(maxBytes)}`,'file-rules');
  const hint=el('small',(extensions.length?extensions.join(' / ').toUpperCase()+' · ':'')+(multiple?'추가 선택하거나 이곳에 끌어 놓으세요.':'새 파일을 고르면 현재 선택을 바꿉니다.'));
  heading.append(title,rules,hint);drop.append(el('span','↑','file-drop-icon'),heading,select);
  const tools=el('div','','file-selection-tools');tools.append(count,clear);
  list.setAttribute('aria-label','업로드 전 선택한 파일');status.setAttribute('role','status');status.setAttribute('aria-live','polite');
  input.before(host);host.append(input,drop,tools,list,status);input.hidden=true;input.tabIndex=-1;
  let entries=[],uploading=false,disabled=false;
  const blocked=()=>uploading||disabled||input.disabled||!!input.closest('fieldset:disabled');
  function changed(){input.dispatchEvent(new CustomEvent('ax:files-change',{bubbles:true,detail:{count:entries.length}}));}
  function render(){
    select.disabled=blocked();clear.disabled=blocked()||!entries.length;
    select.textContent=multiple?'파일 선택 · 추가':entries.length?'파일 바꾸기':'파일 선택';
    host.classList.toggle('is-disabled',blocked());host.setAttribute('aria-busy',String(uploading));
    count.textContent=`선택 ${entries.length} / ${maxFiles}개${entries.length?' · '+fileSize(entries.reduce((sum,e)=>sum+e.file.size,0)):''}`;
    list.replaceChildren();
    if(!entries.length){const empty=el('li','선택한 파일이 없습니다. 파일을 선택하면 이름·용량과 제거 버튼이 표시됩니다.','file-selection-empty');list.append(empty);}
    for(const entry of entries){
      const row=el('li','','file-selection-row'),info=el('div','','file-selection-info');
      const name=el('strong',entry.file.name);name.title=entry.file.name;
      info.append(name,el('small',fileSize(entry.file.size)));
      const state=el('span',entry.state==='sending'?'업로드 중':entry.state==='error'?'실패 · 다시 시도 가능':'등록 대기','file-state '+entry.state);
      if(entry.error)info.append(el('span',entry.error,'file-error'));
      const remove=button('×',()=>api.remove(entry.file),'file-remove');
      remove.setAttribute('aria-label',entry.file.name+' 선택 해제');remove.title='이 파일만 선택 해제';remove.disabled=blocked();
      const ext=entry.file.name.split('.').pop().slice(0,5).toUpperCase();
      row.append(el('span',ext,'file-type'),info,state,remove);list.append(row);
    }
  }
  function add(files){
    if(blocked())return;
    const values=Array.from(files||[]),errors=[];
    if(!multiple&&values.length>1){message(status,'이 항목은 파일 1개만 선택할 수 있습니다. 기존 선택은 유지했습니다.',true);return;}
    let next=multiple?[...entries]:[];
    for(const file of values){
      const ext='.'+file.name.split('.').pop().toLowerCase();
      const accepted=!extensions.length||extensions.some(t=>t.startsWith('.')?t===ext:t.endsWith('/*')?file.type.startsWith(t.slice(0,-1)):file.type===t);
      if(!accepted){errors.push(file.name+': 허용하지 않는 파일 형식입니다.');continue;}
      if(!file.size||file.size>maxBytes){errors.push(file.name+': 빈 파일은 제외하며 파일당 '+fileSize(maxBytes)+'까지 가능합니다.');continue;}
      if(next.some(e=>identity(e.file)===identity(file))){errors.push(file.name+': 이미 선택한 파일입니다.');continue;}
      if(next.length>=maxFiles){errors.push('대기 목록은 최대 '+maxFiles+'개입니다. 먼저 등록하거나 일부를 제거하세요.');break;}
      next.push({file,state:'waiting',error:''});
    }
    // An invalid replacement never discards a valid selected file.
    if(multiple||next.length)entries=next;
    render();changed();message(status,errors.join('\n')||`${entries.length}개를 선택했습니다. 아직 서버에 등록되지 않았습니다.`,!!errors.length);
  }
  const api={host,input,files:()=>entries.map(e=>e.file),isUploading:()=>uploading,
    add,remove(file){if(blocked())return;entries=entries.filter(e=>e.file!==file);render();changed();message(status,'선택 목록에서 제거했습니다. 저장된 파일은 변경하지 않았습니다.');},
    clear(){if(blocked())return false;entries=[];input.value='';render();changed();message(status,'선택 목록을 비웠습니다.');return true;},
    setDisabled(value){disabled=!!value;render();},
    async upload(send){
      if(blocked())throw new Error('파일 처리 중이거나 업로드 권한이 없습니다.');
      if(!entries.length)throw new Error('등록할 파일을 먼저 선택하세요.');
      uploading=true;const succeeded=[],failed=[];render();
      try{
        for(const entry of [...entries]){
          entry.state='sending';entry.error='';render();
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
      return {succeeded,failed,remaining:entries.length};
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

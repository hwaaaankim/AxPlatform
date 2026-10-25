import {replaceAsset,locallyMissing,localLabel} from './asset-replace.js?v=0.9.1';
import {request} from './request.js?v=0.9.1';
import {el,button,message} from './ui.js?v=0.9.1';
import {fileSize} from './file-picker.js?v=0.9.1';

// Saved assets use the existing, reversible archive API. Selection removal in
// an upload picker is intentionally a separate operation.
export function createAssetLibrary(host,context){
  let rows=[],selected=new Set(),scope='',sequence=0,pending=false,loaded=false;
  const can=action=>context.caps().has('workspace.asset.'+action);
  const toolbar=el('div','','asset-library-tools'),search=el('input'),archived=el('input'),count=el('span','','muted');
  search.type='search';search.placeholder='등록 파일 이름 검색';search.setAttribute('aria-label','등록 파일 이름 검색');
  archived.type='checkbox';const archivedLabel=el('label','','check');archivedLabel.append(archived,el('span','삭제한 파일 보기'));
  const list=el('div','','asset-saved-list'),status=el('p','','help-line');status.setAttribute('role','status');
  const reset=button('검색 초기화',()=>{search.value='';archived.checked=false;selected.clear();render();},'button small');
  const refresh=button('목록 새로고침',()=>load(),'button small');
  const selectAll=button('표시 항목 선택',()=>{visible().filter(a=>!a.archived_at).forEach(a=>selected.add(String(a.id)));render();},'button small');
  const deselect=button('선택 해제',()=>{selected.clear();render();},'button small quiet');
  const removeSelected=button('선택 삭제',()=>mutate(rows.filter(a=>!a.archived_at&&selected.has(String(a.id))),true),'button small danger');
  const removeAll=button('표시 파일 전체 삭제',()=>mutate(visible().filter(a=>!a.archived_at),true),'button small danger');
  const batch=el('div','','actions asset-batch-actions');batch.append(count,selectAll,deselect,removeSelected,removeAll);
  toolbar.append(search,reset,refresh,archivedLabel);
  host.append(el('h3','등록된 파일'),toolbar,batch,list,status,el('small','최신 200개 범위에서 조회합니다. 삭제는 복원 가능한 보관 처리이며, 영구 삭제가 아닙니다.','muted'));
  const visible=()=>rows.filter(a=>(archived.checked||!a.archived_at)&&a.original_name.toLocaleLowerCase().includes(search.value.trim().toLocaleLowerCase()));
  const blocked=()=>pending||!loaded||!context.active();
  function render(){
    const items=visible();list.replaceChildren();
    selected=new Set([...selected].filter(id=>rows.some(a=>String(a.id)===id&&!a.archived_at)));
    count.textContent=`등록 ${rows.filter(a=>!a.archived_at).length}개 · 표시 ${items.length}개 · 선택 ${selected.size}개`;
    [search,archived,reset,refresh].forEach(c=>c.disabled=pending);
    selectAll.disabled=blocked()||!can('archive')||!items.some(a=>!a.archived_at);
    deselect.disabled=pending||!selected.size;
    removeSelected.disabled=blocked()||!can('archive')||!selected.size;
    removeAll.disabled=blocked()||!can('archive')||!items.some(a=>!a.archived_at);
    for(const a of items){
      const row=el('div','','asset-item saved-file-row'),cb=el('input'),info=el('div','','grow'),actions=el('div','','actions');
      cb.type='checkbox';cb.checked=selected.has(String(a.id));cb.disabled=blocked()||!!a.archived_at||!can('archive');cb.setAttribute('aria-label',a.original_name+' 삭제 대상 선택');
      cb.addEventListener('change',()=>{cb.checked?selected.add(String(a.id)):selected.delete(String(a.id));render();});
      const name=el('strong',a.original_name);name.title=a.original_name;
      info.append(name,el('small',fileSize(Number(a.byte_size))+' · '+(a.archived_at?'삭제됨 · 복원 가능':'등록 완료')));
      info.append(el('small',localLabel(a),locallyMissing(a)?'missing-file-badge':'muted'));
      if(!a.archived_at&&can('upload')&&can('archive')){const replace=button('파일 교체',()=>replaceAsset(a,{onReplaced:load}),'button small');replace.disabled=blocked();actions.append(replace);}
      if(!a.archived_at&&!locallyMissing(a)&&can('download')){const link=el('a','다운로드','button small');link.href='/api/assets/'+encodeURIComponent(a.id)+'/download';actions.append(link);}
      if(can(a.archived_at?'restore':'archive')){const b=button(a.archived_at?'복원':'×',()=>mutate([a],!a.archived_at),a.archived_at?'button small':'file-remove');b.setAttribute('aria-label',a.original_name+(a.archived_at?' 복원':' 삭제'));b.title=a.archived_at?'복원':'파일 삭제 · 복원 가능';b.disabled=blocked();actions.append(b);}
      row.append(cb,info,actions);list.append(row);
    }
    if(!items.length)list.append(el('p',!loaded?'목록을 불러오세요.':search.value?'검색된 파일이 없습니다. 검색 초기화를 눌러 보세요.':archived.checked?'등록된 파일이 없습니다.':'등록된 파일이 없습니다. 위에서 파일을 선택해 등록하세요.','empty-state'));
  }
  async function load(){
    if(pending)return;
    const next=String(context.business()||'');if(scope!==next){scope=next;rows=[];selected.clear();search.value='';archived.checked=false;loaded=false;}
    const token=++sequence;
    if(!context.active()||!can('list')){rows=[];loaded=false;render();message(status,'현재 사업에서 파일 목록을 조회할 수 없습니다.');return;}
    message(status,'등록 파일을 조회하고 있습니다.');host.setAttribute('aria-busy','true');
    try{const result=await request('/api/assets?businessId='+encodeURIComponent(next));if(token!==sequence||next!==String(context.business()))return;rows=result;loaded=true;render();message(status,rows.length===200?'최신 200개를 표시합니다. 전체 삭제는 현재 표시된 파일에만 적용됩니다.':'');}
    catch(e){if(token===sequence){loaded=false;render();message(status,'목록 갱신 실패: '+e.message+' 목록 새로고침으로 다시 확인하세요.',true);}}
    finally{if(token===sequence)host.setAttribute('aria-busy','false');}
  }
  async function mutate(items,archive){
    if(blocked()||!items.length||!can(archive?'archive':'restore'))return;
    if(!confirm(`${items.length}개 파일을 ${archive?'삭제(보관)':'복원'}할까요?\n${archive?'이 파일을 참조하는 업무에서 다운로드·AI 사용이 중단됩니다. 삭제한 파일 보기에서 복원할 수 있습니다.':'같은 사업에서 다시 사용할 수 있습니다.'}`))return;
    pending=true;++sequence;context.setBusy?.(true);render();let done=0,errors=[];
    try{for(const a of items){try{await request('/api/assets/'+encodeURIComponent(a.id)+'/archive',{method:'POST',body:{archived:archive,version:a.version}});done++;selected.delete(String(a.id));}catch(e){errors.push(a.original_name+': '+e.message);if([401,403,409,429].includes(e.status)||!e.status)break;}}}
    finally{pending=false;context.setBusy?.(false);await load();message(status,`${done} / ${items.length}개 ${archive?'삭제':'복원'} 완료${errors.length?' · '+errors.join(' / ')+' 나머지 항목은 목록에서 확인 후 다시 시도하세요.':''}${!loaded?' · 등록 목록을 갱신하지 못했습니다. 목록 새로고침으로 결과를 다시 확인하세요.':''}`,!!errors.length||!loaded);}
  }
  search.addEventListener('input',render);archived.addEventListener('change',()=>{selected.clear();render();});
  render();return{load,isBusy:()=>pending};
}

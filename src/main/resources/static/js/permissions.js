import {
  request
}
from './request.js?v=0.9.1';
import {
  el,button,message,busy,bool,whenText
}
from './ui.js?v=0.9.1';
import {
  TIERS,available,dependencyAdd,dependencyRemove
}
from './policy-model.js?v=0.9.1';
const status=document.getElementById('policy-status'),reason=document.getElementById('policy-reason'),review=document.getElementById('policy-review'),editor=document.getElementById('catalog-editor');
const catalogSelected=new Set();
let snapshot,grants,dirty=false,sourceVersion=null,reviewBody=null,editing=null,cacheKey='';
const index=()=>new Map(snapshot.catalog.map(a=>[a.code,a]));
const label=code=>index().get(code)?.name||code;
function remember(preserveSource=false) {
  dirty=true;
  if(!preserveSource)sourceVersion=null;
  reviewBody=null;
  if(cacheKey)sessionStorage.setItem(cacheKey,JSON.stringify( {
    revision:snapshot.revision,grants,reason:reason.value,sourceVersion
  }
  ));
}
function add(tier,codes) {
  try {
    const result=dependencyAdd(snapshot.catalog,grants[tier],codes);
    if(result.dependencies.length&&!confirm('함께 필요한 기능을 초안에 추가할까요?\n'+result.dependencies.map(label).join('\n')))return;
    grants[tier]=result.next;
    remember();
    renderBoards();
    message(status,tier+' 초안을 수정했습니다. 아직 적용하지 않았습니다.');
  }
  catch(e) {
    message(status,e.message,true);
  }
}
function remove(tier,code) {
  const result=dependencyRemove(snapshot.catalog,grants[tier],code);
  if(result.removed.length>1&&!confirm('함께 의존하는 기능도 초안에서 제거할까요?\n'+result.removed.map(label).join('\n')))return;
  grants[tier]=result.next;
  remember();
  renderBoards();
}
function renderBoards() {
  const host=document.getElementById('tier-boards');
  host.replaceChildren();
  for(const tier of TIERS) {
    const box=el('section','','tier-board');
    box.setAttribute('aria-label',tier+' 배정');
    box.dataset.tier=tier;
    const heading=el('div','','tier-title');
    heading.append(el('h3',tier),el('span',grants[tier].length+'개','badge'));
    box.append(heading);
    for(const [i,code] of grants[tier].entries()) {
      const item=el('div','','action-item');
      item.append(el('strong',label(code)),el('small',code,'code'));
      const controls=el('div','','actions');
      const move=delta=> {
        const j=i+delta;
        if(j<0||j>=grants[tier].length)return;
        [grants[tier][i],grants[tier][j]]=[grants[tier][j],grants[tier][i]];
        remember();
        renderBoards();
      }
      ;
      controls.append(button('↑',()=>move(-1)),button('↓',()=>move(1)),button('제거',()=>remove(tier,code)));
      item.append(controls);
      box.append(item);
    }
    if(!grants[tier].length)box.append(el('p','미배정 · 이 등급은 해당 기능을 사용할 수 없습니다.','muted'));
    box.addEventListener('dragover',e=> {
      e.preventDefault();
      box.classList.add('drag-over');
    }
    );
    box.addEventListener('dragleave',()=>box.classList.remove('drag-over'));
    box.addEventListener('drop',e=> {
      e.preventDefault();
      box.classList.remove('drag-over');
      try {
        const data=JSON.parse(e.dataTransfer.getData('application/x-ax-actions'));
        if(Array.isArray(data.codes)&&data.codes.every(x=>typeof x==='string'))add(tier,data.codes);
      }
      catch {
        message(status,'끌어 놓은 항목을 확인해 주세요.',true);
      }
    }
    );
    host.append(box);
  }
}
function drag(node,codes) {
  node.draggable=true;
  node.addEventListener('dragstart',e=>e.dataTransfer.setData('application/x-ax-actions',JSON.stringify( {
    codes
  }
  )));
}
function renderCatalog() {
  const host=document.getElementById('catalog-list');
  host.replaceChildren();
  for(const code of catalogSelected)if(!snapshot.catalog.some(a=>a.code===code&&available(a)))catalogSelected.delete(code);
  const q=document.getElementById('catalog-search').value.trim().toLowerCase(),filter=document.getElementById('catalog-state').value;
  for(const group of snapshot.groups) {
    const rows=snapshot.catalog.filter(a=>a.group_code===group.code&&(filter==='ALL'||a.status===filter)&&(`${a.name} ${a.code} ${group.name}`).toLowerCase().includes(q));
    if(!rows.length&&q)continue;
    const block=el('section','','catalog-group'),h=el('h3'),left=el('span',group.name),tools=el('span','','actions');
    const groupCodes=snapshot.catalog.filter(a=>a.group_code===group.code&&available(a)).map(a=>a.code); if(groupCodes.length)drag(left,groupCodes);
    tools.append(button('그룹 배정',()=>add(document.getElementById('assign-tier').value,groupCodes)),button('수정',()=>openEditor('group',group)),button('↑',()=>moveCatalog('group',group,-1)),button('↓',()=>moveCatalog('group',group,1)),button('삭제',()=>deleteGroup(group)));
    h.append(left,tools);
    block.append(h,el('p',`${group.code} · ${group.status}`,'code muted'));
    for(const a of rows) {
      const row=el('div','','catalog-action'),cb=document.createElement('input');
      cb.type='checkbox';
      cb.value=a.code;
      cb.disabled=!available(a);
      cb.setAttribute('aria-label',a.name+' 선택');
      cb.className='assign-checkbox';
      cb.checked=catalogSelected.has(a.code);
      cb.addEventListener('change',()=>{cb.checked?catalogSelected.add(a.code):catalogSelected.delete(a.code);catalogSelectionStatus();});
      const info=el('label');
      info.append(el('strong',a.name),el('small',a.code,'code'));
      const badge=el('span',available(a)?'사용 가능':!a.implemented?'준비 중 · 미연결':a.status==='ACTIVE'?'그룹 중지 / 관리자 전용':a.status,'badge '+(available(a)?'active':'pending'));
      const controls=el('div','','actions');
      controls.append(button('편집',()=>openEditor('action',a)),button('↑',()=>moveCatalog('action',a,-1)),button('↓',()=>moveCatalog('action',a,1)),button('삭제 영향',()=>deleteAction(a)));
      row.append(cb,info,badge,controls);
      if(available(a))drag(info,[a.code]);
      block.append(row);
    }
    host.append(block);
  }
  if(!host.querySelector('.catalog-action'))host.append(el('p','조건에 맞는 기능이 없습니다. 검색 초기화로 전체 목록을 확인하세요.','empty-state'));
  catalogSelectionStatus();
}
function catalogSelectionStatus(){document.getElementById('catalog-selected-count').textContent='선택 '+catalogSelected.size+'개';document.getElementById('assign-selected').disabled=!catalogSelected.size;document.getElementById('catalog-clear-selection').disabled=!catalogSelected.size;}
async function load(recover=false) {
  snapshot=await request('/api/admin/permissions');
  catalogSelected.clear();
  grants=structuredClone(snapshot.grants);
  dirty=false;
  sourceVersion=null;
  reviewBody=null;
  reason.value='';
  document.getElementById('policy-revision').textContent=`현재 정책 #${snapshot.revision}`;
  if(recover&&cacheKey) {
    try {
      const draft=JSON.parse(sessionStorage.getItem(cacheKey)||'null');
      if(draft&&draft.revision===snapshot.revision&&TIERS.every(t=>Array.isArray(draft.grants?.[t]))&&confirm('이 브라우저에 남은 미적용 권한 초안을 복구할까요?')) {
        grants=draft.grants;
        reason.value=draft.reason||'';sourceVersion=draft.sourceVersion??null;
        dirty=true;
      }
      else if(draft)sessionStorage.removeItem(cacheKey);
    }
    catch {
      sessionStorage.removeItem(cacheKey);
    }
  }
  renderBoards();
  renderCatalog();
  await history();
}
reason.addEventListener('input',()=>remember(true));
document.getElementById('catalog-search').addEventListener('input',renderCatalog);
document.getElementById('catalog-state').addEventListener('change',renderCatalog);
document.getElementById('assign-selected').addEventListener('click',()=>add(document.getElementById('assign-tier').value,[...catalogSelected]));
document.getElementById('catalog-reset').addEventListener('click',()=>{document.getElementById('catalog-search').value='';document.getElementById('catalog-state').value='ALL';catalogSelected.clear();renderCatalog();});
document.getElementById('catalog-select-visible').addEventListener('click',()=>{document.querySelectorAll('.assign-checkbox:not(:disabled)').forEach(cb=>catalogSelected.add(cb.value));renderCatalog();});
document.getElementById('catalog-clear-selection').addEventListener('click',()=>{catalogSelected.clear();renderCatalog();});
document.getElementById('copy-tier').addEventListener('click',()=> {
  const from=document.getElementById('copy-from').value,to=document.getElementById('copy-to').value;
  if(from===to)return;
  if(confirm(from+'의 배정으로 '+to+' 초안을 바꿀까요? 두 등급은 이후에도 독립적으로 편집됩니다.')) {
    grants[to]=[...grants[from]];
    remember();
    renderBoards();
  }
}
);
document.getElementById('policy-reload').addEventListener('click',()=> {
  if(!dirty||confirm('미적용 초안을 버리고 현재 정책을 불러올까요?')) {
    if(cacheKey)sessionStorage.removeItem(cacheKey);
    load().catch(e=>message(status,e.message,true));
  }
}
);
async function preview() {
  try {
    if(!reason.value.trim())throw new Error('변경 사유를 입력해 주세요.');
    reviewBody= {
      baseRevision:snapshot.revision,grants:structuredClone(grants),reason:reason.value,requestKey:crypto.randomUUID(),sourceVersion
    }
    ;
    const result=await request('/api/admin/permissions/preview', {
      method:'POST',body:reviewBody
    }
    );
    const host=document.getElementById('policy-diff');
    host.replaceChildren(el('p',`현재 권한이 바뀌는 고객 공간 ${result.affectedWorkspaces}개 · 적용 사유: ${result.reason}`));
    for(const tier of TIERS) {
      const card=el('div','','diff-card');
      card.append(el('h3',tier),el('p','추가: '+(result.diff[tier].added.map(label).join(', ')||'없음'),'added'),el('p','제거: '+(result.diff[tier].removed.map(label).join(', ')||'없음'),'removed'));
      host.append(card);
    }
    if(sourceVersion)host.append(el('p',`이력 #${sourceVersion} 복원`));
    message(document.getElementById('apply-status'),'');
    review.showModal();
  }
  catch(e) {
    message(status,e.message,true);
  }
}
document.getElementById('policy-preview').addEventListener('click',preview);
document.getElementById('review-close').addEventListener('click',()=>review.close());
document.getElementById('policy-apply').addEventListener('click',async()=> {
  try {
    if(!reviewBody)return;
    await busy(review,async()=> {
      const result=await request('/api/admin/permissions/apply', {
        method:'POST',body:reviewBody,idempotencyKey:reviewBody.requestKey
      }
      );
      dirty=false;
      if(cacheKey)sessionStorage.removeItem(cacheKey);
      review.close();
      await load();
      message(status,`정책 #${result.revision} 적용을 완료했습니다.`);
    }
    );
  }
  catch(e) {
    message(document.getElementById('apply-status'),e.message,true);
  }
}
);
async function history() {
  const rows=await request('/api/admin/permissions/history'),host=document.getElementById('policy-history');
  host.replaceChildren();
  for(const row of rows) {
    const entry=el('div','','history-entry');
    entry.append(el('strong',`#${row.revision} · ${row.kind} · ${whenText(row.created_at)}`),el('p',row.reason),button('이력 배정 보기·복원 초안',async()=> {
      try {
        if(dirty&&!confirm('현재 초안을 선택한 이력으로 바꿀까요?'))return;
        const old=await request(`/api/admin/permissions/history/${row.id}`);
        grants=structuredClone(old.grants);
        sourceVersion=row.id;
        dirty=true;
        reason.value=`정책 #${row.revision} 복원`;remember(true);
        reviewBody=null;
        renderBoards();
        message(status,'복원 초안을 불러왔습니다. 현재 기능 상태와 의존 조건을 검토한 뒤 적용하세요.');
      }
      catch(e) {
        message(status,e.message,true);
      }
    }
    ));
    host.append(entry);
  }
}
document.getElementById('history-refresh').addEventListener('click',()=>history().catch(e=>message(status,e.message,true)));
function acceptCatalog() {
  return !dirty||confirm('목록을 수정하면 서버 정책 이력이 추가됩니다. 현재 미적용 초안을 버리고 진행할까요?');
}
function openEditor(kind,row=null) {
  if(!acceptCatalog())return;
  editing= {
    kind,row
  }
  ;
  document.getElementById('editor-title').textContent=(kind==='group'?'그룹':'기능')+(row?' 수정':' 추가');
  for(const key of ['code','name','description','binding','deps','reason'])document.getElementById('catalog-'+key).value='';
  document.getElementById('catalog-code').value=row?.code||'';
  document.getElementById('catalog-code').readOnly=!!row;
  document.getElementById('catalog-name').value=row?.name||'';
  document.getElementById('catalog-status').value=['ACTIVE','PREPARING','DISABLED'].includes(row?.status)?row.status:'PREPARING';
  document.getElementById('catalog-order').value=row?.sort_order||0;
  document.getElementById('catalog-reason').value=reason.value;
  document.querySelectorAll('[data-action-field]').forEach(x=>x.hidden=kind==='group');
  const groups=document.getElementById('catalog-group');
  groups.replaceChildren();
  for(const g of snapshot.groups) {
    const o=el('option',g.name);
    o.value=g.code;
    groups.append(o);
  }
  groups.value=row?.group_code||snapshot.groups[0]?.code||'';
  document.getElementById('catalog-description').value=row?.description||'';
  document.getElementById('catalog-binding').value=row?.binding_key||'';
  document.getElementById('catalog-deps').value=(row?.dependencies||[]).join('\n');
  document.getElementById('catalog-admin').checked=bool(row?.admin_only);
  document.getElementById('catalog-risky').checked=bool(row?.risky);
  message(document.getElementById('catalog-error'),'');
  editor.showModal();
}
document.getElementById('new-group').addEventListener('click',()=>openEditor('group'));
document.getElementById('new-action').addEventListener('click',()=>openEditor('action'));
document.getElementById('editor-close').addEventListener('click',()=>editor.close());
function catalogBody(kind,row) {
  return kind==='group'? {
    code:row.code,name:row.name,status:row.status,sortOrder:row.sort_order,version:row.revision
  }
  : {
    code:row.code,name:row.name,groupCode:row.group_code,description:row.description,status:row.status,bindingKey:row.binding_key,adminOnly:bool(row.admin_only),risky:bool(row.risky),sortOrder:row.sort_order,version:row.version,dependencies:row.dependencies
  }
  ;
}
async function moveCatalog(kind,row,delta) {
  if(!acceptCatalog())return;
  try {
    const why=prompt('목록 순서 변경 사유',reason.value);
    if(!why?.trim())return;
    const peers=kind==='group'?snapshot.groups:snapshot.catalog.filter(a=>a.group_code===row.group_code);
    const ordered=[...peers].sort((a,b)=>a.sort_order-b.sort_order||a.code.localeCompare(b.code));
    const i=ordered.findIndex(x=>x.code===row.code),target=ordered[i+delta];
    if(!target)return;
    const body=catalogBody(kind,row);
    body.sortOrder=target.sort_order+(delta<0?-1:1);
    body.reason=why;
    await request(`/api/admin/permissions/${kind==='group'?'groups':'actions'}/${encodeURIComponent(row.code)}`, {
      method:'PUT',body
    }
    );
    if(cacheKey)sessionStorage.removeItem(cacheKey);
    await load();
    message(status,'표시 순서를 변경했습니다.');
  }
  catch(e) {
    message(status,e.message,true);
  }
}
document.getElementById('catalog-form').addEventListener('submit',async e=> {
  e.preventDefault();
  const kind=editing.kind,row=editing.row;
  try {
    await busy(e.currentTarget,async()=> {
      const value=k=>document.getElementById('catalog-'+k).value;
      let body= {
        code:value('code'),name:value('name'),status:value('status'),sortOrder:Number(value('order')),version:row?(kind==='group'?row.revision:row.version):0,reason:value('reason')
      }
      ;
      if(kind==='action')body= {
        ...body,groupCode:value('group'),description:value('description'),bindingKey:value('binding'),adminOnly:document.getElementById('catalog-admin').checked,risky:document.getElementById('catalog-risky').checked,dependencies:value('deps').split(/\s+/).filter(Boolean)
      }
      ;
      await request(`/api/admin/permissions/${kind==='group'?'groups':'actions'}${row?'/'+encodeURIComponent(row.code):''}`, {
        method:row?'PUT':'POST',body
      }
      );
      editor.close();
      if(cacheKey)sessionStorage.removeItem(cacheKey);
      await load();
      message(status,'목록을 저장했습니다. 신규 코드는 미배정 상태입니다.');
    }
    );
  }
  catch(e) {
    message(document.getElementById('catalog-error'),e.message,true);
  }
}
);
async function deleteAction(a) {
  try {
    const impact=await request(`/api/admin/permissions/actions/${encodeURIComponent(a.code)}/impact`);
    if(!confirm(a.name+' 삭제 영향:\n'+JSON.stringify(impact,null,2)+'\n사용 중인 배정·의존이 있으면 서버가 삭제를 거부합니다. 진행할까요?'))return;
    if(!acceptCatalog())return;
    const why=prompt('삭제 사유',reason.value);
    if(!why?.trim())return;
    await request(`/api/admin/permissions/actions/${encodeURIComponent(a.code)}?`+new URLSearchParams( {
      version:a.version,reason:why
    }
    ), {
      method:'DELETE'
    }
    );
    if(cacheKey)sessionStorage.removeItem(cacheKey);
    await load();
    message(status,'기능 삭제 또는 이력 보존용 보관 처리를 완료했습니다.');
  }
  catch(e) {
    message(status,e.message+(Object.keys(e.details|| {
    }
    ).length?' · '+JSON.stringify(e.details):''),true);
  }
}
async function deleteGroup(g) {
  if(!acceptCatalog())return;
  try {
    if(!confirm('그룹을 삭제할까요? 소속 기능이 있으면 먼저 다른 그룹으로 이동해야 합니다.'))return;
    const why=prompt('그룹 삭제 사유',reason.value);
    if(!why?.trim())return;
    await request(`/api/admin/permissions/groups/${encodeURIComponent(g.code)}?`+new URLSearchParams( {
      version:g.revision,reason:why
    }
    ), {
      method:'DELETE'
    }
    );
    if(cacheKey)sessionStorage.removeItem(cacheKey);
    await load();
    message(status,'빈 그룹을 삭제했습니다.');
  }
  catch(e) {
    message(status,e.message,true);
  }
}
window.addEventListener('beforeunload',e=> {
  if(dirty) {
    e.preventDefault();
    e.returnValue='';
  }
}
);
request('/api/me').then(me=> {
  cacheKey=`ax:policy-draft:${me.accountId}`;
  return load(true);
}
).catch(e=>message(status,e.message,true));

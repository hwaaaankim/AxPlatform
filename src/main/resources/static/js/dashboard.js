import {decorateIcons} from './app-shell.js?v=0.6.0';
import {
  request
}
from './request.js?v=0.6.0';
import {
  el,button,message,busy,whenText,bool
}
from './ui.js?v=0.6.0';
import {
  watch
}
from './events.js?v=0.6.0';
const root=document.getElementById('dashboard-root'),workspace=Number(root.dataset.workspaceId),status=document.getElementById('dashboard-status'),selection=document.getElementById('business-select');
const workflows= {
  EXCEL:['엑셀 · 데이터','표 만들기부터 계산·데이터 분석까지'],WORD:['워드','작성할 문서의 목적과 구성'],HWP:['한글','보고서·공문 작성의 요구사항'],PPT:['프레젠테이션','발표 목적과 청중·자료'],CRM:['고객관리','고객·영업 관리의 요구사항'],CALENDAR:['일정','시간·업무 일정의 요구사항'],APP:['앱 제작','만들고 싶은 앱의 요구사항']
}
;
const params=new URLSearchParams(location.search),filter=document.getElementById('draft-workflow'),search=document.getElementById('draft-search'),sort=document.getElementById('draft-sort');
filter.value=workflows[params.get('workflow')]?params.get('workflow'):'ALL';search.value=params.get('q')||'';sort.value=params.get('sort')==='name'?'name':'recent';
let loadedRows=[];
let scopes=[],caps=new Set(),dash= {
  widgets:Object.keys(workflows),version:0
}
,stopEvents,sequence=0,refreshTimer;
const can=code=>caps.has('workspace.'+code),current=()=>scopes.find(s=>String(s.id)===selection.value);
const active=()=>current()&&!current().archived_at;
async function permissions() {
  const own=await request(`/api/workspaces/${workspace}/access`);
  caps=new Set(own.capabilities);
  document.getElementById('space-meta').textContent=own.tier==='ADMIN'?'관리자 업무 검토 공간':'내 업무 공간 · '+({GUEST:'게스트',TIER1:'1단계',TIER2:'2단계',TIER3:'3단계'}[own.tier]||own.tier);
  try {
    if(can('dashboard.read'))dash=await request(`/api/workspaces/${workspace}/dashboard`);
  }
  catch(e) {
    message(status,e.message,true);
  }
  document.getElementById('widgets-edit').disabled=!can('dashboard.manage');
  document.getElementById('scope-create').disabled=!can('scope.create');
  document.getElementById('scope-rename').disabled=!can('scope.update');
  document.getElementById('scope-archive').disabled=!can('scope.archive');
  renderWorkflows();
}
function renderWorkflows() {
  const grid=document.getElementById('workflow-grid');
  grid.replaceChildren();
  const selectedWorkflow=filter.value;
  document.getElementById('overview-heading').textContent=selectedWorkflow==='ALL'?'어떤 일을 시작할까요?':workflows[selectedWorkflow][0];
  document.getElementById('overview-title').textContent=selectedWorkflow==='ALL'?'내 업무에 맞는 도구를, 하나씩 만들어 보세요.':selectedWorkflow==='EXCEL'?'흩어진 자료를, 내 업무에 맞는 표로.':workflows[selectedWorkflow][0]+'의 목적과 구성을 정리하세요.';
  document.getElementById('overview-copy').textContent=selectedWorkflow==='ALL'?'자료를 고르고 편집한 뒤 저장하세요. 지난 작업은 목록에서 이어서 사용할 수 있습니다.':selectedWorkflow==='EXCEL'?'사진·대화·파일·직접 입력으로 시작해 편집, 저장, 분석까지 이어갑니다.':'현재는 요구사항 초안·AI 검토를 지원합니다. 최종 파일이나 실제 업무 엔진은 준비 중입니다.';
  for(const code of selectedWorkflow==='ALL'?dash.widgets:[selectedWorkflow]) {
    const info=workflows[code];
    if(!info)continue;
    const card=el('article','','workflow-card');card.dataset.workflow=code;const icon=el('span','','workflow-icon');icon.dataset.icon=({EXCEL:'table',WORD:'document',HWP:'document',PPT:'slides',CRM:'users',CALENDAR:'calendar',APP:'grid'})[code];card.append(icon);
    card.append(el('span',code==='EXCEL'?'편집 · 저장 · 분석':'요구사항 준비','badge '+(code==='EXCEL'?'active':'pending')),el('h3',info[0]),el('p',code==='EXCEL'?info[1]:info[1]+'을 정리합니다. 최종 엔진은 준비 중입니다.'));
    const link=el('a',code==='EXCEL'?'새 엑셀 만들기 →':'요구사항 정리 →','button'+(code==='EXCEL'?' primary':''));
    link.href='/work/new?'+new URLSearchParams( {
      businessId:selection.value,workflow:code
    }
    );
    if(!can('draft.create')||!can('draft.read')||!can('draft.update')||!active()) {
      link.removeAttribute('href');
      link.setAttribute('aria-disabled','true');
      link.textContent='현재 범위에서 생성 불가';
    }
    card.append(link);
    grid.append(card);
  }
  decorateIcons(grid);
}
async function loadScopes() {
  if(!can('scope.read')) {
    scopes=[];
    selection.replaceChildren(el('option','사업 조회 권한이 없습니다.'));
    return;
  }
  const old=selection.value||params.get('businessId');
  scopes=await request('/api/business-scopes?workspaceId='+workspace);
  selection.replaceChildren();
  for(const s of scopes) {
    if(s.archived_at&&!document.getElementById('show-archived-scopes').checked)continue;
    const o=el('option',s.name+(s.archived_at?' · 보관':''));
    o.value=s.id;
    selection.append(o);
  }
  if([...selection.options].some(x=>x.value===old))selection.value=old;
  else selection.value=String(scopes.find(s=>!s.archived_at)?.id||selection.options[0]?.value||'');
}
async function drafts() {
  const host=document.getElementById('draft-list');
  if(!active()||!can('draft.list')) {
    host.replaceChildren(el('p',active()?'초안 목록 권한이 없습니다.':'보관 사업은 먼저 복원해 주세요.','muted'));
    return;
  }
  const scope=selection.value,token=sequence;
  const rows=await request('/api/drafts?'+new URLSearchParams( {
    businessId:scope,archived:document.getElementById('show-archived-drafts').checked
  }
  ));
  if(scope!==selection.value||token!==sequence)return;
  loadedRows=rows;
  renderDrafts();
}
function renderDrafts(){
  const host=document.getElementById('draft-list');
  const scoped=loadedRows.filter(d=>filter.value==='ALL'||d.workflow===filter.value);
  document.getElementById('metric-total').textContent=scoped.length;
  document.getElementById('metric-saved').textContent=scoped.filter(d=>d.status==='SAVED').length;
  document.getElementById('metric-working').textContent=scoped.filter(d=>d.status==='DRAFT').length;
  const rows=scoped.filter(d=>d.title.toLocaleLowerCase().includes(search.value.toLocaleLowerCase())).sort((a,b)=>sort.value==='name'?a.title.localeCompare(b.title,'ko'):String(b.updated_at).localeCompare(String(a.updated_at)));
  document.getElementById('draft-count').textContent=rows.length+'개 작업';
  host.replaceChildren();
  for(const d of rows) {
    const entry=el('div','','asset-item'),info=el('div','','grow');
    info.append(el('strong',d.title),el('small',`${workflows[d.workflow]?.[0]||d.workflow} · ${({DRAFT:'작업 중',SAVED:'정리 완료',ARCHIVED:'보관'})[d.status]||d.status} · ${whenText(d.updated_at)}`));
    const tools=el('div','','actions');
    if(can('draft.read')) {
      const link=el('a','열기','button small');
      link.href='/work/drafts/'+encodeURIComponent(d.id)+'?'+new URLSearchParams({return:location.pathname+location.search});
      tools.append(link);
      if(d.workflow==='EXCEL'&&caps.has('workspace.sheet.analysis.read')){
        const analysisLink=el('a','데이터 보기','button small quiet');analysisLink.href='/work/drafts/'+encodeURIComponent(d.id)+'?'+new URLSearchParams({step:'finish',return:location.pathname+location.search});tools.append(analysisLink);
      }
    }
    const arch=d.status==='ARCHIVED';
    if(can(arch?'draft.restore':'draft.archive'))tools.append(button(arch?'복원':'보관',async()=> {
      try {
        if(!confirm(d.title+(arch?' 초안을 복원할까요?':' 초안을 보관할까요?')))return;
        await request(`/api/drafts/${encodeURIComponent(d.id)}/${arch?'restore':'archive'}`, {
          method:'POST',body: {
            version:d.version
          }
        }
        );
        await drafts();
      }
      catch(e) {
        message(status,e.message,true);
      }
    }
    ));
    if(can('draft.clone')&&can('draft.read')&&can('draft.create')&&!arch)tools.append(button('재사용',async()=> {
      try {
        const title=prompt('새 초안 이름',d.title+' 복사');
        if(!title?.trim())return;
        const created=await request(`/api/drafts/${encodeURIComponent(d.id)}/clone`, {
          method:'POST',body: {
            title
          }
        }
        );
        location.assign('/work/drafts/'+encodeURIComponent(created.id));
      }
      catch(e) {
        message(status,e.message,true);
      }
    }
    ));
    entry.append(info,tools);
    host.append(entry);
  }
  if(!rows.length){const empty=el('div','','empty-state');empty.append(el('strong',search.value?'검색 결과가 없습니다':'아직 저장한 작업이 없습니다'),el('p',search.value?'다른 이름으로 검색하거나 검색어를 지워 주세요.':'위에서 업무를 선택하면 시작 방법을 고를 수 있습니다.'));host.append(empty);}
}
async function assets(){const link=document.getElementById('my-files-link');link.href='/my/files?'+new URLSearchParams({businessId:selection.value});}
async function refresh() {
  await permissions();
  await loadScopes();
  renderWorkflows();
  document.querySelectorAll('.sidebar [data-workflow]').forEach(a=>{const u=new URL(a.href);u.searchParams.set('businessId',selection.value);a.href=u.pathname+u.search;});
  document.getElementById('scope-archive').textContent=current()?.archived_at?'사업 복원':'사업 보관';
  await Promise.all([drafts(),assets()]);
}
function connect() {
  stopEvents?.();
  stopEvents=null;
  if(selection.value&&can('events.read'))stopEvents=watch(selection.value, {
    onStatus:text=>document.getElementById('event-status').textContent=text,onPolicy:()=>schedule(),onResync:()=>schedule(),onChange:()=>schedule()
  }
  );
  else document.getElementById('event-status').textContent='실시간 보기 권한 없음';
}
function schedule() {
  clearTimeout(refreshTimer);
  refreshTimer=setTimeout(()=>refresh().catch(e=>message(status,e.message,true)),300);
}

function saveNavigation(){const q=new URLSearchParams();q.set('businessId',selection.value);if(filter.value!=='ALL')q.set('workflow',filter.value);if(search.value)q.set('q',search.value);q.set('sort',sort.value);history.replaceState({},'',location.pathname+'?'+q);document.querySelectorAll('.sidebar [data-workflow]').forEach(a=>{if(a.dataset.workflow===filter.value)a.setAttribute('aria-current','page');else a.removeAttribute('aria-current');});}
filter.addEventListener('change',()=>{saveNavigation();renderWorkflows();renderDrafts();});sort.addEventListener('change',()=>{saveNavigation();renderDrafts();});search.addEventListener('input',()=>{saveNavigation();renderDrafts();});
selection.addEventListener('change',()=> {
  saveNavigation();
  sequence++;
  renderWorkflows();
  document.querySelectorAll('.sidebar [data-workflow]').forEach(a=>{const u=new URL(a.href);u.searchParams.set('businessId',selection.value);a.href=u.pathname+u.search;});
  document.getElementById('scope-archive').textContent=current()?.archived_at?'사업 복원':'사업 보관';
  Promise.all([drafts(),assets()]).catch(e=>message(status,e.message,true));
  connect();
}
);
document.getElementById('show-archived-scopes').addEventListener('change',()=>{refresh().then(connect).catch(e=>message(status,e.message,true));});
document.getElementById('show-archived-drafts').addEventListener('change',()=>drafts().catch(e=>message(status,e.message,true)));
document.getElementById('scope-create').addEventListener('click',async()=> {
  try {
      const name=prompt('새 사업 이름');
    if(!name?.trim())return;
    const row=await request('/api/business-scopes', {
      method:'POST',body: {
        workspaceId:workspace,name
      }
    }
    );
    await loadScopes();
    selection.value=String(row.id);
    sequence++;
    await refresh();
    connect();
    message(status,'새 사업을 만들었습니다.');
  }
  catch(e) {
    message(status,e.message,true);
  }
}
);
document.getElementById('scope-rename').addEventListener('click',async()=> {
  try {
    const s=current();
    if(!s)return;
    const name=prompt('사업 이름',s.name);
    if(!name?.trim())return;
    await request('/api/business-scopes/'+s.id, {
      method:'PATCH',body: {
        workspaceId:workspace,name,version:s.version
      }
    }
    );
    await refresh();
  }
  catch(e) {
    message(status,e.message,true);
  }
}
);
document.getElementById('scope-archive').addEventListener('click',async()=> {
  try {
    const s=current();
    if(!s||!confirm(s.name+(s.archived_at?' 사업을 복원할까요?':' 사업을 보관할까요?')))return;
    await request(`/api/business-scopes/${s.id}/archive`, {
      method:'POST',body: {
        workspaceId:workspace,version:s.version,archived:!s.archived_at
      }
    }
    );
    sequence++;
    await refresh();
    connect();
  }
  catch(e) {
    message(status,e.message,true);
  }
}
);
document.getElementById('draft-reset').addEventListener('click',()=>{search.value='';filter.value='ALL';sort.value='recent';document.getElementById('show-archived-drafts').checked=false;saveNavigation();renderWorkflows();drafts().catch(e=>message(status,e.message,true));});
const wd=document.getElementById('widgets-dialog');
document.getElementById('widgets-edit').addEventListener('click',()=> {
  const host=document.getElementById('widget-options');
  host.replaceChildren();
  for(const [code,info] of Object.entries(workflows)) {
    const label=el('label','','check'),cb=document.createElement('input');
    cb.type='checkbox';
    cb.value=code;
    cb.checked=dash.widgets.includes(code);
    label.append(cb,el('span',info[0]));
    host.append(label);
  }
  wd.showModal();
}
);
document.getElementById('widgets-close').addEventListener('click',()=>wd.close());
document.getElementById('widgets-form').addEventListener('submit',async e=> {
  e.preventDefault();
  try {
    await busy(document.getElementById('widgets-form'),()=>request(`/api/workspaces/${workspace}/dashboard`, {
      method:'PATCH',body: {
        widgets:[...document.querySelectorAll('#widget-options input:checked')].map(x=>x.value),version:dash.version
      }
    }
    ));
    wd.close();
    await refresh();
  }
  catch(e) {
    message(document.getElementById('widgets-status'),e.message,true);
  }
}
);
const plan=document.getElementById('plan-popup');
document.getElementById('plan-close').addEventListener('click',()=>plan.close());
document.getElementById('plan-free').addEventListener('click',()=>plan.close());
document.getElementById('plan-hide').addEventListener('click',async()=> {
  try {
    await request('/api/preferences/plan-popup/hide', {
      method:'POST'
    }
    );
    plan.close();
  }
  catch(e) {
    message(document.getElementById('plan-status'),e.message,true);
  }
}
);
// Plan information remains accessible without interrupting the first task or tutorial.
if(root.dataset.showPlan==='true'){const b=button('무료 이용 안내',()=>plan.showModal(),'button small quiet');document.querySelector('.page-heading').append(b);}
refresh().then(connect).catch(e=>message(status,e.message,true));
window.addEventListener('pagehide',()=> {
  stopEvents?.();
  clearTimeout(refreshTimer);
}
);

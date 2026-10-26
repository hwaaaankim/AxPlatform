import {el,button} from './ui.js?v=0.10.1';
const step=(selector,title,text,stage,tab)=>({selector,title,text,stage,tab});
export function pageGuide(){
 const p=location.pathname,root=document.getElementById('draft-root'),workflow=root?.dataset.workflow;
 if(root){const excel=workflow==='EXCEL';return {key:'work-'+workflow,steps:[
 step('.work-stages','작업 순서를 먼저 확인하세요','시작 방법 → 기본정보 → 자료 준비 → 확인·편집 → 보기 설정·검토 → 저장·조회 순으로 진행합니다. 상단 단계를 눌러 이전 내용을 다시 고칠 수 있습니다.','start'),
 step('#workflow-modes','가진 자료에 맞게 시작하기',excel?'사진, 대화, 엑셀·CSV 업로드, 직접 입력 중 하나를 고르세요. 선택한 방법에 필요한 화면만 다음에 나타납니다.':'안내 질문 또는 직접 작성 중 선택하세요. 이 업무는 현재 요구사항 정리와 AI 검토를 지원하며 최종 결과 엔진은 준비 중입니다.','start'),
 step('#draft-title','찾기 쉬운 이름 붙이기','작업 목록에서 다시 찾을 이름입니다. 목적·사용할 자료는 안내 질문에서 선택하거나 직접 입력합니다. 기존 답을 수정해도 다른 답은 유지됩니다.','basics'),
 step('#prepare-tools','자료를 넣고 다음으로 이동',excel?'사진별 시트 또는 이어지는 사진을 한 시트로 옮기는 방식을 선택하세요. 사진의 연결 관계와 읽는 순서를 확인할 수 있습니다. 엑셀은 모든 시트의 읽기 결과를 확인하고 가져올 시트를 고릅니다. 오류 셀·수식·차트 안내를 먼저 확인하세요.':'자료가 있으면 업로드하고 업무 첨부에 연결하세요. AI에게 보낼 자료는 AI 검토 탭에서 별도로 선택합니다.','prepare'),
 step('#draft-assets','업무에 연결할 첨부 선택','업무 첨부는 최대 20개입니다. 개별 × 또는 첨부 전체 해제는 이 업무의 선택만 바꿉니다. 원본의 휴지통 이동·복원·영구 삭제는 내 자료에서 처리합니다.','review'),
 step('#draft-summary','입력한 내용 확인하기','정리한 답을 확인하고 항목별 수정 버튼을 누르면 해당 질문으로 돌아갑니다. 처음부터 다시 입력할 필요가 없습니다.','review'),
 ...(excel?[step('.workbook-bar','한 프로젝트 안에서 시트 늘리기','시트 추가에서 직접 입력, AI·대화, 엑셀 가져오기, 사진을 선택하세요. 탭을 눌러 이동해도 미저장 셀은 유지됩니다. 이름 변경·복제·이동·삭제 후 전체 시트 저장으로 확정합니다.','edit','table'),step('#sheet-section','실제 표에서 직접 편집','열·행 추가, 셀 입력과 탭 구분 붙여넣기를 지원합니다. 머리글을 잡아 열 순서를 옮기거나 자세히 조정의 이동 버튼을 사용하세요.','edit','table'),step('.sheet-properties','선택한 열만 자세히 조정','머리글을 누르면 이름·형식·단위·너비를 바꿀 수 있습니다. 날짜 열은 직접 입력하거나 셀의 달력으로 선택합니다. 숫자 계산 열은 고정 열 ID를 참조하는 사칙연산을 지원하며 서버 저장 시 검증합니다.','edit','table')]:[]),
 ...(excel?[step('.ai-targets','사진 여러 장을 시트별로 검토하기','검토할 시트 한 개 또는 사진마다 별도 시트를 선택하세요. 사진별 요청은 최대 3장·합계 6MB이며 호출별 사용량이 합산됩니다. 읽을 수 없는 사진은 이유·질문을 확인하고 해당 시트만 다시 검토합니다.','edit','ai')]:[]),
 step('#ai-section','AI 제안을 검토하고 선택하기','요청 내용과 전송할 자료를 확인해 AI 검토를 실행하세요. 제안 내용은 자동으로 적용되지 않습니다. 직접 고친 내용과 제안의 변경 범위를 확인한 뒤 필요한 항목만 반영합니다.','edit','ai'),
 ...(excel?[step('#view-setup','보기 설정은 필요한 것만','전체 검색·100행 목록이 기본입니다. 검색 대상 열, 자주 쓸 필터, 정렬, 표시 열과 그래프를 선택할 수 있습니다. 선택하지 않아도 저장됩니다.','review')]:[]),
 step('#finish-panel','저장하고 다시 사용하기',excel?'저장하면 데이터 센터가 열립니다. 데이터 추가로 새 행을 입력하면 수식도 적용됩니다. 엑셀로 데이터 추가·교체에서 기존 수식 유지 여부를 정할 수 있습니다. 원본 전체 XLSX는 모든 시트와 수식을 내려받습니다.':'정리 내용을 저장하고 목록에서 다시 열어 수정합니다. 재사용은 별도의 업무 초안을 만들어 기존 내용을 보존합니다.','finish'),
 ...(excel?[step('#analysis-panel','데이터 센터에서 조회하기','시트를 선택하면 저장된 원본이 바로 보입니다. 열 제목을 누르면 정렬하고 조회 조건에서 AND 조건을 여러 개 추가할 수 있습니다. 이 데이터로 무엇을 볼 수 있나요를 열면 실제 열과 값의 예시가 나옵니다.','finish'),step('.dc-toolbar','보고서·계산 열 추가','보고서 만들기에서 여러 X축 기준과 Y축 집계값을 선택하세요. 계산 열 추가는 수식 결과와 적용 행 수를 먼저 확인하고 원본에 저장합니다. 현재 보기 저장으로 여러 보고서를 보관하고 다운로드에서 전체 조회 결과를 내보냅니다.','finish')]:[]),
 step('.tutorial-launch','언제든 다시 확인하세요','다시 보지 않기를 선택하면 자동 안내만 꺼집니다. 우측 아래 사용 안내 버튼은 계속 표시됩니다.')
 ]};}
 if(p==='/login'||p==='/admin/login')return{key:p,steps:[step('.auth-tabs','로그인 종류 선택','일반 회원은 이메일 또는 가입한 소셜 서비스로 로그인합니다. 시스템 관리계정은 관리자 탭을 선택하세요.'),step('form','계정 정보 입력',p.includes('admin')?'관리자 아이디와 비밀번호를 입력하세요. 성공하면 관리자 현황 화면으로 이동합니다.':'가입 이메일과 비밀번호를 입력하세요. 가입 때 사용한 소셜 계정은 해당 로그인 버튼을 사용합니다.'),step('.secondary-auth','계정을 찾거나 가입하기','계정이 없으면 회원가입을, 이메일 계정 정보를 잊었다면 계정 찾기를 이용하세요. 연결이 준비된 로그인 수단만 표시됩니다.')]};
 if(p==='/dashboard')return{key:'dashboard-'+(new URLSearchParams(location.search).get('workflow')||'ALL'),steps:[step('.sidebar','업무 메뉴 고르기','엑셀·데이터 또는 필요한 업무를 선택하세요. 준비 표시가 있는 메뉴는 요구사항 초안을 정리하는 범위입니다.'),step('.scope-bar','자료를 관리할 사업 선택','사업별로 작업과 첨부를 분리합니다. 새 사업을 만들거나 사업 이름을 고칠 수 있으며 보관된 사업은 복원한 뒤 사용합니다.'),step('#workflow-grid','새 작업 시작하기','원하는 업무의 새로 만들기를 누르면 시작 방법을 고르는 화면으로 이동합니다. 기본 양식도 직접 바꿀 수 있습니다.'),step('#draft-list-panel','저장한 작업 이어가기','업무 종류와 이름으로 검색하고 정렬하세요. 데이터 저장됨 표시가 있는 작업은 데이터 센터 열기로 바로 조회하고 원본 편집으로 수정합니다. 재사용은 기존 데이터 행을 제외한 새 초안 생성입니다. 보관은 목록에서 분리하며 복원이 가능합니다.'),step('#assets-panel','내 자료 관리','사업 공통·업무별·작업 전용 자료는 내 자료에서 검색하고 재사용하세요. 보관 용량과 휴지통·영구 삭제도 이곳에서 관리합니다.'),step('#widgets-edit','내 홈에 필요한 업무만','홈 구성을 눌러 업무 카드를 표시하거나 숨깁니다. 숨겨도 해당 작업과 데이터는 삭제되지 않습니다.')]};
 if(p==='/my/files'||p==='/admin/files')return{key:p,steps:[step(p==='/admin/files'?'#member-selector':'#quota-panel','대상과 용량 확인',p==='/admin/files'?'회원 이름 또는 ID로 계정을 고르세요. 관리자 계정도 포함됩니다. 실제 저장 루트와 파일별 전체 경로를 확인할 수 있습니다.':'내 보관 용량과 이번 달 AI 사용량을 확인하세요. 휴지통 파일도 영구 삭제 전까지 용량에 포함됩니다.'),step('#file-filters','범위를 좁혀 자료 찾기','사업, 업무 종류, 공통·작업 전용 범위, 개별 작업과 파일 이름으로 검색하세요. 사용 중과 휴지통을 전환할 수 있습니다.'),step(p==='/admin/files'?'#usage-policy':'#file-upload-panel','자료 등록과 용량 관리',p==='/admin/files'?'회원의 저장 한도와 AI 사용 허용·월 토큰·USD 비용 한도를 사유와 함께 변경합니다. 기존 자료는 자동 삭제하지 않습니다.':'새 자료 보관하기를 열어 사업 공통·업무별 공통·작업 전용 중 범위를 선택하세요. 여러 파일을 추가하고 대기 목록에서 개별 제외한 뒤 등록합니다.'),step('#file-list-panel','개별·선택 자료 관리','자료를 열거나 다운로드하고, 필요 없으면 휴지통으로 옮기세요. 전체 선택은 현재 페이지에만 적용됩니다. 휴지통의 영구 삭제는 연결 작업을 확인한 후 파일을 실제로 지웁니다.')]};
 if(p==='/admin/operations')return{key:p,steps:[step('#operations-filters','조회 기간과 대상 선택','한국 시간 기준 시작·종료일을 선택하고 필요하면 회원 ID와 업무로 좁혀 조회하세요.'),step('#activity-panel','사용 흐름과 실패 확인','일별 활동과 기능 사용을 비교하고 실패 지점을 확인합니다. 방문 횟수를 실제 고객 이탈률로 해석하지 않도록 상세 설명을 함께 확인하세요.'),step('#ai-usage-panel','토큰과 비용 확인','입력·출력·캐시·추론 내역과 모델별 비용을 확인하세요. 미확인 호출은 예약량을 유지합니다. 제공사 기록을 확인한 뒤 정산합니다.'),step('#ai-controls','AI 사용 중지·재개','전체 AI 요청을 제어할 수 있습니다. 이미 전송한 호출은 취소 과금 보장이 없으며 수동 편집은 유지됩니다.'),step('#model-prices','단가 설정','사용 모델의 100만 토큰당 USD 단가와 확인 근거를 등록하세요. 새 단가는 이후 호출부터 적용됩니다. 회원별 한도는 회원 자료·용량 메뉴에서 설정합니다.')]};
 const byPath={
 '/admin': [step('.summary-grid','운영 현황 확인','실제 계정 수와 현재 권한 정책 등 운영 현황을 확인합니다.'),step('main .grid-2','관리할 항목 선택','회원관리에서 계정·등급을, 권한관리에서 등급별 기능을 관리합니다. 업무 공간에서 실제 흐름을 검토할 수 있습니다.'),step('.sidebar','관리 메뉴 이동','관리 메뉴를 통해 회원·권한·시스템 상태를 확인하세요. 고객 업무와 관리자 기능은 분리되어 있습니다.')],
 '/admin/members':[step('.search-form','필요한 회원 찾기','검색어·계정 상태·등급 조건을 선택한 뒤 검색하세요. 초기화하면 조건을 지웁니다.'),step('.table-scroll','회원 상세로 이동','목록의 상세 버튼으로 회원 정보와 변경 이력을 확인하고 등급이나 상태를 변경할 수 있습니다.'),step('.pagination','목록 페이지 이동','이전·다음으로 목록을 이동합니다. 상세에서 뒤로 돌아오면 검색 조건이 유지됩니다.')],
 '/admin/permissions':[step('#catalog-list','배정할 기능 찾기','이름·코드·그룹으로 기능을 찾고 항목 또는 그룹을 등급 칸에 드래그하세요. 선택 항목 배정 버튼도 같은 역할을 합니다.'),step('#tier-boards','등급별 기능 조정','게스트·1·2·3단계는 독립적인 권한입니다. 기능을 넣거나 제거할 때 필수 의존 기능과 영향 범위를 함께 확인하세요.'),step('#policy-reason','변경 사유 작성','배정 초안을 바꾼 이유를 적으세요. 아직 고객에게 반영되지 않은 상태입니다.'),step('#policy-preview','검토 후 정책 적용','변경 검토에서 추가·제거 기능과 영향 범위를 확인한 뒤 정책 적용을 누르면 실제 권한이 변경됩니다.'),step('#new-action','기능 목록 관리','그룹·기능 추가와 상세 편집을 지원합니다. 준비 중 기능은 서버 구현을 완료한 기능처럼 배정할 수 없습니다.'),step('#policy-history','이력 확인·복원','적용 이력에서 이전 정책을 확인하고 새 정책 버전으로 복원할 수 있습니다. 현재 입력을 버리려면 초안 취소를 사용하세요.')],
 '/admin/password':[step('main form','관리자 비밀번호 변경','현재 비밀번호를 확인한 뒤 새 비밀번호와 확인 값을 입력합니다. 완료하면 다시 로그인해야 합니다.'),step('main .notice','변경 영향 확인','기존 관리자 세션이 종료되므로 진행 중인 다른 작업을 먼저 저장하세요.')],
 '/admin/system':[step('#ai-connection-panel','AI 연결 확인 준비','개인 설정을 변경했다면 서버를 재시작합니다. 이곳에서는 사용자 자료 없이 현재 키와 모델의 입력 토큰 확인 요청을 점검합니다.'),step('#ai-connection-check','연결 확인 실행','AI 연결 확인을 누르면 성공 여부와 오류 확인 정보가 아래에 나타납니다. 실패하면 HTTP 상태·오류 코드·요청 식별번호를 확인하세요. 다시 확인은 30초 후 가능합니다.'),step('main .panel:last-of-type','SQL 적용 기록 확인','현재 DB에 적용한 SQL 기록을 확인합니다. AI 연결 확인 성공은 실제 생성 요청이나 응답 품질까지 검증한 결과가 아니므로, 이후 작업 화면에서 필요한 요청을 실행하세요.')],
 '/signup':[step('.step-nav','가입은 세 단계입니다','계정 → 연락처 → 확인 순으로 진행합니다. 이전 단계로 돌아가도 작성 중인 값은 유지됩니다.'),step('#email','가입 이메일 인증','이메일을 입력하고 인증번호를 받아 확인하세요. 인증받은 소셜 이메일은 확인 단계를 줄일 수 있습니다.','signup-0'),step('#phone','연락처 확인','이름과 휴대전화를 입력하고 문자 인증번호를 받아 확인합니다. 인증 시간이 지나면 새 번호를 요청하세요.','signup-1'),step('#agree-terms','최종 확인과 동의','요약한 가입 정보와 약관·개인정보 안내를 읽고 필수 항목에 동의한 뒤 가입을 완료합니다.','signup-2'),step('#signup-next','다음 단계 진행','비밀번호를 입력한 뒤 연락처 단계에서 이름·휴대전화 인증을 진행합니다. 마지막에 약관과 정보를 확인해 가입합니다.')],
 '/signup/social-existing':[step('main form','기존 SNS 계정으로 계속','이미 가입한 소셜 계정이 확인되었습니다. 기존 계정으로 로그인을 누르면 저장한 업무 공간으로 이동합니다. 새 계정을 중복 생성하지 않습니다.')],
 '/recovery':[step('#purpose','찾을 정보 선택','아이디 찾기는 문자 안내, 비밀번호 재설정은 이메일 안내입니다. 선택에 따라 필요한 항목이 표시됩니다.'),step('#recovery-form','가입 정보로 안내 요청','가입한 휴대전화와 필요한 이메일을 입력하세요. SNS 가입 계정은 해당 서비스의 계정 찾기를 이용합니다.')],
 '/password/reset':[step('#reset-form','새 비밀번호 입력','메일로 받은 유효한 링크에서 새 비밀번호를 입력합니다. 변경 후 기존 세션이 만료되며 새 비밀번호로 다시 로그인합니다.')]
 };
 if(p.startsWith('/admin/members/'))return{key:'member-detail',steps:[step('main .panel','회원 정보 확인','선택한 계정의 가입 방식·상태·등급을 먼저 확인합니다. 다른 사람의 계정과 혼동하지 않도록 이름과 연락처를 함께 확인하세요.'),step('main form','변경 항목과 이유 입력','상태·등급 변경 폼에서 필요한 항목만 고르고 사유를 입력해 저장합니다. 자동 결제 요금이 변경되는 동작은 아닙니다.'),step('#member-workspaces','등급 만료일 설정','기존 만료일은 입력란에 유지됩니다. 달력 버튼에서 날짜·시간을 선택하고 적용하세요. 비우기는 계속 유지할 때만 사용합니다. 변경 사유를 입력한 후 등급 적용을 누릅니다.'),step('.history-entry, #member-history','변경 이력 확인','저장 후 상세와 이력을 다시 확인하세요. 변경은 서버의 최신 권한과 버전 기준으로 검증됩니다.')]};
 if(p==='/terms'||p==='/privacy')return{key:p,steps:[step('main','문서 내용을 확인하세요','회원가입에서 동의하는 문서입니다. 항목을 확인하고 브라우저 뒤로가기로 가입 화면에 돌아갈 수 있습니다.')]};
 return{key:p,steps:byPath[p]||[step('main','이 화면의 안내 확인','화면에 표시된 설명과 필요한 작업을 확인하세요. 오류가 표시되면 안내된 이동 버튼으로 돌아가거나 다시 로그인하세요.')]};
}
export async function initTutorial(){
 if(document.querySelector('.tutorial-launch'))return;
 const launcher=button('사용 안내',start,'tutorial-launch');launcher.setAttribute('aria-label','이 페이지 튜토리얼 다시 보기');document.body.append(launcher);
 let account='public';if(document.querySelector('.app-frame')){try{const r=await fetch('/api/me',{headers:{Accept:'application/json'},credentials:'same-origin',redirect:'error'});if(r.ok&&r.headers.get('content-type')?.includes('json'))account=(await r.json()).accountId;}catch{}}
 let guide=pageGuide(),index=0,layer=null,target=null,origin=null,positionTimer,originMenu=false,signupState=[];
 const key='ax.tutorial.v4.'+account+'.'+guide.key;
 const read=(store,k)=>{try{return store.getItem(k);}catch{return null;}};
 const write=(store,k,v)=>{try{store.setItem(k,v);}catch{}};
 async function start(){
  if(layer)return;if(document.querySelector('dialog[open]'))return;
  guide=pageGuide();index=0;origin=document.activeElement;originMenu=document.body.classList.contains('menu-open');signupState=[...document.querySelectorAll('#signup-form [data-step]')].map(n=>[n,n.hidden]);
  document.dispatchEvent(new CustomEvent('ax:tour:start'));
  layer=el('dialog','','tour-layer');layer.setAttribute('aria-label','페이지 사용 안내');
  const shades=Array.from({length:4},()=>el('div','','tour-shade')),highlight=el('div','','tour-highlight'),bubble=el('section','','tour-bubble');
  const closeButton=button('×',()=>close(false),'tour-close');closeButton.setAttribute('aria-label','안내 닫기');
  const count=el('div','','eyebrow'),title=el('h2'),body=el('p'),progress=el('div','','tour-progress'),bar=el('span'),actions=el('div','','actions'),bottom=el('div','','tour-bottom');
  title.id='tutorial-title';body.id='tutorial-copy';layer.setAttribute('aria-labelledby',title.id);layer.setAttribute('aria-describedby',body.id);progress.append(bar);
  const prev=button('이전',()=>{index--;show();},'button'),next=button('다음',()=>{if(index===guide.steps.length-1)close(false);else{index++;show();}},'button primary');
  actions.append(prev,next);bottom.append(button('다시 보지 않기',()=>close(true),''),button('나중에 보기',()=>close(false),''));bubble.append(closeButton,count,title,body,progress,actions,bottom);layer.append(...shades,highlight,bubble);document.body.append(layer);layer.showModal();
  layer.addEventListener('cancel',e=>{e.preventDefault();close(false);});
  function position(){
   if(!layer)return;const W=innerWidth,H=innerHeight,raw=target?.getBoundingClientRect();let r=raw&&raw.width&&raw.height&&raw.right>0&&raw.bottom>0?{left:Math.max(8,raw.left-6),top:Math.max(8,raw.top-6),right:Math.min(W-8,raw.right+6),bottom:Math.min(H-8,raw.bottom+6)}:{left:W/2,top:H/2,right:W/2,bottom:H/2};
   if(r.right<r.left||r.bottom<r.top)r={left:W/2,top:H/2,right:W/2,bottom:H/2};
   [[0,0,W,r.top],[0,r.top,r.left,r.bottom-r.top],[r.right,r.top,W-r.right,r.bottom-r.top],[0,r.bottom,W,H-r.bottom]].forEach((v,i)=>{Object.assign(shades[i].style,{left:v[0]+'px',top:v[1]+'px',width:v[2]+'px',height:v[3]+'px'});});
   Object.assign(highlight.style,{left:r.left+'px',top:r.top+'px',width:r.right-r.left+'px',height:r.bottom-r.top+'px'});
   const bh=bubble.offsetHeight,bw=bubble.offsetWidth;let above=false,y=r.bottom+16;if(y+bh>H-16){y=r.top-bh-16;above=true;}if(y<16)y=Math.max(16,(H-bh)/2);
   bubble.dataset.above=String(above);Object.assign(bubble.style,{left:Math.max(16,Math.min(W-bw-16,r.left))+'px',top:y+'px',maxHeight:(H-32)+'px',overflowY:'auto'});
  }
  function show(){const s=guide.steps[index];document.body.classList.toggle('menu-open',s.selector==='.sidebar'||originMenu);if(s.stage?.startsWith('signup-')){const step=s.stage.slice(7);document.querySelectorAll('#signup-form [data-step]').forEach(n=>n.hidden=n.dataset.step!==step);}document.dispatchEvent(new CustomEvent('ax:tour:stage',{detail:{stage:s.stage,tab:s.tab}}));target=document.querySelector(s.selector);if(target?.getClientRects().length)target.scrollIntoView({block:'center',behavior:'instant'});count.textContent=`사용 안내 · ${index+1} / ${guide.steps.length}`;title.textContent=s.title;body.textContent=s.text;prev.disabled=index===0;next.textContent=index===guide.steps.length-1?'안내 완료':'다음';bar.style.width=((index+1)/guide.steps.length*100)+'%';requestAnimationFrame(()=>{position();next.focus({preventScroll:true});});}
  layer._position=position;show();
 }
 function close(permanent){if(!layer)return;if(permanent)write(localStorage,key,'hidden');write(sessionStorage,key,'seen');layer.close();layer.remove();layer=null;document.body.classList.toggle('menu-open',originMenu);signupState.forEach(([n,h])=>n.hidden=h);document.dispatchEvent(new CustomEvent('ax:tour:end'));(origin?.isConnected?origin:launcher).focus({preventScroll:true});}
 window.addEventListener('resize',()=>{clearTimeout(positionTimer);positionTimer=setTimeout(()=>layer?._position(),80);});window.addEventListener('scroll',()=>layer?._position(),true);
 setTimeout(()=>{if(!read(localStorage,key)&&!read(sessionStorage,key))start();},900);
}

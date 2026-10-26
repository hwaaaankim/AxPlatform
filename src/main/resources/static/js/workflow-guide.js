import {
  el,button
} from './ui.js?v=0.10.1';
export const fields=[['purpose','업무 목적'],['data','사용할 자료'],['result','원하는 결과'],['audience','사용할 사람'],['details','추가 조건']];
const q=(key,title,hint,choices)=>({
  key,title,hint,choices
});
const extra=q('details','꼭 지켜야 할 조건이 있나요?','단위·기간·표현·반드시 제외할 내용을 적어 주세요. 금액이나 날짜가 불명확하면 추측하지 않습니다.',['원본 항목과 순서를 유지','빈 양식부터 만들기','아직 정하지 않았어요']);
export const guides={
  EXCEL:{
    name:'엑셀',intro:'사진의 표를 읽거나, 업무를 설명하거나, 빈 표를 직접 만들 수 있습니다.',questions:[q('purpose','어떤 일을 표로 정리할까요?','예: 거래처별 납품내역을 기록하고 싶어요.',['매출·지출 기록','상품·재고 목록','직접 만든 기록표']),q('data','현재 어떤 자료가 있나요?','사진은 이전 자료 단계에서 올립니다. 여기에는 AI가 참고할 자료의 내용·범위를 적어 주세요.',['사진·캡처 자료가 있어요','항목 이름만 정했어요','아직 자료가 없어요']),q('result','어떤 표부터 필요하세요?','데이터가 필요한지, 입력용 빈 양식인지 구분해 주세요.',['현재 자료가 들어간 표','반복 입력할 빈 양식','기존 표의 항목 개선']),q('audience','주로 누가 입력하거나 확인하나요?','예: 매장 직원이 매일 입력하고 관리자가 확인합니다.',['나 혼자','직원들과 함께','고객에게 전달']),extra]
  },
  WORD:{
    name:'워드',intro:'문서의 목적과 읽는 사람부터 정리합니다. 이번 버전은 검토된 업무 요약을 제공합니다.',questions:[q('purpose','무슨 문서가 필요하세요?','예: 거래처에 보낼 납품 제안서',['제안서·보고서','안내문·공지','업무 매뉴얼']),q('audience','누가 읽는 문서인가요?','읽는 사람과 원하는 반응을 알려 주세요.',['내부 직원','거래처·고객','외부 제출처']),q('data','어떤 내용을 담을까요?','글이나 사진은 이전 자료 단계에서 올립니다. 여기에는 문서에 반영할 범위를 적어 주세요.',['기존 자료를 다듬기','메모를 문서로 정리','처음부터 구성']),q('result','어떤 분량과 말투가 좋을까요?','정확한 페이지·문체가 정해져 있으면 직접 입력하세요.',['짧고 간결하게','근거를 자세하게','친절한 안내 형식']),extra]
  },
  HWP:{
    name:'한글',intro:'제출 목적과 지정 서식을 먼저 확인합니다. 이번 버전은 HWP 파일을 생성하지 않습니다.',questions:[q('purpose','어디에 제출할 내용인가요?','예: 기관 제출용 사업계획서',['기관 제출','사내 보고','고객 안내']),q('result','지정된 서식이 있나요?','필수 항목·글자 크기·분량이 있으면 적어 주세요.',['지정 서식이 있어요','내용 구성만 필요해요','제출 기준을 확인해야 해요']),q('data','어떤 자료를 바탕으로 쓸까요?','JPG·PNG·TXT 자료는 이전 단계에서 올립니다. 여기에는 사용할 내용·출처를 적어 주세요.',['기존 글·메모','사진·캡처','아직 자료가 없어요']),q('audience','누가 검토하나요?','제출처가 요구하는 기준을 알면 함께 적어 주세요.',['내부 검토자','외부 기관','거래처']),extra]
  },
  PPT:{
    name:'프레젠테이션',intro:'발표 목적·청중·핵심 메시지를 좁혀 업무 요약을 만듭니다. 슬라이드 파일 생성은 후속 범위입니다.',questions:[q('purpose','발표로 무엇을 전달할까요?','예: 신규 서비스 도입의 필요성을 설명합니다.',['새 아이디어 제안','성과·진행 보고','교육·사용법 안내']),q('audience','누가 듣는 발표인가요?','이미 알고 있는 내용과 결정해야 하는 일을 적어 주세요.',['경영진·의사결정자','동료·직원','고객·외부 청중']),q('data','반드시 전달할 메시지와 자료는요?','한 문장 핵심 메시지와 실제 수치의 출처를 적어 주세요.',['정리한 자료가 있어요','핵심 메모만 있어요','구성부터 같이 정하기']),q('result','시간과 분량을 정했나요?','발표 시간·예상 장수가 정해졌으면 직접 입력하세요.',['짧은 소개','상세한 설명','아직 정하지 않았어요']),extra]
  },
  CRM:{
    name:'고객관리',intro:'관리할 대상과 놓치기 쉬운 일을 먼저 정리합니다. 실제 CRM 생성·외부 발송은 아직 지원하지 않습니다.',questions:[q('audience','무엇을 관리하고 싶으세요?','예: 납품 거래처와 담당자 연락처',['거래처·담당자','문의·잠재 고객','기존 고객']),q('purpose','어떤 일을 놓치기 쉬우세요?','해결할 반복 업무를 한 가지 골라 주세요.',['재주문·후속 연락','문의 처리 상태','상담 내용 찾기']),q('data','현재 목록이 있나요?','실제 자료에서 필요한 항목을 함께 검토합니다.',['기존 고객 목록','메모·명함 사진','새로 시작']),q('result','다음 행동이 어떻게 보이면 좋을까요?','예: 이번 주 연락할 거래처를 확인하고 싶어요.',['연락할 대상 목록','진행 상태별 정리','고객별 기록 요약']),extra]
  },
  CALENDAR:{
    name:'일정',intro:'할 일과 대상, 일시를 정리합니다. 실제 일정 등록·반복·알림 발송은 후속 범위입니다.',questions:[q('purpose','어떤 일정을 정리할까요?','예: 거래처 미팅과 납품 예정일',['미팅·방문','납품·마감','개인 할 일']),q('audience','누구와 관련된 일정인가요?','예: 기존 거래처, 팀원 또는 나만 보는 일정',['거래처·고객','팀원과 함께','나만']),q('data','언제 해야 하나요?','정확한 날짜·시간·시간대를 적거나 나중에 결정할 수 있습니다.',['날짜가 정해졌어요','기간만 정해졌어요','아직 정하지 않았어요']),q('result','반복이나 알림이 필요한가요?','반복 범위와 알림 시점은 실제 기능 제공 전 다시 확인해야 합니다.',['한 번만','반복 일정 검토','알림 조건 검토']),extra]
  },
  APP:{
    name:'앱 제작',intro:'줄이고 싶은 업무부터 정리합니다. 고객이 개발 언어나 DB를 선택할 필요는 없습니다. 실행 앱 생성은 후속 범위입니다.',questions:[q('purpose','가장 줄이고 싶은 업무는요?','예: 매일 받는 주문을 한곳에 모으고 싶어요.',['기록·조회 반복','주문·신청 접수','업무 진행 확인']),q('details','어디에서 사용할까요?','기기와 인터넷 사용 가능 여부를 알려 주세요.',['PC·인터넷 사용','휴대폰·인터넷 사용','인터넷 없이 사용 필요']),q('audience','누가 이용하나요?','사용자마다 봐야 할 범위가 다르면 적어 주세요.',['나 혼자','사내 직원','외부 고객도']),q('data','무엇을 입력하고 어떤 결과를 보나요?','한 가지 실제 예시를 적으면 동작을 더 정확하게 정리할 수 있습니다.',['양식 입력 → 목록 조회','자료 선택 → 결과 확인','사진·메모 → 업무 기록']),q('result','첫 버전에서 꼭 필요한 결과는요?','기능을 많이 나열하기보다 먼저 완료해야 할 업무를 적어 주세요.',['입력·검색 흐름','상태 변경 흐름','업무 요약·화면 구성'])]
  }
};
export const excelSourceGuides={
 photo:[
  q('purpose','사진에서 어떤 기록을 옮길까요?','예: 수기로 적은 업체명·주소·연락처 목록. 기록의 의미나 약어만 알려 주세요.',['손글씨 기록을 그대로 전산화','인쇄된 표·캡처의 값 옮기기','표의 항목과 실제 값 유지']),
  q('data','사진 장수와 연결 관계를 알려 주세요.','예: 8장, 같은 목록의 연속 페이지입니다. 실제 장수는 다음 단계에서 선택한 파일로 확인합니다.',['한 장의 표','여러 장 · 각각 별도 표','여러 장 · 같은 목록의 이어지는 페이지']),
  q('result','사진의 가로·세로 기준은 무엇인가요?','예: 가로 제목은 날짜, 세로 제목은 상품입니다. 제목 행·열, 단위, 합계 위치를 알면 적어 주세요.',['첫 행은 항목 이름 · 아래는 기록','첫 행과 첫 열에 기준이 있는 교차표','제목 위치를 사진에서 찾아 주세요']),
  q('audience','옮긴 데이터를 어떻게 확인하고 싶으세요?','예: 업체명으로 검색하고 지역으로 필터. 보기 설정은 데이터 확인 후에도 바꿀 수 있습니다.',['검색·필터가 있는 목록','날짜별 기록 조회','항목별 합계와 그래프']),
  q('details','판독·계산 시 지킬 조건이 있나요?','예: 흐릿한 값은 비워 두고 알려 주세요. 수량 × 단가로 금액 열을 추가하세요.',['원본 값 보존 · 불명확한 값은 추측 금지','계산 열은 원본 옆에 추가','먼저 원본만 옮기고 이후 가공'])
 ],
 upload:[
  q('purpose','가져올 파일의 내용을 간단히 알려 주세요.','예: 업체 연락처 2만 건. 자료의 의미·주의점만 적어도 됩니다. 파일의 실제 열과 값은 다음 단계에서 읽습니다.',['현재 파일을 검색 가능한 웹 표로','기존 파일의 데이터 정리','기존 프로젝트에 데이터 추가·교체']),
  q('data','어느 범위와 구조를 가져오나요?','예: 업체목록과 지역코드 2개 시트. 머리글·합계·숨김 시트 등 주의할 부분이 있으면 알려 주세요.',['필요한 시트는 파일을 읽은 후 선택','여러 시트와 참조 수식 함께 유지','제목 행과 실제 데이터 범위 확인']),
  q('result','원본에서 유지할 기준이 있나요?','예: 업체코드는 앞자리 0을 유지. 날짜·금액 형식, 가로/세로 기준, 원본 열 순서를 적어 주세요.',['열 순서와 실제 값 유지','기존 수식도 유지','코드·전화번호는 문자로 보존']),
  q('audience','업로드 후 무엇을 찾거나 비교할까요?','예: 지역별 업체 검색, 담당자별 실적 그래프. 현재 파일의 실제 항목을 기준으로 적어 주세요.',['검색·필터·정렬로 조회','선택 항목의 합계·비교 그래프','가져온 내용 확인 후 결정']),
  q('details','정리하거나 새로 계산할 내용이 있나요?','예: #REF! 셀을 비우기, 수량 × 단가 열 추가. 중복 삭제·잘못된 값 변경은 제안을 확인하고 적용합니다.',['원본부터 확인 · 수정은 직접 결정','오류 셀을 찾아 처리 방법 제안','계산 열을 추가하되 원본 보존'])
 ],
 direct:[
  q('purpose','직접 만들 표의 이름과 용도는 무엇인가요?','예: 거래처별 납품 내역을 매일 기록합니다.',['반복해서 행을 추가하는 기록','목록·현황 관리','자유롭게 표 구성']),
  q('data','처음에 어떤 열이 필요하세요?','예: 날짜, 업체명, 품목, 수량, 단가. 붙여넣을 데이터가 있으면 편집 화면에서 바로 넣을 수 있습니다.',['열부터 직접 추가','복사한 표를 붙여넣기','AI에게 초안 열 구성 요청']),
  q('result','원하는 계산은 무엇인가요?','예: 수량 × 단가 = 공급가, 공급가 × 0.1 = 세금. 모르면 나중에 AI와 대화해도 됩니다.',['우선 입력할 값만 구성','계산 열을 함께 만들기','작성하면서 AI와 결정']),
  q('audience','조회할 때 어떤 기준이 필요한가요?','예: 날짜·담당자별 필터와 품목 검색. 기본 검색·정렬은 설정 없이 사용할 수 있습니다.',['기본 목록 조회','기간·분류별 확인','집계·그래프도 보기']),
  q('details','입력 규칙이나 주의할 점이 있나요?','숫자·날짜·단위, 빈 값 처리 등을 적어 주세요. 실제 데이터는 직접 입력한 값을 사용합니다.',['행을 계속 추가할 예정','추정한 실제 데이터 생성 금지','단위와 계산 방법을 표시'])
 ],
 guided:[
  q('purpose','대화로 어떤 표를 만들까요?','예: 매일 거래처별 주문을 기록하고 월별 매출을 보고 싶어요.',['업무 기록표 만들기','관리 목록 만들기','원하는 내용을 직접 설명']),
  q('data','이미 정한 항목이나 실제 데이터가 있나요?','예: 날짜, 거래처, 수량, 단가. 실제 데이터는 아래에 적거나 편집 화면에서 추가할 수 있습니다.',['항목만 정해 두었어요','실제 데이터를 입력할게요','필요한 열부터 함께 정하기']),
  q('result','어떤 초안을 만들까요?','빈 양식은 실제 데이터가 없는 상태로 만듭니다. 연습용 예시를 원하면 가상 데이터라고 구분합니다.',['반복 입력할 빈 양식','내가 제공한 실제 데이터로 구성','가상 예시 데이터로 연습']),
  q('audience','어떻게 조회하고 보고 싶으세요?','예: 담당자별 필터, 월별 매출 그래프, 업체명 검색.',['검색 가능한 목록','합계·평균이 있는 표','기록과 그래프 함께']),
  q('details','계산이나 조건을 말로 알려 주세요.','예: 수량과 단가를 곱한 금액 열, 날짜에서 월을 구하는 열. 모호한 부분은 AI가 다시 확인합니다.',['원본 입력과 계산 열을 구분','수식까지 포함한 초안','우선 단순하게 · 나중에 확장'])
 ]
};
export function createGuide(host,workflow,{onChange,onSummary,onMode}={}) {
 const guide={...(guides[workflow]||guides.WORD)};
 let steps=[],current=0,sourceMode=null;
 const nav=el('div','','guide-position'),body=el('div','','guide-question');host.append(nav,body);
 function show(index,focus=false){current=index;steps.forEach((s,i)=>s.box.hidden=i!==index);nav.textContent=`선택 조건 ${index+1} / ${steps.length} · ${guide.questions[index].title}`;if(focus)steps[index].input.focus();}
 function next(index){if(index+1>=steps.length){onSummary?.();return;}show(index+1,true);}
 function build(values={}){
  body.replaceChildren();steps=[];
  guide.questions.forEach((question,index)=>{
   const box=el('section','','draft-question'),heading=el('h3',question.title),hint=el('p',question.hint,'muted'),choices=el('div','','guide-choices');heading.id='question-heading-'+question.key;
   const label=el('label','선택하거나 직접 작성하세요. 입력하지 않아도 다음 단계로 갈 수 있습니다.','muted');label.htmlFor='answer-'+question.key;
   const input=el('textarea');input.id='answer-'+question.key;input.maxLength=2000;input.rows=3;input.value=values[question.key]||'';input.placeholder=question.hint;input.setAttribute('aria-labelledby',heading.id);
   question.choices.forEach(value=>choices.append(button(value,()=>{input.value=value;onChange?.();input.focus();},'button guide-choice')));
   input.addEventListener('input',()=>onChange?.());const actions=el('div','','actions'),previous=button('이전 조건',()=>show(Math.max(0,index-1),true));previous.disabled=index===0;
   actions.append(previous,button('이 조건 건너뛰기',()=>next(index),'button quiet'),button(index===guide.questions.length-1?'자료 준비로 →':'다음 조건',()=>next(index),'button primary'));
   box.append(heading,hint,choices,label,input,actions);body.append(box);steps.push({question,box,input});
  });show(0);
 }
 build();
 return {guide,open(key){const index=steps.findIndex(s=>s.question.key===key);if(index>=0)show(index,true);},resume(){show(Math.max(0,Math.min(current,steps.length-1)));},
 setSourceMode(mode){if(workflow!=='EXCEL')return;if(sourceMode===mode)return;const values=Object.fromEntries(steps.map(s=>[s.question.key,s.input.value]));sourceMode=mode;guide.questions=excelSourceGuides[mode]||excelSourceGuides.guided;build(values);},
 setMode(mode){if(workflow==='EXCEL')this.setSourceMode(mode);else this.resume();onMode?.(mode);},values:()=>Object.fromEntries(steps.map(s=>[s.question.key,s.input.value]))};
}

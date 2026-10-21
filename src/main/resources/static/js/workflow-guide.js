import {
  el,button
} from './ui.js?v=0.6.0';
export const fields=[['purpose','업무 목적'],['data','사용할 자료'],['result','원하는 결과'],['audience','사용할 사람'],['details','추가 조건']];
const q=(key,title,hint,choices)=>({
  key,title,hint,choices
});
const extra=q('details','꼭 지켜야 할 조건이 있나요?','단위·기간·표현·반드시 제외할 내용을 적어 주세요. 금액이나 날짜가 불명확하면 추측하지 않습니다.',['원본 항목과 순서를 유지','빈 양식부터 만들기','아직 정하지 않았어요']);
export const guides={
  EXCEL:{
    name:'엑셀',intro:'사진의 표를 읽거나, 업무를 설명하거나, 빈 표를 직접 만들 수 있습니다.',questions:[q('purpose','어떤 일을 표로 정리할까요?','예: 거래처별 납품내역을 기록하고 싶어요.',['매출·지출 기록','상품·재고 목록','직접 만든 기록표']),q('data','현재 어떤 자료가 있나요?','사진이 있으면 아래 원본 자료에서 선택하세요. 개인정보가 필요한지 먼저 살펴 주세요.',['사진·캡처 자료가 있어요','항목 이름만 정했어요','아직 자료가 없어요']),q('result','어떤 표부터 필요하세요?','데이터가 필요한지, 입력용 빈 양식인지 구분해 주세요.',['현재 자료가 들어간 표','반복 입력할 빈 양식','기존 표의 항목 개선']),q('audience','주로 누가 입력하거나 확인하나요?','예: 매장 직원이 매일 입력하고 관리자가 확인합니다.',['나 혼자','직원들과 함께','고객에게 전달']),extra]
  },
  WORD:{
    name:'워드',intro:'문서의 목적과 읽는 사람부터 정리합니다. 이번 버전은 검토된 업무 요약을 제공합니다.',questions:[q('purpose','무슨 문서가 필요하세요?','예: 거래처에 보낼 납품 제안서',['제안서·보고서','안내문·공지','업무 매뉴얼']),q('audience','누가 읽는 문서인가요?','읽는 사람과 원하는 반응을 알려 주세요.',['내부 직원','거래처·고객','외부 제출처']),q('data','어떤 내용을 담을까요?','이미 가진 글이나 사진은 원본 자료에서 선택할 수 있습니다.',['기존 자료를 다듬기','메모를 문서로 정리','처음부터 구성']),q('result','어떤 분량과 말투가 좋을까요?','정확한 페이지·문체가 정해져 있으면 직접 입력하세요.',['짧고 간결하게','근거를 자세하게','친절한 안내 형식']),extra]
  },
  HWP:{
    name:'한글',intro:'제출 목적과 지정 서식을 먼저 확인합니다. 이번 버전은 HWP 파일을 생성하지 않습니다.',questions:[q('purpose','어디에 제출할 내용인가요?','예: 기관 제출용 사업계획서',['기관 제출','사내 보고','고객 안내']),q('result','지정된 서식이 있나요?','필수 항목·글자 크기·분량이 있으면 적어 주세요.',['지정 서식이 있어요','내용 구성만 필요해요','제출 기준을 확인해야 해요']),q('data','어떤 자료를 바탕으로 쓸까요?','읽을 수 있는 JPG·PNG·TXT 자료를 선택하거나 내용을 적어 주세요.',['기존 글·메모','사진·캡처','아직 자료가 없어요']),q('audience','누가 검토하나요?','제출처가 요구하는 기준을 알면 함께 적어 주세요.',['내부 검토자','외부 기관','거래처']),extra]
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
export function createGuide(host,workflow,{
  onChange,onSummary,onMode
}
={
}) {
  const guide=guides[workflow]||guides.WORD;
  const nav=el('div','','guide-position'),body=el('div','','guide-question'),steps=[];
  host.append(nav,body);
  guide.questions.forEach((question,index)=>{
    const box=el('section','','draft-question'),heading=el('h3',question.title),hint=el('p',question.hint,'muted'),choices=el('div','','guide-choices');
    heading.id='question-heading-'+question.key;
    const label=el('label','직접 입력하거나 선택한 답을 자세히 고칠 수 있습니다.','muted');
    label.htmlFor='answer-'+question.key;
    const input=el('textarea');
    input.id='answer-'+question.key;
    input.maxLength=2000;
    input.placeholder=question.hint;
    input.setAttribute('aria-labelledby',heading.id);
    question.choices.forEach(value=>choices.append(button(value,()=>{
      input.value=value;
      onChange?.();
      input.focus();
    },'button guide-choice')));
    choices.append(button('직접 입력',()=>input.focus(),'button guide-choice quiet'));
    input.addEventListener('input',()=>onChange?.());
    const actions=el('div','','actions');
    const previous=button('이전 답 보기',()=>show(Math.max(0,index-1),true));
    previous.disabled=index===0;
    actions.append(previous,button('잘 모르겠어요 · 건너뛰기',()=>next(index),'button quiet'),button(index===guide.questions.length-1?'요약 확인':'다음으로',()=>next(index),'button primary'));
    box.append(heading,hint,choices,label,input,actions);
    box.hidden=true;
    body.append(box);
    steps.push({
      question,box,input
    });
  });
  let current=0;
  function show(index,focus=false){
    current=index;
    steps.forEach((s,i)=>s.box.hidden=i!==index);
    nav.textContent=`지금: ${guide.questions[index].title} · 이미 작성한 답은 다음 진행에서 건너뜁니다.`;
    if(focus)steps[index].input.focus();
  }
  function next(index){
    const found=steps.findIndex((s,i)=>i>index&&!s.input.value.trim());
    if(found<0){
      nav.textContent='답변 정리 완료 · 요약에서 필요한 답만 다시 수정하세요.';
      steps.forEach(s=>s.box.hidden=true);
      onSummary?.();
    }
    else show(found,true);
  }
  show(0);
  return {
    guide,open:key=>{
      const index=steps.findIndex(s=>s.question.key===key);
      if(index>=0)show(index,true);
    },resume(){
      const index=steps.findIndex(s=>!s.input.value.trim());
      if(index<0){
        steps.forEach(s=>s.box.hidden=true);
        nav.textContent='이전 답변을 유지했습니다. 요약에서 필요한 항목만 수정하세요.';
      }
      else show(index);
    },setMode(mode){
      if(mode==='direct'){
        steps.forEach(s=>s.box.hidden=true);
        nav.textContent='직접 만들기 · 표 편집에서 열을 추가하고 입력하세요. 질문은 요약의 수정 버튼으로 열 수 있습니다.';
      }
      else{
        this.resume();
      }
      onMode?.(mode);
    },values:()=>Object.fromEntries(steps.map(s=>[s.question.key,s.input.value]))
  };
}

// Presentation helpers. Never execute provider-supplied code or formulas in the browser.
export function columnLetter(index){let n=index+1,text='';while(n>0){n--;text=String.fromCharCode(65+n%26)+text;n=Math.floor(n/26);}return text;}
export function editExamples(sheet,mode='direct'){
 const data=sheet?.data||{columns:[],rows:[]},columns=data.columns||[],numeric=columns.map((c,i)=>({...c,letter:columnLetter(i)})).filter(c=>c.type==='NUMBER');
 if(!columns.length)return mode==='photo'?[['사진을 표로 옮기기','선택한 사진의 제목행과 행·열 기준을 읽어 실제 값만 표로 옮겨 주세요. 판독이 어려운 값은 추측하지 말고 셀 위치를 질문해 주세요.']]:[['필요한 표 함께 만들기','현재 기본정보의 목적과 지침을 기준으로 필요한 열과 자료 형태를 제안해 주세요. 모호한 내용부터 물어보고 실제 데이터는 임의로 만들지 마세요.'],['열만 먼저 만들기','현재 기본정보에 맞는 열 이름과 자료형을 구성해 주세요. 데이터 행은 비워 두고 직접 입력할 수 있게 해 주세요.']];
 const examples=[['새 행 추가','다음 자료를 현재 표의 새 행으로 추가해 주세요. 기존 행은 유지하고 계산열 수식은 새 행에도 적용해 주세요. 아래에 실제 자료가 없으면 먼저 물어봐 주세요.\n추가할 실제 자료: '],['문제 수식 임시 처리','#REF!, #DIV/0! 등 문제가 있는 수식의 위치와 건수를 보여 주고, 원본 수식을 보관한 상태로 잠시 멈춰 빈 값으로 임시 처리해 주세요. 나중에 수식 검토에서 원본을 복원할 수 있어야 합니다. 정상 수식과 값은 유지하세요.'],['오류 수식 제거','#REF!, #DIV/0! 등 현재 시트의 오류 위치와 건수를 확인하고, 오류인 셀의 수식을 제거해 빈 값으로 바꾸는 변경안을 보여 주세요. 임시 보관 없이 제거하는 작업임을 표시하고 정상 값은 유지하세요.'],['셀·범위 비우기','현재 시트의 A2 셀만 비워 주세요. 행이나 다른 셀은 이동하지 마세요.'],['열 추가','현재 시트 마지막에 ‘메모’ 텍스트 열을 추가하고 기존 데이터는 유지해 주세요.']];
 if(numeric.length>=2){const [a,b]=numeric;examples.unshift(['계산열 만들기',`‘계산금액’ 열을 추가해 ${a.letter}열 ‘${a.label}’ × ${b.letter}열 ‘${b.label}’을 계산해 주세요. 기존 행 전체에 적용하고 이후 추가되는 행에도 같은 수식이 적용되게 해 주세요.`]);}
 else examples.unshift(['계산 방법 함께 정하기',`현재 열(${columns.slice(0,8).map(c=>c.label).join(', ')})을 기준으로 필요한 계산열을 만들고 싶어요. 계산할 값과 계산 방법을 먼저 물어봐 주세요.`]);
 return examples;
}
export function describeOperation(op={}){
 const kinds={SET_CELL:'셀 값 변경',CLEAR_RANGE:'셀 범위 비우기',CLEAR_ERRORS:'오류 셀 내용·수식 제거',PAUSE_ERRORS:'문제 수식 임시 처리 · 원본 수식 보존',ADD_COLUMN:'새 열 추가',ADD_CALCULATED_COLUMN:'계산열 추가',DELETE_ROWS:'행 삭제',APPEND_ROWS:'새 데이터 행 추가'};
 const title=kinds[op.kind]||'데이터 편집',range=op.range==='ALL'?'현재 시트 전체':op.range||'현재 시트';
 let detail=range;
 if(op.kind==='SET_CELL')detail=`${range} → ${op.value===''?'빈 값':op.value}`;
 if(op.kind==='CLEAR_RANGE')detail=`${range}의 내용만 비웁니다. 행·열 위치는 유지합니다.`;
 if(op.kind==='CLEAR_ERRORS')detail=`${range} 중 ${op.formula&&op.formula!=='ALL'?op.formula+' ':''}오류인 셀만 ${op.value===''?'빈 값':`‘${op.value}’`}으로 바꿉니다. 해당 셀의 수식은 제거되며 임시 보관하지 않습니다.`;
 if(op.kind==='PAUSE_ERRORS')detail=`${range} 중 ${op.formula&&op.formula!=='ALL'?op.formula+' ':''}문제 수식을 잠시 멈추고 ${op.value===''?'빈 값':`‘${op.value}’`}으로 표시합니다. 원본 수식은 보관하며 원본 편집 → 수식 검토 → 보관한 원본 수식 복원에서 되돌릴 수 있습니다. 정상 값과 수식은 유지합니다.`;
 if(op.kind==='ADD_COLUMN')detail=`${op.label} · ${({TEXT:'텍스트',NUMBER:'숫자',DATE:'날짜'})[op.type]||op.type||'텍스트'} · ${op.value?`초깃값: ${op.value}`:'빈 값으로 시작'}`;
 if(op.kind==='ADD_CALCULATED_COLUMN')detail=`${op.label} · 수식: ${op.formula} · {row}는 각 행 번호입니다. 새 행에도 자동 적용합니다.`;
 if(op.kind==='APPEND_ROWS')detail=`기존 데이터 다음에 ${(op.rows||[]).length.toLocaleString()}행을 추가합니다. 기존 행은 보존하며 계산열·반복 수식은 새 행에도 적용합니다. 한 번의 처리 단위는 최대 200행·4,000셀입니다. 이어 처리 작업은 적용한 뒤 다음 구간을 요청해 누적합니다.`;
 if(op.kind==='DELETE_ROWS')detail=`Excel ${range}행을 삭제합니다. 아래 행과 관련 수식 참조가 조정됩니다.`;
 return {title,detail};
}
export function conversationItems(runs,selectedRun){
 const byId=new Map((runs||[]).map(r=>[r.id,r]));if(selectedRun)byId.set(selectedRun.id,selectedRun);
 return [...byId.values()].sort((a,b)=>String(a.createdAt||'').localeCompare(String(b.createdAt||''))).slice(-30);
}

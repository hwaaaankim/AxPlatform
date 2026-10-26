import {el,button} from './ui.js?v=0.10.1';

const SAMPLE_LIMIT=5;
const EXCEL_ERROR=/^#(?:REF!|DIV\/0!|VALUE!|NAME\?|N\/A|NUM!|NULL!|SPILL!|CALC!|GETTING_DATA|CONNECT!|BLOCKED!|FIELD!|UNKNOWN!|BUSY!|DATA!)$/i;
const definitions=[
 ['blankRows','완전히 빈 행','행','입력할 자리를 미리 만든 행일 수 있습니다. 필요하지 않은 행인지 확인하세요.'],
 ['duplicateRows','값이 같은 반복 행','행','첫 번째 행을 제외한 반복 횟수입니다. 같은 거래·기록이 실제로 반복된 것일 수 있으므로 확인 후 처리하세요.'],
 ['duplicateHeaders','이름이 같은 열','열','같은 제목의 두 번째 열부터 표시합니다. 검색·계산할 때 구별할 수 있는 이름이 필요한지 확인하세요.'],
 ['whitespaceCells','앞뒤 공백이 있는 값','셀','검색 결과가 달라질 수 있습니다. 원본에서 의도한 공백인지 먼저 확인하세요.'],
 ['formulaErrors','오류 표기·계산 확인','셀','Excel 오류 표기 또는 이미 기록된 계산 문제입니다. 실제 문자인지 수식 문제인지 확인하세요.'],
 ['pausedFormulas','임시 처리된 수식','셀','원본 수식이 보관된 상태입니다. 현재 임시 값으로 조회해도 되는지 확인하세요.']
];
const valueAt=(row,index)=>row?.[index]??'';
const own=(object,key)=>Object.prototype.hasOwnProperty.call(object,key);
function letter(index){let text='';for(let n=index+1;n;n=Math.floor((n-1)/26))text=String.fromCharCode(65+(n-1)%26)+text;return text;}
function position(address){const match=/^([A-Z]+)([1-9][0-9]*)$/.exec(address);if(!match)return null;let column=0;for(const char of match[1])column=column*26+char.charCodeAt(0)-64;return {column:column-1,row:Number(match[2])};}
function sameRow(left,right,width){for(let i=0;i<width;i++)if(valueAt(left,i)!==valueAt(right,i))return false;return true;}

// Read-only and linear in the amount of source text. Hash buckets retain row
// indexes rather than another copy of the workbook; exact comparison resolves
// any hash collision before a row can be reported as a repeat.
export function checkWorkbookQuality(book){
 const totals=Object.fromEntries(definitions.map(([key])=>[key,0])),sheets=[];
 let rowCount=0,cellCount=0,ignoredFormulaTemplateRows=0;
 for(const sheet of book?.sheets||[]){
  const data=sheet.data||{},columns=data.columns||[],rows=data.rows||[],width=columns.length,letters=columns.map((_,i)=>letter(i));
  const checks=definitions.map(([key,label,unit,advice])=>({key,label,unit,advice,count:0,locations:[],omitted:0})),byKey=Object.fromEntries(checks.map(item=>[item.key,item]));
  const add=(key,location)=>{const check=byKey[key];check.count++;totals[key]++;if(check.locations.length<SAMPLE_LIMIT)check.locations.push(location);};
  const formulas=data.excel?.formulas||{},issues=data.excel?.issues||{},paused=data.excel?.paused||{},computed=columns.some(column=>!!column.formula),headers=new Map(),seen=new Map();
  const hasIssues=Object.keys(issues).length>0,hasFormulaMetadata=Object.keys(formulas).length>0||Object.keys(paused).length>0;
  let templateRows=0;
  columns.forEach((column,index)=>{
   const label=String(column.label??''),address=letters[index]+'1';
   if(label!==label.trim())add('whitespaceCells',{address,row:1});
   if(label!==''){if(headers.has(label))add('duplicateHeaders',{address,row:1,originalAddress:headers.get(label)});else headers.set(label,address);}
  });
  for(let index=0;index<rows.length;index++){
   const row=rows[index],number=index+2;let empty=true,hasFormula=computed,hash1=2166136261,hash2=5381;
   for(let column=0;column<width;column++){
    const value=valueAt(row,column),text=String(value),address=hasIssues||hasFormulaMetadata?letters[column]+number:null;
    if(value!=='')empty=false;
    if(text!==text.trim())add('whitespaceCells',{address:address||letters[column]+number,row:number});
    if(text[0]==='#'&&EXCEL_ERROR.test(text)||hasIssues&&own(issues,address))add('formulaErrors',{address:address||letters[column]+number,row:number});
    if(hasFormulaMetadata&&(own(formulas,address)||own(paused,address)))hasFormula=true;
    hash1=Math.imul(hash1^text.length,16777619);hash2=Math.imul(hash2,33)^text.length;
    for(let char=0;char<text.length;char++){const code=text.charCodeAt(char);hash1=Math.imul(hash1^code,16777619);hash2=Math.imul(hash2,33)^code;}
   }
   if(empty){if(width){if(hasFormula)templateRows++;else add('blankRows',{address:'A'+number,row:number});}continue;}
   const key=(hash1>>>0)+':'+(hash2>>>0),bucket=seen.get(key);
   const original=bucket?.find(previous=>sameRow(rows[previous],row,width));
   if(original!==undefined)add('duplicateRows',{address:'A'+number,row:number,originalAddress:'A'+(original+2),originalRow:original+2});
   else if(bucket)bucket.push(index);else seen.set(key,[index]);
  }
  // Header formulas may also have existing diagnostics. Data-cell diagnostics
  // were already included above, including those with an empty cached value.
  for(const address of Object.keys(issues)){const p=position(address);if(p?.row===1&&p.column<width)add('formulaErrors',{address,row:1});}
  for(const address of Object.keys(paused)){const p=position(address);if(p&&p.column<width&&p.row<=rows.length+1)add('pausedFormulas',{address,row:p.row});}
  checks.forEach(check=>check.omitted=check.count-check.locations.length);
  sheets.push({sheetId:sheet.id,sheetName:sheet.name,rowCount:rows.length,columnCount:width,checks:checks.filter(check=>check.count),ignoredFormulaTemplateRows:templateRows});
  rowCount+=rows.length;cellCount+=rows.length*width;ignoredFormulaTemplateRows+=templateRows;
 }
 return {sheetCount:sheets.length,rowCount,cellCount,totals,sheets,ignoredFormulaTemplateRows,totalFindings:Object.values(totals).reduce((sum,count)=>sum+count,0),sampleLimit:SAMPLE_LIMIT};
}

function locationText(location){return location.address+(location.originalAddress?' · 첫 위치 '+location.originalAddress:'');}
function requestText(sheet,check){
 const locations=check.locations.map(locationText).join(', ');
 return `현재 시트 '${sheet.sheetName}'의 저장 전 점검에서 '${check.label}' ${check.count}${check.unit}를 확인했습니다. 확인 위치: ${locations}${check.omitted?` (이 밖에 ${check.omitted}${check.unit}가 더 있습니다)`:''}. 이 위치부터 원본을 보존하며 정리 방법을 설명하고, 필요한 변경만 제안해 주세요. 숫자처럼 보이는 코드의 앞자리 0과 실제 입력값을 유지하세요. 반복 행이나 빈 행은 의도한 기록·입력 양식일 수 있으므로 임의로 삭제하지 말고 먼저 확인해 주세요. ${check.advice}`;
}

// Invoke on review entry, not on each keystroke. No network request is made;
// callbacks only open an existing editor or fill its AI request composer.
export function renderDataQuality(host,{workbook,edit,askAi}={}){
 const report=checkWorkbookQuality(typeof workbook==='function'?workbook():workbook);
 host.replaceChildren();host.classList.add('data-quality');
 const heading=el('div','','data-quality-heading'),title=el('h3','저장 전 데이터 점검'),badge=el('span',report.totalFindings?'확인할 항목이 있습니다':'점검 완료','data-quality-badge');heading.append(title,badge);
 const scope=el('p',`${report.sheetCount}개 시트 · ${report.rowCount.toLocaleString()}행을 확인했습니다. 빈 선택 항목이나 숫자 형태의 코드 값은 오류로 판단하지 않습니다.`,'muted');host.append(heading,scope);
 if(!report.totalFindings){host.append(el('p','빈 행·동일 행·중복 열 이름·앞뒤 공백·오류 표기에서 확인할 항목을 찾지 못했습니다. 원본 대조와 계산 결과 확인은 별도로 진행하세요.','data-quality-clear'));if(report.ignoredFormulaTemplateRows)host.append(el('small',`수식이 준비된 빈 행 ${report.ignoredFormulaTemplateRows.toLocaleString()}개는 입력 양식으로 보고 빈 행 집계에서 제외했습니다.`,'muted'));return report;}
 const counts=el('div','','data-quality-counts');for(const [key,label,unit] of definitions)if(report.totals[key])counts.append(el('span',`${label} ${report.totals[key].toLocaleString()}${unit}`,'data-quality-count'));host.append(counts,el('p','자동으로 값을 바꾸거나 삭제하지 않습니다. 확인이 필요한 위치만 살펴보고, 그대로 저장해도 됩니다. 같은 셀이 여러 항목에 포함될 수 있습니다.','muted'));
 const details=el('details','','data-quality-details'),summary=el('summary','확인 위치와 정리 방법 보기'),body=el('div','','data-quality-body');details.append(summary,body);host.append(details);
 let rendered=false;
 const render=()=>{if(rendered)return;rendered=true;for(const sheet of report.sheets.filter(item=>item.checks.length)){
  const section=el('section','','data-quality-sheet');section.append(el('h4',sheet.sheetName));
  for(const check of sheet.checks){
   const card=el('div','','data-quality-check'),head=el('div','','data-quality-check-heading');head.append(el('strong',`${check.label} ${check.count.toLocaleString()}${check.unit}`));
   if(askAi)head.append(button('AI에게 정리 방법 요청',()=>askAi(requestText(sheet,check),sheet.sheetId),'button small quiet'));
   card.append(head,el('p',check.advice,'muted'));const list=el('ul','','data-quality-locations');
   for(const location of check.locations){const row=el('li');row.append(el('span',locationText(location)));if(edit){const action=button('셀 확인',()=>edit(sheet.sheetId,location.address),'button small');action.setAttribute('aria-label',sheet.sheetName+' '+location.address+' 셀 확인');row.append(action);}list.append(row);}card.append(list);
   if(check.omitted)card.append(el('small',`앞 ${check.locations.length}곳을 표시했습니다. 나머지 ${check.omitted.toLocaleString()}${check.unit}도 같은 기준으로 집계했습니다.`,'muted'));section.append(card);
  }body.append(section);
 }if(report.ignoredFormulaTemplateRows)body.append(el('p',`수식이 준비된 빈 행 ${report.ignoredFormulaTemplateRows.toLocaleString()}개는 입력 양식으로 보고 빈 행 집계에서 제외했습니다.`,'muted'));};
 details.addEventListener('toggle',()=>{if(details.open)render();});
 return report;
}

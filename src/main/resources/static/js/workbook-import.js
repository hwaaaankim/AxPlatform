import {el,button,message} from './ui.js?v=0.6.0';
import {makePreviewTable} from './sheet-editor.js?v=0.6.0';
export function renderWorkbookImport(host,report,context){
 host.replaceChildren();const heading=el('h3','가져올 시트 확인'),info=el('p','','help-line'),list=el('div','','import-sheet-list'),agree=el('input'),label=el('label','','check'),actions=el('div','','actions');agree.type='checkbox';label.append(agree,el('span','시트별 제외 항목·수식의 계산값 변환·숨김 여부를 확인했습니다. 선택한 시트만 편집 초안에 넣습니다.'));
 const snapshot=JSON.stringify(context.editor.workbook()),choices=[];
 const selectAll=button('읽을 수 있는 표시 시트 전체 선택',()=>{choices.forEach(({cb,source})=>{if(!cb.disabled)cb.checked=source.visibility==='visible';});summary();});
 const clear=button('선택 해제',()=>{choices.forEach(({cb})=>cb.checked=false);summary();});
 report.sheets.forEach(source=>{
  const item=el('section','','subpanel import-sheet'),row=el('div','','panel-heading'),cb=el('input'),name=el('label','','check'),meta=el('span',source.status==='BLOCKED'?'가져오기 불가':source.status==='EMPTY'?'빈 시트':`${source.headers.length}열 · ${source.rows.length}행`,'badge');cb.type='checkbox';cb.disabled=source.status==='BLOCKED';cb.checked=!cb.disabled&&source.visibility==='visible';name.append(cb,el('strong',source.sheetName));row.append(name,meta);item.append(row);cb.addEventListener('change',summary);choices.push({cb,source});
  for(const issue of source.issues||[]){const notice=el('div','',issue.severity==='ERROR'?'notice error':'notice warning');notice.append(el('strong',issue.message),el('p',issue.remedy));item.append(notice);}
  if(source.status!=='BLOCKED'){
   const detail=el('details'),title=el('summary','셀 값 미리보기 · 가져오기 안내');detail.append(title);
   (source.warnings||[]).forEach(t=>detail.append(el('p',t,'muted')));
   if(source.headers.length)detail.append(makePreviewTable({columns:source.headers.map((h,i)=>({label:h||'열 '+(i+1)})),rows:source.rows}));
   else detail.append(el('p','빈 시트로 추가한 뒤 열을 직접 만들 수 있습니다.','muted'));
   if(source.headers.length&&context.mapCurrent)detail.append(button('이 시트만 현재 시트의 열에 연결',()=>context.mapCurrent(source),'button'));
   item.append(detail);
  }
  list.append(item);
 });
 const apply=button('선택한 시트를 프로젝트에 추가',()=>{
  try{if(!agree.checked)throw Error('시트별 안내를 확인한 뒤 확인란을 선택하세요.');const selected=choices.filter(x=>x.cb.checked&&!x.cb.disabled);if(!selected.length)throw Error('가져올 시트를 선택하세요.');if(JSON.stringify(context.editor.workbook())!==snapshot)throw Error('미리보기 후 표가 바뀌었습니다. 입력은 유지했습니다. 파일을 다시 읽어 적용 범위를 확인하세요.');
   const sheets=selected.map(({source:s})=>({name:s.sheetName,data:{columns:s.headers.map((h,i)=>({id:'c_'+crypto.randomUUID().replaceAll('-','').slice(0,18),label:h.trim()||'열 '+(i+1),type:'TEXT',unit:'',width:160})),rows:s.rows,rowIds:s.rows.map(()=>crypto.randomUUID())},sourceAssetIds:[context.assetId]}));
   context.editor.addSheets(sheets);apply.disabled=true;context.done(selected.length);
  }catch(error){message(info,error.message,true);}
 },'button primary');
 function summary(){const count=choices.filter(x=>x.cb.checked&&!x.cb.disabled).length;message(info,`원본 ${report.sheets.length}시트 · 선택 ${count}시트 · 가져오기 불가 ${choices.filter(x=>x.cb.disabled).length}시트`);apply.disabled=count===0;}
 actions.append(selectAll,clear);host.append(heading,el('p','정상 시트는 한 번에 가져옵니다. 같은 이름은 번호를 붙이고, 기존 시트의 데이터는 유지합니다. 숫자·날짜는 우선 문자로 읽으므로 편집에서 열 형식을 확인하세요.','muted'),actions,info,list,label,apply);summary();
}

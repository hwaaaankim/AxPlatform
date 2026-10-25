import {withLoading} from './loading.js?v=0.9.1';
import {formulaReview} from './formula-review.js?v=0.9.1';
import {PAGE_SIZE,letter,cellText,cellStyle,orderedRows,pagedPreview,projectColumns,projectRows} from './table-view.js?v=0.9.1';
import {asWorkbook,emptyTable,workbookControls,sheetName,uniqueSheetName} from './workbook-controls.js?v=0.9.1';
import {enhanceDateInput} from './date-picker.js?v=0.9.1';
import {
  request
} from './request.js?v=0.9.1';
import {
  el,button,message
} from './ui.js?v=0.9.1';
import {
  editState
} from './edit-state.js?v=0.9.1';
const copy=value=>JSON.parse(JSON.stringify(value));
export function reorderColumns(sheet,ids) {
  if(ids.length!==sheet.columns.length||new Set(ids).size!==ids.length||ids.some(id=>!sheet.columns.some(c=>c.id===id)))throw new Error('열 구성이 서로 다릅니다.');
  const positions=ids.map(id=>sheet.columns.findIndex(c=>c.id===id));
  return {
    ...sheet,presentation:projectColumns(sheet,positions),view:undefined,columns:positions.map(i=>copy(sheet.columns[i])),rows:sheet.rows.map(row=>positions.map(i=>row[i]??''))
  };
}
export function removeColumn(sheet,id){
  const keep=sheet.columns.filter(c=>c.id!==id).map(c=>c.id);
  const positions=keep.map(value=>sheet.columns.findIndex(c=>c.id===value));
  return {
    ...sheet,presentation:projectColumns(sheet,positions),view:undefined,columns:positions.map(i=>copy(sheet.columns[i])),rows:keep.length?sheet.rows.map(row=>positions.map(i=>row[i]??'')):[],rowIds:keep.length?[...(sheet.rowIds||[])]:[]
  };
}
export const makePreviewTable=pagedPreview;
export function createSheetEditor(host,context) {
  let state={
    version:-1,draftVersion:0,sheet:{
      columns:[],rows:[],rowIds:[]
    }
  },working=copy(state.sheet),dirty=false,selectedColumn=null,selectedRows=new Set(),pending=false,cache=editState(context.account(),context.business(),(context.id()||'new-EXCEL')+'-sheet'),loadedId=context.id()||'new-EXCEL',cacheRestored=false;
  let book=asWorkbook(state.sheet),bookControls;
  let page=0,cacheTimer=null,visible=[],revision=0,calcTimer=null,calculationPromise=null,selectedCell=null;
  const formulaHost=el('div'),formulaBar=el('div','','cell-formula-bar'),cellAddress=el('input'),cellExpression=el('input'),calculationStatus=el('span','','calculation-status');let formulaPanel;
  cellAddress.placeholder='A12388';cellAddress.setAttribute('aria-label','Excel 셀 주소');cellExpression.placeholder='셀 선택 후 값 또는 =SUM(B2:C2)';cellExpression.maxLength=8193;cellExpression.setAttribute('aria-label','선택 셀 값 또는 Excel 수식');
  function syncBook(){book.sheets.find(s=>s.id===book.activeSheetId).data=working;return book;}
  function restoreBook(value){selectedCell=null;cellExpression.value="";cellAddress.value="";book=asWorkbook(value);working=copy(book.sheets.find(s=>s.id===book.activeSheetId).data);selectedRows.clear();selectedColumn=working.columns[0]?.id||null;}
  function selectSheet(id){if(pending)return;selectedCell=null;cellExpression.value="";cellAddress.value="";page=0;syncBook();const target=book.sheets.find(s=>s.id===id);if(!target)throw Error('시트를 찾을 수 없습니다.');book.activeSheetId=id;working=copy(target.data);selectedRows.clear();selectedColumn=working.columns[0]?.id||null;render();context.onSheetChange?.();}
  function addSheets(entries,{replaceEmpty=true}={}){
    if(!editable('schema.update'))throw Error('시트 추가 권한 또는 저장 중 상태를 확인하세요.');
    if(entries.some(e=>e.data?.rows?.length)&&(!editable('rows.create')||!editable('cells.update')))throw Error('데이터가 있는 시트를 추가하려면 행 추가·셀 편집 권한이 필요합니다.');
    syncBook();const replace=replaceEmpty&&book.sheets.length===1&&!book.sheets[0].data.columns.length&&!book.sheets[0].sourceAssetIds.length;
    if(!entries.length||book.sheets.length+entries.length-(replace?1:0)>20)throw Error('프로젝트에는 최대 20개 시트를 담을 수 있습니다. 선택 수를 줄이거나 새 프로젝트로 나누세요.');
    const next=replace?[]:[...book.sheets],ids=[];const crossReferences=entries.some(e=>Object.values(e.data?.excel?.formulas||{}).some(f=>f.replace(/"(?:[^"]|"")*"/g,'').includes('!')));if(crossReferences&&entries.some(e=>next.some(s=>s.name.toLowerCase()===String(e.name||'시트').toLowerCase())))throw Error('시트 사이 수식이 있는 파일의 이름이 현재 시트와 겹칩니다. 새 프로젝트에서 가져오거나 현재 시트 이름을 바꾼 뒤 추가하세요. 다른 데이터에 잘못 연결하지 않도록 가져오기를 중단했습니다.');
    for(const [i,entry] of entries.entries()){const id=replace&&i===0?book.sheets[0].id:'s_'+crypto.randomUUID().replaceAll('-','');next.push({id,name:uniqueSheetName(entry.name,next.map(s=>s.name)),data:copy(entry.data||emptyTable()),sourceAssetIds:[...(entry.sourceAssetIds||[])]});ids.push(id);}
    book.sheets=next;book.activeSheetId=ids[0];working=copy(next.find(s=>s.id===ids[0]).data);selectedRows.clear();selectedColumn=working.columns[0]?.id||null;mark();render();context.onSheetChange?.();return ids;
  }
  const notice=el('p','','help-line'),toolbar=el('div','','actions sheet-toolbar'),grid=el('div','','sheet-grid table-scroll'),details=el('details','','sheet-properties'),summary=el('summary','선택한 열 자세히 조정'),properties=el('div'),footer=el('div','','actions sheet-actions');
  notice.setAttribute('role','status');
  notice.setAttribute('aria-live','polite');
  details.append(summary,properties);
  const addColumn=button('열 추가',()=>{
    if(working.columns.length>=100||(working.columns.length+1)*working.rows.length>1000000)return message(notice,'최대 100열·시트당 100만 셀입니다.',true);
    const id='c_'+crypto.randomUUID().replaceAll('-','').slice(0,20);
    working.columns.push({
      id,label:'새 항목 '+(working.columns.length+1),type:'TEXT',unit:'',width:160
    });
    working.rows.forEach(row=>row.push(''));
    selectedColumn=id;
    mark();
    render();
    details.open=true;
  }),addRow=button('행 추가',()=>{
    if(!working.columns.length)return message(notice,'먼저 열을 하나 추가하세요.',true);
    if(working.rows.length>=50000||(working.rows.length+1)*working.columns.length>1000000)return message(notice,'최대 50,000행·시트당 100만 셀입니다.',true);
    working.rows.push(working.columns.map(()=>''));
    working.rowIds.push(crypto.randomUUID());
    working.view={};page=Math.floor((working.rows.length-1)/PAGE_SIZE);
    mark();
    renderGrid();
  }),deleteRows=button('선택한 행 삭제',()=>{
    if(!selectedRows.size)return message(notice,'지울 행의 번호 옆 선택란을 선택하세요.',true);
    if(!confirm(`선택한 ${selectedRows.size}행을 표 초안에서 삭제할까요? 서버 적용 전까지 확정되지 않습니다.`))return;
    const kept=working.rows.map((_,i)=>i).filter(i=>!selectedRows.has(i));if(hasExcel()){changeStructure({rows:kept}).catch(fail);return;}working.presentation=projectRows(working,kept);
    working.rows=working.rows.filter((_,index)=>!selectedRows.has(index));
    working.rowIds=working.rowIds.filter((_,index)=>!selectedRows.has(index));
    selectedRows.clear();
    mark();
    renderGrid();
  },'button danger'),apply=button('전체 시트 저장',()=>save().catch(fail),'button primary'),undo=button('이전 통합문서로 되돌리기',()=>restorePrevious().catch(fail)),discard=button('전체 시트 변경 취소',()=>{
    if(dirty&&!confirm('모든 시트의 미적용 변경을 버리고 마지막 서버 저장본으로 돌아갈까요? 다른 업무 입력은 유지합니다.'))return;
    restoreBook(state.workbook||state.sheet);
    dirty=false;
    cache?.clear();
    selectedRows.clear();
    render();
    context.onLocalChange?.();
  }),download=button('현재 데이터 XLSX',()=>downloadFile(false).catch(fail)),template=button('빈 양식 XLSX',()=>downloadFile(true).catch(fail));
  toolbar.append(addColumn,addRow,deleteRows);
  footer.append(apply,undo,discard,download,template);
  const goto=button('셀로 이동',()=>{try{goTo(book.activeSheetId,cellAddress.value.trim().toUpperCase());}catch(e){fail(e);}},'button small'),applyFormula=button('값·수식 적용',()=>{try{applyExpression();}catch(e){fail(e);}},'button small primary');
  formulaBar.append(cellAddress,goto,el('span','fx'),cellExpression,applyFormula,calculationStatus);
  host.append(toolbar,notice,formulaHost,formulaBar,grid,details,footer);
  cellAddress.addEventListener('keydown',e=>{if(e.key==='Enter'){e.preventDefault();goto.click();}});cellExpression.addEventListener('keydown',e=>{if(e.key==='Enter'){e.preventDefault();applyFormula.click();}});
  formulaPanel=formulaReview(formulaHost,{book:()=>syncBook(),jump:goTo,canEdit:()=>editable('formula.update'),process:action=>recalculate(action)});
  function hasExcel(){return syncBook().sheets.some(s=>Object.keys(s.data.excel?.formulas||{}).length||Object.keys(s.data.excel?.paused||{}).length);}
  function metadata(){return working.excel??={formulas:{},values:{},paused:{},issues:{}};}
  function addressAt(r,c){return letter(c)+(r+1);}
  function coordinates(address){const m=/^([A-Z]{1,2})([1-9][0-9]{0,4})$/.exec(address);if(!m)throw Error('A12388처럼 Excel 셀 주소를 입력하세요.');let c=0;for(const v of m[1])c=c*26+v.charCodeAt(0)-64;const r=Number(m[2])-1;if(r>working.rows.length||c>working.columns.length)throw Error('현재 시트 범위 안의 셀 주소를 입력하세요.');return {r,c:c-1};}
  function chooseCell(r,c){selectedCell={r,c};const address=addressAt(r,c);cellAddress.value=address;const f=working.excel?.formulas?.[address],value=r===0?(working.columns[c].sourceHeader??working.columns[c].label):working.rows[r-1][c];cellExpression.value=f?'='+f:value;cellExpression.readOnly=!!working.columns[c].formula;applyFormula.disabled=!editable('cells.update')||!!working.columns[c].formula;}
  function goTo(id,address){if(pending)throw Error('처리가 끝난 뒤 셀로 이동하세요.');if(id!==book.activeSheetId)selectSheet(id);const {r,c}=coordinates(address);working.view={};page=Math.max(0,Math.floor((r-1)/PAGE_SIZE));renderGrid();chooseCell(r,c);const target=r===0?grid.querySelector(`[data-column-id="${CSS.escape(working.columns[c].id)}"]`):grid.querySelector(`[data-row="${r-1}"][data-col="${c}"]`);if(target){target.classList.add('cell-highlight');target.scrollIntoView?.({block:'center',inline:'center',behavior:'smooth'});target.focus({preventScroll:true});}message(notice,`${book.sheets.find(s=>s.id===id).name}!${address} · 원본 위치로 이동했습니다. 검색·정렬 보기를 초기화했습니다.`);}
  function applyExpression(){if(!selectedCell)throw Error('수정할 셀을 먼저 선택하세요.');if(!editable('cells.update'))throw Error('셀 편집 권한을 확인하세요.');const {r,c}=selectedCell,address=addressAt(r,c),value=cellExpression.value,meta=metadata();if(working.columns[c].formula)throw Error('전체 열 계산식은 열 속성에서 수정하세요.');if((value.startsWith('=')||meta.formulas?.[address]||meta.paused?.[address])&&!editable('formula.update'))throw Error('수식 편집 권한이 필요합니다.');meta.formulas??={};meta.paused??={};meta.issues??={};delete meta.issues[address];if(meta.blocked)delete meta.blocked[address];
   if(value.startsWith('=')){if(!value.slice(1).trim())throw Error('등호 뒤에 수식을 입력하세요.');meta.formulas[address]=value.slice(1);delete meta.paused[address];if(r)working.rows[r-1][c]='';else working.columns[c].sourceHeader='';}
   else{if(value.length>2000)throw Error('셀 값은 2,000자 이하입니다.');delete meta.formulas[address];if(meta.paused[address])meta.paused[address].mode='USER_INPUT';if(r)working.rows[r-1][c]=value;else{working.columns[c].sourceHeader=value;working.columns[c].label=value.trim().slice(0,100)||letter(c);}}
   mark();renderGrid();formulaPanel.render();scheduleCalculation();
  }
  function scheduleCalculation(){clearTimeout(calcTimer);if(!context.active()||!has('update')||!syncBook().sheets.some(s=>Object.keys(s.data.excel?.formulas||{}).length||s.data.columns.some(c=>c.formula)))return;calculationStatus.textContent='계산 대기';calcTimer=setTimeout(()=>recalculate('calculate',{background:true}).catch(fail),1000);}
  async function recalculate(action='calculate',{background=false}={}){
   clearTimeout(calcTimer);if(pending) return;if(calculationPromise){if(action!=='calculate'){await calculationPromise;return recalculate(action);}return calculationPromise;}
   const stamp=revision,current=book.activeSheetId;calculationStatus.textContent='수식 계산 중…';if(action!=='calculate'){pending=true;updateControls();}
   const run=(async()=>{let accepted=false;try{const next=await request('/api/sheets/workbook-calculation?businessId='+context.business()+'&action='+action,{method:'POST',body:syncBook(),loading:{label:'수식을 계산하고 있습니다…',blocking:!background}});if(stamp!==revision){calculationStatus.textContent='새 입력 계산 대기';return;}
     const changedWhileClean=!dirty&&JSON.stringify(next.sheets)!==JSON.stringify(syncBook().sheets);accepted=true;const active=document.activeElement,focusAddress=active?.dataset?.cell?addressAt(Number(active.dataset.row)+1,Number(active.dataset.col)):null;next.activeSheetId=book.sheets.some(s=>s.id===book.activeSheetId)?book.activeSheetId:current;restoreBook(next);formulaPanel.render();renderGrid();if(focusAddress){const at=coordinates(focusAddress),input=grid.querySelector(`[data-row="${at.r-1}"][data-col="${at.c}"]`);input?.focus({preventScroll:true});}if(changedWhileClean&&action==='calculate')mark(false);if(action!=='calculate'){mark(false);message(notice,'수식을 보관해 처리했습니다. 전체 시트 저장으로 확정하세요.');}calculationStatus.textContent='계산 완료';
    }catch(e){calculationStatus.textContent='계산 확인 필요';throw e;}finally{calculationPromise=null;if(action!=='calculate'){pending=false;updateControls();}if(stamp!==revision&&!pending&&!accepted)scheduleCalculation();}})();calculationPromise=run;return run;
  }
  async function changeStructure(operation){if(pending)return;if(calculationPromise)await calculationPromise;clearTimeout(calcTimer);pending=true;revision++;updateControls();try{const next=await request('/api/sheets/workbook-structure?businessId='+context.business(),{method:'POST',body:{workbook:syncBook(),sheetId:book.activeSheetId,...operation}});restoreBook(next);selectedCell=null;mark(false);render();context.onSheetChange?.();}finally{pending=false;updateControls();}}

  function has(action){
    return context.caps().has('workspace.sheet.'+action);
  }
  const rowCount=el('span','','muted');rowCount.setAttribute('role','status');
  const selectRows=button('현재 페이지 선택',()=>{if(!editable('rows.delete'))return;visible.forEach(i=>selectedRows.add(i));renderGrid();},'button small');
  const clearRows=button('행 선택 해제',()=>{if(pending)return;selectedRows.clear();renderGrid();},'button small quiet');
  toolbar.append(selectRows,clearRows,rowCount);
  function editable(action){
    return !pending&&context.active()&&has('update')&&has(action);
  }
  function updateControls(){
    addColumn.disabled=!editable('schema.update');
    addRow.disabled=!editable('rows.create')||!working.columns.length;
    deleteRows.disabled=!editable('rows.delete')||!selectedRows.size;
    selectRows.disabled=!editable('rows.delete')||!working.rows.length;clearRows.disabled=pending||!selectedRows.size;rowCount.textContent=`선택 ${selectedRows.size} / ${working.rows.length}행`;
    bookControls?.refresh();
    applyFormula.disabled=!editable('cells.update')||!selectedCell;cellExpression.disabled=pending;cellAddress.disabled=pending;goto.disabled=pending;
    grid.querySelectorAll('input[type=checkbox]').forEach(cb=>cb.disabled=!editable('rows.delete'));
    apply.disabled=pending||!context.active()||!has('update')||!dirty;
    undo.disabled=pending||!context.active()||!has('update')||!state.canUndo;
    discard.disabled=pending||!dirty;
    download.disabled=pending||!has('export')||state.version<0;
    template.disabled=pending||!has('export.template')||state.version<0;
    grid.querySelectorAll('input[data-cell]').forEach(n=>n.disabled=!editable('cells.update'));
    properties.querySelectorAll('input,select,button').forEach(n=>n.disabled=!editable('schema.update')||(n.dataset.formulaControl==='true'&&!has('formula.update')));
  }
  function mark(calculate=true){
    dirty=true;revision++;if(calculate)scheduleCalculation();
    clearTimeout(cacheTimer);cacheTimer=setTimeout(()=>{if(!dirty)return;const cells=book.sheets.reduce((n,s)=>n+s.data.rows.length*s.data.columns.length,0);if(cells<40000)cache?.write({version:state.version,workbook:syncBook()});else cache?.clear();},900);
    message(notice,`저장 전 변경 · ${working.rows.length.toLocaleString()}행${working.rows.length*working.columns.length>=40000?' · 큰 표는 창을 닫기 전에 서버에 저장하세요.':''}`);
    context.onLocalChange?.();
    updateControls();
  }
  function fail(error){
    message(notice,error.status===409?'서버 표나 업무 초안이 변경되었습니다. 입력은 보존했습니다. 최신 표를 불러오기 전에 현재 입력을 확인하세요.':error.message,true);
    if(error.status===409)context.onConflict?.();
  }
  function field(label,input,help='',full=false){
    const wrap=el('div','',full?'field field-span-full':'field'),l=el('label',label);
    input.id='sheet-'+label.replaceAll(/[^a-zA-Z가-힣]/g,'');
    l.htmlFor=input.id;
    wrap.append(l,input);
    if(help){const note=el('small',help,'field-help');note.id=input.id+'-help';input.setAttribute('aria-describedby',note.id);wrap.append(note);}
    return wrap;
  }
  function renderProperties(){
    properties.replaceChildren();
    const column=working.columns.find(c=>c.id===selectedColumn);
    if(!column){
      properties.append(el('p','표 머리글의 항목 이름을 선택하세요.','muted'));
      return;
    }
    const name=el('input');
    name.value=column.label;
    name.maxLength=100;
    name.addEventListener('input',()=>{
      const ci=working.columns.indexOf(column);if(working.excel?.formulas?.[addressAt(0,ci)])return message(notice,'수식이 있는 제목 셀은 fx 입력란에서 수정하세요.',true);column.label=name.value;delete column.sourceHeader;
      mark();
      const head=grid.querySelector(`[data-column-id="${CSS.escape(column.id)}"]`);
      if(head)head.textContent=column.label||'이름 없음';
    });
    const type=el('select');
    [['TEXT','문자 · 식별번호·전화번호'],['NUMBER','숫자 · 최대 15자리 정밀도'],['DATE','날짜 · YYYY-MM-DD']].forEach(([value,label])=>{
      const option=el('option',label);
      option.value=value;
      type.append(option);
    });
    type.value=column.type;
    type.addEventListener('change',()=>{
      column.type=type.value;
      mark();
      renderGrid();
    });
    const unit=el('input');
    unit.value=column.unit||'';
    unit.maxLength=30;
    unit.addEventListener('input',()=>{
      column.unit=unit.value;
      mark();
    });
    const width=el('input');
    width.type='number';
    width.min='80';
    width.max='480';
    width.step='1';
    width.value=String(column.width);
    width.addEventListener('input',()=>{
      column.width=Number(width.value);
      mark();
      renderGrid();
    });
    const formula=el('input');formula.value=column.formula||'';formula.maxLength=500;formula.placeholder='예: [수량열ID] * [단가열ID]';formula.dataset.formulaControl='true';
    formula.addEventListener('change',()=>{const ci=working.columns.indexOf(column);if(Object.keys(working.excel?.formulas||{}).some(ref=>coordinates(ref).c===ci)){formula.value=column.formula||'';return message(notice,'이 열에는 개별 Excel 수식이 있습니다. 셀을 선택해 fx에서 수정하세요.',true);}if(formula.value.trim()){column.formula=formula.value.trim();column.type='NUMBER';type.value='NUMBER';}else delete column.formula;mark();renderGrid();});
    const references=el('div','','actions');working.columns.filter(c=>c.id!==column.id&&c.type==='NUMBER').forEach(c=>{const b=button(c.label+' 넣기',()=>{formula.value+=(formula.value?' ':'')+'['+c.id+']';formula.dispatchEvent(new Event('change'));formula.focus();},'button small');b.dataset.formulaControl='true';references.append(b);});
    const format=el('select');for(const [v,t] of [['PLAIN','원래 값'],['NUMBER','천 단위 구분'],['CURRENCY','원화'],['PERCENT','백분율']]){const o=el('option',t);o.value=v;format.append(o);}format.value=column.format||'PLAIN';const decimals=el('input');decimals.type='number';decimals.min=0;decimals.max=8;decimals.value=column.decimals??0;format.addEventListener('change',()=>{column.format=format.value;mark(false);renderGrid();});decimals.addEventListener('change',()=>{column.decimals=Math.max(0,Math.min(8,Number(decimals.value)||0));mark(false);renderGrid();});
    const calculate=button('지금 다시 계산',()=>recalculate().catch(fail),'button small');calculate.dataset.formulaControl='true';
    const code=el('input');
    code.value=column.id;
    code.readOnly=true;
    const move=delta=>{
      const index=working.columns.findIndex(c=>c.id===selectedColumn),target=index+delta;
      if(target<0||target>=working.columns.length)return;
      const ids=working.columns.map(c=>c.id);
      [ids[index],ids[target]]=[ids[target],ids[index]];
      if(hasExcel()){changeStructure({columns:ids}).catch(fail);return;}working=reorderColumns(working,ids);
      mark();
      render();
    };
    const actions=el('div','','actions');
    actions.append(button('열 왼쪽으로',()=>move(-1)),button('열 오른쪽으로',()=>move(1)),button('이 열 삭제',()=>{
      if(working.columns.length===1&&working.rows.length&&!editable('rows.delete'))return message(notice,'마지막 열을 지우면 행도 사라지므로 행 삭제 권한이 필요합니다.',true);
      if(!confirm(`‘${column.label}’ 열과 그 열의 값을 표 초안에서 삭제할까요? 다른 열의 값은 유지합니다.`))return;
      if(hasExcel()){changeStructure({columns:working.columns.filter(c=>c.id!==column.id).map(c=>c.id)}).catch(fail);return;}working=removeColumn(working,column.id);
      selectedColumn=working.columns[0]?.id||null;
      mark();
      render();
    },'button danger'));
    const selected=el('div','','sheet-selection field-span-full');selected.append(el('span','선택 대상: '+column.label,'badge'));
    const formulaField=field('계산식 · 비워 두면 직접 입력',formula,'숫자 열만 참조합니다. 지원: + - * / ( ). 빈 값은 빈 결과, 0 나눗셈·순환 참조는 저장 차단. 열을 옮겨도 참조는 유지됩니다.',true);
    formulaField.append(calculate);if(references.childElementCount)formulaField.append(references);
    else formulaField.append(el('small','참조할 숫자 열이 생기면 여기에 열 넣기 버튼이 표시됩니다.','muted'));
    properties.append(selected,field('열 이름',name),field('값 형식',type),field('단위',unit),field('너비',width,'열 너비 80~480px. 화면과 XLSX에 같은 너비 설정을 사용합니다.'),field('표시 형식',format),field('소수 자릿수',decimals),formulaField,actions);
    updateControls();
  }
  const viewTools=el('div','','sheet-view-tools'),search=el('input'),filter=el('select'),sort=el('select'),direction=el('select'),pager=el('div','','sheet-pager'),pageInfo=el('span'),pageJump=el('input');
  search.type='search';search.maxLength=200;search.placeholder='값 검색';search.setAttribute('aria-label','표 값 검색');filter.setAttribute('aria-label','검색할 열');sort.setAttribute('aria-label','정렬할 열');direction.setAttribute('aria-label','정렬 방향');for(const [v,t] of [['asc','오름차순 ↑'],['desc','내림차순 ↓']]){const o=el('option',t);o.value=v;direction.append(o);}pageJump.type='number';pageJump.min=1;pageJump.setAttribute('aria-label','편집 페이지');
  const changeDisplay=(label,action)=>withLoading(label,action).catch(fail);const resetView=button('보기 초기화',()=>changeDisplay('원본 순서로 표시하고 있습니다…',()=>{working.view={};page=0;if(editable('schema.update'))mark(false);renderGrid();}),'button small quiet');
  const before=button('이전',()=>changeDisplay('이전 페이지를 표시하고 있습니다…',()=>{page--;renderGrid();}),'button small'),after=button('다음',()=>changeDisplay('다음 페이지를 표시하고 있습니다…',()=>{page++;renderGrid();}),'button small');
  viewTools.append(search,filter,sort,direction,resetView);pager.append(pageInfo,before,pageJump,after);grid.before(viewTools);grid.after(pager);
  let searchTimer;function changeView(){return changeDisplay('검색·정렬 결과를 준비하고 있습니다…',()=>{working.view={search:search.value,column:filter.value,sort:sort.value,direction:direction.value};page=0;if(editable('schema.update'))mark(false);renderGrid();});}search.addEventListener('input',()=>{clearTimeout(searchTimer);searchTimer=setTimeout(changeView,220);});[filter,sort,direction].forEach(n=>n.addEventListener('change',changeView));pageJump.addEventListener('change',()=>changeDisplay('페이지를 이동하고 있습니다…',()=>{page=Math.max(0,Number(pageJump.value)-1||0);renderGrid();}));
  function renderGrid(){
    grid.replaceChildren();const view=working.view||{},ids=orderedRows(working,view),pages=Math.max(1,Math.ceil(ids.length/PAGE_SIZE));page=Math.max(0,Math.min(page,pages-1));visible=ids.slice(page*PAGE_SIZE,(page+1)*PAGE_SIZE);
    for(const [select,title,key] of [[filter,'전체 열','column'],[sort,'원본 순서','sort']]){select.replaceChildren();const all=el('option',title);all.value='';select.append(all);working.columns.forEach(c=>{const o=el('option',c.label);o.value=c.id;select.append(o);});select.value=view[key]||'';}search.value=view.search||'';direction.value=view.direction||'asc';
    pageInfo.textContent=`전체 ${working.rows.length.toLocaleString()}행 · 검색 ${ids.length.toLocaleString()}행 · ${page+1} / ${pages}페이지`;pageJump.value=page+1;pageJump.max=pages;before.disabled=page===0;after.disabled=page===pages-1;
    if(!working.columns.length){grid.append(el('div','열을 추가해서 시작하세요.','empty-state'));updateControls();return;}
    const table=el('table','','editable-sheet'),head=el('thead'),line=el('tr');line.append(el('th','#'));
    working.columns.forEach((column,ci)=>{const th=el('th'),b=button(`${letter(ci)} · ${column.sourceHeader||column.label}`,()=>{selectedColumn=column.id;chooseCell(0,ci);renderProperties();details.open=true;},'button small quiet');b.dataset.columnId=column.id;b.title='이름 · 표시 형식 · 계산식';th.style.minWidth=column.width+'px';th.draggable=editable('schema.update');cellStyle(th,working,0,ci);th.append(b);line.append(th);
      th.addEventListener('dragstart',e=>{if(!editable('schema.update'))return e.preventDefault();e.dataTransfer.setData('application/x-ax-column',column.id);});th.addEventListener('dragover',e=>{if(editable('schema.update')&&e.dataTransfer.types.includes('application/x-ax-column'))e.preventDefault();});th.addEventListener('drop',e=>{const id=e.dataTransfer.getData('application/x-ax-column');if(!editable('schema.update')||!working.columns.some(c=>c.id===id))return;e.preventDefault();const order=working.columns.map(c=>c.id),from=order.indexOf(id),to=order.indexOf(column.id);order.splice(from,1);order.splice(to,0,id);if(hasExcel()){changeStructure({columns:order}).catch(fail);return;}working=reorderColumns(working,order);mark();render();});
    });head.append(line);const body=el('tbody');
    visible.forEach((ri,vi)=>{const row=working.rows[ri],tr=el('tr'),marker=el('th'),label=el('label','','check'),cb=el('input');cb.type='checkbox';cb.checked=selectedRows.has(ri);cb.setAttribute('aria-label',`${ri+2}행 선택`);cb.addEventListener('change',()=>{cb.checked?selectedRows.add(ri):selectedRows.delete(ri);updateControls();});label.append(cb,el('span',String(ri+2)));marker.append(label);tr.append(marker);
      working.columns.forEach((column,ci)=>{const td=el('td'),input=el('input');cellStyle(td,working,ri+1,ci);input.value=cellText(row[ci],column);input.dataset.cell='true';input.dataset.row=ri;input.dataset.col=ci;const ref=addressAt(ri+1,ci),excelFormula=working.excel?.formulas?.[ref];input.dataset.computed=String(!!column.formula||!!excelFormula);if(excelFormula){input.classList.add('formula-cell');input.title='='+excelFormula;}if(working.excel?.issues?.[ref])input.classList.add('formula-cell-error');input.maxLength=2000;input.setAttribute('aria-label',`${letter(ci)}${ri+2} · ${column.label}`);input.readOnly=!!column.formula||!!excelFormula;input.addEventListener('focus',()=>{chooseCell(ri+1,ci);input.value=row[ci]??'';});input.addEventListener('blur',()=>{input.value=cellText(row[ci],column);});input.addEventListener('input',()=>{working.rows[ri][ci]=input.value;const backup=working.excel?.paused?.[ref];if(backup)backup.mode='USER_INPUT';if(working.excel?.issues?.[ref]?.code==='INPUT_ERROR'){delete working.excel.issues[ref];formulaPanel.render();}cellExpression.value=input.value;mark(!input.value.startsWith('='));});input.addEventListener('change',()=>{if(input.value.startsWith('=')){chooseCell(ri+1,ci);cellExpression.value=input.value;try{applyExpression();}catch(e){fail(e);}}});
        input.addEventListener('keydown',e=>{if(e.isComposing)return;let r=vi,c=ci;if(e.key==='Enter')r+=e.shiftKey?-1:1;else if(e.key==='ArrowDown')r++;else if(e.key==='ArrowUp')r--;else if(e.key==='ArrowLeft'&&input.selectionStart===0&&input.selectionEnd===0)c--;else if(e.key==='ArrowRight'&&input.selectionStart===input.value.length)c++;else return;const n=grid.querySelector(`[data-row="${visible[r]}"][data-col="${c}"]`);if(n&&!n.disabled){e.preventDefault();n.focus();}});
        input.addEventListener('paste',e=>{const raw=e.clipboardData?.getData('text/plain')||'';if(!raw.includes('\t')&&!raw.includes('\n'))return;if(!editable('cells.update')||column.formula||excelFormula)return;e.preventDefault();if(view.sort||view.search)return message(notice,'여러 셀 붙여넣기는 보기 초기화 후 사용할 수 있습니다.',true);const matrix=raw.replaceAll('\r\n','\n').replaceAll('\r','\n').replace(/\n$/,'').split('\n').map(x=>x.split('\t'));if(ri+matrix.length>working.rows.length||matrix.some(cells=>ci+cells.length>working.columns.length))return message(notice,'행·열을 먼저 추가하세요. 붙여넣기 범위가 표보다 큽니다.',true);if(matrix.some((cells,r)=>cells.some((v,c)=>working.excel?.formulas?.[addressAt(ri+r+1,ci+c)]||v.length>2000||working.columns[ci+c].formula||v.startsWith('='))))return message(notice,'계산 열 또는 2,000자 초과 셀에는 붙여넣을 수 없습니다.',true);matrix.forEach((cells,r)=>cells.forEach((v,c)=>working.rows[ri+r][ci+c]=v));mark();renderGrid();});
        td.style.minWidth=column.width+'px';td.append(input);if(column.type==='DATE'&&!column.formula&&!excelFormula){input.dataset.dateKind='date';enhanceDateInput(input);}tr.append(td);
      });body.append(tr);
    });table.append(head,body);grid.append(table);if(!ids.length)grid.append(el('p',working.rows.length?'검색 결과가 없습니다.':'행 추가로 입력하세요.','empty-state'));updateControls();
  }
  function render(){
    syncBook();bookControls?.render();
    renderGrid();
    renderProperties();formulaPanel?.render();
    if(!dirty)message(notice,`${state.version<0?'표 미저장':'표 버전 '+state.version} · ${working.columns.length}열 / ${working.rows.length}행`);
  }
  async function load({
    force=false
  }
  ={
  }){
    const id=context.id();
    if(force){
      cacheRestored=true;
      cache?.clear();
    }
    if(!id){
      state={
        version:-1,draftVersion:context.draftVersion(),sheet:{
          columns:[],rows:[],rowIds:[]
        }
      };
      if(!dirty)restoreBook(state.workbook||state.sheet);
      if(!cacheRestored){
        cacheRestored=true;
        const stored=cache.read();
        if(stored&&stored.version===-1&&confirm('이 탭의 저장 전 새 표를 복구할까요?')){
          restoreBook(stored.workbook||stored.sheet);
          dirty=true;
          message(notice,'새 표의 저장 전 입력을 복구했습니다.');
        }
      }
      render();
      return;
    }
    if(!has('read')){
      message(notice,'표 조회 권한이 없습니다. 관리자가 엑셀 표 권한을 배정해야 합니다.',true);
      host.querySelectorAll('button,input,select').forEach(n=>n.disabled=true);
      return;
    }
    if(dirty&&!force){
      message(notice,'서버에 새 내용이 있을 수 있습니다. 현재 표 입력을 유지했습니다. 서버 재조회는 상단 ‘최신 내용 불러오기’를 사용하세요.');
      return;
    }
    state=await request(`/api/drafts/${encodeURIComponent(id)}/sheet`);
    restoreBook(state.workbook||state.sheet);
    selectedRows.clear();
    selectedColumn=working.columns[0]?.id||null;
    dirty=false;
    if(!cacheRestored||loadedId!==id){
      cacheRestored=true;
      loadedId=id;
      cache=editState(context.account(),context.business(),id+'-sheet');
      const stored=cache.read();
      if(stored&&stored.version===state.version&&confirm('이 탭의 저장 전 표 입력을 복구할까요?')){
        restoreBook(stored.workbook||stored.sheet);
        dirty=true;
        message(notice,'이 탭에 보관된 표 입력을 복구했습니다. 아직 서버에 적용되지 않았습니다.');
      }
      else if(stored&&stored.version!==state.version){
        context.onConflict?.();
        message(notice,'브라우저 표 입력과 서버 버전이 달라 자동 복구하지 않았습니다.',true);
      }
    }
    render();scheduleCalculation();
    context.onLocalChange?.();
  }
  function save(){return withLoading('전체 시트와 수식을 저장하고 있습니다…',saveWork);}
  async function saveWork(){
    if(!dirty)return state;
    clearTimeout(calcTimer);if(calculationPromise)await calculationPromise;revision++;
    if(pending)throw new Error('표 저장이 진행 중입니다.');
    const invalid=grid.querySelector('input[data-date-kind]:invalid');if(invalid){invalid.reportValidity();throw new Error('날짜 형식 또는 날짜 범위를 확인하세요. 입력 내용은 유지했습니다.');}
    if(!has('update'))throw new Error('표 저장 권한이 없습니다.');
    pending=true;
    context.setBusy?.(true);
    updateControls();
    try{
      await context.ensureDraft();
      state=await request(`/api/drafts/${encodeURIComponent(context.id())}/workbook`,{
        method:'PATCH',body:{
          workbook:syncBook(),version:state.version,draftVersion:context.draftVersion()
        }
      });
      restoreBook(state.workbook||state.sheet);
      dirty=false;
      cache?.clear();
      if(loadedId!==context.id()){
        loadedId=context.id();
        cache=editState(context.account(),context.business(),loadedId+'-sheet');
      }
      await context.onSaved?.(state);
      render();
      message(notice,`${book.sheets.length}개 시트를 서버에 저장했습니다. XLSX 다운로드도 이 버전을 사용합니다.`);
      return state;
    }
    finally{
      pending=false;
      context.setBusy?.(false);
      updateControls();
      context.onLocalChange?.();
    }
  }
  function restorePrevious(){return withLoading('이전 통합문서를 불러오고 있습니다…',restorePreviousWork);}
  async function restorePreviousWork(){
    if(dirty)throw new Error('현재 표의 미적용 입력을 먼저 저장하거나 취소하세요.');
    if(!confirm('모든 시트를 서버의 한 단계 이전 통합문서로 되돌릴까요? 업무 답변과 첨부는 바뀌지 않습니다.'))return;
    pending=true;
    context.setBusy?.(true);
    updateControls();
    try{
      await context.ensureDraft();
      state=await request(`/api/drafts/${encodeURIComponent(context.id())}/sheet/undo`,{
        method:'POST',body:{
          version:state.version,draftVersion:context.draftVersion()
        }
      });
      restoreBook(state.workbook||state.sheet);
      dirty=false;
      cache?.clear();
      await context.onSaved?.(state);
      render();
      message(notice,'한 단계 이전 통합문서를 새 버전으로 저장했습니다.');
    }
    finally{
      pending=false;
      context.setBusy?.(false);
      updateControls();
    }
  }
  function downloadFile(blank){return withLoading('XLSX 파일을 만들고 내려받고 있습니다…',()=>downloadFileWork(blank));}
  async function downloadFileWork(blank){
    if(dirty){
      if(confirm(blank?'표의 미적용 변경을 먼저 저장한 뒤 빈 양식을 내려받을까요? 취소하면 다운로드하지 않습니다.':'현재 표의 미적용 변경을 저장한 뒤 내려받을까요? 취소하면 다운로드하지 않습니다.'))await save();
      else return;
    }
    if(!context.id())throw new Error('표를 먼저 저장하세요.');
    const url=`/api/drafts/${encodeURIComponent(context.id())}/sheet.xlsx${blank?'?template=true':''}`;
    pending=true;updateControls();
    try{
      const response=await fetch(url,{credentials:'same-origin',redirect:'error',headers:{Accept:'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet'}});
      const type=response.headers.get('content-type')||'';
      if(!response.ok||!type.includes('application/vnd.openxmlformats-officedocument.spreadsheetml.sheet')){
        const error=type.includes('json')?await response.json():{};
        throw new Error(error.message||'다운로드 권한·로그인 상태를 확인한 뒤 다시 시도하세요.');
      }
      const blob=await response.blob(),objectUrl=URL.createObjectURL(blob),a=el('a');
      const disposition=response.headers.get('content-disposition')||'',encoded=disposition.match(/filename\*=UTF-8''([^;]+)/i);
      let filename=blank?'빈양식.xlsx':'데이터.xlsx';if(encoded){try{filename=decodeURIComponent(encoded[1]).replaceAll(/[\\/:*?"<>|\x00-\x1f]/g,'_');}catch{}}
      a.href=objectUrl;a.download=filename;document.body.append(a);a.click();a.remove();setTimeout(()=>URL.revokeObjectURL(objectUrl),30000);
      message(notice,blank?'빈 양식 XLSX를 내려받았습니다.':'서버에 저장된 데이터 XLSX를 내려받았습니다.');
    }finally{pending=false;updateControls();}

  }
  bookControls=workbookControls(host,{
    book:()=>syncBook(),current:()=>book.sheets.find(s=>s.id===book.activeSheetId),editable:()=>editable('schema.update'),canDelete:()=>editable('rows.delete'),busy:()=>pending,
    select:selectSheet,
    addMode(mode){if(mode==='direct'||mode==='guided'){addSheets([{name:'시트',data:emptyTable()}],{replaceEmpty:false});if(mode==='guided')context.onAddSheet?.('guided');}else context.onAddSheet?.(mode);},
    rename(value){const name=sheetName(value);if(hasExcel())return changeStructure({name});if(book.sheets.some(s=>s.id!==book.activeSheetId&&s.name.toLowerCase()===name.toLowerCase()))throw Error('같은 이름의 시트가 있습니다.');book.sheets.find(s=>s.id===book.activeSheetId).name=name;mark();render();context.onSheetChange?.();},
    duplicate(){if(!editable('rows.create')||!editable('cells.update'))throw Error('시트 복제에는 행 추가·셀 편집 권한이 필요합니다.');const current=syncBook().sheets.find(s=>s.id===book.activeSheetId);addSheets([{...copy(current),name:current.name+' 복사'}],{replaceEmpty:false});},
    move(delta){const index=book.sheets.findIndex(s=>s.id===book.activeSheetId),next=index+delta;if(next<0||next>=book.sheets.length)return;syncBook();[book.sheets[index],book.sheets[next]]=[book.sheets[next],book.sheets[index]];mark();render();},
    remove(){if(hasExcel())return changeStructure({remove:true});if(!editable('rows.delete')||book.sheets.length===1)throw Error('마지막 시트는 삭제할 수 없으며 시트 삭제 권한이 필요합니다.');const index=book.sheets.findIndex(s=>s.id===book.activeSheetId);book.sheets.splice(index,1);book.activeSheetId=book.sheets[Math.min(index,book.sheets.length-1)].id;working=copy(book.sheets.find(s=>s.id===book.activeSheetId).data);selectedRows.clear();selectedColumn=working.columns[0]?.id||null;mark();render();context.onSheetChange?.();}
  });
  bookControls.render();
  return {
    load,save,addSheets,selectSheet,goTo,recalculate,activeSheetId:()=>book.activeSheetId,workbook:()=>copy(syncBook()),hasColumns:()=>syncBook().sheets.some(s=>s.data.columns.length),download:downloadFile,replaceWorking(next,{sourceAssetId}={}){
      if(!editable('schema.update')||!editable('cells.update')||!editable('rows.create'))throw new Error('열·셀 수정과 행 추가 권한을 확인하세요.');
      const nextIds=new Set(next.rowIds);if(working.rowIds.some(id=>!nextIds.has(id))&&!editable('rows.delete'))throw new Error('기존 행을 바꾸려면 행 삭제 권한이 필요합니다.');
      const target=book.sheets.find(s=>s.id===book.activeSheetId);if(sourceAssetId&&!target.sourceAssetIds.includes(sourceAssetId)){if(target.sourceAssetIds.length>=20)throw Error('시트 원본 연결은 최대 20개입니다. 새 시트로 가져오세요.');target.sourceAssetIds.push(sourceAssetId);}
      working=copy(next);selectedRows.clear();selectedColumn=working.columns[0]?.id||null;mark();render();context.onSheetChange?.();
    },dirty:()=>dirty,version:()=>state.version,snapshot:()=>copy(working),refreshControls:updateControls,hasChanges:()=>dirty,isPending:()=>pending,clearDraft(){
      dirty=false;
      cache?.clear();
    },preview:sheet=>makePreviewTable(sheet)
  };
}

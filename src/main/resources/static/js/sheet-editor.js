import {asWorkbook,emptyTable,workbookControls,sheetName,uniqueSheetName} from './workbook-controls.js?v=0.6.0';
import {enhanceDateInput} from './date-picker.js?v=0.6.0';
import {
  request
} from './request.js?v=0.6.0';
import {
  el,button,message
} from './ui.js?v=0.6.0';
import {
  editState
} from './edit-state.js?v=0.6.0';
const copy=value=>JSON.parse(JSON.stringify(value));
export function reorderColumns(sheet,ids) {
  if(ids.length!==sheet.columns.length||new Set(ids).size!==ids.length||ids.some(id=>!sheet.columns.some(c=>c.id===id)))throw new Error('열 구성이 서로 다릅니다.');
  const positions=ids.map(id=>sheet.columns.findIndex(c=>c.id===id));
  return {
    ...sheet,columns:positions.map(i=>copy(sheet.columns[i])),rows:sheet.rows.map(row=>positions.map(i=>row[i]??''))
  };
}
export function removeColumn(sheet,id){
  const keep=sheet.columns.filter(c=>c.id!==id).map(c=>c.id);
  const positions=keep.map(value=>sheet.columns.findIndex(c=>c.id===value));
  return {
    ...sheet,columns:positions.map(i=>copy(sheet.columns[i])),rows:keep.length?sheet.rows.map(row=>positions.map(i=>row[i]??'')):[],rowIds:keep.length?[...(sheet.rowIds||[])]:[]
  };
}
export function makePreviewTable(sheet,{
  editable=false,onChange
}
={
}) {
  const wrap=el('div','','table-scroll sheet-preview'),table=el('table'),head=el('thead'),tr=el('tr'),body=el('tbody');
  sheet.columns.forEach(column=>tr.append(el('th',`${column.label}${column.unit?' ('+column.unit+')':''}`)));
  head.append(tr);
  sheet.rows.slice(0,200).forEach((row,ri)=>{
    const line=el('tr');
    sheet.columns.forEach((column,ci)=>{
      const td=el('td');
      if(editable){
        const input=el('input');
        input.value=row[ci]??'';
        input.maxLength=2000;
        if(column.formula){input.readOnly=true;input.title='계산 열 값은 저장 시 계산됩니다.';}
        input.setAttribute('aria-label',`${ri+1}행 ${column.label}`);
        input.addEventListener('input',()=>{
          sheet.rows[ri][ci]=input.value;
          onChange?.();
        });
        td.append(input);
        if(column.type==='DATE'&&!column.formula){input.dataset.dateKind='date';enhanceDateInput(input);}
      }
      else td.textContent=row[ci]??'';
      line.append(td);
    });
    body.append(line);
  });
  table.append(head,body);
  wrap.append(table);
  if(!sheet.rows.length)wrap.append(el('p','데이터가 없는 입력용 양식입니다.','muted'));
  return wrap;
}
export function createSheetEditor(host,context) {
  let state={
    version:-1,draftVersion:0,sheet:{
      columns:[],rows:[],rowIds:[]
    }
  },working=copy(state.sheet),dirty=false,selectedColumn=null,selectedRows=new Set(),pending=false,cache=editState(context.account(),context.business(),(context.id()||'new-EXCEL')+'-sheet'),loadedId=context.id()||'new-EXCEL',cacheRestored=false;
  let book=asWorkbook(state.sheet),bookControls;
  function syncBook(){book.sheets.find(s=>s.id===book.activeSheetId).data=copy(working);return book;}
  function restoreBook(value){book=asWorkbook(value);working=copy(book.sheets.find(s=>s.id===book.activeSheetId).data);selectedRows.clear();selectedColumn=working.columns[0]?.id||null;}
  function selectSheet(id){if(pending)return;syncBook();const target=book.sheets.find(s=>s.id===id);if(!target)throw Error('시트를 찾을 수 없습니다.');book.activeSheetId=id;working=copy(target.data);selectedRows.clear();selectedColumn=working.columns[0]?.id||null;render();context.onSheetChange?.();}
  function addSheets(entries,{replaceEmpty=true}={}){
    if(!editable('schema.update'))throw Error('시트 추가 권한 또는 저장 중 상태를 확인하세요.');
    if(entries.some(e=>e.data?.rows?.length)&&(!editable('rows.create')||!editable('cells.update')))throw Error('데이터가 있는 시트를 추가하려면 행 추가·셀 편집 권한이 필요합니다.');
    syncBook();const replace=replaceEmpty&&book.sheets.length===1&&!book.sheets[0].data.columns.length&&!book.sheets[0].sourceAssetIds.length;
    if(!entries.length||book.sheets.length+entries.length-(replace?1:0)>20)throw Error('프로젝트에는 최대 20개 시트를 담을 수 있습니다. 선택 수를 줄이거나 새 프로젝트로 나누세요.');
    const next=replace?[]:[...book.sheets],ids=[];
    for(const [i,entry] of entries.entries()){const id=replace&&i===0?book.sheets[0].id:'s_'+crypto.randomUUID().replaceAll('-','');next.push({id,name:uniqueSheetName(entry.name,next.map(s=>s.name)),data:copy(entry.data||emptyTable()),sourceAssetIds:[...(entry.sourceAssetIds||[])]});ids.push(id);}
    book.sheets=next;book.activeSheetId=ids[0];working=copy(next.find(s=>s.id===ids[0]).data);selectedRows.clear();selectedColumn=working.columns[0]?.id||null;mark();render();context.onSheetChange?.();return ids;
  }
  const notice=el('p','','help-line'),toolbar=el('div','','actions sheet-toolbar'),grid=el('div','','sheet-grid table-scroll'),details=el('details','','sheet-properties'),summary=el('summary','선택한 열 자세히 조정'),properties=el('div'),footer=el('div','','actions sheet-actions');
  notice.setAttribute('role','status');
  notice.setAttribute('aria-live','polite');
  details.append(summary,properties);
  const addColumn=button('열 추가',()=>{
    if(working.columns.length>=20)return message(notice,'열은 최대 20개입니다.',true);
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
    if(working.rows.length>=200)return message(notice,'현재 표는 최대 200행입니다.',true);
    working.rows.push(working.columns.map(()=>''));
    working.rowIds.push(crypto.randomUUID());
    mark();
    renderGrid();
  }),deleteRows=button('선택한 행 삭제',()=>{
    if(!selectedRows.size)return message(notice,'지울 행의 번호 옆 선택란을 선택하세요.',true);
    if(!confirm(`선택한 ${selectedRows.size}행을 표 초안에서 삭제할까요? 서버 적용 전까지 확정되지 않습니다.`))return;
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
  host.append(el('p','열을 선택하면 이름·형식·단위·너비·순서를 정밀 조정할 수 있습니다. 셀 입력은 AI 없이 가능합니다. 숫자는 최대 15자리 정밀도이며 00123 같은 식별번호는 문자형으로 지정하세요.','muted'),toolbar,notice,grid,details,footer,el('p','계산 열은 숫자 열의 [열ID] 참조와 사칙연산을 지원합니다. 저장 시 재계산되며 빈 참조는 빈 값으로 남습니다. 일반 문자 셀의 = 기호는 실행되지 않습니다.','muted'));
  function has(action){
    return context.caps().has('workspace.sheet.'+action);
  }
  const rowCount=el('span','','muted');rowCount.setAttribute('role','status');
  const selectRows=button('전체 행 선택',()=>{if(!editable('rows.delete'))return;working.rows.forEach((_,i)=>selectedRows.add(i));renderGrid();},'button small');
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
    grid.querySelectorAll('input[type=checkbox]').forEach(cb=>cb.disabled=!editable('rows.delete'));
    apply.disabled=pending||!context.active()||!has('update')||!dirty;
    undo.disabled=pending||!context.active()||!has('update')||!state.canUndo;
    discard.disabled=pending||!dirty;
    download.disabled=pending||!has('export')||state.version<0;
    template.disabled=pending||!has('export.template')||state.version<0;
    grid.querySelectorAll('input[data-cell]').forEach(n=>n.disabled=!editable('cells.update')||n.dataset.computed==='true');
    properties.querySelectorAll('input,select,button').forEach(n=>n.disabled=!editable('schema.update')||(n.dataset.formulaControl==='true'&&!has('formula.update')));
  }
  function mark(){
    dirty=true;
    cache?.write({
      version:state.version,workbook:syncBook()
    });
    message(notice,`전체 ${book.sheets.length}시트 · ${book.sheets.find(s=>s.id===book.activeSheetId).name} 수정 중 · ${working.columns.length}열 / ${working.rows.length}행 · 아직 서버에 적용되지 않았습니다.`);
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
      column.label=name.value;
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
    formula.addEventListener('change',()=>{if(formula.value.trim()){column.formula=formula.value.trim();column.type='NUMBER';type.value='NUMBER';}else delete column.formula;mark();renderGrid();});
    const references=el('div','','actions');working.columns.filter(c=>c.id!==column.id&&c.type==='NUMBER').forEach(c=>{const b=button(c.label+' 넣기',()=>{formula.value+=(formula.value?' ':'')+'['+c.id+']';formula.dispatchEvent(new Event('change'));formula.focus();},'button small');b.dataset.formulaControl='true';references.append(b);});
    const code=el('input');
    code.value=column.id;
    code.readOnly=true;
    const move=delta=>{
      const index=working.columns.findIndex(c=>c.id===selectedColumn),target=index+delta;
      if(target<0||target>=working.columns.length)return;
      const ids=working.columns.map(c=>c.id);
      [ids[index],ids[target]]=[ids[target],ids[index]];
      working=reorderColumns(working,ids);
      mark();
      render();
    };
    const actions=el('div','','actions');
    actions.append(button('열 왼쪽으로',()=>move(-1)),button('열 오른쪽으로',()=>move(1)),button('이 열 삭제',()=>{
      if(working.columns.length===1&&working.rows.length&&!editable('rows.delete'))return message(notice,'마지막 열을 지우면 행도 사라지므로 행 삭제 권한이 필요합니다.',true);
      if(!confirm(`‘${column.label}’ 열과 그 열의 값을 표 초안에서 삭제할까요? 다른 열의 값은 유지합니다.`))return;
      working=removeColumn(working,column.id);
      selectedColumn=working.columns[0]?.id||null;
      mark();
      render();
    },'button danger'));
    const selected=el('div','','sheet-selection field-span-full');selected.append(el('span','선택 대상: '+column.label,'badge'));
    const formulaField=field('계산식 · 비워 두면 직접 입력',formula,'숫자 열만 참조합니다. 지원: + - * / ( ). 빈 값은 빈 결과, 0 나눗셈·순환 참조는 저장 차단. 열을 옮겨도 참조는 유지됩니다.',true);
    if(references.childElementCount)formulaField.append(references);
    else formulaField.append(el('small','참조할 숫자 열이 생기면 여기에 열 넣기 버튼이 표시됩니다.','muted'));
    properties.append(selected,field('열 이름',name),field('값 형식',type),field('단위',unit),field('너비',width,'열 너비 80~480px. 화면과 XLSX에 같은 너비 설정을 사용합니다.'),formulaField,field('열 ID',code,'이 열을 구분하는 고정 식별값입니다. 열 순서를 바꾸거나 다른 열을 지워도 이 열의 값은 유지됩니다.',true),actions);
    updateControls();
  }
  function renderGrid(){
    grid.replaceChildren();
    if(!working.columns.length){
      grid.append(el('div','아직 열이 없습니다. ‘열 추가’로 빈 양식부터 만들거나 AI에게 필요한 항목을 제안받으세요.','empty-state'));
      updateControls();
      return;
    }
    const table=el('table','','editable-sheet'),head=el('thead'),line=el('tr');
    line.append(el('th','행 선택'));
    working.columns.forEach((column,ci)=>{
      const th=el('th'),b=button(`${String.fromCharCode(65+ci)} · ${column.label||'이름 없음'}`,()=>{
        selectedColumn=column.id;
        renderProperties();
        details.open=true;
        properties.querySelector('input')?.focus();
      },'button small quiet');
      b.dataset.columnId=column.id;
      b.setAttribute('aria-label',`${column.label} 열 선택·자세히 조정`);
      th.style.width=column.width+'px';th.style.minWidth=column.width+'px';th.draggable=editable('schema.update');
      th.addEventListener('dragstart',e=>{if(!editable('schema.update'))return e.preventDefault();e.dataTransfer.setData('application/x-ax-column',column.id);e.dataTransfer.effectAllowed='move';});
      th.addEventListener('dragover',e=>{if(editable('schema.update')&&e.dataTransfer.types.includes('application/x-ax-column')){e.preventDefault();th.classList.add('drag-over');}});
      th.addEventListener('dragleave',()=>th.classList.remove('drag-over'));
      th.addEventListener('drop',e=>{th.classList.remove('drag-over');const id=e.dataTransfer.getData('application/x-ax-column');if(!editable('schema.update')||!working.columns.some(c=>c.id===id))return;e.preventDefault();const ids=working.columns.map(c=>c.id),from=ids.indexOf(id),to=ids.indexOf(column.id);ids.splice(from,1);ids.splice(to,0,id);working=reorderColumns(working,ids);mark();render();});
      th.append(b);
      line.append(th);
    });
    head.append(line);
    const body=el('tbody');
    working.rows.forEach((row,ri)=>{
      const tr=el('tr'),marker=el('td'),label=el('label','','check'),cb=el('input');
      cb.type='checkbox';
      cb.checked=selectedRows.has(ri);
      cb.setAttribute('aria-label',`${ri+1}행 선택`);
      cb.addEventListener('change',()=>{cb.checked?selectedRows.add(ri):selectedRows.delete(ri);updateControls();});
      label.append(cb,el('span',String(ri+1)));
      marker.append(label);
      tr.append(marker);
      working.columns.forEach((column,ci)=>{
        const td=el('td'),input=el('input');
        input.value=row[ci]??'';
        input.dataset.cell='true';
        input.maxLength=2000;
        input.setAttribute('aria-label',`${ri+1}행 ${column.label}`);
        input.addEventListener('input',()=>{
          working.rows[ri][ci]=input.value;
          mark();
        });
        input.addEventListener('keydown',event=>{
          if(event.isComposing)return;
          let r=ri,c=ci;
          if(event.key==='ArrowDown'||event.key==='Enter')r++;
          else if(event.key==='ArrowUp')r--;
          else return;
          const next=body.children[r]?.querySelectorAll('input[data-cell]')[c];
          if(next){
            event.preventDefault();
            next.focus();
          }
        });
        input.addEventListener('paste',event=>{
          const raw=event.clipboardData?.getData('text/plain')||'';
          if(!raw.includes('\t')&&!raw.includes('\n'))return;
          if(!editable('cells.update')||column.formula)return;
          event.preventDefault();
          const matrix=raw.replaceAll('\r\n','\n').replaceAll('\r','\n').replace(/\n$/,'').split('\n').map(x=>x.split('\t'));
          if(ri+matrix.length>working.rows.length||matrix.some(cells=>ci+cells.length>working.columns.length))return message(notice,'붙여넣을 범위가 현재 표를 넘습니다. 필요한 행·열을 먼저 추가하세요. 원래 값은 유지했습니다.',true);
          if(matrix.some(cells=>cells.some(v=>v.length>2000)))return message(notice,'한 셀은 최대 2,000자입니다.',true);
          if(matrix.some(cells=>cells.some((_,c)=>working.columns[ci+c].formula)))return message(notice,'계산 열에는 붙여넣을 수 없습니다. 입력 열만 선택하세요.',true);
          matrix.forEach((cells,r)=>cells.forEach((value,c)=>working.rows[ri+r][ci+c]=value));
          mark();
          renderGrid();
        });
        td.style.width=column.width+'px';td.style.minWidth=column.width+'px';
        input.dataset.computed=String(!!column.formula);input.dataset.row=ri;input.dataset.col=ci;
        if(column.formula){input.title='계산 열 · 저장 시 재계산';input.readOnly=true;}
        input.addEventListener('keydown',e=>{if(e.isComposing||e.keyCode===229)return;let nr=ri,nc=ci;if(e.key==='Enter')nr+=e.shiftKey?-1:1;else if(e.key==='ArrowDown')nr++;else if(e.key==='ArrowUp')nr--;else if(e.key==='ArrowLeft'&&input.selectionStart===0&&input.selectionEnd===0)nc--;else if(e.key==='ArrowRight'&&input.selectionStart===input.value.length)nc++;else return;const next=grid.querySelector(`[data-row="${nr}"][data-col="${nc}"]`);if(next&&!next.disabled){e.preventDefault();next.focus();}});
        td.append(input);
        if(column.type==='DATE'&&!column.formula){input.dataset.dateKind='date';enhanceDateInput(input);}
        tr.append(td);
      });
      body.append(tr);
    });
    table.append(head,body);
    grid.append(table);
    if(!working.rows.length)grid.append(el('p','열만 있는 빈 양식입니다. 데이터를 넣으려면 행을 추가하세요.','muted'));
    updateControls();
  }
  function render(){
    syncBook();bookControls?.render();
    renderGrid();
    renderProperties();
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
    render();
    context.onLocalChange?.();
  }
  async function save(){
    if(!dirty)return state;
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
  async function restorePrevious(){
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
  async function downloadFile(blank){
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
    rename(value){const name=sheetName(value);if(book.sheets.some(s=>s.id!==book.activeSheetId&&s.name.toLowerCase()===name.toLowerCase()))throw Error('같은 이름의 시트가 있습니다.');book.sheets.find(s=>s.id===book.activeSheetId).name=name;mark();render();context.onSheetChange?.();},
    duplicate(){if(!editable('rows.create')||!editable('cells.update'))throw Error('시트 복제에는 행 추가·셀 편집 권한이 필요합니다.');const current=syncBook().sheets.find(s=>s.id===book.activeSheetId);addSheets([{...copy(current),name:current.name+' 복사'}],{replaceEmpty:false});},
    move(delta){const index=book.sheets.findIndex(s=>s.id===book.activeSheetId),next=index+delta;if(next<0||next>=book.sheets.length)return;syncBook();[book.sheets[index],book.sheets[next]]=[book.sheets[next],book.sheets[index]];mark();render();},
    remove(){if(!editable('rows.delete')||book.sheets.length===1)throw Error('마지막 시트는 삭제할 수 없으며 시트 삭제 권한이 필요합니다.');const index=book.sheets.findIndex(s=>s.id===book.activeSheetId);book.sheets.splice(index,1);book.activeSheetId=book.sheets[Math.min(index,book.sheets.length-1)].id;working=copy(book.sheets.find(s=>s.id===book.activeSheetId).data);selectedRows.clear();selectedColumn=working.columns[0]?.id||null;mark();render();context.onSheetChange?.();}
  });
  bookControls.render();
  return {
    load,save,addSheets,selectSheet,activeSheetId:()=>book.activeSheetId,workbook:()=>copy(syncBook()),hasColumns:()=>syncBook().sheets.some(s=>s.data.columns.length),download:downloadFile,replaceWorking(next,{sourceAssetId}={}){
      if(!editable('schema.update')||!editable('cells.update')||!editable('rows.create'))throw new Error('열·셀 수정과 행 추가 권한을 확인하세요.');
      if(working.rowIds.some(id=>!next.rowIds.includes(id))&&!editable('rows.delete'))throw new Error('기존 행을 바꾸려면 행 삭제 권한이 필요합니다.');
      const target=book.sheets.find(s=>s.id===book.activeSheetId);if(sourceAssetId&&!target.sourceAssetIds.includes(sourceAssetId)){if(target.sourceAssetIds.length>=20)throw Error('시트 원본 연결은 최대 20개입니다. 새 시트로 가져오세요.');target.sourceAssetIds.push(sourceAssetId);}
      working=copy(next);selectedRows.clear();selectedColumn=working.columns[0]?.id||null;mark();render();context.onSheetChange?.();
    },dirty:()=>dirty,version:()=>state.version,snapshot:()=>copy(working),refreshControls:updateControls,hasChanges:()=>dirty,isPending:()=>pending,clearDraft(){
      dirty=false;
      cache?.clear();
    },preview:sheet=>makePreviewTable(sheet)
  };
}

// Client guidance mirrors storage rules; the server validates every actual batch.
// This module never moves or removes source data and never rebases A1 formulas.
export const CONTINUATION_CAPACITY_LIMITS=Object.freeze({
 rows:50000,columns:100,sheetCells:1000000,workbookCells:2000000,sheets:20,
 sheetTextBytes:10000000,workbookJsonBytes:32000000,formulaCells:200000,formulaChars:8000000
});
const encoder=new TextEncoder();
const utf8=value=>encoder.encode(String(value??'')).length;
const ownEntries=value=>value&&typeof value==='object'&&!Array.isArray(value)?Object.entries(value):[];
function target(book,sheetId){
 if(!book||!Array.isArray(book.sheets))throw Error('통합문서를 먼저 불러오세요.');
 const sheet=book.sheets.find(s=>s.id===(sheetId||book.activeSheetId));
 if(!sheet||!Array.isArray(sheet.data?.columns)||!Array.isArray(sheet.data?.rows))throw Error('이어갈 시트를 찾을 수 없습니다.');
 return sheet;
}
function formulaCount(sheet){return ownEntries(sheet.data?.excel?.formulas).length+(sheet.data?.columns||[]).filter(c=>!!c.formula).length*(sheet.data?.rows||[]).length;}
function lastRowFormulaCount(sheet){
 const row=(sheet.data.rows.length+1).toString();
 return sheet.data.columns.filter(c=>!!c.formula).length+ownEntries(sheet.data.excel?.formulas).filter(([address])=>address.match(/[0-9]+$/)?.[0]===row).length;
}
function uniqueName(value,names){
 const base=(String(value||'이어지는 시트').replace(/[\\/?*:\[\]\x00-\x1f]/g,' ').replace(/^'+|'+$/g,'').trim()||'이어지는 시트').slice(0,31),used=new Set(names.map(n=>String(n).toLowerCase()));
 let name=base,n=2;while(used.has(name.toLowerCase())){const suffix=' ('+(n++)+')';name=base.slice(0,31-suffix.length)+suffix;}return name;
}

/** Count-only is cheap enough for UI refreshes. Byte scanning is opt-in at a boundary. */
export function workbookCapacity(book,sheetId,{includeBytes=false}={}){
 const sheet=target(book,sheetId),limits=CONTINUATION_CAPACITY_LIMITS,columns=sheet.data.columns.length,rows=sheet.data.rows.length;
 const cells=rows*columns,totalCells=book.sheets.reduce((sum,s)=>sum+(s.data?.rows?.length||0)*(s.data?.columns?.length||0),0),totalFormulas=book.sheets.reduce((sum,s)=>sum+formulaCount(s),0),formulasPerNewRow=lastRowFormulaCount(sheet);
 const remainingProjectCells=Math.max(0,limits.workbookCells-totalCells),remainingSheetCells=Math.max(0,limits.sheetCells-cells),remainingFormulaCells=Math.max(0,limits.formulaCells-totalFormulas);
 const remainingRows=columns?Math.max(0,Math.min(limits.rows-rows,Math.floor(remainingSheetCells/columns),Math.floor(remainingProjectCells/columns))):0;
 const formulaRowHeadroom=formulasPerNewRow?Math.floor(remainingFormulaCells/formulasPerNewRow):Infinity;
 let sheetTextBytes=null,workbookJsonBytes=null;
 if(includeBytes){sheetTextBytes=sheet.data.columns.reduce((sum,c)=>sum+utf8(c.label)+utf8(c.unit),0);for(const row of sheet.data.rows)for(const value of row)sheetTextBytes+=utf8(value);workbookJsonBytes=utf8(JSON.stringify(book));}
 const formulaChars=ownEntries(sheet.data.excel?.formulas).reduce((sum,[,value])=>sum+String(value??'').trim().length,0),hasCellFormulas=ownEntries(sheet.data.excel?.formulas).length>0||ownEntries(sheet.data.excel?.paused).length>0;
 const reasons=[],newSheetReasons=[];
 if(!columns)reasons.push('먼저 열 구성을 만들면 다음 처리량을 계산할 수 있습니다.');
 if(columns>limits.columns)reasons.push('열 수가 지원 범위를 넘습니다. 원본을 보존하고 열 구성을 확인하세요.');
 if(rows>=limits.rows)reasons.push('현재 시트의 50,000행을 채웠습니다. 새 시트로 이어갈 수 있습니다.');
 if(remainingSheetCells<columns&&columns)reasons.push('현재 시트의 100만 셀을 채웠습니다. 새 시트로 이어갈 수 있습니다.');
 if(remainingProjectCells<Math.max(1,columns)){const text='프로젝트의 200만 셀을 채웠습니다. 완료한 데이터와 진행 위치는 유지됩니다. 프로젝트 데이터를 정리하거나 별도 프로젝트로 나누어 사용하세요.';reasons.push(text);newSheetReasons.push(text);}
 if(formulasPerNewRow&&formulaRowHeadroom<1){const text='프로젝트의 수식 계산량을 채웠습니다. 진행 위치를 유지하고 계산 범위를 줄이거나 별도 프로젝트에서 계속하세요.';reasons.push(text);newSheetReasons.push(text);}
 if(sheetTextBytes!==null&&sheetTextBytes>=limits.sheetTextBytes)reasons.push('현재 시트의 본문 용량을 채웠습니다. 새 시트로 이어갈 수 있습니다.');
 if(workbookJsonBytes!==null&&workbookJsonBytes>=limits.workbookJsonBytes){const text='프로젝트 저장 용량을 채웠습니다. 완료한 데이터와 진행 위치를 유지하고 데이터를 나누어 사용하세요.';reasons.push(text);newSheetReasons.push(text);}
 if(formulaChars>=limits.formulaChars)reasons.push('현재 시트의 수식 문자열 용량을 채웠습니다. 수식을 확인한 뒤 이어가세요.');
 if(book.sheets.length>=limits.sheets)newSheetReasons.push('프로젝트의 시트 20개를 모두 사용하고 있습니다. 완료한 데이터를 보존하고 별도 프로젝트로 나누세요.');
 if(!columns)newSheetReasons.push('먼저 현재 시트의 열 구성을 만들어 주세요.');
 if(columns>limits.columns)newSheetReasons.push('열 구성이 지원 범위를 넘으므로 그대로 복제할 수 없습니다.');
 if(hasCellFormulas)newSheetReasons.push('현재 시트에는 개별 셀 수식 또는 보관 수식이 있습니다. 참조 위치를 임의로 바꾸거나 수식을 누락하지 않도록 자동 시트 연결을 멈췄습니다. 수식을 확인해 같은 시트에서 계속하거나, 새 작업에 맞는 계산 규칙을 먼저 구성하세요.');
 const maxNextRows=Math.max(0,Math.min(remainingRows,formulaRowHeadroom)),bytesBlocked=sheetTextBytes!==null&&sheetTextBytes>=limits.sheetTextBytes||workbookJsonBytes!==null&&workbookJsonBytes>=limits.workbookJsonBytes;
 return {sheetId:sheet.id,sheetName:sheet.name,rows,columns,cells,totalCells,totalFormulas,formulasPerNewRow,remainingRows,remainingSheetCells,remainingProjectCells,remainingFormulaCells,maxNextRows,sheetTextBytes,workbookJsonBytes,formulaChars,hasCellFormulas,
  canContinueHere:columns>0&&columns<=limits.columns&&maxNextRows>0&&!bytesBlocked&&formulaChars<limits.formulaChars,
  canCreateSheet:newSheetReasons.length===0,reasons,newSheetReasons,limits,serverValidationRequired:true};
}

/** Explicit, reversible empty-destination proposal, for editor.addSheets(...). */
export function continuationSheet(book,sheetId,{name}={}){
 const source=target(book,sheetId),capacity=workbookCapacity(book,source.id,{includeBytes:true});
 if(!capacity.canCreateSheet)throw Error(capacity.newSheetReasons.join(' '));
 const sources=[...(source.sourceAssetIds||[])];
 if(sources.length>20||new Set(sources).size!==sources.length)throw Error('원본 자료 연결을 확인한 뒤 새 시트로 이어가세요.');
 // Keep stable column IDs and whole-column arithmetic formulas; their references
 // are to those IDs, so no positional guessing or lost formula is involved.
 const entry={name:uniqueName(name||source.name+' 이어서',book.sheets.map(s=>s.name)),data:{columns:structuredClone(source.data.columns),rows:[],rowIds:[]},sourceAssetIds:sources};
 const projected={...book,sheets:[...book.sheets,{id:'s_'+('0'.repeat(32)),...entry}]};
 if(utf8(JSON.stringify(projected))>CONTINUATION_CAPACITY_LIMITS.workbookJsonBytes)throw Error('새 시트를 저장할 프로젝트 용량이 부족합니다. 완료 데이터와 진행 위치를 유지하고 프로젝트 데이터를 나누세요.');
 return entry;
}

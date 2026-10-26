package com.axplatform.ai;

import com.axplatform.common.Problem;
import com.axplatform.sheet.*;
import java.util.*;

/** Persisted per-run checkpoints. Only applied, verified chunks advance the source cursor. */
public final class AiContinuation {
 private AiContinuation(){}
 private static final Set<String> MODES=Set.of("GENERATE","PHOTO","RANGE");
 public static final Set<String> REDUCE=Set.of("AI_OUTPUT_LIMIT","AI_CONTEXT_LIMIT","AI_ROUND_LIMIT","AI_RESPONSE_LIMIT","AI_PROPOSAL_INVALID");
 public static final Set<String> REPLACE_SOURCE=Set.of("AI_IMAGE_TOO_LARGE","AI_IMAGE_INVALID","AI_IMAGE_DIMENSIONS","AI_IMAGE_REJECTED","AI_ASSET_CHANGED","AI_ASSET_LIMIT");
 public static String instructions(Map<String,Object> plan,String answer,boolean replace){
  String checked=com.axplatform.common.Inputs.text(answer,12000,"추가 답변");
  String combined=replace?checked:str(plan,"originalPrompt")+"\n\n추가 확인 답변: "+checked;
  if(combined.length()>12000)throw new Problem(422,"AI_INSTRUCTION_LIMIT","기존 지침과 답변이 합계 12,000자를 넘었습니다. ‘AI 지침 전체 교체’를 선택하고 필요한 전체 지침을 요약해 입력하세요. 완료한 데이터와 처리 위치는 유지됩니다.");
  return combined;
 }
 public static Map<String,Object> initial(Map<String,Object> input,Map<String,Object> book,String target,String rootId){
  if(input==null||input.isEmpty())return Map.of();
  if(!Set.of("mode","targetRows","chunkRows","sourceAssetIds","sourceSheetIds","rowStart","rowCount").containsAll(input.keySet()))throw Problem.invalid("이어 처리 설정 항목을 확인하세요.");
  String mode=Objects.toString(input.get("mode"),"");if(!MODES.contains(mode))throw Problem.invalid("이어 처리 방식을 확인하세요.");
  var sheet=WorkbookRules.sheet(book,target);var data=WorkbookRules.data(book,(String)sheet.get("id"));int total=rows(data);
  var plan=new LinkedHashMap<String,Object>();plan.put("mode",mode);plan.put("rootId",rootId);plan.put("parentRunId","");plan.put("processedRows",0);plan.put("targetRows",integer(input,"targetRows",0,0,1000000));plan.put("chunkRows",integer(input,"chunkRows",100,1,200));plan.put("sourceIndex",0);plan.put("sourceRowStart",1);plan.put("targetSheetId",sheet.get("id"));plan.put("done",false);
  var sources=strings(input.get("sourceAssetIds"),20);var targets=strings(input.get("sourceSheetIds"),20);
  if(mode.equals("PHOTO")){if(sources.isEmpty()||new HashSet<>(sources).size()!=sources.size()||sources.stream().anyMatch(v->!v.matches("[a-f0-9-]{36}")))throw Problem.invalid("사진 이어 처리 원본을 순서대로 선택하세요.");if(targets.isEmpty())targets=new ArrayList<>(Collections.nCopies(sources.size(),(String)sheet.get("id")));if(targets.size()!=sources.size())throw Problem.invalid("사진 원본과 대상 시트 수가 다릅니다.");for(String id:targets)WorkbookRules.sheet(book,id);plan.put("targetSheetId",targets.get(0));}
  else if(!sources.isEmpty()||!targets.isEmpty())throw Problem.invalid("사진 원본 대기열은 사진 이어 처리에서만 지정하세요.");
  plan.put("sourceAssetIds",sources);plan.put("sourceSheetIds",targets);plan.put("sourceCount",sources.size());
  int start=integer(input,"rowStart",1,1,50000),count=integer(input,"rowCount",Math.max(0,total-start+1),0,50000);
  if(mode.equals("RANGE")&&(count<1||(long)start+count-1>total))throw Problem.invalid("이어 검토할 시작 행과 전체 행 수를 실제 데이터 안에서 선택하세요.");
  plan.put("rangeStart",start);plan.put("rangeCount",count);plan.put("rangeOrigin",start);if(mode.equals("RANGE"))plan.put("rangeIdentity",identity(data));return prepare(plan,book);
 }
 @SuppressWarnings("unchecked") public static Map<String,Object> prepare(Map<String,Object> source,Map<String,Object> book){
  var p=new LinkedHashMap<>(source);if(Boolean.TRUE.equals(p.get("done")))return p;var data=WorkbookRules.data(book,str(p,"targetSheetId"));int width=((List<?>)data.get("columns")).size(),limit=Math.min(number(p,"chunkRows"),Math.min(200,4000/Math.max(1,width)));
  String mode=str(p,"mode");if(mode.equals("GENERATE")&&number(p,"targetRows")>0)limit=Math.min(limit,number(p,"targetRows")-number(p,"processedRows"));
  if(mode.equals("RANGE")){if(!Objects.equals(p.get("rangeIdentity"),identity(data)))throw new Problem(409,"AI_SOURCE_CHANGED","이어 검토 중 원본 행의 순서나 열 구성이 바뀌었습니다. 완료한 부분은 보존했습니다. 새 범위로 작업을 시작하세요.");limit=Math.min(limit,number(p,"rangeCount")-number(p,"processedRows"));while(limit>0){try{AiSheetScope.project(data,AiSheetScope.range(data,number(p,"rangeStart"),limit));break;}catch(Problem tooLarge){if(limit==1)throw new Problem(422,"AI_SINGLE_ROW_LIMIT","한 행이 AI 입력 크기(80,000자)를 넘습니다. 완료한 부분은 보존했습니다. 긴 셀 내용을 줄이거나 원본 자료를 열 단위로 나눈 후 현재 위치에서 이어 주세요.");limit=Math.max(1,limit/2);}}}
  else if(width>0){
   int room=Math.min(SheetRules.MAX_ROWS-rows(data),(SheetRules.MAX_CELLS/width)-rows(data));
   if(room<1)throw new Problem(422,"AI_DESTINATION_FULL","현재 시트의 저장 공간이 찼습니다. 완료한 부분은 보존했습니다. 같은 열 구성의 빈 시트를 준비하고 이어 처리 대상을 바꿔 주세요.");
   long totalCells=0,totalFormulas=0;for(var sheet:WorkbookRules.sheets(book)){var table=WorkbookRules.data(book,str(sheet,"id"));totalCells+=(long)rows(table)*((List<?>)table.get("columns")).size();totalFormulas+=ExcelCells.map(ExcelCells.meta(table),"formulas").size();for(Object column:(List<?>)table.get("columns"))if(((Map<?,?>)column).containsKey("formula"))totalFormulas+=rows(table);}
   long projectRoom=(WorkbookRules.MAX_WORKBOOK_CELLS-totalCells)/width;if(projectRoom<1)throw new Problem(422,"AI_PROJECT_FULL","프로젝트의 전체 셀 저장 공간이 찼습니다. 완료한 부분은 보존했습니다. 필요한 자료를 내려받거나 다른 프로젝트에 나누어 저장하세요.");
   int perRowFormulas=0;for(Object column:(List<?>)data.get("columns"))if(((Map<?,?>)column).containsKey("formula"))perRowFormulas++;
   for(String address:ExcelCells.map(ExcelCells.meta(data),"formulas").keySet())if(ExcelCells.position(address)[0]==rows(data))perRowFormulas++;
   if(perRowFormulas>0){long formulaRoom=(200000-totalFormulas)/perRowFormulas;if(formulaRoom<1)throw new Problem(422,"AI_FORMULA_CAPACITY","프로젝트의 수식 계산 공간이 찼습니다. 완료한 부분은 보존했습니다. 계산 열을 줄이거나 프로젝트를 나누어 이어 주세요.");room=(int)Math.min(room,formulaRoom);}
   limit=(int)Math.min(limit,Math.min(room,projectRoom));
  }
  if(limit<1)throw Problem.invalid("처리할 남은 행이 없습니다.");p.put("chunkRows",limit);p.put("operationsOnly",!mode.equals("RANGE")&&rows(data)>0);return p;
 }
 public static Map<String,Object> resume(Map<String,Object> request,String status,Map<String,Object> result,String error){
  var plan=object(request.get("continuation"));if(plan.isEmpty())throw Problem.invalid("이어 처리 체크포인트가 없는 작업입니다.");if(Boolean.TRUE.equals(plan.get("abandoned")))throw Problem.conflict("사용자가 종료한 이어 처리 작업입니다. 완료한 데이터는 유지되어 있습니다.");
  Map<String,Object> next;
  if(status.equals("APPLIED")){var checkpoint=object(result.get("continuationResult"));if(checkpoint.isEmpty())throw Problem.invalid("데이터를 적용한 체크포인트를 찾지 못했습니다.");next=new LinkedHashMap<>(object(checkpoint.get("next")));if(Boolean.TRUE.equals(next.get("done")))throw Problem.invalid("이 이어 처리 작업은 이미 완료되었습니다.");}
  else if(status.equals("READY")&&Boolean.FALSE.equals(object(result.get("proposal")).get("ready")))next=new LinkedHashMap<>(plan);
  else if(Set.of("FAILED","CANCELLED").contains(status)){if(REPLACE_SOURCE.contains(error))throw new Problem(409,"AI_SOURCE_REPLACEMENT_REQUIRED","같은 원본으로 반복 요청할 수 없습니다. 완료한 데이터를 보존하고 이어 처리를 종료한 뒤, 크기를 줄이거나 올바른 새 원본을 연결해 작업을 시작하세요.");next=new LinkedHashMap<>(plan);if(REDUCE.contains(error))next.put("chunkRows",Math.max(1,number(next,"chunkRows")/2));}
  else throw Problem.conflict("현재 분량을 먼저 검토·적용하거나 실패한 요청을 취소한 뒤 이어 처리하세요.");
  return next;
 }
 /** Validate the model's extraction cursor against actual proposed rows; never trust its count. */
 @SuppressWarnings("unchecked") public static Map<String,Object> result(Map<String,Object> plan,Map<String,Object> proposal,Map<String,Object> book){
  if(plan.isEmpty())return Map.of();var progress=object(proposal.get("continuation"));
  if(progress.isEmpty())throw Problem.invalid("AI가 이어 처리 위치를 제공하지 않았습니다. 완료로 판단하지 않고 현재 분량을 다시 요청합니다.");
  String mode=str(plan,"mode");var table=object(proposal.get("sheet"));var ops=proposal.get("operations") instanceof List<?> l?l:List.of();int produced=0;
  if(mode.equals("RANGE")){if(table.isEmpty()||!ops.isEmpty())throw Problem.invalid("범위 이어 검토는 현재 범위의 표만 제안해야 합니다.");produced=rows(table);if(produced!=number(plan,"chunkRows"))throw Problem.invalid("범위 이어 검토의 행 수가 달라졌습니다. 같은 행을 다시 검토하세요.");AiSheetScope.merge(WorkbookRules.data(book,str(plan,"targetSheetId")),table,Map.of("rowStart",number(plan,"rangeStart"),"rowCount",produced));}
  else{
   if(!table.isEmpty()){if(Boolean.TRUE.equals(plan.get("operationsOnly"))||rows(WorkbookRules.data(book,str(plan,"targetSheetId")))>0)throw Problem.invalid("이어 추가에서는 기존 원본을 표 전체로 교체할 수 없습니다.");produced=rows(table);}
   for(Object op:ops){var command=ProposalRules.object(op);if(!"APPEND_ROWS".equals(command.get("kind")))throw Problem.invalid("생성·사진 이어 처리는 새 행 추가만 제안해야 합니다. 다른 편집은 별도 요청하세요.");produced+=((List<?>)command.get("rows")).size();}
  }
  int columns=!table.isEmpty()?((List<?>)table.get("columns")).size():((List<?>)WorkbookRules.data(book,str(plan,"targetSheetId")).get("columns")).size();
  if(produced>number(plan,"chunkRows")||(long)produced*columns>4000)throw new Problem(422,"AI_OUTPUT_LIMIT","이번 분량이 안전한 행·셀 크기를 넘었습니다. 같은 위치에서 더 작게 나누어 다시 요청하세요.");
  boolean hasMore=Boolean.TRUE.equals(progress.get("hasMore"));int cursor=number(plan,mode.equals("RANGE")?"rangeStart":"sourceRowStart");
  if(number(progress,"nextRow")!=cursor+produced)throw Problem.invalid("AI가 반환한 다음 원본 행이 실제 추가 행 수와 맞지 않습니다. 누락·중복을 막기 위해 이번 분량을 적용하지 않습니다.");
  boolean eofAllowed=mode.equals("PHOTO")||mode.equals("GENERATE")&&number(plan,"targetRows")==0&&Boolean.TRUE.equals(plan.get("endCheckNeeded"));
  boolean schemaOnly=mode.equals("GENERATE")&&number(plan,"targetRows")==0&&number(plan,"processedRows")==0&&produced==0&&!hasMore&&!table.isEmpty()&&columns>0&&ops.isEmpty();
  if(produced==0&&!schemaOnly&&(!eofAllowed||hasMore||!table.isEmpty()||!ops.isEmpty()))throw Problem.invalid("새 행 없이 계속 진행한다고 응답했습니다. 현재 자료의 남은 범위를 확인한 뒤 다시 요청하세요.");
  long characters=0;if(!table.isEmpty())for(Object r:(List<?>)table.get("rows"))for(Object value:(List<?>)r)characters+=value.toString().length();for(Object o:ops)for(Object r:(List<?>)ProposalRules.object(o).get("rows"))for(Object value:(List<?>)r)characters+=value.toString().length();
  boolean endCheckNeeded=!hasMore&&produced>0&&(mode.equals("PHOTO")||mode.equals("GENERATE")&&number(plan,"targetRows")==0)&&(produced==number(plan,"chunkRows")||(long)produced*columns==4000||characters>=80000);
  if(endCheckNeeded)hasMore=true;
  var next=new LinkedHashMap<>(plan);next.put("processedRows",number(plan,"processedRows")+produced);next.put("sourceRowStart",cursor+produced);
  next.put("endCheckNeeded",endCheckNeeded);
  boolean done;
  if(mode.equals("PHOTO")){if(!hasMore){int index=number(plan,"sourceIndex")+1;next.put("sourceIndex",index);next.put("sourceRowStart",1);if(index<number(plan,"sourceCount"))next.put("targetSheetId",((List<String>)plan.get("sourceSheetIds")).get(index));}done=number(next,"sourceIndex")>=number(plan,"sourceCount");}
  else if(mode.equals("RANGE")){next.put("rangeStart",cursor+produced);done=number(next,"processedRows")>=number(plan,"rangeCount");}
  else{done=number(plan,"targetRows")>0?number(next,"processedRows")>=number(plan,"targetRows"):!hasMore;if(number(plan,"targetRows")>0&&number(next,"processedRows")>number(plan,"targetRows"))throw Problem.invalid("요청한 전체 목표 행 수를 넘었습니다.");}
  next.put("done",done);return Map.of("producedRows",produced,"next",next,"note",Objects.toString(progress.get("note"),""),"done",done,"requiredField",schemaOnly?"sheet":produced==0?"continuation":table.isEmpty()?"operations":"sheet", "source",Map.of("mode",mode,"rowStart",cursor,"rowCount",produced,"assetId",mode.equals("PHOTO")?((List<String>)plan.get("sourceAssetIds")).get(number(plan,"sourceIndex")):"","targetSheetId",str(plan,"targetSheetId")));
 }
 public static Map<String,Object> publicState(Map<String,Object> plan,String status,Map<String,Object> result,String error){
  if(plan.isEmpty())return Map.of();
  var checked=object(result.get("continuationResult"));
  var base=status.equals("APPLIED")&&!checked.isEmpty()?object(checked.get("next")):plan;
  var out=new LinkedHashMap<>(base);
  boolean retry=Set.of("FAILED","CANCELLED").contains(status);
  boolean instructionRequired=retry&&REDUCE.contains(error)&&number(base,"chunkRows")==1;
  boolean sourceRequired=retry&&REPLACE_SOURCE.contains(error);
  boolean done=status.equals("APPLIED")&&Boolean.TRUE.equals(base.get("done"));
  String phase=done?"COMPLETED":status.equals("APPLIED")?"NEXT":instructionRequired?"NEEDS_INPUT":retry?"RETRY":status.equals("READY")?(Boolean.FALSE.equals(object(result.get("proposal")).get("ready"))?"NEEDS_INPUT":"REVIEW"):"RUNNING";
  if(sourceRequired){phase="SOURCE_REQUIRED";retry=false;}
  if(Boolean.TRUE.equals(plan.get("abandoned"))){phase="ABANDONED";retry=false;done=false;instructionRequired=false;sourceRequired=false;out.put("abandoned",true);}
  out.put("status",phase);out.put("nextAllowed",phase.equals("NEXT")||retry&&!instructionRequired);out.put("canRetry",retry&&!instructionRequired);out.put("done",done);
  out.put("needsInstruction",instructionRequired);out.put("needsSourceReplacement",sourceRequired);out.put("proposedRows",checked.getOrDefault("producedRows",0));out.put("requiredField",checked.getOrDefault("requiredField",""));
  out.put("nextChunkRows",retry&&REDUCE.contains(error)?Math.max(1,number(base,"chunkRows")/2):number(base,"chunkRows"));
  out.put("message",phase.equals("ABANDONED")?"이어 처리를 종료했습니다. 적용한 데이터는 그대로 유지됩니다.":done?"선택한 작업을 모두 적용했습니다.":sourceRequired?"같은 원본을 반복 요청하지 않았습니다. 완료한 데이터를 보존하고 이어 처리를 종료한 뒤, 크기를 줄이거나 올바른 새 원본으로 작업을 시작하세요.":instructionRequired?"1행으로 줄여도 처리 한도를 넘었습니다. 긴 문장의 요약·불필요한 열 제외 등 지침을 수정하면 같은 위치에서 다시 요청할 수 있습니다.":phase.equals("NEEDS_INPUT")?"AI의 질문에 답변하면 같은 원본 위치에서 이어 처리합니다.":retry?"완료한 부분을 보존했습니다. 실패한 위치부터 이어 처리할 수 있습니다.":phase.equals("NEXT")?"이번 분량을 적용했습니다. 다음 분량을 이어 처리하세요.":"이번 분량을 검토한 뒤 적용하면 처리 위치가 저장됩니다.");
  return out;
 }
 public static String instruction(Map<String,Object> p){return p.isEmpty()?"":"\n서버에서 관리하는 이어 처리 체크포인트: "+p+"\n이번 분량은 chunkRows행 이하이며 4000셀·80000자를 넘기지 마세요. GENERATE는 processedRows 이후 새 행만 만들고 targetRows>0이면 남은 목표만 작성하세요. PHOTO는 현재 사진의 제목을 제외한 sourceRowStart 번째 실제 데이터 행부터 읽고, 판독 가능한 정확한 연속 행만 옮기세요. 다음 행을 생략하거나 같은 행을 반복하지 마세요. 사진의 끝이 확실하지 않거나 잘려 있으면 완료라 말하지 말고 질문하세요. RANGE는 rangeStart에서 chunkRows 행의 셀 값만 검토합니다. continuation에 hasMore(원본/목표의 남은 행 여부), nextRow(현재 원본 시작 행 + 실제 제안 행수), note(끝 위치와 남은 범위)를 반드시 기록하세요. 사진 끝 또는 endCheckNeeded인 생성 작업에 추가할 행이 전혀 없는 것이 확인되면 sheet=null,operations=[],ready=true,hasMore=false,nextRow=현재 sourceRowStart로 종료 확인을 제안할 수 있습니다. 한도 도달은 전체 완료가 아닙니다. endCheckNeeded=true이면 직전 분량이 한도에 도달해 끝 확인을 위해 이어 요청한 것입니다. 다음 원본 행의 존재를 다시 확인하세요.";}
 public static int number(Map<String,Object> m,String key){return m.get(key) instanceof Number n?n.intValue():0;}
 public static String str(Map<String,Object> m,String key){return Objects.toString(m.get(key),"");}
 @SuppressWarnings("unchecked") public static Map<String,Object> object(Object x){return x instanceof Map<?,?>?(Map<String,Object>)x:Map.of();}
 private static String identity(Map<String,Object> data){return com.axplatform.security.Crypto.sha256(data.get("rowIds").toString()+"|"+data.get("columns").toString());}
 private static int rows(Map<String,Object> data){return data.get("rows") instanceof List<?> l?l.size():0;}
 private static int integer(Map<String,Object> m,String key,int fallback,int min,int max){Object x=m.get(key);if(x==null)return fallback;if(!(x instanceof Number n)||n.doubleValue()!=n.intValue()||n.intValue()<min||n.intValue()>max)throw Problem.invalid(key+" 범위를 확인하세요.");return n.intValue();}
 private static List<String> strings(Object value,int max){if(value==null)return List.of();if(!(value instanceof List<?> l)||l.size()>max||l.stream().anyMatch(x->!(x instanceof String s)||s.isBlank()))throw Problem.invalid("이어 처리 원본/시트 목록을 확인하세요.");return l.stream().map(Object::toString).toList();}
}

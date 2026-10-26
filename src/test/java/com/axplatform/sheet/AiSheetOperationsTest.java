package com.axplatform.sheet;
import com.axplatform.ai.ProposalRules;
import com.axplatform.common.Problem;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class AiSheetOperationsTest {
 private Map<String,Object> data(int n){var rows=new ArrayList<List<String>>();var ids=new ArrayList<String>();for(int r=0;r<n;r++){rows.add(List.of(String.valueOf(r+1)));ids.add("r"+r);}return Map.of("columns",List.of(Map.of("id","quantity","label","수량","type","NUMBER","unit","","width",160)),"rows",rows,"rowIds",ids);}
 private Map<String,Object> book(Map<String,Object> data){return WorkbookRules.normalize(Map.of("format","AX_WORKBOOK_1","activeSheetId","sheet1","sheets",List.of(Map.of("id","sheet1","name","원본","data",data,"sourceAssetIds",List.of()))));}
 private Map<String,Object> op(String kind,String range,String value,String label,String type,String formula){return Map.of("kind",kind,"range",range,"value",value,"label",label,"columnId","","type",type,"formula",formula);}
 @Test void largeCalculatedColumnUsesAllRowsWithoutReplacingSourceWithSample(){
  var original=book(data(25000));var sample=AiSheetOperations.sample(WorkbookRules.data(original,null));assertThat(sample).containsEntry("totalRows",25000).containsEntry("sampleOnly",true).containsEntry("sampleRows",20);
  var result=AiSheetOperations.apply(original,"sheet1",List.of(op("ADD_CALCULATED_COLUMN","","","두 배","","A{row}*2")));var after=WorkbookRules.data(result.workbook(),null);
  assertThat(DataReport.rows(after)).hasSize(25000);assertThat(ExcelCells.value(after,"A25001")).isEqualTo("25000");assertThat(ExcelCells.value(after,"B25001")).isEqualTo("50000");assertThat(after.get("rowIds")).isEqualTo(WorkbookRules.data(original,null).get("rowIds"));assertThat(DataReport.columns(WorkbookRules.data(original,null))).hasSize(1);assertThat(result.preview().get(0)).containsEntry("affectedRows",25000).containsEntry("affectedCells",25000L);
 }
 @Test void appendingToLargeSourceKeepsAllExistingRowsAndPreviewOnlyShowsNewRows(){
  var original=book(data(25000));var append=new LinkedHashMap<>(op("APPEND_ROWS","","","","",""));append.put("rows",List.of(List.of("25001"),List.of("25002")));var result=AiSheetOperations.apply(original,"sheet1",List.of(append));var after=WorkbookRules.data(result.workbook(),null);
  assertThat(DataReport.rows(after)).hasSize(25002);assertThat(ExcelCells.value(after,"A25003")).isEqualTo("25002");assertThat((List<String>)after.get("rowIds")).startsWith(((List<String>)WorkbookRules.data(original,null).get("rowIds")).toArray(String[]::new));assertThat(result.preview().get(0)).containsEntry("affectedRows",2).containsEntry("affectedCells",2L);assertThat(DataReport.rows(WorkbookRules.data(original,null))).hasSize(25000);
 }
 @Test void appendedRowsExtendFormulasAndRejectOverwritingCalculatedInputs(){
  var raw=new LinkedHashMap<String,Object>();raw.put("columns",List.of(Map.of("id","amount","label","금액","type","NUMBER","width",160),Map.of("id","double","label","두배","type","NUMBER","width",160)));raw.put("rows",List.of(List.of("3","6")));raw.put("rowIds",List.of("keep"));raw.put("excel",Map.of("formulas",Map.of("B2","$A2*2")));var original=book(raw);
  var append=new LinkedHashMap<>(op("APPEND_ROWS","","","","",""));append.put("rows",List.of(List.of("4",""),List.of("5","")));var after=WorkbookRules.data(AiSheetOperations.apply(original,"sheet1",List.of(append)).workbook(),null);assertThat(ExcelCells.map(ExcelCells.meta(after),"formulas")).containsEntry("B3","$A3*2").containsEntry("B4","$A4*2");assertThat(ExcelCells.value(after,"B4")).isEqualTo("10");assertThat((List<String>)after.get("rowIds")).startsWith("keep").hasSize(3);assertThat((List<?>)AiSheetOperations.sample(WorkbookRules.data(original,null)).get("appendFormulaColumns")).hasSize(1);
  append.put("rows",List.of(List.of("4","999")));assertThatThrownBy(()->AiSheetOperations.apply(original,"sheet1",List.of(append))).isInstanceOf(Problem.class).hasMessageContaining("입력값");assertThat(DataReport.rows(WorkbookRules.data(original,null))).hasSize(1);
 }
 @Test void appendedRowsHaveSeparateNewDataBoundsAndExactWidth(){
  var original=book(data(1));var append=new LinkedHashMap<>(op("APPEND_ROWS","","","","",""));append.put("rows",Collections.nCopies(201,List.of("2")));assertThatThrownBy(()->AiSheetOperations.apply(original,"sheet1",List.of(append))).isInstanceOf(Problem.class).hasMessageContaining("200행");append.put("rows",List.of(List.of("2","3")));assertThatThrownBy(()->AiSheetOperations.apply(original,"sheet1",List.of(append))).isInstanceOf(Problem.class).hasMessageContaining("셀 수");append.put("rows",List.of());assertThatThrownBy(()->AiSheetOperations.apply(original,"sheet1",List.of(append))).isInstanceOf(Problem.class);assertThat(DataReport.rows(WorkbookRules.data(original,null))).hasSize(1);
 }
 @Test void clearRefErrorsPreservesHealthyFormulaAndUntargetedData(){
  var raw=new LinkedHashMap<>(data(3));raw.put("excel",Map.of("formulas",Map.of("A2","#REF!*2","A3","IF(TRUE,5,\"#REF!\")")));var original=WorkbookCalculation.calculate(book(raw));var result=AiSheetOperations.apply(original,"sheet1",List.of(op("CLEAR_ERRORS","ALL","","","","#REF!")));var after=WorkbookRules.data(result.workbook(),null);
  assertThat(ExcelCells.value(after,"A2")).isEmpty();assertThat(ExcelCells.value(after,"A3")).isEqualTo("5");assertThat(ExcelCells.value(after,"A4")).isEqualTo("3");assertThat(ExcelCells.map(ExcelCells.meta(after),"formulas")).containsEntry("A3","IF(TRUE,5,\"#REF!\")").doesNotContainKey("A2");assertThat(result.preview().get(0)).containsEntry("affectedCells",1L);
 }
 @Test void pausingErrorsRetainsOriginalFormulaAndRestoreReinstatesIt(){
  var raw=new LinkedHashMap<>(data(3));raw.put("excel",Map.of("formulas",Map.of("A2","#REF!*2","A3","SUM(2,3)","A4","A2+1")));var original=WorkbookCalculation.calculate(book(raw));var result=AiSheetOperations.apply(original,"sheet1",List.of(op("PAUSE_ERRORS","ALL","","","","ALL")));var after=WorkbookRules.data(result.workbook(),null);
  assertThat(ExcelCells.value(after,"A2")).isEmpty();assertThat(ExcelCells.map(ExcelCells.meta(after),"formulas")).containsEntry("A3","SUM(2,3)").containsEntry("A4","A2+1").doesNotContainKey("A2");assertThat(ExcelCells.<Map<String,Object>>map(ExcelCells.meta(after),"paused").get("A2")).containsEntry("formula","#REF!*2").containsEntry("mode","INPUT_NEEDED");assertThat(result.preview().get(0)).containsEntry("affectedCells",1L);
  var restored=WorkbookRules.data(WorkbookCalculation.repair(result.workbook(),"restore"),null);assertThat(ExcelCells.map(ExcelCells.meta(restored),"formulas")).containsEntry("A2","#REF!*2");assertThat(ExcelCells.value(restored,"A2")).isEqualTo("#REF!");assertThat(ExcelCells.map(ExcelCells.meta(restored),"paused")).isEmpty();assertThat(ExcelCells.value(WorkbookRules.data(original,null),"A2")).isEqualTo("#REF!");
 }
 @Test void pausingExplicitRangeWithReplacementValueKeepsOtherErrorsUnchanged(){
  var raw=new LinkedHashMap<>(data(2));raw.put("excel",Map.of("formulas",Map.of("A2","#REF!*2","A3","#REF!+1")));var original=WorkbookCalculation.calculate(book(raw));var after=WorkbookRules.data(AiSheetOperations.apply(original,"sheet1",List.of(op("PAUSE_ERRORS","A2","0","","","#REF!"))).workbook(),null);assertThat(ExcelCells.value(after,"A2")).isEqualTo("0");assertThat(ExcelCells.<Map<String,Object>>map(ExcelCells.meta(after),"paused").get("A2")).containsEntry("mode","USER_INPUT");assertThat(ExcelCells.value(after,"A3")).isEqualTo("#REF!");assertThat(ExcelCells.map(ExcelCells.meta(after),"formulas")).containsEntry("A3","#REF!+1");
 }
 @Test void explicitCellAndRangeEditsPreserveUnselectedRowsAndOriginalObject(){
  var original=book(data(500));var result=AiSheetOperations.apply(original,"sheet1",List.of(op("SET_CELL","A252","=SUM(2,3)","","",""),op("CLEAR_RANGE","A3:A5","","","","")));var after=WorkbookRules.data(result.workbook(),null);
  assertThat(ExcelCells.value(after,"A252")).isEqualTo("5");assertThat(ExcelCells.value(after,"A251")).isEqualTo("250");assertThat(ExcelCells.value(after,"A501")).isEqualTo("500");assertThat(ExcelCells.value(after,"A3")).isEmpty();assertThat(ExcelCells.value(WorkbookRules.data(original,null),"A3")).isEqualTo("2");
 }
 @Test void deletingRowsUsesStructureEngineAndPreservesStableRemainingIdentities(){
  var original=book(data(5));var result=AiSheetOperations.apply(original,"sheet1",List.of(op("DELETE_ROWS","3:4","","","","")));var after=WorkbookRules.data(result.workbook(),null);assertThat(after.get("rowIds")).isEqualTo(List.of("r0","r3","r4"));assertThat(DataReport.rows(after)).containsExactly(List.of("1"),List.of("4"),List.of("5"));assertThat(DataReport.rows(WorkbookRules.data(original,null))).hasSize(5);
 }
 @Test void invalidRangeHeaderAndUnknownOperationNeverMutateSource(){
  var original=book(data(3));assertThatThrownBy(()->AiSheetOperations.apply(original,"sheet1",List.of(op("CLEAR_RANGE","A1:A3","","","","")))).isInstanceOf(Problem.class);assertThatThrownBy(()->AiSheetOperations.apply(original,"sheet1",List.of(op("SET_CELL","A9","1","","","")))).isInstanceOf(Problem.class);assertThatThrownBy(()->AiSheetOperations.apply(original,"sheet1",List.of(op("SHELL","ALL","rm","","","")))).isInstanceOf(Problem.class);assertThat(DataReport.rows(WorkbookRules.data(original,null))).containsExactly(List.of("1"),List.of("2"),List.of("3"));
 }
 @Test void proposalCannotCombineOperationsAndReplacementSheet(){
  var proposal=new LinkedHashMap<String,Object>();proposal.put("summary","검토");proposal.put("confirmed",List.of());proposal.put("assumptions",List.of());proposal.put("questions",List.of());proposal.put("draft",Map.of("title","원본","answers",Map.of("purpose","","data","","result","","details","","audience","")));proposal.put("sheet",data(1));proposal.put("warnings",List.of());proposal.put("ready",true);proposal.put("operations",List.of(op("SET_CELL","A2","2","","","")));
  assertThatThrownBy(()->ProposalRules.normalize(proposal,"EXCEL")).isInstanceOf(IllegalArgumentException.class);proposal.put("sheet",null);assertThat(ProposalRules.normalize(proposal,"EXCEL").get("operations")).isEqualTo(proposal.get("operations"));assertThatThrownBy(()->ProposalRules.normalize(proposal,"WORD")).isInstanceOf(IllegalArgumentException.class);
 }
}

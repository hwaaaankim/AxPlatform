package com.axplatform.sheet;
import com.axplatform.common.Problem;
import java.util.*;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
class V004WorkflowRulesTest {
    private Map<String,Object> column(String id,String type,String formula){var c=new LinkedHashMap<String,Object>(Map.of("id",id,"label",id,"type",type,"unit","","width",160));if(formula!=null)c.put("formula",formula);return c;}
    private Map<String,Object> table(List<Map<String,Object>> cols,List<List<String>> rows){return Map.of("columns",cols,"rows",rows,"rowIds",java.util.stream.IntStream.range(0,rows.size()).mapToObj(i->"r"+i).toList());}
    @Test void formulasRecalculateByStableIdAndPreserveEmptyInputs(){
        var cols=List.of(column("qty","NUMBER",null),column("price","NUMBER",null),column("total","NUMBER","[qty] * [price]"));
        var normalized=SheetRules.normalize(table(cols,List.of(List.of("2","12.5","999"),List.of("","3","0"))));
        assertThat(normalized.get("rows")).isEqualTo(List.of(List.of("2","12.5","25"),List.of("","3","")));
        var reordered=SheetRules.normalize(table(List.of(cols.get(2),cols.get(1),cols.get(0)),List.of(List.of("999","12.5","2"))));
        assertThat(reordered.get("rows")).isEqualTo(List.of(List.of("25","12.5","2")));
    }
    @Test void badReferencesZeroDivisionCyclesAndExecutableSyntaxAreRejected(){
        for(String expr:List.of("[missing]+1","[a]/0","[total]+1","SUM(A1)","java.lang.Runtime()"))assertThatThrownBy(()->SheetRules.normalize(table(List.of(column("a","NUMBER",null),column("total","NUMBER",expr)),List.of(List.of("2",""))))).isInstanceOf(Problem.class);
        assertThatThrownBy(()->SheetRules.normalize(table(List.of(column("a","NUMBER","[b]"),column("b","NUMBER","[a]")),List.of()))).isInstanceOf(Problem.class);
        assertThatThrownBy(()->SheetRules.normalize(table(List.of(column("a","TEXT",null),column("total","NUMBER","[a]+1")),List.of()))).isInstanceOf(Problem.class);
    }
    @Test void formulaChangesRequireAdditionalPermissionAndRemovingReferencedColumnFails(){
        var cols=List.of(column("a","NUMBER",null),column("total","NUMBER",null));var before=table(cols,List.of(List.of("2","4")));var after=table(List.of(cols.get(0),column("total","NUMBER","[a]*2")),List.of(List.of("2","4")));
        assertThat(SheetRules.requiredActions(before,after)).contains("workspace.sheet.formula.update","workspace.sheet.schema.update");
        assertThatThrownBy(()->SheetRules.normalize(table(List.of(column("total","NUMBER","[a]*2")),List.of()))).isInstanceOf(Problem.class);
    }
    @Test void aggregationUsesDecimalArithmeticAndSkipsBlanksWithoutMixingUnits(){
        var sheet=SheetRules.normalize(table(List.of(column("company","TEXT",null),column("amount","NUMBER",null)),List.of(List.of("A","0.1"),List.of("A","0.2"),List.of("B",""),List.of("B","-3"))));
        var result=SheetAnalysis.calculate(sheet,Map.of("name","매출","operation","SUM","groupColumn","company","valueColumn","amount"),7);
        assertThat(result.get("sheetVersion")).isEqualTo(7L);assertThat(result.get("blankValues")).isEqualTo(1);
        assertThat(result.get("points")).isEqualTo(List.of(Map.of("label","A","value","0.3","rows",2,"numericCount",2),Map.of("label","B","value","-3","rows",2,"numericCount",1)));
        assertThatThrownBy(()->SheetAnalysis.calculate(sheet,Map.of("operation","SUM","valueColumn","company"),0)).isInstanceOf(Problem.class);
    }
    @Test void csvPreservesQuotedNewlinesLeadingZeroAndRejectsMalformedInput(){
        var preview=SheetImport.read("코드,내용\r\n00123,\"쉼표, 줄\n바꿈\"\r\n".getBytes(StandardCharsets.UTF_8),"자료.csv","UTF-8","");
        assertThat(preview.get("headers")).isEqualTo(List.of("코드","내용"));assertThat(preview.get("rows")).isEqualTo(List.of(List.of("00123","쉼표, 줄\n바꿈")));
        assertThatThrownBy(()->SheetImport.read("a\n\"broken".getBytes(StandardCharsets.UTF_8),"bad.csv","UTF-8","")).isInstanceOf(Problem.class);
    }
    @Test void generatedWorkbookRoundTripsAndFormulaPreviewWarnsAboutCachedValues() throws Exception {
        var table=SheetRules.normalize(table(List.of(column("qty","NUMBER",null),column("total","NUMBER","[qty]*2")),List.of(List.of("3",""))));
        var preview=SheetImport.read(XlsxWriter.bytes(table),"data.xlsx","UTF-8","");
        assertThat(preview.get("rows")).isEqualTo(List.of(List.of("3","6")));assertThat((List<?>)preview.get("warnings")).hasSize(2);
        assertThatThrownBy(()->SheetImport.read(new byte[]{1,2,3},"bad.xlsx","UTF-8","")).isInstanceOf(Problem.class);
    }
}

package com.axplatform.sheet;

import com.axplatform.common.Problem;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class WorkbookMaintenanceTest {
    private Map<String,Object> fixture() {
        return WorkbookFormulaTest.book(WorkbookFormulaTest.table(
            List.of(List.of("2", "3", "6"), List.of("4", "5", "20")),
            Map.of("C2", "A2*B2", "C3", "$A3*B3")));
    }
    @Test void appendedRowsKeepOriginalIdentitiesAndExtendRelativeFormula() {
        var before = fixture();
        var result = WorkbookMaintenance.rows(before, "s0", List.of(List.of("6", "7", "")), List.of("c0", "c1", "c2"), "append", "keep");
        result = WorkbookMaintenance.extend(before, result, "s0", 2);
        var data = WorkbookRules.data(result, "s0");
        assertThat(ExcelCells.map(ExcelCells.meta(data), "formulas")).containsEntry("C4", "$A4*B4");
        assertThat(ExcelCells.value(data, "C4")).isEqualTo("42");
        assertThat((List<String>) data.get("rowIds")).startsWith("r0", "r1").hasSize(3);
        assertThat((List<?>) WorkbookRules.data(before, "s0").get("rows")).hasSize(2);
    }
    @Test void replacementKeepsStableColumnIdsAndClipsOldFormulaMetadata() {
        var result = WorkbookMaintenance.rows(fixture(), "s0", List.of(List.of("9", "8")), List.of("c1", "c0"), "replace", "keep");
        var data = WorkbookRules.data(WorkbookCalculation.calculate(result), "s0");
        assertThat(ExcelCells.value(data, "A2")).isEqualTo("8");
        assertThat(ExcelCells.value(data, "C2")).isEqualTo("72");
        assertThat(ExcelCells.map(ExcelCells.meta(data), "formulas")).containsOnlyKeys("C2");
        assertThat((List<String>) data.get("rowIds")).doesNotContain("r0", "r1");
    }
    @Test void replacementWithoutFormulasAllowsLiteralImportedComputedValues() {
        var result = WorkbookMaintenance.rows(fixture(), "s0", List.of(List.of("8", "9", "99")), List.of("c0", "c1", "c2"), "replace", "discard");
        var data = WorkbookRules.data(result, "s0");
        assertThat(ExcelCells.value(data, "C2")).isEqualTo("99");
        assertThat(ExcelCells.meta(data)).isEmpty();
    }
    @Test void extensionCannotSilentlyOverwriteImportedValues() {
        var before = fixture();
        var appended = WorkbookMaintenance.rows(before, "s0", List.of(List.of("2", "3", "100")), List.of("c0", "c1", "c2"), "append", "keep");
        assertThatThrownBy(() -> WorkbookMaintenance.extend(before, appended, "s0", 2)).isInstanceOf(Problem.class).hasMessageContaining("C4").hasMessageContaining("입력값");
        assertThat(ExcelCells.value(WorkbookRules.data(appended, "s0"), "C4")).isEqualTo("100");
    }
    @Test void duplicateOrUnknownMappingFailsWithoutChangingSource() {
        var before = fixture();
        assertThatThrownBy(() -> WorkbookMaintenance.rows(before, "s0", List.of(List.of("1", "2")), List.of("c0", "c0"), "replace", "keep")).isInstanceOf(Problem.class);
        assertThatThrownBy(() -> WorkbookMaintenance.rows(before, "s0", List.of(List.of("1")), List.of("missing"), "append", "keep")).isInstanceOf(Problem.class);
        assertThat(ExcelCells.value(WorkbookRules.data(before, "s0"), "A2")).isEqualTo("2");
    }
    @Test void ordinaryCalculatedColumnsApplyToNewRows() {
        var data = new LinkedHashMap<>(SheetRules.normalize(WorkbookFormulaTest.table(List.of(List.of("2", "3", "")), Map.of())));
        var columns = new ArrayList<>((List<Map<String,Object>>) data.get("columns"));
        var resultColumn = new LinkedHashMap<>(columns.get(2)); resultColumn.put("formula", "[c0]*[c1]"); columns.set(2, resultColumn); data.put("columns", columns);
        var before = WorkbookFormulaTest.book(data);
        var next = WorkbookMaintenance.rows(before, "s0", List.of(List.of("4", "5", "")), List.of("c0", "c1", "c2"), "append", "keep");
        assertThat(ExcelCells.value(WorkbookRules.data(next, "s0"), "C3")).isEqualTo("20");
    }
    @Test void rowGrowthRespectsCellLimitAndMalformedInputCannotClearRows() {
        assertThatThrownBy(() -> WorkbookMaintenance.rows(fixture(), "s0", List.of(), List.of("c0"), "replace", "keep")).isInstanceOf(Problem.class);
        assertThatThrownBy(() -> WorkbookMaintenance.rows(fixture(), "s0", List.of(List.of("1", "2")), List.of("c0"), "append", "keep")).isInstanceOf(Problem.class);
    }
    @Test void replacementRecalculatesOtherSheetsWithoutMovingExplicitCellReferences() {
        var before = WorkbookFormulaTest.book(
            WorkbookFormulaTest.table(List.of(List.of("2", "3", "6"), List.of("4", "5", "20")), Map.of("C2", "A2*B2", "C3", "A3*B3")),
            WorkbookFormulaTest.table(List.of(List.of("26", "20")), Map.of("A2", "SUM('시트1'!C2:C3)", "B2", "'시트1'!C3")));
        var replacement = WorkbookMaintenance.rows(before, "s0", List.of(List.of("7", "8")), List.of("c0", "c1"), "replace", "keep");
        var result = WorkbookCalculation.calculate(replacement);
        var overview = WorkbookRules.data(result, "s1");
        assertThat(ExcelCells.map(ExcelCells.meta(overview), "formulas")).containsEntry("A2", "SUM('시트1'!C2:C3)").containsEntry("B2", "'시트1'!C3");
        assertThat(ExcelCells.value(overview, "A2")).isEqualTo("56");
        // Replacing data does not delete worksheet coordinates: the now-empty C3 follows Excel's zero rule.
        assertThat(ExcelCells.value(overview, "B2")).isEqualTo("0");
        assertThat(WorkbookRules.sheet(result, "s0").get("name")).isEqualTo("시트1");
        assertThat(ExcelCells.value(WorkbookRules.data(before, "s1"), "A2")).isEqualTo("26");
    }
    @Test void multipleNewRowsTranslateRelativeRangeEndAndPreserveAbsoluteRangeStart() {
        var before = WorkbookFormulaTest.book(WorkbookFormulaTest.table(
            List.of(List.of("2", "2"), List.of("4", "6")), Map.of("B2", "SUM($A$2:A2)", "B3", "SUM($A$2:A3)")));
        var changed = WorkbookMaintenance.rows(before, "s0", List.of(List.of("6", ""), List.of("8", "")), List.of("c0", "c1"), "append", "keep");
        var result = WorkbookMaintenance.extend(before, changed, "s0", 2);
        var data = WorkbookRules.data(result, "s0");
        assertThat(ExcelCells.map(ExcelCells.meta(data), "formulas")).containsEntry("B4", "SUM($A$2:A4)").containsEntry("B5", "SUM($A$2:A5)");
        assertThat(ExcelCells.value(data, "B4")).isEqualTo("12");
        assertThat(ExcelCells.value(data, "B5")).isEqualTo("20");
    }
    @Test void textIdentifiersPreserveLeadingZeroAndLongDigitsAfterReplacement() {
        var data = new LinkedHashMap<String,Object>();
        data.put("columns", List.of(Map.of("id", "code", "label", "업체 코드", "type", "TEXT", "width", 160)));
        data.put("rows", List.of(List.of("123"))); data.put("rowIds", List.of("r0"));
        data.put("excel", Map.of("values", Map.of("A2", List.of("123", "123", "n", ""))));
        var result = WorkbookMaintenance.rows(WorkbookFormulaTest.book(data), "s0", List.of(List.of("00123"), List.of("12345678901234567890")), List.of("code"), "replace", "keep");
        var next = WorkbookRules.data(WorkbookCalculation.calculate(result), "s0");
        assertThat(ExcelCells.value(next, "A2")).isEqualTo("00123");
        assertThat(ExcelCells.value(next, "A3")).isEqualTo("12345678901234567890");
        assertThat(ExcelCells.map(ExcelCells.meta(next), "values")).isEmpty();
    }
    @Test void invalidNumericAndDateValuesFailWithoutChangingOriginalRows() {
        var before = fixture();
        assertThatThrownBy(() -> WorkbookMaintenance.rows(before, "s0", List.of(List.of("00123")), List.of("c0"), "append", "keep")).isInstanceOf(Problem.class).hasMessageContaining("문자 열");
        assertThatThrownBy(() -> WorkbookMaintenance.rows(before, "s0", List.of(List.of("판매")), List.of("c0"), "replace", "discard")).isInstanceOf(Problem.class).hasMessageContaining("숫자");
        var date = WorkbookFormulaTest.book(Map.of("columns", List.of(Map.of("id", "date", "label", "일자", "type", "DATE", "width", 160)), "rows", List.of(List.of("2026-01-01")), "rowIds", List.of("r0")));
        assertThatThrownBy(() -> WorkbookMaintenance.rows(date, "s0", List.of(List.of("2026-02-31")), List.of("date"), "append", "keep")).isInstanceOf(Problem.class).hasMessageContaining("날짜");
        assertThat(((List<?>) WorkbookRules.data(before, "s0").get("rows"))).hasSize(2);
        assertThat(ExcelCells.value(WorkbookRules.data(before, "s0"), "A2")).isEqualTo("2");
    }
    @Test void brokenFormulaExpansionReportsErrorInsteadOfReusingPreviousCachedResult() {
        var before = WorkbookFormulaTest.book(WorkbookFormulaTest.table(List.of(List.of("2", "999")), Map.of("B2", "#REF!+A2")));
        var changed = WorkbookMaintenance.rows(before, "s0", List.of(List.of("4", "")), List.of("c0", "c1"), "append", "keep");
        var result = WorkbookMaintenance.extend(before, changed, "s0", 1);
        var data = WorkbookRules.data(result, "s0");
        assertThat(ExcelCells.value(data, "B3")).isEqualTo("#REF!");
        assertThat(ExcelCells.map(ExcelCells.meta(data), "issues")).containsKey("B3");
        assertThat(ExcelCells.map(ExcelCells.meta(data), "formulas")).containsEntry("B3", "#REF!+A3");
    }
    @Test void nullModeAndWorkbookAreInvalidRequestsInsteadOfNullPointerFailures() {
        assertThatThrownBy(() -> WorkbookMaintenance.rows(fixture(), "s0", List.of(List.of("1")), List.of("c0"), null, "keep")).isInstanceOf(Problem.class);
        assertThatThrownBy(() -> WorkbookMaintenance.rows(fixture(), "s0", List.of(List.of("1")), List.of("c0"), "append", null)).isInstanceOf(Problem.class);
        assertThatThrownBy(() -> WorkbookMaintenance.rows(null, "s0", List.of(List.of("1")), List.of("c0"), "append", "keep")).isInstanceOf(Problem.class);
    }
}

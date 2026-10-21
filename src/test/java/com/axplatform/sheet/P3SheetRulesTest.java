package com.axplatform.sheet;

import com.axplatform.ai.ProposalRules;
import com.axplatform.common.Problem;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.*;
import javax.xml.parsers.DocumentBuilderFactory;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class P3SheetRulesTest {
    private Map<String,Object> column(String id, String type) {
        return Map.of("id", id, "label", id, "type", type, "unit", "", "width", 160);
    }
    private Map<String,Object> sheet(List<?> columns, List<?> rows, List<String> ids) {
        return Map.of("columns", columns, "rows", rows, "rowIds", ids);
    }
    private Map<String,Object> twoRows() {
        return sheet(List.of(column("code", "TEXT"), column("quantity", "NUMBER")), List.of(List.of("001", "-3"), List.of("002", "4")), List.of("row_A", "row_B"));
    }
    @Test void preservesLeadingZeroTextNegativeNumbersAndStableRowIds() {
        assertThat(SheetRules.normalize(twoRows())).isEqualTo(twoRows());
        var changed = sheet(List.of(column("quantity", "NUMBER"), column("code", "TEXT")), List.of(List.of("-3", "001"), List.of("4", "002")), List.of("row_A", "row_B"));
        assertThat(SheetRules.requiredActions(twoRows(), changed)).containsExactly("workspace.sheet.schema.update");
    }
    @Test void changingRowIdsCannotDisguiseReplacementAsACellEdit() {
        var replaced = sheet(List.of(column("code", "TEXT"), column("quantity", "NUMBER")), List.of(List.of("001", "-3"), List.of("002", "4")), List.of("new_A", "new_B"));
        assertThat(SheetRules.requiredActions(twoRows(), replaced)).containsExactlyInAnyOrder("workspace.sheet.rows.delete", "workspace.sheet.rows.create");
        var reordered = sheet(List.of(column("code", "TEXT"), column("quantity", "NUMBER")), List.of(List.of("002", "4"), List.of("001", "-3")), List.of("row_B", "row_A"));
        assertThat(SheetRules.requiredActions(twoRows(), reordered)).containsExactly("workspace.sheet.cells.update");
    }
    @Test void calculatesAllActualPermissionsForSchemaRowsAndCells() {
        var edited = sheet(List.of(column("code", "TEXT"), column("quantity", "NUMBER")), List.of(List.of("001", "9"), List.of("003", "5")), List.of("row_A", "row_C"));
        assertThat(SheetRules.requiredActions(twoRows(), edited)).containsExactlyInAnyOrder("workspace.sheet.rows.delete", "workspace.sheet.rows.create", "workspace.sheet.cells.update");
    }
    @Test void rejectsAmbiguousNumbersUnrepresentablePrecisionAndInvalidDates() {
        for (String value : List.of("001", "1e3", "1,000", "NaN", "9007199254740993", "1.1234567890123"))
            assertThatThrownBy(() -> SheetRules.normalize(sheet(List.of(column("n", "NUMBER")), List.of(List.of(value)), List.of("r")))).isInstanceOf(Problem.class);
        for (String value : List.of("2026-02-30", "2026/10/20", "1899-12-31"))
            assertThatThrownBy(() -> SheetRules.normalize(sheet(List.of(column("d", "DATE")), List.of(List.of(value)), List.of("r")))).isInstanceOf(Problem.class);
    }
    @Test void rejectsDuplicateIdsControlCharactersAndJaggedRows() {
        assertThatThrownBy(() -> SheetRules.normalize(sheet(List.of(column("a", "TEXT")), List.of(List.of("a"), List.of("b")), List.of("r", "r")))).isInstanceOf(Problem.class);
        assertThatThrownBy(() -> SheetRules.normalize(sheet(List.of(column("a", "TEXT")), List.of(List.of("\u0000")), List.of("r")))).isInstanceOf(Problem.class);
        assertThatThrownBy(() -> SheetRules.normalize(sheet(List.of(column("a", "TEXT")), List.of(List.of("a", "extra")), List.of("r")))).isInstanceOf(Problem.class);
        assertThatThrownBy(() -> SheetRules.normalize(sheet(List.of(column("a", "TEXT"), column("a", "NUMBER")), List.of(), List.of()))).isInstanceOf(Problem.class);
    }
    @Test void xlsxIsParseableOOXMLAndFormulaLikeTextRemainsText() throws Exception {
        var table = sheet(List.of(column("code", "TEXT"), column("amount", "NUMBER"), column("day", "DATE")),
            List.of(List.of("=HYPERLINK(\"https://example.invalid\") & <문자>", "-10.50", "2026-10-20")), List.of("r"));
        byte[] archive = XlsxWriter.bytes(table);
        Map<String,String> entries = new HashMap<>();
        var factory = DocumentBuilderFactory.newInstance(); factory.setNamespaceAware(true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(archive), StandardCharsets.UTF_8)) {
            for (ZipEntry entry; (entry = zip.getNextEntry()) != null;) {
                byte[] bytes = zip.readAllBytes(); entries.put(entry.getName(), new String(bytes, StandardCharsets.UTF_8));
                factory.newDocumentBuilder().parse(new ByteArrayInputStream(bytes));
            }
        }
        assertThat(entries).containsKeys("[Content_Types].xml", "xl/workbook.xml", "xl/styles.xml", "xl/worksheets/sheet1.xml");
        String xml = entries.get("xl/worksheets/sheet1.xml");
        assertThat(xml).contains("t=\"inlineStr\"", "=HYPERLINK", "&amp; &lt;문자&gt;", "<v>-10.50</v>", "t=\"d\"", "2026-10-20");
        assertThat(xml).doesNotContain("<f>", "<hyperlink");
    }
    @Test void requiredQuestionsCannotBeReadyAndNonExcelCannotIncludeSheet() {
        Map<String,Object> proposal = new LinkedHashMap<>();
        proposal.put("summary", "확인 필요"); proposal.put("confirmed", List.of()); proposal.put("assumptions", List.of());
        proposal.put("questions", List.of(Map.of("id", "q1", "field", "purpose", "title", "어떤 목적입니까?", "reason", "사진에서 확인할 수 없습니다.", "choices", List.of("매출"), "recommended", "매출", "required", true)));
        proposal.put("draft", Map.of("title", "초안", "answers", Map.of("purpose", "", "data", "", "result", "", "details", "", "audience", "")));
        proposal.put("sheet", null); proposal.put("warnings", List.of()); proposal.put("ready", true);
        assertThatThrownBy(() -> ProposalRules.normalize(proposal, "WORD")).isInstanceOf(IllegalArgumentException.class);
        proposal.put("ready", false); assertThat(ProposalRules.normalize(proposal, "WORD").get("ready")).isEqualTo(false);
        proposal.put("sheet", twoRows());
        assertThatThrownBy(() -> ProposalRules.normalize(proposal, "WORD")).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void aiSheetRequiresStableRowIdsAndRepeatedNormalizationIsDeterministic() {
        Map<String,Object> proposal = new LinkedHashMap<>();
        proposal.put("summary", "반복 적용 검토"); proposal.put("confirmed", List.of()); proposal.put("assumptions", List.of()); proposal.put("questions", List.of());
        proposal.put("draft", Map.of("title", "초안", "answers", Map.of("purpose", "", "data", "", "result", "", "details", "", "audience", "")));
        proposal.put("sheet", twoRows()); proposal.put("warnings", List.of()); proposal.put("ready", true);
        assertThat(ProposalRules.normalize(proposal, "EXCEL")).isEqualTo(ProposalRules.normalize(proposal, "EXCEL"));
        Map<String,Object> missing = new LinkedHashMap<>(twoRows()); missing.remove("rowIds"); proposal.put("sheet", missing);
        assertThatThrownBy(() -> ProposalRules.normalize(proposal, "EXCEL")).isInstanceOf(IllegalArgumentException.class);
        Map<String,Object> additional = new LinkedHashMap<>(twoRows()); additional.put("arbitrary", "value"); proposal.put("sheet", additional);
        assertThatThrownBy(() -> ProposalRules.normalize(proposal, "EXCEL")).isInstanceOf(IllegalArgumentException.class);
    }
}

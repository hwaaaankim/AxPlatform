package com.axplatform.sheet;

import com.axplatform.common.Problem;
import java.util.*;

/** Reversible workbook previews for continuing an existing dataset. Persistence still uses SheetService. */
public final class WorkbookMaintenance {
    private WorkbookMaintenance() { }

    @SuppressWarnings("unchecked")
    public static Map<String,Object> rows(Map<String,Object> raw, String sheetId,
            List<List<String>> incoming, List<String> mapping, String mode, String formulaPolicy) {
        if (mode == null || formulaPolicy == null || !Set.of("append", "replace").contains(mode) || !Set.of("keep", "discard").contains(formulaPolicy))
            throw Problem.invalid("행 반영 방식과 수식 유지 여부를 확인하세요.");
        if (mode.equals("append") && formulaPolicy.equals("discard"))
            throw Problem.invalid("행 추가에서는 기존 수식을 제거하지 않습니다. 수식 유지 또는 자동 확장 해제를 선택하세요.");
        var book = WorkbookRules.normalize(raw);
        var old = WorkbookRules.data(book, sheetId);
        var columns = (List<Map<String,Object>>) old.get("columns");
        var oldRows = (List<List<String>>) old.get("rows");
        if (columns.isEmpty()) throw Problem.invalid("먼저 표의 열을 만들어 주세요.");
        if (incoming == null || incoming.isEmpty()) throw Problem.invalid("추가하거나 교체할 데이터 행이 없습니다.");
        if (mapping == null || mapping.isEmpty() || mapping.size() > SheetRules.MAX_COLUMNS)
            throw Problem.invalid("원본 열과 현재 표의 열 연결을 확인하세요.");
        var positions = new HashMap<String,Integer>();
        for (int c = 0; c < columns.size(); c++) positions.put((String) columns.get(c).get("id"), c);
        var mapped = new HashSet<String>();
        for (String id : mapping) {
            if (id == null || !id.isEmpty() && (!positions.containsKey(id) || !mapped.add(id)))
                throw Problem.invalid("대상 열이 없거나 같은 대상 열을 두 번 연결했습니다. 열 연결을 다시 확인하세요.");
        }
        if (mapped.isEmpty()) throw Problem.invalid("가져올 열을 한 개 이상 연결하세요.");
        boolean append = mode.equals("append"), keep = formulaPolicy.equals("keep");
        int offset = append ? oldRows.size() : 0;
        if (offset + incoming.size() > SheetRules.MAX_ROWS || (long) (offset + incoming.size()) * columns.size() > SheetRules.MAX_CELLS)
            throw Problem.invalid("50,000행 또는 시트당 100만 셀 한도를 초과합니다.");
        var data = ExcelCells.mutable(old);
        var rows = (List<List<String>>) data.get("rows");
        if (!append) rows.clear();
        var ids = new ArrayList<>(append ? (List<String>) old.get("rowIds") : List.<String>of());
        for (var source : incoming) {
            if (source == null || source.size() != mapping.size()) throw Problem.invalid("원본 행의 셀 수와 열 연결 수가 다릅니다.");
            var row = new ArrayList<>(Collections.nCopies(columns.size(), ""));
            for (int i = 0; i < mapping.size(); i++) if (!mapping.get(i).isEmpty()) {
                String value = source.get(i);
                if (value == null) throw Problem.invalid("셀 값은 문자열이어야 합니다. 빈칸은 빈 문자열로 보내세요.");
                int c = positions.get(mapping.get(i));
                if (keep && columns.get(c).containsKey("formula") && !value.isBlank())
                    throw Problem.invalid(columns.get(c).get("label") + ": 계산 열에는 입력값을 연결할 수 없습니다. 연결을 해제하거나 수식 제거를 선택하세요.");
                row.set(c, value);
            }
            rows.add(row); ids.add(UUID.randomUUID().toString());
        }
        data.put("rowIds", ids);
        data.remove("view");
        var meta = ExcelCells.meta(data);
        if (!append) {
            // An old numeric/text cache must never reinterpret a newly imported value.
            meta.remove("values"); meta.remove("issues");
            for (String key : List.of("formulas", "paused", "blocked")) {
                var entries = new LinkedHashMap<String,Object>(ExcelCells.map(meta, key));
                entries.keySet().removeIf(address -> ExcelCells.position(address)[0] > rows.size());
                meta.put(key, entries);
            }
            data.remove("presentation");
        }
        if (!keep) {
            data.remove("excel");
            ((List<Map<String,Object>>) data.get("columns")).forEach(c -> c.remove("formula"));
        } else {
            var formulas = ExcelCells.<String>map(meta, "formulas");
            // Existing formulas in replacement rows take precedence only after explicit keep selection.
            for (String address : formulas.keySet()) {
                var at = ExcelCells.position(address);
                if (at[0] > 0 && at[0] <= rows.size() && (!append || at[0] > offset)) {
                    if (!rows.get(at[0] - 1).get(at[1]).isBlank())
                        throw Problem.invalid(address + ": 기존 수식이 있는 열에 입력값이 연결되었습니다. 해당 열을 제외하거나 수식 제거를 선택하세요.");
                    rows.get(at[0] - 1).set(at[1], "");
                }
            }
        }
        return WorkbookRules.replace(book, sheetId, data);
    }

    /** Only the last data row is used as the user-visible formula template; no guessing from earlier rows. */
    @SuppressWarnings("unchecked")
    public static Map<String,Object> extend(Map<String,Object> before, Map<String,Object> changed, String sheetId, int fromRow) {
        var old = WorkbookRules.data(before, sheetId);
        int templateRow = ((List<?>) old.get("rows")).size();
        if (templateRow == 0) return WorkbookCalculation.calculate(changed);
        var data = ExcelCells.mutable(WorkbookRules.data(changed, sheetId));
        var rows = (List<List<String>>) data.get("rows");
        var meta = ExcelCells.meta(data);
        var formulas = ExcelCells.<String>map(meta, "formulas");
        var templates = ExcelCells.<String>map(ExcelCells.meta(old), "formulas");
        var names = WorkbookRules.sheets(before).stream().map(s -> (String) s.get("name")).toList();
        String name = (String) WorkbookRules.sheet(before, sheetId).get("name");
        try (var copies = new WorkbookCalculation.Copies(names)) {
            for (var source : templates.entrySet()) {
                var at = ExcelCells.position(source.getKey());
                if (at[0] != templateRow) continue;
                if (ExcelCells.map(ExcelCells.meta(old), "blocked").containsKey(source.getKey()))
                    throw Problem.invalid(source.getKey() + ": 배열 수식은 자동 확장할 수 없습니다. 자동 확장을 해제하고 개별 수식을 입력하세요.");
                for (int r = fromRow; r < rows.size(); r++) {
                    String address = ExcelCells.ref(r + 1, at[1]);
                    if (formulas.containsKey(address)) continue;
                    if (!rows.get(r).get(at[1]).isBlank())
                        throw Problem.invalid(address + ": 수식 자동 확장 대상에 입력값이 있습니다. 해당 열의 연결을 해제하거나 자동 확장을 해제하세요.");
                    try { formulas.put(address, copies.translate(source.getValue(), name, at[0], at[1], r + 1, at[1])); }
                    catch (RuntimeException e) { throw Problem.invalid(source.getKey() + ": 수식을 안전하게 이어 적용할 수 없습니다. 자동 확장을 해제하고 수식을 직접 확인하세요."); }
                }
            }
        }
        return WorkbookCalculation.calculate(WorkbookRules.replace(changed, sheetId, data));
    }
}

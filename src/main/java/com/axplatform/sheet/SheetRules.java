package com.axplatform.sheet;

import com.axplatform.common.Problem;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.*;

/** Canonical, bounded sheet values shared by manual edits and AI proposals. */
public final class SheetRules {
    public static final int MAX_COLUMNS = 20;
    public static final int MAX_ROWS = 200;
    public static final int MAX_CELL_LENGTH = 2000;
    private SheetRules() { }

    public static Map<String,Object> empty() {
        return Map.of("columns", List.of(), "rows", List.of(), "rowIds", List.of());
    }

    private static String text(Object input, int max, String label, boolean blank) {
        if (!(input instanceof String value) || value.length() > max || (!blank && value.isBlank()))
            throw Problem.invalid(label + " 형식·길이를 확인해 주세요.");
        for (int at = 0; at < value.length();) {
            int cp = value.codePointAt(at);
            if (!(cp == 9 || cp == 10 || cp == 13 || (cp >= 32 && cp <= 0xd7ff)
                    || (cp >= 0xe000 && cp <= 0xfffd) || (cp >= 0x10000 && cp <= 0x10ffff)))
                throw Problem.invalid(label + "에 저장할 수 없는 문자가 있습니다.");
            at += Character.charCount(cp);
        }
        return value;
    }

    public static Map<String,Object> normalize(Map<String,Object> raw) {
        if (raw == null || !Set.of("columns", "rows", "rowIds").containsAll(raw.keySet())
                || !(raw.get("columns") instanceof List<?> inputColumns)
                || !(raw.get("rows") instanceof List<?> inputRows))
            throw Problem.invalid("표의 열과 행을 확인해 주세요.");
        if (inputColumns.size() > MAX_COLUMNS || inputRows.size() > MAX_ROWS)
            throw Problem.invalid("이번 기본 표는 최대 20열·200행입니다.");
        if (inputColumns.isEmpty() && !inputRows.isEmpty())
            throw Problem.invalid("먼저 열을 추가해 주세요.");
        List<Map<String,Object>> columns = new ArrayList<>();
        Set<String> columnIds = new HashSet<>();
        long totalBytes = 0;
        for (Object value : inputColumns) {
            if (!(value instanceof Map<?,?> c)
                    || !Set.of("id", "label", "type", "unit", "width", "formula").containsAll(c.keySet()))
                throw Problem.invalid("열 속성을 확인해 주세요.");
            String id = text(c.get("id"), 40, "열 ID", false);
            if (!id.matches("[A-Za-z][A-Za-z0-9_-]{0,39}") || !columnIds.add(id))
                throw Problem.invalid("열 ID는 중복되지 않는 영문·숫자 식별자여야 합니다.");
            String label = text(c.get("label"), 100, "열 이름", false).strip();
            String type = text(c.get("type"), 10, "열 형식", false);
            if (!Set.of("TEXT", "NUMBER", "DATE").contains(type))
                throw Problem.invalid("열 형식은 문자·숫자·날짜 중 선택해 주세요.");
            String unit = text(c.containsKey("unit") ? c.get("unit") : "", 30, "단위", true).strip();
            int width = 160;
            if (c.containsKey("width")) {
                try {
                    if (!(c.get("width") instanceof Number)) throw new ArithmeticException();
                    width = new BigDecimal(c.get("width").toString()).intValueExact();
                } catch (NumberFormatException | ArithmeticException e) {
                    throw Problem.invalid("열 너비는 80~480 사이의 정수입니다.");
                }
            }
            if (width < 80 || width > 480) throw Problem.invalid("열 너비는 80~480 사이입니다.");
            Map<String,Object> column = new LinkedHashMap<>();
            column.put("id", id); column.put("label", label); column.put("type", type);
            column.put("unit", unit); column.put("width", width);
            String formula = text(c.containsKey("formula") ? c.get("formula") : "", 500, "계산식", true).strip();
            if (!formula.isEmpty()) column.put("formula", formula);
            columns.add(column);
            totalBytes += label.getBytes(StandardCharsets.UTF_8).length + unit.getBytes(StandardCharsets.UTF_8).length;
        }
        List<List<String>> rows = new ArrayList<>();
        for (int r = 0; r < inputRows.size(); r++) {
            if (!(inputRows.get(r) instanceof List<?> values) || values.size() != columns.size())
                throw Problem.invalid((r + 1) + "행의 셀 수와 열 수가 다릅니다.");
            List<String> row = new ArrayList<>();
            for (int c = 0; c < values.size(); c++) {
                String value = text(values.get(c), MAX_CELL_LENGTH, (r + 1) + "행 " + (c + 1) + "열", true);
                String type = (String) columns.get(c).get("type");
                if (columns.get(c).containsKey("formula")) value="";
                if (!type.equals("TEXT")) value = value.strip();
                if (!value.isEmpty() && type.equals("NUMBER")) {
                    if (!value.matches("[+-]?(?:0|[1-9][0-9]*)(?:\\.[0-9]{1,12})?"))
                        throw Problem.invalid((r + 1) + "행 " + (c + 1) + "열: 숫자는 소수점 형식으로 입력하세요. 앞자리 0이 필요한 번호는 문자 열을 사용하세요.");
                    BigDecimal n = new BigDecimal(value);
                    if (n.stripTrailingZeros().precision() > 15)
                        throw Problem.invalid((r + 1) + "행 " + (c + 1) + "열: Excel 정밀도를 넘는 15자리 초과 숫자는 문자 열로 보관하세요.");
                    value = n.toPlainString();
                }
                if (!value.isEmpty() && type.equals("DATE")) {
                    try {
                        LocalDate day = LocalDate.parse(value);
                        if (day.getYear() < 1900 || day.getYear() > 9999) throw new DateTimeParseException("range", value, 0);
                        value = day.toString();
                    } catch (DateTimeParseException e) {
                        throw Problem.invalid((r + 1) + "행 " + (c + 1) + "열: 날짜는 1900~9999년의 YYYY-MM-DD 형식입니다.");
                    }
                }
                totalBytes += value.getBytes(StandardCharsets.UTF_8).length;
                if (totalBytes > 2_000_000) throw Problem.invalid("표 내용은 UTF-8 기준 2MB 이내로 나누어 주세요.");
                row.add(value);
            }
            rows.add(row);
        }
        SheetFormula.calculate(columns, rows);
        List<?> inputIds;
        if (!raw.containsKey("rowIds")) inputIds = rows.stream().map(x -> UUID.randomUUID().toString()).toList();
        else if (raw.get("rowIds") instanceof List<?> ids && ids.size() == rows.size()) inputIds = ids;
        else throw Problem.invalid("행 ID 수와 데이터 행 수가 다릅니다.");
        List<String> rowIds = new ArrayList<>();
        Set<String> uniqueIds = new HashSet<>();
        for (Object idValue : inputIds) {
            String id = text(idValue, 64, "행 ID", false);
            if (!id.matches("[A-Za-z0-9_-]{1,64}") || !uniqueIds.add(id)) throw Problem.invalid("중복되거나 잘못된 행 ID입니다.");
            rowIds.add(id);
        }
        Map<String,Object> result = new LinkedHashMap<>();
        result.put("columns", List.copyOf(columns)); result.put("rows", List.copyOf(rows)); result.put("rowIds", List.copyOf(rowIds));
        return result;
    }

    @SuppressWarnings("unchecked")
    public static Set<String> requiredActions(Map<String,Object> oldSheet, Map<String,Object> newSheet) {
        Map<String,Object> before = normalize(oldSheet), after = normalize(newSheet);
        var oldColumns = (List<Map<String,Object>>) before.get("columns");
        var newColumns = (List<Map<String,Object>>) after.get("columns");
        var oldRows = (List<List<String>>) before.get("rows"); var newRows = (List<List<String>>) after.get("rows");
        var oldIds = (List<String>) before.get("rowIds"); var newIds = (List<String>) after.get("rowIds");
        Set<String> actions = new LinkedHashSet<>();
        if (!oldColumns.equals(newColumns)) actions.add("workspace.sheet.schema.update");
        Map<String,Object> oldFormulas=new LinkedHashMap<>(),newFormulas=new LinkedHashMap<>();
        oldColumns.stream().filter(c->c.containsKey("formula")).forEach(c->oldFormulas.put((String)c.get("id"),c.get("formula")));
        newColumns.stream().filter(c->c.containsKey("formula")).forEach(c->newFormulas.put((String)c.get("id"),c.get("formula")));
        if(!oldFormulas.equals(newFormulas))actions.add("workspace.sheet.formula.update");
        if (oldIds.stream().anyMatch(x -> !newIds.contains(x))) actions.add("workspace.sheet.rows.delete");
        if (newIds.stream().anyMatch(x -> !oldIds.contains(x))) actions.add("workspace.sheet.rows.create");
        List<String> oldCommon = oldIds.stream().filter(newIds::contains).toList();
        List<String> newCommon = newIds.stream().filter(oldIds::contains).toList();
        if (!oldCommon.equals(newCommon)) actions.add("workspace.sheet.cells.update");
        Map<String,Integer> oldIndex = new HashMap<>();
        for (int c = 0; c < oldColumns.size(); c++) oldIndex.put((String) oldColumns.get(c).get("id"), c);
        for (String rowId : newCommon) {
            List<String> oldRow = oldRows.get(oldIds.indexOf(rowId)), newRow = newRows.get(newIds.indexOf(rowId));
            for (int c = 0; c < newColumns.size(); c++) {
                Integer oldAt = oldIndex.get(newColumns.get(c).get("id"));
                if (oldAt == null ? !newRow.get(c).isEmpty() : !Objects.equals(oldRow.get(oldAt), newRow.get(c)))
                    actions.add("workspace.sheet.cells.update");
            }
        }
        return Collections.unmodifiableSet(actions);
    }
}

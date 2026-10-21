package com.axplatform.sheet;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.*;

/** Small real OOXML workbook writer: text/decimal/ISO date cells and validated internal arithmetic formulas. */
public final class XlsxWriter {
    private XlsxWriter() { }
    private static String xml(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&apos;");
    }
    private static void part(ZipOutputStream zip, String name, String value) throws IOException {
        ZipEntry entry = new ZipEntry(name); entry.setTime(0); zip.putNextEntry(entry);
        zip.write(("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>" + value).getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }
    private static void textCell(StringBuilder out, String ref, String text, int style) {
        out.append("<c r=\"").append(ref).append("\" t=\"inlineStr\" s=\"").append(style)
            .append("\"><is><t xml:space=\"preserve\">").append(xml(text)).append("</t></is></c>");
    }
    @SuppressWarnings("unchecked")
    private static String worksheet(Map<String,Object> input) {
        Map<String,Object> sheet = SheetRules.normalize(input);
        var columns = (List<Map<String,Object>>) sheet.get("columns");
        var rows = (List<List<String>>) sheet.get("rows");
        if (columns.isEmpty()) return "<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\"><sheetData/></worksheet>";
        String last = String.valueOf((char) ('A' + columns.size() - 1));
        StringBuilder data = new StringBuilder("<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">");
        data.append("<dimension ref=\"A1:").append(last).append(rows.size() + 1).append("\"/>")
            .append("<sheetViews><sheetView workbookViewId=\"0\"><pane ySplit=\"1\" topLeftCell=\"A2\" activePane=\"bottomLeft\" state=\"frozen\"/></sheetView></sheetViews><cols>");
        for (int c = 0; c < columns.size(); c++) data.append("<col min=\"").append(c + 1).append("\" max=\"").append(c + 1)
            .append("\" width=\"").append(Math.round(((Number) columns.get(c).get("width")).doubleValue() / 7.0 * 100) / 100.0)
            .append("\" customWidth=\"1\"/>");
        data.append("</cols><sheetData><row r=\"1\">");
        for (int c = 0; c < columns.size(); c++) {
            String unit = (String) columns.get(c).get("unit");
            String label = columns.get(c).get("label") + (unit.isEmpty() ? "" : " (" + unit + ")");
            textCell(data, String.valueOf((char) ('A' + c)) + 1, label, 1);
        }
        data.append("</row>");
        for (int r = 0; r < rows.size(); r++) {
            data.append("<row r=\"").append(r + 2).append("\">");
            for (int c = 0; c < columns.size(); c++) {
                String value = rows.get(r).get(c), ref = String.valueOf((char) ('A' + c)) + (r + 2);
                if (value.isEmpty() && !columns.get(c).containsKey("formula")) continue;
                String type = (String) columns.get(c).get("type");
                if (columns.get(c).get("formula") instanceof String formula) {
                    Map<String,Integer> indexes=new LinkedHashMap<>();for(int a=0;a<columns.size();a++)indexes.put((String)columns.get(a).get("id"),a);
                    String expression=SheetFormula.excel(formula,indexes,r+2);
                    var references=SheetFormula.references(formula);
                    if(!references.isEmpty()) {
                        int excelRow=r+2;
                        String args=String.join(",",references.stream().map(id->""+(char)('A'+indexes.get(id))+excelRow).toList());
                        expression="IF(COUNT("+args+")<"+references.size()+",\"\","+expression+")";
                    }
                    data.append("<c r=\"").append(ref).append(value.isEmpty()?"\" t=\"str\"><f>":"\"><f>").append(xml(expression)).append("</f><v>").append(xml(value)).append("</v></c>");
                    continue;
                }
                if (type.equals("TEXT")) textCell(data, ref, value, 0);
                else data.append("<c r=\"").append(ref).append("\" t=\"").append(type.equals("DATE") ? "d" : "n")
                    .append("\" s=\"").append(type.equals("DATE") ? 2 : 0).append("\"><v>").append(xml(value)).append("</v></c>");
            }
            data.append("</row>");
        }
        data.append("</sheetData><autoFilter ref=\"A1:").append(last).append(rows.size() + 1).append("\"/></worksheet>");
        return data.toString();
    }
    public static byte[] bytes(Map<String,Object> input) throws IOException {
        if(((List<?>)SheetRules.normalize(input).get("columns")).isEmpty()) throw com.axplatform.common.Problem.invalid("표에 열을 한 개 이상 추가한 뒤 다운로드하세요.");
        return workbookBytes(WorkbookRules.normalize(input));
    }
    public static byte[] workbookBytes(Map<String,Object> input) throws IOException {
        var book=WorkbookRules.normalize(input);
        if(WorkbookRules.sheets(book).stream().allMatch(s->((List<?>)WorkbookRules.data(book,(String)s.get("id")).get("columns")).isEmpty()))
            throw com.axplatform.common.Problem.invalid("한 시트 이상에 열을 추가한 뒤 다운로드하세요.");
        ByteArrayOutputStream buffer=new ByteArrayOutputStream();
        try(ZipOutputStream zip=new ZipOutputStream(buffer,StandardCharsets.UTF_8)) {
            StringBuilder types=new StringBuilder("<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\"><Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/><Default Extension=\"xml\" ContentType=\"application/xml\"/><Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/><Override PartName=\"/xl/styles.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml\"/>");
            StringBuilder workbook=new StringBuilder("<workbook xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\"><sheets>");
            StringBuilder links=new StringBuilder("<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\"><Relationship Id=\"styles\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles\" Target=\"styles.xml\"/>");
            int index=0;
            for(var sheet:WorkbookRules.sheets(book)) {
                index++;String path="worksheets/sheet"+index+".xml";
                types.append("<Override PartName=\"/xl/").append(path).append("\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>");
                workbook.append("<sheet name=\"").append(xml((String)sheet.get("name"))).append("\" sheetId=\"").append(index).append("\" r:id=\"rId").append(index).append("\"/>");
                links.append("<Relationship Id=\"rId").append(index).append("\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"").append(path).append("\"/>");
                part(zip,"xl/"+path,worksheet(WorkbookRules.data(book,(String)sheet.get("id"))));
            }
            part(zip,"[Content_Types].xml",types.append("</Types>").toString());
            part(zip,"xl/workbook.xml",workbook.append("</sheets></workbook>").toString());
            part(zip,"xl/_rels/workbook.xml.rels",links.append("</Relationships>").toString());
            part(zip, "_rels/.rels", "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\"><Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"xl/workbook.xml\"/></Relationships>");
            part(zip, "xl/styles.xml", "<styleSheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\"><fonts count=\"2\"><font><sz val=\"11\"/><name val=\"맑은 고딕\"/></font><font><b/><sz val=\"11\"/><color rgb=\"FFFFFFFF\"/><name val=\"맑은 고딕\"/></font></fonts><fills count=\"3\"><fill><patternFill patternType=\"none\"/></fill><fill><patternFill patternType=\"gray125\"/></fill><fill><patternFill patternType=\"solid\"><fgColor rgb=\"FF145E65\"/><bgColor indexed=\"64\"/></patternFill></fill></fills><borders count=\"1\"><border><left/><right/><top/><bottom/><diagonal/></border></borders><cellStyleXfs count=\"1\"><xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\"/></cellStyleXfs><cellXfs count=\"3\"><xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\" xfId=\"0\" applyAlignment=\"1\"><alignment vertical=\"top\" wrapText=\"1\"/></xf><xf numFmtId=\"0\" fontId=\"1\" fillId=\"2\" borderId=\"0\" xfId=\"0\" applyFont=\"1\" applyFill=\"1\" applyAlignment=\"1\"><alignment vertical=\"center\" wrapText=\"1\"/></xf><xf numFmtId=\"14\" fontId=\"0\" fillId=\"0\" borderId=\"0\" xfId=\"0\" applyNumberFormat=\"1\"/></cellXfs><cellStyles count=\"1\"><cellStyle name=\"Normal\" xfId=\"0\" builtinId=\"0\"/></cellStyles></styleSheet>");
        }
        return buffer.toByteArray();
    }
}

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
    private static String worksheet(Map<String,Object> input,XlsxStyles styles) {
        Map<String,Object> sheet = SheetRules.normalize(input);
        var columns = (List<Map<String,Object>>) sheet.get("columns");
        var rows = (List<List<String>>) sheet.get("rows");
        if (columns.isEmpty()) return "<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\"><sheetData/></worksheet>";
        String last = SheetFormula.letter(columns.size()-1);
        StringBuilder data = new StringBuilder("<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">");
        data.append("<dimension ref=\"A1:").append(last).append(rows.size() + 1).append("\"/>")
            .append("<sheetViews><sheetView workbookViewId=\"0\"><pane ySplit=\"1\" topLeftCell=\"A2\" activePane=\"bottomLeft\" state=\"frozen\"/></sheetView></sheetViews><cols>");
        for (int c = 0; c < columns.size(); c++) data.append("<col min=\"").append(c + 1).append("\" max=\"").append(c + 1)
            .append("\" width=\"").append(Math.round(((Number) columns.get(c).get("width")).doubleValue() / 7.0 * 100) / 100.0)
            .append("\" customWidth=\"1\"/>");
        data.append("</cols><sheetData><row r=\"1\">");
        for (int c = 0; c < columns.size(); c++) {
            String unit = (String) columns.get(c).get("unit");
            String label = columns.get(c).containsKey("sourceHeader")?(String)columns.get(c).get("sourceHeader"):columns.get(c).get("label") + (unit.isEmpty() ? "" : " (" + unit + ")");
            String ref=SheetFormula.letter(c)+1;int style=styles.cell(sheet,0,c);if(!excelCell(data,sheet,ref,label,style))textCell(data,ref,label,style);
        }
        data.append("</row>");
        for (int r = 0; r < rows.size(); r++) {
            data.append("<row r=\"").append(r + 2).append("\">");
            for (int c = 0; c < columns.size(); c++) {
                String value = rows.get(r).get(c), ref = SheetFormula.letter(c) + (r + 2);
                int style=styles.cell(sheet,r+1,c);
                String type = (String) columns.get(c).get("type");
                if(excelCell(data,sheet,ref,value,style))continue;
                if (columns.get(c).get("formula") instanceof String formula) {
                    Map<String,Integer> indexes=new LinkedHashMap<>();for(int a=0;a<columns.size();a++)indexes.put((String)columns.get(a).get("id"),a);
                    String expression=SheetFormula.excel(formula,indexes,r+2);
                    var references=SheetFormula.references(formula);
                    if(!references.isEmpty()) {
                        int excelRow=r+2;
                        String args=String.join(",",references.stream().map(id->SheetFormula.letter(indexes.get(id))+excelRow).toList());
                        expression="IF(COUNT("+args+")<"+references.size()+",\"\","+expression+")";
                    }
                    data.append("<c r=\"").append(ref).append("\" s=\"").append(style).append(value.isEmpty()?"\" t=\"str\"><f>":"\"><f>").append(xml(expression)).append("</f><v>").append(xml(value)).append("</v></c>");
                    continue;
                }
                if (type.equals("TEXT")) textCell(data, ref, value, style);
                else if(!value.isEmpty()) data.append("<c r=\"").append(ref).append("\" t=\"").append(type.equals("DATE") ? "d" : "n")
                    .append("\" s=\"").append(style).append("\"><v>").append(xml(value)).append("</v></c>");
            }
            data.append("</row>");
        }
        data.append("</sheetData><autoFilter ref=\"A1:").append(last).append(rows.size() + 1).append("\"/>");
        if(sheet.get("presentation") instanceof Map<?,?> p && p.get("merges") instanceof List<?> merges && !merges.isEmpty()){data.append("<mergeCells count=\"").append(merges.size()).append("\">");for(Object item:merges){var m=(List<Integer>)item;data.append("<mergeCell ref=\"").append(SheetFormula.letter(m.get(1))).append(m.get(0)+1).append(":").append(SheetFormula.letter(m.get(3))).append(m.get(2)+1).append("\"/>");}data.append("</mergeCells>");}
        data.append("</worksheet>");
        return data.toString();
    }
    private static boolean excelCell(StringBuilder out,Map<String,Object> sheet,String ref,String value,int style){
        var meta=ExcelCells.meta(sheet);String formula=ExcelCells.<String>map(meta,"formulas").get(ref);var nativeValue=ExcelCells.<List<String>>map(meta,"values").get(ref);String type="",raw=value;
        if(nativeValue!=null&&nativeValue.get(1).equals(value)){raw=nativeValue.get(0);type=nativeValue.get(2);}
        if(formula==null&&type.isEmpty())return false;
        if(ExcelCells.error(value)){type="e";raw=value.equals("#CIRC!")?"#REF!":value;}
        if(formula==null&&type.equals("s")){textCell(out,ref,value,style);return true;}
        if(type.isEmpty())type=value.matches("[+-]?(?:0|[1-9][0-9]*)(?:\\.[0-9]+)?")?"n":"s";
        out.append("<c r=\"").append(ref).append("\" s=\"").append(style).append("\" t=\"").append(type.equals("s")?"str":type).append("\">");
        if(formula!=null)out.append("<f>").append(xml(formula)).append("</f>");out.append("<v>").append(xml(raw)).append("</v></c>");return true;
    }
    public static byte[] bytes(Map<String,Object> input) throws IOException {
        if(((List<?>)SheetRules.normalize(input).get("columns")).isEmpty()) throw com.axplatform.common.Problem.invalid("표에 열을 한 개 이상 추가한 뒤 다운로드하세요.");
        return workbookBytes(WorkbookRules.normalize(input));
    }
    public static byte[] workbookBytes(Map<String,Object> input) throws IOException {
        var book=WorkbookCalculation.calculate(input);
        if(WorkbookRules.sheets(book).stream().allMatch(s->((List<?>)WorkbookRules.data(book,(String)s.get("id")).get("columns")).isEmpty()))
            throw com.axplatform.common.Problem.invalid("한 시트 이상에 열을 추가한 뒤 다운로드하세요.");
        ByteArrayOutputStream buffer=new ByteArrayOutputStream();
        try(ZipOutputStream zip=new ZipOutputStream(buffer,StandardCharsets.UTF_8)) {
            StringBuilder types=new StringBuilder("<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\"><Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/><Default Extension=\"xml\" ContentType=\"application/xml\"/><Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/><Override PartName=\"/xl/styles.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml\"/>");
            boolean date1904=WorkbookRules.sheets(book).stream().anyMatch(s->Boolean.TRUE.equals(ExcelCells.meta(WorkbookRules.data(book,(String)s.get("id"))).get("date1904")));
            StringBuilder workbook=new StringBuilder("<workbook xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\"><workbookPr date1904=\""+(date1904?"1":"0")+"\"/><sheets>");
            StringBuilder links=new StringBuilder("<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\"><Relationship Id=\"styles\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles\" Target=\"styles.xml\"/>");
            int index=0;var styles=new XlsxStyles();
            for(var sheet:WorkbookRules.sheets(book)) {
                index++;String path="worksheets/sheet"+index+".xml";
                types.append("<Override PartName=\"/xl/").append(path).append("\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>");
                workbook.append("<sheet name=\"").append(xml((String)sheet.get("name"))).append("\" sheetId=\"").append(index).append("\" r:id=\"rId").append(index).append("\"/>");
                links.append("<Relationship Id=\"rId").append(index).append("\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"").append(path).append("\"/>");
                part(zip,"xl/"+path,worksheet(WorkbookRules.data(book,(String)sheet.get("id")),styles));
            }
            part(zip,"[Content_Types].xml",types.append("</Types>").toString());
            part(zip,"xl/workbook.xml",workbook.append("</sheets><calcPr calcId=\"0\" fullCalcOnLoad=\"1\"/></workbook>").toString());
            part(zip,"xl/_rels/workbook.xml.rels",links.append("</Relationships>").toString());
            part(zip, "_rels/.rels", "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\"><Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"xl/workbook.xml\"/></Relationships>");
            part(zip, "xl/styles.xml", styles.xml());
        }
        return buffer.toByteArray();
    }
}

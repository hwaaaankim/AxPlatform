package com.axplatform.web;

import com.axplatform.security.*;
import com.axplatform.sheet.SheetService;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

@RestController
public class SheetApiController {
    public record Edit(Map<String, Object> sheet, long version, long draftVersion) {}

    public record WorkbookEdit(Map<String,Object> workbook,long version,long draftVersion) {}

    @PatchMapping("/api/drafts/{id}/workbook")
    public Object editWorkbook(@PathVariable String id,@RequestBody WorkbookEdit input) {
        return sheets.putWorkbook(Subjects.current(),id,input.workbook(),input.version(),input.draftVersion());
    }

    public record Undo(long version, long draftVersion) {}

    private final com.axplatform.workspace.AssetService assets;
    private final com.axplatform.workspace.DraftService drafts;
    private final SheetService sheets;

    public SheetApiController(
            SheetService sheets,
            com.axplatform.workspace.AssetService assets,
            com.axplatform.workspace.DraftService drafts) {
        this.sheets = sheets;
        this.assets = assets;
        this.drafts = drafts;
    }

    @GetMapping("/api/drafts/{id}/sheet")
    public Object read(@PathVariable String id) {
        return sheets.get(Subjects.current(), id);
    }

    @PatchMapping("/api/drafts/{id}/sheet")
    public Object edit(@PathVariable String id, @RequestBody Edit input) {
        return sheets.put(
                Subjects.current(), id, input.sheet(), input.version(), input.draftVersion());
    }

    @PostMapping("/api/drafts/{id}/sheet/undo")
    public Object undo(@PathVariable String id, @RequestBody Undo input) {
        return sheets.undo(Subjects.current(), id, input.version(), input.draftVersion());
    }

    @PostMapping("/api/drafts/{id}/sheet/export-asset")
    public Object storeExport(@PathVariable String id) {
        var s = Subjects.current();
        var exported = sheets.export(s, id, false);
        var draft = drafts.get(s, id);
        return assets.store(
                s,
                com.axplatform.common.Inputs.number(draft, "business_scope_id"),
                exported.filename(),
                exported.data(),
                "PROJECT",
                "EXCEL",
                id,
                "OUTPUT");
    }

    @GetMapping("/api/drafts/{id}/sheet.xlsx")
    public ResponseEntity<StreamingResponseBody> export(
            @PathVariable String id,
            @RequestParam(defaultValue = "false") boolean template,
            HttpServletRequest request) {
        SessionSubject actor = Subjects.current();
        var lease = request.getSession(false);
        String originalSessionId = SessionLease.captureId(lease, actor);
        var exported = sheets.export(actor, id, template);
        StreamingResponseBody body =
                out -> {
                    try {
                        for (int at = 0; at < exported.data().length; at += 32768) {
                            SessionLease.require(lease, actor, originalSessionId);
                            sheets.checkExport(actor, id, template);
                            out.write(
                                    exported.data(),
                                    at,
                                    Math.min(32768, exported.data().length - at));
                            out.flush();
                        }
                    } catch (RuntimeException denied) {
                        try {
                            out.close();
                        } catch (IOException ignored) {
                        }
                        throw new IOException("다운로드 중 세션·범위·권한이 변경되었습니다.");
                    }
                };
        return ResponseEntity.ok()
                .contentType(
                        MediaType.parseMediaType(
                                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .contentLength(exported.data().length)
                .header("Cache-Control", "no-store")
                .header("X-Content-Type-Options", "nosniff")
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment()
                                .filename(exported.filename(), StandardCharsets.UTF_8)
                                .build()
                                .toString())
                .header("X-Ax-Sheet-Version", String.valueOf(exported.version()))
                .body(body);
    }
}

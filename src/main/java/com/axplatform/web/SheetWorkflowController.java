package com.axplatform.web;

import com.axplatform.common.Problem;
import com.axplatform.policy.AccessService;
import com.axplatform.security.Subjects;
import com.axplatform.sheet.*;

import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.*;

@RestController
public class SheetWorkflowController {
    private final AccessService access;
    private final SheetViewService views;
    private final com.axplatform.workspace.AssetService assets;

    public SheetWorkflowController(
            AccessService access,
            SheetViewService views,
            com.axplatform.workspace.AssetService assets) {
        this.access = access;
        this.views = views;
        this.assets = assets;
    }

    public record ViewInput(
            String id, long version, long sheetVersion, Map<String, Object> config) {}

    public record StoredPreview(String assetId, String encoding, String sheetName) {}

    @PostMapping("/api/sheets/workbook-preview")
    public Object workbookPreview(@RequestBody StoredPreview i) {
        var actor=Subjects.current();var asset=assets.metadata(actor,i.assetId());
        access.business(actor,com.axplatform.common.Inputs.number(asset,"business_scope_id"),false,
                List.of("workspace.sheet.import","workspace.sheet.update"),()->null);
        return SheetImport.readWorkbook(assets.read(actor,i.assetId()),(String)asset.get("original_name"),Objects.toString(i.encoding(),"UTF-8"));
    }

    @PostMapping("/api/sheets/stored-preview")
    public Object stored(@RequestBody StoredPreview i) {
        var s = Subjects.current();
        var r = assets.metadata(s, i.assetId());
        long business = com.axplatform.common.Inputs.number(r, "business_scope_id");
        access.business(
                s,
                business,
                false,
                List.of("workspace.sheet.import", "workspace.sheet.update"),
                () -> null);
        return SheetImport.read(
                assets.read(s, i.assetId()),
                (String) r.get("original_name"),
                Objects.toString(i.encoding(), "UTF-8"),
                Objects.toString(i.sheetName(), ""));
    }

    @PostMapping("/api/sheets/import-preview")
    public Object preview(
            @RequestParam long businessId,
            @RequestParam(defaultValue = "UTF-8") String encoding,
            @RequestParam(defaultValue = "") String sheetName,
            @RequestPart MultipartFile file) {
        return access.business(
                Subjects.current(),
                businessId,
                false,
                List.of("workspace.sheet.import", "workspace.sheet.update"),
                () -> {
                    try {
                        return SheetImport.read(
                                file.getBytes(),
                                Objects.toString(file.getOriginalFilename(), ""),
                                encoding,
                                sheetName);
                    } catch (IOException ex) {
                        throw Problem.invalid("파일을 읽지 못했습니다.");
                    }
                });
    }

    @PostMapping("/api/drafts/{id}/sheet/analysis")
    public Object analysis(@PathVariable String id, @RequestBody Map<String, Object> config) {
        return views.analyze(Subjects.current(), id, config);
    }

    @GetMapping("/api/drafts/{id}/sheet/views")
    public Object list(@PathVariable String id) {
        return views.list(Subjects.current(), id);
    }

    @PostMapping("/api/drafts/{id}/sheet/views")
    public Object save(@PathVariable String id, @RequestBody ViewInput input) {
        return views.save(
                Subjects.current(),
                id,
                input.id(),
                input.version(),
                input.sheetVersion(),
                input.config());
    }

    @DeleteMapping("/api/drafts/{id}/sheet/views/{viewId}")
    public Object delete(
            @PathVariable String id, @PathVariable String viewId, @RequestParam long version) {
        return views.delete(Subjects.current(), id, viewId, version);
    }
}

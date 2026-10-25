package com.axplatform.web;

import com.axplatform.operations.UsagePolicyService;
import com.axplatform.security.Subjects;
import com.axplatform.workspace.AssetService;

import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
public class StorageController {
    private final AssetService assets;
    private final UsagePolicyService limits;
    private final org.springframework.jdbc.core.JdbcTemplate db;

    public StorageController(
            AssetService assets,
            UsagePolicyService limits,
            org.springframework.jdbc.core.JdbcTemplate db) {
        this.assets = assets;
        this.limits = limits;
        this.db = db;
    }

    public record Purge(long version, boolean detach) {}

    public record Version(long version) {}

    @PostMapping(value = {"/api/my/files/{id}/replace", "/api/admin/files/{id}/replace"},
            consumes = org.springframework.http.MediaType.MULTIPART_FORM_DATA_VALUE)
    public Object replace(@PathVariable String id, @RequestParam long version,
            @RequestParam("file") org.springframework.web.multipart.MultipartFile file,
            jakarta.servlet.http.HttpServletRequest request) {
        return assets.replace(Subjects.current(), id, file, version,
                request.getRequestURI().startsWith("/api/admin/"));
    }

    @GetMapping({"/api/my/files/{id}/preview", "/api/admin/files/{id}/preview"})
    public org.springframework.http.ResponseEntity<byte[]> preview(@PathVariable String id,
            jakarta.servlet.http.HttpServletRequest request) {
        var preview = assets.preview(Subjects.current(), id, request.getRequestURI().startsWith("/api/admin/"));
        return org.springframework.http.ResponseEntity.ok()
                .contentType(org.springframework.http.MediaType.parseMediaType(preview.mediaType()))
                .header("Cache-Control", "no-store")
                .header("X-Content-Type-Options", "nosniff")
                .body(preview.bytes());
    }

    @GetMapping({"/api/my/files", "/api/admin/files"})
    public Object list(
            jakarta.servlet.http.HttpServletRequest request,
            @RequestParam(required = false) Long accountId,
            @RequestParam(required = false) Long businessId,
            @RequestParam(required = false) String workflow,
            @RequestParam(required = false) String scope,
            @RequestParam(required = false) String projectId,
            @RequestParam(defaultValue = "") String q,
            @RequestParam(defaultValue = "ACTIVE") String state,
            @RequestParam(defaultValue = "1") int page) {
        return assets.page(
                Subjects.current(),
                request.getRequestURI().startsWith("/api/admin/"),
                accountId,
                businessId,
                workflow,
                scope,
                projectId,
                q,
                state,
                page);
    }

    @GetMapping({"/api/my/files/{id}/impact", "/api/admin/files/{id}/impact"})
    public Object impact(@PathVariable String id, jakarta.servlet.http.HttpServletRequest request) {
        return assets.impact(
                Subjects.current(), id, request.getRequestURI().startsWith("/api/admin/"));
    }

    @PostMapping({"/api/my/files/{id}/purge", "/api/admin/files/{id}/purge"})
    public Object purge(
            @PathVariable String id,
            @RequestBody Purge input,
            jakarta.servlet.http.HttpServletRequest request) {
        return assets.purge(
                Subjects.current(),
                id,
                input.version(),
                input.detach(),
                request.getRequestURI().startsWith("/api/admin/"));
    }

    @PostMapping("/api/admin/files/{id}/archive")
    public Object archive(
            @PathVariable String id, @RequestBody WorkspaceApiController.Archive input) {
        assets.archive(Subjects.current(), id, input.archived(), input.version(), true);
        return Map.of("status", "saved");
    }

    @PostMapping("/api/admin/files/{id}/migrate")
    public Object migrate(@PathVariable String id, @RequestBody Version input) {
        return assets.migrate(Subjects.current(), id, input.version());
    }

    @GetMapping("/api/admin/files/retained-copies")
    public Object retained(@RequestParam long accountId) {
        return assets.retained(Subjects.current(), accountId);
    }

    @DeleteMapping("/api/admin/files/retained-copies/{id}")
    public Object cleanup(@PathVariable String id) {
        return assets.cleanupCopy(Subjects.current(), id);
    }

    @GetMapping("/api/admin/files/{id}/download")
    public org.springframework.http.ResponseEntity<
                    org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody>
            download(@PathVariable String id, jakarta.servlet.http.HttpServletRequest request) {
        var actor = Subjects.current();
        var session = request.getSession(false);
        String original = com.axplatform.security.SessionLease.captureId(session, actor);
        var m = assets.adminMetadata(actor, id);
        org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody stream =
                out -> assets.adminStream(actor, id, session, original, out);
        return org.springframework.http.ResponseEntity.ok()
                .contentType(
                        org.springframework.http.MediaType.parseMediaType(
                                (String) m.get("media_type")))
                .contentLength(com.axplatform.common.Inputs.number(m, "byte_size"))
                .header(
                        "Content-Disposition",
                        org.springframework.http.ContentDisposition.attachment()
                                .filename(
                                        (String) m.get("original_name"),
                                        java.nio.charset.StandardCharsets.UTF_8)
                                .build()
                                .toString())
                .header("Cache-Control", "no-store")
                .header("X-Content-Type-Options", "nosniff")
                .body(stream);
    }

    @GetMapping("/api/admin/files/roots")
    public Object roots() {
        return assets.roots(Subjects.current());
    }

    @GetMapping("/api/admin/storage-members")
    public Object members(@RequestParam(defaultValue = "") String q) {
        limits.admin(Subjects.current());
        String query = "%" + q.replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
        return db.queryForList(
                "SELECT a.id,a.display_name,a.account_type,a.status,COALESCE((SELECT SUM(byte_size)"
                    + " FROM ap_asset WHERE owner_account_id=a.id AND storage_state<>'PURGED'),0)"
                    + " AS used_bytes FROM ap_account a WHERE LOWER(display_name) LIKE LOWER(?)"
                    + " ESCAPE '!' OR CAST(a.id AS CHAR)=? ORDER BY a.id LIMIT 100",
                query,
                q);
    }

    @GetMapping("/api/admin/usage-policy/{id}")
    public Object quota(@PathVariable long id) {
        return limits.quota(Subjects.current(), id, true);
    }

    @PutMapping("/api/admin/usage-policy/{id}")
    public Object quota(@PathVariable long id, @RequestBody UsagePolicyService.Change change) {
        return limits.change(Subjects.current(), id, change);
    }
}

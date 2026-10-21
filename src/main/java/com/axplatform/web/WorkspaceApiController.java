package com.axplatform.web;

import com.axplatform.common.*;
import com.axplatform.security.*;
import com.axplatform.workspace.*;

import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.*;

import java.nio.charset.StandardCharsets;
import java.util.*;

@RestController
public class WorkspaceApiController {
    public record Dashboard(List<String> widgets, long version) {}

    public record Archive(boolean archived, long version) {}

    public record Clone(String title) {}

    private final SpaceService spaces;
    private final DraftService drafts;
    private final AssetService assets;
    private final EventService events;
    private final PreferenceService preferences;
    private final com.axplatform.policy.AccessService access;

    public WorkspaceApiController(
            SpaceService spaces,
            DraftService drafts,
            AssetService assets,
            EventService events,
            PreferenceService preferences,
            com.axplatform.policy.AccessService access) {
        this.spaces = spaces;
        this.drafts = drafts;
        this.assets = assets;
        this.events = events;
        this.preferences = preferences;
        this.access = access;
    }

    @GetMapping("/api/workspaces")
    public Object spaces() {
        return spaces.spaces(Subjects.current());
    }

    @GetMapping("/api/business-scopes")
    public Object scopes(@RequestParam long workspaceId) {
        return spaces.scopes(Subjects.current(), workspaceId);
    }

    @PostMapping("/api/business-scopes")
    public Object createScope(@RequestBody SpaceService.ScopeInput i) {
        return spaces.create(Subjects.current(), i);
    }

    @PatchMapping("/api/business-scopes/{id}")
    public Object changeScope(@PathVariable long id, @RequestBody SpaceService.ScopeInput i) {
        return spaces.change(Subjects.current(), id, i, false);
    }

    @PostMapping("/api/business-scopes/{id}/archive")
    public Object archiveScope(@PathVariable long id, @RequestBody SpaceService.ScopeInput i) {
        return spaces.change(Subjects.current(), id, i, true);
    }

    @GetMapping("/api/workspaces/{id}/access")
    public Object capabilities(@PathVariable long id) {
        SessionSubject s = Subjects.current();
        return access.workspace(
                s,
                id,
                List.of(),
                () ->
                        Map.of(
                                "capabilities",
                                access.allowed(s, id),
                                "tier",
                                s.administrator() ? "ADMIN" : access.tier(id)));
    }

    @GetMapping("/api/workspaces/{id}/dashboard")
    public Object dashboard(@PathVariable long id) {
        return spaces.dashboard(Subjects.current(), id);
    }

    @PatchMapping("/api/workspaces/{id}/dashboard")
    public Object dashboard(@PathVariable long id, @RequestBody Dashboard i) {
        return spaces.dashboardSave(Subjects.current(), id, i.widgets(), i.version());
    }

    @GetMapping("/api/drafts")
    public Object listDrafts(
            @RequestParam long businessId, @RequestParam(defaultValue = "false") boolean archived) {
        return drafts.list(Subjects.current(), businessId, archived);
    }

    @PostMapping("/api/drafts")
    public Object createDraft(@RequestBody DraftService.Create i) {
        return drafts.create(Subjects.current(), i);
    }

    @GetMapping("/api/drafts/{id}")
    public Object draft(@PathVariable String id) {
        return drafts.get(Subjects.current(), id);
    }

    @PatchMapping("/api/drafts/{id}")
    public Object edit(@PathVariable String id, @RequestBody DraftService.Edit i) {
        return drafts.edit(Subjects.current(), id, i);
    }

    @PostMapping("/api/drafts/{id}/{command:save|cancel|archive|restore}")
    public Object command(
            @PathVariable String id,
            @PathVariable String command,
            @RequestBody DraftService.Command i) {
        return drafts.command(Subjects.current(), id, command, i);
    }

    @PostMapping("/api/drafts/{id}/clone")
    public Object cloneDraft(@PathVariable String id, @RequestBody Clone i) {
        return drafts.cloneDraft(Subjects.current(), id, i.title());
    }

    public record SelectedAssets(long businessId, String projectId, List<String> ids) {}

    @PostMapping("/api/assets/selected")
    public Object selected(@RequestBody SelectedAssets i) {
        return assets.selected(Subjects.current(), i.businessId(), i.projectId(), i.ids());
    }

    @GetMapping("/api/assets/choices")
    public Object choices(
            @RequestParam long businessId,
            @RequestParam(required = false) String projectId,
            @RequestParam(required = false) String workflow,
            @RequestParam(defaultValue = "ALL") String kind,
            @RequestParam(defaultValue = "") String q,
            @RequestParam(defaultValue = "1") int page) {
        return assets.choices(Subjects.current(), businessId, projectId, workflow, kind, q, page);
    }

    @GetMapping("/api/assets")
    public Object listAssets(
            @RequestParam long businessId, @RequestParam(required = false) String projectId) {
        return assets.list(Subjects.current(), businessId, projectId);
    }

    @PostMapping(value = "/api/assets", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Object upload(
            @RequestParam long businessId,
            @RequestPart MultipartFile file,
            @RequestParam(defaultValue = "COMMON") String scope,
            @RequestParam(required = false) String workflow,
            @RequestParam(required = false) String projectId,
            @RequestParam(defaultValue = "SOURCE") String purpose,
            @RequestParam(required = false) String requestKey) {
        return assets.upload(
                Subjects.current(),
                businessId,
                file,
                scope,
                workflow,
                projectId,
                purpose,
                requestKey);
    }

    @GetMapping("/api/assets/{id}/download")
    public ResponseEntity<StreamingResponseBody> download(
            @PathVariable String id, jakarta.servlet.http.HttpServletRequest request) {
        SessionSubject s = Subjects.current();
        var lease = request.getSession(false);
        String originalSessionId = SessionLease.captureId(lease, s);
        var m = assets.metadata(s, id);
        StreamingResponseBody stream = out -> assets.stream(s, id, lease, originalSessionId, out);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType((String) m.get("media_type")))
                .contentLength(Inputs.number(m, "byte_size"))
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment()
                                .filename((String) m.get("original_name"), StandardCharsets.UTF_8)
                                .build()
                                .toString())
                .header("X-Content-Type-Options", "nosniff")
                .header("Cache-Control", "no-store")
                .body(stream);
    }

    @PostMapping("/api/assets/{id}/archive")
    public Map<String, String> archiveAsset(@PathVariable String id, @RequestBody Archive i) {
        assets.archive(Subjects.current(), id, i.archived(), i.version());
        return Map.of("status", "saved");
    }

    @GetMapping(value = "/api/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter events(
            @RequestParam long businessId,
            @RequestHeader(value = "Last-Event-ID", required = false) String last,
            jakarta.servlet.http.HttpServletRequest request) {
        return events.subscribe(Subjects.current(), businessId, last, request.getSession(false));
    }

    @PostMapping("/api/preferences/plan-popup/hide")
    public Map<String, String> hide() {
        preferences.hide(Subjects.current());
        return Map.of("status", "saved");
    }
}

package com.axplatform.web;

import com.axplatform.ai.AiService;
import com.axplatform.security.Subjects;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
public final class AiApiController {
    private final AiService ai;

    public AiApiController(AiService ai) {
        this.ai = ai;
    }

    @GetMapping("/api/ai/settings")
    public Object settings() {
        return ai.settings(Subjects.current());
    }

    @PostMapping("/api/admin/ai/diagnostics")
    public Object diagnose() {
        return ai.diagnose(Subjects.current());
    }

    @PostMapping("/api/drafts/{id}/ai-runs")
    public Object start(
            @PathVariable String id,
            @RequestBody AiService.Start command,
            @RequestHeader("Idempotency-Key") String key,
            HttpServletRequest request) {
        return ai.start(Subjects.current(), request.getSession(false), id, command, key);
    }

    @GetMapping("/api/drafts/{id}/ai-runs")
    public Object list(@PathVariable String id) {
        return ai.list(Subjects.current(), id);
    }

    @GetMapping("/api/ai-runs/{id}")
    public Object get(@PathVariable String id) {
        return ai.get(Subjects.current(), id);
    }

    @PostMapping("/api/ai-runs/{id}/cancel")
    public Object cancel(
            @PathVariable String id,
            @RequestBody Map<String, Object> command,
            HttpServletRequest request) {
        return ai.cancel(Subjects.current(), request.getSession(false), id);
    }

    @PostMapping("/api/ai-runs/{id}/apply-sheets")
    public Object applySheets(@PathVariable String id,@RequestBody AiService.BatchApply command,
            @RequestHeader("Idempotency-Key") String key,HttpServletRequest request) {
        return ai.applyBatch(Subjects.current(),request.getSession(false),id,command,key);
    }

    @PostMapping("/api/ai-runs/{id}/apply")
    public Object apply(
            @PathVariable String id,
            @RequestBody AiService.Apply command,
            @RequestHeader("Idempotency-Key") String key,
            HttpServletRequest request) {
        return ai.apply(Subjects.current(), request.getSession(false), id, command, key);
    }
}

package com.axplatform.web;

import com.axplatform.operations.*;
import com.axplatform.security.Subjects;

import org.springframework.web.bind.annotation.*;

@RestController
public class OperationsController {
    private final OperationsService operations;
    private final AiMeteringService meter;

    public OperationsController(OperationsService operations, AiMeteringService meter) {
        this.operations = operations;
        this.meter = meter;
    }

    @GetMapping({"/api/admin/operations", "/api/my/usage"})
    public Object report(
            @RequestParam String from,
            @RequestParam String to,
            @RequestParam(required = false) Long accountId,
            @RequestParam(required = false) String workflow,
            jakarta.servlet.http.HttpServletRequest r) {
        return operations.report(
                Subjects.current(),
                r.getRequestURI().startsWith("/api/admin/"),
                from,
                to,
                accountId,
                workflow);
    }

    @PutMapping("/api/admin/operations/global")
    public Object global(@RequestBody OperationsService.Global i) {
        return operations.global(Subjects.current(), i);
    }

    @PostMapping("/api/admin/operations/prices")
    public Object price(@RequestBody AiMeteringService.Price i) {
        return meter.price(Subjects.current(), i);
    }

    @PostMapping("/api/admin/operations/calls/{id}/reconcile")
    public Object reconcile(@PathVariable String id, @RequestBody AiMeteringService.Reconcile i) {
        return meter.reconcile(Subjects.current(), id, i);
    }
}

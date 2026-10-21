package com.axplatform.web;

import java.util.*;
import com.axplatform.identity.*;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/admin") public class MemberApiController {
    private final MemberAdminService service;
    public MemberApiController(MemberAdminService service) {
        this.service=service;
    }
    @GetMapping("/members/{id}") public Object detail(@PathVariable long id) {
        return service.detail(id);
    }
    @PatchMapping("/members/{id}") public Map<String,String> change(@PathVariable long id,@RequestBody MemberAdminService.Change i) {
        service.change(id,i);
        return Map.of("status","saved");
    }
    @PostMapping("/workspaces/{id}/tier") public Map<String,String> tier(@PathVariable long id,@RequestBody MemberAdminService.TierChange i) {
        service.tier(id,i);
        return Map.of("status","saved");
    }
    @GetMapping("/workspaces/{id}/tier/history") public Object history(@PathVariable long id) {
        return service.tierHistory(id);
    }
}

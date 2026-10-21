package com.axplatform.web;

import java.util.*;
import com.axplatform.policy.*;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/admin/permissions") public class PolicyApiController {
    private final PolicyService policies;
    private final CatalogService catalog;
    public PolicyApiController(PolicyService policies,CatalogService catalog) {
        this.policies=policies;
        this.catalog=catalog;
    }
    @GetMapping public Map<String,Object> snapshot() {
        return policies.snapshot();
    }
    @PostMapping("/preview") public Map<String,Object> preview(@RequestBody PolicyService.Draft draft) {
        return policies.preview(draft);
    }
    @PostMapping("/apply") public Map<String,Object> apply(@RequestBody PolicyService.Draft draft,@RequestHeader("Idempotency-Key") String key) {
        if(!key.equals(draft.requestKey()))throw com.axplatform.common.Problem.invalid("요청 식별 헤더와 내용이 다릅니다.");
        return policies.apply(draft);
    }
    @GetMapping("/history") public Object history() {
        return policies.history();
    }
    @GetMapping("/history/{id}") public Object historical(@PathVariable long id) {
        return policies.historical(id);
    }
    @PostMapping("/groups") public Map<String,String> addGroup(@RequestBody CatalogService.GroupInput i) {
        catalog.group(i,true);
        return Map.of("status","saved");
    }
    @PutMapping("/groups/{code}") public Map<String,String> group(@PathVariable String code,@RequestBody CatalogService.GroupInput i) {
        if(!code.equals(i.code()))throw com.axplatform.common.Problem.invalid("코드를 변경할 수 없습니다.");
        catalog.group(i,false);
        return Map.of("status","saved");
    }
    @DeleteMapping("/groups/{code}") public Map<String,String> deleteGroup(@PathVariable String code,@RequestParam long version,@RequestParam String reason) {
        catalog.removeGroup(code,version,reason);
        return Map.of("status","removed");
    }
    @PostMapping("/actions") public Map<String,String> addAction(@RequestBody CatalogService.ActionInput i) {
        catalog.action(i,true);
        return Map.of("status","saved");
    }
    @PutMapping("/actions/{code}") public Map<String,String> action(@PathVariable String code,@RequestBody CatalogService.ActionInput i) {
        if(!code.equals(i.code()))throw com.axplatform.common.Problem.invalid("코드를 변경할 수 없습니다.");
        catalog.action(i,false);
        return Map.of("status","saved");
    }
    @GetMapping("/actions/{code}/impact") public Object impact(@PathVariable String code) {
        return catalog.impact(code);
    }
    @DeleteMapping("/actions/{code}") public Map<String,String> delete(@PathVariable String code,@RequestParam long version,@RequestParam String reason) {
        catalog.removeAction(code,version,reason);
        return Map.of("status","removed");
    }
}

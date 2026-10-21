package com.axplatform.web;

import java.util.*;
import com.axplatform.workspace.*;
import com.axplatform.security.*;
import com.axplatform.common.*;
import com.axplatform.policy.AccessService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
@Controller public class WorkspacePageController {
    private final SpaceService spaces;
    private final DraftService drafts;
    private final AccessService access;
    private final PreferenceService prefs;
    public WorkspacePageController(SpaceService spaces,DraftService drafts,AccessService access,PreferenceService prefs) {
        this.spaces=spaces;
        this.drafts=drafts;
        this.access=access;
        this.prefs=prefs;
    }
    @GetMapping("/dashboard") public String dashboard(HttpServletRequest request,Model model) {
        var actor=Subjects.current();
        var owned=spaces.spaces(actor);
        if(owned.isEmpty())throw new Problem(404,"WORKSPACE_NOT_FOUND","소유한 업무 공간이 없습니다.");
        model.addAttribute("space",owned.get(0));
        model.addAttribute("showPlan",prefs.showPlan(actor,Inputs.number(owned.get(0),"id"),request.getSession()));
        model.addAttribute("admin",actor.administrator());
        return "workspace/dashboard";
    }
    @GetMapping("/work/new") public String create(@RequestParam long businessId,@RequestParam String workflow,Model model) {
        Inputs.state(workflow,DraftService.WORKFLOWS);
        var actor=Subjects.current();
        var scope=access.business(actor,businessId,false,List.of("workspace.draft.create"),()->access.scope(actor,businessId,false));
        model.addAttribute("businessId",businessId);
        model.addAttribute("workflow",workflow);
        model.addAttribute("draftId","");
        model.addAttribute("workspaceId",scope.get("workspace_id"));
        model.addAttribute("admin",actor.administrator());
        return "workspace/draft";
    }
    @GetMapping("/work/drafts/{id}") public String edit(@PathVariable String id,Model model) {
        var actor=Subjects.current();
        var draft=drafts.get(actor,id);
        model.addAttribute("businessId",draft.get("business_scope_id"));
        model.addAttribute("workspaceId",draft.get("workspace_id"));
        model.addAttribute("workflow",draft.get("workflow"));
        model.addAttribute("draftId",id);
        model.addAttribute("admin",actor.administrator());
        return "workspace/draft";
    }
}

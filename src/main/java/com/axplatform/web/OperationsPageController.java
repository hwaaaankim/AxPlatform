package com.axplatform.web;

import com.axplatform.security.Subjects;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class OperationsPageController {
    @GetMapping("/my/files")
    public String files(Model m) {
        m.addAttribute("admin", Subjects.current().administrator());
        m.addAttribute("manager", false);
        return "workspace/files";
    }

    @GetMapping("/admin/files")
    public String adminFiles(Model m) {
        m.addAttribute("manager", true);
        m.addAttribute("admin", true);
        return "workspace/files";
    }

    @GetMapping("/admin/operations")
    public String operations() {
        return "admin/operations";
    }
}

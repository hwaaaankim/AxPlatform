package com.axplatform.web;

import com.axplatform.policy.AccessService;
import com.axplatform.security.Subjects;
import com.axplatform.sheet.*;
import java.util.*;
import org.springframework.web.bind.annotation.*;

@RestController
public class SheetMaintenanceController {
    private final AccessService access;
    public SheetMaintenanceController(AccessService access) { this.access = access; }
    public record RowsInput(Map<String,Object> workbook, String sheetId, List<List<String>> rows,
            List<String> mapping, String mode, String formulaPolicy, boolean extendFormulas) { }

    @PostMapping("/api/sheets/workbook-rows")
    public Object rows(@RequestParam long businessId, @RequestBody RowsInput input) {
        var subject = Subjects.current();
        return access.business(subject, businessId, false,
            List.of("workspace.sheet.read", "workspace.sheet.update", "workspace.sheet.rows.create", "workspace.sheet.cells.update"), () -> {
                var before = WorkbookRules.normalize(input.workbook());
                var result = WorkbookMaintenance.rows(before, input.sheetId(), input.rows(), input.mapping(), input.mode(), input.formulaPolicy());
                if (input.extendFormulas() && "keep".equals(input.formulaPolicy())) {
                    int start = "append".equals(input.mode()) ? ((List<?>) WorkbookRules.data(before, input.sheetId()).get("rows")).size() : 0;
                    result = WorkbookMaintenance.extend(before, result, input.sheetId(), start);
                } else result = WorkbookCalculation.calculate(result);
                for (String action : WorkbookRules.requiredActions(before, result))
                    access.business(subject, businessId, false, List.of(action), () -> null);
                return result;
            });
    }
}

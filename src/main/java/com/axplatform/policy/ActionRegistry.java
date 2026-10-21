package com.axplatform.policy;

import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

/** Closed server-action allowlist. A catalog row never creates executable code. */
@Component
public class ActionRegistry {
    private static final Map<String, Set<String>> REQUIRED_DEPENDENCIES = Map.ofEntries(
        Map.entry("workspace.sheet.import", Set.of("workspace.sheet.update")),
        Map.entry("workspace.sheet.formula.update", Set.of("workspace.sheet.schema.update", "workspace.sheet.cells.update")),
        Map.entry("workspace.sheet.analysis.read", Set.of("workspace.sheet.read")),
        Map.entry("workspace.sheet.analysis.manage", Set.of("workspace.sheet.analysis.read")),
        Map.entry("workspace.ai.run", Set.of("workspace.ai.read", "workspace.draft.read")),
        Map.entry("workspace.ai.read", Set.of("workspace.draft.read")),
        Map.entry("workspace.ai.apply", Set.of("workspace.ai.read", "workspace.draft.update")),
        Map.entry("workspace.ai.cancel", Set.of("workspace.ai.read")),
        Map.entry("workspace.sheet.read", Set.of("workspace.draft.read")),
        Map.entry("workspace.sheet.update", Set.of("workspace.sheet.read", "workspace.draft.update")),
        Map.entry("workspace.sheet.export", Set.of("workspace.sheet.read")),
        Map.entry("workspace.sheet.schema.update", Set.of("workspace.sheet.update")),
        Map.entry("workspace.sheet.rows.create", Set.of("workspace.sheet.update")),
        Map.entry("workspace.sheet.rows.delete", Set.of("workspace.sheet.update")),
        Map.entry("workspace.sheet.cells.update", Set.of("workspace.sheet.update")),
        Map.entry("workspace.sheet.export.template", Set.of("workspace.sheet.read")));

    private final Set<String> bindings = Set.of(
        "workspace.sheet.import",
        "workspace.sheet.formula.update",
        "workspace.sheet.analysis.read",
        "workspace.sheet.analysis.manage",

        "workspace.scope.read",
        "workspace.scope.create",
        "workspace.scope.update",
        "workspace.scope.archive",
        "workspace.dashboard.read",
        "workspace.dashboard.manage",
        "workspace.events.read",
        "workspace.draft.list",
        "workspace.draft.read",
        "workspace.draft.create",
        "workspace.draft.update",
        "workspace.draft.save",
        "workspace.draft.archive",
        "workspace.draft.restore",
        "workspace.asset.list",
        "workspace.asset.upload",
        "workspace.asset.download",
        "workspace.asset.archive",
        "workspace.asset.restore",
        "workspace.draft.cancel",
        "workspace.draft.clone",
        "workspace.ai.run",
        "workspace.ai.read",
        "workspace.ai.apply",
        "workspace.ai.cancel",
        "workspace.sheet.read",
        "workspace.sheet.update",
        "workspace.sheet.export",
        "workspace.sheet.schema.update",
        "workspace.sheet.rows.create",
        "workspace.sheet.rows.delete",
        "workspace.sheet.cells.update",
        "workspace.sheet.export.template");

    public boolean implemented(String code, String binding) {
        return code != null && code.equals(binding) && bindings.contains(code);
    }

    /** Fixed safety edges for implemented actions; administrator additions are allowed. */
    public Map<String, Set<String>> requiredDependencies() {
        return REQUIRED_DEPENDENCIES;
    }

    public Set<String> requiredDependencies(String code) {
        return REQUIRED_DEPENDENCIES.getOrDefault(code, Set.of());
    }

    public Set<String> bindings() {
        return bindings;
    }
}

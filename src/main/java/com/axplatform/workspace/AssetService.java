package com.axplatform.workspace;

import com.axplatform.common.*;
import com.axplatform.operations.UsagePolicyService;
import com.axplatform.policy.*;
import com.axplatform.security.*;
import com.axplatform.storage.ManagedStorage;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.nio.file.*;
import java.time.*;
import java.util.*;

@Service
public class AssetService {
    private final PolicyService policies;
    private final JdbcTemplate db;
    private final AccessService access;
    private final Clock clock;
    private final ManagedStorage storage;
    private final UsagePolicyService limits;
    private final TransactionTemplate tx;
    private final JsonCodec json;
    private static final String PUBLIC =
            "a.id,a.business_scope_id,a.original_name,a.media_type,a.byte_size,a.version,a.archived_at,a.created_at,a.scope_kind,a.workflow,a.project_id,a.purpose,a.storage_state,d.title"
                + " AS project_title,b.name AS business_name";

    public AssetService(
            JdbcTemplate db,
            AccessService access,
            Clock clock,
            ManagedStorage storage,
            UsagePolicyService limits,
            TransactionTemplate tx,
            JsonCodec json,
            PolicyService policies) {
        this.policies = policies;
        this.db = db;
        this.access = access;
        this.clock = clock;
        this.storage = storage;
        this.limits = limits;
        this.tx = tx;
        this.json = json;
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock);
    }

    private Map<String, Object> row(String id) {
        var rows = db.queryForList("SELECT * FROM ap_asset WHERE id=?", id);
        if (rows.isEmpty()) throw new Problem(404, "ASSET_NOT_FOUND", "자료를 찾지 못했습니다.");
        return rows.get(0);
    }

    private void owner(SessionSubject s, Map<String, Object> r) {
        limits.current(s);
        if (Inputs.number(r, "owner_account_id") != s.accountId())
            throw new Problem(404, "ASSET_NOT_FOUND", "자료를 찾지 못했습니다.");
    }

    public List<Map<String, Object>> list(SessionSubject s, long business) {
        return list(s, business, null);
    }

    public List<Map<String, Object>> list(SessionSubject s, long business, String project) {
        return access.business(
                s,
                business,
                true,
                List.of("workspace.asset.list"),
                () -> {
                    String where = "";
                    List<Object> args = new ArrayList<>(List.of(business, s.accountId()));
                    if (project != null && !project.isBlank()) {
                        var d = project(s, business, project);
                        where =
                                " AND (a.scope_kind='COMMON' OR (a.scope_kind='WORKFLOW' AND"
                                    + " a.workflow=?) OR (a.scope_kind='PROJECT' AND"
                                    + " a.project_id=?))";
                        args.add(d.get("workflow"));
                        args.add(project);
                    }
                    return db.queryForList(
                            "SELECT "
                                    + PUBLIC
                                    + " FROM ap_asset a LEFT JOIN ap_work_draft d ON"
                                    + " d.id=a.project_id JOIN ap_business_scope b ON"
                                    + " b.id=a.business_scope_id WHERE a.business_scope_id=? AND"
                                    + " a.owner_account_id=? AND a.storage_state='READY'"
                                    + where
                                    + " ORDER BY a.created_at DESC,a.id LIMIT 200",
                            args.toArray());
                });
    }

    public List<Map<String, Object>> selected(
            SessionSubject s, long business, String project, List<String> ids) {
        if (ids == null || ids.size() > 20 || new HashSet<>(ids).size() != ids.size())
            throw Problem.invalid("작업 자료는 최대 20개입니다.");
        return access.business(
                s,
                business,
                true,
                List.of("workspace.asset.list"),
                () -> {
                    if (ids.isEmpty()) return List.of();
                    List<Object> args = new ArrayList<>(List.of(business, s.accountId()));
                    args.addAll(ids);
                    String where =
                            "a.business_scope_id=? AND a.owner_account_id=? AND"
                                + " a.storage_state='READY' AND a.id IN ("
                                    + String.join(",", Collections.nCopies(ids.size(), "?"))
                                    + ")";
                    if (project != null && !project.isBlank()) {
                        var d = project(s, business, project);
                        where +=
                                " AND (a.scope_kind='COMMON' OR (a.scope_kind='WORKFLOW' AND"
                                    + " a.workflow=?) OR (a.scope_kind='PROJECT' AND"
                                    + " a.project_id=?))";
                        args.add(d.get("workflow"));
                        args.add(project);
                    }
                    return db.queryForList(
                            "SELECT "
                                    + PUBLIC
                                    + " FROM ap_asset a LEFT JOIN ap_work_draft d ON"
                                    + " d.id=a.project_id JOIN ap_business_scope b ON"
                                    + " b.id=a.business_scope_id WHERE "
                                    + where
                                    + " ORDER BY a.created_at DESC,a.id",
                            args.toArray());
                });
    }

    public Map<String, Object> choices(
            SessionSubject s,
            long business,
            String project,
            String workflow,
            String kind,
            String q,
            int page) {
        return access.business(
                s,
                business,
                false,
                List.of("workspace.asset.list"),
                () -> {
                    String wf = workflow;
                    if (project != null && !project.isBlank())
                        wf = (String) project(s, business, project).get("workflow");
                    if (!ManagedStorage.WORKFLOWS.contains(Objects.toString(wf, "")))
                        throw Problem.invalid("자료를 사용할 업무를 선택하세요.");
                    List<Object> args =
                            new ArrayList<>(Arrays.asList(business, s.accountId(), wf, project));
                    String where =
                            "a.business_scope_id=? AND a.owner_account_id=? AND"
                                + " a.storage_state='READY' AND a.archived_at IS NULL AND"
                                + " (a.scope_kind='COMMON' OR (a.scope_kind='WORKFLOW' AND"
                                + " a.workflow=?) OR (a.scope_kind='PROJECT' AND a.project_id=?))";
                    if ("AI".equals(kind))
                        where += " AND a.media_type IN ('image/png','image/jpeg','text/plain')";
                    else if ("SHEET".equals(kind))
                        where +=
                                " AND a.media_type IN"
                                    + " ('text/csv','application/vnd.openxmlformats-officedocument.spreadsheetml.sheet')";
                    else if (!"ALL".equals(kind)) throw Problem.invalid("자료 종류를 확인하세요.");
                    if (q != null && !q.isBlank()) {
                        if (q.length() > 200) throw Problem.invalid("검색어는 200자 이하입니다.");
                        where += " AND LOWER(a.original_name) LIKE ? ESCAPE '!'";
                        args.add(
                                "%"
                                        + q.toLowerCase(Locale.ROOT)
                                                .replace("!", "!!")
                                                .replace("%", "!%")
                                                .replace("_", "!_")
                                        + "%");
                    }
                    long total =
                            db.queryForObject(
                                    "SELECT COUNT(*) FROM ap_asset a WHERE " + where,
                                    Long.class,
                                    args.toArray());
                    long pages = Math.max(1, (total + 24) / 25);
                    int p = (int) Math.max(1, Math.min(page, pages));
                    args.add((p - 1) * 25);
                    var items =
                            db.queryForList(
                                    "SELECT "
                                            + PUBLIC
                                            + " FROM ap_asset a LEFT JOIN ap_work_draft d ON"
                                            + " d.id=a.project_id JOIN ap_business_scope b ON"
                                            + " b.id=a.business_scope_id WHERE "
                                            + where
                                            + " ORDER BY a.created_at DESC,a.id LIMIT 25 OFFSET ?",
                                    args.toArray());
                    return Map.of("items", items, "total", total, "page", p, "pages", pages);
                });
    }

    private Map<String, Object> project(SessionSubject s, long business, String id) {
        var rows =
                db.queryForList(
                        "SELECT d.* FROM ap_work_draft d JOIN ap_workspace w ON w.id=d.workspace_id"
                            + " WHERE d.id=? AND d.business_scope_id=? AND w.owner_account_id=?",
                        id,
                        business,
                        s.accountId());
        if (rows.isEmpty()) throw new Problem(404, "PROJECT_NOT_FOUND", "같은 사업의 내 작업을 선택하세요.");
        return rows.get(0);
    }

    public Map<String, Object> upload(SessionSubject s, long business, MultipartFile f) {
        return upload(s, business, f, "COMMON", null, null, "SOURCE", null);
    }

    public Map<String, Object> upload(
            SessionSubject s,
            long business,
            MultipartFile f,
            String scope,
            String workflow,
            String project,
            String purpose,
            String requestKey) {
        access.business(s, business, false, List.of("workspace.asset.upload"), () -> null);
        try {
            if (f.getSize() > 10 * 1024 * 1024) throw new IOException("파일당 10MB 이하입니다.");
            return store(
                    s,
                    business,
                    Objects.toString(f.getOriginalFilename(), "file"),
                    f.getBytes(),
                    scope,
                    workflow,
                    project,
                    purpose,
                    requestKey);
        } catch (IOException e) {
            throw new Problem(422, "ASSET_FORMAT", e.getMessage());
        }
    }

    public Map<String, Object> store(
            SessionSubject s,
            long business,
            String name,
            byte[] bytes,
            String scope,
            String workflow,
            String project,
            String purpose) {
        return store(s, business, name, bytes, scope, workflow, project, purpose, null);
    }

    public Map<String, Object> store(
            SessionSubject s,
            long business,
            String name,
            byte[] bytes,
            String scope,
            String workflow,
            String project,
            String purpose,
            String requestKey) {
        if (requestKey != null && !requestKey.matches("[a-f0-9-]{36}"))
            throw Problem.invalid("등록 요청 식별자를 확인하세요.");
        final String filename = safeName(name);
        final AssetValidation.Prepared file;
        try {
            file = AssetValidation.validate(filename, bytes);
        } catch (IOException e) {
            throw new Problem(422, "ASSET_FORMAT", e.getMessage());
        }
        if (!Set.of("COMMON", "WORKFLOW", "PROJECT").contains(Objects.toString(scope, ""))
                || !ManagedStorage.PURPOSES.contains(Objects.toString(purpose, "")))
            throw Problem.invalid("자료 범위·용도를 선택하세요.");
        if ("PHOTO".equals(purpose) && !Set.of("png", "jpg").contains(file.extension()))
            throw Problem.invalid("사진 원본에는 PNG·JPG 이미지를 선택하세요.");
        if ("IMPORT".equals(purpose) && !Set.of("xlsx", "csv").contains(file.extension()))
            throw Problem.invalid("표 가져오기 원본에는 XLSX·CSV를 선택하세요.");
        String id = UUID.randomUUID().toString();
        final String[] written = {null};
        try {
            return access.business(
                    s,
                    business,
                    false,
                    List.of("workspace.asset.upload"),
                    () -> {
                        limits.lock(s.accountId());
                        var b = access.scope(s, business, false);
                        String wf = "COMMON".equals(scope) ? null : workflow,
                                pr = "PROJECT".equals(scope) ? project : null;
                        if (pr != null) {
                            var d = project(s, business, pr);
                            if ("ARCHIVED".equals(d.get("status")))
                                throw Problem.invalid("보관 작업에는 자료를 추가할 수 없습니다.");
                            wf = (String) d.get("workflow");
                        }
                        if (requestKey != null) {
                            var prior =
                                    db.queryForList(
                                            "SELECT * FROM ap_asset WHERE owner_account_id=? AND"
                                                + " upload_key=?",
                                            s.accountId(),
                                            requestKey);
                            if (!prior.isEmpty()) {
                                var previous = prior.get(0);
                                if (!Objects.equals(
                                                previous.get("sha256"),
                                                ManagedStorage.sha(file.data()))
                                        || !Objects.equals(previous.get("scope_kind"), scope)
                                        || !Objects.equals(previous.get("workflow"), wf)
                                        || !Objects.equals(previous.get("project_id"), pr)
                                        || Inputs.number(previous, "business_scope_id") != business
                                        || !Objects.equals(previous.get("purpose"), purpose)
                                        || !Objects.equals(previous.get("original_name"), filename)
                                        || !"READY".equals(previous.get("storage_state")))
                                    throw Problem.conflict(
                                            "이 등록 요청은 다른 파일·범위 또는 삭제한 자료에 사용되었습니다. 파일을 다시 선택하세요.");
                                return Map.of(
                                        "id",
                                        previous.get("id"),
                                        "name",
                                        filename,
                                        "bytes",
                                        file.data().length,
                                        "scope",
                                        scope,
                                        "replayed",
                                        true);
                            }
                        }
                        limits.admitFile(s.accountId(), file.data().length);
                        String key =
                                storage.key(
                                        s.accountId(),
                                        business,
                                        scope,
                                        wf,
                                        pr,
                                        purpose,
                                        id,
                                        file.extension());
                        try {
                            storage.memberFolders(s.accountId());
                            storage.write(key, file.data());
                            written[0] = key;
                        } catch (IOException e) {
                            throw new Problem(
                                    503, "STORAGE_UNAVAILABLE", "자료 폴더에 저장하지 못했습니다. 다시 시도해 주세요.");
                        }
                        db.update(
                                "INSERT INTO"
                                    + " ap_asset(id,workspace_id,business_scope_id,creator_account_id,owner_account_id,original_name,disk_key,media_type,byte_size,sha256,created_at,scope_kind,workflow,project_id,purpose,upload_key,root_kind,storage_state)"
                                    + " VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,'MANAGED','READY')",
                                id,
                                b.get("workspace_id"),
                                business,
                                s.accountId(),
                                s.accountId(),
                                filename,
                                key,
                                file.mediaType(),
                                file.data().length,
                                ManagedStorage.sha(file.data()),
                                now(),
                                scope,
                                wf,
                                pr,
                                purpose,
                                requestKey);
                        access.event(
                                Inputs.number(b, "workspace_id"),
                                business,
                                "asset.uploaded",
                                id,
                                0);
                        return Map.of(
                                "id",
                                id,
                                "name",
                                filename,
                                "bytes",
                                file.data().length,
                                "scope",
                                scope);
                    });
        } catch (RuntimeException e) {
            if (written[0] != null)
                try {
                    if (db.queryForObject(
                                    "SELECT COUNT(*) FROM ap_asset WHERE id=?", Integer.class, id)
                            == 0) Files.deleteIfExists(storage.resolve("MANAGED", written[0]));
                } catch (Exception ignored) {
                }
            throw e;
        }
    }

    private static String safeName(String name) {
        String n = Objects.toString(name, "file").replaceAll("[\\\\/\\p{Cntrl}]", "_");
        return n.length() > 200 ? n.substring(n.length() - 200) : n;
    }

    private Path path(Map<String, Object> r) {
        return storage.resolve((String) r.get("root_kind"), (String) r.get("disk_key"));
    }

    public Map<String, Object> metadata(SessionSubject s, String id) {
        var r = row(id);
        owner(s, r);
        return access.business(
                s,
                Inputs.number(r, "business_scope_id"),
                true,
                List.of("workspace.asset.download"),
                () -> {
                    var current = row(id);
                    if (current.get("archived_at") != null
                            || !"READY".equals(current.get("storage_state")))
                        throw new Problem(404, "ASSET_ARCHIVED", "휴지통 또는 삭제 처리 중인 자료입니다.");
                    if (!Files.isRegularFile(path(current), LinkOption.NOFOLLOW_LINKS))
                        throw new Problem(503, "STORAGE_MISSING", "원본 파일을 찾지 못했습니다. 관리자에게 문의하세요.");
                    return current;
                });
    }

    public byte[] read(SessionSubject s, String id) {
        var r = metadata(s, id);
        try {
            byte[] b =
                    storage.read(
                            (String) r.get("root_kind"),
                            (String) r.get("disk_key"),
                            Inputs.number(r, "byte_size"),
                            (String) r.get("sha256"));
            var after = metadata(s, id);
            if (Inputs.number(after, "version") != Inputs.number(r, "version"))
                throw Problem.conflict("자료 상태가 변경되었습니다.");
            return b;
        } catch (IOException e) {
            throw new Problem(503, "STORAGE_INTEGRITY", "자료 원본의 크기·무결성을 확인하지 못했습니다.");
        }
    }

    public void stream(
            SessionSubject s, String id, jakarta.servlet.http.HttpSession lease, OutputStream out)
            throws IOException {
        stream(s, id, lease, SessionLease.captureId(lease, s), out);
    }

    public void stream(
            SessionSubject s,
            String id,
            jakarta.servlet.http.HttpSession lease,
            String original,
            OutputStream out)
            throws IOException {
        try {
            SessionLease.require(lease, s, original);
            try (var in = Files.newInputStream(path(metadata(s, id)), LinkOption.NOFOLLOW_LINKS)) {
                byte[] b = new byte[65536];
                int n;
                while ((n = in.read(b)) != -1) {
                    SessionLease.require(lease, s, original);
                    metadata(s, id);
                    out.write(b, 0, n);
                    out.flush();
                }
            }
        } catch (RuntimeException e) {
            try {
                out.close();
            } catch (IOException ignored) {
            }
            throw new IOException("다운로드 권한 확인을 계속할 수 없습니다.");
        }
    }

    public Map<String, Object> adminMetadata(SessionSubject s, String id) {
        limits.admin(s);
        var r = row(id);
        if (!"READY".equals(r.get("storage_state")))
            throw new Problem(404, "ASSET_NOT_FOUND", "다운로드 가능한 원본이 없습니다.");
        if (!Files.isRegularFile(path(r), LinkOption.NOFOLLOW_LINKS))
            throw new Problem(503, "STORAGE_MISSING", "원본 파일을 찾지 못했습니다.");
        return r;
    }

    public void adminStream(
            SessionSubject s,
            String id,
            jakarta.servlet.http.HttpSession lease,
            String sessionId,
            OutputStream out)
            throws IOException {
        try {
            SessionLease.require(lease, s, sessionId);
            try (var in =
                    Files.newInputStream(path(adminMetadata(s, id)), LinkOption.NOFOLLOW_LINKS)) {
                byte[] bytes = new byte[65536];
                int n;
                while ((n = in.read(bytes)) != -1) {
                    SessionLease.require(lease, s, sessionId);
                    adminMetadata(s, id);
                    out.write(bytes, 0, n);
                    out.flush();
                }
            }
        } catch (RuntimeException e) {
            try {
                out.close();
            } catch (IOException ignored) {
            }
            throw new IOException("관리자 다운로드 권한 확인을 계속할 수 없습니다.");
        }
    }

    public record AiSource(String name, String mediaType, byte[] data) {}

    public AiSource readForAi(SessionSubject s, String id, long business) {
        var r = metadata(s, id);
        if (Inputs.number(r, "business_scope_id") != business)
            throw new Problem(404, "ASSET_SCOPE", "같은 사업의 자료만 선택할 수 있습니다.");
        if (!Set.of("image/png", "image/jpeg", "text/plain").contains(r.get("media_type"))
                || Inputs.number(r, "byte_size") > 6 * 1024 * 1024)
            throw new Problem(422, "AI_ASSET_LIMIT", "AI에는 PNG·JPG·TXT 자료 합계 6MB 이하를 선택하세요.");
        return new AiSource(
                (String) r.get("original_name"), (String) r.get("media_type"), read(s, id));
    }

    public void archive(SessionSubject s, String id, boolean archived, long version) {
        archive(s, id, archived, version, false);
    }

    public void archive(
            SessionSubject s, String id, boolean archived, long version, boolean admin) {
        var r = row(id);
        if (admin) limits.admin(s);
        else {
            owner(s, r);
            access.business(
                    s,
                    Inputs.number(r, "business_scope_id"),
                    true,
                    List.of(archived ? "workspace.asset.archive" : "workspace.asset.restore"),
                    () -> null);
        }
        tx.executeWithoutResult(
                t -> {
                    policies.state(true);
                    if (admin) limits.admin(s);
                    else {
                        owner(s, r);
                        access.business(
                                s,
                                Inputs.number(r, "business_scope_id"),
                                true,
                                List.of(
                                        archived
                                                ? "workspace.asset.archive"
                                                : "workspace.asset.restore"),
                                () -> null);
                    }
                    limits.lock(Inputs.number(r, "owner_account_id"));
                    var current = row(id);
                    version(current, version);
                    if (!"READY".equals(current.get("storage_state")))
                        throw Problem.conflict("삭제 처리 상태를 확인하세요.");
                    db.update(
                            "UPDATE ap_asset SET archived_at=?,version=version+1 WHERE id=?",
                            archived ? now() : null,
                            id);
                    if (admin)
                        limits.audit(
                                s,
                                archived ? "ASSET_TRASH" : "ASSET_RESTORE",
                                id,
                                "관리자 자료 관리",
                                Map.of("version", version),
                                Map.of("archived", archived));
                    access.event(
                            Inputs.number(r, "workspace_id"),
                            Inputs.number(r, "business_scope_id"),
                            archived ? "asset.archived" : "asset.restored",
                            id,
                            version + 1);
                });
    }

    private static void version(Map<String, Object> r, long v) {
        if (Inputs.number(r, "version") != v) throw Problem.conflict("자료가 변경되었습니다. 목록을 새로 불러오세요.");
    }

    public Map<String, Object> page(
            SessionSubject s,
            boolean admin,
            Long account,
            Long business,
            String workflow,
            String scope,
            String project,
            String q,
            String state,
            int page) {
        if (admin) limits.admin(s);
        else limits.current(s);
        long owner = admin && account != null ? account : s.accountId();
        if (db.queryForObject("SELECT COUNT(*) FROM ap_account WHERE id=?", Integer.class, owner)
                != 1) throw new Problem(404, "MEMBER_NOT_FOUND", "회원을 찾지 못했습니다.");
        if (!admin)
            for (Long w :
                    db.queryForList(
                            "SELECT id FROM ap_workspace WHERE owner_account_id=? AND kind=?",
                            Long.class,
                            owner,
                            s.administrator() ? "ADMIN_TEST" : "CUSTOMER"))
                access.workspace(s, w, List.of("workspace.asset.list"), () -> null);
        try {
            storage.memberFolders(owner);
        } catch (IOException e) {
            throw new Problem(503, "STORAGE_UNAVAILABLE", "회원 자료 폴더를 준비하지 못했습니다.");
        }
        List<Object> args = new ArrayList<>();
        StringBuilder where = new StringBuilder(" WHERE a.owner_account_id=?");
        args.add(owner);
        if (business != null) {
            where.append(" AND a.business_scope_id=?");
            args.add(business);
        }
        if (workflow != null && !workflow.isBlank()) {
            where.append(" AND a.workflow=?");
            args.add(workflow);
        }
        if (scope != null && !scope.isBlank()) {
            where.append(" AND a.scope_kind=?");
            args.add(scope);
        }
        if (project != null && !project.isBlank()) {
            where.append(" AND a.project_id=?");
            args.add(project);
        }
        if (q != null && !q.isBlank()) {
            where.append(" AND LOWER(a.original_name) LIKE ? ESCAPE '!' ");
            args.add(
                    "%"
                            + q.toLowerCase(Locale.ROOT)
                                    .replace("!", "!!")
                                    .replace("%", "!%")
                                    .replace("_", "!_")
                            + "%");
        }
        where.append(
                "TRASH".equals(state)
                        ? " AND a.archived_at IS NOT NULL AND a.storage_state<>'PURGED'"
                        : " AND a.archived_at IS NULL AND a.storage_state='READY'");
        long count =
                db.queryForObject(
                        "SELECT COUNT(*) FROM ap_asset a" + where, Long.class, args.toArray());
        int p = Math.max(1, Math.min(page, (int) Math.max(1, (count + 24) / 25)));
        var queryArgs = new ArrayList<>(args);
        queryArgs.add((p - 1) * 25);
        var files =
                db.queryForList(
                        "SELECT "
                                + PUBLIC
                                + (admin ? ",a.owner_account_id,a.root_kind,a.disk_key" : "")
                                + " FROM ap_asset a LEFT JOIN ap_work_draft d ON d.id=a.project_id"
                                + " JOIN ap_business_scope b ON b.id=a.business_scope_id"
                                + where
                                + " ORDER BY a.created_at DESC,a.id LIMIT 25 OFFSET ?",
                        queryArgs.toArray());
        if (admin)
            for (var f : files) {
                try {
                    f.put("absolute_path", path(f).toString());
                    f.put("file_exists", Files.isRegularFile(path(f), LinkOption.NOFOLLOW_LINKS));
                } catch (RuntimeException bad) {
                    f.put("absolute_path", "경로 검증 실패");
                    f.put("file_exists", false);
                }
            }
        return Map.of(
                "items",
                files,
                "total",
                count,
                "page",
                p,
                "pages",
                Math.max(1, (count + 24) / 25),
                "quota",
                limits.quota(s, owner, admin));
    }

    public Map<String, Object> impact(SessionSubject s, String id, boolean admin) {
        var r = row(id);
        if (admin) limits.admin(s);
        else {
            owner(s, r);
            access.business(
                    s,
                    Inputs.number(r, "business_scope_id"),
                    true,
                    List.of("workspace.asset.list"),
                    () -> null);
        }
        var linked =
                db.queryForList(
                        "SELECT d.id,d.title,d.workflow FROM ap_work_draft d WHERE d.id IN (SELECT"
                            + " draft_id FROM ap_draft_asset WHERE asset_id=?) OR"
                            + " d.saved_assets_json LIKE ?",
                        id,
                        "%" + id + "%");
        int active =
                db.queryForObject(
                        "SELECT COUNT(*) FROM ap_ai_run WHERE status IN ('QUEUED','RUNNING') AND"
                            + " request_json LIKE ?",
                        Integer.class,
                        "%" + id + "%");
        return Map.of(
                "references",
                linked,
                "activeAi",
                active,
                "bytes",
                r.get("byte_size"),
                "version",
                r.get("version"),
                "state",
                r.get("storage_state"));
    }

    public Object purge(SessionSubject s, String id, long version, boolean detach, boolean admin) {
        var r = row(id);
        if (admin) limits.admin(s);
        else owner(s, r);
        tx.executeWithoutResult(
                t -> {
                    policies.state(true);
                    if (admin) limits.admin(s);
                    else {
                        owner(s, r);
                        access.business(
                                s,
                                Inputs.number(r, "business_scope_id"),
                                true,
                                List.of("workspace.asset.archive"),
                                () -> null);
                    }
                    limits.lock(Inputs.number(r, "owner_account_id"));
                    var current = row(id);
                    version(current, version);
                    if ("PURGED".equals(current.get("storage_state"))) return;
                    if (current.get("archived_at") == null)
                        throw Problem.invalid("먼저 휴지통으로 이동하세요.");
                    var impact = impact(s, id, admin);
                    if (((Number) impact.get("activeAi")).intValue() > 0)
                        throw Problem.conflict("이 자료를 사용하는 AI 작업이 실행 중입니다. 완료하거나 취소한 후 삭제하세요.");
                    var refs = (List<?>) impact.get("references");
                    if (!refs.isEmpty() && !detach)
                        throw new Problem(
                                409, "ASSET_REFERENCED", "연결된 작업을 확인하고 연결 해제에 동의해야 합니다.", impact);
                    if (detach) {
                        for (Object item : refs) {
                            var d = (Map<?, ?>) item;
                            String did = (String) d.get("id");
                            var saved =
                                    (List<?>)
                                            json.value(
                                                    db.queryForObject(
                                                            "SELECT saved_assets_json FROM"
                                                                + " ap_work_draft WHERE id=?",
                                                            String.class,
                                                            did));
                            var next = new ArrayList<>(saved);
                            next.remove(id);
                            db.update(
                                    "DELETE FROM ap_draft_asset WHERE draft_id=? AND asset_id=?",
                                    did,
                                    id);
                            db.update(
                                    "UPDATE ap_work_draft SET"
                                        + " saved_assets_json=?,version=version+1,updated_at=?"
                                        + " WHERE id=?",
                                    json.write(next),
                                    now(),
                                    did);
                        }
                    }
                    db.update(
                            "UPDATE ap_asset SET storage_state='PURGING',version=version+1 WHERE"
                                + " id=?",
                            id);
                    limits.audit(
                            s,
                            "ASSET_PURGE_REQUEST",
                            id,
                            detach ? "연결 해제 및 영구 삭제 확인" : "영구 삭제 확인",
                            Map.of("bytes", current.get("byte_size")),
                            Map.of("references", refs.size()));
                });
        finishPurge(id);
        return Map.of("state", row(id).get("storage_state"));
    }

    private void finishPurge(String id) {
        tx.executeWithoutResult(
                t -> {
                    policies.state(true);
                    var before = row(id);
                    limits.lock(Inputs.number(before, "owner_account_id"));
                    var r = row(id);
                    if (!"PURGING".equals(r.get("storage_state"))) return;
                    try {
                        for (var copy :
                                db.queryForList(
                                        "SELECT * FROM ap_storage_copy WHERE asset_id=? AND"
                                            + " state='READY'",
                                        id)) {
                            deleteCopy(copy);
                        }
                        Files.deleteIfExists(path(r));
                    } catch (IOException e) {
                        throw new Problem(
                                503,
                                "STORAGE_DELETE_PENDING",
                                "파일 삭제를 완료하지 못했습니다. 용량은 유지되며 자동 재시도됩니다.");
                    }
                    db.update(
                            "UPDATE ap_asset SET"
                                + " storage_state='PURGED',purged_at=?,version=version+1 WHERE"
                                + " id=?",
                            now(),
                            id);
                    access.event(
                            Inputs.number(r, "workspace_id"),
                            Inputs.number(r, "business_scope_id"),
                            "asset.purged",
                            id,
                            Inputs.number(r, "version") + 1);
                });
    }

    @Scheduled(fixedDelay = 60000, initialDelay = 60000)
    public void recoverPurges() {
        for (String id :
                db.queryForList(
                        "SELECT id FROM ap_asset WHERE storage_state='PURGING' ORDER BY created_at"
                            + " LIMIT 20",
                        String.class))
            try {
                finishPurge(id);
            } catch (RuntimeException ignored) {
            }
    }

    public Object migrate(SessionSubject s, String id, long version) {
        limits.admin(s);
        return tx.execute(
                t -> {
                    policies.state(true);
                    limits.admin(s);
                    var before = row(id);
                    limits.lock(Inputs.number(before, "owner_account_id"));
                    var r = row(id);
                    version(r, version);
                    if (!"LEGACY".equals(r.get("root_kind")))
                        return Map.of("status", "already-managed");
                    if (!"READY".equals(r.get("storage_state")))
                        throw Problem.conflict("삭제 중인 자료는 이전할 수 없습니다.");
                    Object created = r.get("created_at");
                    LocalDate registered =
                            (created instanceof LocalDateTime dt
                                            ? dt
                                            : ((java.sql.Timestamp) created).toLocalDateTime())
                                    .atOffset(ZoneOffset.UTC)
                                    .atZoneSameInstant(ZoneId.of("Asia/Seoul"))
                                    .toLocalDate();
                    String old = (String) r.get("disk_key"),
                            ext = old.substring(old.lastIndexOf('.') + 1),
                            key =
                                    storage.key(
                                            Inputs.number(r, "owner_account_id"),
                                            Inputs.number(r, "business_scope_id"),
                                            (String) r.get("scope_kind"),
                                            (String) r.get("workflow"),
                                            (String) r.get("project_id"),
                                            (String) r.get("purpose"),
                                            id,
                                            ext,
                                            registered);
                    try {
                        byte[] bytes =
                                storage.read(
                                        "LEGACY",
                                        old,
                                        Inputs.number(r, "byte_size"),
                                        (String) r.get("sha256"));
                        storage.memberFolders(Inputs.number(r, "owner_account_id"));
                        if (!Files.exists(storage.resolve("MANAGED", key)))
                            storage.write(key, bytes);
                        storage.read("MANAGED", key, bytes.length, (String) r.get("sha256"));
                    } catch (IOException e) {
                        throw new Problem(
                                503, "STORAGE_MIGRATION", "원본 또는 복사 파일을 확인하지 못했습니다. 원본을 유지합니다.");
                    }
                    db.update(
                            "INSERT INTO"
                                + " ap_storage_copy(id,asset_id,owner_account_id,disk_key,byte_size,sha256,created_at)"
                                + " SELECT ?,?,?,?,?,?,? WHERE NOT EXISTS(SELECT 1 FROM"
                                + " ap_storage_copy WHERE asset_id=? AND disk_key=?)",
                            UUID.randomUUID().toString(),
                            id,
                            r.get("owner_account_id"),
                            old,
                            r.get("byte_size"),
                            r.get("sha256"),
                            now(),
                            id,
                            old);
                    db.update(
                            "UPDATE ap_asset SET root_kind='MANAGED',disk_key=?,version=version+1"
                                + " WHERE id=?",
                            key,
                            id);
                    limits.audit(
                            s,
                            "ASSET_MIGRATE",
                            id,
                            "무결성 확인 후 저장 경로 이전 · 기존 원본 유지",
                            Map.of("path", old),
                            Map.of("path", key));
                    return Map.of("status", "migrated", "legacyCopyRetained", true);
                });
    }

    private void deleteCopy(Map<String, Object> copy) throws IOException {
        Path p = storage.resolve("LEGACY", (String) copy.get("disk_key"));
        if (Files.exists(p, LinkOption.NOFOLLOW_LINKS)) {
            storage.read(
                    "LEGACY",
                    (String) copy.get("disk_key"),
                    Inputs.number(copy, "byte_size"),
                    (String) copy.get("sha256"));
            Files.delete(p);
        }
        db.update(
                "UPDATE ap_storage_copy SET state='DELETED',deleted_at=? WHERE id=?",
                now(),
                copy.get("id"));
    }

    public Object retained(SessionSubject s, long account) {
        limits.admin(s);
        var list =
                db.queryForList(
                        "SELECT c.*,a.original_name FROM ap_storage_copy c JOIN ap_asset a ON"
                            + " a.id=c.asset_id WHERE c.owner_account_id=? AND c.state='READY'"
                            + " ORDER BY c.created_at LIMIT 100",
                        account);
        for (var c : list)
            c.put(
                    "absolute_path",
                    storage.resolve("LEGACY", (String) c.get("disk_key")).toString());
        return list;
    }

    public Object cleanupCopy(SessionSubject s, String id) {
        limits.admin(s);
        return tx.execute(
                t -> {
                    policies.state(true);
                    limits.admin(s);
                    var copies = db.queryForList("SELECT * FROM ap_storage_copy WHERE id=?", id);
                    if (copies.isEmpty())
                        throw new Problem(404, "COPY_NOT_FOUND", "이전 사본을 찾지 못했습니다.");
                    var copy = copies.get(0);
                    limits.lock(Inputs.number(copy, "owner_account_id"));
                    if (!"READY".equals(copy.get("state")))
                        return Map.of("status", "already-removed");
                    var current = row((String) copy.get("asset_id"));
                    try {
                        if (!"PURGED".equals(current.get("storage_state")))
                            storage.read(
                                    (String) current.get("root_kind"),
                                    (String) current.get("disk_key"),
                                    Inputs.number(current, "byte_size"),
                                    (String) current.get("sha256"));
                        deleteCopy(copy);
                    } catch (IOException e) {
                        throw new Problem(
                                503, "COPY_CLEANUP", "새 원본과 이전 사본 무결성을 확인하지 못했습니다. 사본을 유지합니다.");
                    }
                    limits.audit(
                            s,
                            "LEGACY_COPY_DELETE",
                            id,
                            "새 원본 검증 후 이전 사본 정리",
                            Map.of("bytes", copy.get("byte_size")),
                            Map.of("state", "DELETED"));
                    return Map.of("status", "removed");
                });
    }

    public Map<String, String> roots(SessionSubject s) {
        limits.admin(s);
        return storage.roots();
    }
}

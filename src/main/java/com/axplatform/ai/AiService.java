package com.axplatform.ai;

import com.axplatform.common.*;
import com.axplatform.policy.AccessService;
import com.axplatform.security.*;
import com.axplatform.sheet.SheetService;
import com.axplatform.sheet.WorkbookRules;
import com.axplatform.workspace.*;

import jakarta.annotation.PreDestroy;
import jakarta.servlet.http.HttpSession;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.*;
import java.util.*;
import java.util.concurrent.*;

/** Durable status, scoped Java tools, bounded async provider calls, explicit versioned apply. */
@Service
public final class AiService {
    public record Start(
            String prompt,
            List<String> assetIds,
            String requestKey,
            long draftVersion,
            long sheetVersion,
            String previousRunId, String targetSheetId, List<String> photoSheetIds) {
        public Start(String prompt,List<String> assetIds,String requestKey,long draftVersion,long sheetVersion,String previousRunId) {
            this(prompt,assetIds,requestKey,draftVersion,sheetVersion,previousRunId,null,List.of());
        }
    }
    public record BatchApply(String requestKey,long draftVersion,long sheetVersion,List<String> sheetIds,boolean acceptAssumptions) {}

    public record Apply(
            String requestKey,
            long draftVersion,
            long sheetVersion,
            List<String> fields,
            Map<String, Object> editedProposal,
            boolean acceptAssumptions) {}

    private static final Set<String> APPLY_FIELDS =
            Set.of("title", "purpose", "data", "result", "details", "audience", "sheet");
    private static final Set<String> ACTIVE = Set.of("QUEUED", "RUNNING");
    private final com.axplatform.operations.AiMeteringService metering;
    private final JdbcTemplate db;
    private final AccessService access;
    private final DraftService drafts;
    private final SheetService sheets;
    private final AssetService assets;
    private final JsonCodec json;
    private final Clock clock;
    private final AiSettings settings;
    private final OpenAiResponses provider;
    private final AccountSessionService sessions;
    private final String owner = UUID.randomUUID().toString();
    private final ThreadPoolExecutor executor;
    private final java.util.concurrent.atomic.AtomicLong nextDiagnostic =
            new java.util.concurrent.atomic.AtomicLong(Long.MIN_VALUE);
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(AiService.class);

    public AiService(
            JdbcTemplate db,
            AccessService access,
            DraftService drafts,
            SheetService sheets,
            AssetService assets,
            JsonCodec json,
            Clock clock,
            AiSettings settings,
            OpenAiResponses provider,
            AccountSessionService sessions,
            com.axplatform.operations.AiMeteringService metering) {
        this.metering = metering;
        this.db = db;
        this.access = access;
        this.drafts = drafts;
        this.sheets = sheets;
        this.assets = assets;
        this.json = json;
        this.clock = clock;
        this.settings = settings;
        this.provider = provider;
        this.sessions = sessions;
        int threads = settings.threads();
        this.executor =
                new ThreadPoolExecutor(
                        threads,
                        threads,
                        30,
                        TimeUnit.SECONDS,
                        new ArrayBlockingQueue<>(32),
                        r -> {
                            Thread t = new Thread(r, "ax-ai-worker");
                            t.setDaemon(true);
                            return t;
                        },
                        new ThreadPoolExecutor.AbortPolicy());
    }

    @PreDestroy
    public void close() {
        executor.shutdownNow();
    }

    public Map<String, Object> settings(SessionSubject subject) {
        if (!sessions.current(subject)) throw new Problem(401, "SESSION_EXPIRED", "다시 로그인해 주세요.");
        var result = new LinkedHashMap<String, Object>(settings.publicSettings());
        result.put("administrator", subject.administrator());
        return result;
    }

    public Map<String, Object> diagnose(SessionSubject subject) {
        if (!sessions.current(subject)) throw new Problem(401, "SESSION_EXPIRED", "다시 로그인해 주세요.");
        if (!subject.administrator()) throw new Problem(403, "ADMIN_REQUIRED", "관리자 전용 기능입니다.");
        long now = System.nanoTime(), previous = nextDiagnostic.get();
        if (now < previous
                || !nextDiagnostic.compareAndSet(previous, now + TimeUnit.SECONDS.toNanos(30)))
            throw new Problem(429, "AI_DIAGNOSTIC_WAIT", "연결 확인은 30초 간격으로 실행할 수 있습니다.");
        var result = provider.diagnose();
        if (!sessions.current(subject)) throw new Problem(401, "SESSION_EXPIRED", "다시 로그인해 주세요.");
        return result;
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock);
    }

    private Map<String, Object> row(String id) {
        if (id == null || !id.matches("[a-f0-9-]{36}"))
            throw new Problem(404, "AI_RUN_NOT_FOUND", "AI 작업을 찾지 못했습니다.");
        var rows = db.queryForList("SELECT * FROM ap_ai_run WHERE id=?", id);
        if (rows.isEmpty()) throw new Problem(404, "AI_RUN_NOT_FOUND", "AI 작업을 찾지 못했습니다.");
        return rows.get(0);
    }

    private void owns(SessionSubject s, Map<String, Object> r) {
        if (Inputs.number(r, "account_id") != s.accountId())
            throw new Problem(404, "AI_RUN_NOT_FOUND", "본인의 AI 작업을 찾지 못했습니다.");
    }

    private String sessionHash(HttpSession session, SessionSubject subject) {
        SessionLease.require(session, subject);
        try {
            return Crypto.sha256(session.getId());
        } catch (IllegalStateException ended) {
            throw new Problem(401, "SESSION_EXPIRED", "원래 로그인 세션이 종료되었습니다.");
        }
    }

    private void requireOrigin(
            Map<String, Object> run, HttpSession session, SessionSubject subject) {
        Object original = json.object((String) run.get("request_json")).get("originSessionHash");
        if (!(original instanceof String hash)
                || !Crypto.equal(hash, sessionHash(session, subject)))
            throw new Problem(
                    401,
                    "AI_ORIGIN_SESSION_ENDED",
                    "이 AI 작업을 시작한 로그인 세션이 종료되거나 변경되었습니다. 최신 초안으로 새 AI 요청을 시작해 주세요.");
    }

    private static String key(String value, String header) {
        if (value == null || !value.matches("[A-Za-z0-9_-]{8,100}") || !value.equals(header))
            throw Problem.invalid("Idempotency-Key와 요청 키가 일치해야 합니다.");
        return value;
    }

    private static List<String> ids(List<String> values, int max) {
        if (values == null
                || values.size() > max
                || new HashSet<>(values).size() != values.size()
                || values.stream().anyMatch(v -> v == null || !v.matches("[a-f0-9-]{36}")))
            throw Problem.invalid("명시적으로 선택한 첨부 목록을 확인해 주세요.");
        return List.copyOf(values);
    }

    public Map<String, Object> start(
            SessionSubject s, HttpSession session, String draftId, Start command, String header) {
        if (command == null) throw Problem.invalid("AI 요청 내용을 입력해 주세요.");
        SessionLease.require(session, s);
        settings.require();
        String requestKey = key(command.requestKey(), header),
                prompt = Inputs.text(command.prompt(), 12000, "AI 요청"),
                originSessionHash = sessionHash(session, s);
        metering.check(s);
        List<String> selected = ids(command.assetIds(), settings.assets());
        var initial = drafts.get(s, draftId);
        long business = Inputs.number(initial, "business_scope_id");
        var normalized = new LinkedHashMap<String, Object>();
        normalized.put("prompt", prompt);
        normalized.put("assetIds", selected);
        normalized.put("draftVersion", command.draftVersion());
        normalized.put("sheetVersion", command.sheetVersion());
        normalized.put("previousRunId", command.previousRunId());
        normalized.put("targetSheetId",command.targetSheetId());
        if(command.photoSheetIds()!=null&&command.photoSheetIds().stream().anyMatch(v->v==null||!v.matches("[A-Za-z][A-Za-z0-9_-]{0,63}")))throw Problem.invalid("사진별 시트 ID를 확인하세요.");
        List<String> photoIds=command.photoSheetIds()==null?List.of():List.copyOf(command.photoSheetIds());
        if(photoIds.size()>settings.assets()||new HashSet<>(photoIds).size()!=photoIds.size())throw Problem.invalid("한 번에 사진별 시트는 "+settings.assets()+"개까지 중복 없이 선택하세요.");
        normalized.put("photoSheetIds",photoIds);
        String hash = Crypto.sha256(json.write(normalized));
        String id = UUID.randomUUID().toString();
        boolean[] created = {false};
        Map<String, Object> result =
                access.business(
                        s,
                        business,
                        false,
                        List.of("workspace.ai.run"),
                        () -> {
                            SessionLease.require(session, s);
                            var duplicates =
                                    db.queryForList(
                                            "SELECT * FROM ap_ai_run WHERE account_id=? AND"
                                                    + " request_key=?",
                                            s.accountId(),
                                            requestKey);
                            if (!duplicates.isEmpty()) {
                                var previous = duplicates.get(0);
                                if (!draftId.equals(previous.get("draft_id"))
                                        || !hash.equals(previous.get("request_hash")))
                                    throw new Problem(
                                            409,
                                            "IDEMPOTENCY_CONFLICT",
                                            "같은 요청 키가 다른 AI 요청에 사용되었습니다.");
                                return publicRun(previous);
                            }
                            var draft = drafts.get(s, draftId);
                            requireActive(draft);
                            boolean excel = "EXCEL".equals(draft.get("workflow"));
                            if (Inputs.number(draft, "version") != command.draftVersion())
                                throw Problem.conflict("초안이 변경되었습니다. 최신 내용을 확인한 뒤 새로 요청해 주세요.");
                            long sheetVersion =
                                    excel ? Inputs.number(sheets.get(s, draftId), "version") : -1;
                            if (sheetVersion != command.sheetVersion())
                                throw Problem.conflict("표가 변경되었습니다. 최신 내용을 확인한 뒤 새로 요청해 주세요.");
                            if(excel) {
                                @SuppressWarnings("unchecked") var book=(Map<String,Object>)sheets.get(s,draftId).get("workbook");
                                if(photoIds.isEmpty()) {
                                    if((command.targetSheetId()==null||command.targetSheetId().isBlank())&&WorkbookRules.sheets(book).size()>1)throw Problem.invalid("AI가 편집할 시트를 선택하세요.");
                                    var target=WorkbookRules.sheet(book,command.targetSheetId());normalized.put("targetSheetId",target.get("id"));normalized.put("targetSheetName",target.get("name"));
                                } else {
                                    Set<String> targetAssets=new LinkedHashSet<>();List<Map<String,Object>> photoTargets=new ArrayList<>();
                                    for(String sheetId:photoIds){var target=WorkbookRules.sheet(book,sheetId);var sourceIds=(List<?>)target.get("sourceAssetIds");
                                        if(sourceIds.size()!=1)throw Problem.invalid("사진별 시트에는 원본 사진을 한 장씩 연결하세요.");
                                        String assetId=(String)sourceIds.get(0);var asset=assets.metadata(s,assetId);
                                        if(!Set.of("image/png","image/jpeg").contains(asset.get("media_type")))throw Problem.invalid(target.get("name")+": 사진별 시트는 PNG/JPG 사진만 선택하세요.");
                                        if(!targetAssets.add(assetId))throw Problem.invalid("같은 사진을 여러 시트에 중복 요청할 수 없습니다.");
                                        photoTargets.add(Map.of("sheetId",sheetId,"sheetName",target.get("name"),"assetId",assetId,"sourceName",asset.get("original_name")));
                                    }
                                    normalized.put("photoTargets",photoTargets);
                                    if(!targetAssets.equals(new HashSet<>(selected)))throw Problem.invalid("선택한 사진과 시트에 연결된 원본이 다릅니다.");
                                    if(command.previousRunId()!=null&&!command.previousRunId().isBlank())throw Problem.invalid("사진별 시트 요청은 이전 단일 시트 제안 없이 시작하세요.");
                                }
                            } else if(!photoIds.isEmpty()||command.targetSheetId()!=null) throw Problem.invalid("시트 선택은 엑셀 작업에서만 사용할 수 있습니다.");
                            db.queryForList(
                                    "SELECT id FROM ap_account WHERE id=? FOR UPDATE",
                                    s.accountId());
                            if (!(draft.get("assetIds") instanceof List<?> linked)
                                    || !linked.containsAll(selected))
                                throw Problem.invalid("현재 초안에 연결된 첨부만 명시 선택할 수 있습니다.");
                            long bytes = 0;
                            for (String assetId : selected) {
                                var metadata = assets.metadata(s, assetId);
                                if (Inputs.number(metadata, "business_scope_id") != business)
                                    throw Problem.invalid("다른 사업의 첨부를 사용할 수 없습니다.");
                                bytes += Inputs.number(metadata, "byte_size");
                            }
                            if (bytes > 6 * 1024 * 1024L)
                                throw new Problem(422, "AI_ASSET_LIMIT", "선택 첨부의 합계는 6MB 이하입니다.");
                            Map<String, Object> previousProposal = null;
                            if (command.previousRunId() != null
                                    && !command.previousRunId().isBlank()) {
                                var previous = row(command.previousRunId());
                                owns(s, previous);
                                if (!draftId.equals(previous.get("draft_id"))
                                        || previous.get("result_json") == null
                                        || !Set.of("READY", "APPLIED")
                                                .contains(previous.get("status")))
                                    throw Problem.invalid("같은 초안의 완료된 검토 제안만 이어서 사용할 수 있습니다.");
                                var previousRequest=json.object((String)previous.get("request_json"));
                                if(excel&&!Objects.equals(previousRequest.getOrDefault("targetSheetId",WorkbookRules.LEGACY_ID),normalized.get("targetSheetId")))
                                    throw Problem.invalid("다른 시트의 제안을 이어서 사용할 수 없습니다. 새 요청으로 시작하세요.");
                                previousProposal =
                                        ProposalRules.object(
                                                json.object((String) previous.get("result_json"))
                                                        .get("proposal"));
                            }
                            LocalDateTime timestamp = now(),
                                    day = LocalDate.now(clock).atStartOfDay();
                            Integer daily =
                                    db.queryForObject(
                                            "SELECT COUNT(*) FROM ap_ai_run WHERE account_id=? AND"
                                                    + " created_at>=?",
                                            Integer.class,
                                            s.accountId(),
                                            day);
                            Integer concurrent =
                                    db.queryForObject(
                                            "SELECT COUNT(*) FROM ap_ai_run WHERE account_id=? AND"
                                                    + " status IN ('QUEUED','RUNNING') AND"
                                                    + " lease_until>?",
                                            Integer.class,
                                            s.accountId(),
                                            timestamp);
                            if (daily != null && daily >= settings.daily())
                                throw new Problem(
                                        429,
                                        "AI_DAILY_LIMIT",
                                        "계정별 오늘 AI 작업 한도에 도달했습니다. UTC 날짜 기준입니다.");
                            if (concurrent != null && concurrent >= settings.concurrent())
                                throw new Problem(
                                        429,
                                        "AI_CONCURRENT_LIMIT",
                                        "현재 진행 중인 AI 작업이 있습니다. 완료하거나 취소한 뒤 다시 요청해 주세요.");
                            normalized.put("workflow", draft.get("workflow"));
                            normalized.put("previousProposal", previousProposal);
                            normalized.put("originSessionHash", originSessionHash);
                            db.update(
                                    "INSERT INTO"
                                        + " ap_ai_run(id,account_id,workspace_id,business_scope_id,draft_id,request_key,request_hash,base_draft_version,base_sheet_version,request_json,status,phase,model,reasoning_effort,worker_owner,lease_until,created_at,updated_at)"
                                        + " VALUES(?,?,?,?,?,?,?,?,?,?,'QUEUED','서버"
                                        + " 대기',?,?,?,?,?,?)",
                                    id,
                                    s.accountId(),
                                    draft.get("workspace_id"),
                                    business,
                                    draftId,
                                    requestKey,
                                    hash,
                                    command.draftVersion(),
                                    sheetVersion,
                                    json.write(normalized),
                                    settings.model(),
                                    settings.reasoning(),
                                    owner,
                                    timestamp.plusSeconds(settings.jobSeconds()),
                                    timestamp,
                                    timestamp);
                            access.event(
                                    Inputs.number(draft, "workspace_id"),
                                    business,
                                    "ai.queued",
                                    id,
                                    0);
                            created[0] = true;
                            return publicRun(row(id));
                        });
        if (created[0]) {
            try {
                executor.execute(() -> work(s, session, id));
            } catch (RejectedExecutionException full) {
                fail(id, "AI_QUEUE_FULL", "서버 작업 대기가 가득 찼습니다. 자동 재시도하지 않았습니다.");
                return get(s, id);
            }
        }
        return result;
    }

    private void requireActive(Map<String, Object> draft) {
        if ("ARCHIVED".equals(draft.get("status")))
            throw new Problem(409, "DRAFT_ARCHIVED", "보관된 초안은 먼저 복원해 주세요.");
    }

    public List<Map<String, Object>> list(SessionSubject s, String draftId) {
        var draft = drafts.get(s, draftId);
        return access.business(
                s,
                Inputs.number(draft, "business_scope_id"),
                false,
                List.of("workspace.ai.read"),
                () -> {
                    List<Map<String, Object>> out = new ArrayList<>();
                    for (var r :
                            db.queryForList(
                                    "SELECT * FROM ap_ai_run WHERE account_id=? AND draft_id=?"
                                            + " ORDER BY created_at DESC,id DESC LIMIT 10",
                                    s.accountId(),
                                    draftId)) out.add(publicRun(r));
                    return out;
                });
    }

    public Map<String, Object> get(SessionSubject s, String id) {
        var initial = row(id);
        owns(s, initial);
        return access.business(
                s,
                Inputs.number(initial, "business_scope_id"),
                false,
                List.of("workspace.ai.read"),
                () -> {
                    var current = row(id);
                    owns(s, current);
                    return publicRun(current);
                });
    }

    public Map<String, Object> cancel(SessionSubject s, HttpSession session, String id) {
        SessionLease.require(session, s);
        var initial = row(id);
        owns(s, initial);
        return access.business(
                s,
                Inputs.number(initial, "business_scope_id"),
                false,
                List.of("workspace.ai.cancel"),
                () -> {
                    SessionLease.require(session, s);
                    var current = row(id);
                    owns(s, current);
                    if (ACTIVE.contains(current.get("status"))
                            || "READY".equals(current.get("status"))) {
                        db.update(
                                "UPDATE ap_ai_run SET"
                                    + " status='CANCELLED',phase='취소됨',safe_error_code='AI_CANCELLED',safe_message=?,updated_at=?"
                                    + " WHERE id=? AND status IN ('QUEUED','RUNNING','READY')",
                                "사용자가 작업을 취소했습니다. 이미 처리된 외부 API 사용료가 환불된다는 뜻은 아닙니다.",
                                now(),
                                id);
                        access.event(
                                Inputs.number(current, "workspace_id"),
                                Inputs.number(current, "business_scope_id"),
                                "ai.cancelled",
                                id,
                                0);
                    }
                    return publicRun(row(id));
                });
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> apply(
            SessionSubject s, HttpSession session, String id, Apply command, String header) {
        if (command == null) throw Problem.invalid("적용할 제안을 확인해 주세요.");
        SessionLease.require(session, s);
        String requestKey = key(command.requestKey(), header);
        if (command.fields() == null
                || command.fields().isEmpty()
                || command.fields().size() > 7
                || new HashSet<>(command.fields()).size() != command.fields().size()
                || !APPLY_FIELDS.containsAll(command.fields()))
            throw Problem.invalid("적용할 항목을 중복 없이 선택해 주세요.");
        var initial = row(id);
        owns(s, initial);
        return access.business(
                s,
                Inputs.number(initial, "business_scope_id"),
                false,
                List.of("workspace.ai.apply"),
                () -> {
                    SessionLease.require(session, s);
                    var current = row(id);
                    owns(s, current);
                    requireOrigin(current, session, s);
                    String draftId = (String) current.get("draft_id");
                    var draft = drafts.get(s, draftId);
                    requireActive(draft);
                    String workflow = (String) draft.get("workflow");
                    Map<String, Object> proposal;
                    try {
                        proposal =
                                ProposalRules.normalize(
                                        command.editedProposal() != null
                                                ? command.editedProposal()
                                                : ProposalRules.object(
                                                        json.object(
                                                                        (String)
                                                                                current.get(
                                                                                        "result_json"))
                                                                .get("proposal")),
                                        workflow);
                    } catch (RuntimeException bad) {
                        throw new Problem(
                                422, "AI_PROPOSAL_INVALID", "적용할 제안의 항목·표·문자열 형식을 확인해 주세요.");
                    }
                    var fingerprint = new LinkedHashMap<String, Object>();
                    fingerprint.put("draftVersion", command.draftVersion());
                    fingerprint.put("sheetVersion", command.sheetVersion());
                    fingerprint.put("fields", command.fields().stream().sorted().toList());
                    fingerprint.put("proposal", proposal);
                    fingerprint.put("acceptAssumptions", command.acceptAssumptions());
                    String hash = Crypto.sha256(json.write(fingerprint));
                    if ("APPLIED".equals(current.get("status"))) {
                        if (requestKey.equals(current.get("apply_key"))
                                && hash.equals(current.get("apply_hash")))
                            return publicRun(current);
                        throw new Problem(
                                409, "AI_ALREADY_APPLIED", "이미 적용된 제안입니다. 같은 내용은 중복 적용하지 않습니다.");
                    }
                    if (!"READY".equals(current.get("status")))
                        throw new Problem(409, "AI_NOT_READY", "검토할 AI 제안이 아직 준비되지 않았습니다.");
                    if (!Boolean.TRUE.equals(proposal.get("ready")))
                        throw new Problem(
                                422,
                                "AI_CONFIRM_REQUIRED",
                                "필수 확인 질문에 답한 뒤 새 제안을 검토하거나, 확인한 내용으로 제안을 직접 수정해 주세요.");
                    if (!((List<?>) proposal.get("assumptions")).isEmpty()
                            && !command.acceptAssumptions())
                        throw new Problem(
                                422, "AI_ASSUMPTIONS_REQUIRED", "가정한 내용을 확인한 뒤 동의 항목을 선택해 주세요.");
                    if (Inputs.number(current, "base_draft_version") != command.draftVersion()
                            || Inputs.number(draft, "version") != command.draftVersion())
                        throw Problem.conflict(
                                "AI 요청 이후 초안이 변경되었습니다. 입력을 보존하고 최신 초안으로 새로 요청해 주세요.");
                    boolean excel = "EXCEL".equals(workflow);
                    long sheetVersion =
                            excel ? Inputs.number(sheets.get(s, draftId), "version") : -1;
                    if (Inputs.number(current, "base_sheet_version") != command.sheetVersion()
                            || sheetVersion != command.sheetVersion())
                        throw Problem.conflict("AI 요청 이후 표가 변경되었습니다. 최신 표로 새로 요청해 주세요.");
                    if (command.fields().contains("sheet")
                            && (!excel || proposal.get("sheet") == null))
                        throw Problem.invalid("현재 제안에 적용할 EXCEL 표가 없습니다.");
                    Map<String, Object>
                            suggestedDraft = ProposalRules.object(proposal.get("draft")),
                            suggestedAnswers = ProposalRules.object(suggestedDraft.get("answers"));
                    Map<String, Object> currentAnswers = ProposalRules.object(draft.get("answers"));
                    Map<String, String> answers = new LinkedHashMap<>();
                    for (String field : ProposalRules.FIELDS)
                        answers.put(
                                field,
                                (String)
                                        (command.fields().contains(field)
                                                ? suggestedAnswers.get(field)
                                                : currentAnswers.getOrDefault(field, "")));
                    String title =
                            (String)
                                    (command.fields().contains("title")
                                            ? suggestedDraft.get("title")
                                            : draft.get("title"));
                    var changed =
                            drafts.edit(
                                    s,
                                    draftId,
                                    new DraftService.Edit(
                                            title,
                                            answers,
                                            (List<String>) draft.get("assetIds"),
                                            command.draftVersion()));
                    long newDraftVersion = Inputs.number(changed, "version"),
                            newSheetVersion = sheetVersion;
                    if (command.fields().contains("sheet")) {
                        var changedSheet =
                                sheets.applyAi(
                                        s,
                                        draftId,
                                        ProposalRules.object(proposal.get("sheet")),
                                        sheetVersion,
                                        newDraftVersion,
                                        Objects.toString(json.object((String)current.get("request_json")).get("targetSheetId"),WorkbookRules.LEGACY_ID));
                        newSheetVersion = Inputs.number(changedSheet, "version");
                    }
                    requireOrigin(current, session, s);
                    var applied =
                            Map.of(
                                    "draftVersion",
                                    newDraftVersion,
                                    "sheetVersion",
                                    newSheetVersion);
                    db.update(
                            "UPDATE ap_ai_run SET status='APPLIED',phase='선택 항목 적용"
                                + " 완료',result_json=?,apply_key=?,apply_hash=?,applied_json=?,updated_at=?"
                                + " WHERE id=? AND status='READY'",
                            json.write(
                                    Map.of(
                                            "proposal",
                                            proposal,
                                            "toolNames",
                                            resultTools(current))),
                            requestKey,
                            hash,
                            json.write(applied),
                            now(),
                            id);
                    access.event(
                            Inputs.number(current, "workspace_id"),
                            Inputs.number(current, "business_scope_id"),
                            "ai.applied",
                            id,
                            newDraftVersion);
                    return publicRun(row(id));
                });
    }

    private Map<String, Object> publicRun(Map<String, Object> r) {
        var request = json.object((String) r.get("request_json"));
        var out = new LinkedHashMap<String, Object>();
        out.put("id", r.get("id"));
        out.put("draftId", r.get("draft_id"));
        out.put("status", r.get("status"));
        out.put("phase", r.get("phase"));
        out.put("baseDraftVersion", r.get("base_draft_version"));
        out.put("baseSheetVersion", r.get("base_sheet_version"));
        out.put(
                "proposal",
                r.get("result_json") == null
                        ? null
                        : json.object((String) r.get("result_json")).get("proposal"));
        out.put("model", r.get("model"));
        out.put("reasoningEffort", r.get("reasoning_effort"));
        out.put("inputTokens", r.get("input_tokens"));
        out.put("outputTokens", r.get("output_tokens"));
        out.put("errorCode", r.get("safe_error_code"));
        out.put("message", r.get("safe_message"));
        out.put(
                "diagnostic",
                r.get("result_json") == null
                        ? Map.of()
                        : json.object((String) r.get("result_json"))
                                .getOrDefault("diagnostic", Map.of()));
        out.put(
                "applied",
                r.get("applied_json") == null ? null : json.object((String) r.get("applied_json")));
        out.put("createdAt", r.get("created_at"));
        out.put("updatedAt", r.get("updated_at"));
        out.put("prompt", request.get("prompt"));
        out.put("targetSheetId",request.getOrDefault("targetSheetId",WorkbookRules.LEGACY_ID));
        out.put("targetSheetName",request.getOrDefault("targetSheetName","시트1"));
        out.put("photoTargets",request.getOrDefault("photoTargets",List.of()));
        out.put("photoSheetIds",request.getOrDefault("photoSheetIds",List.of()));
        out.put("sheetResults",r.get("result_json")==null?List.of():json.object((String)r.get("result_json")).getOrDefault("sheetResults",List.of()));
        out.put("toolNames", resultTools(r));
        return out;
    }

    private Object resultTools(Map<String, Object> r) {
        return r.get("result_json") == null
                ? List.of()
                : json.object((String) r.get("result_json")).getOrDefault("toolNames", List.of());
    }

    private final class RunningContext implements OpenAiResponses.Context {
        final SessionSubject subject;
        final HttpSession session;
        final String id, draftId, workflow;
        String targetSheetId; int roundOffset;
        final long business, baseDraft, baseSheet;
        final Instant deadline;

        RunningContext(SessionSubject s, HttpSession session, Map<String, Object> r) {
            this.subject = s;
            this.session = session;
            this.id = (String) r.get("id");
            this.draftId = (String) r.get("draft_id");
            this.business = Inputs.number(r, "business_scope_id");
            this.baseDraft = Inputs.number(r, "base_draft_version");
            this.baseSheet = Inputs.number(r, "base_sheet_version");
            this.workflow = (String) json.object((String) r.get("request_json")).get("workflow");
            this.targetSheetId=Objects.toString(json.object((String)r.get("request_json")).get("targetSheetId"),WorkbookRules.LEGACY_ID);
            Object value = r.get("lease_until");
            LocalDateTime until =
                    value instanceof LocalDateTime t
                            ? t
                            : ((java.sql.Timestamp) value).toLocalDateTime();
            this.deadline = until.toInstant(ZoneOffset.UTC);
        }

        public boolean metered() {
            return true;
        }

        public String beforeCall(int round, String model, long input, int output) {
            return metering.reserve(subject, id, round+roundOffset, model, input, output);
        }

        public void usage(String call, Map<?, ?> usage, long elapsed) {
            metering.measured(call, usage, elapsed);
        }

        public void failed(String call, String code, long elapsed) {
            metering.unknown(call, code, elapsed);
        }

        public void checkpoint() {
            metering.check(subject);
            if (Thread.currentThread().isInterrupted() || !clock.instant().isBefore(deadline))
                throw new OpenAiResponses.Failure(
                        "AI_JOB_TIMEOUT", "AI 작업 제한 시간이 지났습니다. 자동 재시도하지 않았습니다.");
            SessionLease.require(session, subject);
            access.business(
                    subject,
                    business,
                    false,
                    List.of("workspace.ai.run"),
                    () -> {
                        SessionLease.require(session, subject);
                        var current = row(id);
                        owns(subject, current);
                        requireOrigin(current, session, subject);
                        if (!"RUNNING".equals(current.get("status"))
                                || !owner.equals(current.get("worker_owner")))
                            throw new OpenAiResponses.Failure(
                                    "AI_STOPPED", "AI 작업이 취소되거나 중단되었습니다.");
                        var draft = drafts.get(subject, draftId);
                        requireActive(draft);
                        if (Inputs.number(draft, "version") != baseDraft)
                            throw Problem.conflict("작업 중 원본 초안이 변경되어 AI 작업을 중단했습니다.");
                        if ("EXCEL".equals(workflow)
                                && Inputs.number(sheets.get(subject, draftId), "version")
                                        != baseSheet)
                            throw Problem.conflict("작업 중 원본 표가 변경되어 AI 작업을 중단했습니다.");
                        for (String assetId : requestAssets(current)) {
                            var m = assets.metadata(subject, assetId);
                            if (Inputs.number(m, "business_scope_id") != business)
                                throw Problem.invalid("첨부 범위가 변경되었습니다.");
                        }
                        return null;
                    });
        }

        public Object read(String name) {
            checkpoint();
            return switch (name) {
                case "read_workflow_guide" -> AiTools.guide(workflow);
                case "read_current_draft" -> {
                    var d = drafts.get(subject, draftId);
                    yield Map.of(
                            "workflow",
                            workflow,
                            "title",
                            d.get("title"),
                            "answers",
                            d.get("answers"),
                            "version",
                            d.get("version"));
                }
                case "read_current_sheet" -> {
                    if (!workflow.equals("EXCEL")) throw Problem.invalid("EXCEL 업무가 아닙니다.");
                    var sheet = sheets.getSheet(subject, draftId,targetSheetId);
                    yield Map.of(
                            "version",
                            sheet.get("version"),
                            "draftVersion",
                            sheet.get("draftVersion"),
                            "sheet",
                            sheet.get("sheet"),"sheetId",sheet.get("sheetId"),"sheetName",sheet.get("sheetName"));
                }
                default -> throw new OpenAiResponses.Failure("AI_TOOL_DENIED", "허용하지 않은 도구 요청입니다.");
            };
        }
    }

    @SuppressWarnings("unchecked")
    private List<String> requestAssets(Map<String, Object> r) {
        return (List<String>) json.object((String) r.get("request_json")).get("assetIds");
    }

    @SuppressWarnings("unchecked")
    private void work(SessionSubject s, HttpSession session, String id) {
        try {
            var initial = row(id);
            owns(s, initial);
            long business = Inputs.number(initial, "business_scope_id");
            boolean claimed =
                    access.business(
                            s,
                            business,
                            false,
                            List.of("workspace.ai.run"),
                            () -> {
                                SessionLease.require(session, s);
                                requireOrigin(row(id), session, s);
                                return db.update(
                                                "UPDATE ap_ai_run SET status='RUNNING',phase='선택 자료"
                                                        + " 확인 및 모델 처리',updated_at=? WHERE id=? AND"
                                                        + " status='QUEUED' AND worker_owner=? AND"
                                                        + " lease_until>?",
                                                now(),
                                                id,
                                                owner,
                                                now())
                                        == 1;
                            });
            if (!claimed) return;
            var current = row(id);
            var context = new RunningContext(s, session, current);
            context.checkpoint();
            var storedRequest=json.object((String)current.get("request_json"));
            if(storedRequest.get("photoSheetIds") instanceof List<?> batch && !batch.isEmpty()) {
                workPhotoSheets(s,session,current,context,batch.stream().map(Object::toString).toList());return;
            }
            List<OpenAiResponses.Source> sources = new ArrayList<>();
            long bytes = 0;
            for (String assetId : requestAssets(current)) {
                context.checkpoint();
                var source = assets.readForAi(s, assetId, business);
                bytes += source.data().length;
                if (bytes > 6 * 1024 * 1024L)
                    throw new OpenAiResponses.Failure("AI_ASSET_LIMIT", "선택 첨부 합계가 6MB를 넘습니다.");
                sources.add(
                        new OpenAiResponses.Source(
                                source.name(), source.mediaType(), source.data()));
            }
            for(var source:sources)AiImageChecks.validate(source);
            var request = json.object((String) current.get("request_json"));
            OpenAiResponses.Result result =
                    provider.run(
                            context.workflow,
                            (String) request.get("prompt"),
                            (Map<String, Object>) request.get("previousProposal"),
                            sources,
                            context.deadline,
                            context);
            context.checkpoint();
            access.business(
                    s,
                    business,
                    false,
                    List.of("workspace.ai.run"),
                    () -> {
                        context.checkpoint();
                        db.update(
                                "UPDATE ap_ai_run SET status='READY',phase='사용자 검토"
                                    + " 대기',result_json=?,input_tokens=?,output_tokens=?,updated_at=?"
                                    + " WHERE id=? AND status='RUNNING' AND worker_owner=?",
                                json.write(
                                        Map.of(
                                                "proposal",
                                                result.proposal(),
                                                "toolNames",
                                                result.tools())),
                                result.inputTokens(),
                                result.outputTokens(),
                                now(),
                                id,
                                owner);
                        access.event(
                                Inputs.number(current, "workspace_id"),
                                business,
                                "ai.ready",
                                id,
                                0);
                        return null;
                    });
        } catch (OpenAiResponses.Failure stopped) {
            fail(id, stopped.code(), stopped.getMessage(), stopped.diagnostic());
        } catch (Problem denied) {
            String code =
                    denied.status() == 401
                            ? "AI_SESSION_ENDED"
                            : denied.status() == 403
                                    ? "AI_PERMISSION_CHANGED"
                                    : denied.status() == 409
                                            ? "AI_SOURCE_CHANGED"
                                            : "AI_SCOPE_UNAVAILABLE";
            fail(id, code, "세션·현재 권한·원본 상태를 확인할 수 없어 작업을 중단했습니다. 최신 화면에서 다시 확인해 주세요.");
        } catch (RuntimeException unknown) {
            fail(id, "AI_INTERNAL", "AI 작업을 완료하지 못했습니다. 자동 재시도하지 않았습니다. 설정과 최신 자료를 확인해 주세요.");
        }
    }

    @SuppressWarnings("unchecked")
    private void workPhotoSheets(SessionSubject actor,HttpSession session,Map<String,Object> run,RunningContext context,List<String> targets) {
        var request=json.object((String)run.get("request_json"));
        var book=(Map<String,Object>)sheets.get(actor,context.draftId).get("workbook");
        List<Map<String,Object>> results=new ArrayList<>();Set<String> tools=new LinkedHashSet<>();boolean stop=false;
        for(int index=0;index<targets.size();index++) {
            String targetId=targets.get(index);var target=WorkbookRules.sheet(book,targetId);String assetId=(String)((List<?>)target.get("sourceAssetIds")).get(0);
            var item=new LinkedHashMap<String,Object>();item.put("sheetId",targetId);item.put("sheetName",target.get("name"));item.put("assetId",assetId);
            if(stop){item.put("status","SKIPPED");item.put("message","앞선 연결·사용 한도 오류로 이 사진의 생성 요청은 보내지 않았습니다. 원인을 해결한 후 해당 시트만 다시 요청하세요.");}
            else {
                context.targetSheetId=targetId;context.roundOffset=index*settings.rounds();
                try {
                    context.checkpoint();
                    db.update("UPDATE ap_ai_run SET phase=?,updated_at=? WHERE id=? AND status='RUNNING'","사진 "+(index+1)+" / "+targets.size()+" · "+target.get("name"),now(),context.id);
                    var source=assets.readForAi(actor,assetId,context.business);item.put("sourceName",source.name());
                    var input=new OpenAiResponses.Source(source.name(),source.mediaType(),source.data());AiImageChecks.validate(input);
                    var result=provider.run("EXCEL",request.get("prompt")+"\n이번 요청은 사진 한 장을 지정된 시트 ‘"+target.get("name")+"’에 반영하는 제안입니다. 다른 시트를 합치지 마세요.",null,List.of(input),context.deadline,context);
                    item.put("proposal",result.proposal());item.put("status",Boolean.TRUE.equals(result.proposal().get("ready"))&&result.proposal().get("sheet")!=null?"READY":"NEEDS_INPUT");tools.addAll(result.tools());
                } catch(OpenAiResponses.Failure failure) {
                    item.put("status","FAILED");item.put("errorCode",failure.code());item.put("message",failure.getMessage());item.put("diagnostic",failure.diagnostic());
                    stop=Set.of("AI_PROVIDER_INVALID_KEY","AI_PROVIDER_AUTH","AI_PROVIDER_PERMISSION","AI_MODEL_UNAVAILABLE","AI_PROVIDER_BILLING","AI_PROVIDER_RATE_LIMIT","AI_PROVIDER_LIMIT","AI_JOB_TIMEOUT","AI_NETWORK","AI_PROVIDER_UNAVAILABLE","AI_PROVIDER_TIMEOUT","AI_INTERRUPTED","AI_PROVIDER_POLICY").contains(failure.code());
                } catch(Problem failure) {
                    if(failure.status()==401||failure.status()==403||failure.status()==409)throw failure;
                    item.put("status","FAILED");item.put("errorCode",failure.code());item.put("message",failure.getMessage());
                    stop=failure.status()==429||failure.status()>=500;
                }
            }
            results.add(item);
            db.update("UPDATE ap_ai_run SET result_json=?,updated_at=? WHERE id=? AND status='RUNNING'",
                    json.write(Map.of("sheetResults",results,"toolNames",tools)),now(),context.id);
        }
        context.checkpoint();
        boolean review=results.stream().anyMatch(r->r.containsKey("proposal"));
        access.business(actor,context.business,false,List.of("workspace.ai.run"),()->{
            context.checkpoint();int changed=db.update("UPDATE ap_ai_run SET status=?,phase=?,safe_error_code=?,safe_message=?,updated_at=? WHERE id=? AND status='RUNNING'",
                    review?"READY":"FAILED",review?"사진별 시트 제안 검토":"사진별 시트 처리 중단",review?null:"AI_BATCH_FAILED",
                    review?"시트별 결과를 검토하고 적용할 시트를 선택하세요.":"사진별 실패 원인과 해결 방법을 아래에서 확인하세요.",now(),context.id);
            if(changed==1)access.event(Inputs.number(run,"workspace_id"),context.business,review?"ai.ready":"ai.failed",context.id,0);return null;
        });
    }

    @SuppressWarnings("unchecked")
    public Map<String,Object> applyBatch(SessionSubject actor,HttpSession session,String id,BatchApply command,String header) {
        if(command==null)throw Problem.invalid("적용할 시트를 선택하세요.");SessionLease.require(session,actor);
        String requestKey=key(command.requestKey(),header);
        if(command.sheetIds()==null||command.sheetIds().isEmpty()||command.sheetIds().stream().anyMatch(Objects::isNull)||command.sheetIds().size()>3||new HashSet<>(command.sheetIds()).size()!=command.sheetIds().size())throw Problem.invalid("적용할 시트를 중복 없이 선택하세요.");
        if(!command.acceptAssumptions())throw Problem.invalid("사진 판독·추정·주의 사항을 먼저 확인하세요.");
        var first=row(id);owns(actor,first);
        String hash=Crypto.sha256(json.write(Map.of("sheets",command.sheetIds().stream().sorted().toList(),"draftVersion",command.draftVersion(),"sheetVersion",command.sheetVersion(),"accept",true)));
        return access.business(actor,Inputs.number(first,"business_scope_id"),false,List.of("workspace.ai.apply"),()->{
            SessionLease.require(session,actor);var current=row(id);owns(actor,current);requireOrigin(current,session,actor);
            if("APPLIED".equals(current.get("status"))){if(requestKey.equals(current.get("apply_key"))&&hash.equals(current.get("apply_hash")))return publicRun(current);throw Problem.conflict("이미 적용한 사진별 시트 제안입니다.");}
            if(!"READY".equals(current.get("status")))throw Problem.conflict("적용할 수 있는 사진별 시트 제안이 아닙니다.");
            String draftId=(String)current.get("draft_id");var draft=drafts.get(actor,draftId);requireActive(draft);var snapshot=sheets.get(actor,draftId);
            if(Inputs.number(draft,"version")!=command.draftVersion()||Inputs.number(current,"base_draft_version")!=command.draftVersion()
                    ||Inputs.number(snapshot,"version")!=command.sheetVersion()||Inputs.number(current,"base_sheet_version")!=command.sheetVersion())throw Problem.conflict("요청 후 프로젝트 또는 시트가 변경되었습니다. 현재 입력을 보존하고 최신 내용으로 다시 검토하세요.");
            var stored=json.object((String)current.get("result_json"));
            if(!(stored.get("sheetResults") instanceof List<?> results))throw Problem.invalid("사진별 시트 제안이 아닙니다.");
            var book=(Map<String,Object>)snapshot.get("workbook");
            for(String sheetId:command.sheetIds()) {
                var entry=results.stream().map(v->(Map<String,Object>)v).filter(v->sheetId.equals(v.get("sheetId"))).findFirst().orElseThrow(()->Problem.invalid("요청에 포함되지 않은 시트입니다."));
                if(!"READY".equals(entry.get("status")))throw Problem.invalid(entry.get("sheetName")+": 판독 실패 또는 확인 질문이 남아 적용할 수 없습니다.");
                var proposal=ProposalRules.normalize(ProposalRules.object(entry.get("proposal")),"EXCEL");
                if(!Boolean.TRUE.equals(proposal.get("ready"))||proposal.get("sheet")==null)throw Problem.invalid("확인 질문을 해결한 뒤 해당 시트를 다시 요청하세요.");
                book=WorkbookRules.replace(book,sheetId,ProposalRules.object(proposal.get("sheet")));
            }
            var changed=drafts.edit(actor,draftId,new DraftService.Edit((String)draft.get("title"),(Map<String,String>)(Map<?,?>)draft.get("answers"),(List<String>)draft.get("assetIds"),command.draftVersion()));
            var sheet=sheets.applyAiWorkbook(actor,draftId,book,command.sheetVersion(),Inputs.number(changed,"version"));
            requireOrigin(current,session,actor);
            db.update("UPDATE ap_ai_run SET status='APPLIED',phase='사진별 시트 적용 완료',apply_key=?,apply_hash=?,applied_json=?,updated_at=? WHERE id=? AND status='READY'",
                    requestKey,hash,json.write(Map.of("draftVersion",changed.get("version"),"sheetVersion",sheet.get("version"),"sheetIds",command.sheetIds())),now(),id);
            access.event(Inputs.number(current,"workspace_id"),Inputs.number(current,"business_scope_id"),"ai.applied",id,Inputs.number(changed,"version"));return publicRun(row(id));
        });
    }

    private void fail(String id, String code, String message) {
        fail(id, code, message, Map.of());
    }

    private void fail(String id, String code, String message, Map<String, Object> diagnostic) {
        try {
            var kept=new LinkedHashMap<String,Object>();var existing=row(id);
            if(existing.get("result_json") instanceof String stored)kept.putAll(json.object(stored));kept.put("diagnostic",diagnostic);
            int changed =
                    db.update(
                            "UPDATE ap_ai_run SET"
                                + " status='FAILED',phase='중단됨',safe_error_code=?,safe_message=?,result_json=?,updated_at=?"
                                + " WHERE id=? AND status IN ('QUEUED','RUNNING')",
                            code,
                            message,
                            json.write(kept),
                            now(),
                            id);
            if (changed > 0) {
                log.warn("AI job failed runId={} code={} diagnostic={}", id, code, diagnostic);
                var r = row(id);
                access.event(
                        Inputs.number(r, "workspace_id"),
                        Inputs.number(r, "business_scope_id"),
                        "ai.failed",
                        id,
                        0);
            }
        } catch (RuntimeException ignored) {
            /* Durable lease expiry will safely classify this job later. */
        }
    }

    @Scheduled(fixedDelay = 30000)
    public void expireAbandoned() {
        try {
            db.update(
                    "UPDATE ap_ai_run SET status='FAILED',phase='제한 시간 또는 서버 재시작으로"
                            + " 중단',safe_error_code='AI_LEASE_EXPIRED',safe_message=?,updated_at=?"
                            + " WHERE status IN ('QUEUED','RUNNING') AND lease_until<=?",
                    "작업 기한이 지났거나 서버가 다시 시작되었습니다. 원래 세션 없이 자동으로 유료 요청을 재시도하지 않습니다.",
                    now(),
                    now());
        } catch (RuntimeException ignored) {
            /* Schema startup checker reports missing V003 explicitly. */
        }
    }
}

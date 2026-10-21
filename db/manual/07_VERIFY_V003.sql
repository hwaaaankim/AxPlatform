-- READ ONLY. Run after 06_V003__ai_and_sheet.sql, before starting v0.3.0.
-- Does not output API keys, prompts, raw files, model results or password hashes.
USE `AxPlatform`;
SET NAMES utf8mb4 COLLATE utf8mb4_unicode_ci;

SELECT version,description,applied_at
FROM ap_schema_migration WHERE version IN ('V001','V002','V003') ORDER BY version;
SELECT CASE WHEN COUNT(*)=3 THEN 'READY' ELSE 'INCOMPLETE: V001/V002/V003 marker missing' END AS migrations
FROM ap_schema_migration WHERE version IN ('V001','V002','V003');
SELECT COUNT(*) AS ap_table_count_expected_33_on_unmodified_v030
FROM information_schema.TABLES
WHERE TABLE_SCHEMA=DATABASE() AND TABLE_TYPE='BASE TABLE' AND LEFT(TABLE_NAME,3)='ap_';
SELECT TABLE_NAME,ENGINE,TABLE_COLLATION
FROM information_schema.TABLES
WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME IN ('ap_ai_run','ap_sheet') ORDER BY TABLE_NAME;

-- 26 + 8 = 34 required columns. Both must report their expected count.
SELECT 'ap_ai_run' AS table_name,26 AS expected_columns,COUNT(*) AS found_columns,
 CASE WHEN COUNT(*)=26 THEN 'READY' ELSE 'INCOMPLETE' END AS required_columns
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='ap_ai_run' AND COLUMN_NAME IN ('id','account_id','workspace_id','business_scope_id','draft_id','request_key','request_hash','base_draft_version','base_sheet_version','request_json','result_json','status','phase','model','reasoning_effort','input_tokens','output_tokens','safe_error_code','safe_message','apply_key','apply_hash','applied_json','worker_owner','lease_until','created_at','updated_at');
SELECT 'ap_sheet' AS table_name,8 AS expected_columns,COUNT(*) AS found_columns,
 CASE WHEN COUNT(*)=8 THEN 'READY' ELSE 'INCOMPLETE' END AS required_columns
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='ap_sheet' AND COLUMN_NAME IN ('draft_id','workspace_id','business_scope_id','content_json','previous_json','version','created_at','updated_at');

-- Required types, lengths, nullability, PK and indexes are checked by the final
-- marker as well. Inspect these definitions if any READY check is incomplete.
SELECT TABLE_NAME,COLUMN_NAME,DATA_TYPE,CHARACTER_MAXIMUM_LENGTH,IS_NULLABLE,DATETIME_PRECISION
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME IN ('ap_ai_run','ap_sheet')
ORDER BY TABLE_NAME,ORDINAL_POSITION;

-- Two exact case-sensitive idempotency keys. Each collation must be utf8mb4_bin.
SELECT TABLE_NAME,COLUMN_NAME,DATA_TYPE,IS_NULLABLE,COLLATION_NAME
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='ap_ai_run'
  AND COLUMN_NAME IN ('request_key','apply_key') ORDER BY COLUMN_NAME;

-- Three FK constraints; five listed columns. No VARCHAR draft/asset FK is introduced.
SELECT TABLE_NAME,CONSTRAINT_NAME,COLUMN_NAME,REFERENCED_TABLE_NAME,REFERENCED_COLUMN_NAME
FROM information_schema.KEY_COLUMN_USAGE
WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME IN ('ap_ai_run','ap_sheet')
  AND REFERENCED_TABLE_NAME IS NOT NULL
ORDER BY TABLE_NAME,CONSTRAINT_NAME,ORDINAL_POSITION;
SELECT TABLE_NAME,INDEX_NAME,NON_UNIQUE,SEQ_IN_INDEX,COLUMN_NAME
FROM information_schema.STATISTICS
WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME IN ('ap_ai_run','ap_sheet')
ORDER BY TABLE_NAME,INDEX_NAME,SEQ_IN_INDEX;

-- 12 new catalog actions. Admin edits of status/name/admin_only and unbound drafts
-- are preserved. A null binding is safe PREPARING/unavailable, not executable.
SELECT code,name,binding_key,status,admin_only,risky,version
FROM ap_permission_action WHERE code IN ('workspace.ai.run','workspace.ai.read','workspace.ai.apply','workspace.ai.cancel','workspace.sheet.read','workspace.sheet.update','workspace.sheet.export','workspace.sheet.schema.update','workspace.sheet.rows.create','workspace.sheet.rows.delete','workspace.sheet.cells.update','workspace.sheet.export.template') ORDER BY sort_order,code;
SELECT CASE WHEN COUNT(*)=12 THEN 'READY' ELSE 'INCOMPLETE: catalog code/binding mismatch' END AS new_action_bindings
FROM ap_permission_action WHERE code IN ('workspace.ai.run','workspace.ai.read','workspace.ai.apply','workspace.ai.cancel','workspace.sheet.read','workspace.sheet.update','workspace.sheet.export','workspace.sheet.schema.update','workspace.sheet.rows.create','workspace.sheet.rows.delete','workspace.sheet.cells.update','workspace.sheet.export.template') AND (binding_key=code OR binding_key IS NULL);

-- Zero rows expected. Missing mandatory dependency edges are listed below.
SELECT required.action_code,required.depends_on AS missing_dependency
FROM (
SELECT 'workspace.ai.run' AS action_code,'workspace.ai.read' AS depends_on
UNION ALL
SELECT 'workspace.ai.run' AS action_code,'workspace.draft.read' AS depends_on
UNION ALL
SELECT 'workspace.ai.read' AS action_code,'workspace.draft.read' AS depends_on
UNION ALL
SELECT 'workspace.ai.apply' AS action_code,'workspace.ai.read' AS depends_on
UNION ALL
SELECT 'workspace.ai.apply' AS action_code,'workspace.draft.update' AS depends_on
UNION ALL
SELECT 'workspace.ai.cancel' AS action_code,'workspace.ai.read' AS depends_on
UNION ALL
SELECT 'workspace.sheet.read' AS action_code,'workspace.draft.read' AS depends_on
UNION ALL
SELECT 'workspace.sheet.update' AS action_code,'workspace.sheet.read' AS depends_on
UNION ALL
SELECT 'workspace.sheet.update' AS action_code,'workspace.draft.update' AS depends_on
UNION ALL
SELECT 'workspace.sheet.export' AS action_code,'workspace.sheet.read' AS depends_on
UNION ALL
SELECT 'workspace.sheet.schema.update' AS action_code,'workspace.sheet.update' AS depends_on
UNION ALL
SELECT 'workspace.sheet.rows.create' AS action_code,'workspace.sheet.update' AS depends_on
UNION ALL
SELECT 'workspace.sheet.rows.delete' AS action_code,'workspace.sheet.update' AS depends_on
UNION ALL
SELECT 'workspace.sheet.cells.update' AS action_code,'workspace.sheet.update' AS depends_on
UNION ALL
SELECT 'workspace.sheet.export.template' AS action_code,'workspace.sheet.read' AS depends_on
) AS required
LEFT JOIN ap_permission_dependency actual
  ON actual.action_code=required.action_code AND actual.depends_on=required.depends_on
WHERE actual.action_code IS NULL ORDER BY required.action_code,required.depends_on;

-- Migration does not assign new tier grants or create a policy version.
-- Before the admin assigns new actions, the query below normally returns zero rows.
-- After explicit assignment its rows are expected; no fixed zero-grant assertion is made.
SELECT g.tier_code,g.action_code
FROM ap_policy_grant g
WHERE g.policy_version_id=(SELECT active_policy_id FROM ap_policy_state WHERE id=1)
  AND g.action_code IN ('workspace.ai.run','workspace.ai.read','workspace.ai.apply','workspace.ai.cancel','workspace.sheet.read','workspace.sheet.update','workspace.sheet.export','workspace.sheet.schema.update','workspace.sheet.rows.create','workspace.sheet.rows.delete','workspace.sheet.cells.update','workspace.sheet.export.template')
ORDER BY g.tier_code,g.sort_order,g.action_code;
SELECT id,revision,active_policy_id FROM ap_policy_state WHERE id=1;
SELECT COUNT(*) AS stored_ai_run_count FROM ap_ai_run;
SELECT COUNT(*) AS stored_excel_sheet_count FROM ap_sheet;

-- AxPlatform v0.3.0 / V003: AI reviewed proposals and validated Excel tables.
-- HeidiSQL: stop AxPlatform, select this whole file, then execute all statements as DBA.
-- Existing V001 + V002 (31 tables) are required. Run this file only once per upgrade;
-- re-running it preserves existing actions, policies, accounts, data and request history.
-- No policy grants are added: customers need explicit admin assignment of these new actions.
-- DDL causes implicit commits in MariaDB. A final marker is inserted only after required
-- tables, columns, bindings and dependencies exist. Stop at the first SQL error.
-- New tables use BIGINT business-scope FKs; no VARCHAR FK to old V002 draft IDs.
USE `AxPlatform`;
SET NAMES utf8mb4 COLLATE utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS ap_ai_run (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    account_id BIGINT NOT NULL,
    workspace_id BIGINT NOT NULL,
    business_scope_id BIGINT NOT NULL,
    draft_id VARCHAR(36) NOT NULL,
    request_key VARCHAR(100) COLLATE utf8mb4_bin NOT NULL,
    request_hash VARCHAR(64) NOT NULL,
    base_draft_version BIGINT NOT NULL,
    base_sheet_version BIGINT NOT NULL DEFAULT -1,
    request_json MEDIUMTEXT NOT NULL,
    result_json MEDIUMTEXT NULL,
    status VARCHAR(16) NOT NULL,
    phase VARCHAR(100) NOT NULL,
    model VARCHAR(100) NOT NULL,
    reasoning_effort VARCHAR(16) NOT NULL,
    input_tokens BIGINT NOT NULL DEFAULT 0,
    output_tokens BIGINT NOT NULL DEFAULT 0,
    safe_error_code VARCHAR(80) NULL,
    safe_message VARCHAR(500) NULL,
    apply_key VARCHAR(100) COLLATE utf8mb4_bin NULL,
    apply_hash VARCHAR(64) NULL,
    applied_json TEXT NULL,
    worker_owner VARCHAR(36) NULL,
    lease_until DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    CONSTRAINT uq_ai_account_request UNIQUE (account_id,request_key),
    CONSTRAINT fk_ai_account FOREIGN KEY (account_id) REFERENCES ap_account(id),
    CONSTRAINT fk_ai_business FOREIGN KEY (workspace_id,business_scope_id)
        REFERENCES ap_business_scope(workspace_id,id),
    CONSTRAINT ck_ai_status CHECK (status IN ('QUEUED','RUNNING','READY','APPLIED','FAILED','CANCELLED')),
    INDEX ix_ai_draft_created (draft_id,created_at),
    INDEX ix_ai_status_lease (status,lease_until)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS ap_sheet (
    draft_id VARCHAR(36) NOT NULL PRIMARY KEY,
    workspace_id BIGINT NOT NULL,
    business_scope_id BIGINT NOT NULL,
    content_json MEDIUMTEXT NOT NULL,
    previous_json MEDIUMTEXT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    CONSTRAINT fk_sheet_business FOREIGN KEY (workspace_id,business_scope_id)
        REFERENCES ap_business_scope(workspace_id,id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Parent group is already present in V002. Existing group state/name/order are preserved.
INSERT INTO ap_feature_group(code,name,status,sort_order)
SELECT 'workspace','공통 업무 기반','ACTIVE',0
WHERE NOT EXISTS(SELECT 1 FROM ap_feature_group WHERE code='workspace');

-- Immutable codes and binding keys. Existing administrator edits are not overwritten.
INSERT INTO ap_permission_action(code,group_code,name,description,binding_key,status,admin_only,risky,sort_order,version,created_at,updated_at)
SELECT 'workspace.ai.run','workspace','AI 제안 만들기','현재 업무와 선택 자료로 AI 작업을 시작합니다. 사진·텍스트 자료는 별도 다운로드 권한도 필요합니다.','workspace.ai.run','ACTIVE',FALSE,TRUE,21,0,UTC_TIMESTAMP(6),UTC_TIMESTAMP(6)
WHERE NOT EXISTS(SELECT 1 FROM ap_permission_action WHERE code='workspace.ai.run');
INSERT INTO ap_permission_action(code,group_code,name,description,binding_key,status,admin_only,risky,sort_order,version,created_at,updated_at)
SELECT 'workspace.ai.read','workspace','AI 제안 조회','본인 사업과 업무에 속한 AI 진행 상태·제안만 조회합니다.','workspace.ai.read','ACTIVE',FALSE,FALSE,22,0,UTC_TIMESTAMP(6),UTC_TIMESTAMP(6)
WHERE NOT EXISTS(SELECT 1 FROM ap_permission_action WHERE code='workspace.ai.read');
INSERT INTO ap_permission_action(code,group_code,name,description,binding_key,status,admin_only,risky,sort_order,version,created_at,updated_at)
SELECT 'workspace.ai.apply','workspace','AI 제안 선택 반영','선택한 제안을 현재 초안에 반영합니다. 실제 표·셀 변경 권한을 함께 검사합니다.','workspace.ai.apply','ACTIVE',FALSE,TRUE,23,0,UTC_TIMESTAMP(6),UTC_TIMESTAMP(6)
WHERE NOT EXISTS(SELECT 1 FROM ap_permission_action WHERE code='workspace.ai.apply');
INSERT INTO ap_permission_action(code,group_code,name,description,binding_key,status,admin_only,risky,sort_order,version,created_at,updated_at)
SELECT 'workspace.ai.cancel','workspace','AI 작업·제안 취소','실행 중 AI 작업을 취소하거나 미반영 제안을 취소합니다. 반영된 자료를 되돌리는 권한이 아닙니다.','workspace.ai.cancel','ACTIVE',FALSE,FALSE,24,0,UTC_TIMESTAMP(6),UTC_TIMESTAMP(6)
WHERE NOT EXISTS(SELECT 1 FROM ap_permission_action WHERE code='workspace.ai.cancel');
INSERT INTO ap_permission_action(code,group_code,name,description,binding_key,status,admin_only,risky,sort_order,version,created_at,updated_at)
SELECT 'workspace.sheet.read','workspace','엑셀 표 조회','EXCEL 업무의 저장된 열·행·셀을 조회합니다.','workspace.sheet.read','ACTIVE',FALSE,FALSE,25,0,UTC_TIMESTAMP(6),UTC_TIMESTAMP(6)
WHERE NOT EXISTS(SELECT 1 FROM ap_permission_action WHERE code='workspace.sheet.read');
INSERT INTO ap_permission_action(code,group_code,name,description,binding_key,status,admin_only,risky,sort_order,version,created_at,updated_at)
SELECT 'workspace.sheet.update','workspace','엑셀 표 변경 적용','표 변경의 저장 진입 권한입니다. 열·행·셀 각각의 실제 변경 권한이 추가로 필요합니다.','workspace.sheet.update','ACTIVE',FALSE,TRUE,26,0,UTC_TIMESTAMP(6),UTC_TIMESTAMP(6)
WHERE NOT EXISTS(SELECT 1 FROM ap_permission_action WHERE code='workspace.sheet.update');
INSERT INTO ap_permission_action(code,group_code,name,description,binding_key,status,admin_only,risky,sort_order,version,created_at,updated_at)
SELECT 'workspace.sheet.export','workspace','엑셀 데이터 다운로드','현재 서버 확정본의 열과 데이터를 XLSX로 다운로드합니다.','workspace.sheet.export','ACTIVE',FALSE,FALSE,27,0,UTC_TIMESTAMP(6),UTC_TIMESTAMP(6)
WHERE NOT EXISTS(SELECT 1 FROM ap_permission_action WHERE code='workspace.sheet.export');
INSERT INTO ap_permission_action(code,group_code,name,description,binding_key,status,admin_only,risky,sort_order,version,created_at,updated_at)
SELECT 'workspace.sheet.schema.update','workspace','엑셀 열 구조 수정','열 추가·삭제·이름·형식·단위·폭·순서를 수정합니다.','workspace.sheet.schema.update','ACTIVE',FALSE,TRUE,28,0,UTC_TIMESTAMP(6),UTC_TIMESTAMP(6)
WHERE NOT EXISTS(SELECT 1 FROM ap_permission_action WHERE code='workspace.sheet.schema.update');
INSERT INTO ap_permission_action(code,group_code,name,description,binding_key,status,admin_only,risky,sort_order,version,created_at,updated_at)
SELECT 'workspace.sheet.rows.create','workspace','엑셀 행 추가','기존 표에 데이터 행을 추가합니다.','workspace.sheet.rows.create','ACTIVE',FALSE,TRUE,29,0,UTC_TIMESTAMP(6),UTC_TIMESTAMP(6)
WHERE NOT EXISTS(SELECT 1 FROM ap_permission_action WHERE code='workspace.sheet.rows.create');
INSERT INTO ap_permission_action(code,group_code,name,description,binding_key,status,admin_only,risky,sort_order,version,created_at,updated_at)
SELECT 'workspace.sheet.rows.delete','workspace','엑셀 행 삭제','기존 표의 데이터 행을 삭제합니다.','workspace.sheet.rows.delete','ACTIVE',FALSE,TRUE,30,0,UTC_TIMESTAMP(6),UTC_TIMESTAMP(6)
WHERE NOT EXISTS(SELECT 1 FROM ap_permission_action WHERE code='workspace.sheet.rows.delete');
INSERT INTO ap_permission_action(code,group_code,name,description,binding_key,status,admin_only,risky,sort_order,version,created_at,updated_at)
SELECT 'workspace.sheet.cells.update','workspace','엑셀 셀 수정','기존 행의 셀 값을 수정합니다.','workspace.sheet.cells.update','ACTIVE',FALSE,TRUE,31,0,UTC_TIMESTAMP(6),UTC_TIMESTAMP(6)
WHERE NOT EXISTS(SELECT 1 FROM ap_permission_action WHERE code='workspace.sheet.cells.update');
INSERT INTO ap_permission_action(code,group_code,name,description,binding_key,status,admin_only,risky,sort_order,version,created_at,updated_at)
SELECT 'workspace.sheet.export.template','workspace','엑셀 빈 양식 다운로드','현재 서버 확정본의 열만 포함한 빈 XLSX 입력 양식을 다운로드합니다.','workspace.sheet.export.template','ACTIVE',FALSE,FALSE,32,0,UTC_TIMESTAMP(6),UTC_TIMESTAMP(6)
WHERE NOT EXISTS(SELECT 1 FROM ap_permission_action WHERE code='workspace.sheet.export.template');

-- Dependencies are server-enforced; no implied tier inheritance or grants.
INSERT INTO ap_permission_dependency(action_code,depends_on)
SELECT 'workspace.ai.run','workspace.ai.read'
WHERE NOT EXISTS(SELECT 1 FROM ap_permission_dependency WHERE action_code='workspace.ai.run' AND depends_on='workspace.ai.read');
INSERT INTO ap_permission_dependency(action_code,depends_on)
SELECT 'workspace.ai.run','workspace.draft.read'
WHERE NOT EXISTS(SELECT 1 FROM ap_permission_dependency WHERE action_code='workspace.ai.run' AND depends_on='workspace.draft.read');
INSERT INTO ap_permission_dependency(action_code,depends_on)
SELECT 'workspace.ai.read','workspace.draft.read'
WHERE NOT EXISTS(SELECT 1 FROM ap_permission_dependency WHERE action_code='workspace.ai.read' AND depends_on='workspace.draft.read');
INSERT INTO ap_permission_dependency(action_code,depends_on)
SELECT 'workspace.ai.apply','workspace.ai.read'
WHERE NOT EXISTS(SELECT 1 FROM ap_permission_dependency WHERE action_code='workspace.ai.apply' AND depends_on='workspace.ai.read');
INSERT INTO ap_permission_dependency(action_code,depends_on)
SELECT 'workspace.ai.apply','workspace.draft.update'
WHERE NOT EXISTS(SELECT 1 FROM ap_permission_dependency WHERE action_code='workspace.ai.apply' AND depends_on='workspace.draft.update');
INSERT INTO ap_permission_dependency(action_code,depends_on)
SELECT 'workspace.ai.cancel','workspace.ai.read'
WHERE NOT EXISTS(SELECT 1 FROM ap_permission_dependency WHERE action_code='workspace.ai.cancel' AND depends_on='workspace.ai.read');
INSERT INTO ap_permission_dependency(action_code,depends_on)
SELECT 'workspace.sheet.read','workspace.draft.read'
WHERE NOT EXISTS(SELECT 1 FROM ap_permission_dependency WHERE action_code='workspace.sheet.read' AND depends_on='workspace.draft.read');
INSERT INTO ap_permission_dependency(action_code,depends_on)
SELECT 'workspace.sheet.update','workspace.sheet.read'
WHERE NOT EXISTS(SELECT 1 FROM ap_permission_dependency WHERE action_code='workspace.sheet.update' AND depends_on='workspace.sheet.read');
INSERT INTO ap_permission_dependency(action_code,depends_on)
SELECT 'workspace.sheet.update','workspace.draft.update'
WHERE NOT EXISTS(SELECT 1 FROM ap_permission_dependency WHERE action_code='workspace.sheet.update' AND depends_on='workspace.draft.update');
INSERT INTO ap_permission_dependency(action_code,depends_on)
SELECT 'workspace.sheet.export','workspace.sheet.read'
WHERE NOT EXISTS(SELECT 1 FROM ap_permission_dependency WHERE action_code='workspace.sheet.export' AND depends_on='workspace.sheet.read');
INSERT INTO ap_permission_dependency(action_code,depends_on)
SELECT 'workspace.sheet.schema.update','workspace.sheet.update'
WHERE NOT EXISTS(SELECT 1 FROM ap_permission_dependency WHERE action_code='workspace.sheet.schema.update' AND depends_on='workspace.sheet.update');
INSERT INTO ap_permission_dependency(action_code,depends_on)
SELECT 'workspace.sheet.rows.create','workspace.sheet.update'
WHERE NOT EXISTS(SELECT 1 FROM ap_permission_dependency WHERE action_code='workspace.sheet.rows.create' AND depends_on='workspace.sheet.update');
INSERT INTO ap_permission_dependency(action_code,depends_on)
SELECT 'workspace.sheet.rows.delete','workspace.sheet.update'
WHERE NOT EXISTS(SELECT 1 FROM ap_permission_dependency WHERE action_code='workspace.sheet.rows.delete' AND depends_on='workspace.sheet.update');
INSERT INTO ap_permission_dependency(action_code,depends_on)
SELECT 'workspace.sheet.cells.update','workspace.sheet.update'
WHERE NOT EXISTS(SELECT 1 FROM ap_permission_dependency WHERE action_code='workspace.sheet.cells.update' AND depends_on='workspace.sheet.update');
INSERT INTO ap_permission_dependency(action_code,depends_on)
SELECT 'workspace.sheet.export.template','workspace.sheet.read'
WHERE NOT EXISTS(SELECT 1 FROM ap_permission_dependency WHERE action_code='workspace.sheet.export.template' AND depends_on='workspace.sheet.read');

-- MUST BE LAST WRITE: a marker is not proof of a successful paid API/runtime test.
-- A zero-row INSERT means installation is incomplete; run 07_VERIFY_V003.sql.
INSERT INTO ap_schema_migration(version,description,applied_at)
SELECT 'V003','v0.3 AI reviewed proposals and scoped Excel tables',UTC_TIMESTAMP(6)
WHERE NOT EXISTS(SELECT 1 FROM ap_schema_migration WHERE version='V003')
  AND (SELECT COUNT(*) FROM ap_schema_migration WHERE version IN ('V001','V002'))=2
  AND (SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_TYPE='BASE TABLE' AND TABLE_NAME IN ('ap_ai_run','ap_sheet') AND ENGINE='InnoDB' AND TABLE_COLLATION='utf8mb4_unicode_ci')=2
  AND (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='ap_ai_run' AND COLUMN_NAME IN ('id','account_id','workspace_id','business_scope_id','draft_id','request_key','request_hash','base_draft_version','base_sheet_version','request_json','result_json','status','phase','model','reasoning_effort','input_tokens','output_tokens','safe_error_code','safe_message','apply_key','apply_hash','applied_json','worker_owner','lease_until','created_at','updated_at'))=26
  AND (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='ap_sheet' AND COLUMN_NAME IN ('draft_id','workspace_id','business_scope_id','content_json','previous_json','version','created_at','updated_at'))=8
  AND (SELECT COUNT(*) FROM information_schema.KEY_COLUMN_USAGE WHERE TABLE_SCHEMA=DATABASE() AND ((TABLE_NAME='ap_ai_run' AND CONSTRAINT_NAME='fk_ai_account' AND COLUMN_NAME='account_id' AND REFERENCED_TABLE_NAME='ap_account' AND REFERENCED_COLUMN_NAME='id') OR (TABLE_NAME='ap_ai_run' AND CONSTRAINT_NAME='fk_ai_business' AND ((COLUMN_NAME='workspace_id' AND REFERENCED_COLUMN_NAME='workspace_id') OR (COLUMN_NAME='business_scope_id' AND REFERENCED_COLUMN_NAME='id')) AND REFERENCED_TABLE_NAME='ap_business_scope') OR (TABLE_NAME='ap_sheet' AND CONSTRAINT_NAME='fk_sheet_business' AND ((COLUMN_NAME='workspace_id' AND REFERENCED_COLUMN_NAME='workspace_id') OR (COLUMN_NAME='business_scope_id' AND REFERENCED_COLUMN_NAME='id')) AND REFERENCED_TABLE_NAME='ap_business_scope')))=5
  AND (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='ap_ai_run' AND INDEX_NAME='uq_ai_account_request')=2
  AND (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='ap_ai_run' AND INDEX_NAME='uq_ai_account_request' AND NON_UNIQUE=0 AND ((SEQ_IN_INDEX=1 AND COLUMN_NAME='account_id') OR (SEQ_IN_INDEX=2 AND COLUMN_NAME='request_key')))=2
  AND (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='ap_ai_run' AND INDEX_NAME='PRIMARY')=1
  AND EXISTS(SELECT 1 FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='ap_ai_run' AND INDEX_NAME='PRIMARY' AND NON_UNIQUE=0 AND SEQ_IN_INDEX=1 AND COLUMN_NAME='id')
  AND (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='ap_sheet' AND INDEX_NAME='PRIMARY')=1
  AND EXISTS(SELECT 1 FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='ap_sheet' AND INDEX_NAME='PRIMARY' AND NON_UNIQUE=0 AND SEQ_IN_INDEX=1 AND COLUMN_NAME='draft_id')
  AND (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='ap_ai_run' AND COLUMN_NAME IN ('request_key','apply_key') AND COLLATION_NAME='utf8mb4_bin')=2
  AND (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='ap_ai_run' AND (
      (COLUMN_NAME='id' AND DATA_TYPE='varchar' AND IS_NULLABLE='NO' AND CHARACTER_MAXIMUM_LENGTH=36)
      OR (COLUMN_NAME='account_id' AND DATA_TYPE='bigint' AND IS_NULLABLE='NO')
      OR (COLUMN_NAME='workspace_id' AND DATA_TYPE='bigint' AND IS_NULLABLE='NO')
      OR (COLUMN_NAME='business_scope_id' AND DATA_TYPE='bigint' AND IS_NULLABLE='NO')
      OR (COLUMN_NAME='draft_id' AND DATA_TYPE='varchar' AND IS_NULLABLE='NO' AND CHARACTER_MAXIMUM_LENGTH=36)
      OR (COLUMN_NAME='request_key' AND DATA_TYPE='varchar' AND IS_NULLABLE='NO' AND CHARACTER_MAXIMUM_LENGTH=100)
      OR (COLUMN_NAME='request_hash' AND DATA_TYPE='varchar' AND IS_NULLABLE='NO' AND CHARACTER_MAXIMUM_LENGTH=64)
      OR (COLUMN_NAME='base_draft_version' AND DATA_TYPE='bigint' AND IS_NULLABLE='NO')
      OR (COLUMN_NAME='base_sheet_version' AND DATA_TYPE='bigint' AND IS_NULLABLE='NO')
      OR (COLUMN_NAME='request_json' AND DATA_TYPE='mediumtext' AND IS_NULLABLE='NO')
      OR (COLUMN_NAME='result_json' AND DATA_TYPE='mediumtext' AND IS_NULLABLE='YES')
      OR (COLUMN_NAME='status' AND DATA_TYPE='varchar' AND IS_NULLABLE='NO' AND CHARACTER_MAXIMUM_LENGTH=16)
      OR (COLUMN_NAME='phase' AND DATA_TYPE='varchar' AND IS_NULLABLE='NO' AND CHARACTER_MAXIMUM_LENGTH=100)
      OR (COLUMN_NAME='model' AND DATA_TYPE='varchar' AND IS_NULLABLE='NO' AND CHARACTER_MAXIMUM_LENGTH=100)
      OR (COLUMN_NAME='reasoning_effort' AND DATA_TYPE='varchar' AND IS_NULLABLE='NO' AND CHARACTER_MAXIMUM_LENGTH=16)
      OR (COLUMN_NAME='input_tokens' AND DATA_TYPE='bigint' AND IS_NULLABLE='NO')
      OR (COLUMN_NAME='output_tokens' AND DATA_TYPE='bigint' AND IS_NULLABLE='NO')
      OR (COLUMN_NAME='safe_error_code' AND DATA_TYPE='varchar' AND IS_NULLABLE='YES' AND CHARACTER_MAXIMUM_LENGTH=80)
      OR (COLUMN_NAME='safe_message' AND DATA_TYPE='varchar' AND IS_NULLABLE='YES' AND CHARACTER_MAXIMUM_LENGTH=500)
      OR (COLUMN_NAME='apply_key' AND DATA_TYPE='varchar' AND IS_NULLABLE='YES' AND CHARACTER_MAXIMUM_LENGTH=100)
      OR (COLUMN_NAME='apply_hash' AND DATA_TYPE='varchar' AND IS_NULLABLE='YES' AND CHARACTER_MAXIMUM_LENGTH=64)
      OR (COLUMN_NAME='applied_json' AND DATA_TYPE='text' AND IS_NULLABLE='YES')
      OR (COLUMN_NAME='worker_owner' AND DATA_TYPE='varchar' AND IS_NULLABLE='YES' AND CHARACTER_MAXIMUM_LENGTH=36)
      OR (COLUMN_NAME='lease_until' AND DATA_TYPE='datetime' AND IS_NULLABLE='YES' AND DATETIME_PRECISION=6)
      OR (COLUMN_NAME='created_at' AND DATA_TYPE='datetime' AND IS_NULLABLE='NO' AND DATETIME_PRECISION=6)
      OR (COLUMN_NAME='updated_at' AND DATA_TYPE='datetime' AND IS_NULLABLE='NO' AND DATETIME_PRECISION=6)))=26
  AND (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='ap_sheet' AND (
      (COLUMN_NAME='draft_id' AND DATA_TYPE='varchar' AND IS_NULLABLE='NO' AND CHARACTER_MAXIMUM_LENGTH=36)
      OR (COLUMN_NAME='workspace_id' AND DATA_TYPE='bigint' AND IS_NULLABLE='NO')
      OR (COLUMN_NAME='business_scope_id' AND DATA_TYPE='bigint' AND IS_NULLABLE='NO')
      OR (COLUMN_NAME='content_json' AND DATA_TYPE='mediumtext' AND IS_NULLABLE='NO')
      OR (COLUMN_NAME='previous_json' AND DATA_TYPE='mediumtext' AND IS_NULLABLE='YES')
      OR (COLUMN_NAME='version' AND DATA_TYPE='bigint' AND IS_NULLABLE='NO')
      OR (COLUMN_NAME='created_at' AND DATA_TYPE='datetime' AND IS_NULLABLE='NO' AND DATETIME_PRECISION=6)
      OR (COLUMN_NAME='updated_at' AND DATA_TYPE='datetime' AND IS_NULLABLE='NO' AND DATETIME_PRECISION=6)))=8
  AND (SELECT COUNT(*) FROM ap_permission_action WHERE code IN ('workspace.ai.run','workspace.ai.read','workspace.ai.apply','workspace.ai.cancel','workspace.sheet.read','workspace.sheet.update','workspace.sheet.export','workspace.sheet.schema.update','workspace.sheet.rows.create','workspace.sheet.rows.delete','workspace.sheet.cells.update','workspace.sheet.export.template') AND (binding_key=code OR binding_key IS NULL))=12
  AND EXISTS(SELECT 1 FROM ap_permission_dependency WHERE action_code='workspace.ai.run' AND depends_on='workspace.ai.read')
  AND EXISTS(SELECT 1 FROM ap_permission_dependency WHERE action_code='workspace.ai.run' AND depends_on='workspace.draft.read')
  AND EXISTS(SELECT 1 FROM ap_permission_dependency WHERE action_code='workspace.ai.read' AND depends_on='workspace.draft.read')
  AND EXISTS(SELECT 1 FROM ap_permission_dependency WHERE action_code='workspace.ai.apply' AND depends_on='workspace.ai.read')
  AND EXISTS(SELECT 1 FROM ap_permission_dependency WHERE action_code='workspace.ai.apply' AND depends_on='workspace.draft.update')
  AND EXISTS(SELECT 1 FROM ap_permission_dependency WHERE action_code='workspace.ai.cancel' AND depends_on='workspace.ai.read')
  AND EXISTS(SELECT 1 FROM ap_permission_dependency WHERE action_code='workspace.sheet.read' AND depends_on='workspace.draft.read')
  AND EXISTS(SELECT 1 FROM ap_permission_dependency WHERE action_code='workspace.sheet.update' AND depends_on='workspace.sheet.read')
  AND EXISTS(SELECT 1 FROM ap_permission_dependency WHERE action_code='workspace.sheet.update' AND depends_on='workspace.draft.update')
  AND EXISTS(SELECT 1 FROM ap_permission_dependency WHERE action_code='workspace.sheet.export' AND depends_on='workspace.sheet.read')
  AND EXISTS(SELECT 1 FROM ap_permission_dependency WHERE action_code='workspace.sheet.schema.update' AND depends_on='workspace.sheet.update')
  AND EXISTS(SELECT 1 FROM ap_permission_dependency WHERE action_code='workspace.sheet.rows.create' AND depends_on='workspace.sheet.update')
  AND EXISTS(SELECT 1 FROM ap_permission_dependency WHERE action_code='workspace.sheet.rows.delete' AND depends_on='workspace.sheet.update')
  AND EXISTS(SELECT 1 FROM ap_permission_dependency WHERE action_code='workspace.sheet.cells.update' AND depends_on='workspace.sheet.update')
  AND EXISTS(SELECT 1 FROM ap_permission_dependency WHERE action_code='workspace.sheet.export.template' AND depends_on='workspace.sheet.read');

-- AxPlatform 0.4.0: V001~V003 완료 환경에서 앱 중지 후 전체 실행.
-- 기존 계정/사업/표/정책 보존. 신규 권한은 고객 등급에 자동 배정하지 않습니다.
-- MariaDB DDL은 암묵 커밋됩니다. 첫 오류에서 멈추고 확인하세요.
USE `AxPlatform`;
SET NAMES utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE TABLE IF NOT EXISTS ap_sheet_view (
 id VARCHAR(36) NOT NULL PRIMARY KEY,
 draft_id VARCHAR(36) NOT NULL,
 name VARCHAR(100) NOT NULL,
 config_json TEXT NOT NULL,
 version BIGINT NOT NULL DEFAULT 0,
 created_at DATETIME(6) NOT NULL,
 updated_at DATETIME(6) NOT NULL,
 INDEX ix_sheet_view_draft(draft_id,updated_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
INSERT INTO ap_permission_action(code,group_code,name,description,binding_key,status,admin_only,risky,sort_order,version,created_at,updated_at)
SELECT 'workspace.sheet.import','workspace','엑셀 파일 미리보기','XLSX·CSV를 저장 전 미리보고 열을 연결합니다.','workspace.sheet.import','ACTIVE',FALSE,FALSE,40,0,UTC_TIMESTAMP(6),UTC_TIMESTAMP(6)
WHERE NOT EXISTS(SELECT 1 FROM ap_permission_action WHERE code='workspace.sheet.import');
INSERT INTO ap_permission_dependency(action_code,depends_on)
SELECT 'workspace.sheet.import','workspace.sheet.update' WHERE NOT EXISTS(SELECT 1 FROM ap_permission_dependency WHERE action_code='workspace.sheet.import' AND depends_on='workspace.sheet.update');
INSERT INTO ap_permission_action(code,group_code,name,description,binding_key,status,admin_only,risky,sort_order,version,created_at,updated_at)
SELECT 'workspace.sheet.formula.update','workspace','엑셀 계산 열 설정','허용된 사칙연산 계산 열을 추가·수정·제거합니다.','workspace.sheet.formula.update','ACTIVE',FALSE,FALSE,41,0,UTC_TIMESTAMP(6),UTC_TIMESTAMP(6)
WHERE NOT EXISTS(SELECT 1 FROM ap_permission_action WHERE code='workspace.sheet.formula.update');
INSERT INTO ap_permission_dependency(action_code,depends_on)
SELECT 'workspace.sheet.formula.update','workspace.sheet.schema.update' WHERE NOT EXISTS(SELECT 1 FROM ap_permission_dependency WHERE action_code='workspace.sheet.formula.update' AND depends_on='workspace.sheet.schema.update');
INSERT INTO ap_permission_action(code,group_code,name,description,binding_key,status,admin_only,risky,sort_order,version,created_at,updated_at)
SELECT 'workspace.sheet.analysis.read','workspace','엑셀 분석 조회','서버 저장본의 집계와 저장한 분석 보기를 조회합니다.','workspace.sheet.analysis.read','ACTIVE',FALSE,FALSE,42,0,UTC_TIMESTAMP(6),UTC_TIMESTAMP(6)
WHERE NOT EXISTS(SELECT 1 FROM ap_permission_action WHERE code='workspace.sheet.analysis.read');
INSERT INTO ap_permission_dependency(action_code,depends_on)
SELECT 'workspace.sheet.analysis.read','workspace.sheet.read' WHERE NOT EXISTS(SELECT 1 FROM ap_permission_dependency WHERE action_code='workspace.sheet.analysis.read' AND depends_on='workspace.sheet.read');
INSERT INTO ap_permission_action(code,group_code,name,description,binding_key,status,admin_only,risky,sort_order,version,created_at,updated_at)
SELECT 'workspace.sheet.analysis.manage','workspace','엑셀 분석 보기 관리','분석 조건을 저장·수정·삭제합니다. 원본 표는 변경하지 않습니다.','workspace.sheet.analysis.manage','ACTIVE',FALSE,FALSE,43,0,UTC_TIMESTAMP(6),UTC_TIMESTAMP(6)
WHERE NOT EXISTS(SELECT 1 FROM ap_permission_action WHERE code='workspace.sheet.analysis.manage');
INSERT INTO ap_permission_dependency(action_code,depends_on)
SELECT 'workspace.sheet.analysis.manage','workspace.sheet.analysis.read' WHERE NOT EXISTS(SELECT 1 FROM ap_permission_dependency WHERE action_code='workspace.sheet.analysis.manage' AND depends_on='workspace.sheet.analysis.read');
INSERT INTO ap_permission_dependency(action_code,depends_on)
SELECT 'workspace.sheet.formula.update','workspace.sheet.cells.update'
WHERE NOT EXISTS(SELECT 1 FROM ap_permission_dependency WHERE action_code='workspace.sheet.formula.update' AND depends_on='workspace.sheet.cells.update');

INSERT INTO ap_schema_migration(version,description,applied_at)
SELECT 'V004','Guided workspace and saved sheet analysis',UTC_TIMESTAMP(6)
WHERE NOT EXISTS(SELECT 1 FROM ap_schema_migration WHERE version='V004')
 AND EXISTS(SELECT 1 FROM ap_schema_migration WHERE version='V003')
 AND (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='ap_sheet_view' AND column_name IN ('id','draft_id','name','config_json','version','created_at','updated_at'))=7
 AND (SELECT COUNT(*) FROM ap_permission_action WHERE code IN ('workspace.sheet.import','workspace.sheet.formula.update','workspace.sheet.analysis.read','workspace.sheet.analysis.manage'))=4;

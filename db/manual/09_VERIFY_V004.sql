USE `AxPlatform`;
SELECT version,description,applied_at FROM ap_schema_migration ORDER BY version;
SHOW COLUMNS FROM ap_sheet_view;
SELECT code,name,status,binding_key FROM ap_permission_action
WHERE code IN ('workspace.sheet.import','workspace.sheet.formula.update','workspace.sheet.analysis.read','workspace.sheet.analysis.manage');
SELECT action_code,depends_on FROM ap_permission_dependency
WHERE action_code IN ('workspace.sheet.import','workspace.sheet.formula.update','workspace.sheet.analysis.read','workspace.sheet.analysis.manage');
-- 새 버전은 총 34개 기본 테이블. 현재 등급 배정은 관리자 권한관리에서 확인하세요.
SELECT COUNT(*) AS table_count FROM information_schema.tables WHERE table_schema=DATABASE() AND table_type='BASE TABLE';

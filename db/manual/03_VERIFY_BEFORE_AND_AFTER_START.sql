-- Read only. Before application start the administrator count is expected to be zero.
USE `AxPlatform`;
SELECT VERSION() AS db_version, DATABASE() AS current_database;
SELECT version,description,applied_at FROM ap_schema_migration ORDER BY version;
SELECT table_name FROM information_schema.tables
WHERE table_schema=DATABASE() AND table_name LIKE 'ap\_%' ORDER BY table_name;
SELECT name,completed_at,account_id FROM ap_bootstrap;
SELECT COUNT(*) AS admin_count FROM ap_admin_credential WHERE username='admin';
SELECT a.id,a.account_type,a.system_role,a.status,c.username,c.security_version,
       CASE WHEN c.password_hash LIKE '$2a$%' OR c.password_hash LIKE '$2b$%'
            OR c.password_hash LIKE '$2y$%' THEN 'BCRYPT_HASH' ELSE 'CHECK_REQUIRED' END AS password_storage
FROM ap_account a JOIN ap_admin_credential c ON c.account_id=a.id;
SELECT w.id,w.name,w.kind,w.plan_code,b.name AS business_name
FROM ap_workspace w JOIN ap_business_scope b ON b.workspace_id=w.id;
SELECT code,name,plan_code FROM ap_access_tier ORDER BY sort_order;

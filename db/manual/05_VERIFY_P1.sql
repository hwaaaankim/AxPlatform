-- READ ONLY. Run in HeidiSQL after V002. Initial counts change when catalog/policy is edited.
USE `AxPlatform`;
SELECT version,description,applied_at FROM ap_schema_migration WHERE version IN ('V001','V002') ORDER BY version;
SELECT COUNT(*) AS ap_table_count_expected_31_on_fresh_install FROM information_schema.tables
 WHERE table_schema=DATABASE() AND LEFT(table_name,3)='ap_';
SELECT id,revision,active_policy_id FROM ap_policy_state;
SELECT tier_code,COUNT(*) AS action_count FROM ap_policy_grant
 WHERE policy_version_id=(SELECT active_policy_id FROM ap_policy_state WHERE id=1) GROUP BY tier_code ORDER BY tier_code;
SELECT status,COUNT(*) AS action_count FROM ap_permission_action GROUP BY status;
SELECT COUNT(*) AS administrator_count FROM ap_account WHERE account_type='SYSTEM_ADMIN';
SELECT COUNT(*) AS initialized_once FROM ap_bootstrap WHERE name='INITIAL_ADMIN' AND completed_at IS NOT NULL;
SELECT security_version,withdrawn_at FROM ap_account LIMIT 0;
SELECT failed_attempts,locked_until FROM ap_email_credential LIMIT 0;
SELECT archived_at FROM ap_business_scope LIMIT 0;
SELECT saved_title,saved_answers_json,saved_assets_json FROM ap_work_draft LIMIT 0;
SELECT version FROM ap_asset LIMIT 0;
SELECT COUNT(*) AS draft_count FROM ap_work_draft;
SELECT COUNT(*) AS asset_count FROM ap_asset;

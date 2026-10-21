-- AxPlatform V005. Stop the app; back up DB and both storage roots first.
-- Run after V004 in HeidiSQL. No existing file is moved or deleted by this SQL.
ALTER TABLE ap_asset MODIFY COLUMN disk_key VARCHAR(600) NOT NULL;
ALTER TABLE ap_asset ADD COLUMN IF NOT EXISTS owner_account_id BIGINT NULL;
ALTER TABLE ap_asset ADD COLUMN IF NOT EXISTS scope_kind VARCHAR(16) NOT NULL DEFAULT 'COMMON';
ALTER TABLE ap_asset ADD COLUMN IF NOT EXISTS workflow VARCHAR(16) NULL;
ALTER TABLE ap_asset ADD COLUMN IF NOT EXISTS project_id VARCHAR(36) NULL;
ALTER TABLE ap_asset ADD COLUMN IF NOT EXISTS purpose VARCHAR(16) NOT NULL DEFAULT 'SOURCE';
ALTER TABLE ap_asset ADD COLUMN IF NOT EXISTS root_kind VARCHAR(16) NOT NULL DEFAULT 'LEGACY';
ALTER TABLE ap_asset ADD COLUMN IF NOT EXISTS storage_state VARCHAR(16) NOT NULL DEFAULT 'READY';
ALTER TABLE ap_asset ADD COLUMN IF NOT EXISTS purged_at DATETIME(6) NULL;
UPDATE ap_asset a SET owner_account_id=(SELECT w.owner_account_id FROM ap_workspace w WHERE w.id=a.workspace_id) WHERE owner_account_id IS NULL;
ALTER TABLE ap_asset MODIFY COLUMN owner_account_id BIGINT NOT NULL;
CREATE INDEX IF NOT EXISTS ix_asset_owner_state ON ap_asset(owner_account_id,storage_state,created_at);
CREATE INDEX IF NOT EXISTS ix_asset_project ON ap_asset(project_id,scope_kind);
CREATE TABLE IF NOT EXISTS ap_usage_policy (
 account_id BIGINT NOT NULL PRIMARY KEY, storage_limit_bytes BIGINT NOT NULL,
 ai_enabled BOOLEAN NOT NULL DEFAULT TRUE, monthly_token_limit BIGINT NOT NULL DEFAULT 0,
 monthly_cost_limit DECIMAL(18,6) NOT NULL DEFAULT 0, version BIGINT NOT NULL DEFAULT 0,
 updated_at DATETIME(6) NOT NULL, FOREIGN KEY(account_id) REFERENCES ap_account(id)
);
CREATE TABLE IF NOT EXISTS ap_operation_settings (
 id INT NOT NULL PRIMARY KEY, ai_enabled BOOLEAN NOT NULL DEFAULT TRUE,
 version BIGINT NOT NULL DEFAULT 0, updated_at DATETIME(6) NOT NULL
);
INSERT INTO ap_operation_settings(id,ai_enabled,updated_at) SELECT 1,TRUE,CURRENT_TIMESTAMP WHERE NOT EXISTS(SELECT 1 FROM ap_operation_settings WHERE id=1);
CREATE TABLE IF NOT EXISTS ap_model_price (
 id VARCHAR(36) NOT NULL PRIMARY KEY, model VARCHAR(100) NOT NULL,
 input_rate DECIMAL(18,6) NOT NULL, cached_rate DECIMAL(18,6) NOT NULL,
 cache_write_rate DECIMAL(18,6) NOT NULL, output_rate DECIMAL(18,6) NOT NULL,
 created_at DATETIME(6) NOT NULL, actor_account_id BIGINT NOT NULL,
 INDEX ix_price_model(model,created_at)
);
CREATE TABLE IF NOT EXISTS ap_ai_call (
 id VARCHAR(36) NOT NULL PRIMARY KEY, run_id VARCHAR(36) NOT NULL,
 account_id BIGINT NOT NULL, business_scope_id BIGINT NOT NULL, project_id VARCHAR(36) NOT NULL,
 workflow VARCHAR(16) NOT NULL, model VARCHAR(100) NOT NULL, round_no INT NOT NULL,
 state VARCHAR(16) NOT NULL, input_tokens BIGINT NULL, cached_tokens BIGINT NULL,
 cache_write_tokens BIGINT NULL, output_tokens BIGINT NULL, reasoning_tokens BIGINT NULL,
 reserved_tokens BIGINT NOT NULL, reserved_cost DECIMAL(18,6) NULL,
 estimated_cost DECIMAL(18,6) NULL, price_id VARCHAR(36) NULL,
 duration_ms BIGINT NOT NULL DEFAULT 0, safe_error_code VARCHAR(80) NULL,
 created_at DATETIME(6) NOT NULL, updated_at DATETIME(6) NOT NULL,
 UNIQUE(run_id,round_no), FOREIGN KEY(run_id) REFERENCES ap_ai_run(id),
 INDEX ix_ai_call_owner_date(account_id,created_at), INDEX ix_ai_call_state(state,created_at)
);
CREATE TABLE IF NOT EXISTS ap_activity_event (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY, actor_account_id BIGINT NULL,
 business_scope_id BIGINT NULL, project_id VARCHAR(36) NULL, workflow VARCHAR(16) NULL,
 action_code VARCHAR(100) NOT NULL, outcome VARCHAR(16) NOT NULL,
 duration_ms BIGINT NOT NULL DEFAULT 0, safe_error_code VARCHAR(80) NULL,
 created_at DATETIME(6) NOT NULL,
 INDEX ix_activity_date(created_at,action_code), INDEX ix_activity_actor(actor_account_id,created_at)
);
CREATE TABLE IF NOT EXISTS ap_operations_audit (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY, actor_account_id BIGINT NOT NULL,
 target_id VARCHAR(100) NOT NULL, action_code VARCHAR(80) NOT NULL,
 reason VARCHAR(500) NOT NULL, before_json TEXT NOT NULL, after_json TEXT NOT NULL,
 created_at DATETIME(6) NOT NULL
);
CREATE TABLE IF NOT EXISTS ap_storage_copy (
 id VARCHAR(36) NOT NULL PRIMARY KEY, asset_id VARCHAR(36) NOT NULL,
 owner_account_id BIGINT NOT NULL, disk_key VARCHAR(600) NOT NULL,
 byte_size BIGINT NOT NULL, sha256 VARCHAR(64) NOT NULL,
 state VARCHAR(16) NOT NULL DEFAULT 'READY', created_at DATETIME(6) NOT NULL,
 deleted_at DATETIME(6) NULL, UNIQUE(asset_id,disk_key), FOREIGN KEY(asset_id) REFERENCES ap_asset(id)
);
ALTER TABLE ap_asset ADD COLUMN IF NOT EXISTS upload_key VARCHAR(36) NULL;
CREATE UNIQUE INDEX IF NOT EXISTS uq_asset_upload ON ap_asset(owner_account_id,upload_key);
CREATE TABLE IF NOT EXISTS ap_storage_binding (
 id INT NOT NULL PRIMARY KEY, environment_key VARCHAR(32) NULL,
 root_path VARCHAR(1000) NULL, volume_id VARCHAR(36) NULL, bound_at DATETIME(6) NULL
);
INSERT INTO ap_storage_binding(id) SELECT 1 WHERE NOT EXISTS(SELECT 1 FROM ap_storage_binding WHERE id=1);
INSERT INTO ap_schema_migration(version,description,applied_at)
 SELECT 'V005','Member storage, project sources and operational metering',CURRENT_TIMESTAMP
 WHERE NOT EXISTS(SELECT 1 FROM ap_schema_migration WHERE version='V005')
 AND EXISTS(SELECT 1 FROM ap_schema_migration WHERE version='V004');

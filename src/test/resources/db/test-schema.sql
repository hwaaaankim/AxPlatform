-- TEST ONLY: H2 MySQL-mode adaptation. Never run this file in MariaDB.

CREATE TABLE IF NOT EXISTS ap_schema_migration (
    version VARCHAR(32) NOT NULL PRIMARY KEY,
    description VARCHAR(200) NOT NULL,
    applied_at DATETIME(6) NOT NULL
);

CREATE TABLE IF NOT EXISTS ap_account (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    account_type VARCHAR(32) NOT NULL,
    system_role VARCHAR(32) NOT NULL,
    status VARCHAR(32) NOT NULL,
    display_name VARCHAR(100) NOT NULL,
    contact_email VARCHAR(254) NULL,
    phone_e164 VARCHAR(20) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_account_type CHECK (account_type IN ('CUSTOMER','SYSTEM_ADMIN')),
    CONSTRAINT ck_account_role CHECK (system_role IN ('CUSTOMER','SUPER_ADMIN')),
    CONSTRAINT ck_account_role_type CHECK (
        (account_type='SYSTEM_ADMIN' AND system_role='SUPER_ADMIN') OR
        (account_type='CUSTOMER' AND system_role='CUSTOMER')),
    CONSTRAINT ck_account_status CHECK (status IN ('ACTIVE','SUSPENDED','WITHDRAWN')),
    CONSTRAINT ck_customer_contacts CHECK (account_type='SYSTEM_ADMIN' OR
        (contact_email IS NOT NULL AND phone_e164 IS NOT NULL)),
    INDEX ix_account_type_status (account_type,status,id)
);

CREATE TABLE IF NOT EXISTS ap_admin_credential (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    account_id BIGINT NOT NULL,
    username VARCHAR(100) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    security_version BIGINT NOT NULL DEFAULT 0,
    failed_attempts INT NOT NULL DEFAULT 0,
    locked_until DATETIME(6) NULL,
    password_changed_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    CONSTRAINT uq_admin_account UNIQUE (account_id),
    CONSTRAINT uq_admin_username UNIQUE (username),
    CONSTRAINT fk_admin_account FOREIGN KEY (account_id) REFERENCES ap_account(id),
    CONSTRAINT ck_admin_failures CHECK (failed_attempts >= 0)
);

CREATE TABLE IF NOT EXISTS ap_bootstrap (
    name VARCHAR(100) NOT NULL PRIMARY KEY,
    completed_at DATETIME(6) NULL,
    account_id BIGINT NULL
    
);

CREATE TABLE IF NOT EXISTS ap_email_credential (
    account_id BIGINT NOT NULL PRIMARY KEY,
    login_email VARCHAR(254) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    email_verified_at DATETIME(6) NOT NULL,
    security_version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_email_login UNIQUE (login_email),
    CONSTRAINT fk_email_account FOREIGN KEY (account_id) REFERENCES ap_account(id)
);

CREATE TABLE IF NOT EXISTS ap_external_identity (
    account_id BIGINT NOT NULL PRIMARY KEY,
    provider VARCHAR(16) NOT NULL,
    provider_subject VARCHAR(255) NOT NULL,
    CONSTRAINT uq_provider_subject UNIQUE (provider,provider_subject),
    CONSTRAINT ck_external_provider CHECK (provider IN ('GOOGLE','KAKAO','NAVER')),
    CONSTRAINT fk_external_account FOREIGN KEY (account_id) REFERENCES ap_account(id)
);

CREATE TABLE IF NOT EXISTS ap_workspace (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    owner_account_id BIGINT NOT NULL,
    name VARCHAR(100) NOT NULL,
    kind VARCHAR(32) NOT NULL,
    plan_code VARCHAR(16) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_workspace_owner FOREIGN KEY (owner_account_id) REFERENCES ap_account(id),
    CONSTRAINT ck_workspace_kind CHECK (kind IN ('CUSTOMER','ADMIN_TEST')),
    CONSTRAINT ck_workspace_plan CHECK ((kind='ADMIN_TEST' AND plan_code IS NULL) OR
        (kind='CUSTOMER' AND plan_code IN ('FREE','BASIC','PRO','MAX'))),
    INDEX ix_workspace_owner (owner_account_id,id)
);

CREATE TABLE IF NOT EXISTS ap_workspace_member (
    workspace_id BIGINT NOT NULL,
    account_id BIGINT NOT NULL,
    member_role VARCHAR(32) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (workspace_id,account_id),
    CONSTRAINT fk_member_workspace FOREIGN KEY (workspace_id) REFERENCES ap_workspace(id),
    CONSTRAINT fk_member_account FOREIGN KEY (account_id) REFERENCES ap_account(id)
);

CREATE TABLE IF NOT EXISTS ap_business_scope (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    workspace_id BIGINT NOT NULL,
    name VARCHAR(100) NOT NULL,
    is_default BOOLEAN NOT NULL DEFAULT FALSE,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_business_workspace FOREIGN KEY (workspace_id) REFERENCES ap_workspace(id),
    CONSTRAINT uq_business_scope UNIQUE (workspace_id,id),
    INDEX ix_scope_workspace (workspace_id,is_default,id)
);

CREATE TABLE IF NOT EXISTS ap_access_tier (
    code VARCHAR(16) NOT NULL PRIMARY KEY,
    name VARCHAR(32) NOT NULL,
    plan_code VARCHAR(16) NOT NULL UNIQUE,
    sort_order INT NOT NULL
);

CREATE TABLE IF NOT EXISTS ap_feature_group (
    code VARCHAR(32) NOT NULL PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    status VARCHAR(32) NOT NULL,
    sort_order INT NOT NULL,
    revision BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_feature_status CHECK (status IN ('PREPARING','ACTIVE','DISABLED','ARCHIVED'))
);

CREATE TABLE IF NOT EXISTS ap_admin_audit (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    actor_account_id BIGINT NULL,
    action VARCHAR(64) NOT NULL,
    detail VARCHAR(500) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    INDEX ix_admin_audit_actor_time (actor_account_id,created_at,id)
);

INSERT INTO ap_bootstrap(name) SELECT 'INITIAL_ADMIN'
WHERE NOT EXISTS (SELECT 1 FROM ap_bootstrap WHERE name='INITIAL_ADMIN');

INSERT INTO ap_access_tier(code,name,plan_code,sort_order) SELECT 'GUEST','게스트','FREE',0
WHERE NOT EXISTS (SELECT 1 FROM ap_access_tier WHERE code='GUEST');

INSERT INTO ap_access_tier(code,name,plan_code,sort_order) SELECT 'TIER1','1단계','BASIC',1
WHERE NOT EXISTS (SELECT 1 FROM ap_access_tier WHERE code='TIER1');

INSERT INTO ap_access_tier(code,name,plan_code,sort_order) SELECT 'TIER2','2단계','PRO',2
WHERE NOT EXISTS (SELECT 1 FROM ap_access_tier WHERE code='TIER2');

INSERT INTO ap_access_tier(code,name,plan_code,sort_order) SELECT 'TIER3','3단계','MAX',3
WHERE NOT EXISTS (SELECT 1 FROM ap_access_tier WHERE code='TIER3');

INSERT INTO ap_feature_group(code,name,status,sort_order) SELECT 'excel','엑셀','PREPARING',1
WHERE NOT EXISTS (SELECT 1 FROM ap_feature_group WHERE code='excel');

INSERT INTO ap_feature_group(code,name,status,sort_order) SELECT 'crm','CRM','PREPARING',2
WHERE NOT EXISTS (SELECT 1 FROM ap_feature_group WHERE code='crm');

INSERT INTO ap_feature_group(code,name,status,sort_order) SELECT 'calendar','일정','PREPARING',3
WHERE NOT EXISTS (SELECT 1 FROM ap_feature_group WHERE code='calendar');

INSERT INTO ap_feature_group(code,name,status,sort_order) SELECT 'word','워드','PREPARING',4
WHERE NOT EXISTS (SELECT 1 FROM ap_feature_group WHERE code='word');

INSERT INTO ap_feature_group(code,name,status,sort_order) SELECT 'hwp','한글','PREPARING',5
WHERE NOT EXISTS (SELECT 1 FROM ap_feature_group WHERE code='hwp');

INSERT INTO ap_feature_group(code,name,status,sort_order) SELECT 'ppt','PPT','PREPARING',6
WHERE NOT EXISTS (SELECT 1 FROM ap_feature_group WHERE code='ppt');

INSERT INTO ap_feature_group(code,name,status,sort_order) SELECT 'app','프로그램','PREPARING',7
WHERE NOT EXISTS (SELECT 1 FROM ap_feature_group WHERE code='app');

INSERT INTO ap_schema_migration(version,description,applied_at) SELECT 'V001','P0 bootstrap foundation',CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM ap_schema_migration WHERE version='V001');

ALTER TABLE ap_external_identity MODIFY COLUMN provider_subject VARCHAR(255) NOT NULL;

ALTER TABLE ap_account ADD COLUMN IF NOT EXISTS security_version BIGINT NOT NULL DEFAULT 0;

ALTER TABLE ap_account ADD COLUMN IF NOT EXISTS withdrawn_at DATETIME(6) NULL;

ALTER TABLE ap_email_credential ADD COLUMN IF NOT EXISTS failed_attempts INT NOT NULL DEFAULT 0;

ALTER TABLE ap_email_credential ADD COLUMN IF NOT EXISTS locked_until DATETIME(6) NULL;

ALTER TABLE ap_business_scope ADD COLUMN IF NOT EXISTS archived_at DATETIME(6) NULL;

CREATE TABLE IF NOT EXISTS ap_auth_rate (
 key_hash VARCHAR(64) NOT NULL PRIMARY KEY, attempts INT NOT NULL DEFAULT 0,
 window_start DATETIME(6) NOT NULL
);

CREATE TABLE IF NOT EXISTS ap_verification (
 id VARCHAR(36) NOT NULL PRIMARY KEY, purpose VARCHAR(32) NOT NULL,
 target VARCHAR(254) NOT NULL, session_hash VARCHAR(64) NOT NULL, code_mac VARCHAR(64) NOT NULL,
 attempts INT NOT NULL DEFAULT 0, status VARCHAR(16) NOT NULL,
 expires_at DATETIME(6) NOT NULL, proof_expires_at DATETIME(6) NULL,
 verified_at DATETIME(6) NULL, consumed_at DATETIME(6) NULL, created_at DATETIME(6) NOT NULL,
 INDEX ix_verification_rate (session_hash,purpose,target,created_at)
);

CREATE TABLE IF NOT EXISTS ap_password_reset (
 token_hash VARCHAR(64) NOT NULL PRIMARY KEY, account_id BIGINT NOT NULL,
 expires_at DATETIME(6) NOT NULL, consumed_at DATETIME(6) NULL, created_at DATETIME(6) NOT NULL,
 FOREIGN KEY (account_id) REFERENCES ap_account(id)
);

CREATE TABLE IF NOT EXISTS ap_consent (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY, account_id BIGINT NOT NULL,
 terms_version VARCHAR(100) NOT NULL, privacy_version VARCHAR(100) NOT NULL,
 agreed_at DATETIME(6) NOT NULL, FOREIGN KEY (account_id) REFERENCES ap_account(id)
);

CREATE TABLE IF NOT EXISTS ap_account_preference (
 account_id BIGINT NOT NULL PRIMARY KEY, plan_hide_until DATETIME(6) NULL,
 FOREIGN KEY (account_id) REFERENCES ap_account(id)
);

CREATE TABLE IF NOT EXISTS ap_permission_action (
 code VARCHAR(100) NOT NULL PRIMARY KEY, group_code VARCHAR(32) NOT NULL,
 name VARCHAR(100) NOT NULL, description VARCHAR(500) NOT NULL,
 binding_key VARCHAR(100) NULL UNIQUE, status VARCHAR(16) NOT NULL,
 admin_only BOOLEAN NOT NULL DEFAULT FALSE, risky BOOLEAN NOT NULL DEFAULT FALSE,
 sort_order INT NOT NULL DEFAULT 0, version BIGINT NOT NULL DEFAULT 0,
 created_at DATETIME(6) NOT NULL, updated_at DATETIME(6) NOT NULL,
 FOREIGN KEY (group_code) REFERENCES ap_feature_group(code),
 CHECK(status IN ('PREPARING','ACTIVE','DISABLED','ARCHIVED'))
);

CREATE TABLE IF NOT EXISTS ap_permission_dependency (
 action_code VARCHAR(100) NOT NULL, depends_on VARCHAR(100) NOT NULL,
 PRIMARY KEY(action_code,depends_on),
 FOREIGN KEY(action_code) REFERENCES ap_permission_action(code),
 FOREIGN KEY(depends_on) REFERENCES ap_permission_action(code)
);

CREATE TABLE IF NOT EXISTS ap_policy_version (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY, revision BIGINT NOT NULL UNIQUE,
 actor_account_id BIGINT NULL, kind VARCHAR(32) NOT NULL,
 reason VARCHAR(500) NOT NULL, source_version_id BIGINT NULL, created_at DATETIME(6) NOT NULL
);

CREATE TABLE IF NOT EXISTS ap_policy_state (
 id INT NOT NULL PRIMARY KEY, revision BIGINT NOT NULL, active_policy_id BIGINT NOT NULL,
 FOREIGN KEY(active_policy_id) REFERENCES ap_policy_version(id), CHECK(id=1)
);

CREATE TABLE IF NOT EXISTS ap_policy_grant (
 policy_version_id BIGINT NOT NULL, tier_code VARCHAR(16) NOT NULL, action_code VARCHAR(100) NOT NULL,
 sort_order INT NOT NULL DEFAULT 0, PRIMARY KEY(policy_version_id,tier_code,action_code),
 FOREIGN KEY(policy_version_id) REFERENCES ap_policy_version(id),
 FOREIGN KEY(tier_code) REFERENCES ap_access_tier(code)
 
);

CREATE TABLE IF NOT EXISTS ap_policy_request (
 actor_account_id BIGINT NOT NULL, request_key VARCHAR(100) NOT NULL,
 payload_hash VARCHAR(64) NOT NULL, result_version_id BIGINT NOT NULL,
 PRIMARY KEY(actor_account_id,request_key), FOREIGN KEY(result_version_id) REFERENCES ap_policy_version(id)
);

CREATE TABLE IF NOT EXISTS ap_member_change (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY, account_id BIGINT NOT NULL,
 actor_account_id BIGINT NOT NULL, action VARCHAR(32) NOT NULL, reason VARCHAR(500) NOT NULL,
 before_json TEXT NOT NULL, after_json TEXT NOT NULL, created_at DATETIME(6) NOT NULL,
 INDEX ix_member_change(account_id,id)
);

CREATE TABLE IF NOT EXISTS ap_workspace_tier_override (
 workspace_id BIGINT NOT NULL PRIMARY KEY, tier_code VARCHAR(16) NOT NULL, reason VARCHAR(500) NOT NULL,
 expires_at DATETIME(6) NULL, actor_account_id BIGINT NOT NULL, updated_at DATETIME(6) NOT NULL,
 version BIGINT NOT NULL DEFAULT 0,
 FOREIGN KEY(workspace_id) REFERENCES ap_workspace(id), FOREIGN KEY(tier_code) REFERENCES ap_access_tier(code)
);

CREATE TABLE IF NOT EXISTS ap_workspace_tier_history (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY, workspace_id BIGINT NOT NULL,
 actor_account_id BIGINT NOT NULL, action VARCHAR(32) NOT NULL, tier_code VARCHAR(16) NULL,
 reason VARCHAR(500) NOT NULL, expires_at DATETIME(6) NULL, created_at DATETIME(6) NOT NULL,
 FOREIGN KEY(workspace_id) REFERENCES ap_workspace(id)
);

CREATE TABLE IF NOT EXISTS ap_workspace_dashboard (
 workspace_id BIGINT NOT NULL PRIMARY KEY, widgets_json TEXT NOT NULL,
 version BIGINT NOT NULL DEFAULT 0, FOREIGN KEY(workspace_id) REFERENCES ap_workspace(id)
);

CREATE TABLE IF NOT EXISTS ap_work_draft (
 id VARCHAR(36) NOT NULL PRIMARY KEY, workspace_id BIGINT NOT NULL, business_scope_id BIGINT NOT NULL,
 creator_account_id BIGINT NOT NULL, workflow VARCHAR(16) NOT NULL, title VARCHAR(100) NOT NULL,
 answers_json TEXT NOT NULL, saved_title VARCHAR(100) NOT NULL, saved_answers_json TEXT NOT NULL,
 saved_assets_json TEXT NOT NULL, status VARCHAR(16) NOT NULL DEFAULT 'DRAFT',
 version BIGINT NOT NULL DEFAULT 0, created_at DATETIME(6) NOT NULL, updated_at DATETIME(6) NOT NULL,
 FOREIGN KEY(workspace_id,business_scope_id) REFERENCES ap_business_scope(workspace_id,id),
 INDEX ix_draft_scope(business_scope_id,status,updated_at)
);

CREATE TABLE IF NOT EXISTS ap_asset (
 id VARCHAR(36) NOT NULL PRIMARY KEY, workspace_id BIGINT NOT NULL, business_scope_id BIGINT NOT NULL,
 creator_account_id BIGINT NOT NULL, original_name VARCHAR(200) NOT NULL, disk_key VARCHAR(100) NOT NULL,
 media_type VARCHAR(80) NOT NULL, byte_size BIGINT NOT NULL, sha256 VARCHAR(64) NOT NULL,
 archived_at DATETIME(6) NULL, version BIGINT NOT NULL DEFAULT 0, created_at DATETIME(6) NOT NULL,
 FOREIGN KEY(workspace_id,business_scope_id) REFERENCES ap_business_scope(workspace_id,id),
 INDEX ix_asset_scope(business_scope_id,created_at)
);

CREATE TABLE IF NOT EXISTS ap_draft_asset (
 draft_id VARCHAR(36) NOT NULL, asset_id VARCHAR(36) NOT NULL, PRIMARY KEY(draft_id,asset_id),
 FOREIGN KEY(draft_id) REFERENCES ap_work_draft(id), FOREIGN KEY(asset_id) REFERENCES ap_asset(id)
);

CREATE TABLE IF NOT EXISTS ap_event_outbox (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY, workspace_id BIGINT NOT NULL, business_scope_id BIGINT NULL,
 event_type VARCHAR(40) NOT NULL, object_id VARCHAR(100) NOT NULL, object_version BIGINT NOT NULL,
 created_at DATETIME(6) NOT NULL, INDEX ix_event_scope(workspace_id,business_scope_id,id)
);

INSERT INTO ap_feature_group(code,name,status,sort_order) SELECT 'workspace','공통 업무 기반','ACTIVE',0
 WHERE NOT EXISTS(SELECT 1 FROM ap_feature_group WHERE code='workspace');

INSERT INTO ap_feature_group(code,name,status,sort_order) SELECT 'workspace','workspace','PREPARING',100 WHERE NOT EXISTS(SELECT 1 FROM ap_feature_group WHERE code='workspace');

INSERT INTO ap_permission_action(code,group_code,name,description,binding_key,status,sort_order,created_at,updated_at) SELECT 'workspace.scope.read','workspace','사업 목록 조회','P1 서버 행동','workspace.scope.read','ACTIVE',0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP WHERE NOT EXISTS(SELECT 1 FROM ap_permission_action WHERE code='workspace.scope.read');

INSERT INTO ap_feature_group(code,name,status,sort_order) SELECT 'workspace','workspace','PREPARING',100 WHERE NOT EXISTS(SELECT 1 FROM ap_feature_group WHERE code='workspace');

INSERT INTO ap_permission_action(code,group_code,name,description,binding_key,status,sort_order,created_at,updated_at) SELECT 'workspace.scope.create','workspace','사업 만들기','P1 서버 행동','workspace.scope.create','ACTIVE',1,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP WHERE NOT EXISTS(SELECT 1 FROM ap_permission_action WHERE code='workspace.scope.create');

INSERT INTO ap_feature_group(code,name,status,sort_order) SELECT 'workspace','workspace','PREPARING',100 WHERE NOT EXISTS(SELECT 1 FROM ap_feature_group WHERE code='workspace');

INSERT INTO ap_permission_action(code,group_code,name,description,binding_key,status,sort_order,created_at,updated_at) SELECT 'workspace.scope.update','workspace','사업 이름 수정','P1 서버 행동','workspace.scope.update','ACTIVE',2,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP WHERE NOT EXISTS(SELECT 1 FROM ap_permission_action WHERE code='workspace.scope.update');

INSERT INTO ap_feature_group(code,name,status,sort_order) SELECT 'workspace','workspace','PREPARING',100 WHERE NOT EXISTS(SELECT 1 FROM ap_feature_group WHERE code='workspace');

INSERT INTO ap_permission_action(code,group_code,name,description,binding_key,status,sort_order,created_at,updated_at) SELECT 'workspace.scope.archive','workspace','사업 보관·복원','P1 서버 행동','workspace.scope.archive','ACTIVE',3,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP WHERE NOT EXISTS(SELECT 1 FROM ap_permission_action WHERE code='workspace.scope.archive');

INSERT INTO ap_feature_group(code,name,status,sort_order) SELECT 'workspace','workspace','PREPARING',100 WHERE NOT EXISTS(SELECT 1 FROM ap_feature_group WHERE code='workspace');

INSERT INTO ap_permission_action(code,group_code,name,description,binding_key,status,sort_order,created_at,updated_at) SELECT 'workspace.dashboard.read','workspace','업무 홈 조회','P1 서버 행동','workspace.dashboard.read','ACTIVE',4,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP WHERE NOT EXISTS(SELECT 1 FROM ap_permission_action WHERE code='workspace.dashboard.read');

INSERT INTO ap_feature_group(code,name,status,sort_order) SELECT 'workspace','workspace','PREPARING',100 WHERE NOT EXISTS(SELECT 1 FROM ap_feature_group WHERE code='workspace');

INSERT INTO ap_permission_action(code,group_code,name,description,binding_key,status,sort_order,created_at,updated_at) SELECT 'workspace.dashboard.manage','workspace','홈 구성 저장','P1 서버 행동','workspace.dashboard.manage','ACTIVE',5,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP WHERE NOT EXISTS(SELECT 1 FROM ap_permission_action WHERE code='workspace.dashboard.manage');

INSERT INTO ap_feature_group(code,name,status,sort_order) SELECT 'workspace','workspace','PREPARING',100 WHERE NOT EXISTS(SELECT 1 FROM ap_feature_group WHERE code='workspace');

INSERT INTO ap_permission_action(code,group_code,name,description,binding_key,status,sort_order,created_at,updated_at) SELECT 'workspace.events.read','workspace','변경 알림 구독','P1 서버 행동','workspace.events.read','ACTIVE',6,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP WHERE NOT EXISTS(SELECT 1 FROM ap_permission_action WHERE code='workspace.events.read');

INSERT INTO ap_feature_group(code,name,status,sort_order) SELECT 'workspace','workspace','PREPARING',100 WHERE NOT EXISTS(SELECT 1 FROM ap_feature_group WHERE code='workspace');

INSERT INTO ap_permission_action(code,group_code,name,description,binding_key,status,sort_order,created_at,updated_at) SELECT 'workspace.draft.list','workspace','업무 초안 목록','P1 서버 행동','workspace.draft.list','ACTIVE',7,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP WHERE NOT EXISTS(SELECT 1 FROM ap_permission_action WHERE code='workspace.draft.list');

INSERT INTO ap_feature_group(code,name,status,sort_order) SELECT 'workspace','workspace','PREPARING',100 WHERE NOT EXISTS(SELECT 1 FROM ap_feature_group WHERE code='workspace');

INSERT INTO ap_permission_action(code,group_code,name,description,binding_key,status,sort_order,created_at,updated_at) SELECT 'workspace.draft.read','workspace','업무 초안 상세','P1 서버 행동','workspace.draft.read','ACTIVE',8,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP WHERE NOT EXISTS(SELECT 1 FROM ap_permission_action WHERE code='workspace.draft.read');

INSERT INTO ap_feature_group(code,name,status,sort_order) SELECT 'workspace','workspace','PREPARING',100 WHERE NOT EXISTS(SELECT 1 FROM ap_feature_group WHERE code='workspace');

INSERT INTO ap_permission_action(code,group_code,name,description,binding_key,status,sort_order,created_at,updated_at) SELECT 'workspace.draft.create','workspace','업무 초안 만들기','P1 서버 행동','workspace.draft.create','ACTIVE',9,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP WHERE NOT EXISTS(SELECT 1 FROM ap_permission_action WHERE code='workspace.draft.create');

INSERT INTO ap_feature_group(code,name,status,sort_order) SELECT 'workspace','workspace','PREPARING',100 WHERE NOT EXISTS(SELECT 1 FROM ap_feature_group WHERE code='workspace');

INSERT INTO ap_permission_action(code,group_code,name,description,binding_key,status,sort_order,created_at,updated_at) SELECT 'workspace.draft.update','workspace','답변 수정','P1 서버 행동','workspace.draft.update','ACTIVE',10,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP WHERE NOT EXISTS(SELECT 1 FROM ap_permission_action WHERE code='workspace.draft.update');

INSERT INTO ap_feature_group(code,name,status,sort_order) SELECT 'workspace','workspace','PREPARING',100 WHERE NOT EXISTS(SELECT 1 FROM ap_feature_group WHERE code='workspace');

INSERT INTO ap_permission_action(code,group_code,name,description,binding_key,status,sort_order,created_at,updated_at) SELECT 'workspace.draft.save','workspace','업무 초안 확정','P1 서버 행동','workspace.draft.save','ACTIVE',11,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP WHERE NOT EXISTS(SELECT 1 FROM ap_permission_action WHERE code='workspace.draft.save');

INSERT INTO ap_feature_group(code,name,status,sort_order) SELECT 'workspace','workspace','PREPARING',100 WHERE NOT EXISTS(SELECT 1 FROM ap_feature_group WHERE code='workspace');

INSERT INTO ap_permission_action(code,group_code,name,description,binding_key,status,sort_order,created_at,updated_at) SELECT 'workspace.draft.archive','workspace','업무 초안 보관','P1 서버 행동','workspace.draft.archive','ACTIVE',12,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP WHERE NOT EXISTS(SELECT 1 FROM ap_permission_action WHERE code='workspace.draft.archive');

INSERT INTO ap_feature_group(code,name,status,sort_order) SELECT 'workspace','workspace','PREPARING',100 WHERE NOT EXISTS(SELECT 1 FROM ap_feature_group WHERE code='workspace');

INSERT INTO ap_permission_action(code,group_code,name,description,binding_key,status,sort_order,created_at,updated_at) SELECT 'workspace.draft.restore','workspace','업무 초안 복원','P1 서버 행동','workspace.draft.restore','ACTIVE',13,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP WHERE NOT EXISTS(SELECT 1 FROM ap_permission_action WHERE code='workspace.draft.restore');

INSERT INTO ap_feature_group(code,name,status,sort_order) SELECT 'workspace','workspace','PREPARING',100 WHERE NOT EXISTS(SELECT 1 FROM ap_feature_group WHERE code='workspace');

INSERT INTO ap_permission_action(code,group_code,name,description,binding_key,status,sort_order,created_at,updated_at) SELECT 'workspace.asset.list','workspace','첨부 목록 조회','P1 서버 행동','workspace.asset.list','ACTIVE',14,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP WHERE NOT EXISTS(SELECT 1 FROM ap_permission_action WHERE code='workspace.asset.list');

INSERT INTO ap_feature_group(code,name,status,sort_order) SELECT 'workspace','workspace','PREPARING',100 WHERE NOT EXISTS(SELECT 1 FROM ap_feature_group WHERE code='workspace');

INSERT INTO ap_permission_action(code,group_code,name,description,binding_key,status,sort_order,created_at,updated_at) SELECT 'workspace.asset.upload','workspace','이미지·텍스트 첨부','P1 서버 행동','workspace.asset.upload','ACTIVE',15,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP WHERE NOT EXISTS(SELECT 1 FROM ap_permission_action WHERE code='workspace.asset.upload');

INSERT INTO ap_feature_group(code,name,status,sort_order) SELECT 'workspace','workspace','PREPARING',100 WHERE NOT EXISTS(SELECT 1 FROM ap_feature_group WHERE code='workspace');

INSERT INTO ap_permission_action(code,group_code,name,description,binding_key,status,sort_order,created_at,updated_at) SELECT 'workspace.asset.download','workspace','첨부 다운로드','P1 서버 행동','workspace.asset.download','ACTIVE',16,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP WHERE NOT EXISTS(SELECT 1 FROM ap_permission_action WHERE code='workspace.asset.download');

INSERT INTO ap_feature_group(code,name,status,sort_order) SELECT 'workspace','workspace','PREPARING',100 WHERE NOT EXISTS(SELECT 1 FROM ap_feature_group WHERE code='workspace');

INSERT INTO ap_permission_action(code,group_code,name,description,binding_key,status,sort_order,created_at,updated_at) SELECT 'workspace.asset.archive','workspace','첨부 보관','P1 서버 행동','workspace.asset.archive','ACTIVE',17,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP WHERE NOT EXISTS(SELECT 1 FROM ap_permission_action WHERE code='workspace.asset.archive');

INSERT INTO ap_feature_group(code,name,status,sort_order) SELECT 'excel','excel','PREPARING',100 WHERE NOT EXISTS(SELECT 1 FROM ap_feature_group WHERE code='excel');

INSERT INTO ap_permission_action(code,group_code,name,description,binding_key,status,sort_order,created_at,updated_at) SELECT 'excel.overview.read','excel','오버뷰 조회','P2 구현 예정',NULL,'PREPARING',18,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP WHERE NOT EXISTS(SELECT 1 FROM ap_permission_action WHERE code='excel.overview.read');

INSERT INTO ap_feature_group(code,name,status,sort_order) SELECT 'excel','excel','PREPARING',100 WHERE NOT EXISTS(SELECT 1 FROM ap_feature_group WHERE code='excel');

INSERT INTO ap_permission_action(code,group_code,name,description,binding_key,status,sort_order,created_at,updated_at) SELECT 'excel.list.read','excel','목록 메타데이터 조회','P2 구현 예정',NULL,'PREPARING',19,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP WHERE NOT EXISTS(SELECT 1 FROM ap_permission_action WHERE code='excel.list.read');

INSERT INTO ap_feature_group(code,name,status,sort_order) SELECT 'excel','excel','PREPARING',100 WHERE NOT EXISTS(SELECT 1 FROM ap_feature_group WHERE code='excel');

INSERT INTO ap_permission_action(code,group_code,name,description,binding_key,status,sort_order,created_at,updated_at) SELECT 'excel.detail.read','excel','헤더·셀 상세 조회','P2 구현 예정',NULL,'PREPARING',20,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP WHERE NOT EXISTS(SELECT 1 FROM ap_permission_action WHERE code='excel.detail.read');

INSERT INTO ap_feature_group(code,name,status,sort_order) SELECT 'excel','excel','PREPARING',100 WHERE NOT EXISTS(SELECT 1 FROM ap_feature_group WHERE code='excel');

INSERT INTO ap_permission_action(code,group_code,name,description,binding_key,status,sort_order,created_at,updated_at) SELECT 'excel.create.manual','excel','직접 생성','P2 구현 예정',NULL,'PREPARING',21,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP WHERE NOT EXISTS(SELECT 1 FROM ap_permission_action WHERE code='excel.create.manual');

INSERT INTO ap_feature_group(code,name,status,sort_order) SELECT 'excel','excel','PREPARING',100 WHERE NOT EXISTS(SELECT 1 FROM ap_feature_group WHERE code='excel');

INSERT INTO ap_permission_action(code,group_code,name,description,binding_key,status,sort_order,created_at,updated_at) SELECT 'excel.create.chat','excel','채팅 생성','P2 구현 예정',NULL,'PREPARING',22,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP WHERE NOT EXISTS(SELECT 1 FROM ap_permission_action WHERE code='excel.create.chat');

INSERT INTO ap_feature_group(code,name,status,sort_order) SELECT 'excel','excel','PREPARING',100 WHERE NOT EXISTS(SELECT 1 FROM ap_feature_group WHERE code='excel');

INSERT INTO ap_permission_action(code,group_code,name,description,binding_key,status,sort_order,created_at,updated_at) SELECT 'excel.create.photo','excel','사진 생성','P2 구현 예정',NULL,'PREPARING',23,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP WHERE NOT EXISTS(SELECT 1 FROM ap_permission_action WHERE code='excel.create.photo');

INSERT INTO ap_feature_group(code,name,status,sort_order) SELECT 'excel','excel','PREPARING',100 WHERE NOT EXISTS(SELECT 1 FROM ap_feature_group WHERE code='excel');

INSERT INTO ap_permission_action(code,group_code,name,description,binding_key,status,sort_order,created_at,updated_at) SELECT 'excel.meta.update','excel','이름·설명 수정','P2 구현 예정',NULL,'PREPARING',24,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP WHERE NOT EXISTS(SELECT 1 FROM ap_permission_action WHERE code='excel.meta.update');

INSERT INTO ap_feature_group(code,name,status,sort_order) SELECT 'excel','excel','PREPARING',100 WHERE NOT EXISTS(SELECT 1 FROM ap_feature_group WHERE code='excel');

INSERT INTO ap_permission_action(code,group_code,name,description,binding_key,status,sort_order,created_at,updated_at) SELECT 'excel.data.create','excel','데이터 행 추가','P2 구현 예정',NULL,'PREPARING',25,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP WHERE NOT EXISTS(SELECT 1 FROM ap_permission_action WHERE code='excel.data.create');

INSERT INTO ap_feature_group(code,name,status,sort_order) SELECT 'excel','excel','PREPARING',100 WHERE NOT EXISTS(SELECT 1 FROM ap_feature_group WHERE code='excel');

INSERT INTO ap_permission_action(code,group_code,name,description,binding_key,status,sort_order,created_at,updated_at) SELECT 'excel.data.update','excel','데이터 값 수정','P2 구현 예정',NULL,'PREPARING',26,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP WHERE NOT EXISTS(SELECT 1 FROM ap_permission_action WHERE code='excel.data.update');

INSERT INTO ap_feature_group(code,name,status,sort_order) SELECT 'excel','excel','PREPARING',100 WHERE NOT EXISTS(SELECT 1 FROM ap_feature_group WHERE code='excel');

INSERT INTO ap_permission_action(code,group_code,name,description,binding_key,status,sort_order,created_at,updated_at) SELECT 'excel.data.delete','excel','데이터 행 삭제','P2 구현 예정',NULL,'PREPARING',27,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP WHERE NOT EXISTS(SELECT 1 FROM ap_permission_action WHERE code='excel.data.delete');

INSERT INTO ap_feature_group(code,name,status,sort_order) SELECT 'excel','excel','PREPARING',100 WHERE NOT EXISTS(SELECT 1 FROM ap_feature_group WHERE code='excel');

INSERT INTO ap_permission_action(code,group_code,name,description,binding_key,status,sort_order,created_at,updated_at) SELECT 'excel.schema.update','excel','헤더·구조 변경','P2 구현 예정',NULL,'PREPARING',28,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP WHERE NOT EXISTS(SELECT 1 FROM ap_permission_action WHERE code='excel.schema.update');

INSERT INTO ap_feature_group(code,name,status,sort_order) SELECT 'excel','excel','PREPARING',100 WHERE NOT EXISTS(SELECT 1 FROM ap_feature_group WHERE code='excel');

INSERT INTO ap_permission_action(code,group_code,name,description,binding_key,status,sort_order,created_at,updated_at) SELECT 'excel.formula.manage','excel','수식 직접 설정/재계산','P2 구현 예정',NULL,'PREPARING',29,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP WHERE NOT EXISTS(SELECT 1 FROM ap_permission_action WHERE code='excel.formula.manage');

INSERT INTO ap_feature_group(code,name,status,sort_order) SELECT 'excel','excel','PREPARING',100 WHERE NOT EXISTS(SELECT 1 FROM ap_feature_group WHERE code='excel');

INSERT INTO ap_permission_action(code,group_code,name,description,binding_key,status,sort_order,created_at,updated_at) SELECT 'excel.formula.ai','excel','수식 AI 제안','P2 구현 예정',NULL,'PREPARING',30,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP WHERE NOT EXISTS(SELECT 1 FROM ap_permission_action WHERE code='excel.formula.ai');

INSERT INTO ap_feature_group(code,name,status,sort_order) SELECT 'excel','excel','PREPARING',100 WHERE NOT EXISTS(SELECT 1 FROM ap_feature_group WHERE code='excel');

INSERT INTO ap_permission_action(code,group_code,name,description,binding_key,status,sort_order,created_at,updated_at) SELECT 'excel.import.append','excel','추가 업로드','P2 구현 예정',NULL,'PREPARING',31,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP WHERE NOT EXISTS(SELECT 1 FROM ap_permission_action WHERE code='excel.import.append');

INSERT INTO ap_feature_group(code,name,status,sort_order) SELECT 'excel','excel','PREPARING',100 WHERE NOT EXISTS(SELECT 1 FROM ap_feature_group WHERE code='excel');

INSERT INTO ap_permission_action(code,group_code,name,description,binding_key,status,sort_order,created_at,updated_at) SELECT 'excel.import.reset','excel','전체 교체 업로드','P2 구현 예정',NULL,'PREPARING',32,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP WHERE NOT EXISTS(SELECT 1 FROM ap_permission_action WHERE code='excel.import.reset');

INSERT INTO ap_feature_group(code,name,status,sort_order) SELECT 'excel','excel','PREPARING',100 WHERE NOT EXISTS(SELECT 1 FROM ap_feature_group WHERE code='excel');

INSERT INTO ap_permission_action(code,group_code,name,description,binding_key,status,sort_order,created_at,updated_at) SELECT 'excel.export.template','excel','빈 입력 양식 다운로드','P2 구현 예정',NULL,'PREPARING',33,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP WHERE NOT EXISTS(SELECT 1 FROM ap_permission_action WHERE code='excel.export.template');

INSERT INTO ap_feature_group(code,name,status,sort_order) SELECT 'excel','excel','PREPARING',100 WHERE NOT EXISTS(SELECT 1 FROM ap_feature_group WHERE code='excel');

INSERT INTO ap_permission_action(code,group_code,name,description,binding_key,status,sort_order,created_at,updated_at) SELECT 'excel.export.data','excel','현재 데이터 다운로드','P2 구현 예정',NULL,'PREPARING',34,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP WHERE NOT EXISTS(SELECT 1 FROM ap_permission_action WHERE code='excel.export.data');

INSERT INTO ap_feature_group(code,name,status,sort_order) SELECT 'excel','excel','PREPARING',100 WHERE NOT EXISTS(SELECT 1 FROM ap_feature_group WHERE code='excel');

INSERT INTO ap_permission_action(code,group_code,name,description,binding_key,status,sort_order,created_at,updated_at) SELECT 'excel.analysis.read','excel','저장 분석 조회','P2 구현 예정',NULL,'PREPARING',35,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP WHERE NOT EXISTS(SELECT 1 FROM ap_permission_action WHERE code='excel.analysis.read');

INSERT INTO ap_feature_group(code,name,status,sort_order) SELECT 'excel','excel','PREPARING',100 WHERE NOT EXISTS(SELECT 1 FROM ap_feature_group WHERE code='excel');

INSERT INTO ap_permission_action(code,group_code,name,description,binding_key,status,sort_order,created_at,updated_at) SELECT 'excel.analysis.manage','excel','분석 보기 생성/수정/삭제','P2 구현 예정',NULL,'PREPARING',36,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP WHERE NOT EXISTS(SELECT 1 FROM ap_permission_action WHERE code='excel.analysis.manage');

INSERT INTO ap_feature_group(code,name,status,sort_order) SELECT 'excel','excel','PREPARING',100 WHERE NOT EXISTS(SELECT 1 FROM ap_feature_group WHERE code='excel');

INSERT INTO ap_permission_action(code,group_code,name,description,binding_key,status,sort_order,created_at,updated_at) SELECT 'excel.template.manage','excel','내 엑셀 양식 관리','P2 구현 예정',NULL,'PREPARING',37,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP WHERE NOT EXISTS(SELECT 1 FROM ap_permission_action WHERE code='excel.template.manage');

INSERT INTO ap_feature_group(code,name,status,sort_order) SELECT 'excel','excel','PREPARING',100 WHERE NOT EXISTS(SELECT 1 FROM ap_feature_group WHERE code='excel');

INSERT INTO ap_permission_action(code,group_code,name,description,binding_key,status,sort_order,created_at,updated_at) SELECT 'excel.project.archive','excel','작업공간 보관/삭제','P2 구현 예정',NULL,'PREPARING',38,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP WHERE NOT EXISTS(SELECT 1 FROM ap_permission_action WHERE code='excel.project.archive');

INSERT INTO ap_feature_group(code,name,status,sort_order) SELECT 'excel','excel','PREPARING',100 WHERE NOT EXISTS(SELECT 1 FROM ap_feature_group WHERE code='excel');

INSERT INTO ap_permission_action(code,group_code,name,description,binding_key,status,sort_order,created_at,updated_at) SELECT 'excel.project.restore','excel','작업공간/스냅샷 복구','P2 구현 예정',NULL,'PREPARING',39,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP WHERE NOT EXISTS(SELECT 1 FROM ap_permission_action WHERE code='excel.project.restore');

INSERT INTO ap_feature_group(code,name,status,sort_order) SELECT 'excel','excel','PREPARING',100 WHERE NOT EXISTS(SELECT 1 FROM ap_feature_group WHERE code='excel');

INSERT INTO ap_permission_action(code,group_code,name,description,binding_key,status,sort_order,created_at,updated_at) SELECT 'excel.create.file','excel','파일로 새 양식 생성','P2 구현 예정',NULL,'PREPARING',40,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP WHERE NOT EXISTS(SELECT 1 FROM ap_permission_action WHERE code='excel.create.file');

INSERT INTO ap_feature_group(code,name,status,sort_order) SELECT 'excel','excel','PREPARING',100 WHERE NOT EXISTS(SELECT 1 FROM ap_feature_group WHERE code='excel');

INSERT INTO ap_permission_action(code,group_code,name,description,binding_key,status,sort_order,created_at,updated_at) SELECT 'excel.create.template','excel','내 양식으로 생성','P2 구현 예정',NULL,'PREPARING',41,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP WHERE NOT EXISTS(SELECT 1 FROM ap_permission_action WHERE code='excel.create.template');

INSERT INTO ap_feature_group(code,name,status,sort_order) SELECT 'excel','excel','PREPARING',100 WHERE NOT EXISTS(SELECT 1 FROM ap_feature_group WHERE code='excel');

INSERT INTO ap_permission_action(code,group_code,name,description,binding_key,status,sort_order,created_at,updated_at) SELECT 'excel.data.photo','excel','기존 자료에 사진 후보 추가','P2 구현 예정',NULL,'PREPARING',42,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP WHERE NOT EXISTS(SELECT 1 FROM ap_permission_action WHERE code='excel.data.photo');

INSERT INTO ap_feature_group(code,name,status,sort_order) SELECT 'excel','excel','PREPARING',100 WHERE NOT EXISTS(SELECT 1 FROM ap_feature_group WHERE code='excel');

INSERT INTO ap_permission_action(code,group_code,name,description,binding_key,status,sort_order,created_at,updated_at) SELECT 'excel.data.copy','excel','데이터 명시 복제','P2 구현 예정',NULL,'PREPARING',43,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP WHERE NOT EXISTS(SELECT 1 FROM ap_permission_action WHERE code='excel.data.copy');

INSERT INTO ap_feature_group(code,name,status,sort_order) SELECT 'excel','excel','PREPARING',100 WHERE NOT EXISTS(SELECT 1 FROM ap_feature_group WHERE code='excel');

INSERT INTO ap_permission_action(code,group_code,name,description,binding_key,status,sort_order,created_at,updated_at) SELECT 'excel.project.duplicate','excel','양식/작업공간 구조 복제','P2 구현 예정',NULL,'PREPARING',44,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP WHERE NOT EXISTS(SELECT 1 FROM ap_permission_action WHERE code='excel.project.duplicate');

INSERT INTO ap_feature_group(code,name,status,sort_order) SELECT 'excel','excel','PREPARING',100 WHERE NOT EXISTS(SELECT 1 FROM ap_feature_group WHERE code='excel');

INSERT INTO ap_permission_action(code,group_code,name,description,binding_key,status,sort_order,created_at,updated_at) SELECT 'excel.version.read','excel','확정·스냅샷 이력 조회','P2 구현 예정',NULL,'PREPARING',45,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP WHERE NOT EXISTS(SELECT 1 FROM ap_permission_action WHERE code='excel.version.read');

INSERT INTO ap_feature_group(code,name,status,sort_order) SELECT 'excel','excel','PREPARING',100 WHERE NOT EXISTS(SELECT 1 FROM ap_feature_group WHERE code='excel');

INSERT INTO ap_permission_action(code,group_code,name,description,binding_key,status,sort_order,created_at,updated_at) SELECT 'excel.resource.read','excel','원본 사진 조회','P2 구현 예정',NULL,'PREPARING',46,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP WHERE NOT EXISTS(SELECT 1 FROM ap_permission_action WHERE code='excel.resource.read');

INSERT INTO ap_feature_group(code,name,status,sort_order) SELECT 'excel','excel','PREPARING',100 WHERE NOT EXISTS(SELECT 1 FROM ap_feature_group WHERE code='excel');

INSERT INTO ap_permission_action(code,group_code,name,description,binding_key,status,sort_order,created_at,updated_at) SELECT 'excel.resource.manage','excel','원본 자료 연결 관리','P2 구현 예정',NULL,'PREPARING',47,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP WHERE NOT EXISTS(SELECT 1 FROM ap_permission_action WHERE code='excel.resource.manage');

INSERT INTO ap_feature_group(code,name,status,sort_order) SELECT 'excel','excel','PREPARING',100 WHERE NOT EXISTS(SELECT 1 FROM ap_feature_group WHERE code='excel');

INSERT INTO ap_permission_action(code,group_code,name,description,binding_key,status,sort_order,created_at,updated_at) SELECT 'excel.dashboard.manage','excel','개별 대시보드 위젯 관리','P2 구현 예정',NULL,'PREPARING',48,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP WHERE NOT EXISTS(SELECT 1 FROM ap_permission_action WHERE code='excel.dashboard.manage');

INSERT INTO ap_feature_group(code,name,status,sort_order) SELECT 'word','word','PREPARING',100 WHERE NOT EXISTS(SELECT 1 FROM ap_feature_group WHERE code='word');

INSERT INTO ap_permission_action(code,group_code,name,description,binding_key,status,sort_order,created_at,updated_at) SELECT 'word.overview.read','word','WORD 오버뷰','P4 구현 예정',NULL,'PREPARING',49,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP WHERE NOT EXISTS(SELECT 1 FROM ap_permission_action WHERE code='word.overview.read');

INSERT INTO ap_feature_group(code,name,status,sort_order) SELECT 'word','word','PREPARING',100 WHERE NOT EXISTS(SELECT 1 FROM ap_feature_group WHERE code='word');

INSERT INTO ap_permission_action(code,group_code,name,description,binding_key,status,sort_order,created_at,updated_at) SELECT 'word.list.read','word','WORD 목록','P4 구현 예정',NULL,'PREPARING',50,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP WHERE NOT EXISTS(SELECT 1 FROM ap_permission_action WHERE code='word.list.read');

INSERT INTO ap_feature_group(code,name,status,sort_order) SELECT 'word','word','PREPARING',100 WHERE NOT EXISTS(SELECT 1 FROM ap_feature_group WHERE code='word');

INSERT INTO ap_permission_action(code,group_code,name,description,binding_key,status,sort_order,created_at,updated_at) SELECT 'word.detail.read','word','WORD 문서 상세','P4 구현 예정',NULL,'PREPARING',51,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP WHERE NOT EXISTS(SELECT 1 FROM ap_permission_action WHERE code='word.detail.read');

INSERT INTO ap_feature_group(code,name,status,sort_order) SELECT 'word','word','PREPARING',100 WHERE NOT EXISTS(SELECT 1 FROM ap_feature_group WHERE code='word');

INSERT INTO ap_permission_action(code,group_code,name,description,binding_key,status,sort_order,created_at,updated_at) SELECT 'word.create.manual','word','WORD 직접 작성','P4 구현 예정',NULL,'PREPARING',52,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP WHERE NOT EXISTS(SELECT 1 FROM ap_permission_action WHERE code='word.create.manual');

INSERT INTO ap_feature_group(code,name,status,sort_order) SELECT 'word','word','PREPARING',100 WHERE NOT EXISTS(SELECT 1 FROM ap_feature_group WHERE code='word');

INSERT INTO ap_permission_action(code,group_code,name,description,binding_key,status,sort_order,created_at,updated_at) SELECT 'word.create.chat','word','WORD 채팅 생성','P4 구현 예정',NULL,'PREPARING',53,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP WHERE NOT EXISTS(SELECT 1 FROM ap_permission_action WHERE code='word.create.chat');

INSERT INTO ap_feature_group(code,name,status,sort_order) SELECT 'word','word','PREPARING',100 WHERE NOT EXISTS(SELECT 1 FROM ap_feature_group WHERE code='word');

INSERT INTO ap_permission_action(code,group_code,name,description,binding_key,status,sort_order,created_at,updated_at) SELECT 'word.create.photo','word','WORD 사진 생성','P4 구현 예정',NULL,'PREPARING',54,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP WHERE NOT EXISTS(SELECT 1 FROM ap_permission_action WHERE code='word.create.photo');

INSERT INTO ap_feature_group(code,name,status,sort_order) SELECT 'word','word','PREPARING',100 WHERE NOT EXISTS(SELECT 1 FROM ap_feature_group WHERE code='word');

INSERT INTO ap_permission_action(code,group_code,name,description,binding_key,status,sort_order,created_at,updated_at) SELECT 'word.create.file','word','WORD 원본 파일 가져오기','P4 구현 예정',NULL,'PREPARING',55,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP WHERE NOT EXISTS(SELECT 1 FROM ap_permission_action WHERE code='word.create.file');

INSERT INTO ap_feature_group(code,name,status,sort_order) SELECT 'word','word','PREPARING',100 WHERE NOT EXISTS(SELECT 1 FROM ap_feature_group WHERE code='word');

INSERT INTO ap_permission_action(code,group_code,name,description,binding_key,status,sort_order,created_at,updated_at) SELECT 'word.create.template','word','WORD 내 양식 생성','P4 구현 예정',NULL,'PREPARING',56,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP WHERE NOT EXISTS(SELECT 1 FROM ap_permission_action WHERE code='word.create.template');

INSERT INTO ap_feature_group(code,name,status,sort_order) SELECT 'word','word','PREPARING',100 WHERE NOT EXISTS(SELECT 1 FROM ap_feature_group WHERE code='word');

INSERT INTO ap_permission_action(code,group_code,name,description,binding_key,status,sort_order,created_at,updated_at) SELECT 'word.meta.update','word','WORD 이름 설명','P4 구현 예정',NULL,'PREPARING',57,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP WHERE NOT EXISTS(SELECT 1 FROM ap_permission_action WHERE code='word.meta.update');

INSERT INTO ap_feature_group(code,name,status,sort_order) SELECT 'word','word','PREPARING',100 WHERE NOT EXISTS(SELECT 1 FROM ap_feature_group WHERE code='word');

INSERT INTO ap_permission_action(code,group_code,name,description,binding_key,status,sort_order,created_at,updated_at) SELECT 'word.content.update','word','WORD 글·문단 직접 수정','P4 구현 예정',NULL,'PREPARING',58,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP WHERE NOT EXISTS(SELECT 1 FROM ap_permission_action WHERE code='word.content.update');

INSERT INTO ap_feature_group(code,name,status,sort_order) SELECT 'word','word','PREPARING',100 WHERE NOT EXISTS(SELECT 1 FROM ap_feature_group WHERE code='word');

INSERT INTO ap_permission_action(code,group_code,name,description,binding_key,status,sort_order,created_at,updated_at) SELECT 'word.object.create','word','WORD 표·도형·이미지 삽입','P4 구현 예정',NULL,'PREPARING',59,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP WHERE NOT EXISTS(SELECT 1 FROM ap_permission_action WHERE code='word.object.create');

INSERT INTO ap_feature_group(code,name,status,sort_order) SELECT 'word','word','PREPARING',100 WHERE NOT EXISTS(SELECT 1 FROM ap_feature_group WHERE code='word');

INSERT INTO ap_permission_action(code,group_code,name,description,binding_key,status,sort_order,created_at,updated_at) SELECT 'word.object.update','word','WORD 객체 속성 직접 수정','P4 구현 예정',NULL,'PREPARING',60,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP WHERE NOT EXISTS(SELECT 1 FROM ap_permission_action WHERE code='word.object.update');

INSERT INTO ap_feature_group(code,name,status,sort_order) SELECT 'word','word','PREPARING',100 WHERE NOT EXISTS(SELECT 1 FROM ap_feature_group WHERE code='word');

INSERT INTO ap_permission_action(code,group_code,name,description,binding_key,status,sort_order,created_at,updated_at) SELECT 'word.object.delete','word','WORD 객체 삭제','P4 구현 예정',NULL,'PREPARING',61,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP WHERE NOT EXISTS(SELECT 1 FROM ap_permission_action WHERE code='word.object.delete');

INSERT INTO ap_feature_group(code,name,status,sort_order) SELECT 'word','word','PREPARING',100 WHERE NOT EXISTS(SELECT 1 FROM ap_feature_group WHERE code='word');

INSERT INTO ap_permission_action(code,group_code,name,description,binding_key,status,sort_order,created_at,updated_at) SELECT 'word.edit.ai','word','WORD 선택 AI 변경 제안','P4 구현 예정',NULL,'PREPARING',62,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP WHERE NOT EXISTS(SELECT 1 FROM ap_permission_action WHERE code='word.edit.ai');

INSERT INTO ap_feature_group(code,name,status,sort_order) SELECT 'word','word','PREPARING',100 WHERE NOT EXISTS(SELECT 1 FROM ap_feature_group WHERE code='word');

INSERT INTO ap_permission_action(code,group_code,name,description,binding_key,status,sort_order,created_at,updated_at) SELECT 'word.version.read','word','WORD 버전 이력','P4 구현 예정',NULL,'PREPARING',63,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP WHERE NOT EXISTS(SELECT 1 FROM ap_permission_action WHERE code='word.version.read');

INSERT INTO ap_feature_group(code,name,status,sort_order) SELECT 'word','word','PREPARING',100 WHERE NOT EXISTS(SELECT 1 FROM ap_feature_group WHERE code='word');

INSERT INTO ap_permission_action(code,group_code,name,description,binding_key,status,sort_order,created_at,updated_at) SELECT 'word.version.restore','word','WORD 과거 버전으로 새 초안','P4 구현 예정',NULL,'PREPARING',64,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP WHERE NOT EXISTS(SELECT 1 FROM ap_permission_action WHERE code='word.version.restore');

INSERT INTO ap_feature_group(code,name,status,sort_order) SELECT 'word','word','PREPARING',100 WHERE NOT EXISTS(SELECT 1 FROM ap_feature_group WHERE code='word');

INSERT INTO ap_permission_action(code,group_code,name,description,binding_key,status,sort_order,created_at,updated_at) SELECT 'word.export.native','word','WORD 원본 형식 출력','P4 구현 예정',NULL,'PREPARING',65,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP WHERE NOT EXISTS(SELECT 1 FROM ap_permission_action WHERE code='word.export.native');

INSERT INTO ap_feature_group(code,name,status,sort_order) SELECT 'word','word','PREPARING',100 WHERE NOT EXISTS(SELECT 1 FROM ap_feature_group WHERE code='word');

INSERT INTO ap_permission_action(code,group_code,name,description,binding_key,status,sort_order,created_at,updated_at) SELECT 'word.export.pdf','word','WORD PDF 출력','P4 구현 예정',NULL,'PREPARING',66,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP WHERE NOT EXISTS(SELECT 1 FROM ap_permission_action WHERE code='word.export.pdf');

INSERT INTO ap_feature_group(code,name,status,sort_order) SELECT 'word','word','PREPARING',100 WHERE NOT EXISTS(SELECT 1 FROM ap_feature_group WHERE code='word');

INSERT INTO ap_permission_action(code,group_code,name,description,binding_key,status,sort_order,created_at,updated_at) SELECT 'word.template.manage','word','WORD 내 양식 관리','P4 구현 예정',NULL,'PREPARING',67,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP WHERE NOT EXISTS(SELECT 1 FROM ap_permission_action WHERE code='word.template.manage');

INSERT INTO ap_feature_group(code,name,status,sort_order) SELECT 'word','word','PREPARING',100 WHERE NOT EXISTS(SELECT 1 FROM ap_feature_group WHERE code='word');

INSERT INTO ap_permission_action(code,group_code,name,description,binding_key,status,sort_order,created_at,updated_at) SELECT 'word.resource.read','word','WORD 리소스/사용 위치 조회','P4 구현 예정',NULL,'PREPARING',68,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP WHERE NOT EXISTS(SELECT 1 FROM ap_permission_action WHERE code='word.resource.read');

INSERT INTO ap_feature_group(code,name,status,sort_order) SELECT 'word','word','PREPARING',100 WHERE NOT EXISTS(SELECT 1 FROM ap_feature_group WHERE code='word');

INSERT INTO ap_permission_action(code,group_code,name,description,binding_key,status,sort_order,created_at,updated_at) SELECT 'word.resource.create','word','WORD 리소스 추가','P4 구현 예정',NULL,'PREPARING',69,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP WHERE NOT EXISTS(SELECT 1 FROM ap_permission_action WHERE code='word.resource.create');

INSERT INTO ap_feature_group(code,name,status,sort_order) SELECT 'word','word','PREPARING',100 WHERE NOT EXISTS(SELECT 1 FROM ap_feature_group WHERE code='word');

INSERT INTO ap_permission_action(code,group_code,name,description,binding_key,status,sort_order,created_at,updated_at) SELECT 'word.resource.update','word','WORD 리소스 교체','P4 구현 예정',NULL,'PREPARING',70,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP WHERE NOT EXISTS(SELECT 1 FROM ap_permission_action WHERE code='word.resource.update');

INSERT INTO ap_feature_group(code,name,status,sort_order) SELECT 'word','word','PREPARING',100 WHERE NOT EXISTS(SELECT 1 FROM ap_feature_group WHERE code='word');

INSERT INTO ap_permission_action(code,group_code,name,description,binding_key,status,sort_order,created_at,updated_at) SELECT 'word.resource.delete','word','WORD 리소스 삭제','P4 구현 예정',NULL,'PREPARING',71,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP WHERE NOT EXISTS(SELECT 1 FROM ap_permission_action WHERE code='word.resource.delete');

INSERT INTO ap_feature_group(code,name,status,sort_order) SELECT 'word','word','PREPARING',100 WHERE NOT EXISTS(SELECT 1 FROM ap_feature_group WHERE code='word');

INSERT INTO ap_permission_action(code,group_code,name,description,binding_key,status,sort_order,created_at,updated_at) SELECT 'word.layout.manage','word','WORD 레이아웃 적용','P4 구현 예정',NULL,'PREPARING',72,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP WHERE NOT EXISTS(SELECT 1 FROM ap_permission_action WHERE code='word.layout.manage');

INSERT INTO ap_feature_group(code,name,status,sort_order) SELECT 'word','word','PREPARING',100 WHERE NOT EXISTS(SELECT 1 FROM ap_feature_group WHERE code='word');

INSERT INTO ap_permission_action(code,group_code,name,description,binding_key,status,sort_order,created_at,updated_at) SELECT 'word.data.link','word','WORD 자료 스냅샷 연결','P4 구현 예정',NULL,'PREPARING',73,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP WHERE NOT EXISTS(SELECT 1 FROM ap_permission_action WHERE code='word.data.link');

INSERT INTO ap_feature_group(code,name,status,sort_order) SELECT 'word','word','PREPARING',100 WHERE NOT EXISTS(SELECT 1 FROM ap_feature_group WHERE code='word');

INSERT INTO ap_permission_action(code,group_code,name,description,binding_key,status,sort_order,created_at,updated_at) SELECT 'word.data.refresh','word','WORD 연결 자료 갱신 초안','P4 구현 예정',NULL,'PREPARING',74,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP WHERE NOT EXISTS(SELECT 1 FROM ap_permission_action WHERE code='word.data.refresh');

INSERT INTO ap_feature_group(code,name,status,sort_order) SELECT 'word','word','PREPARING',100 WHERE NOT EXISTS(SELECT 1 FROM ap_feature_group WHERE code='word');

INSERT INTO ap_permission_action(code,group_code,name,description,binding_key,status,sort_order,created_at,updated_at) SELECT 'word.project.archive','word','WORD 보관','P4 구현 예정',NULL,'PREPARING',75,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP WHERE NOT EXISTS(SELECT 1 FROM ap_permission_action WHERE code='word.project.archive');

INSERT INTO ap_feature_group(code,name,status,sort_order) SELECT 'word','word','PREPARING',100 WHERE NOT EXISTS(SELECT 1 FROM ap_feature_group WHERE code='word');

INSERT INTO ap_permission_action(code,group_code,name,description,binding_key,status,sort_order,created_at,updated_at) SELECT 'word.project.delete','word','WORD 삭제','P4 구현 예정',NULL,'PREPARING',76,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP WHERE NOT EXISTS(SELECT 1 FROM ap_permission_action WHERE code='word.project.delete');

INSERT INTO ap_feature_group(code,name,status,sort_order) SELECT 'word','word','PREPARING',100 WHERE NOT EXISTS(SELECT 1 FROM ap_feature_group WHERE code='word');

INSERT INTO ap_permission_action(code,group_code,name,description,binding_key,status,sort_order,created_at,updated_at) SELECT 'word.project.restore','word','WORD 보관 복원','P4 구현 예정',NULL,'PREPARING',77,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP WHERE NOT EXISTS(SELECT 1 FROM ap_permission_action WHERE code='word.project.restore');

INSERT INTO ap_feature_group(code,name,status,sort_order) SELECT 'word','word','PREPARING',100 WHERE NOT EXISTS(SELECT 1 FROM ap_feature_group WHERE code='word');

INSERT INTO ap_permission_action(code,group_code,name,description,binding_key,status,sort_order,created_at,updated_at) SELECT 'word.project.duplicate','word','WORD 구조 복제','P4 구현 예정',NULL,'PREPARING',78,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP WHERE NOT EXISTS(SELECT 1 FROM ap_permission_action WHERE code='word.project.duplicate');

INSERT INTO ap_feature_group(code,name,status,sort_order) SELECT 'hwp','hwp','PREPARING',100 WHERE NOT EXISTS(SELECT 1 FROM ap_feature_group WHERE code='hwp');

INSERT INTO ap_permission_action(code,group_code,name,description,binding_key,status,sort_order,created_at,updated_at) SELECT 'hwp.overview.read','hwp','HWP 오버뷰','P4 구현 예정',NULL,'PREPARING',79,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP WHERE NOT EXISTS(SELECT 1 FROM ap_permission_action WHERE code='hwp.overview.read');

INSERT INTO ap_feature_group(code,name,status,sort_order) SELECT 'hwp','hwp','PREPARING',100 WHERE NOT EXISTS(SELECT 1 FROM ap_feature_group WHERE code='hwp');

INSERT INTO ap_permission_action(code,group_code,name,description,binding_key,status,sort_order,created_at,updated_at) SELECT 'hwp.list.read','hwp','HWP 목록','P4 구현 예정',NULL,'PREPARING',80,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP WHERE NOT EXISTS(SELECT 1 FROM ap_permission_action WHERE code='hwp.list.read');

INSERT INTO ap_feature_group(code,name,status,sort_order) SELECT 'hwp','hwp','PREPARING',100 WHERE NOT EXISTS(SELECT 1 FROM ap_feature_group WHERE code='hwp');

INSERT INTO ap_permission_action(code,group_code,name,description,binding_key,status,sort_order,created_at,updated_at) SELECT 'hwp.detail.read','hwp','HWP 문서 상세','P4 구현 예정',NULL,'PREPARING',81,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP WHERE NOT EXISTS(SELECT 1 FROM ap_permission_action WHERE code='hwp.detail.read');

INSERT INTO ap_feature_group(code,name,status,sort_order) SELECT 'hwp','hwp','PREPARING',100 WHERE NOT EXISTS(SELECT 1 FROM ap_feature_group WHERE code='hwp');

INSERT INTO ap_permission_action(code,group_code,name,description,binding_key,status,sort_order,created_at,updated_at) SELECT 'hwp.create.manual','hwp','HWP 직접 작성','P4 구현 예정',NULL,'PREPARING',82,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP WHERE NOT EXISTS(SELECT 1 FROM ap_permission_action WHERE code='hwp.create.manual');

INSERT INTO ap_feature_group(code,name,status,sort_order) SELECT 'hwp','hwp','PREPARING',100 WHERE NOT EXISTS(SELECT 1 FROM ap_feature_group WHERE code='hwp');

INSERT INTO ap_permission_action(code,group_code,name,description,binding_key,status,sort_order,created_at,updated_at) SELECT 'hwp.create.chat','hwp','HWP 채팅 생성','P4 구현 예정',NULL,'PREPARING',83,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP WHERE NOT EXISTS(SELECT 1 FROM ap_permission_action WHERE code='hwp.create.chat');

INSERT INTO ap_feature_group(code,name,status,sort_order) SELECT 'hwp','hwp','PREPARING',100 WHERE NOT EXISTS(SELECT 1 FROM ap_feature_group WHERE code='hwp');

INSERT INTO ap_permission_action(code,group_code,name,description,binding_key,status,sort_order,created_at,updated_at) SELECT 'hwp.create.photo','hwp','HWP 사진 생성','P4 구현 예정',NULL,'PREPARING',84,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP WHERE NOT EXISTS(SELECT 1 FROM ap_permission_action WHERE code='hwp.create.photo');

INSERT INTO ap_feature_group(code,name,status,sort_order) SELECT 'hwp','hwp','PREPARING',100 WHERE NOT EXISTS(SELECT 1 FROM ap_feature_group WHERE code='hwp');

INSERT INTO ap_permission_action(code,group_code,name,description,binding_key,status,sort_order,created_at,updated_at) SELECT 'hwp.create.file','hwp','HWP 원본 파일 가져오기','P4 구현 예정',NULL,'PREPARING',85,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP WHERE NOT EXISTS(SELECT 1 FROM ap_permission_action WHERE code='hwp.create.file');

INSERT INTO ap_feature_group(code,name,status,sort_order) SELECT 'hwp','hwp','PREPARING',100 WHERE NOT EXISTS(SELECT 1 FROM ap_feature_group WHERE code='hwp');

INSERT INTO ap_permission_action(code,group_code,name,description,binding_key,status,sort_order,created_at,updated_at) SELECT 'hwp.create.template','hwp','HWP 내 양식 생성','P4 구현 예정',NULL,'PREPARING',86,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP WHERE NOT EXISTS(SELECT 1 FROM ap_permission_action WHERE code='hwp.create.template');

INSERT INTO ap_feature_group(code,name,status,sort_order) SELECT 'hwp','hwp','PREPARING',100 WHERE NOT EXISTS(SELECT 1 FROM ap_feature_group WHERE code='hwp');

INSERT INTO ap_permission_action(code,group_code,name,description,binding_key,status,sort_order,created_at,updated_at) SELECT 'hwp.meta.update','hwp','HWP 이름 설명','P4 구현 예정',NULL,'PREPARING',87,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP WHERE NOT EXISTS(SELECT 1 FROM ap_permission_action WHERE code='hwp.meta.update');

INSERT INTO ap_feature_group(code,name,status,sort_order) SELECT 'hwp','hwp','PREPARING',100 WHERE NOT EXISTS(SELECT 1 FROM ap_feature_group WHERE code='hwp');

INSERT INTO ap_permission_action(code,group_code,name,description,binding_key,status,sort_order,created_at,updated_at) SELECT 'hwp.content.update','hwp','HWP 글·문단 직접 수정','P4 구현 예정',NULL,'PREPARING',88,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP WHERE NOT EXISTS(SELECT 1 FROM ap_permission_action WHERE code='hwp.content.update');

INSERT INTO ap_feature_group(code,name,status,sort_order) SELECT 'hwp','hwp','PREPARING',100 WHERE NOT EXISTS(SELECT 1 FROM ap_feature_group WHERE code='hwp');

INSERT INTO ap_permission_action(code,group_code,name,description,binding_key,status,sort_order,created_at,updated_at) SELECT 'hwp.object.create','hwp','HWP 표·도형·이미지 삽입','P4 구현 예정',NULL,'PREPARING',89,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP WHERE NOT EXISTS(SELECT 1 FROM ap_permission_action WHERE code='hwp.object.create');

INSERT INTO ap_feature_group(code,name,status,sort_order) SELECT 'hwp','hwp','PREPARING',100 WHERE NOT EXISTS(SELECT 1 FROM ap_feature_group WHERE code='hwp');

INSERT INTO ap_permission_action(code,group_code,name,description,binding_key,status,sort_order,created_at,updated_at) SELECT 'hwp.object.update','hwp','HWP 객체 속성 직접 수정','P4 구현 예정',NULL,'PREPARING',90,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP WHERE NOT EXISTS(SELECT 1 FROM ap_permission_action WHERE code='hwp.object.update');

INSERT INTO ap_feature_group(code,name,status,sort_order) SELECT 'hwp','hwp','PREPARING',100 WHERE NOT EXISTS(SELECT 1 FROM ap_feature_group WHERE code='hwp');

INSERT INTO ap_permission_action(code,group_code,name,description,binding_key,status,sort_order,created_at,updated_at) SELECT 'hwp.object.delete','hwp','HWP 객체 삭제','P4 구현 예정',NULL,'PREPARING',91,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP WHERE NOT EXISTS(SELECT 1 FROM ap_permission_action WHERE code='hwp.object.delete');

INSERT INTO ap_feature_group(code,name,status,sort_order) SELECT 'hwp','hwp','PREPARING',100 WHERE NOT EXISTS(SELECT 1 FROM ap_feature_group WHERE code='hwp');

INSERT INTO ap_permission_action(code,group_code,name,description,binding_key,status,sort_order,created_at,updated_at) SELECT 'hwp.edit.ai','hwp','HWP 선택 AI 변경 제안','P4 구현 예정',NULL,'PREPARING',92,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP WHERE NOT EXISTS(SELECT 1 FROM ap_permission_action WHERE code='hwp.edit.ai');

INSERT INTO ap_feature_group(code,name,status,sort_order) SELECT 'hwp','hwp','PREPARING',100 WHERE NOT EXISTS(SELECT 1 FROM ap_feature_group WHERE code='hwp');

INSERT INTO ap_permission_action(code,group_code,name,description,binding_key,status,sort_order,created_at,updated_at) SELECT 'hwp.version.read','hwp','HWP 버전 이력','P4 구현 예정',NULL,'PREPARING',93,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP WHERE NOT EXISTS(SELECT 1 FROM ap_permission_action WHERE code='hwp.version.read');

INSERT INTO ap_feature_group(code,name,status,sort_order) SELECT 'hwp','hwp','PREPARING',100 WHERE NOT EXISTS(SELECT 1 FROM ap_feature_group WHERE code='hwp');

INSERT INTO ap_permission_action(code,group_code,name,description,binding_key,status,sort_order,created_at,updated_at) SELECT 'hwp.version.restore','hwp','HWP 과거 버전으로 새 초안','P4 구현 예정',NULL,'PREPARING',94,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP WHERE NOT EXISTS(SELECT 1 FROM ap_permission_action WHERE code='hwp.version.restore');

INSERT INTO ap_feature_group(code,name,status,sort_order) SELECT 'hwp','hwp','PREPARING',100 WHERE NOT EXISTS(SELECT 1 FROM ap_feature_group WHERE code='hwp');

INSERT INTO ap_permission_action(code,group_code,name,description,binding_key,status,sort_order,created_at,updated_at) SELECT 'hwp.export.native','hwp','HWP 원본 형식 출력','P4 구현 예정',NULL,'PREPARING',95,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP WHERE NOT EXISTS(SELECT 1 FROM ap_permission_action WHERE code='hwp.export.native');

INSERT INTO ap_feature_group(code,name,status,sort_order) SELECT 'hwp','hwp','PREPARING',100 WHERE NOT EXISTS(SELECT 1 FROM ap_feature_group WHERE code='hwp');

INSERT INTO ap_permission_action(code,group_code,name,description,binding_key,status,sort_order,created_at,updated_at) SELECT 'hwp.export.pdf','hwp','HWP PDF 출력','P4 구현 예정',NULL,'PREPARING',96,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP WHERE NOT EXISTS(SELECT 1 FROM ap_permission_action WHERE code='hwp.export.pdf');

INSERT INTO ap_feature_group(code,name,status,sort_order) SELECT 'hwp','hwp','PREPARING',100 WHERE NOT EXISTS(SELECT 1 FROM ap_feature_group WHERE code='hwp');

INSERT INTO ap_permission_action(code,group_code,name,description,binding_key,status,sort_order,created_at,updated_at) SELECT 'hwp.template.manage','hwp','HWP 내 양식 관리','P4 구현 예정',NULL,'PREPARING',97,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP WHERE NOT EXISTS(SELECT 1 FROM ap_permission_action WHERE code='hwp.template.manage');

INSERT INTO ap_feature_group(code,name,status,sort_order) SELECT 'hwp','hwp','PREPARING',100 WHERE NOT EXISTS(SELECT 1 FROM ap_feature_group WHERE code='hwp');

INSERT INTO ap_permission_action(code,group_code,name,description,binding_key,status,sort_order,created_at,updated_at) SELECT 'hwp.resource.read','hwp','HWP 리소스/사용 위치 조회','P4 구현 예정',NULL,'PREPARING',98,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP WHERE NOT EXISTS(SELECT 1 FROM ap_permission_action WHERE code='hwp.resource.read');

INSERT INTO ap_feature_group(code,name,status,sort_order) SELECT 'hwp','hwp','PREPARING',100 WHERE NOT EXISTS(SELECT 1 FROM ap_feature_group WHERE code='hwp');

INSERT INTO ap_permission_action(code,group_code,name,description,binding_key,status,sort_order,created_at,updated_at) SELECT 'hwp.resource.create','hwp','HWP 리소스 추가','P4 구현 예정',NULL,'PREPARING',99,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP WHERE NOT EXISTS(SELECT 1 FROM ap_permission_action WHERE code='hwp.resource.create');

INSERT INTO ap_feature_group(code,name,status,sort_order) SELECT 'hwp','hwp','PREPARING',100 WHERE NOT EXISTS(SELECT 1 FROM ap_feature_group WHERE code='hwp');

INSERT INTO ap_permission_action(code,group_code,name,description,binding_key,status,sort_order,created_at,updated_at) SELECT 'hwp.resource.update','hwp','HWP 리소스 교체','P4 구현 예정',NULL,'PREPARING',100,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP WHERE NOT EXISTS(SELECT 1 FROM ap_permission_action WHERE code='hwp.resource.update');

INSERT INTO ap_feature_group(code,name,status,sort_order) SELECT 'hwp','hwp','PREPARING',100 WHERE NOT EXISTS(SELECT 1 FROM ap_feature_group WHERE code='hwp');

INSERT INTO ap_permission_action(code,group_code,name,description,binding_key,status,sort_order,created_at,updated_at) SELECT 'hwp.resource.delete','hwp','HWP 리소스 삭제','P4 구현 예정',NULL,'PREPARING',101,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP WHERE NOT EXISTS(SELECT 1 FROM ap_permission_action WHERE code='hwp.resource.delete');

INSERT INTO ap_feature_group(code,name,status,sort_order) SELECT 'hwp','hwp','PREPARING',100 WHERE NOT EXISTS(SELECT 1 FROM ap_feature_group WHERE code='hwp');

INSERT INTO ap_permission_action(code,group_code,name,description,binding_key,status,sort_order,created_at,updated_at) SELECT 'hwp.layout.manage','hwp','HWP 레이아웃 적용','P4 구현 예정',NULL,'PREPARING',102,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP WHERE NOT EXISTS(SELECT 1 FROM ap_permission_action WHERE code='hwp.layout.manage');

INSERT INTO ap_feature_group(code,name,status,sort_order) SELECT 'hwp','hwp','PREPARING',100 WHERE NOT EXISTS(SELECT 1 FROM ap_feature_group WHERE code='hwp');

INSERT INTO ap_permission_action(code,group_code,name,description,binding_key,status,sort_order,created_at,updated_at) SELECT 'hwp.data.link','hwp','HWP 자료 스냅샷 연결','P4 구현 예정',NULL,'PREPARING',103,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP WHERE NOT EXISTS(SELECT 1 FROM ap_permission_action WHERE code='hwp.data.link');

INSERT INTO ap_feature_group(code,name,status,sort_order) SELECT 'hwp','hwp','PREPARING',100 WHERE NOT EXISTS(SELECT 1 FROM ap_feature_group WHERE code='hwp');

INSERT INTO ap_permission_action(code,group_code,name,description,binding_key,status,sort_order,created_at,updated_at) SELECT 'hwp.data.refresh','hwp','HWP 연결 자료 갱신 초안','P4 구현 예정',NULL,'PREPARING',104,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP WHERE NOT EXISTS(SELECT 1 FROM ap_permission_action WHERE code='hwp.data.refresh');

INSERT INTO ap_feature_group(code,name,status,sort_order) SELECT 'hwp','hwp','PREPARING',100 WHERE NOT EXISTS(SELECT 1 FROM ap_feature_group WHERE code='hwp');

INSERT INTO ap_permission_action(code,group_code,name,description,binding_key,status,sort_order,created_at,updated_at) SELECT 'hwp.project.archive','hwp','HWP 보관','P4 구현 예정',NULL,'PREPARING',105,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP WHERE NOT EXISTS(SELECT 1 FROM ap_permission_action WHERE code='hwp.project.archive');

INSERT INTO ap_feature_group(code,name,status,sort_order) SELECT 'hwp','hwp','PREPARING',100 WHERE NOT EXISTS(SELECT 1 FROM ap_feature_group WHERE code='hwp');

INSERT INTO ap_permission_action(code,group_code,name,description,binding_key,status,sort_order,created_at,updated_at) SELECT 'hwp.project.delete','hwp','HWP 삭제','P4 구현 예정',NULL,'PREPARING',106,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP WHERE NOT EXISTS(SELECT 1 FROM ap_permission_action WHERE code='hwp.project.delete');

INSERT INTO ap_feature_group(code,name,status,sort_order) SELECT 'hwp','hwp','PREPARING',100 WHERE NOT EXISTS(SELECT 1 FROM ap_feature_group WHERE code='hwp');

INSERT INTO ap_permission_action(code,group_code,name,description,binding_key,status,sort_order,created_at,updated_at) SELECT 'hwp.project.restore','hwp','HWP 보관 복원','P4 구현 예정',NULL,'PREPARING',107,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP WHERE NOT EXISTS(SELECT 1 FROM ap_permission_action WHERE code='hwp.project.restore');

INSERT INTO ap_feature_group(code,name,status,sort_order) SELECT 'hwp','hwp','PREPARING',100 WHERE NOT EXISTS(SELECT 1 FROM ap_feature_group WHERE code='hwp');

INSERT INTO ap_permission_action(code,group_code,name,description,binding_key,status,sort_order,created_at,updated_at) SELECT 'hwp.project.duplicate','hwp','HWP 구조 복제','P4 구현 예정',NULL,'PREPARING',108,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP WHERE NOT EXISTS(SELECT 1 FROM ap_permission_action WHERE code='hwp.project.duplicate');

INSERT INTO ap_feature_group(code,name,status,sort_order) SELECT 'ppt','ppt','PREPARING',100 WHERE NOT EXISTS(SELECT 1 FROM ap_feature_group WHERE code='ppt');

INSERT INTO ap_permission_action(code,group_code,name,description,binding_key,status,sort_order,created_at,updated_at) SELECT 'ppt.overview.read','ppt','PPT 오버뷰','P4 구현 예정',NULL,'PREPARING',109,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP WHERE NOT EXISTS(SELECT 1 FROM ap_permission_action WHERE code='ppt.overview.read');

INSERT INTO ap_feature_group(code,name,status,sort_order) SELECT 'ppt','ppt','PREPARING',100 WHERE NOT EXISTS(SELECT 1 FROM ap_feature_group WHERE code='ppt');

INSERT INTO ap_permission_action(code,group_code,name,description,binding_key,status,sort_order,created_at,updated_at) SELECT 'ppt.list.read','ppt','PPT 목록','P4 구현 예정',NULL,'PREPARING',110,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP WHERE NOT EXISTS(SELECT 1 FROM ap_permission_action WHERE code='ppt.list.read');

INSERT INTO ap_feature_group(code,name,status,sort_order) SELECT 'ppt','ppt','PREPARING',100 WHERE NOT EXISTS(SELECT 1 FROM ap_feature_group WHERE code='ppt');

INSERT INTO ap_permission_action(code,group_code,name,description,binding_key,status,sort_order,created_at,updated_at) SELECT 'ppt.detail.read','ppt','PPT 문서 상세','P4 구현 예정',NULL,'PREPARING',111,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP WHERE NOT EXISTS(SELECT 1 FROM ap_permission_action WHERE code='ppt.detail.read');

INSERT INTO ap_feature_group(code,name,status,sort_order) SELECT 'ppt','ppt','PREPARING',100 WHERE NOT EXISTS(SELECT 1 FROM ap_feature_group WHERE code='ppt');

INSERT INTO ap_permission_action(code,group_code,name,description,binding_key,status,sort_order,created_at,updated_at) SELECT 'ppt.create.manual','ppt','PPT 직접 작성','P4 구현 예정',NULL,'PREPARING',112,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP WHERE NOT EXISTS(SELECT 1 FROM ap_permission_action WHERE code='ppt.create.manual');

INSERT INTO ap_feature_group(code,name,status,sort_order) SELECT 'ppt','ppt','PREPARING',100 WHERE NOT EXISTS(SELECT 1 FROM ap_feature_group WHERE code='ppt');

INSERT INTO ap_permission_action(code,group_code,name,description,binding_key,status,sort_order,created_at,updated_at) SELECT 'ppt.create.chat','ppt','PPT 채팅 생성','P4 구현 예정',NULL,'PREPARING',113,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP WHERE NOT EXISTS(SELECT 1 FROM ap_permission_action WHERE code='ppt.create.chat');

INSERT INTO ap_feature_group(code,name,status,sort_order) SELECT 'ppt','ppt','PREPARING',100 WHERE NOT EXISTS(SELECT 1 FROM ap_feature_group WHERE code='ppt');

INSERT INTO ap_permission_action(code,group_code,name,description,binding_key,status,sort_order,created_at,updated_at) SELECT 'ppt.create.photo','ppt','PPT 사진 생성','P4 구현 예정',NULL,'PREPARING',114,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP WHERE NOT EXISTS(SELECT 1 FROM ap_permission_action WHERE code='ppt.create.photo');

INSERT INTO ap_feature_group(code,name,status,sort_order) SELECT 'ppt','ppt','PREPARING',100 WHERE NOT EXISTS(SELECT 1 FROM ap_feature_group WHERE code='ppt');

INSERT INTO ap_permission_action(code,group_code,name,description,binding_key,status,sort_order,created_at,updated_at) SELECT 'ppt.create.file','ppt','PPT 원본 파일 가져오기','P4 구현 예정',NULL,'PREPARING',115,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP WHERE NOT EXISTS(SELECT 1 FROM ap_permission_action WHERE code='ppt.create.file');

INSERT INTO ap_feature_group(code,name,status,sort_order) SELECT 'ppt','ppt','PREPARING',100 WHERE NOT EXISTS(SELECT 1 FROM ap_feature_group WHERE code='ppt');

INSERT INTO ap_permission_action(code,group_code,name,description,binding_key,status,sort_order,created_at,updated_at) SELECT 'ppt.create.template','ppt','PPT 내 양식 생성','P4 구현 예정',NULL,'PREPARING',116,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP WHERE NOT EXISTS(SELECT 1 FROM ap_permission_action WHERE code='ppt.create.template');

INSERT INTO ap_feature_group(code,name,status,sort_order) SELECT 'ppt','ppt','PREPARING',100 WHERE NOT EXISTS(SELECT 1 FROM ap_feature_group WHERE code='ppt');

INSERT INTO ap_permission_action(code,group_code,name,description,binding_key,status,sort_order,created_at,updated_at) SELECT 'ppt.meta.update','ppt','PPT 이름 설명','P4 구현 예정',NULL,'PREPARING',117,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP WHERE NOT EXISTS(SELECT 1 FROM ap_permission_action WHERE code='ppt.meta.update');

INSERT INTO ap_feature_group(code,name,status,sort_order) SELECT 'ppt','ppt','PREPARING',100 WHERE NOT EXISTS(SELECT 1 FROM ap_feature_group WHERE code='ppt');

INSERT INTO ap_permission_action(code,group_code,name,description,binding_key,status,sort_order,created_at,updated_at) SELECT 'ppt.content.update','ppt','PPT 글·문단 직접 수정','P4 구현 예정',NULL,'PREPARING',118,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP WHERE NOT EXISTS(SELECT 1 FROM ap_permission_action WHERE code='ppt.content.update');

INSERT INTO ap_feature_group(code,name,status,sort_order) SELECT 'ppt','ppt','PREPARING',100 WHERE NOT EXISTS(SELECT 1 FROM ap_feature_group WHERE code='ppt');

INSERT INTO ap_permission_action(code,group_code,name,description,binding_key,status,sort_order,created_at,updated_at) SELECT 'ppt.object.create','ppt','PPT 표·도형·이미지 삽입','P4 구현 예정',NULL,'PREPARING',119,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP WHERE NOT EXISTS(SELECT 1 FROM ap_permission_action WHERE code='ppt.object.create');

INSERT INTO ap_feature_group(code,name,status,sort_order) SELECT 'ppt','ppt','PREPARING',100 WHERE NOT EXISTS(SELECT 1 FROM ap_feature_group WHERE code='ppt');

INSERT INTO ap_permission_action(code,group_code,name,description,binding_key,status,sort_order,created_at,updated_at) SELECT 'ppt.object.update','ppt','PPT 객체 속성 직접 수정','P4 구현 예정',NULL,'PREPARING',120,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP WHERE NOT EXISTS(SELECT 1 FROM ap_permission_action WHERE code='ppt.object.update');

INSERT INTO ap_feature_group(code,name,status,sort_order) SELECT 'ppt','ppt','PREPARING',100 WHERE NOT EXISTS(SELECT 1 FROM ap_feature_group WHERE code='ppt');

INSERT INTO ap_permission_action(code,group_code,name,description,binding_key,status,sort_order,created_at,updated_at) SELECT 'ppt.object.delete','ppt','PPT 객체 삭제','P4 구현 예정',NULL,'PREPARING',121,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP WHERE NOT EXISTS(SELECT 1 FROM ap_permission_action WHERE code='ppt.object.delete');

INSERT INTO ap_feature_group(code,name,status,sort_order) SELECT 'ppt','ppt','PREPARING',100 WHERE NOT EXISTS(SELECT 1 FROM ap_feature_group WHERE code='ppt');

INSERT INTO ap_permission_action(code,group_code,name,description,binding_key,status,sort_order,created_at,updated_at) SELECT 'ppt.edit.ai','ppt','PPT 선택 AI 변경 제안','P4 구현 예정',NULL,'PREPARING',122,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP WHERE NOT EXISTS(SELECT 1 FROM ap_permission_action WHERE code='ppt.edit.ai');

INSERT INTO ap_feature_group(code,name,status,sort_order) SELECT 'ppt','ppt','PREPARING',100 WHERE NOT EXISTS(SELECT 1 FROM ap_feature_group WHERE code='ppt');

INSERT INTO ap_permission_action(code,group_code,name,description,binding_key,status,sort_order,created_at,updated_at) SELECT 'ppt.version.read','ppt','PPT 버전 이력','P4 구현 예정',NULL,'PREPARING',123,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP WHERE NOT EXISTS(SELECT 1 FROM ap_permission_action WHERE code='ppt.version.read');

INSERT INTO ap_feature_group(code,name,status,sort_order) SELECT 'ppt','ppt','PREPARING',100 WHERE NOT EXISTS(SELECT 1 FROM ap_feature_group WHERE code='ppt');

INSERT INTO ap_permission_action(code,group_code,name,description,binding_key,status,sort_order,created_at,updated_at) SELECT 'ppt.version.restore','ppt','PPT 과거 버전으로 새 초안','P4 구현 예정',NULL,'PREPARING',124,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP WHERE NOT EXISTS(SELECT 1 FROM ap_permission_action WHERE code='ppt.version.restore');

INSERT INTO ap_feature_group(code,name,status,sort_order) SELECT 'ppt','ppt','PREPARING',100 WHERE NOT EXISTS(SELECT 1 FROM ap_feature_group WHERE code='ppt');

INSERT INTO ap_permission_action(code,group_code,name,description,binding_key,status,sort_order,created_at,updated_at) SELECT 'ppt.export.native','ppt','PPT 원본 형식 출력','P4 구현 예정',NULL,'PREPARING',125,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP WHERE NOT EXISTS(SELECT 1 FROM ap_permission_action WHERE code='ppt.export.native');

INSERT INTO ap_feature_group(code,name,status,sort_order) SELECT 'ppt','ppt','PREPARING',100 WHERE NOT EXISTS(SELECT 1 FROM ap_feature_group WHERE code='ppt');

INSERT INTO ap_permission_action(code,group_code,name,description,binding_key,status,sort_order,created_at,updated_at) SELECT 'ppt.export.pdf','ppt','PPT PDF 출력','P4 구현 예정',NULL,'PREPARING',126,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP WHERE NOT EXISTS(SELECT 1 FROM ap_permission_action WHERE code='ppt.export.pdf');

INSERT INTO ap_feature_group(code,name,status,sort_order) SELECT 'ppt','ppt','PREPARING',100 WHERE NOT EXISTS(SELECT 1 FROM ap_feature_group WHERE code='ppt');

INSERT INTO ap_permission_action(code,group_code,name,description,binding_key,status,sort_order,created_at,updated_at) SELECT 'ppt.template.manage','ppt','PPT 내 양식 관리','P4 구현 예정',NULL,'PREPARING',127,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP WHERE NOT EXISTS(SELECT 1 FROM ap_permission_action WHERE code='ppt.template.manage');

INSERT INTO ap_feature_group(code,name,status,sort_order) SELECT 'ppt','ppt','PREPARING',100 WHERE NOT EXISTS(SELECT 1 FROM ap_feature_group WHERE code='ppt');

INSERT INTO ap_permission_action(code,group_code,name,description,binding_key,status,sort_order,created_at,updated_at) SELECT 'ppt.resource.read','ppt','PPT 리소스/사용 위치 조회','P4 구현 예정',NULL,'PREPARING',128,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP WHERE NOT EXISTS(SELECT 1 FROM ap_permission_action WHERE code='ppt.resource.read');

INSERT INTO ap_feature_group(code,name,status,sort_order) SELECT 'ppt','ppt','PREPARING',100 WHERE NOT EXISTS(SELECT 1 FROM ap_feature_group WHERE code='ppt');

INSERT INTO ap_permission_action(code,group_code,name,description,binding_key,status,sort_order,created_at,updated_at) SELECT 'ppt.resource.create','ppt','PPT 리소스 추가','P4 구현 예정',NULL,'PREPARING',129,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP WHERE NOT EXISTS(SELECT 1 FROM ap_permission_action WHERE code='ppt.resource.create');

INSERT INTO ap_feature_group(code,name,status,sort_order) SELECT 'ppt','ppt','PREPARING',100 WHERE NOT EXISTS(SELECT 1 FROM ap_feature_group WHERE code='ppt');

INSERT INTO ap_permission_action(code,group_code,name,description,binding_key,status,sort_order,created_at,updated_at) SELECT 'ppt.resource.update','ppt','PPT 리소스 교체','P4 구현 예정',NULL,'PREPARING',130,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP WHERE NOT EXISTS(SELECT 1 FROM ap_permission_action WHERE code='ppt.resource.update');

INSERT INTO ap_feature_group(code,name,status,sort_order) SELECT 'ppt','ppt','PREPARING',100 WHERE NOT EXISTS(SELECT 1 FROM ap_feature_group WHERE code='ppt');

INSERT INTO ap_permission_action(code,group_code,name,description,binding_key,status,sort_order,created_at,updated_at) SELECT 'ppt.resource.delete','ppt','PPT 리소스 삭제','P4 구현 예정',NULL,'PREPARING',131,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP WHERE NOT EXISTS(SELECT 1 FROM ap_permission_action WHERE code='ppt.resource.delete');

INSERT INTO ap_feature_group(code,name,status,sort_order) SELECT 'ppt','ppt','PREPARING',100 WHERE NOT EXISTS(SELECT 1 FROM ap_feature_group WHERE code='ppt');

INSERT INTO ap_permission_action(code,group_code,name,description,binding_key,status,sort_order,created_at,updated_at) SELECT 'ppt.layout.manage','ppt','PPT 레이아웃 적용','P4 구현 예정',NULL,'PREPARING',132,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP WHERE NOT EXISTS(SELECT 1 FROM ap_permission_action WHERE code='ppt.layout.manage');

INSERT INTO ap_feature_group(code,name,status,sort_order) SELECT 'ppt','ppt','PREPARING',100 WHERE NOT EXISTS(SELECT 1 FROM ap_feature_group WHERE code='ppt');

INSERT INTO ap_permission_action(code,group_code,name,description,binding_key,status,sort_order,created_at,updated_at) SELECT 'ppt.data.link','ppt','PPT 자료 스냅샷 연결','P4 구현 예정',NULL,'PREPARING',133,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP WHERE NOT EXISTS(SELECT 1 FROM ap_permission_action WHERE code='ppt.data.link');

INSERT INTO ap_feature_group(code,name,status,sort_order) SELECT 'ppt','ppt','PREPARING',100 WHERE NOT EXISTS(SELECT 1 FROM ap_feature_group WHERE code='ppt');

INSERT INTO ap_permission_action(code,group_code,name,description,binding_key,status,sort_order,created_at,updated_at) SELECT 'ppt.data.refresh','ppt','PPT 연결 자료 갱신 초안','P4 구현 예정',NULL,'PREPARING',134,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP WHERE NOT EXISTS(SELECT 1 FROM ap_permission_action WHERE code='ppt.data.refresh');

INSERT INTO ap_feature_group(code,name,status,sort_order) SELECT 'ppt','ppt','PREPARING',100 WHERE NOT EXISTS(SELECT 1 FROM ap_feature_group WHERE code='ppt');

INSERT INTO ap_permission_action(code,group_code,name,description,binding_key,status,sort_order,created_at,updated_at) SELECT 'ppt.project.archive','ppt','PPT 보관','P4 구현 예정',NULL,'PREPARING',135,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP WHERE NOT EXISTS(SELECT 1 FROM ap_permission_action WHERE code='ppt.project.archive');

INSERT INTO ap_feature_group(code,name,status,sort_order) SELECT 'ppt','ppt','PREPARING',100 WHERE NOT EXISTS(SELECT 1 FROM ap_feature_group WHERE code='ppt');

INSERT INTO ap_permission_action(code,group_code,name,description,binding_key,status,sort_order,created_at,updated_at) SELECT 'ppt.project.delete','ppt','PPT 삭제','P4 구현 예정',NULL,'PREPARING',136,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP WHERE NOT EXISTS(SELECT 1 FROM ap_permission_action WHERE code='ppt.project.delete');

INSERT INTO ap_feature_group(code,name,status,sort_order) SELECT 'ppt','ppt','PREPARING',100 WHERE NOT EXISTS(SELECT 1 FROM ap_feature_group WHERE code='ppt');

INSERT INTO ap_permission_action(code,group_code,name,description,binding_key,status,sort_order,created_at,updated_at) SELECT 'ppt.project.restore','ppt','PPT 보관 복원','P4 구현 예정',NULL,'PREPARING',137,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP WHERE NOT EXISTS(SELECT 1 FROM ap_permission_action WHERE code='ppt.project.restore');

INSERT INTO ap_feature_group(code,name,status,sort_order) SELECT 'ppt','ppt','PREPARING',100 WHERE NOT EXISTS(SELECT 1 FROM ap_feature_group WHERE code='ppt');

INSERT INTO ap_permission_action(code,group_code,name,description,binding_key,status,sort_order,created_at,updated_at) SELECT 'ppt.project.duplicate','ppt','PPT 구조 복제','P4 구현 예정',NULL,'PREPARING',138,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP WHERE NOT EXISTS(SELECT 1 FROM ap_permission_action WHERE code='ppt.project.duplicate');

INSERT INTO ap_feature_group(code,name,status,sort_order) SELECT 'crm','crm','PREPARING',100 WHERE NOT EXISTS(SELECT 1 FROM ap_feature_group WHERE code='crm');

INSERT INTO ap_permission_action(code,group_code,name,description,binding_key,status,sort_order,created_at,updated_at) SELECT 'crm.overview.read','crm','전체오버뷰','P3 구현 예정',NULL,'PREPARING',139,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP WHERE NOT EXISTS(SELECT 1 FROM ap_permission_action WHERE code='crm.overview.read');

INSERT INTO ap_feature_group(code,name,status,sort_order) SELECT 'crm','crm','PREPARING',100 WHERE NOT EXISTS(SELECT 1 FROM ap_feature_group WHERE code='crm');

INSERT INTO ap_permission_action(code,group_code,name,description,binding_key,status,sort_order,created_at,updated_at) SELECT 'crm.list.read','crm','CRM목록','P3 구현 예정',NULL,'PREPARING',140,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP WHERE NOT EXISTS(SELECT 1 FROM ap_permission_action WHERE code='crm.list.read');

INSERT INTO ap_feature_group(code,name,status,sort_order) SELECT 'crm','crm','PREPARING',100 WHERE NOT EXISTS(SELECT 1 FROM ap_feature_group WHERE code='crm');

INSERT INTO ap_permission_action(code,group_code,name,description,binding_key,status,sort_order,created_at,updated_at) SELECT 'crm.detail.read','crm','CRM설정·현황','P3 구현 예정',NULL,'PREPARING',141,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP WHERE NOT EXISTS(SELECT 1 FROM ap_permission_action WHERE code='crm.detail.read');

INSERT INTO ap_feature_group(code,name,status,sort_order) SELECT 'crm','crm','PREPARING',100 WHERE NOT EXISTS(SELECT 1 FROM ap_feature_group WHERE code='crm');

INSERT INTO ap_permission_action(code,group_code,name,description,binding_key,status,sort_order,created_at,updated_at) SELECT 'crm.create.manual','crm','직접CRM생성','P3 구현 예정',NULL,'PREPARING',142,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP WHERE NOT EXISTS(SELECT 1 FROM ap_permission_action WHERE code='crm.create.manual');

INSERT INTO ap_feature_group(code,name,status,sort_order) SELECT 'crm','crm','PREPARING',100 WHERE NOT EXISTS(SELECT 1 FROM ap_feature_group WHERE code='crm');

INSERT INTO ap_permission_action(code,group_code,name,description,binding_key,status,sort_order,created_at,updated_at) SELECT 'crm.create.chat','crm','질문CRM생성','P3 구현 예정',NULL,'PREPARING',143,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP WHERE NOT EXISTS(SELECT 1 FROM ap_permission_action WHERE code='crm.create.chat');

INSERT INTO ap_feature_group(code,name,status,sort_order) SELECT 'crm','crm','PREPARING',100 WHERE NOT EXISTS(SELECT 1 FROM ap_feature_group WHERE code='crm');

INSERT INTO ap_permission_action(code,group_code,name,description,binding_key,status,sort_order,created_at,updated_at) SELECT 'crm.create.photo','crm','사진CRM생성','P3 구현 예정',NULL,'PREPARING',144,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP WHERE NOT EXISTS(SELECT 1 FROM ap_permission_action WHERE code='crm.create.photo');

INSERT INTO ap_feature_group(code,name,status,sort_order) SELECT 'crm','crm','PREPARING',100 WHERE NOT EXISTS(SELECT 1 FROM ap_feature_group WHERE code='crm');

INSERT INTO ap_permission_action(code,group_code,name,description,binding_key,status,sort_order,created_at,updated_at) SELECT 'crm.create.template','crm','양식CRM생성','P3 구현 예정',NULL,'PREPARING',145,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP WHERE NOT EXISTS(SELECT 1 FROM ap_permission_action WHERE code='crm.create.template');

INSERT INTO ap_feature_group(code,name,status,sort_order) SELECT 'crm','crm','PREPARING',100 WHERE NOT EXISTS(SELECT 1 FROM ap_feature_group WHERE code='crm');

INSERT INTO ap_permission_action(code,group_code,name,description,binding_key,status,sort_order,created_at,updated_at) SELECT 'crm.meta.update','crm','CRM이름수정','P3 구현 예정',NULL,'PREPARING',146,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP WHERE NOT EXISTS(SELECT 1 FROM ap_permission_action WHERE code='crm.meta.update');

INSERT INTO ap_feature_group(code,name,status,sort_order) SELECT 'crm','crm','PREPARING',100 WHERE NOT EXISTS(SELECT 1 FROM ap_feature_group WHERE code='crm');

INSERT INTO ap_permission_action(code,group_code,name,description,binding_key,status,sort_order,created_at,updated_at) SELECT 'crm.schema.update','crm','항목구조변경','P3 구현 예정',NULL,'PREPARING',147,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP WHERE NOT EXISTS(SELECT 1 FROM ap_permission_action WHERE code='crm.schema.update');

INSERT INTO ap_feature_group(code,name,status,sort_order) SELECT 'crm','crm','PREPARING',100 WHERE NOT EXISTS(SELECT 1 FROM ap_feature_group WHERE code='crm');

INSERT INTO ap_permission_action(code,group_code,name,description,binding_key,status,sort_order,created_at,updated_at) SELECT 'crm.schema.ai','crm','AI구조제안','P3 구현 예정',NULL,'PREPARING',148,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP WHERE NOT EXISTS(SELECT 1 FROM ap_permission_action WHERE code='crm.schema.ai');

INSERT INTO ap_feature_group(code,name,status,sort_order) SELECT 'crm','crm','PREPARING',100 WHERE NOT EXISTS(SELECT 1 FROM ap_feature_group WHERE code='crm');

INSERT INTO ap_permission_action(code,group_code,name,description,binding_key,status,sort_order,created_at,updated_at) SELECT 'crm.record.list.read','crm','고객목록','P3 구현 예정',NULL,'PREPARING',149,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP WHERE NOT EXISTS(SELECT 1 FROM ap_permission_action WHERE code='crm.record.list.read');

INSERT INTO ap_feature_group(code,name,status,sort_order) SELECT 'crm','crm','PREPARING',100 WHERE NOT EXISTS(SELECT 1 FROM ap_feature_group WHERE code='crm');

INSERT INTO ap_permission_action(code,group_code,name,description,binding_key,status,sort_order,created_at,updated_at) SELECT 'crm.record.detail.read','crm','고객상세·연락처','P3 구현 예정',NULL,'PREPARING',150,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP WHERE NOT EXISTS(SELECT 1 FROM ap_permission_action WHERE code='crm.record.detail.read');

INSERT INTO ap_feature_group(code,name,status,sort_order) SELECT 'crm','crm','PREPARING',100 WHERE NOT EXISTS(SELECT 1 FROM ap_feature_group WHERE code='crm');

INSERT INTO ap_permission_action(code,group_code,name,description,binding_key,status,sort_order,created_at,updated_at) SELECT 'crm.record.create','crm','고객등록','P3 구현 예정',NULL,'PREPARING',151,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP WHERE NOT EXISTS(SELECT 1 FROM ap_permission_action WHERE code='crm.record.create');

INSERT INTO ap_feature_group(code,name,status,sort_order) SELECT 'crm','crm','PREPARING',100 WHERE NOT EXISTS(SELECT 1 FROM ap_feature_group WHERE code='crm');

INSERT INTO ap_permission_action(code,group_code,name,description,binding_key,status,sort_order,created_at,updated_at) SELECT 'crm.record.update','crm','고객수정','P3 구현 예정',NULL,'PREPARING',152,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP WHERE NOT EXISTS(SELECT 1 FROM ap_permission_action WHERE code='crm.record.update');

INSERT INTO ap_feature_group(code,name,status,sort_order) SELECT 'crm','crm','PREPARING',100 WHERE NOT EXISTS(SELECT 1 FROM ap_feature_group WHERE code='crm');

INSERT INTO ap_permission_action(code,group_code,name,description,binding_key,status,sort_order,created_at,updated_at) SELECT 'crm.record.delete','crm','고객삭제','P3 구현 예정',NULL,'PREPARING',153,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP WHERE NOT EXISTS(SELECT 1 FROM ap_permission_action WHERE code='crm.record.delete');

INSERT INTO ap_feature_group(code,name,status,sort_order) SELECT 'crm','crm','PREPARING',100 WHERE NOT EXISTS(SELECT 1 FROM ap_feature_group WHERE code='crm');

INSERT INTO ap_permission_action(code,group_code,name,description,binding_key,status,sort_order,created_at,updated_at) SELECT 'crm.record.ai','crm','AI고객변경제안','P3 구현 예정',NULL,'PREPARING',154,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP WHERE NOT EXISTS(SELECT 1 FROM ap_permission_action WHERE code='crm.record.ai');

INSERT INTO ap_feature_group(code,name,status,sort_order) SELECT 'crm','crm','PREPARING',100 WHERE NOT EXISTS(SELECT 1 FROM ap_feature_group WHERE code='crm');

INSERT INTO ap_permission_action(code,group_code,name,description,binding_key,status,sort_order,created_at,updated_at) SELECT 'crm.record.photo','crm','사진고객후보','P3 구현 예정',NULL,'PREPARING',155,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP WHERE NOT EXISTS(SELECT 1 FROM ap_permission_action WHERE code='crm.record.photo');

INSERT INTO ap_feature_group(code,name,status,sort_order) SELECT 'crm','crm','PREPARING',100 WHERE NOT EXISTS(SELECT 1 FROM ap_feature_group WHERE code='crm');

INSERT INTO ap_permission_action(code,group_code,name,description,binding_key,status,sort_order,created_at,updated_at) SELECT 'crm.view.read','crm','저장보기조회','P3 구현 예정',NULL,'PREPARING',156,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP WHERE NOT EXISTS(SELECT 1 FROM ap_permission_action WHERE code='crm.view.read');

INSERT INTO ap_feature_group(code,name,status,sort_order) SELECT 'crm','crm','PREPARING',100 WHERE NOT EXISTS(SELECT 1 FROM ap_feature_group WHERE code='crm');

INSERT INTO ap_permission_action(code,group_code,name,description,binding_key,status,sort_order,created_at,updated_at) SELECT 'crm.view.manage','crm','보기생성/수정/삭제','P3 구현 예정',NULL,'PREPARING',157,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP WHERE NOT EXISTS(SELECT 1 FROM ap_permission_action WHERE code='crm.view.manage');

INSERT INTO ap_feature_group(code,name,status,sort_order) SELECT 'crm','crm','PREPARING',100 WHERE NOT EXISTS(SELECT 1 FROM ap_feature_group WHERE code='crm');

INSERT INTO ap_permission_action(code,group_code,name,description,binding_key,status,sort_order,created_at,updated_at) SELECT 'crm.import.append','crm','추가가져오기','P3 구현 예정',NULL,'PREPARING',158,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP WHERE NOT EXISTS(SELECT 1 FROM ap_permission_action WHERE code='crm.import.append');

INSERT INTO ap_feature_group(code,name,status,sort_order) SELECT 'crm','crm','PREPARING',100 WHERE NOT EXISTS(SELECT 1 FROM ap_feature_group WHERE code='crm');

INSERT INTO ap_permission_action(code,group_code,name,description,binding_key,status,sort_order,created_at,updated_at) SELECT 'crm.import.update','crm','명시갱신가져오기','P3 구현 예정',NULL,'PREPARING',159,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP WHERE NOT EXISTS(SELECT 1 FROM ap_permission_action WHERE code='crm.import.update');

INSERT INTO ap_feature_group(code,name,status,sort_order) SELECT 'crm','crm','PREPARING',100 WHERE NOT EXISTS(SELECT 1 FROM ap_feature_group WHERE code='crm');

INSERT INTO ap_permission_action(code,group_code,name,description,binding_key,status,sort_order,created_at,updated_at) SELECT 'crm.import.reset','crm','전체교체가져오기','P3 구현 예정',NULL,'PREPARING',160,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP WHERE NOT EXISTS(SELECT 1 FROM ap_permission_action WHERE code='crm.import.reset');

INSERT INTO ap_feature_group(code,name,status,sort_order) SELECT 'crm','crm','PREPARING',100 WHERE NOT EXISTS(SELECT 1 FROM ap_feature_group WHERE code='crm');

INSERT INTO ap_permission_action(code,group_code,name,description,binding_key,status,sort_order,created_at,updated_at) SELECT 'crm.export.data','crm','고객내보내기','P3 구현 예정',NULL,'PREPARING',161,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP WHERE NOT EXISTS(SELECT 1 FROM ap_permission_action WHERE code='crm.export.data');

INSERT INTO ap_feature_group(code,name,status,sort_order) SELECT 'crm','crm','PREPARING',100 WHERE NOT EXISTS(SELECT 1 FROM ap_feature_group WHERE code='crm');

INSERT INTO ap_permission_action(code,group_code,name,description,binding_key,status,sort_order,created_at,updated_at) SELECT 'crm.analysis.read','crm','분석조회','P3 구현 예정',NULL,'PREPARING',162,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP WHERE NOT EXISTS(SELECT 1 FROM ap_permission_action WHERE code='crm.analysis.read');

INSERT INTO ap_feature_group(code,name,status,sort_order) SELECT 'crm','crm','PREPARING',100 WHERE NOT EXISTS(SELECT 1 FROM ap_feature_group WHERE code='crm');

INSERT INTO ap_permission_action(code,group_code,name,description,binding_key,status,sort_order,created_at,updated_at) SELECT 'crm.analysis.manage','crm','분석관리','P3 구현 예정',NULL,'PREPARING',163,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP WHERE NOT EXISTS(SELECT 1 FROM ap_permission_action WHERE code='crm.analysis.manage');

INSERT INTO ap_feature_group(code,name,status,sort_order) SELECT 'crm','crm','PREPARING',100 WHERE NOT EXISTS(SELECT 1 FROM ap_feature_group WHERE code='crm');

INSERT INTO ap_permission_action(code,group_code,name,description,binding_key,status,sort_order,created_at,updated_at) SELECT 'crm.template.manage','crm','CRM내양식','P3 구현 예정',NULL,'PREPARING',164,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP WHERE NOT EXISTS(SELECT 1 FROM ap_permission_action WHERE code='crm.template.manage');

INSERT INTO ap_feature_group(code,name,status,sort_order) SELECT 'crm','crm','PREPARING',100 WHERE NOT EXISTS(SELECT 1 FROM ap_feature_group WHERE code='crm');

INSERT INTO ap_permission_action(code,group_code,name,description,binding_key,status,sort_order,created_at,updated_at) SELECT 'crm.partner.link','crm','공통거래처연결','P3 구현 예정',NULL,'PREPARING',165,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP WHERE NOT EXISTS(SELECT 1 FROM ap_permission_action WHERE code='crm.partner.link');

INSERT INTO ap_feature_group(code,name,status,sort_order) SELECT 'crm','crm','PREPARING',100 WHERE NOT EXISTS(SELECT 1 FROM ap_feature_group WHERE code='crm');

INSERT INTO ap_permission_action(code,group_code,name,description,binding_key,status,sort_order,created_at,updated_at) SELECT 'crm.attachment.read','crm','고객첨부조회','P3 구현 예정',NULL,'PREPARING',166,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP WHERE NOT EXISTS(SELECT 1 FROM ap_permission_action WHERE code='crm.attachment.read');

INSERT INTO ap_feature_group(code,name,status,sort_order) SELECT 'crm','crm','PREPARING',100 WHERE NOT EXISTS(SELECT 1 FROM ap_feature_group WHERE code='crm');

INSERT INTO ap_permission_action(code,group_code,name,description,binding_key,status,sort_order,created_at,updated_at) SELECT 'crm.attachment.manage','crm','고객첨부관리','P3 구현 예정',NULL,'PREPARING',167,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP WHERE NOT EXISTS(SELECT 1 FROM ap_permission_action WHERE code='crm.attachment.manage');

INSERT INTO ap_feature_group(code,name,status,sort_order) SELECT 'crm','crm','PREPARING',100 WHERE NOT EXISTS(SELECT 1 FROM ap_feature_group WHERE code='crm');

INSERT INTO ap_permission_action(code,group_code,name,description,binding_key,status,sort_order,created_at,updated_at) SELECT 'crm.project.archive','crm','CRM보관','P3 구현 예정',NULL,'PREPARING',168,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP WHERE NOT EXISTS(SELECT 1 FROM ap_permission_action WHERE code='crm.project.archive');

INSERT INTO ap_feature_group(code,name,status,sort_order) SELECT 'crm','crm','PREPARING',100 WHERE NOT EXISTS(SELECT 1 FROM ap_feature_group WHERE code='crm');

INSERT INTO ap_permission_action(code,group_code,name,description,binding_key,status,sort_order,created_at,updated_at) SELECT 'crm.project.delete','crm','CRM삭제','P3 구현 예정',NULL,'PREPARING',169,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP WHERE NOT EXISTS(SELECT 1 FROM ap_permission_action WHERE code='crm.project.delete');

INSERT INTO ap_feature_group(code,name,status,sort_order) SELECT 'crm','crm','PREPARING',100 WHERE NOT EXISTS(SELECT 1 FROM ap_feature_group WHERE code='crm');

INSERT INTO ap_permission_action(code,group_code,name,description,binding_key,status,sort_order,created_at,updated_at) SELECT 'crm.project.restore','crm','CRM복원','P3 구현 예정',NULL,'PREPARING',170,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP WHERE NOT EXISTS(SELECT 1 FROM ap_permission_action WHERE code='crm.project.restore');

INSERT INTO ap_feature_group(code,name,status,sort_order) SELECT 'crm','crm','PREPARING',100 WHERE NOT EXISTS(SELECT 1 FROM ap_feature_group WHERE code='crm');

INSERT INTO ap_permission_action(code,group_code,name,description,binding_key,status,sort_order,created_at,updated_at) SELECT 'crm.project.duplicate','crm','CRM구조복제','P3 구현 예정',NULL,'PREPARING',171,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP WHERE NOT EXISTS(SELECT 1 FROM ap_permission_action WHERE code='crm.project.duplicate');

INSERT INTO ap_feature_group(code,name,status,sort_order) SELECT 'calendar','calendar','PREPARING',100 WHERE NOT EXISTS(SELECT 1 FROM ap_feature_group WHERE code='calendar');

INSERT INTO ap_permission_action(code,group_code,name,description,binding_key,status,sort_order,created_at,updated_at) SELECT 'calendar.overview.read','calendar','일정현황','P3 구현 예정',NULL,'PREPARING',172,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP WHERE NOT EXISTS(SELECT 1 FROM ap_permission_action WHERE code='calendar.overview.read');

INSERT INTO ap_feature_group(code,name,status,sort_order) SELECT 'calendar','calendar','PREPARING',100 WHERE NOT EXISTS(SELECT 1 FROM ap_feature_group WHERE code='calendar');

INSERT INTO ap_permission_action(code,group_code,name,description,binding_key,status,sort_order,created_at,updated_at) SELECT 'calendar.list.read','calendar','달력/목록','P3 구현 예정',NULL,'PREPARING',173,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP WHERE NOT EXISTS(SELECT 1 FROM ap_permission_action WHERE code='calendar.list.read');

INSERT INTO ap_feature_group(code,name,status,sort_order) SELECT 'calendar','calendar','PREPARING',100 WHERE NOT EXISTS(SELECT 1 FROM ap_feature_group WHERE code='calendar');

INSERT INTO ap_permission_action(code,group_code,name,description,binding_key,status,sort_order,created_at,updated_at) SELECT 'calendar.detail.read','calendar','일정상세','P3 구현 예정',NULL,'PREPARING',174,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP WHERE NOT EXISTS(SELECT 1 FROM ap_permission_action WHERE code='calendar.detail.read');

INSERT INTO ap_feature_group(code,name,status,sort_order) SELECT 'calendar','calendar','PREPARING',100 WHERE NOT EXISTS(SELECT 1 FROM ap_feature_group WHERE code='calendar');

INSERT INTO ap_permission_action(code,group_code,name,description,binding_key,status,sort_order,created_at,updated_at) SELECT 'calendar.event.create','calendar','직접일정생성','P3 구현 예정',NULL,'PREPARING',175,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP WHERE NOT EXISTS(SELECT 1 FROM ap_permission_action WHERE code='calendar.event.create');

INSERT INTO ap_feature_group(code,name,status,sort_order) SELECT 'calendar','calendar','PREPARING',100 WHERE NOT EXISTS(SELECT 1 FROM ap_feature_group WHERE code='calendar');

INSERT INTO ap_permission_action(code,group_code,name,description,binding_key,status,sort_order,created_at,updated_at) SELECT 'calendar.event.update','calendar','일정수정/드래그','P3 구현 예정',NULL,'PREPARING',176,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP WHERE NOT EXISTS(SELECT 1 FROM ap_permission_action WHERE code='calendar.event.update');

INSERT INTO ap_feature_group(code,name,status,sort_order) SELECT 'calendar','calendar','PREPARING',100 WHERE NOT EXISTS(SELECT 1 FROM ap_feature_group WHERE code='calendar');

INSERT INTO ap_permission_action(code,group_code,name,description,binding_key,status,sort_order,created_at,updated_at) SELECT 'calendar.event.delete','calendar','일정삭제','P3 구현 예정',NULL,'PREPARING',177,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP WHERE NOT EXISTS(SELECT 1 FROM ap_permission_action WHERE code='calendar.event.delete');

INSERT INTO ap_feature_group(code,name,status,sort_order) SELECT 'calendar','calendar','PREPARING',100 WHERE NOT EXISTS(SELECT 1 FROM ap_feature_group WHERE code='calendar');

INSERT INTO ap_permission_action(code,group_code,name,description,binding_key,status,sort_order,created_at,updated_at) SELECT 'calendar.event.ai.create','calendar','AI일정생성후보','P3 구현 예정',NULL,'PREPARING',178,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP WHERE NOT EXISTS(SELECT 1 FROM ap_permission_action WHERE code='calendar.event.ai.create');

INSERT INTO ap_feature_group(code,name,status,sort_order) SELECT 'calendar','calendar','PREPARING',100 WHERE NOT EXISTS(SELECT 1 FROM ap_feature_group WHERE code='calendar');

INSERT INTO ap_permission_action(code,group_code,name,description,binding_key,status,sort_order,created_at,updated_at) SELECT 'calendar.event.ai.update','calendar','AI일정수정후보','P3 구현 예정',NULL,'PREPARING',179,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP WHERE NOT EXISTS(SELECT 1 FROM ap_permission_action WHERE code='calendar.event.ai.update');

INSERT INTO ap_feature_group(code,name,status,sort_order) SELECT 'calendar','calendar','PREPARING',100 WHERE NOT EXISTS(SELECT 1 FROM ap_feature_group WHERE code='calendar');

INSERT INTO ap_permission_action(code,group_code,name,description,binding_key,status,sort_order,created_at,updated_at) SELECT 'calendar.event.photo','calendar','사진일정후보','P3 구현 예정',NULL,'PREPARING',180,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP WHERE NOT EXISTS(SELECT 1 FROM ap_permission_action WHERE code='calendar.event.photo');

INSERT INTO ap_feature_group(code,name,status,sort_order) SELECT 'calendar','calendar','PREPARING',100 WHERE NOT EXISTS(SELECT 1 FROM ap_feature_group WHERE code='calendar');

INSERT INTO ap_permission_action(code,group_code,name,description,binding_key,status,sort_order,created_at,updated_at) SELECT 'calendar.recurrence.manage','calendar','반복·예외관리','P3 구현 예정',NULL,'PREPARING',181,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP WHERE NOT EXISTS(SELECT 1 FROM ap_permission_action WHERE code='calendar.recurrence.manage');

INSERT INTO ap_feature_group(code,name,status,sort_order) SELECT 'calendar','calendar','PREPARING',100 WHERE NOT EXISTS(SELECT 1 FROM ap_feature_group WHERE code='calendar');

INSERT INTO ap_permission_action(code,group_code,name,description,binding_key,status,sort_order,created_at,updated_at) SELECT 'calendar.reminder.manage','calendar','알림계획관리','P3 구현 예정',NULL,'PREPARING',182,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP WHERE NOT EXISTS(SELECT 1 FROM ap_permission_action WHERE code='calendar.reminder.manage');

INSERT INTO ap_feature_group(code,name,status,sort_order) SELECT 'calendar','calendar','PREPARING',100 WHERE NOT EXISTS(SELECT 1 FROM ap_feature_group WHERE code='calendar');

INSERT INTO ap_permission_action(code,group_code,name,description,binding_key,status,sort_order,created_at,updated_at) SELECT 'calendar.partner.read','calendar','거래처조회','P3 구현 예정',NULL,'PREPARING',183,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP WHERE NOT EXISTS(SELECT 1 FROM ap_permission_action WHERE code='calendar.partner.read');

INSERT INTO ap_feature_group(code,name,status,sort_order) SELECT 'calendar','calendar','PREPARING',100 WHERE NOT EXISTS(SELECT 1 FROM ap_feature_group WHERE code='calendar');

INSERT INTO ap_permission_action(code,group_code,name,description,binding_key,status,sort_order,created_at,updated_at) SELECT 'calendar.partner.create','calendar','거래처등록','P3 구현 예정',NULL,'PREPARING',184,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP WHERE NOT EXISTS(SELECT 1 FROM ap_permission_action WHERE code='calendar.partner.create');

INSERT INTO ap_feature_group(code,name,status,sort_order) SELECT 'calendar','calendar','PREPARING',100 WHERE NOT EXISTS(SELECT 1 FROM ap_feature_group WHERE code='calendar');

INSERT INTO ap_permission_action(code,group_code,name,description,binding_key,status,sort_order,created_at,updated_at) SELECT 'calendar.partner.update','calendar','거래처수정','P3 구현 예정',NULL,'PREPARING',185,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP WHERE NOT EXISTS(SELECT 1 FROM ap_permission_action WHERE code='calendar.partner.update');

INSERT INTO ap_feature_group(code,name,status,sort_order) SELECT 'calendar','calendar','PREPARING',100 WHERE NOT EXISTS(SELECT 1 FROM ap_feature_group WHERE code='calendar');

INSERT INTO ap_permission_action(code,group_code,name,description,binding_key,status,sort_order,created_at,updated_at) SELECT 'calendar.attachment.read','calendar','일정첨부읽기','P3 구현 예정',NULL,'PREPARING',186,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP WHERE NOT EXISTS(SELECT 1 FROM ap_permission_action WHERE code='calendar.attachment.read');

INSERT INTO ap_feature_group(code,name,status,sort_order) SELECT 'calendar','calendar','PREPARING',100 WHERE NOT EXISTS(SELECT 1 FROM ap_feature_group WHERE code='calendar');

INSERT INTO ap_permission_action(code,group_code,name,description,binding_key,status,sort_order,created_at,updated_at) SELECT 'calendar.attachment.manage','calendar','일정첨부관리','P3 구현 예정',NULL,'PREPARING',187,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP WHERE NOT EXISTS(SELECT 1 FROM ap_permission_action WHERE code='calendar.attachment.manage');

INSERT INTO ap_feature_group(code,name,status,sort_order) SELECT 'calendar','calendar','PREPARING',100 WHERE NOT EXISTS(SELECT 1 FROM ap_feature_group WHERE code='calendar');

INSERT INTO ap_permission_action(code,group_code,name,description,binding_key,status,sort_order,created_at,updated_at) SELECT 'calendar.import.manage','calendar','일정가져오기','P3 구현 예정',NULL,'PREPARING',188,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP WHERE NOT EXISTS(SELECT 1 FROM ap_permission_action WHERE code='calendar.import.manage');

INSERT INTO ap_feature_group(code,name,status,sort_order) SELECT 'calendar','calendar','PREPARING',100 WHERE NOT EXISTS(SELECT 1 FROM ap_feature_group WHERE code='calendar');

INSERT INTO ap_permission_action(code,group_code,name,description,binding_key,status,sort_order,created_at,updated_at) SELECT 'calendar.export.data','calendar','일정내보내기','P3 구현 예정',NULL,'PREPARING',189,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP WHERE NOT EXISTS(SELECT 1 FROM ap_permission_action WHERE code='calendar.export.data');

INSERT INTO ap_feature_group(code,name,status,sort_order) SELECT 'calendar','calendar','PREPARING',100 WHERE NOT EXISTS(SELECT 1 FROM ap_feature_group WHERE code='calendar');

INSERT INTO ap_permission_action(code,group_code,name,description,binding_key,status,sort_order,created_at,updated_at) SELECT 'calendar.view.manage','calendar','일정보기관리','P3 구현 예정',NULL,'PREPARING',190,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP WHERE NOT EXISTS(SELECT 1 FROM ap_permission_action WHERE code='calendar.view.manage');

INSERT INTO ap_feature_group(code,name,status,sort_order) SELECT 'calendar','calendar','PREPARING',100 WHERE NOT EXISTS(SELECT 1 FROM ap_feature_group WHERE code='calendar');

INSERT INTO ap_permission_action(code,group_code,name,description,binding_key,status,sort_order,created_at,updated_at) SELECT 'calendar.template.manage','calendar','일정양식관리','P3 구현 예정',NULL,'PREPARING',191,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP WHERE NOT EXISTS(SELECT 1 FROM ap_permission_action WHERE code='calendar.template.manage');

INSERT INTO ap_feature_group(code,name,status,sort_order) SELECT 'app','app','PREPARING',100 WHERE NOT EXISTS(SELECT 1 FROM ap_feature_group WHERE code='app');

INSERT INTO ap_permission_action(code,group_code,name,description,binding_key,status,sort_order,created_at,updated_at) SELECT 'app.overview.read','app','앱오버뷰','P6 구현 예정',NULL,'PREPARING',192,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP WHERE NOT EXISTS(SELECT 1 FROM ap_permission_action WHERE code='app.overview.read');

INSERT INTO ap_feature_group(code,name,status,sort_order) SELECT 'app','app','PREPARING',100 WHERE NOT EXISTS(SELECT 1 FROM ap_feature_group WHERE code='app');

INSERT INTO ap_permission_action(code,group_code,name,description,binding_key,status,sort_order,created_at,updated_at) SELECT 'app.list.read','app','앱목록','P6 구현 예정',NULL,'PREPARING',193,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP WHERE NOT EXISTS(SELECT 1 FROM ap_permission_action WHERE code='app.list.read');

INSERT INTO ap_feature_group(code,name,status,sort_order) SELECT 'app','app','PREPARING',100 WHERE NOT EXISTS(SELECT 1 FROM ap_feature_group WHERE code='app');

INSERT INTO ap_permission_action(code,group_code,name,description,binding_key,status,sort_order,created_at,updated_at) SELECT 'app.detail.read','app','부품·상세','P6 구현 예정',NULL,'PREPARING',194,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP WHERE NOT EXISTS(SELECT 1 FROM ap_permission_action WHERE code='app.detail.read');

INSERT INTO ap_feature_group(code,name,status,sort_order) SELECT 'app','app','PREPARING',100 WHERE NOT EXISTS(SELECT 1 FROM ap_feature_group WHERE code='app');

INSERT INTO ap_permission_action(code,group_code,name,description,binding_key,status,sort_order,created_at,updated_at) SELECT 'app.create.manual','app','부품직접생성','P6 구현 예정',NULL,'PREPARING',195,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP WHERE NOT EXISTS(SELECT 1 FROM ap_permission_action WHERE code='app.create.manual');

INSERT INTO ap_feature_group(code,name,status,sort_order) SELECT 'app','app','PREPARING',100 WHERE NOT EXISTS(SELECT 1 FROM ap_feature_group WHERE code='app');

INSERT INTO ap_permission_action(code,group_code,name,description,binding_key,status,sort_order,created_at,updated_at) SELECT 'app.create.chat','app','질문생성','P6 구현 예정',NULL,'PREPARING',196,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP WHERE NOT EXISTS(SELECT 1 FROM ap_permission_action WHERE code='app.create.chat');

INSERT INTO ap_feature_group(code,name,status,sort_order) SELECT 'app','app','PREPARING',100 WHERE NOT EXISTS(SELECT 1 FROM ap_feature_group WHERE code='app');

INSERT INTO ap_permission_action(code,group_code,name,description,binding_key,status,sort_order,created_at,updated_at) SELECT 'app.create.photo','app','사진생성','P6 구현 예정',NULL,'PREPARING',197,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP WHERE NOT EXISTS(SELECT 1 FROM ap_permission_action WHERE code='app.create.photo');

INSERT INTO ap_feature_group(code,name,status,sort_order) SELECT 'app','app','PREPARING',100 WHERE NOT EXISTS(SELECT 1 FROM ap_feature_group WHERE code='app');

INSERT INTO ap_permission_action(code,group_code,name,description,binding_key,status,sort_order,created_at,updated_at) SELECT 'app.create.template','app','양식생성','P6 구현 예정',NULL,'PREPARING',198,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP WHERE NOT EXISTS(SELECT 1 FROM ap_permission_action WHERE code='app.create.template');

INSERT INTO ap_feature_group(code,name,status,sort_order) SELECT 'app','app','PREPARING',100 WHERE NOT EXISTS(SELECT 1 FROM ap_feature_group WHERE code='app');

INSERT INTO ap_permission_action(code,group_code,name,description,binding_key,status,sort_order,created_at,updated_at) SELECT 'app.meta.update','app','앱이름변경','P6 구현 예정',NULL,'PREPARING',199,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP WHERE NOT EXISTS(SELECT 1 FROM ap_permission_action WHERE code='app.meta.update');

INSERT INTO ap_feature_group(code,name,status,sort_order) SELECT 'app','app','PREPARING',100 WHERE NOT EXISTS(SELECT 1 FROM ap_feature_group WHERE code='app');

INSERT INTO ap_permission_action(code,group_code,name,description,binding_key,status,sort_order,created_at,updated_at) SELECT 'app.component.create','app','부품추가','P6 구현 예정',NULL,'PREPARING',200,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP WHERE NOT EXISTS(SELECT 1 FROM ap_permission_action WHERE code='app.component.create');

INSERT INTO ap_feature_group(code,name,status,sort_order) SELECT 'app','app','PREPARING',100 WHERE NOT EXISTS(SELECT 1 FROM ap_feature_group WHERE code='app');

INSERT INTO ap_permission_action(code,group_code,name,description,binding_key,status,sort_order,created_at,updated_at) SELECT 'app.component.update','app','부품수정/순서','P6 구현 예정',NULL,'PREPARING',201,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP WHERE NOT EXISTS(SELECT 1 FROM ap_permission_action WHERE code='app.component.update');

INSERT INTO ap_feature_group(code,name,status,sort_order) SELECT 'app','app','PREPARING',100 WHERE NOT EXISTS(SELECT 1 FROM ap_feature_group WHERE code='app');

INSERT INTO ap_permission_action(code,group_code,name,description,binding_key,status,sort_order,created_at,updated_at) SELECT 'app.component.delete','app','부품삭제','P6 구현 예정',NULL,'PREPARING',202,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP WHERE NOT EXISTS(SELECT 1 FROM ap_permission_action WHERE code='app.component.delete');

INSERT INTO ap_feature_group(code,name,status,sort_order) SELECT 'app','app','PREPARING',100 WHERE NOT EXISTS(SELECT 1 FROM ap_feature_group WHERE code='app');

INSERT INTO ap_permission_action(code,group_code,name,description,binding_key,status,sort_order,created_at,updated_at) SELECT 'app.edit.ai','app','AI부품제안','P6 구현 예정',NULL,'PREPARING',203,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP WHERE NOT EXISTS(SELECT 1 FROM ap_permission_action WHERE code='app.edit.ai');

INSERT INTO ap_feature_group(code,name,status,sort_order) SELECT 'app','app','PREPARING',100 WHERE NOT EXISTS(SELECT 1 FROM ap_feature_group WHERE code='app');

INSERT INTO ap_permission_action(code,group_code,name,description,binding_key,status,sort_order,created_at,updated_at) SELECT 'app.trial.execute','app','샌드박스시험','P6 구현 예정',NULL,'PREPARING',204,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP WHERE NOT EXISTS(SELECT 1 FROM ap_permission_action WHERE code='app.trial.execute');

INSERT INTO ap_feature_group(code,name,status,sort_order) SELECT 'app','app','PREPARING',100 WHERE NOT EXISTS(SELECT 1 FROM ap_feature_group WHERE code='app');

INSERT INTO ap_permission_action(code,group_code,name,description,binding_key,status,sort_order,created_at,updated_at) SELECT 'app.version.create','app','불변V1/V2저장','P6 구현 예정',NULL,'PREPARING',205,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP WHERE NOT EXISTS(SELECT 1 FROM ap_permission_action WHERE code='app.version.create');

INSERT INTO ap_feature_group(code,name,status,sort_order) SELECT 'app','app','PREPARING',100 WHERE NOT EXISTS(SELECT 1 FROM ap_feature_group WHERE code='app');

INSERT INTO ap_permission_action(code,group_code,name,description,binding_key,status,sort_order,created_at,updated_at) SELECT 'app.version.read','app','버전목록/차이','P6 구현 예정',NULL,'PREPARING',206,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP WHERE NOT EXISTS(SELECT 1 FROM ap_permission_action WHERE code='app.version.read');

INSERT INTO ap_feature_group(code,name,status,sort_order) SELECT 'app','app','PREPARING',100 WHERE NOT EXISTS(SELECT 1 FROM ap_feature_group WHERE code='app');

INSERT INTO ap_permission_action(code,group_code,name,description,binding_key,status,sort_order,created_at,updated_at) SELECT 'app.version.restore','app','이전설정새초안','P6 구현 예정',NULL,'PREPARING',207,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP WHERE NOT EXISTS(SELECT 1 FROM ap_permission_action WHERE code='app.version.restore');

INSERT INTO ap_feature_group(code,name,status,sort_order) SELECT 'app','app','PREPARING',100 WHERE NOT EXISTS(SELECT 1 FROM ap_feature_group WHERE code='app');

INSERT INTO ap_permission_action(code,group_code,name,description,binding_key,status,sort_order,created_at,updated_at) SELECT 'app.build.execute','app','격리빌드/재시도','P6 구현 예정',NULL,'PREPARING',208,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP WHERE NOT EXISTS(SELECT 1 FROM ap_permission_action WHERE code='app.build.execute');

INSERT INTO ap_feature_group(code,name,status,sort_order) SELECT 'app','app','PREPARING',100 WHERE NOT EXISTS(SELECT 1 FROM ap_feature_group WHERE code='app');

INSERT INTO ap_permission_action(code,group_code,name,description,binding_key,status,sort_order,created_at,updated_at) SELECT 'app.package.download','app','버전패키지다운로드','P6 구현 예정',NULL,'PREPARING',209,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP WHERE NOT EXISTS(SELECT 1 FROM ap_permission_action WHERE code='app.package.download');

INSERT INTO ap_feature_group(code,name,status,sort_order) SELECT 'app','app','PREPARING',100 WHERE NOT EXISTS(SELECT 1 FROM ap_feature_group WHERE code='app');

INSERT INTO ap_permission_action(code,group_code,name,description,binding_key,status,sort_order,created_at,updated_at) SELECT 'app.deployment.activate','app','운영활성화','P6 구현 예정',NULL,'PREPARING',210,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP WHERE NOT EXISTS(SELECT 1 FROM ap_permission_action WHERE code='app.deployment.activate');

INSERT INTO ap_feature_group(code,name,status,sort_order) SELECT 'app','app','PREPARING',100 WHERE NOT EXISTS(SELECT 1 FROM ap_feature_group WHERE code='app');

INSERT INTO ap_permission_action(code,group_code,name,description,binding_key,status,sort_order,created_at,updated_at) SELECT 'app.deployment.stop','app','활성중지','P6 구현 예정',NULL,'PREPARING',211,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP WHERE NOT EXISTS(SELECT 1 FROM ap_permission_action WHERE code='app.deployment.stop');

INSERT INTO ap_feature_group(code,name,status,sort_order) SELECT 'app','app','PREPARING',100 WHERE NOT EXISTS(SELECT 1 FROM ap_feature_group WHERE code='app');

INSERT INTO ap_permission_action(code,group_code,name,description,binding_key,status,sort_order,created_at,updated_at) SELECT 'app.data.read','app','실행데이터읽기','P6 구현 예정',NULL,'PREPARING',212,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP WHERE NOT EXISTS(SELECT 1 FROM ap_permission_action WHERE code='app.data.read');

INSERT INTO ap_feature_group(code,name,status,sort_order) SELECT 'app','app','PREPARING',100 WHERE NOT EXISTS(SELECT 1 FROM ap_feature_group WHERE code='app');

INSERT INTO ap_permission_action(code,group_code,name,description,binding_key,status,sort_order,created_at,updated_at) SELECT 'app.data.create','app','실행데이터등록','P6 구현 예정',NULL,'PREPARING',213,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP WHERE NOT EXISTS(SELECT 1 FROM ap_permission_action WHERE code='app.data.create');

INSERT INTO ap_feature_group(code,name,status,sort_order) SELECT 'app','app','PREPARING',100 WHERE NOT EXISTS(SELECT 1 FROM ap_feature_group WHERE code='app');

INSERT INTO ap_permission_action(code,group_code,name,description,binding_key,status,sort_order,created_at,updated_at) SELECT 'app.data.update','app','실행데이터수정','P6 구현 예정',NULL,'PREPARING',214,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP WHERE NOT EXISTS(SELECT 1 FROM ap_permission_action WHERE code='app.data.update');

INSERT INTO ap_feature_group(code,name,status,sort_order) SELECT 'app','app','PREPARING',100 WHERE NOT EXISTS(SELECT 1 FROM ap_feature_group WHERE code='app');

INSERT INTO ap_permission_action(code,group_code,name,description,binding_key,status,sort_order,created_at,updated_at) SELECT 'app.data.delete','app','실행데이터삭제','P6 구현 예정',NULL,'PREPARING',215,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP WHERE NOT EXISTS(SELECT 1 FROM ap_permission_action WHERE code='app.data.delete');

INSERT INTO ap_feature_group(code,name,status,sort_order) SELECT 'app','app','PREPARING',100 WHERE NOT EXISTS(SELECT 1 FROM ap_feature_group WHERE code='app');

INSERT INTO ap_permission_action(code,group_code,name,description,binding_key,status,sort_order,created_at,updated_at) SELECT 'app.data.export','app','실행데이터출력','P6 구현 예정',NULL,'PREPARING',216,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP WHERE NOT EXISTS(SELECT 1 FROM ap_permission_action WHERE code='app.data.export');

INSERT INTO ap_feature_group(code,name,status,sort_order) SELECT 'app','app','PREPARING',100 WHERE NOT EXISTS(SELECT 1 FROM ap_feature_group WHERE code='app');

INSERT INTO ap_permission_action(code,group_code,name,description,binding_key,status,sort_order,created_at,updated_at) SELECT 'app.data.restore','app','업무데이터복구','P6 구현 예정',NULL,'PREPARING',217,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP WHERE NOT EXISTS(SELECT 1 FROM ap_permission_action WHERE code='app.data.restore');

INSERT INTO ap_feature_group(code,name,status,sort_order) SELECT 'app','app','PREPARING',100 WHERE NOT EXISTS(SELECT 1 FROM ap_feature_group WHERE code='app');

INSERT INTO ap_permission_action(code,group_code,name,description,binding_key,status,sort_order,created_at,updated_at) SELECT 'app.template.manage','app','앱양식관리','P6 구현 예정',NULL,'PREPARING',218,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP WHERE NOT EXISTS(SELECT 1 FROM ap_permission_action WHERE code='app.template.manage');

INSERT INTO ap_feature_group(code,name,status,sort_order) SELECT 'app','app','PREPARING',100 WHERE NOT EXISTS(SELECT 1 FROM ap_feature_group WHERE code='app');

INSERT INTO ap_permission_action(code,group_code,name,description,binding_key,status,sort_order,created_at,updated_at) SELECT 'app.project.archive','app','앱보관','P6 구현 예정',NULL,'PREPARING',219,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP WHERE NOT EXISTS(SELECT 1 FROM ap_permission_action WHERE code='app.project.archive');

INSERT INTO ap_feature_group(code,name,status,sort_order) SELECT 'app','app','PREPARING',100 WHERE NOT EXISTS(SELECT 1 FROM ap_feature_group WHERE code='app');

INSERT INTO ap_permission_action(code,group_code,name,description,binding_key,status,sort_order,created_at,updated_at) SELECT 'app.project.delete','app','앱삭제','P6 구현 예정',NULL,'PREPARING',220,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP WHERE NOT EXISTS(SELECT 1 FROM ap_permission_action WHERE code='app.project.delete');

INSERT INTO ap_feature_group(code,name,status,sort_order) SELECT 'app','app','PREPARING',100 WHERE NOT EXISTS(SELECT 1 FROM ap_feature_group WHERE code='app');

INSERT INTO ap_permission_action(code,group_code,name,description,binding_key,status,sort_order,created_at,updated_at) SELECT 'app.project.restore','app','앱복원','P6 구현 예정',NULL,'PREPARING',221,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP WHERE NOT EXISTS(SELECT 1 FROM ap_permission_action WHERE code='app.project.restore');

INSERT INTO ap_feature_group(code,name,status,sort_order) SELECT 'app','app','PREPARING',100 WHERE NOT EXISTS(SELECT 1 FROM ap_feature_group WHERE code='app');

INSERT INTO ap_permission_action(code,group_code,name,description,binding_key,status,sort_order,created_at,updated_at) SELECT 'app.project.duplicate','app','앱구조복제','P6 구현 예정',NULL,'PREPARING',222,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP WHERE NOT EXISTS(SELECT 1 FROM ap_permission_action WHERE code='app.project.duplicate');

INSERT INTO ap_feature_group(code,name,status,sort_order) SELECT 'connections','connections','PREPARING',100 WHERE NOT EXISTS(SELECT 1 FROM ap_feature_group WHERE code='connections');

INSERT INTO ap_permission_action(code,group_code,name,description,binding_key,status,sort_order,created_at,updated_at) SELECT 'connection.gmail.read','connections','gmail 상태조회','P5 구현 예정',NULL,'PREPARING',223,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP WHERE NOT EXISTS(SELECT 1 FROM ap_permission_action WHERE code='connection.gmail.read');

INSERT INTO ap_feature_group(code,name,status,sort_order) SELECT 'connections','connections','PREPARING',100 WHERE NOT EXISTS(SELECT 1 FROM ap_feature_group WHERE code='connections');

INSERT INTO ap_permission_action(code,group_code,name,description,binding_key,status,sort_order,created_at,updated_at) SELECT 'connection.gmail.create','connections','gmail 연결','P5 구현 예정',NULL,'PREPARING',224,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP WHERE NOT EXISTS(SELECT 1 FROM ap_permission_action WHERE code='connection.gmail.create');

INSERT INTO ap_feature_group(code,name,status,sort_order) SELECT 'connections','connections','PREPARING',100 WHERE NOT EXISTS(SELECT 1 FROM ap_feature_group WHERE code='connections');

INSERT INTO ap_permission_action(code,group_code,name,description,binding_key,status,sort_order,created_at,updated_at) SELECT 'connection.gmail.update','connections','gmail 연결교체','P5 구현 예정',NULL,'PREPARING',225,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP WHERE NOT EXISTS(SELECT 1 FROM ap_permission_action WHERE code='connection.gmail.update');

INSERT INTO ap_feature_group(code,name,status,sort_order) SELECT 'connections','connections','PREPARING',100 WHERE NOT EXISTS(SELECT 1 FROM ap_feature_group WHERE code='connections');

INSERT INTO ap_permission_action(code,group_code,name,description,binding_key,status,sort_order,created_at,updated_at) SELECT 'connection.gmail.delete','connections','gmail 해제','P5 구현 예정',NULL,'PREPARING',226,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP WHERE NOT EXISTS(SELECT 1 FROM ap_permission_action WHERE code='connection.gmail.delete');

INSERT INTO ap_feature_group(code,name,status,sort_order) SELECT 'connections','connections','PREPARING',100 WHERE NOT EXISTS(SELECT 1 FROM ap_feature_group WHERE code='connections');

INSERT INTO ap_permission_action(code,group_code,name,description,binding_key,status,sort_order,created_at,updated_at) SELECT 'connection.gmail.test','connections','gmail 연결시험','P5 구현 예정',NULL,'PREPARING',227,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP WHERE NOT EXISTS(SELECT 1 FROM ap_permission_action WHERE code='connection.gmail.test');

INSERT INTO ap_feature_group(code,name,status,sort_order) SELECT 'message','message','PREPARING',100 WHERE NOT EXISTS(SELECT 1 FROM ap_feature_group WHERE code='message');

INSERT INTO ap_permission_action(code,group_code,name,description,binding_key,status,sort_order,created_at,updated_at) SELECT 'message.gmail.send','message','gmail 단발발송','P5 구현 예정',NULL,'PREPARING',228,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP WHERE NOT EXISTS(SELECT 1 FROM ap_permission_action WHERE code='message.gmail.send');

INSERT INTO ap_feature_group(code,name,status,sort_order) SELECT 'message','message','PREPARING',100 WHERE NOT EXISTS(SELECT 1 FROM ap_feature_group WHERE code='message');

INSERT INTO ap_permission_action(code,group_code,name,description,binding_key,status,sort_order,created_at,updated_at) SELECT 'message.gmail.automation.create','message','gmail 예약규칙생성','P5 구현 예정',NULL,'PREPARING',229,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP WHERE NOT EXISTS(SELECT 1 FROM ap_permission_action WHERE code='message.gmail.automation.create');

INSERT INTO ap_feature_group(code,name,status,sort_order) SELECT 'message','message','PREPARING',100 WHERE NOT EXISTS(SELECT 1 FROM ap_feature_group WHERE code='message');

INSERT INTO ap_permission_action(code,group_code,name,description,binding_key,status,sort_order,created_at,updated_at) SELECT 'message.gmail.automation.update','message','gmail 예약규칙수정','P5 구현 예정',NULL,'PREPARING',230,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP WHERE NOT EXISTS(SELECT 1 FROM ap_permission_action WHERE code='message.gmail.automation.update');

INSERT INTO ap_feature_group(code,name,status,sort_order) SELECT 'message','message','PREPARING',100 WHERE NOT EXISTS(SELECT 1 FROM ap_feature_group WHERE code='message');

INSERT INTO ap_permission_action(code,group_code,name,description,binding_key,status,sort_order,created_at,updated_at) SELECT 'message.gmail.automation.delete','message','gmail 예약규칙삭제','P5 구현 예정',NULL,'PREPARING',231,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP WHERE NOT EXISTS(SELECT 1 FROM ap_permission_action WHERE code='message.gmail.automation.delete');

INSERT INTO ap_feature_group(code,name,status,sort_order) SELECT 'message','message','PREPARING',100 WHERE NOT EXISTS(SELECT 1 FROM ap_feature_group WHERE code='message');

INSERT INTO ap_permission_action(code,group_code,name,description,binding_key,status,sort_order,created_at,updated_at) SELECT 'message.gmail.automation.activate','message','gmail 예약활성','P5 구현 예정',NULL,'PREPARING',232,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP WHERE NOT EXISTS(SELECT 1 FROM ap_permission_action WHERE code='message.gmail.automation.activate');

INSERT INTO ap_feature_group(code,name,status,sort_order) SELECT 'message','message','PREPARING',100 WHERE NOT EXISTS(SELECT 1 FROM ap_feature_group WHERE code='message');

INSERT INTO ap_permission_action(code,group_code,name,description,binding_key,status,sort_order,created_at,updated_at) SELECT 'message.gmail.automation.stop','message','gmail 예약중지','P5 구현 예정',NULL,'PREPARING',233,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP WHERE NOT EXISTS(SELECT 1 FROM ap_permission_action WHERE code='message.gmail.automation.stop');

INSERT INTO ap_feature_group(code,name,status,sort_order) SELECT 'connections','connections','PREPARING',100 WHERE NOT EXISTS(SELECT 1 FROM ap_feature_group WHERE code='connections');

INSERT INTO ap_permission_action(code,group_code,name,description,binding_key,status,sort_order,created_at,updated_at) SELECT 'connection.sms.read','connections','sms 상태조회','P5 구현 예정',NULL,'PREPARING',234,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP WHERE NOT EXISTS(SELECT 1 FROM ap_permission_action WHERE code='connection.sms.read');

INSERT INTO ap_feature_group(code,name,status,sort_order) SELECT 'connections','connections','PREPARING',100 WHERE NOT EXISTS(SELECT 1 FROM ap_feature_group WHERE code='connections');

INSERT INTO ap_permission_action(code,group_code,name,description,binding_key,status,sort_order,created_at,updated_at) SELECT 'connection.sms.create','connections','sms 연결','P5 구현 예정',NULL,'PREPARING',235,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP WHERE NOT EXISTS(SELECT 1 FROM ap_permission_action WHERE code='connection.sms.create');

INSERT INTO ap_feature_group(code,name,status,sort_order) SELECT 'connections','connections','PREPARING',100 WHERE NOT EXISTS(SELECT 1 FROM ap_feature_group WHERE code='connections');

INSERT INTO ap_permission_action(code,group_code,name,description,binding_key,status,sort_order,created_at,updated_at) SELECT 'connection.sms.update','connections','sms 연결교체','P5 구현 예정',NULL,'PREPARING',236,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP WHERE NOT EXISTS(SELECT 1 FROM ap_permission_action WHERE code='connection.sms.update');

INSERT INTO ap_feature_group(code,name,status,sort_order) SELECT 'connections','connections','PREPARING',100 WHERE NOT EXISTS(SELECT 1 FROM ap_feature_group WHERE code='connections');

INSERT INTO ap_permission_action(code,group_code,name,description,binding_key,status,sort_order,created_at,updated_at) SELECT 'connection.sms.delete','connections','sms 해제','P5 구현 예정',NULL,'PREPARING',237,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP WHERE NOT EXISTS(SELECT 1 FROM ap_permission_action WHERE code='connection.sms.delete');

INSERT INTO ap_feature_group(code,name,status,sort_order) SELECT 'connections','connections','PREPARING',100 WHERE NOT EXISTS(SELECT 1 FROM ap_feature_group WHERE code='connections');

INSERT INTO ap_permission_action(code,group_code,name,description,binding_key,status,sort_order,created_at,updated_at) SELECT 'connection.sms.test','connections','sms 연결시험','P5 구현 예정',NULL,'PREPARING',238,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP WHERE NOT EXISTS(SELECT 1 FROM ap_permission_action WHERE code='connection.sms.test');

INSERT INTO ap_feature_group(code,name,status,sort_order) SELECT 'message','message','PREPARING',100 WHERE NOT EXISTS(SELECT 1 FROM ap_feature_group WHERE code='message');

INSERT INTO ap_permission_action(code,group_code,name,description,binding_key,status,sort_order,created_at,updated_at) SELECT 'message.sms.send','message','sms 단발발송','P5 구현 예정',NULL,'PREPARING',239,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP WHERE NOT EXISTS(SELECT 1 FROM ap_permission_action WHERE code='message.sms.send');

INSERT INTO ap_feature_group(code,name,status,sort_order) SELECT 'message','message','PREPARING',100 WHERE NOT EXISTS(SELECT 1 FROM ap_feature_group WHERE code='message');

INSERT INTO ap_permission_action(code,group_code,name,description,binding_key,status,sort_order,created_at,updated_at) SELECT 'message.sms.automation.create','message','sms 예약규칙생성','P5 구현 예정',NULL,'PREPARING',240,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP WHERE NOT EXISTS(SELECT 1 FROM ap_permission_action WHERE code='message.sms.automation.create');

INSERT INTO ap_feature_group(code,name,status,sort_order) SELECT 'message','message','PREPARING',100 WHERE NOT EXISTS(SELECT 1 FROM ap_feature_group WHERE code='message');

INSERT INTO ap_permission_action(code,group_code,name,description,binding_key,status,sort_order,created_at,updated_at) SELECT 'message.sms.automation.update','message','sms 예약규칙수정','P5 구현 예정',NULL,'PREPARING',241,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP WHERE NOT EXISTS(SELECT 1 FROM ap_permission_action WHERE code='message.sms.automation.update');

INSERT INTO ap_feature_group(code,name,status,sort_order) SELECT 'message','message','PREPARING',100 WHERE NOT EXISTS(SELECT 1 FROM ap_feature_group WHERE code='message');

INSERT INTO ap_permission_action(code,group_code,name,description,binding_key,status,sort_order,created_at,updated_at) SELECT 'message.sms.automation.delete','message','sms 예약규칙삭제','P5 구현 예정',NULL,'PREPARING',242,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP WHERE NOT EXISTS(SELECT 1 FROM ap_permission_action WHERE code='message.sms.automation.delete');

INSERT INTO ap_feature_group(code,name,status,sort_order) SELECT 'message','message','PREPARING',100 WHERE NOT EXISTS(SELECT 1 FROM ap_feature_group WHERE code='message');

INSERT INTO ap_permission_action(code,group_code,name,description,binding_key,status,sort_order,created_at,updated_at) SELECT 'message.sms.automation.activate','message','sms 예약활성','P5 구현 예정',NULL,'PREPARING',243,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP WHERE NOT EXISTS(SELECT 1 FROM ap_permission_action WHERE code='message.sms.automation.activate');

INSERT INTO ap_feature_group(code,name,status,sort_order) SELECT 'message','message','PREPARING',100 WHERE NOT EXISTS(SELECT 1 FROM ap_feature_group WHERE code='message');

INSERT INTO ap_permission_action(code,group_code,name,description,binding_key,status,sort_order,created_at,updated_at) SELECT 'message.sms.automation.stop','message','sms 예약중지','P5 구현 예정',NULL,'PREPARING',244,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP WHERE NOT EXISTS(SELECT 1 FROM ap_permission_action WHERE code='message.sms.automation.stop');

INSERT INTO ap_feature_group(code,name,status,sort_order) SELECT 'connections','connections','PREPARING',100 WHERE NOT EXISTS(SELECT 1 FROM ap_feature_group WHERE code='connections');

INSERT INTO ap_permission_action(code,group_code,name,description,binding_key,status,sort_order,created_at,updated_at) SELECT 'connection.kakao.read','connections','kakao 상태조회','P5 구현 예정',NULL,'PREPARING',245,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP WHERE NOT EXISTS(SELECT 1 FROM ap_permission_action WHERE code='connection.kakao.read');

INSERT INTO ap_feature_group(code,name,status,sort_order) SELECT 'connections','connections','PREPARING',100 WHERE NOT EXISTS(SELECT 1 FROM ap_feature_group WHERE code='connections');

INSERT INTO ap_permission_action(code,group_code,name,description,binding_key,status,sort_order,created_at,updated_at) SELECT 'connection.kakao.create','connections','kakao 연결','P5 구현 예정',NULL,'PREPARING',246,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP WHERE NOT EXISTS(SELECT 1 FROM ap_permission_action WHERE code='connection.kakao.create');

INSERT INTO ap_feature_group(code,name,status,sort_order) SELECT 'connections','connections','PREPARING',100 WHERE NOT EXISTS(SELECT 1 FROM ap_feature_group WHERE code='connections');

INSERT INTO ap_permission_action(code,group_code,name,description,binding_key,status,sort_order,created_at,updated_at) SELECT 'connection.kakao.update','connections','kakao 연결교체','P5 구현 예정',NULL,'PREPARING',247,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP WHERE NOT EXISTS(SELECT 1 FROM ap_permission_action WHERE code='connection.kakao.update');

INSERT INTO ap_feature_group(code,name,status,sort_order) SELECT 'connections','connections','PREPARING',100 WHERE NOT EXISTS(SELECT 1 FROM ap_feature_group WHERE code='connections');

INSERT INTO ap_permission_action(code,group_code,name,description,binding_key,status,sort_order,created_at,updated_at) SELECT 'connection.kakao.delete','connections','kakao 해제','P5 구현 예정',NULL,'PREPARING',248,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP WHERE NOT EXISTS(SELECT 1 FROM ap_permission_action WHERE code='connection.kakao.delete');

INSERT INTO ap_feature_group(code,name,status,sort_order) SELECT 'connections','connections','PREPARING',100 WHERE NOT EXISTS(SELECT 1 FROM ap_feature_group WHERE code='connections');

INSERT INTO ap_permission_action(code,group_code,name,description,binding_key,status,sort_order,created_at,updated_at) SELECT 'connection.kakao.test','connections','kakao 연결시험','P5 구현 예정',NULL,'PREPARING',249,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP WHERE NOT EXISTS(SELECT 1 FROM ap_permission_action WHERE code='connection.kakao.test');

INSERT INTO ap_feature_group(code,name,status,sort_order) SELECT 'message','message','PREPARING',100 WHERE NOT EXISTS(SELECT 1 FROM ap_feature_group WHERE code='message');

INSERT INTO ap_permission_action(code,group_code,name,description,binding_key,status,sort_order,created_at,updated_at) SELECT 'message.kakao.send','message','kakao 단발발송','P5 구현 예정',NULL,'PREPARING',250,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP WHERE NOT EXISTS(SELECT 1 FROM ap_permission_action WHERE code='message.kakao.send');

INSERT INTO ap_feature_group(code,name,status,sort_order) SELECT 'message','message','PREPARING',100 WHERE NOT EXISTS(SELECT 1 FROM ap_feature_group WHERE code='message');

INSERT INTO ap_permission_action(code,group_code,name,description,binding_key,status,sort_order,created_at,updated_at) SELECT 'message.kakao.automation.create','message','kakao 예약규칙생성','P5 구현 예정',NULL,'PREPARING',251,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP WHERE NOT EXISTS(SELECT 1 FROM ap_permission_action WHERE code='message.kakao.automation.create');

INSERT INTO ap_feature_group(code,name,status,sort_order) SELECT 'message','message','PREPARING',100 WHERE NOT EXISTS(SELECT 1 FROM ap_feature_group WHERE code='message');

INSERT INTO ap_permission_action(code,group_code,name,description,binding_key,status,sort_order,created_at,updated_at) SELECT 'message.kakao.automation.update','message','kakao 예약규칙수정','P5 구현 예정',NULL,'PREPARING',252,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP WHERE NOT EXISTS(SELECT 1 FROM ap_permission_action WHERE code='message.kakao.automation.update');

INSERT INTO ap_feature_group(code,name,status,sort_order) SELECT 'message','message','PREPARING',100 WHERE NOT EXISTS(SELECT 1 FROM ap_feature_group WHERE code='message');

INSERT INTO ap_permission_action(code,group_code,name,description,binding_key,status,sort_order,created_at,updated_at) SELECT 'message.kakao.automation.delete','message','kakao 예약규칙삭제','P5 구현 예정',NULL,'PREPARING',253,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP WHERE NOT EXISTS(SELECT 1 FROM ap_permission_action WHERE code='message.kakao.automation.delete');

INSERT INTO ap_feature_group(code,name,status,sort_order) SELECT 'message','message','PREPARING',100 WHERE NOT EXISTS(SELECT 1 FROM ap_feature_group WHERE code='message');

INSERT INTO ap_permission_action(code,group_code,name,description,binding_key,status,sort_order,created_at,updated_at) SELECT 'message.kakao.automation.activate','message','kakao 예약활성','P5 구현 예정',NULL,'PREPARING',254,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP WHERE NOT EXISTS(SELECT 1 FROM ap_permission_action WHERE code='message.kakao.automation.activate');

INSERT INTO ap_feature_group(code,name,status,sort_order) SELECT 'message','message','PREPARING',100 WHERE NOT EXISTS(SELECT 1 FROM ap_feature_group WHERE code='message');

INSERT INTO ap_permission_action(code,group_code,name,description,binding_key,status,sort_order,created_at,updated_at) SELECT 'message.kakao.automation.stop','message','kakao 예약중지','P5 구현 예정',NULL,'PREPARING',255,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP WHERE NOT EXISTS(SELECT 1 FROM ap_permission_action WHERE code='message.kakao.automation.stop');

INSERT INTO ap_feature_group(code,name,status,sort_order) SELECT 'workspace','workspace','PREPARING',100 WHERE NOT EXISTS(SELECT 1 FROM ap_feature_group WHERE code='workspace');

INSERT INTO ap_permission_action(code,group_code,name,description,binding_key,status,sort_order,created_at,updated_at) SELECT 'workspace.billing.read','workspace','본인구독조회','P5 구현 예정',NULL,'PREPARING',256,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP WHERE NOT EXISTS(SELECT 1 FROM ap_permission_action WHERE code='workspace.billing.read');

INSERT INTO ap_feature_group(code,name,status,sort_order) SELECT 'workspace','workspace','PREPARING',100 WHERE NOT EXISTS(SELECT 1 FROM ap_feature_group WHERE code='workspace');

INSERT INTO ap_permission_action(code,group_code,name,description,binding_key,status,sort_order,created_at,updated_at) SELECT 'workspace.billing.manage','workspace','본인구독변경','P5 구현 예정',NULL,'PREPARING',257,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP WHERE NOT EXISTS(SELECT 1 FROM ap_permission_action WHERE code='workspace.billing.manage');

INSERT INTO ap_permission_dependency(action_code,depends_on) SELECT 'workspace.dashboard.manage','workspace.dashboard.read' WHERE NOT EXISTS(SELECT 1 FROM ap_permission_dependency WHERE action_code='workspace.dashboard.manage' AND depends_on='workspace.dashboard.read');

INSERT INTO ap_permission_dependency(action_code,depends_on) SELECT 'workspace.draft.update','workspace.draft.read' WHERE NOT EXISTS(SELECT 1 FROM ap_permission_dependency WHERE action_code='workspace.draft.update' AND depends_on='workspace.draft.read');

INSERT INTO ap_permission_dependency(action_code,depends_on) SELECT 'workspace.draft.save','workspace.draft.read' WHERE NOT EXISTS(SELECT 1 FROM ap_permission_dependency WHERE action_code='workspace.draft.save' AND depends_on='workspace.draft.read');

INSERT INTO ap_permission_dependency(action_code,depends_on) SELECT 'excel.create.manual','excel.detail.read' WHERE NOT EXISTS(SELECT 1 FROM ap_permission_dependency WHERE action_code='excel.create.manual' AND depends_on='excel.detail.read');

INSERT INTO ap_permission_dependency(action_code,depends_on) SELECT 'excel.create.chat','excel.detail.read' WHERE NOT EXISTS(SELECT 1 FROM ap_permission_dependency WHERE action_code='excel.create.chat' AND depends_on='excel.detail.read');

INSERT INTO ap_permission_dependency(action_code,depends_on) SELECT 'excel.create.photo','excel.detail.read' WHERE NOT EXISTS(SELECT 1 FROM ap_permission_dependency WHERE action_code='excel.create.photo' AND depends_on='excel.detail.read');

INSERT INTO ap_permission_dependency(action_code,depends_on) SELECT 'excel.data.create','excel.detail.read' WHERE NOT EXISTS(SELECT 1 FROM ap_permission_dependency WHERE action_code='excel.data.create' AND depends_on='excel.detail.read');

INSERT INTO ap_permission_dependency(action_code,depends_on) SELECT 'excel.data.update','excel.detail.read' WHERE NOT EXISTS(SELECT 1 FROM ap_permission_dependency WHERE action_code='excel.data.update' AND depends_on='excel.detail.read');

INSERT INTO ap_permission_dependency(action_code,depends_on) SELECT 'excel.data.delete','excel.detail.read' WHERE NOT EXISTS(SELECT 1 FROM ap_permission_dependency WHERE action_code='excel.data.delete' AND depends_on='excel.detail.read');

INSERT INTO ap_permission_dependency(action_code,depends_on) SELECT 'excel.schema.update','excel.detail.read' WHERE NOT EXISTS(SELECT 1 FROM ap_permission_dependency WHERE action_code='excel.schema.update' AND depends_on='excel.detail.read');

INSERT INTO ap_permission_dependency(action_code,depends_on) SELECT 'excel.formula.manage','excel.detail.read' WHERE NOT EXISTS(SELECT 1 FROM ap_permission_dependency WHERE action_code='excel.formula.manage' AND depends_on='excel.detail.read');

INSERT INTO ap_permission_dependency(action_code,depends_on) SELECT 'excel.formula.ai','excel.detail.read' WHERE NOT EXISTS(SELECT 1 FROM ap_permission_dependency WHERE action_code='excel.formula.ai' AND depends_on='excel.detail.read');

INSERT INTO ap_permission_dependency(action_code,depends_on) SELECT 'excel.analysis.manage','excel.detail.read' WHERE NOT EXISTS(SELECT 1 FROM ap_permission_dependency WHERE action_code='excel.analysis.manage' AND depends_on='excel.detail.read');

INSERT INTO ap_permission_dependency(action_code,depends_on) SELECT 'excel.create.file','excel.detail.read' WHERE NOT EXISTS(SELECT 1 FROM ap_permission_dependency WHERE action_code='excel.create.file' AND depends_on='excel.detail.read');

INSERT INTO ap_permission_dependency(action_code,depends_on) SELECT 'excel.create.template','excel.detail.read' WHERE NOT EXISTS(SELECT 1 FROM ap_permission_dependency WHERE action_code='excel.create.template' AND depends_on='excel.detail.read');

INSERT INTO ap_permission_dependency(action_code,depends_on) SELECT 'excel.data.photo','excel.data.create' WHERE NOT EXISTS(SELECT 1 FROM ap_permission_dependency WHERE action_code='excel.data.photo' AND depends_on='excel.data.create');

INSERT INTO ap_permission_dependency(action_code,depends_on) SELECT 'excel.data.copy','excel.data.create' WHERE NOT EXISTS(SELECT 1 FROM ap_permission_dependency WHERE action_code='excel.data.copy' AND depends_on='excel.data.create');

INSERT INTO ap_permission_dependency(action_code,depends_on) SELECT 'excel.project.duplicate','excel.detail.read' WHERE NOT EXISTS(SELECT 1 FROM ap_permission_dependency WHERE action_code='excel.project.duplicate' AND depends_on='excel.detail.read');

INSERT INTO ap_permission_dependency(action_code,depends_on) SELECT 'excel.resource.manage','excel.detail.read' WHERE NOT EXISTS(SELECT 1 FROM ap_permission_dependency WHERE action_code='excel.resource.manage' AND depends_on='excel.detail.read');

INSERT INTO ap_permission_dependency(action_code,depends_on) SELECT 'word.create.manual','word.detail.read' WHERE NOT EXISTS(SELECT 1 FROM ap_permission_dependency WHERE action_code='word.create.manual' AND depends_on='word.detail.read');

INSERT INTO ap_permission_dependency(action_code,depends_on) SELECT 'word.create.chat','word.detail.read' WHERE NOT EXISTS(SELECT 1 FROM ap_permission_dependency WHERE action_code='word.create.chat' AND depends_on='word.detail.read');

INSERT INTO ap_permission_dependency(action_code,depends_on) SELECT 'word.create.photo','word.detail.read' WHERE NOT EXISTS(SELECT 1 FROM ap_permission_dependency WHERE action_code='word.create.photo' AND depends_on='word.detail.read');

INSERT INTO ap_permission_dependency(action_code,depends_on) SELECT 'word.create.file','word.detail.read' WHERE NOT EXISTS(SELECT 1 FROM ap_permission_dependency WHERE action_code='word.create.file' AND depends_on='word.detail.read');

INSERT INTO ap_permission_dependency(action_code,depends_on) SELECT 'word.create.template','word.detail.read' WHERE NOT EXISTS(SELECT 1 FROM ap_permission_dependency WHERE action_code='word.create.template' AND depends_on='word.detail.read');

INSERT INTO ap_permission_dependency(action_code,depends_on) SELECT 'word.meta.update','word.detail.read' WHERE NOT EXISTS(SELECT 1 FROM ap_permission_dependency WHERE action_code='word.meta.update' AND depends_on='word.detail.read');

INSERT INTO ap_permission_dependency(action_code,depends_on) SELECT 'word.content.update','word.detail.read' WHERE NOT EXISTS(SELECT 1 FROM ap_permission_dependency WHERE action_code='word.content.update' AND depends_on='word.detail.read');

INSERT INTO ap_permission_dependency(action_code,depends_on) SELECT 'word.object.create','word.detail.read' WHERE NOT EXISTS(SELECT 1 FROM ap_permission_dependency WHERE action_code='word.object.create' AND depends_on='word.detail.read');

INSERT INTO ap_permission_dependency(action_code,depends_on) SELECT 'word.object.update','word.detail.read' WHERE NOT EXISTS(SELECT 1 FROM ap_permission_dependency WHERE action_code='word.object.update' AND depends_on='word.detail.read');

INSERT INTO ap_permission_dependency(action_code,depends_on) SELECT 'word.object.delete','word.detail.read' WHERE NOT EXISTS(SELECT 1 FROM ap_permission_dependency WHERE action_code='word.object.delete' AND depends_on='word.detail.read');

INSERT INTO ap_permission_dependency(action_code,depends_on) SELECT 'word.edit.ai','word.detail.read' WHERE NOT EXISTS(SELECT 1 FROM ap_permission_dependency WHERE action_code='word.edit.ai' AND depends_on='word.detail.read');

INSERT INTO ap_permission_dependency(action_code,depends_on) SELECT 'word.version.read','word.detail.read' WHERE NOT EXISTS(SELECT 1 FROM ap_permission_dependency WHERE action_code='word.version.read' AND depends_on='word.detail.read');

INSERT INTO ap_permission_dependency(action_code,depends_on) SELECT 'word.version.restore','word.detail.read' WHERE NOT EXISTS(SELECT 1 FROM ap_permission_dependency WHERE action_code='word.version.restore' AND depends_on='word.detail.read');

INSERT INTO ap_permission_dependency(action_code,depends_on) SELECT 'word.template.manage','word.detail.read' WHERE NOT EXISTS(SELECT 1 FROM ap_permission_dependency WHERE action_code='word.template.manage' AND depends_on='word.detail.read');

INSERT INTO ap_permission_dependency(action_code,depends_on) SELECT 'word.resource.read','word.detail.read' WHERE NOT EXISTS(SELECT 1 FROM ap_permission_dependency WHERE action_code='word.resource.read' AND depends_on='word.detail.read');

INSERT INTO ap_permission_dependency(action_code,depends_on) SELECT 'word.resource.create','word.detail.read' WHERE NOT EXISTS(SELECT 1 FROM ap_permission_dependency WHERE action_code='word.resource.create' AND depends_on='word.detail.read');

INSERT INTO ap_permission_dependency(action_code,depends_on) SELECT 'word.resource.update','word.detail.read' WHERE NOT EXISTS(SELECT 1 FROM ap_permission_dependency WHERE action_code='word.resource.update' AND depends_on='word.detail.read');

INSERT INTO ap_permission_dependency(action_code,depends_on) SELECT 'word.resource.delete','word.detail.read' WHERE NOT EXISTS(SELECT 1 FROM ap_permission_dependency WHERE action_code='word.resource.delete' AND depends_on='word.detail.read');

INSERT INTO ap_permission_dependency(action_code,depends_on) SELECT 'word.layout.manage','word.detail.read' WHERE NOT EXISTS(SELECT 1 FROM ap_permission_dependency WHERE action_code='word.layout.manage' AND depends_on='word.detail.read');

INSERT INTO ap_permission_dependency(action_code,depends_on) SELECT 'word.data.link','word.detail.read' WHERE NOT EXISTS(SELECT 1 FROM ap_permission_dependency WHERE action_code='word.data.link' AND depends_on='word.detail.read');

INSERT INTO ap_permission_dependency(action_code,depends_on) SELECT 'word.data.refresh','word.detail.read' WHERE NOT EXISTS(SELECT 1 FROM ap_permission_dependency WHERE action_code='word.data.refresh' AND depends_on='word.detail.read');

INSERT INTO ap_permission_dependency(action_code,depends_on) SELECT 'word.project.archive','word.detail.read' WHERE NOT EXISTS(SELECT 1 FROM ap_permission_dependency WHERE action_code='word.project.archive' AND depends_on='word.detail.read');

INSERT INTO ap_permission_dependency(action_code,depends_on) SELECT 'word.project.delete','word.detail.read' WHERE NOT EXISTS(SELECT 1 FROM ap_permission_dependency WHERE action_code='word.project.delete' AND depends_on='word.detail.read');

INSERT INTO ap_permission_dependency(action_code,depends_on) SELECT 'word.project.restore','word.detail.read' WHERE NOT EXISTS(SELECT 1 FROM ap_permission_dependency WHERE action_code='word.project.restore' AND depends_on='word.detail.read');

INSERT INTO ap_permission_dependency(action_code,depends_on) SELECT 'word.project.duplicate','word.detail.read' WHERE NOT EXISTS(SELECT 1 FROM ap_permission_dependency WHERE action_code='word.project.duplicate' AND depends_on='word.detail.read');

INSERT INTO ap_permission_dependency(action_code,depends_on) SELECT 'hwp.create.manual','hwp.detail.read' WHERE NOT EXISTS(SELECT 1 FROM ap_permission_dependency WHERE action_code='hwp.create.manual' AND depends_on='hwp.detail.read');

INSERT INTO ap_permission_dependency(action_code,depends_on) SELECT 'hwp.create.chat','hwp.detail.read' WHERE NOT EXISTS(SELECT 1 FROM ap_permission_dependency WHERE action_code='hwp.create.chat' AND depends_on='hwp.detail.read');

INSERT INTO ap_permission_dependency(action_code,depends_on) SELECT 'hwp.create.photo','hwp.detail.read' WHERE NOT EXISTS(SELECT 1 FROM ap_permission_dependency WHERE action_code='hwp.create.photo' AND depends_on='hwp.detail.read');

INSERT INTO ap_permission_dependency(action_code,depends_on) SELECT 'hwp.create.file','hwp.detail.read' WHERE NOT EXISTS(SELECT 1 FROM ap_permission_dependency WHERE action_code='hwp.create.file' AND depends_on='hwp.detail.read');

INSERT INTO ap_permission_dependency(action_code,depends_on) SELECT 'hwp.create.template','hwp.detail.read' WHERE NOT EXISTS(SELECT 1 FROM ap_permission_dependency WHERE action_code='hwp.create.template' AND depends_on='hwp.detail.read');

INSERT INTO ap_permission_dependency(action_code,depends_on) SELECT 'hwp.meta.update','hwp.detail.read' WHERE NOT EXISTS(SELECT 1 FROM ap_permission_dependency WHERE action_code='hwp.meta.update' AND depends_on='hwp.detail.read');

INSERT INTO ap_permission_dependency(action_code,depends_on) SELECT 'hwp.content.update','hwp.detail.read' WHERE NOT EXISTS(SELECT 1 FROM ap_permission_dependency WHERE action_code='hwp.content.update' AND depends_on='hwp.detail.read');

INSERT INTO ap_permission_dependency(action_code,depends_on) SELECT 'hwp.object.create','hwp.detail.read' WHERE NOT EXISTS(SELECT 1 FROM ap_permission_dependency WHERE action_code='hwp.object.create' AND depends_on='hwp.detail.read');

INSERT INTO ap_permission_dependency(action_code,depends_on) SELECT 'hwp.object.update','hwp.detail.read' WHERE NOT EXISTS(SELECT 1 FROM ap_permission_dependency WHERE action_code='hwp.object.update' AND depends_on='hwp.detail.read');

INSERT INTO ap_permission_dependency(action_code,depends_on) SELECT 'hwp.object.delete','hwp.detail.read' WHERE NOT EXISTS(SELECT 1 FROM ap_permission_dependency WHERE action_code='hwp.object.delete' AND depends_on='hwp.detail.read');

INSERT INTO ap_permission_dependency(action_code,depends_on) SELECT 'hwp.edit.ai','hwp.detail.read' WHERE NOT EXISTS(SELECT 1 FROM ap_permission_dependency WHERE action_code='hwp.edit.ai' AND depends_on='hwp.detail.read');

INSERT INTO ap_permission_dependency(action_code,depends_on) SELECT 'hwp.version.read','hwp.detail.read' WHERE NOT EXISTS(SELECT 1 FROM ap_permission_dependency WHERE action_code='hwp.version.read' AND depends_on='hwp.detail.read');

INSERT INTO ap_permission_dependency(action_code,depends_on) SELECT 'hwp.version.restore','hwp.detail.read' WHERE NOT EXISTS(SELECT 1 FROM ap_permission_dependency WHERE action_code='hwp.version.restore' AND depends_on='hwp.detail.read');

INSERT INTO ap_permission_dependency(action_code,depends_on) SELECT 'hwp.template.manage','hwp.detail.read' WHERE NOT EXISTS(SELECT 1 FROM ap_permission_dependency WHERE action_code='hwp.template.manage' AND depends_on='hwp.detail.read');

INSERT INTO ap_permission_dependency(action_code,depends_on) SELECT 'hwp.resource.read','hwp.detail.read' WHERE NOT EXISTS(SELECT 1 FROM ap_permission_dependency WHERE action_code='hwp.resource.read' AND depends_on='hwp.detail.read');

INSERT INTO ap_permission_dependency(action_code,depends_on) SELECT 'hwp.resource.create','hwp.detail.read' WHERE NOT EXISTS(SELECT 1 FROM ap_permission_dependency WHERE action_code='hwp.resource.create' AND depends_on='hwp.detail.read');

INSERT INTO ap_permission_dependency(action_code,depends_on) SELECT 'hwp.resource.update','hwp.detail.read' WHERE NOT EXISTS(SELECT 1 FROM ap_permission_dependency WHERE action_code='hwp.resource.update' AND depends_on='hwp.detail.read');

INSERT INTO ap_permission_dependency(action_code,depends_on) SELECT 'hwp.resource.delete','hwp.detail.read' WHERE NOT EXISTS(SELECT 1 FROM ap_permission_dependency WHERE action_code='hwp.resource.delete' AND depends_on='hwp.detail.read');

INSERT INTO ap_permission_dependency(action_code,depends_on) SELECT 'hwp.layout.manage','hwp.detail.read' WHERE NOT EXISTS(SELECT 1 FROM ap_permission_dependency WHERE action_code='hwp.layout.manage' AND depends_on='hwp.detail.read');

INSERT INTO ap_permission_dependency(action_code,depends_on) SELECT 'hwp.data.link','hwp.detail.read' WHERE NOT EXISTS(SELECT 1 FROM ap_permission_dependency WHERE action_code='hwp.data.link' AND depends_on='hwp.detail.read');

INSERT INTO ap_permission_dependency(action_code,depends_on) SELECT 'hwp.data.refresh','hwp.detail.read' WHERE NOT EXISTS(SELECT 1 FROM ap_permission_dependency WHERE action_code='hwp.data.refresh' AND depends_on='hwp.detail.read');

INSERT INTO ap_permission_dependency(action_code,depends_on) SELECT 'hwp.project.archive','hwp.detail.read' WHERE NOT EXISTS(SELECT 1 FROM ap_permission_dependency WHERE action_code='hwp.project.archive' AND depends_on='hwp.detail.read');

INSERT INTO ap_permission_dependency(action_code,depends_on) SELECT 'hwp.project.delete','hwp.detail.read' WHERE NOT EXISTS(SELECT 1 FROM ap_permission_dependency WHERE action_code='hwp.project.delete' AND depends_on='hwp.detail.read');

INSERT INTO ap_permission_dependency(action_code,depends_on) SELECT 'hwp.project.restore','hwp.detail.read' WHERE NOT EXISTS(SELECT 1 FROM ap_permission_dependency WHERE action_code='hwp.project.restore' AND depends_on='hwp.detail.read');

INSERT INTO ap_permission_dependency(action_code,depends_on) SELECT 'hwp.project.duplicate','hwp.detail.read' WHERE NOT EXISTS(SELECT 1 FROM ap_permission_dependency WHERE action_code='hwp.project.duplicate' AND depends_on='hwp.detail.read');

INSERT INTO ap_permission_dependency(action_code,depends_on) SELECT 'ppt.create.manual','ppt.detail.read' WHERE NOT EXISTS(SELECT 1 FROM ap_permission_dependency WHERE action_code='ppt.create.manual' AND depends_on='ppt.detail.read');

INSERT INTO ap_permission_dependency(action_code,depends_on) SELECT 'ppt.create.chat','ppt.detail.read' WHERE NOT EXISTS(SELECT 1 FROM ap_permission_dependency WHERE action_code='ppt.create.chat' AND depends_on='ppt.detail.read');

INSERT INTO ap_permission_dependency(action_code,depends_on) SELECT 'ppt.create.photo','ppt.detail.read' WHERE NOT EXISTS(SELECT 1 FROM ap_permission_dependency WHERE action_code='ppt.create.photo' AND depends_on='ppt.detail.read');

INSERT INTO ap_permission_dependency(action_code,depends_on) SELECT 'ppt.create.file','ppt.detail.read' WHERE NOT EXISTS(SELECT 1 FROM ap_permission_dependency WHERE action_code='ppt.create.file' AND depends_on='ppt.detail.read');

INSERT INTO ap_permission_dependency(action_code,depends_on) SELECT 'ppt.create.template','ppt.detail.read' WHERE NOT EXISTS(SELECT 1 FROM ap_permission_dependency WHERE action_code='ppt.create.template' AND depends_on='ppt.detail.read');

INSERT INTO ap_permission_dependency(action_code,depends_on) SELECT 'ppt.meta.update','ppt.detail.read' WHERE NOT EXISTS(SELECT 1 FROM ap_permission_dependency WHERE action_code='ppt.meta.update' AND depends_on='ppt.detail.read');

INSERT INTO ap_permission_dependency(action_code,depends_on) SELECT 'ppt.content.update','ppt.detail.read' WHERE NOT EXISTS(SELECT 1 FROM ap_permission_dependency WHERE action_code='ppt.content.update' AND depends_on='ppt.detail.read');

INSERT INTO ap_permission_dependency(action_code,depends_on) SELECT 'ppt.object.create','ppt.detail.read' WHERE NOT EXISTS(SELECT 1 FROM ap_permission_dependency WHERE action_code='ppt.object.create' AND depends_on='ppt.detail.read');

INSERT INTO ap_permission_dependency(action_code,depends_on) SELECT 'ppt.object.update','ppt.detail.read' WHERE NOT EXISTS(SELECT 1 FROM ap_permission_dependency WHERE action_code='ppt.object.update' AND depends_on='ppt.detail.read');

INSERT INTO ap_permission_dependency(action_code,depends_on) SELECT 'ppt.object.delete','ppt.detail.read' WHERE NOT EXISTS(SELECT 1 FROM ap_permission_dependency WHERE action_code='ppt.object.delete' AND depends_on='ppt.detail.read');

INSERT INTO ap_permission_dependency(action_code,depends_on) SELECT 'ppt.edit.ai','ppt.detail.read' WHERE NOT EXISTS(SELECT 1 FROM ap_permission_dependency WHERE action_code='ppt.edit.ai' AND depends_on='ppt.detail.read');

INSERT INTO ap_permission_dependency(action_code,depends_on) SELECT 'ppt.version.read','ppt.detail.read' WHERE NOT EXISTS(SELECT 1 FROM ap_permission_dependency WHERE action_code='ppt.version.read' AND depends_on='ppt.detail.read');

INSERT INTO ap_permission_dependency(action_code,depends_on) SELECT 'ppt.version.restore','ppt.detail.read' WHERE NOT EXISTS(SELECT 1 FROM ap_permission_dependency WHERE action_code='ppt.version.restore' AND depends_on='ppt.detail.read');

INSERT INTO ap_permission_dependency(action_code,depends_on) SELECT 'ppt.template.manage','ppt.detail.read' WHERE NOT EXISTS(SELECT 1 FROM ap_permission_dependency WHERE action_code='ppt.template.manage' AND depends_on='ppt.detail.read');

INSERT INTO ap_permission_dependency(action_code,depends_on) SELECT 'ppt.resource.read','ppt.detail.read' WHERE NOT EXISTS(SELECT 1 FROM ap_permission_dependency WHERE action_code='ppt.resource.read' AND depends_on='ppt.detail.read');

INSERT INTO ap_permission_dependency(action_code,depends_on) SELECT 'ppt.resource.create','ppt.detail.read' WHERE NOT EXISTS(SELECT 1 FROM ap_permission_dependency WHERE action_code='ppt.resource.create' AND depends_on='ppt.detail.read');

INSERT INTO ap_permission_dependency(action_code,depends_on) SELECT 'ppt.resource.update','ppt.detail.read' WHERE NOT EXISTS(SELECT 1 FROM ap_permission_dependency WHERE action_code='ppt.resource.update' AND depends_on='ppt.detail.read');

INSERT INTO ap_permission_dependency(action_code,depends_on) SELECT 'ppt.resource.delete','ppt.detail.read' WHERE NOT EXISTS(SELECT 1 FROM ap_permission_dependency WHERE action_code='ppt.resource.delete' AND depends_on='ppt.detail.read');

INSERT INTO ap_permission_dependency(action_code,depends_on) SELECT 'ppt.layout.manage','ppt.detail.read' WHERE NOT EXISTS(SELECT 1 FROM ap_permission_dependency WHERE action_code='ppt.layout.manage' AND depends_on='ppt.detail.read');

INSERT INTO ap_permission_dependency(action_code,depends_on) SELECT 'ppt.data.link','ppt.detail.read' WHERE NOT EXISTS(SELECT 1 FROM ap_permission_dependency WHERE action_code='ppt.data.link' AND depends_on='ppt.detail.read');

INSERT INTO ap_permission_dependency(action_code,depends_on) SELECT 'ppt.data.refresh','ppt.detail.read' WHERE NOT EXISTS(SELECT 1 FROM ap_permission_dependency WHERE action_code='ppt.data.refresh' AND depends_on='ppt.detail.read');

INSERT INTO ap_permission_dependency(action_code,depends_on) SELECT 'ppt.project.archive','ppt.detail.read' WHERE NOT EXISTS(SELECT 1 FROM ap_permission_dependency WHERE action_code='ppt.project.archive' AND depends_on='ppt.detail.read');

INSERT INTO ap_permission_dependency(action_code,depends_on) SELECT 'ppt.project.delete','ppt.detail.read' WHERE NOT EXISTS(SELECT 1 FROM ap_permission_dependency WHERE action_code='ppt.project.delete' AND depends_on='ppt.detail.read');

INSERT INTO ap_permission_dependency(action_code,depends_on) SELECT 'ppt.project.restore','ppt.detail.read' WHERE NOT EXISTS(SELECT 1 FROM ap_permission_dependency WHERE action_code='ppt.project.restore' AND depends_on='ppt.detail.read');

INSERT INTO ap_permission_dependency(action_code,depends_on) SELECT 'ppt.project.duplicate','ppt.detail.read' WHERE NOT EXISTS(SELECT 1 FROM ap_permission_dependency WHERE action_code='ppt.project.duplicate' AND depends_on='ppt.detail.read');

INSERT INTO ap_permission_dependency(action_code,depends_on) SELECT 'crm.record.update','crm.record.detail.read' WHERE NOT EXISTS(SELECT 1 FROM ap_permission_dependency WHERE action_code='crm.record.update' AND depends_on='crm.record.detail.read');

INSERT INTO ap_permission_dependency(action_code,depends_on) SELECT 'crm.record.delete','crm.record.detail.read' WHERE NOT EXISTS(SELECT 1 FROM ap_permission_dependency WHERE action_code='crm.record.delete' AND depends_on='crm.record.detail.read');

INSERT INTO ap_permission_dependency(action_code,depends_on) SELECT 'crm.record.ai','crm.record.detail.read' WHERE NOT EXISTS(SELECT 1 FROM ap_permission_dependency WHERE action_code='crm.record.ai' AND depends_on='crm.record.detail.read');

INSERT INTO ap_permission_dependency(action_code,depends_on) SELECT 'crm.attachment.manage','crm.record.detail.read' WHERE NOT EXISTS(SELECT 1 FROM ap_permission_dependency WHERE action_code='crm.attachment.manage' AND depends_on='crm.record.detail.read');

INSERT INTO ap_permission_dependency(action_code,depends_on) SELECT 'calendar.event.update','calendar.detail.read' WHERE NOT EXISTS(SELECT 1 FROM ap_permission_dependency WHERE action_code='calendar.event.update' AND depends_on='calendar.detail.read');

INSERT INTO ap_permission_dependency(action_code,depends_on) SELECT 'calendar.event.delete','calendar.detail.read' WHERE NOT EXISTS(SELECT 1 FROM ap_permission_dependency WHERE action_code='calendar.event.delete' AND depends_on='calendar.detail.read');

INSERT INTO ap_permission_dependency(action_code,depends_on) SELECT 'calendar.event.ai.update','calendar.detail.read' WHERE NOT EXISTS(SELECT 1 FROM ap_permission_dependency WHERE action_code='calendar.event.ai.update' AND depends_on='calendar.detail.read');

INSERT INTO ap_permission_dependency(action_code,depends_on) SELECT 'calendar.recurrence.manage','calendar.detail.read' WHERE NOT EXISTS(SELECT 1 FROM ap_permission_dependency WHERE action_code='calendar.recurrence.manage' AND depends_on='calendar.detail.read');

INSERT INTO ap_permission_dependency(action_code,depends_on) SELECT 'calendar.reminder.manage','calendar.detail.read' WHERE NOT EXISTS(SELECT 1 FROM ap_permission_dependency WHERE action_code='calendar.reminder.manage' AND depends_on='calendar.detail.read');

INSERT INTO ap_permission_dependency(action_code,depends_on) SELECT 'calendar.attachment.manage','calendar.detail.read' WHERE NOT EXISTS(SELECT 1 FROM ap_permission_dependency WHERE action_code='calendar.attachment.manage' AND depends_on='calendar.detail.read');

INSERT INTO ap_permission_dependency(action_code,depends_on) SELECT 'app.component.create','app.detail.read' WHERE NOT EXISTS(SELECT 1 FROM ap_permission_dependency WHERE action_code='app.component.create' AND depends_on='app.detail.read');

INSERT INTO ap_permission_dependency(action_code,depends_on) SELECT 'app.component.update','app.detail.read' WHERE NOT EXISTS(SELECT 1 FROM ap_permission_dependency WHERE action_code='app.component.update' AND depends_on='app.detail.read');

INSERT INTO ap_permission_dependency(action_code,depends_on) SELECT 'app.component.delete','app.detail.read' WHERE NOT EXISTS(SELECT 1 FROM ap_permission_dependency WHERE action_code='app.component.delete' AND depends_on='app.detail.read');

INSERT INTO ap_permission_dependency(action_code,depends_on) SELECT 'app.edit.ai','app.detail.read' WHERE NOT EXISTS(SELECT 1 FROM ap_permission_dependency WHERE action_code='app.edit.ai' AND depends_on='app.detail.read');

INSERT INTO ap_permission_dependency(action_code,depends_on) SELECT 'app.trial.execute','app.detail.read' WHERE NOT EXISTS(SELECT 1 FROM ap_permission_dependency WHERE action_code='app.trial.execute' AND depends_on='app.detail.read');

INSERT INTO ap_permission_dependency(action_code,depends_on) SELECT 'app.version.create','app.detail.read' WHERE NOT EXISTS(SELECT 1 FROM ap_permission_dependency WHERE action_code='app.version.create' AND depends_on='app.detail.read');

INSERT INTO ap_permission_dependency(action_code,depends_on) SELECT 'message.gmail.automation.activate','message.gmail.send' WHERE NOT EXISTS(SELECT 1 FROM ap_permission_dependency WHERE action_code='message.gmail.automation.activate' AND depends_on='message.gmail.send');

INSERT INTO ap_permission_dependency(action_code,depends_on) SELECT 'message.sms.automation.activate','message.sms.send' WHERE NOT EXISTS(SELECT 1 FROM ap_permission_dependency WHERE action_code='message.sms.automation.activate' AND depends_on='message.sms.send');

INSERT INTO ap_permission_dependency(action_code,depends_on) SELECT 'message.kakao.automation.activate','message.kakao.send' WHERE NOT EXISTS(SELECT 1 FROM ap_permission_dependency WHERE action_code='message.kakao.automation.activate' AND depends_on='message.kakao.send');

INSERT INTO ap_policy_version(id,revision,kind,reason,created_at) SELECT 1,1,'INITIAL','P1 최초 공통 기반 권한',CURRENT_TIMESTAMP WHERE NOT EXISTS(SELECT 1 FROM ap_policy_version WHERE id=1);

INSERT INTO ap_policy_state(id,revision,active_policy_id) SELECT 1,1,1 WHERE NOT EXISTS(SELECT 1 FROM ap_policy_state WHERE id=1);

INSERT INTO ap_policy_grant(policy_version_id,tier_code,action_code,sort_order) SELECT 1,'GUEST','workspace.scope.read',0 WHERE NOT EXISTS(SELECT 1 FROM ap_policy_grant WHERE policy_version_id=1 AND tier_code='GUEST' AND action_code='workspace.scope.read');

INSERT INTO ap_policy_grant(policy_version_id,tier_code,action_code,sort_order) SELECT 1,'GUEST','workspace.scope.create',1 WHERE NOT EXISTS(SELECT 1 FROM ap_policy_grant WHERE policy_version_id=1 AND tier_code='GUEST' AND action_code='workspace.scope.create');

INSERT INTO ap_policy_grant(policy_version_id,tier_code,action_code,sort_order) SELECT 1,'GUEST','workspace.scope.update',2 WHERE NOT EXISTS(SELECT 1 FROM ap_policy_grant WHERE policy_version_id=1 AND tier_code='GUEST' AND action_code='workspace.scope.update');

INSERT INTO ap_policy_grant(policy_version_id,tier_code,action_code,sort_order) SELECT 1,'GUEST','workspace.scope.archive',3 WHERE NOT EXISTS(SELECT 1 FROM ap_policy_grant WHERE policy_version_id=1 AND tier_code='GUEST' AND action_code='workspace.scope.archive');

INSERT INTO ap_policy_grant(policy_version_id,tier_code,action_code,sort_order) SELECT 1,'GUEST','workspace.dashboard.read',4 WHERE NOT EXISTS(SELECT 1 FROM ap_policy_grant WHERE policy_version_id=1 AND tier_code='GUEST' AND action_code='workspace.dashboard.read');

INSERT INTO ap_policy_grant(policy_version_id,tier_code,action_code,sort_order) SELECT 1,'GUEST','workspace.dashboard.manage',5 WHERE NOT EXISTS(SELECT 1 FROM ap_policy_grant WHERE policy_version_id=1 AND tier_code='GUEST' AND action_code='workspace.dashboard.manage');

INSERT INTO ap_policy_grant(policy_version_id,tier_code,action_code,sort_order) SELECT 1,'GUEST','workspace.events.read',6 WHERE NOT EXISTS(SELECT 1 FROM ap_policy_grant WHERE policy_version_id=1 AND tier_code='GUEST' AND action_code='workspace.events.read');

INSERT INTO ap_policy_grant(policy_version_id,tier_code,action_code,sort_order) SELECT 1,'GUEST','workspace.draft.list',7 WHERE NOT EXISTS(SELECT 1 FROM ap_policy_grant WHERE policy_version_id=1 AND tier_code='GUEST' AND action_code='workspace.draft.list');

INSERT INTO ap_policy_grant(policy_version_id,tier_code,action_code,sort_order) SELECT 1,'GUEST','workspace.draft.read',8 WHERE NOT EXISTS(SELECT 1 FROM ap_policy_grant WHERE policy_version_id=1 AND tier_code='GUEST' AND action_code='workspace.draft.read');

INSERT INTO ap_policy_grant(policy_version_id,tier_code,action_code,sort_order) SELECT 1,'GUEST','workspace.draft.create',9 WHERE NOT EXISTS(SELECT 1 FROM ap_policy_grant WHERE policy_version_id=1 AND tier_code='GUEST' AND action_code='workspace.draft.create');

INSERT INTO ap_policy_grant(policy_version_id,tier_code,action_code,sort_order) SELECT 1,'GUEST','workspace.draft.update',10 WHERE NOT EXISTS(SELECT 1 FROM ap_policy_grant WHERE policy_version_id=1 AND tier_code='GUEST' AND action_code='workspace.draft.update');

INSERT INTO ap_policy_grant(policy_version_id,tier_code,action_code,sort_order) SELECT 1,'GUEST','workspace.draft.save',11 WHERE NOT EXISTS(SELECT 1 FROM ap_policy_grant WHERE policy_version_id=1 AND tier_code='GUEST' AND action_code='workspace.draft.save');

INSERT INTO ap_policy_grant(policy_version_id,tier_code,action_code,sort_order) SELECT 1,'GUEST','workspace.draft.archive',12 WHERE NOT EXISTS(SELECT 1 FROM ap_policy_grant WHERE policy_version_id=1 AND tier_code='GUEST' AND action_code='workspace.draft.archive');

INSERT INTO ap_policy_grant(policy_version_id,tier_code,action_code,sort_order) SELECT 1,'GUEST','workspace.draft.restore',13 WHERE NOT EXISTS(SELECT 1 FROM ap_policy_grant WHERE policy_version_id=1 AND tier_code='GUEST' AND action_code='workspace.draft.restore');

INSERT INTO ap_policy_grant(policy_version_id,tier_code,action_code,sort_order) SELECT 1,'GUEST','workspace.asset.list',14 WHERE NOT EXISTS(SELECT 1 FROM ap_policy_grant WHERE policy_version_id=1 AND tier_code='GUEST' AND action_code='workspace.asset.list');

INSERT INTO ap_policy_grant(policy_version_id,tier_code,action_code,sort_order) SELECT 1,'GUEST','workspace.asset.upload',15 WHERE NOT EXISTS(SELECT 1 FROM ap_policy_grant WHERE policy_version_id=1 AND tier_code='GUEST' AND action_code='workspace.asset.upload');

INSERT INTO ap_policy_grant(policy_version_id,tier_code,action_code,sort_order) SELECT 1,'GUEST','workspace.asset.download',16 WHERE NOT EXISTS(SELECT 1 FROM ap_policy_grant WHERE policy_version_id=1 AND tier_code='GUEST' AND action_code='workspace.asset.download');

INSERT INTO ap_policy_grant(policy_version_id,tier_code,action_code,sort_order) SELECT 1,'GUEST','workspace.asset.archive',17 WHERE NOT EXISTS(SELECT 1 FROM ap_policy_grant WHERE policy_version_id=1 AND tier_code='GUEST' AND action_code='workspace.asset.archive');

INSERT INTO ap_policy_grant(policy_version_id,tier_code,action_code,sort_order) SELECT 1,'TIER1','workspace.scope.read',0 WHERE NOT EXISTS(SELECT 1 FROM ap_policy_grant WHERE policy_version_id=1 AND tier_code='TIER1' AND action_code='workspace.scope.read');

INSERT INTO ap_policy_grant(policy_version_id,tier_code,action_code,sort_order) SELECT 1,'TIER1','workspace.scope.create',1 WHERE NOT EXISTS(SELECT 1 FROM ap_policy_grant WHERE policy_version_id=1 AND tier_code='TIER1' AND action_code='workspace.scope.create');

INSERT INTO ap_policy_grant(policy_version_id,tier_code,action_code,sort_order) SELECT 1,'TIER1','workspace.scope.update',2 WHERE NOT EXISTS(SELECT 1 FROM ap_policy_grant WHERE policy_version_id=1 AND tier_code='TIER1' AND action_code='workspace.scope.update');

INSERT INTO ap_policy_grant(policy_version_id,tier_code,action_code,sort_order) SELECT 1,'TIER1','workspace.scope.archive',3 WHERE NOT EXISTS(SELECT 1 FROM ap_policy_grant WHERE policy_version_id=1 AND tier_code='TIER1' AND action_code='workspace.scope.archive');

INSERT INTO ap_policy_grant(policy_version_id,tier_code,action_code,sort_order) SELECT 1,'TIER1','workspace.dashboard.read',4 WHERE NOT EXISTS(SELECT 1 FROM ap_policy_grant WHERE policy_version_id=1 AND tier_code='TIER1' AND action_code='workspace.dashboard.read');

INSERT INTO ap_policy_grant(policy_version_id,tier_code,action_code,sort_order) SELECT 1,'TIER1','workspace.dashboard.manage',5 WHERE NOT EXISTS(SELECT 1 FROM ap_policy_grant WHERE policy_version_id=1 AND tier_code='TIER1' AND action_code='workspace.dashboard.manage');

INSERT INTO ap_policy_grant(policy_version_id,tier_code,action_code,sort_order) SELECT 1,'TIER1','workspace.events.read',6 WHERE NOT EXISTS(SELECT 1 FROM ap_policy_grant WHERE policy_version_id=1 AND tier_code='TIER1' AND action_code='workspace.events.read');

INSERT INTO ap_policy_grant(policy_version_id,tier_code,action_code,sort_order) SELECT 1,'TIER1','workspace.draft.list',7 WHERE NOT EXISTS(SELECT 1 FROM ap_policy_grant WHERE policy_version_id=1 AND tier_code='TIER1' AND action_code='workspace.draft.list');

INSERT INTO ap_policy_grant(policy_version_id,tier_code,action_code,sort_order) SELECT 1,'TIER1','workspace.draft.read',8 WHERE NOT EXISTS(SELECT 1 FROM ap_policy_grant WHERE policy_version_id=1 AND tier_code='TIER1' AND action_code='workspace.draft.read');

INSERT INTO ap_policy_grant(policy_version_id,tier_code,action_code,sort_order) SELECT 1,'TIER1','workspace.draft.create',9 WHERE NOT EXISTS(SELECT 1 FROM ap_policy_grant WHERE policy_version_id=1 AND tier_code='TIER1' AND action_code='workspace.draft.create');

INSERT INTO ap_policy_grant(policy_version_id,tier_code,action_code,sort_order) SELECT 1,'TIER1','workspace.draft.update',10 WHERE NOT EXISTS(SELECT 1 FROM ap_policy_grant WHERE policy_version_id=1 AND tier_code='TIER1' AND action_code='workspace.draft.update');

INSERT INTO ap_policy_grant(policy_version_id,tier_code,action_code,sort_order) SELECT 1,'TIER1','workspace.draft.save',11 WHERE NOT EXISTS(SELECT 1 FROM ap_policy_grant WHERE policy_version_id=1 AND tier_code='TIER1' AND action_code='workspace.draft.save');

INSERT INTO ap_policy_grant(policy_version_id,tier_code,action_code,sort_order) SELECT 1,'TIER1','workspace.draft.archive',12 WHERE NOT EXISTS(SELECT 1 FROM ap_policy_grant WHERE policy_version_id=1 AND tier_code='TIER1' AND action_code='workspace.draft.archive');

INSERT INTO ap_policy_grant(policy_version_id,tier_code,action_code,sort_order) SELECT 1,'TIER1','workspace.draft.restore',13 WHERE NOT EXISTS(SELECT 1 FROM ap_policy_grant WHERE policy_version_id=1 AND tier_code='TIER1' AND action_code='workspace.draft.restore');

INSERT INTO ap_policy_grant(policy_version_id,tier_code,action_code,sort_order) SELECT 1,'TIER1','workspace.asset.list',14 WHERE NOT EXISTS(SELECT 1 FROM ap_policy_grant WHERE policy_version_id=1 AND tier_code='TIER1' AND action_code='workspace.asset.list');

INSERT INTO ap_policy_grant(policy_version_id,tier_code,action_code,sort_order) SELECT 1,'TIER1','workspace.asset.upload',15 WHERE NOT EXISTS(SELECT 1 FROM ap_policy_grant WHERE policy_version_id=1 AND tier_code='TIER1' AND action_code='workspace.asset.upload');

INSERT INTO ap_policy_grant(policy_version_id,tier_code,action_code,sort_order) SELECT 1,'TIER1','workspace.asset.download',16 WHERE NOT EXISTS(SELECT 1 FROM ap_policy_grant WHERE policy_version_id=1 AND tier_code='TIER1' AND action_code='workspace.asset.download');

INSERT INTO ap_policy_grant(policy_version_id,tier_code,action_code,sort_order) SELECT 1,'TIER1','workspace.asset.archive',17 WHERE NOT EXISTS(SELECT 1 FROM ap_policy_grant WHERE policy_version_id=1 AND tier_code='TIER1' AND action_code='workspace.asset.archive');

INSERT INTO ap_policy_grant(policy_version_id,tier_code,action_code,sort_order) SELECT 1,'TIER2','workspace.scope.read',0 WHERE NOT EXISTS(SELECT 1 FROM ap_policy_grant WHERE policy_version_id=1 AND tier_code='TIER2' AND action_code='workspace.scope.read');

INSERT INTO ap_policy_grant(policy_version_id,tier_code,action_code,sort_order) SELECT 1,'TIER2','workspace.scope.create',1 WHERE NOT EXISTS(SELECT 1 FROM ap_policy_grant WHERE policy_version_id=1 AND tier_code='TIER2' AND action_code='workspace.scope.create');

INSERT INTO ap_policy_grant(policy_version_id,tier_code,action_code,sort_order) SELECT 1,'TIER2','workspace.scope.update',2 WHERE NOT EXISTS(SELECT 1 FROM ap_policy_grant WHERE policy_version_id=1 AND tier_code='TIER2' AND action_code='workspace.scope.update');

INSERT INTO ap_policy_grant(policy_version_id,tier_code,action_code,sort_order) SELECT 1,'TIER2','workspace.scope.archive',3 WHERE NOT EXISTS(SELECT 1 FROM ap_policy_grant WHERE policy_version_id=1 AND tier_code='TIER2' AND action_code='workspace.scope.archive');

INSERT INTO ap_policy_grant(policy_version_id,tier_code,action_code,sort_order) SELECT 1,'TIER2','workspace.dashboard.read',4 WHERE NOT EXISTS(SELECT 1 FROM ap_policy_grant WHERE policy_version_id=1 AND tier_code='TIER2' AND action_code='workspace.dashboard.read');

INSERT INTO ap_policy_grant(policy_version_id,tier_code,action_code,sort_order) SELECT 1,'TIER2','workspace.dashboard.manage',5 WHERE NOT EXISTS(SELECT 1 FROM ap_policy_grant WHERE policy_version_id=1 AND tier_code='TIER2' AND action_code='workspace.dashboard.manage');

INSERT INTO ap_policy_grant(policy_version_id,tier_code,action_code,sort_order) SELECT 1,'TIER2','workspace.events.read',6 WHERE NOT EXISTS(SELECT 1 FROM ap_policy_grant WHERE policy_version_id=1 AND tier_code='TIER2' AND action_code='workspace.events.read');

INSERT INTO ap_policy_grant(policy_version_id,tier_code,action_code,sort_order) SELECT 1,'TIER2','workspace.draft.list',7 WHERE NOT EXISTS(SELECT 1 FROM ap_policy_grant WHERE policy_version_id=1 AND tier_code='TIER2' AND action_code='workspace.draft.list');

INSERT INTO ap_policy_grant(policy_version_id,tier_code,action_code,sort_order) SELECT 1,'TIER2','workspace.draft.read',8 WHERE NOT EXISTS(SELECT 1 FROM ap_policy_grant WHERE policy_version_id=1 AND tier_code='TIER2' AND action_code='workspace.draft.read');

INSERT INTO ap_policy_grant(policy_version_id,tier_code,action_code,sort_order) SELECT 1,'TIER2','workspace.draft.create',9 WHERE NOT EXISTS(SELECT 1 FROM ap_policy_grant WHERE policy_version_id=1 AND tier_code='TIER2' AND action_code='workspace.draft.create');

INSERT INTO ap_policy_grant(policy_version_id,tier_code,action_code,sort_order) SELECT 1,'TIER2','workspace.draft.update',10 WHERE NOT EXISTS(SELECT 1 FROM ap_policy_grant WHERE policy_version_id=1 AND tier_code='TIER2' AND action_code='workspace.draft.update');

INSERT INTO ap_policy_grant(policy_version_id,tier_code,action_code,sort_order) SELECT 1,'TIER2','workspace.draft.save',11 WHERE NOT EXISTS(SELECT 1 FROM ap_policy_grant WHERE policy_version_id=1 AND tier_code='TIER2' AND action_code='workspace.draft.save');

INSERT INTO ap_policy_grant(policy_version_id,tier_code,action_code,sort_order) SELECT 1,'TIER2','workspace.draft.archive',12 WHERE NOT EXISTS(SELECT 1 FROM ap_policy_grant WHERE policy_version_id=1 AND tier_code='TIER2' AND action_code='workspace.draft.archive');

INSERT INTO ap_policy_grant(policy_version_id,tier_code,action_code,sort_order) SELECT 1,'TIER2','workspace.draft.restore',13 WHERE NOT EXISTS(SELECT 1 FROM ap_policy_grant WHERE policy_version_id=1 AND tier_code='TIER2' AND action_code='workspace.draft.restore');

INSERT INTO ap_policy_grant(policy_version_id,tier_code,action_code,sort_order) SELECT 1,'TIER2','workspace.asset.list',14 WHERE NOT EXISTS(SELECT 1 FROM ap_policy_grant WHERE policy_version_id=1 AND tier_code='TIER2' AND action_code='workspace.asset.list');

INSERT INTO ap_policy_grant(policy_version_id,tier_code,action_code,sort_order) SELECT 1,'TIER2','workspace.asset.upload',15 WHERE NOT EXISTS(SELECT 1 FROM ap_policy_grant WHERE policy_version_id=1 AND tier_code='TIER2' AND action_code='workspace.asset.upload');

INSERT INTO ap_policy_grant(policy_version_id,tier_code,action_code,sort_order) SELECT 1,'TIER2','workspace.asset.download',16 WHERE NOT EXISTS(SELECT 1 FROM ap_policy_grant WHERE policy_version_id=1 AND tier_code='TIER2' AND action_code='workspace.asset.download');

INSERT INTO ap_policy_grant(policy_version_id,tier_code,action_code,sort_order) SELECT 1,'TIER2','workspace.asset.archive',17 WHERE NOT EXISTS(SELECT 1 FROM ap_policy_grant WHERE policy_version_id=1 AND tier_code='TIER2' AND action_code='workspace.asset.archive');

INSERT INTO ap_policy_grant(policy_version_id,tier_code,action_code,sort_order) SELECT 1,'TIER3','workspace.scope.read',0 WHERE NOT EXISTS(SELECT 1 FROM ap_policy_grant WHERE policy_version_id=1 AND tier_code='TIER3' AND action_code='workspace.scope.read');

INSERT INTO ap_policy_grant(policy_version_id,tier_code,action_code,sort_order) SELECT 1,'TIER3','workspace.scope.create',1 WHERE NOT EXISTS(SELECT 1 FROM ap_policy_grant WHERE policy_version_id=1 AND tier_code='TIER3' AND action_code='workspace.scope.create');

INSERT INTO ap_policy_grant(policy_version_id,tier_code,action_code,sort_order) SELECT 1,'TIER3','workspace.scope.update',2 WHERE NOT EXISTS(SELECT 1 FROM ap_policy_grant WHERE policy_version_id=1 AND tier_code='TIER3' AND action_code='workspace.scope.update');

INSERT INTO ap_policy_grant(policy_version_id,tier_code,action_code,sort_order) SELECT 1,'TIER3','workspace.scope.archive',3 WHERE NOT EXISTS(SELECT 1 FROM ap_policy_grant WHERE policy_version_id=1 AND tier_code='TIER3' AND action_code='workspace.scope.archive');

INSERT INTO ap_policy_grant(policy_version_id,tier_code,action_code,sort_order) SELECT 1,'TIER3','workspace.dashboard.read',4 WHERE NOT EXISTS(SELECT 1 FROM ap_policy_grant WHERE policy_version_id=1 AND tier_code='TIER3' AND action_code='workspace.dashboard.read');

INSERT INTO ap_policy_grant(policy_version_id,tier_code,action_code,sort_order) SELECT 1,'TIER3','workspace.dashboard.manage',5 WHERE NOT EXISTS(SELECT 1 FROM ap_policy_grant WHERE policy_version_id=1 AND tier_code='TIER3' AND action_code='workspace.dashboard.manage');

INSERT INTO ap_policy_grant(policy_version_id,tier_code,action_code,sort_order) SELECT 1,'TIER3','workspace.events.read',6 WHERE NOT EXISTS(SELECT 1 FROM ap_policy_grant WHERE policy_version_id=1 AND tier_code='TIER3' AND action_code='workspace.events.read');

INSERT INTO ap_policy_grant(policy_version_id,tier_code,action_code,sort_order) SELECT 1,'TIER3','workspace.draft.list',7 WHERE NOT EXISTS(SELECT 1 FROM ap_policy_grant WHERE policy_version_id=1 AND tier_code='TIER3' AND action_code='workspace.draft.list');

INSERT INTO ap_policy_grant(policy_version_id,tier_code,action_code,sort_order) SELECT 1,'TIER3','workspace.draft.read',8 WHERE NOT EXISTS(SELECT 1 FROM ap_policy_grant WHERE policy_version_id=1 AND tier_code='TIER3' AND action_code='workspace.draft.read');

INSERT INTO ap_policy_grant(policy_version_id,tier_code,action_code,sort_order) SELECT 1,'TIER3','workspace.draft.create',9 WHERE NOT EXISTS(SELECT 1 FROM ap_policy_grant WHERE policy_version_id=1 AND tier_code='TIER3' AND action_code='workspace.draft.create');

INSERT INTO ap_policy_grant(policy_version_id,tier_code,action_code,sort_order) SELECT 1,'TIER3','workspace.draft.update',10 WHERE NOT EXISTS(SELECT 1 FROM ap_policy_grant WHERE policy_version_id=1 AND tier_code='TIER3' AND action_code='workspace.draft.update');

INSERT INTO ap_policy_grant(policy_version_id,tier_code,action_code,sort_order) SELECT 1,'TIER3','workspace.draft.save',11 WHERE NOT EXISTS(SELECT 1 FROM ap_policy_grant WHERE policy_version_id=1 AND tier_code='TIER3' AND action_code='workspace.draft.save');

INSERT INTO ap_policy_grant(policy_version_id,tier_code,action_code,sort_order) SELECT 1,'TIER3','workspace.draft.archive',12 WHERE NOT EXISTS(SELECT 1 FROM ap_policy_grant WHERE policy_version_id=1 AND tier_code='TIER3' AND action_code='workspace.draft.archive');

INSERT INTO ap_policy_grant(policy_version_id,tier_code,action_code,sort_order) SELECT 1,'TIER3','workspace.draft.restore',13 WHERE NOT EXISTS(SELECT 1 FROM ap_policy_grant WHERE policy_version_id=1 AND tier_code='TIER3' AND action_code='workspace.draft.restore');

INSERT INTO ap_policy_grant(policy_version_id,tier_code,action_code,sort_order) SELECT 1,'TIER3','workspace.asset.list',14 WHERE NOT EXISTS(SELECT 1 FROM ap_policy_grant WHERE policy_version_id=1 AND tier_code='TIER3' AND action_code='workspace.asset.list');

INSERT INTO ap_policy_grant(policy_version_id,tier_code,action_code,sort_order) SELECT 1,'TIER3','workspace.asset.upload',15 WHERE NOT EXISTS(SELECT 1 FROM ap_policy_grant WHERE policy_version_id=1 AND tier_code='TIER3' AND action_code='workspace.asset.upload');

INSERT INTO ap_policy_grant(policy_version_id,tier_code,action_code,sort_order) SELECT 1,'TIER3','workspace.asset.download',16 WHERE NOT EXISTS(SELECT 1 FROM ap_policy_grant WHERE policy_version_id=1 AND tier_code='TIER3' AND action_code='workspace.asset.download');

INSERT INTO ap_policy_grant(policy_version_id,tier_code,action_code,sort_order) SELECT 1,'TIER3','workspace.asset.archive',17 WHERE NOT EXISTS(SELECT 1 FROM ap_policy_grant WHERE policy_version_id=1 AND tier_code='TIER3' AND action_code='workspace.asset.archive');

INSERT INTO ap_permission_action(code,group_code,name,description,binding_key,status,sort_order,created_at,updated_at) SELECT 'workspace.draft.cancel','workspace','초안 변경 취소','P1 서버 행동','workspace.draft.cancel','ACTIVE',18,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP WHERE NOT EXISTS(SELECT 1 FROM ap_permission_action WHERE code='workspace.draft.cancel');

INSERT INTO ap_permission_dependency(action_code,depends_on) SELECT 'workspace.draft.cancel','workspace.draft.update' WHERE NOT EXISTS(SELECT 1 FROM ap_permission_dependency WHERE action_code='workspace.draft.cancel' AND depends_on='workspace.draft.update');

INSERT INTO ap_policy_grant(policy_version_id,tier_code,action_code,sort_order) SELECT 1,'GUEST','workspace.draft.cancel',18 WHERE NOT EXISTS(SELECT 1 FROM ap_policy_grant WHERE policy_version_id=1 AND tier_code='GUEST' AND action_code='workspace.draft.cancel');

INSERT INTO ap_policy_grant(policy_version_id,tier_code,action_code,sort_order) SELECT 1,'TIER1','workspace.draft.cancel',18 WHERE NOT EXISTS(SELECT 1 FROM ap_policy_grant WHERE policy_version_id=1 AND tier_code='TIER1' AND action_code='workspace.draft.cancel');

INSERT INTO ap_policy_grant(policy_version_id,tier_code,action_code,sort_order) SELECT 1,'TIER2','workspace.draft.cancel',18 WHERE NOT EXISTS(SELECT 1 FROM ap_policy_grant WHERE policy_version_id=1 AND tier_code='TIER2' AND action_code='workspace.draft.cancel');

INSERT INTO ap_policy_grant(policy_version_id,tier_code,action_code,sort_order) SELECT 1,'TIER3','workspace.draft.cancel',18 WHERE NOT EXISTS(SELECT 1 FROM ap_policy_grant WHERE policy_version_id=1 AND tier_code='TIER3' AND action_code='workspace.draft.cancel');

INSERT INTO ap_permission_action(code,group_code,name,description,binding_key,status,sort_order,created_at,updated_at) SELECT 'workspace.draft.clone','workspace','업무 초안 복제','P1 서버 행동','workspace.draft.clone','ACTIVE',19,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP WHERE NOT EXISTS(SELECT 1 FROM ap_permission_action WHERE code='workspace.draft.clone');

INSERT INTO ap_permission_dependency(action_code,depends_on) SELECT 'workspace.draft.clone','workspace.draft.read' WHERE NOT EXISTS(SELECT 1 FROM ap_permission_dependency WHERE action_code='workspace.draft.clone' AND depends_on='workspace.draft.read');

INSERT INTO ap_permission_dependency(action_code,depends_on) SELECT 'workspace.draft.clone','workspace.draft.create' WHERE NOT EXISTS(SELECT 1 FROM ap_permission_dependency WHERE action_code='workspace.draft.clone' AND depends_on='workspace.draft.create');

INSERT INTO ap_policy_grant(policy_version_id,tier_code,action_code,sort_order) SELECT 1,'GUEST','workspace.draft.clone',19 WHERE NOT EXISTS(SELECT 1 FROM ap_policy_grant WHERE policy_version_id=1 AND tier_code='GUEST' AND action_code='workspace.draft.clone');

INSERT INTO ap_policy_grant(policy_version_id,tier_code,action_code,sort_order) SELECT 1,'TIER1','workspace.draft.clone',19 WHERE NOT EXISTS(SELECT 1 FROM ap_policy_grant WHERE policy_version_id=1 AND tier_code='TIER1' AND action_code='workspace.draft.clone');

INSERT INTO ap_policy_grant(policy_version_id,tier_code,action_code,sort_order) SELECT 1,'TIER2','workspace.draft.clone',19 WHERE NOT EXISTS(SELECT 1 FROM ap_policy_grant WHERE policy_version_id=1 AND tier_code='TIER2' AND action_code='workspace.draft.clone');

INSERT INTO ap_policy_grant(policy_version_id,tier_code,action_code,sort_order) SELECT 1,'TIER3','workspace.draft.clone',19 WHERE NOT EXISTS(SELECT 1 FROM ap_policy_grant WHERE policy_version_id=1 AND tier_code='TIER3' AND action_code='workspace.draft.clone');

INSERT INTO ap_permission_action(code,group_code,name,description,binding_key,status,sort_order,created_at,updated_at) SELECT 'workspace.asset.restore','workspace','첨부 복원','P1 서버 행동','workspace.asset.restore','ACTIVE',20,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP WHERE NOT EXISTS(SELECT 1 FROM ap_permission_action WHERE code='workspace.asset.restore');

INSERT INTO ap_policy_grant(policy_version_id,tier_code,action_code,sort_order) SELECT 1,'GUEST','workspace.asset.restore',20 WHERE NOT EXISTS(SELECT 1 FROM ap_policy_grant WHERE policy_version_id=1 AND tier_code='GUEST' AND action_code='workspace.asset.restore');

INSERT INTO ap_policy_grant(policy_version_id,tier_code,action_code,sort_order) SELECT 1,'TIER1','workspace.asset.restore',20 WHERE NOT EXISTS(SELECT 1 FROM ap_policy_grant WHERE policy_version_id=1 AND tier_code='TIER1' AND action_code='workspace.asset.restore');

INSERT INTO ap_policy_grant(policy_version_id,tier_code,action_code,sort_order) SELECT 1,'TIER2','workspace.asset.restore',20 WHERE NOT EXISTS(SELECT 1 FROM ap_policy_grant WHERE policy_version_id=1 AND tier_code='TIER2' AND action_code='workspace.asset.restore');

INSERT INTO ap_policy_grant(policy_version_id,tier_code,action_code,sort_order) SELECT 1,'TIER3','workspace.asset.restore',20 WHERE NOT EXISTS(SELECT 1 FROM ap_policy_grant WHERE policy_version_id=1 AND tier_code='TIER3' AND action_code='workspace.asset.restore');

INSERT INTO ap_schema_migration(version,description,applied_at) SELECT 'V002','P1 authentication, workspace and dynamic policy',CURRENT_TIMESTAMP WHERE NOT EXISTS(SELECT 1 FROM ap_schema_migration WHERE version='V002');

CREATE TABLE IF NOT EXISTS ap_ai_run (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    account_id BIGINT NOT NULL,
    workspace_id BIGINT NOT NULL,
    business_scope_id BIGINT NOT NULL,
    draft_id VARCHAR(36) NOT NULL,
    request_key VARCHAR(100) NOT NULL,
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
    apply_key VARCHAR(100) NULL,
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
);

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
);

INSERT INTO ap_feature_group(code,name,status,sort_order)
SELECT 'workspace','공통 업무 기반','ACTIVE',0
WHERE NOT EXISTS(SELECT 1 FROM ap_feature_group WHERE code='workspace');

INSERT INTO ap_permission_action(code,group_code,name,description,binding_key,status,admin_only,risky,sort_order,version,created_at,updated_at)
SELECT 'workspace.ai.run','workspace','AI 제안 만들기','현재 업무와 선택 자료로 AI 작업을 시작합니다. 사진·텍스트 자료는 별도 다운로드 권한도 필요합니다.','workspace.ai.run','ACTIVE',FALSE,TRUE,21,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP
WHERE NOT EXISTS(SELECT 1 FROM ap_permission_action WHERE code='workspace.ai.run');

INSERT INTO ap_permission_action(code,group_code,name,description,binding_key,status,admin_only,risky,sort_order,version,created_at,updated_at)
SELECT 'workspace.ai.read','workspace','AI 제안 조회','본인 사업과 업무에 속한 AI 진행 상태·제안만 조회합니다.','workspace.ai.read','ACTIVE',FALSE,FALSE,22,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP
WHERE NOT EXISTS(SELECT 1 FROM ap_permission_action WHERE code='workspace.ai.read');

INSERT INTO ap_permission_action(code,group_code,name,description,binding_key,status,admin_only,risky,sort_order,version,created_at,updated_at)
SELECT 'workspace.ai.apply','workspace','AI 제안 선택 반영','선택한 제안을 현재 초안에 반영합니다. 실제 표·셀 변경 권한을 함께 검사합니다.','workspace.ai.apply','ACTIVE',FALSE,TRUE,23,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP
WHERE NOT EXISTS(SELECT 1 FROM ap_permission_action WHERE code='workspace.ai.apply');

INSERT INTO ap_permission_action(code,group_code,name,description,binding_key,status,admin_only,risky,sort_order,version,created_at,updated_at)
SELECT 'workspace.ai.cancel','workspace','AI 작업·제안 취소','실행 중 AI 작업을 취소하거나 미반영 제안을 취소합니다. 반영된 자료를 되돌리는 권한이 아닙니다.','workspace.ai.cancel','ACTIVE',FALSE,FALSE,24,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP
WHERE NOT EXISTS(SELECT 1 FROM ap_permission_action WHERE code='workspace.ai.cancel');

INSERT INTO ap_permission_action(code,group_code,name,description,binding_key,status,admin_only,risky,sort_order,version,created_at,updated_at)
SELECT 'workspace.sheet.read','workspace','엑셀 표 조회','EXCEL 업무의 저장된 열·행·셀을 조회합니다.','workspace.sheet.read','ACTIVE',FALSE,FALSE,25,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP
WHERE NOT EXISTS(SELECT 1 FROM ap_permission_action WHERE code='workspace.sheet.read');

INSERT INTO ap_permission_action(code,group_code,name,description,binding_key,status,admin_only,risky,sort_order,version,created_at,updated_at)
SELECT 'workspace.sheet.update','workspace','엑셀 표 변경 적용','표 변경의 저장 진입 권한입니다. 열·행·셀 각각의 실제 변경 권한이 추가로 필요합니다.','workspace.sheet.update','ACTIVE',FALSE,TRUE,26,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP
WHERE NOT EXISTS(SELECT 1 FROM ap_permission_action WHERE code='workspace.sheet.update');

INSERT INTO ap_permission_action(code,group_code,name,description,binding_key,status,admin_only,risky,sort_order,version,created_at,updated_at)
SELECT 'workspace.sheet.export','workspace','엑셀 데이터 다운로드','현재 서버 확정본의 열과 데이터를 XLSX로 다운로드합니다.','workspace.sheet.export','ACTIVE',FALSE,FALSE,27,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP
WHERE NOT EXISTS(SELECT 1 FROM ap_permission_action WHERE code='workspace.sheet.export');

INSERT INTO ap_permission_action(code,group_code,name,description,binding_key,status,admin_only,risky,sort_order,version,created_at,updated_at)
SELECT 'workspace.sheet.schema.update','workspace','엑셀 열 구조 수정','열 추가·삭제·이름·형식·단위·폭·순서를 수정합니다.','workspace.sheet.schema.update','ACTIVE',FALSE,TRUE,28,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP
WHERE NOT EXISTS(SELECT 1 FROM ap_permission_action WHERE code='workspace.sheet.schema.update');

INSERT INTO ap_permission_action(code,group_code,name,description,binding_key,status,admin_only,risky,sort_order,version,created_at,updated_at)
SELECT 'workspace.sheet.rows.create','workspace','엑셀 행 추가','기존 표에 데이터 행을 추가합니다.','workspace.sheet.rows.create','ACTIVE',FALSE,TRUE,29,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP
WHERE NOT EXISTS(SELECT 1 FROM ap_permission_action WHERE code='workspace.sheet.rows.create');

INSERT INTO ap_permission_action(code,group_code,name,description,binding_key,status,admin_only,risky,sort_order,version,created_at,updated_at)
SELECT 'workspace.sheet.rows.delete','workspace','엑셀 행 삭제','기존 표의 데이터 행을 삭제합니다.','workspace.sheet.rows.delete','ACTIVE',FALSE,TRUE,30,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP
WHERE NOT EXISTS(SELECT 1 FROM ap_permission_action WHERE code='workspace.sheet.rows.delete');

INSERT INTO ap_permission_action(code,group_code,name,description,binding_key,status,admin_only,risky,sort_order,version,created_at,updated_at)
SELECT 'workspace.sheet.cells.update','workspace','엑셀 셀 수정','기존 행의 셀 값을 수정합니다.','workspace.sheet.cells.update','ACTIVE',FALSE,TRUE,31,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP
WHERE NOT EXISTS(SELECT 1 FROM ap_permission_action WHERE code='workspace.sheet.cells.update');

INSERT INTO ap_permission_action(code,group_code,name,description,binding_key,status,admin_only,risky,sort_order,version,created_at,updated_at)
SELECT 'workspace.sheet.export.template','workspace','엑셀 빈 양식 다운로드','현재 서버 확정본의 열만 포함한 빈 XLSX 입력 양식을 다운로드합니다.','workspace.sheet.export.template','ACTIVE',FALSE,FALSE,32,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP
WHERE NOT EXISTS(SELECT 1 FROM ap_permission_action WHERE code='workspace.sheet.export.template');

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

INSERT INTO ap_schema_migration(version,description,applied_at) SELECT 'V003','H2 test-only P3 schema',CURRENT_TIMESTAMP WHERE NOT EXISTS(SELECT 1 FROM ap_schema_migration WHERE version='V003');

CREATE TABLE IF NOT EXISTS ap_sheet_view (
 id VARCHAR(36) NOT NULL PRIMARY KEY,
 draft_id VARCHAR(36) NOT NULL,
 name VARCHAR(100) NOT NULL,
 config_json TEXT NOT NULL,
 version BIGINT NOT NULL DEFAULT 0,
 created_at DATETIME(6) NOT NULL,
 updated_at DATETIME(6) NOT NULL
 
);
INSERT INTO ap_permission_action(code,group_code,name,description,binding_key,status,admin_only,risky,sort_order,version,created_at,updated_at)
SELECT 'workspace.sheet.import','workspace','엑셀 파일 미리보기','XLSX·CSV를 저장 전 미리보고 열을 연결합니다.','workspace.sheet.import','ACTIVE',FALSE,FALSE,40,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP
WHERE NOT EXISTS(SELECT 1 FROM ap_permission_action WHERE code='workspace.sheet.import');
INSERT INTO ap_permission_dependency(action_code,depends_on)
SELECT 'workspace.sheet.import','workspace.sheet.update' WHERE NOT EXISTS(SELECT 1 FROM ap_permission_dependency WHERE action_code='workspace.sheet.import' AND depends_on='workspace.sheet.update');
INSERT INTO ap_permission_action(code,group_code,name,description,binding_key,status,admin_only,risky,sort_order,version,created_at,updated_at)
SELECT 'workspace.sheet.formula.update','workspace','엑셀 계산 열 설정','허용된 사칙연산 계산 열을 추가·수정·제거합니다.','workspace.sheet.formula.update','ACTIVE',FALSE,FALSE,41,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP
WHERE NOT EXISTS(SELECT 1 FROM ap_permission_action WHERE code='workspace.sheet.formula.update');
INSERT INTO ap_permission_dependency(action_code,depends_on)
SELECT 'workspace.sheet.formula.update','workspace.sheet.schema.update' WHERE NOT EXISTS(SELECT 1 FROM ap_permission_dependency WHERE action_code='workspace.sheet.formula.update' AND depends_on='workspace.sheet.schema.update');
INSERT INTO ap_permission_action(code,group_code,name,description,binding_key,status,admin_only,risky,sort_order,version,created_at,updated_at)
SELECT 'workspace.sheet.analysis.read','workspace','엑셀 분석 조회','서버 저장본의 집계와 저장한 분석 보기를 조회합니다.','workspace.sheet.analysis.read','ACTIVE',FALSE,FALSE,42,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP
WHERE NOT EXISTS(SELECT 1 FROM ap_permission_action WHERE code='workspace.sheet.analysis.read');
INSERT INTO ap_permission_dependency(action_code,depends_on)
SELECT 'workspace.sheet.analysis.read','workspace.sheet.read' WHERE NOT EXISTS(SELECT 1 FROM ap_permission_dependency WHERE action_code='workspace.sheet.analysis.read' AND depends_on='workspace.sheet.read');
INSERT INTO ap_permission_action(code,group_code,name,description,binding_key,status,admin_only,risky,sort_order,version,created_at,updated_at)
SELECT 'workspace.sheet.analysis.manage','workspace','엑셀 분석 보기 관리','분석 조건을 저장·수정·삭제합니다. 원본 표는 변경하지 않습니다.','workspace.sheet.analysis.manage','ACTIVE',FALSE,FALSE,43,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP
WHERE NOT EXISTS(SELECT 1 FROM ap_permission_action WHERE code='workspace.sheet.analysis.manage');
INSERT INTO ap_permission_dependency(action_code,depends_on)
SELECT 'workspace.sheet.analysis.manage','workspace.sheet.analysis.read' WHERE NOT EXISTS(SELECT 1 FROM ap_permission_dependency WHERE action_code='workspace.sheet.analysis.manage' AND depends_on='workspace.sheet.analysis.read');
INSERT INTO ap_permission_dependency(action_code,depends_on)
SELECT 'workspace.sheet.formula.update','workspace.sheet.cells.update'
WHERE NOT EXISTS(SELECT 1 FROM ap_permission_dependency WHERE action_code='workspace.sheet.formula.update' AND depends_on='workspace.sheet.cells.update');

INSERT INTO ap_schema_migration(version,description,applied_at)
SELECT 'V004','Guided workspace and saved sheet analysis',CURRENT_TIMESTAMP
WHERE NOT EXISTS(SELECT 1 FROM ap_schema_migration WHERE version='V004')
 AND EXISTS(SELECT 1 FROM ap_schema_migration WHERE version='V003')
 AND (SELECT COUNT(*) FROM ap_permission_action WHERE code IN ('workspace.sheet.import','workspace.sheet.formula.update','workspace.sheet.analysis.read','workspace.sheet.analysis.manage'))=4;

-- AxPlatform V005. Stop the app; back up DB and both storage roots first.
-- Run after V004 in HeidiSQL. No existing file is moved or deleted by this SQL.
ALTER TABLE ap_asset ALTER COLUMN disk_key VARCHAR(600) NOT NULL;
ALTER TABLE ap_asset ADD COLUMN IF NOT EXISTS owner_account_id BIGINT NULL;
ALTER TABLE ap_asset ADD COLUMN IF NOT EXISTS scope_kind VARCHAR(16) NOT NULL DEFAULT 'COMMON';
ALTER TABLE ap_asset ADD COLUMN IF NOT EXISTS workflow VARCHAR(16) NULL;
ALTER TABLE ap_asset ADD COLUMN IF NOT EXISTS project_id VARCHAR(36) NULL;
ALTER TABLE ap_asset ADD COLUMN IF NOT EXISTS purpose VARCHAR(16) NOT NULL DEFAULT 'SOURCE';
ALTER TABLE ap_asset ADD COLUMN IF NOT EXISTS root_kind VARCHAR(16) NOT NULL DEFAULT 'LEGACY';
ALTER TABLE ap_asset ADD COLUMN IF NOT EXISTS storage_state VARCHAR(16) NOT NULL DEFAULT 'READY';
ALTER TABLE ap_asset ADD COLUMN IF NOT EXISTS purged_at DATETIME(6) NULL;
UPDATE ap_asset a SET owner_account_id=(SELECT w.owner_account_id FROM ap_workspace w WHERE w.id=a.workspace_id) WHERE owner_account_id IS NULL;
ALTER TABLE ap_asset ALTER COLUMN owner_account_id BIGINT NOT NULL;
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

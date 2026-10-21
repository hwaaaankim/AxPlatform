-- HeidiSQL: run after 01 using the local DBA account. Do not use axplatform_app for DDL.
-- This is P0 bootstrap schema only. Future stages ship new versioned SQL.
-- CREATE IF NOT EXISTS is for reruns, not for migrating an unrelated/older schema.
-- No DROP, TRUNCATE, default password SQL, or automatic schema update.
USE `AxPlatform`;
SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS ap_schema_migration (
    version VARCHAR(32) NOT NULL PRIMARY KEY,
    description VARCHAR(200) NOT NULL,
    applied_at DATETIME(6) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS ap_admin_credential (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    account_id BIGINT NOT NULL,
    username VARCHAR(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS ap_bootstrap (
    name VARCHAR(100) NOT NULL PRIMARY KEY,
    completed_at DATETIME(6) NULL,
    account_id BIGINT NULL
    -- Keep the marker even if a DBA deletes an account: no cascading FK here.
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS ap_email_credential (
    account_id BIGINT NOT NULL PRIMARY KEY,
    login_email VARCHAR(254) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    email_verified_at DATETIME(6) NOT NULL,
    security_version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_email_login UNIQUE (login_email),
    CONSTRAINT fk_email_account FOREIGN KEY (account_id) REFERENCES ap_account(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS ap_external_identity (
    account_id BIGINT NOT NULL PRIMARY KEY,
    provider VARCHAR(16) NOT NULL,
    provider_subject VARCHAR(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
    CONSTRAINT uq_provider_subject UNIQUE (provider,provider_subject),
    CONSTRAINT ck_external_provider CHECK (provider IN ('GOOGLE','KAKAO','NAVER')),
    CONSTRAINT fk_external_account FOREIGN KEY (account_id) REFERENCES ap_account(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS ap_workspace_member (
    workspace_id BIGINT NOT NULL,
    account_id BIGINT NOT NULL,
    member_role VARCHAR(32) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (workspace_id,account_id),
    CONSTRAINT fk_member_workspace FOREIGN KEY (workspace_id) REFERENCES ap_workspace(id),
    CONSTRAINT fk_member_account FOREIGN KEY (account_id) REFERENCES ap_account(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS ap_access_tier (
    code VARCHAR(16) NOT NULL PRIMARY KEY,
    name VARCHAR(32) NOT NULL,
    plan_code VARCHAR(16) NOT NULL UNIQUE,
    sort_order INT NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS ap_feature_group (
    code VARCHAR(32) NOT NULL PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    status VARCHAR(32) NOT NULL,
    sort_order INT NOT NULL,
    revision BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_feature_status CHECK (status IN ('PREPARING','ACTIVE','DISABLED','ARCHIVED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS ap_admin_audit (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    actor_account_id BIGINT NULL,
    action VARCHAR(64) NOT NULL,
    detail VARCHAR(500) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    INDEX ix_admin_audit_actor_time (actor_account_id,created_at,id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Preserve initialization and administrator configuration on reruns.
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

INSERT INTO ap_schema_migration(version,description,applied_at) SELECT 'V001','P0 bootstrap foundation',UTC_TIMESTAMP(6)
WHERE NOT EXISTS (SELECT 1 FROM ap_schema_migration WHERE version='V001');

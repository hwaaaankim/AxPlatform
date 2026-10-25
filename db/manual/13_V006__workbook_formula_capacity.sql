-- AxPlatform 0.8.0 / 기존 DB의 표 수식·원본 형식·임시 처리 이력 보존 용량 확장
-- 서비스 중 쓰기를 잠시 중단하고 DB 백업 후, 기존 AxPlatform DB에서 1회 실행합니다.
-- 데이터 삭제/초기화/저장 경로 변경은 없습니다. DDL은 자동 커밋됩니다.
ALTER TABLE ap_sheet
  MODIFY COLUMN content_json LONGTEXT NOT NULL,
  MODIFY COLUMN previous_json LONGTEXT NULL;

INSERT INTO ap_schema_migration(version,description,applied_at)
SELECT 'V006','Workbook formula capacity',UTC_TIMESTAMP(6)
WHERE NOT EXISTS (SELECT 1 FROM ap_schema_migration WHERE version='V006');

-- 최대 32MB 표를 저장하므로 서버 패킷은 64MB 이상을 권장합니다.
-- 아래 조회가 67108864 미만이면 DB 관리자 계정으로 다음 명령을 별도 실행한 뒤 앱 재시작:
-- SET GLOBAL max_allowed_packet=67108864;
-- DB 서버 재시작 후에도 유지하려면 서버 설정 [mysqld]에 max_allowed_packet=64M 설정.
SELECT @@global.max_allowed_packet AS global_packet_bytes,@@session.max_allowed_packet AS session_packet_bytes;
SELECT COLUMN_NAME,DATA_TYPE FROM INFORMATION_SCHEMA.COLUMNS
WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='ap_sheet' AND COLUMN_NAME IN ('content_json','previous_json');
SELECT version,applied_at FROM ap_schema_migration WHERE version='V006';

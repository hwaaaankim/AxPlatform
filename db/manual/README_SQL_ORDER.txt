현재 v0.8.0 SQL 안내 — 아래 과거 릴리스 안내보다 이 절을 우선합니다.
v0.7.0/V005 적용 DB: 앱 중지·백업 → 13_V006__workbook_formula_capacity.sql → 패치 적용 → 앱 재시작.
신규 DB: 01(필요시) → 02(V001) → 04(V002) → 06(V003) → 08(V004) → 10(V005) → 11 → 13(V006) → 최초 실행.
13번은 표의 현재/이전 JSON 저장 용량 확장입니다. 기존 표와 계정을 삭제하지 않습니다.
13번 끝에서 LONGTEXT, V006 기록, max_allowed_packet을 확인하세요. 상세: docs/V080_APPLY.txt.
0.6.1부터 사용하는 per-device 저장 정책은 공용 DB + 각 PC의 개별 파일을 허용합니다.
정상적인 집/사무실/Mac 이동에 12번 환경 재연결 SQL을 실행하지 않습니다.

아래 내용은 과거 릴리스 당시 안내입니다.

현재 v0.5.0 SQL 안내 (이 절을 우선합니다)
V004까지 적용한 DB: 앱 중지·백업 → 10_V005__storage_and_operations.sql → 11_VERIFY_V005.sql → 0.5.0 앱 실행.
신규 DB: 01(필요시 DB/계정) → 02(V001) → 04(V002) → 06(V003) → 08(V004) → 10(V005) → 11 → 최초 실행.
10/11은 HeidiSQL에서 대상 DB를 먼저 선택하고 실행합니다. 실제 MariaDB 적용은 사용 환경에서 확인합니다.
dev/test는 서로 다른 DB와 저장 폴더를 사용합니다. 상세 단계: docs/V005_APPLY.txt.
12_STORAGE_REBIND_TEMPLATE.sql은 기존 환경 전체 이전용입니다. 값 확인 없이 실행하거나 정상 업데이트에 사용하지 마세요.

아래는 과거 릴리스 당시 기록입니다.
현재 v0.4.0 추가 SQL: 08_V004__guided_workspace.sql → 09_VERIFY_V004.sql.
V001~V003 적용 환경은 위 두 파일만 실행하세요. DB 삭제·전체 초기화 불필요.

이전 단계 안내:
AxPlatform v0.3.0 수동 SQL 순서
DB 이름 AxPlatform / MariaDB / HeidiSQL 직접 복사·실행.

현재처럼 V001/V002 설치 완료: 앱 중지 → 06_V003__ai_and_sheet.sql → 07_VERIFY_V003.sql → 업데이트 앱 실행.
V003까지 설치 완료: 07 확인만 합니다. 업데이트를 위해 DB를 삭제하거나 기존 버전을 재실행하지 않습니다.
별도의 새 DB: 01(필요 시 DB/계정) → 02(V001) → 04(V002) → 05 → 06(V003) → 07 → 앱 실행 → 03.
V001만 설치한 별도 DB: 앱 중지 → 04(V002) → 05 → 06(V003) → 07 → 앱 실행.

01_CREATE_DATABASE_AND_USER.sql: DB/접속계정 준비. 기존 계정이면 재실행하지 않아도 됩니다.
02_V001__foundation.sql: 초기12테이블. 원본보존.
03_VERIFY_BEFORE_AND_AFTER_START.sql: 초기 관리자/기초 데이터 읽기 전용 확인.
04_V002__p1_foundation.sql: 19테이블/기존 컬럼·카탈로그/정책 기반. CREATE의 정렬 규칙 명시 보완.
05_VERIFY_P1.sql: V001/V002 설치 직후31테이블 등 확인. 이후 V003의33테이블과 혼동하지 않습니다.
06_V003__ai_and_sheet.sql: ap_ai_run/ap_sheet, 신규12행동/15의존. 기존 등급 권한 자동배정 없음.
07_VERIFY_V003.sql: V001/V002/V003·33테이블·34신규컬럼·PK/FK/요청유일키·카탈로그/의존 확인.

DDL은 생성/변경 권한이 있는 DBA 계정으로 실행합니다. 앱 DML 계정(axplatform_app 등)과 구분합니다.
01로 계정을 만들었다면 private의 spring.datasource.username=axplatform_app,
password=01에 직접 입력한 같은 비밀번호, URL=jdbc:mariadb://127.0.0.1:3306/AxPlatform.
CREATE USER IF NOT EXISTS는 기존 계정의 암호를 바꾸지 않습니다.

첫 SQL 오류에서 멈추고 해당 오류를 해결한 다음 진행합니다. MariaDB DDL은 암묵 커밋되어 전체 일괄 롤백되지 않습니다.
V003 마지막 마커는 필수 스키마/등록이 갖춰질 때만 들어갑니다. 마커만 임의로 수동 추가하지 마세요.
기존 개인 설정·회원·정책·암호·자료는 보존합니다. 이번 업데이트 묶음에 DB 삭제 SQL은 없습니다.
V002는 필요 없는 환경에서 재실행하지 않습니다. 완료 기록만으로 각 seed가 전부 생략되는 구조가 아닙니다.
소스의 SQL과 별도 SQL ZIP은 동일한 파일이므로 두 벌을 중복 실행하지 않습니다.
초기 관리자 0명은 첫 앱 실행 전 정상이며, 새 DB 최초 실행 성공 후 admin/12345가 한 번 생성됩니다.
src/test의 H2 스키마는 설치용이 아닙니다. 실제 수행한 엔진/애플리케이션 검사는 docs/TEST_REPORT.txt를 확인하세요.

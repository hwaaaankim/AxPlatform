현재 릴리스 v0.4.0 안내
먼저 README_UPDATE_v0.4.0.txt를 읽으세요. 아래 내용은 기존 설치 안내이며 현재 변경·검증·잔여 범위는 docs/V004_* 문서가 우선합니다.
신규 전체 설치는 V001 → V002 → V003 → V004 순서입니다. 기존 DB 업데이트는 미적용 SQL만 실행합니다.

[이전 v0.2.0 안내 기록] 현재 v0.3.0은 README_UPDATE_v0.3.0.txt와 db/manual/README_SQL_ORDER.txt를 먼저 읽으세요. 현재 설치 DB에서는 V003만 추가 적용합니다.

AxPlatform v0.2.0 전체 누적 설치본 — 2026-10-20 재포장
Java 17 / Eclipse Maven / Spring Boot 4.1.1 / Thymeleaf + JavaScript / 로컬 MariaDB

1. 무엇이 포함되어 있나요?
AxPlatform_Full_Source_v0.2.0.zip은 초기 버전의 기반 기능과 0.1.1 설정 수정,
0.2.0 계정·권한·공통 업무 준비 기능을 반영한 전체 소스입니다.
이전 소스 ZIP을 차례로 덮어쓸 필요 없이 이 소스 하나를 사용합니다.
버전별 과거 소스를 여러 벌 넣은 것이 아니라 0.2.0 상태로 합쳐진 프로젝트 한 벌입니다.
이번 재포장은 설치 안내를 보강한 것으로, 이전 0.2.0의 Java/HTML/JS/CSS/설정/SQL은 변경하지 않았습니다.

소스 ZIP: AxPlatform/ 아래 pom.xml, src/, config/, db/manual/, docs/, 안내서.
SQL ZIP: AxPlatform_SQL/ 아래 DB·접속계정 준비, V001, V002, 확인 SQL, 실행 안내.
소스 ZIP의 db/manual에도 같은 전체 SQL 5개가 들어 있습니다. 둘 중 한쪽에서만 실행하세요.
실제 개인 설정·기존 DB 데이터·업로드 파일·빌드 JAR·실행 스크립트는 포함하지 않습니다.

2. SQL 파일별 역할과 실행 순서
SQL은 자동 실행되지 않습니다. HeidiSQL 쿼리 창에 전체 내용을 복사해 직접 실행합니다.
테이블 생성/변경 SQL은 DBA(root 또는 해당 DDL 권한이 있는 계정)로 실행합니다.
SQL마다 오류가 없는지 확인한 뒤 다음 파일로 진행합니다.

01_CREATE_DATABASE_AND_USER.sql
  AxPlatform DB와 axplatform_app 접속계정을 준비합니다. 이미 모두 준비됐다면 생략합니다.
  DB가 이미 있으면 CREATE DATABASE IF NOT EXISTS가 다시 생성하거나 지우지 않습니다.
  CHANGE_ME_DB_PASSWORD 2곳을 같은 개인 비밀번호로 변경한 뒤 실행합니다.
  앱 계정에는 AxPlatform DB의 SELECT/INSERT/UPDATE/DELETE 권한만 줍니다.
  기존 접속계정의 암호는 이 SQL을 다시 실행해도 바뀌지 않습니다.
02_V001__foundation.sql
  초기 공통 테이블 12개와 기본 등급·기능 그룹을 만듭니다. 신규 DB에 실행합니다.
03_VERIFY_BEFORE_AND_AFTER_START.sql
  읽기 전용 확인 SQL입니다. 앱 첫 실행 전·후 관리자와 초기화 기록 등을 확인합니다.
04_V002__p1_foundation.sql
  V001 이후 새 테이블 19개, 기존 컬럼 및 0.2.0 공통 기능·권한 정책을 추가합니다.
05_VERIFY_P1.sql
  읽기 전용 확인 SQL입니다. V001/V002 기록, 테이블·정책·초안·첨부 구성을 확인합니다.

새 빈 DB 설치:
  01(필요할 때만) → 02(V001) → 04(V002) → 05(확인)
  → 아래 설정·Eclipse 실행 → 03(관리자 확인), 필요하면 05도 다시 조회.
  03은 읽기 전용이므로 테이블 설치 후 앱 실행 전에도 확인할 수 있습니다.
  V001과 V002를 모두 적용하기 전에는 0.2.0 앱을 시작하지 않습니다.

기존 DB에 V001만 적용된 상태:
  앱 중지 → 프로젝트/DB 백업 → 04(V002) → 05 → 소스 덮어쓰기 → 앱 실행 → 03.
  DB나 접속계정을 새로 만들 필요가 없으며 기존 관리자 암호도 유지합니다.

기존 DB에 V001과 V002가 이미 적용된 상태:
  설치 SQL을 재실행할 필요가 없습니다. 05로 상태를 확인하고 소스만 적용합니다.
  적용 버전 확인: SELECT version, description FROM AxPlatform.ap_schema_migration ORDER BY version;
  V001/V002가 모두 있는지와 확인 SQL 결과를 함께 봅니다.

MariaDB DDL은 일반 트랜잭션처럼 SQL 전체가 일괄 롤백되지 않습니다.
오류가 발생하면 오류와 이미 적용된 상태를 확인한 뒤 해결합니다.
V002는 완료 기록을 보고 SQL 전체를 자동으로 건너뛰는 방식이 아닙니다.
재실행 시 빠진 기본 목록·의존 조건·초기 정책 배정이 재등록될 수 있으므로 필요한 미적용 버전만 실행하세요.
새 설치 직후 ap_ 테이블은 총 31개, V001/V002 기록은 각각 1개가 기준입니다.
관리자는 SQL 실행만으로 생기지 않으므로 앱 첫 실행 전 0명, 성공적인 최초 실행 후 1명이 정상입니다.
실제로 MariaDB에서 이 SQL을 실행한 검수는 이번 제공 환경에서 수행하지 못했습니다.

3. 개인 설정 파일 — 정확히 여기에 입력하세요
프로젝트 루트는 pom.xml과 src 폴더가 보이는 위치입니다.
예: D:\workspace\AxPlatform
예제: D:\workspace\AxPlatform\config\application-private.example.yml
실제 사용 파일: D:\workspace\AxPlatform\config\application-private.yml
src/main/resources 아래가 아니라 프로젝트 루트의 config 폴더입니다.

처음 설치하면 예제를 같은 폴더에 복사하고 파일명을 application-private.yml로 바꿉니다.
이미 개인 파일이 있으면 그 파일을 유지하고 필요한 추가 항목만 예제에서 옮깁니다.
파일은 UTF-8, 공백 들여쓰기로 저장하며 확장자가 .yml.txt가 되지 않게 확인합니다.
다음 항목을 본인 값으로 입력합니다.

spring:
  datasource:
    url: 'jdbc:mariadb://127.0.0.1:3306/AxPlatform'
    username: 'axplatform_app'
    password: '01_SQL에_입력한_같은_비밀번호'
    driver-class-name: org.mariadb.jdbc.Driver

01을 생략하고 기존 DB 접속계정을 사용한다면 username/password에 그 계정 값을 입력합니다.
MariaDB 포트가 다르면 URL의 3306만 실제 포트로 바꿉니다. DB 이름은 AxPlatform입니다.
작은따옴표를 포함한 비밀번호는 YAML과 SQL에서 두 번 씁니다. 실제 ab'cd → 'ab''cd'.
관리자 웹 로그인 암호와 DB 접속 암호는 별개입니다.
실제 application-private.yml 한 파일은 Git과 JAR에서 제외되며, 다른 PC에도 같은 위치에 복사합니다.
다른 PC의 로컬 MariaDB·데이터·첨부 원본까지 이 설정 파일만으로 이동되지는 않습니다.
데이터를 이어 쓰려면 DB 백업/복원과 data/private 폴더 복사도 필요합니다.

4. Eclipse에서 처음 시작하는 경우
소스 ZIP을 풀고 AxPlatform/pom.xml이 보이는지 확인합니다.
File → Import → Maven → Existing Maven Projects → 해당 AxPlatform 폴더 선택 → Finish.
프로젝트 JDK는 Java 17, 텍스트 인코딩은 UTF-8로 맞춥니다.
위 SQL 설치와 실제 개인 설정 파일 입력을 마칩니다.
프로젝트 우클릭 → Maven → Update Project → Force Update 체크 → OK.
Project → Clean을 실행합니다.
com.axplatform.AxPlatformApplication을 Run As → Java Application으로 실행합니다.
Run Configurations → Arguments → Working directory는 pom.xml이 있는 프로젝트 루트로 지정합니다.
예: D:\workspace\AxPlatform

이미 import한 프로젝트라면 새 import 대신 다음 순서로 적용합니다.
앱 중지 → 기존 프로젝트/DB 백업 → AxPlatform/ 안 파일들을 기존 루트의 같은 경로에 복사·덮어쓰기
→ 필요한 미적용 SQL 실행 → 개인 설정 유지/추가 항목 입력 → F5 → Maven Update → Clean → 실행.
config/application-private.yml과 data/는 사용 중인 것을 유지합니다.
pom.xml의 maven-jar-plugin은 3.4.2로 속성과 플러그인 양쪽에 고정되어 있습니다.
spring.web.error.include-message 등 Spring Boot 4 오류 속성을 사용합니다.

5. 첫 로그인과 외부 서비스 설정
일반 로그인: http://localhost:8080/login
관리자 로그인: http://localhost:8080/admin/login
완전히 새 DB에서 최초 실행하면 admin / 12345 계정을 PasswordEncoder로 한 번 생성합니다.
로그인 후 관리자 비밀번호 변경 화면에서 변경합니다. 기존 DB의 암호는 재설치로 초기화하지 않습니다.
관리자·권한·본인 시험 공간을 먼저 확인할 때는 연동값을 비워도 됩니다.
ax.auth.registration-enabled: false와 모든 ax.integrations 하위 enabled: false를 유지합니다.

일반 회원가입을 연결할 때 실제 개인 파일의 ax.auth 및 SMTP/SOLAPI 칸을 채웁니다.
otp-secret은 충분히 무작위인 개인 비밀값 32자 이상으로 바꾸고 예제 CHANGE_ME 값은 사용하지 않습니다.
메일·문자 발송 준비와 본인 수신을 확인한 뒤 registration-enabled를 true로 바꾸고 재시작합니다.
Google/카카오/네이버 앱 등록·콜백·키 입력 위치는 docs/P1_INTEGRATIONS.txt에 적었습니다.
인증 서비스의 실제 로그인/발송·수신 검수는 별도로 필요합니다.

6. 폴더별 용도
pom.xml: Maven 의존성·Java·패키징 설정.
src/main/java: 실제 서버 코드.
src/main/resources/templates: Thymeleaf 화면.
src/main/resources/static: 로컬 CSS/JavaScript.
src/main/resources/application.yml: 공통 설정. 개인 암호는 넣지 않습니다.
config/application-private.example.yml: 복사해 값만 입력할 개인 설정 양식.
db/manual: HeidiSQL에서 직접 실행하는 MariaDB용 전체 SQL.
docs: 결정 사항·외부 연동·API·기능/검수 상태 안내.
src/test: 테스트 전용 코드와 H2 스키마. 여기의 SQL은 실제 MariaDB 설치용이 아닙니다.

7. 현재 구현 범위와 검증 상태
0.2.0은 계정/인증 어댑터·회원 관리·네 등급 권한/이력·사업별 공통 초안·첨부 코드까지 포함합니다.
실제 엑셀·문서 파일 생성/편집, AI 업무 처리, 결제 등 후속 기능은 포함되지 않습니다.
이전 개발 때 수행한 독립 Java/JS·문법·정적 검사 기록은 docs/TEST_REPORT.txt에 있습니다.
이번 재포장에서는 원본 해시/CRC, Java·화면·설정 원본 보존, SQL 누적 포함/양쪽 일치,
설정 양식, 압축 경로, 개인 실제 파일/실행 스크립트/빌드 산출물 제외를 검사했습니다.
이번에 전체 Maven 빌드·JUnit·실제 MariaDB·Eclipse·브라우저·외부 인증을 실행 검증한 것은 아닙니다.
필요한 로컬 테스트: Eclipse Run As → Maven build → Goals: clean test package.
테스트 실행은 본인 환경에서 수행하며, H2 통과가 실제 MariaDB 실행 검수를 대신하지는 않습니다.

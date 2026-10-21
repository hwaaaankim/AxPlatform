이 문서는 0.1.1 당시 업데이트의 역사 기록입니다. 현재 업데이트는 README_UPDATE_v0.2.0.txt를 사용하세요.

AxPlatform 0.1.1 — 오류 설정 수정 / 로컬 DB AxPlatform / 개인 설정파일

1. 제공 파일과 적용 순서
① AxPlatform_Source_Update_v0.1.1.zip
   실행 중인 앱을 종료합니다. ZIP 안 AxPlatform 폴더의 내용을 현재 Eclipse 프로젝트의
   AxPlatform 루트에 덮어씁니다. 별도의 AxPlatform 폴더가 한 겹 더 생기지 않도록 주의합니다.
   기존 프로젝트를 다시 import할 필요는 없습니다. Eclipse에서 프로젝트 Refresh(F5).
   pom.xml 버전도 바뀌므로 Maven > Update Project(Alt+F5)를 실행합니다.
   개인 실파일 config/application-private.yml은 이 소스 ZIP에 포함되지 않습니다.

② application-private.yml
   다운로드한 파일을 이름 그대로 다음 위치에 넣습니다.
   프로젝트 루트/config/application-private.yml
   Eclipse 프로젝트 이름이 AxPlatform이면 표시 경로: AxPlatform/config/application-private.yml
   기준: AxPlatform/pom.xml과 AxPlatform/config/는 같은 프로젝트 루트에 있습니다.
   이미 본인의 개인 설정파일이 있으면 다운로드본으로 덮기 전에 기존 값을 보존하세요.
   src/main/resources/config 안에 넣지 않습니다.

③ AxPlatform_Manual_SQL_v0.1.1.zip
   대상 DB 이름은 정확히 AxPlatform 입니다. 모든 SQL의 대상 DB도 동일합니다.
   이 SQL과 소스 db/manual SQL은 같은 내용입니다. 양쪽을 중복해서 실행할 필요가 없습니다.

2. 개인 설정파일에 입력할 값
spring:
  datasource:
    url: 'jdbc:mariadb://127.0.0.1:3306/AxPlatform'
    username: 'CHANGE_ME_DB_USERNAME'
    password: 'CHANGE_ME_DB_PASSWORD'
    driver-class-name: org.mariadb.jdbc.Driver

- url: 본인 PC의 MariaDB 기본 포트가 3306이면 그대로 사용합니다.
  다른 포트면 3306만 실제 MariaDB 포트로 바꿉니다. 끝의 DB 이름 AxPlatform은 그대로 유지합니다.
- username: CHANGE_ME_DB_USERNAME을 실제 MariaDB 접속 계정 아이디로 바꿉니다.
  아래 01 계정생성 SQL을 사용하면 아이디는 axplatform_app 입니다.
  이미 만든 계정을 사용하면 그 아이디를 입력합니다.
- password: CHANGE_ME_DB_PASSWORD를 위 DB 계정의 실제 비밀번호로 바꿉니다.
  HeidiSQL에서 사용할 비밀번호와 같아야 합니다. 웹 관리자 계정 admin/12345와는 별개입니다.
- 작은따옴표는 유지하세요. 비밀번호 자체에 작은따옴표가 있으면 YAML 안에서는 두 번 씁니다.
  실제 비밀번호 ab'cd → password: 'ab''cd'
  #, $, !, :, 백슬래시 등은 작은따옴표 안에서 그대로 입력합니다.
- 다운로드 파일에는 나머지 전체 설정 양식이 포함되어 있습니다. 위 세 줄만 따로 교체하는 파일이 아닙니다.
- OpenAI/카카오/네이버/SMTP 등은 지금 입력하지 않아도 됩니다.
  현재는 후속 구현용 예약 항목이므로 빈 값과 enabled: false를 유지합니다.

3. HeidiSQL에서 실행할 SQL
본인 PC의 MariaDB에 DBA/root 또는 필요한 생성 권한이 있는 계정으로 접속합니다.

01_CREATE_DATABASE_AND_USER.sql — DB 및 별도 앱 계정 준비가 필요한 경우
- AxPlatform DB를 CREATE DATABASE IF NOT EXISTS로 준비합니다.
  본인이 이미 AxPlatform DB를 만들었으면 중복 생성하지 않습니다.
- 앱 계정은 axplatform_app 입니다. CHANGE_ME_DB_PASSWORD 두 곳을 동일한 비밀번호로 바꿉니다.
- SQL 전체를 HeidiSQL 쿼리 탭에 복사해서 실행합니다.
- 이 경우 개인 YAML username에는 axplatform_app, password에는 같은 비밀번호를 입력합니다.
- CREATE USER IF NOT EXISTS는 이미 있는 계정의 비밀번호를 변경하지 않습니다.
  계정이 이미 있으면 그 실제 비밀번호를 사용합니다.
- DB와 앱 접속 계정 및 AxPlatform DB에 대한 SELECT/INSERT/UPDATE/DELETE 권한이 이미 준비되었으면
  01은 생략하고 02부터 실행해도 됩니다.

02_V001__foundation.sql — 준비된 AxPlatform DB의 초기 테이블
- 파일 전체를 HeidiSQL에 복사해서 실행합니다. SQL 안 USE `AxPlatform`이 대상 DB를 지정합니다.
- 12개 초기 테이블과 초기화 기준 행을 생성합니다. 오류가 있으면 해결한 후 앱을 실행합니다.
- 이 SQL은 테이블 생성 권한이 있는 DBA로 실행합니다. 앱 전용 계정에는 DDL 권한이 없습니다.
- 이전 안내의 axplatform_local DB를 자동으로 옮기거나 삭제하지 않습니다.
  이번 설정은 사용자가 지정한 AxPlatform DB를 사용합니다.

03_VERIFY_BEFORE_AND_AFTER_START.sql — 확인용 조회
- 앱 실행 전/후 HeidiSQL에서 각각 실행합니다.
- 앱 실행 전 admin_count=0, 첫 앱 실행 후 admin_count=1이 예상값입니다.
- 관리자 생성은 앱 첫 실행에서 PasswordEncoder로 처리합니다. SQL에 기본 비밀번호를 저장하지 않습니다.

4. Eclipse 실행 경로와 로그인
Run Configurations > Arguments > Working directory를 pom.xml이 있는 AxPlatform 루트로 맞춥니다.
이 위치를 기준으로 ./config/application-private.yml을 읽습니다.
AxPlatformApplication.java를 Run As > Java Application 또는 Spring Boot App으로 실행합니다.
개인 YAML의 서버 포트가 기본 8080이면 http://localhost:8080/admin/login 으로 접속합니다.
처음 생성한 AxPlatform DB의 관리자 로그인: admin / 12345.

5. 이번에 수정한 오류 설정
src/main/resources/application.yml에서 다음 항목을 Spring Boot 4.1.1 경로로 옮겼습니다.
server.error.include-message → spring.web.error.include-message
server.error.include-stacktrace → spring.web.error.include-stacktrace
server.error.include-binding-errors → spring.web.error.include-binding-errors
세 값은 모두 never 입니다. server.servlet.session 설정은 기존 위치를 사용합니다.
maven-jar-plugin 3.4.2 고정을 유지합니다. 개인 실파일 제외 규칙도 유지합니다.

6. 확인 결과
공식 Spring Boot 4.1.1 속성 문서에서 새 오류 속성 경로를 확인했습니다.
YAML 문법/중복 키/DB URL/SQL 대상 DB/소스 SQL 일치/ZIP 무결성은 검증했습니다.
Maven 빌드와 실제 MariaDB/Eclipse 실행 검증은 아직 수행하지 못했습니다.
공식 문서: https://docs.spring.io/spring-boot/appendix/application-properties/index.html

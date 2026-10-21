[이전 v0.2.0 안내 기록] 현재 v0.3.0은 README_UPDATE_v0.3.0.txt와 db/manual/README_SQL_ORDER.txt를 먼저 읽으세요. 현재 설치 DB에서는 V003만 추가 적용합니다.

AxPlatform 0.2.0 — 기존 import 프로젝트의 다음 개발 업데이트
대상: Java 17 / Eclipse Maven / 로컬 MariaDB / DB 이름 AxPlatform
기준 소스: 0.1.0 또는 DB 설정 수정본 0.1.1 (V001 실행 완료)

1. 이번 파일
- AxPlatform_P1_Source_v0.2.0.zip: AxPlatform/ 안에 덮어쓸 전체 소스.
- AxPlatform_P1_Manual_SQL_v0.2.0.zip: V002 추가 SQL, 읽기 전용 확인 SQL, 실행 안내.
- application-private.example.yml: 현재 형식의 빈 개인 설정 예제. 기존 개인 파일을 이 예제로 덮어쓰지 마세요.
- AxPlatform_P1_Update_Guide_v0.2.0.txt: 이 안내서.
실행 스크립트(.sh/.bat/.cmd/.ps1), 설치 실행파일, 사용자 비밀값, 빌드 JAR는 제공하지 않습니다.

2. 기존 프로젝트에 적용하는 순서
① Eclipse에서 실행 중인 AxPlatform을 중지합니다.
② 현재 프로젝트 폴더와 DB를 백업합니다. HeidiSQL 내보내기로 SQL 백업을 만들 수 있습니다.
③ SQL ZIP을 풉니다. HeidiSQL에서 테이블 변경 권한이 있는 DBA 계정으로 로컬 MariaDB에 접속합니다.
   01_V002__p1_foundation.sql 전체를 복사해 쿼리 창에 붙여넣고 실행합니다.
   첫 USE 문은 `USE AxPlatform`을 지정합니다. 이미 실행한 V001을 재실행하거나 DB를 새로 만들 필요가 없습니다.
   0.1.1의 axplatform_app 계정은 DML용이므로 DDL이 필요한 이 SQL은 DBA로 실행하세요.
   SQL 오류가 있으면 해결 후 계속합니다. MariaDB의 DDL은 일반 트랜잭션처럼 전체 롤백되지 않습니다.
   V002는 새 테이블 19개와 기존 컬럼을 추가하고 공통 기능 목록을 등록합니다. DROP/TRUNCATE/관리자 암호 SQL이 없습니다.
④ 02_VERIFY_P1.sql 전체를 붙여넣어 실행합니다.
   V001/V002 기록, 기본 정책 상태 1행, 최초 설치 시 전체 ap_ 테이블 31개, 최초 공통 ACTIVE 행동 21개를 확인합니다.
   정책·목록을 수정한 뒤에는 정책 번호와 ACTIVE 수가 달라도 정상입니다. 확인 SQL은 데이터를 수정하지 않습니다.
⑤ 소스 ZIP을 풉니다. AxPlatform/ 안의 파일·폴더를 기존 프로젝트 루트에 같은 경로로 복사·덮어씁니다.
   pom.xml, src/, docs/, db/, config/application-private.example.yml 등이 대상입니다.
   ZIP에 config/application-private.yml은 없습니다. 사용 중인 개인 파일과 data/ 원본 자료는 유지하세요.
   프로젝트를 다시 import할 필요가 없습니다. Eclipse 프로젝트명을 AxPlatform으로 유지합니다.
⑥ Eclipse에서 프로젝트 선택 → Refresh(F5).
   프로젝트 우클릭 → Maven → Update Project → Force Update 체크 → OK.
   Project → Clean으로 이전 컴파일 결과를 정리합니다. JDK 17을 선택하고 워크스페이스 인코딩은 UTF-8로 사용합니다.
   pom.xml의 maven-jar-plugin은 3.4.2로 속성·플러그인 양쪽에 고정했습니다. 부모의 3.5.x 상속을 피합니다.
⑦ Run Configurations → Java Application → AxPlatformApplication.
   Main class: com.axplatform.AxPlatformApplication
   JRE: Java 17 이상 중 프로젝트 기준 Java 17 권장.
   Arguments → Working directory: pom.xml이 있는 프로젝트 루트.
   예: D:\workspace\AxPlatform
⑧ 브라우저에서 http://localhost:8080/admin/login 을 엽니다.
   기존 admin 계정과 본인이 변경한 비밀번호를 사용합니다. 업데이트는 비밀번호를 초기화하지 않습니다.
   최초 생성 후 한 번도 변경하지 않았다면 기존 초기 값 admin / 12345입니다.
   관리자 홈에서 회원·권한·본인 시험 공간을 확인합니다. 고객의 업무 데이터에 자동 접근하지 않습니다.

3. 개인 설정 파일의 정확한 위치
프로젝트 루트는 pom.xml과 src 폴더가 보이는 위치입니다.
실제 파일: 프로젝트 루트/config/application-private.yml
Windows 예: D:\workspace\AxPlatform\config\application-private.yml
예제 파일: 같은 config 폴더의 application-private.example.yml
실제 파일은 Git 및 JAR에서 제외됩니다. .env나 별도 키 파일은 필요하지 않습니다.
DB 접속 설정은 기존 파일의 다음 값을 유지합니다.

spring:
  datasource:
    url: 'jdbc:mariadb://127.0.0.1:3306/AxPlatform'
    username: '본인의_DB_접속_아이디'
    password: '본인의_DB_비밀번호'
    driver-class-name: org.mariadb.jdbc.Driver

기본 MariaDB 포트가 다르면 3306 부분만 실제 포트로 바꾸세요.
작은따옴표를 포함한 값은 YAML에서 두 번 씁니다. 실제 ab'cd → 'ab''cd'.
UTF-8로 저장하고 탭 대신 공백 들여쓰기를 사용합니다.

4. 외부 키 없이 먼저 확인할 수 있는 기능
- 관리자 로그인·비밀번호 변경·기존 계정 보존.
- 회원 검색·가입 방식/상태/유형 필터·상세·연락 정보/상태 수정·탈퇴 상태·변경 이력.
- 마지막 활성 최고관리자 정지/탈퇴 차단. 역할 승격·강등 API는 공개하지 않습니다.
- 네 등급 GUEST / TIER1 / TIER2 / TIER3의 독립 편집·그룹/개별 배정·의존 조건 확인·복사.
- 사유 입력 → 변경 검토 → 정책 적용. 버전 충돌과 동일 요청 재처리를 확인합니다.
- 정책 배정 이력 조회·복원 초안. 복원은 현재 서버 기능/상태/의존 조건을 다시 검사합니다.
- 기능 그룹/항목 추가·편집·순서·준비 상태·삭제 영향. 미연결 기능의 ACTIVE 저장·배정을 차단합니다.
  현재 배정과 충돌하는 중지·의존 변경은 먼저 해당 배정을 해제하여 적용한 뒤 진행하세요.
  정책 이력 복원은 등급 배정 목록 기준입니다. 그룹명·설명 등 목록 자체를 이전 모습으로 복구하는 기능은 아닙니다.
- 본인 시험 공간의 사업 추가/이름 수정/보관/복원, 홈 메뉴 구성.
- 7개 업무의 준비 질문·수동 요약·초안 저장·확정 저장·선택 항목 취소·보관/복원·재사용.
- 같은 사업의 PNG/JPG/UTF-8 TXT 첨부 저장·다운로드·보관/복원·초안 연결 (파일당 10MB).
- 사업 범위의 변경 알림(SSE), 재접속 시 다시 조회, 초안 버전 충돌, 현재 탭의 입력 복구.
가짜 일반 회원이나 실제 발송 없는 인증 완료 계정은 SQL로 만들지 않습니다.

5. 회원가입·찾기·SNS 연결에 추가할 값
기존 개인 파일의 ax: 아래에 auth: 블록을 추가합니다. ax:를 두 번 만들지 마세요.
기존 ax.integrations.smtp / solapi / google-login / kakao-login / naver-login 칸은 값을 채우고 enabled를 바꿉니다.
첨부된 application-private.example.yml 전체 구조를 비교하면 됩니다. DB 아이디/비밀번호는 기존 값을 유지합니다.

ax:
  public-base-url: 'http://localhost:8080'
  storage-root: './data/private'
  auth:
    registration-enabled: false
    otp-secret: 'CHANGE_ME_RANDOM_SECRET_AT_LEAST_32_CHARACTERS'
    terms-version: '2026-10-09-DRAFT'
    privacy-version: '2026-10-09-DRAFT'

otp-secret에는 개인적으로 생성한 32자 이상의 충분히 무작위인 비밀값을 입력합니다.
예제의 CHANGE_ME 문자열은 허용하지 않습니다. 다른 PC에서도 같은 개인 파일을 사용하세요.
관리자 시험만 할 때는 registration-enabled: false와 모든 연동 enabled: false를 그대로 두어도 됩니다.
이메일/문자 실수신을 준비한 뒤 registration-enabled를 true로 바꾸고 앱을 재시작합니다.
상세 가입·발급 절차와 콜백 주소는 소스 docs/P1_INTEGRATIONS.txt에 서비스별로 적었습니다.
현재 약관·개인정보 안내는 로컬 개발 검증용 초안이며 운영용 확정 정책이 아닙니다.

6. 화면 확인 순서
관리자: http://localhost:8080/admin/login → 회원 → 권한 → 내 시험 공간.
일반 회원: http://localhost:8080/login → 회원가입 → 이메일 인증 → 연락처 문자 인증 → 약관 확인 → 업무 홈.
SNS: 가입한 서비스 버튼 → 실제 제공자 인증 → 필요한 이메일/휴대전화 인증 → 가입.
같은 이메일의 EMAIL과 SNS 계정은 각각 별도 계정입니다. 네이버 이메일은 별도 이메일 인증을 진행합니다.
고객 암호는 12~64자, UTF-8 기준 72바이트 이내입니다. 인증번호 붙여넣기·자동완성을 사용할 수 있습니다.
회원가입 완료 후 무료 계정에는 세션당 한 번 안내가 보이며, 7일 숨김은 DB에 저장합니다.
아이디 찾기는 EMAIL 계정만 등록 번호로 문자 안내합니다.
비밀번호 찾기는 EMAIL의 이메일+휴대전화가 일치하면 15분 재설정 링크를 메일로 요청합니다.
링크를 열기만 하면 소비되지 않고 새 비밀번호 적용 시 소비됩니다. 기존 세션은 철회되고 자동 로그인하지 않습니다.
두 브라우저로 같은 초안을 열어 수정·저장해 버전 충돌과 입력 보존을 확인할 수 있습니다.
관리자가 고객 권한을 바꾼 후 기존 로그인 창의 다음 요청에서도 새 정책을 확인합니다.

7. 검증 상태와 다음 개발 범위
이번 전달은 P1 계정·권한·공통 업무 준비 코드를 추가한 개발 버전입니다.
독립 Java 검증 202개와 보안 규칙 회귀 검사 37개, 요청 모듈 모의 검사 9개, 정책·첨부 요청 JS 검사 20개가 통과했습니다.
Java/JS 구문 및 POM/YAML/SQL-코드/리소스/ZIP 일치 검사를 수행했습니다.
Maven·Spring 전체 컴파일·JUnit·실제 Thymeleaf 렌더링·MariaDB 실행·Eclipse m2e·외부 실인증/실수신은 이 환경에서 실행하지 못했습니다.
따라서 실행 검수 통과나 운영 준비 완료로 표시하지 않습니다. docs/TEST_REPORT.txt와 P1_ACCEPTANCE.txt에 구분했습니다.
로컬 자동 테스트: 프로젝트 우클릭 → Run As → Maven build → Goals에 clean test package 입력.
이 명령은 설명용 입력값이며 실행 스크립트는 없습니다. 테스트 DB는 메모리 H2, 테스트 발송기는 src/test의 모의 구현입니다.
실제 MariaDB 호환·외부 키·본인 수신·다중 브라우저 검수는 별도로 확인해야 합니다.
후속 범위는 엑셀 실제 파일/표/수식/가져오기·되돌리기와 AI 업무 처리입니다.
Word/HWP/PPT 실제 편집·내보내기, CRM/일정 연동, 카카오 알림톡·결제·독립 앱 패키징은 후속 단계입니다.
현재 화면의 준비 초안은 최종 문서·엑셀·앱 결과물이 아닙니다.

8. 다른 PC에서 작업
최초 import할 PC에는 같은 Java 17/Eclipse Maven/MariaDB가 필요합니다.
그 PC의 로컬 AxPlatform DB와 접속 계정을 준비하고 신규 DB라면 V001 → V002 순서로 수동 SQL을 실행합니다.
기존 데이터를 계속 쓸 경우 HeidiSQL DB 백업을 복원하고 data/private 원본 첨부 폴더도 함께 옮깁니다.
개인 설정 파일 하나는 같은 config/application-private.yml 위치에 복사합니다.
개인 설정만 옮기면 DB 데이터와 첨부 원본까지 옮겨지는 것은 아닙니다.
기존 PC에서 임의로 table/drop/관리자 재생성 SQL을 실행하지 마세요. 초기 관리자 복구는 docs/ADMIN_RECOVERY.txt를 따릅니다.

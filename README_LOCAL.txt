현재 릴리스 v0.4.0 안내
먼저 README_UPDATE_v0.4.0.txt를 읽으세요. 아래 내용은 기존 설치 안내이며 현재 변경·검증·잔여 범위는 docs/V004_* 문서가 우선합니다.
신규 전체 설치는 V001 → V002 → V003 → V004 순서입니다. 기존 DB 업데이트는 미적용 SQL만 실행합니다.

AxPlatform v0.3.0 / Eclipse 로컬 실행

1. 현재처럼 V001/V002까지 설치한 DB라면 앱을 중지하고 HeidiSQL의 AxPlatform DB에서
   db/manual/06_V003__ai_and_sheet.sql 전체를 실행한 뒤 07_VERIFY_V003.sql로 확인합니다.
   기존 01/02/04나 DB 삭제 SQL을 다시 실행하지 않습니다.
2. 전체 소스를 기존 프로젝트의 같은 경로에 덮어씁니다. config/application-private.yml은 유지합니다.
   Eclipse 프로젝트 Refresh(F5) → Maven > Update Project(필요 시 Force Update) → Project > Clean.
3. 기존 private 파일의 ax.integrations.openai를 확인합니다.
   enabled: true, api-key: '본인 OpenAI API 키', model: gpt-6-astra, reasoning-effort: high.
   같은 ax/openai 키를 두 번 만들지 말고 기존 블록을 수정합니다. 나머지 안전 기본값은 공통 설정에 있습니다.
4. JDK 17, 실행 클래스 com.axplatform.AxPlatformApplication,
   Working Directory는 pom.xml과 config/가 있는 프로젝트 루트로 설정하고 실행합니다.
5. http://localhost:8080/login / 관리자 http://localhost:8080/admin/login.
   기존 관리자 암호 유지. 새 DB 최초 생성 시 admin/12345, 변경은 /admin/password.

개인 파일 위치 예: C:/work/AxPlatform/config/application-private.yml
DB URL: jdbc:mariadb://127.0.0.1:3306/AxPlatform
DB 접속 ID/비밀번호는 spring.datasource.username/password에 입력합니다.
OpenAI 키 미입력·비활성 상태에서는 AI만 연동 대기로 표시하고 직접 표 편집·저장·다운로드는 사용할 수 있습니다.
키를 입력해도 계정의 모델 접근 권한·API 과금·네트워크가 충족되어야 실제 요청이 성공합니다.
신규 고객 권한은 기본 미배정입니다. 관리자의 권한관리에서 신규 행동과 의존을 함께 배정하세요.

기본 EXCEL 작업: 업무 요약 입력/저장 → 표 직접 작성 또는 AI 제안 요청 → 검토/수정 → 선택 반영 → 다운로드.
사진 해석은 먼저 같은 초안에 PNG/JPG/TXT를 첨부·연결하고 사용할 파일을 명시 선택합니다.
업무 요약의 저장/취소와 표의 적용/되돌리기는 서로 다른 범위입니다.

처음 설치하는 별도 새 DB: 01(필요 시 DB/계정) → 02(V001) → 04(V002) → 05 → 06(V003) → 07 → 앱 실행 → 03.
03/05의 최초 건수는 설치/앱 실행 시점에 따라 달라집니다. 현재 0.3.0 총 테이블은 수정 없는 환경에서 33개입니다.
수동 SQL은 DDL 권한이 있는 계정, 앱은 기존 DML 계정을 사용합니다.
상세 업데이트: README_UPDATE_v0.3.0.txt. 외부 인증의 기존 등록법: docs/P1_INTEGRATIONS.txt.
빌드/엔진/브라우저 등 수행한 검수는 docs/TEST_REPORT.txt에 기록하며 실제 OpenAI 실호출은 미검증입니다.

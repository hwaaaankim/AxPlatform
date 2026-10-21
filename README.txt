AxPlatform v0.5.0 전체 누적 소스 — 자료 관리 · 활동 분석 · AI 사용량
Java 17 / Spring Boot 4.1.1 / Maven / Eclipse / MariaDB

먼저 docs/V005_APPLY.txt를 읽으세요.
이 ZIP은 실행 JAR가 아닌 전체 소스입니다. 기존 target 폴더나 이전 JAR를 실행하지 말고 Maven/Eclipse에서 다시 빌드합니다.
현재 기준: v0.4.2 UI 개선 + DB V004 완료 상태. 이번 DB 변경은 V005입니다.

적용: 앱 중지·백업 → 기존 private 설정 보관 → 10_V005 SQL → 11_VERIFY SQL → 전체 소스 반영 → 프로파일 지정·빌드·실행.
신규 DB 설치: 01(필요시 계정/DB) → 02 → 04 → 06 → 08 → 10 → 검증 SQL → 최초 실행.
DB를 삭제하거나 기존 데이터/정책/회원/관리자 암호를 초기화하지 않습니다.
db/manual/12_STORAGE_REBIND_TEMPLATE.sql은 환경/저장 루트 이전 전용이며 일반 업데이트 때 실행하지 않습니다.

로컬 dev: D:/AxPlatform/dev / 서버 test: D:/AxPlatform/test
두 환경은 별도 DB와 자료 폴더를 사용합니다. DB와 저장소 식별값이 다르면 시작을 중지합니다.
기존 config/application-private.yml은 그대로 개인 파일로 복사합니다. ZIP에는 실제 DB 비밀번호/API 키가 없습니다.
공통 설정: src/main/resources/application.yml
환경 설정: application-dev.yml / application-test.yml
빈 개인 설정 양식: config/application-private.example.yml

주요 변경
- 작업 시작과 자료 관리를 분리: 새 파일 / 이전 내 자료 → 작업 연결 → 편집·저장.
- 사업 공통 / 업무별 공통 / 개별 작업 전용 자료와 회원별 실제 저장 경로.
- 내 자료 검색·페이지·추가·개별/선택 휴지통·복원·영구 삭제·사용 용량.
- 관리자 전체 회원 자료·실제 경로·기존 파일 이전·사본 정리·용량/AI 한도.
- 활동·실패·업무별 이용·AI 토큰·추정 비용·단가·미확인 호출 정산·전체 AI 중지.

개발 기준
docs/AxPlatform_실무개발기획안_v1.6.2.docx: 원 기획안 보존
docs/AxPlatform_개발정의서_v1.7_추가개정.docx: 이번 변경과 후속 개발 계약
docs/AxPlatform_개발마스터프롬프트_v1.7.txt: 전체 기존 요구 + 최신 규칙
docs/V005_ARCHITECTURE.txt: 경로·상태·공통 서비스
docs/V005_CHANGED_FILES.txt: 0.4.2 대비 변경 파일
docs/V005_VALIDATION.txt: 실제 검사와 외부 검수 구분

엑셀/데이터 기본 편집·분석을 우선 구현한 단계입니다. 현재 기본 표는 20열/200행 범위입니다.
다른 6개 업무는 공통 자료·초안·안내·AI 제안 기반이며 개별 최종 엔진은 후속 개발 대상입니다.
기존 README_UPDATE_* 및 V004/V042 문서는 당시 버전의 기록이며 이번 적용은 V005 문서를 우선합니다.

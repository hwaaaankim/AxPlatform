0.6.0 다중 시트 검증 (기존 검사에 추가)

mvn clean test → npm install --prefix qa
node qa/v060-regression.mjs
node qa/flow-controls-regression.mjs files-EXCEL

WorkbookRulesTest / AiImageChecksTest / P3SheetIntegrationTest의 다중 시트·사진별 처리 검사를 포함합니다.
PhotoBatchHttpFixture는 HttpClient를 테스트 응답으로 대체합니다. 실제 API 키·유료 생성 요청을 사용하지 않습니다.
전체 페이지 렌더링/스타일 검사는 아래 LayoutRenderCheck와 v051-regression 명령에서 출력 폴더를 target/v060-rendered로 지정하면 됩니다.
이번 검증 방법·결과·경계: docs/V060_VALIDATION.txt

0.5.1 진단·너비 회귀 검사 (기존 검사에 추가)

mvn test로 target/ui-rendered 생성 후 아래 명령을 실행합니다.
  node qa/v051-regression.mjs ai
  node qa/v051-regression.mjs sheet
  node qa/v051-regression.mjs admin

전체 페이지 너비 규칙 검사:
  mvn dependency:copy-dependencies -DincludeScope=test -DoutputDirectory=target/qa-libs
  java -cp "target/qa-libs/*" qa/LayoutRenderCheck.java src/main/resources/templates target/v051-rendered
  node qa/v051-regression.mjs pages target/v051-rendered

연결 확인 API는 위 검사에서 테스트 응답으로 대체합니다. 실제 OpenAI 요청을 보내지 않습니다.
실행한 결과 및 검증 경계: docs/V051_VALIDATION.txt

0.5.0 검증 추가 안내 (프로젝트 루트에서 실행)

일반 개발 환경: mvn test → npm install --prefix qa
기존 DOM/컨트롤/업무별 검사 명령은 아래 목록을 그대로 사용합니다.
자료 선택창: node qa/source-chooser-regression.mjs

신규 30종 렌더링 및 신규 화면 DOM:
  mvn dependency:copy-dependencies -DincludeScope=test -DoutputDirectory=target/qa-libs
  java -cp "target/qa-libs/*" qa/LayoutRenderCheck.java src/main/resources/templates target/v050-rendered
  node qa/v050-regression.mjs files target/v050-rendered
  node qa/v050-regression.mjs admin-files target/v050-rendered
  node qa/v050-regression.mjs ops target/v050-rendered

추가 H2/임시 파일 검사 (mvn test 후):
Windows: java -cp "target/classes;target/qa-libs/*" qa/StorageOperationsCheck.java src/test/resources/db/test-schema.sql
Linux:   java -cp "target/classes:target/qa-libs/*" qa/StorageOperationsCheck.java src/test/resources/db/test-schema.sql
CompileAll.java는 JavaCompiler API로 --release 17 -parameters 컴파일할 때의 보조 도구입니다.
  java qa/CompileAll.java <소스폴더> <출력폴더> <클래스또는JAR폴더> ...
실제 이번 검증 방법/결과/실행하지 않은 외부 검수는 docs/V005_VALIDATION.txt에 기록했습니다.

선택적 개발자 검증 (일반 Eclipse/JAR 실행에는 Node/npm 불필요)

1. 프로젝트 루트에서 mvn test 또는 Eclipse Maven test를 실행합니다.
   V004UiContractTest가 target/ui-rendered에 실제 Thymeleaf 렌더링 테스트 자료를 만듭니다.
2. npm install --prefix qa
3. node qa/dom-regression.mjs draft
   node qa/dom-regression.mjs dashboard
   node qa/dom-regression.mjs login
   node qa/dom-regression.mjs admin

DOM 검증은 테스트 전용 API 응답을 사용하여 이벤트·상태 보존을 확인합니다.
실제 브라우저의 레이아웃/터치/IME 검수나 외부 API 통합검수의 대체가 아닙니다.
API·DB·권한 검증은 Maven의 H2 통합검증에서 별도로 실행합니다.
테스트 의존성은 happy-dom 15.11.7이며 운영 애플리케이션에 포함하지 않습니다.

0.4.2 추가 검증:
  node qa/controls-regression.mjs
  node qa/flow-controls-regression.mjs dashboard
  node qa/flow-controls-regression.mjs files-EXCEL
  node qa/flow-controls-regression.mjs files-WORD
  node qa/flow-controls-regression.mjs files-HWP
  node qa/flow-controls-regression.mjs files-PPT
  node qa/flow-controls-regression.mjs files-CRM
  node qa/flow-controls-regression.mjs files-CALENDAR
  node qa/flow-controls-regression.mjs files-APP
  node qa/flow-controls-regression.mjs member
  node qa/flow-controls-regression.mjs permissions
  node qa/flow-controls-regression.mjs recovery
  node qa/flow-controls-regression.mjs ai

controls-regression은 단독 실행할 수 있습니다.
나머지 DOM 검증은 1번 Maven 테스트로 생성된 target/ui-rendered 자료가 필요합니다.
실제 DB·외부 AI·문자·결제에는 접속하지 않고 테스트 응답으로 브라우저 이벤트를 검증합니다.

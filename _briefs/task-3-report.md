[Codex 결과 보고서] Task 3

1. 수행한 작업 요약

- Stage 2 `SeniorityLevelRule`을 추가해 직무명에서만 시니어리티 마커를 추출하고, `profile.maxSeniority()` 초과 시 `WARN`을 반환하게 했다.
- Stage 3 `SkillRule`을 추가해 공고 절별 스킬을 분석하고 `SkillGap(matched, missingRequired, missingNice)`을 생성하게 했다.
- `AtsChecker.check(...)`를 실제 판정 조립 API로 구현했다. Stage 1 하드 필터 실패 시 Stage 2/3을 건너뛰고 `SKIP`을 반환한다.
- `JobPosting`, `Verdict`, `SkillGap`, `CheckResult` 모델을 추가했다.
- 기존 `AtsCheckerTest`의 echo 스텁 테스트 2개를 실제 판정 테스트로 교체하고, Stage 2/3 테스트를 추가했다.

2. 생성/변경한 파일 목록

- 생성: `core/src/main/java/dev/juhyeonl/atscheck/core/model/JobPosting.java`
- 생성: `core/src/main/java/dev/juhyeonl/atscheck/core/model/Verdict.java`
- 생성: `core/src/main/java/dev/juhyeonl/atscheck/core/model/SkillGap.java`
- 생성: `core/src/main/java/dev/juhyeonl/atscheck/core/model/CheckResult.java`
- 생성: `core/src/main/java/dev/juhyeonl/atscheck/core/rule/SeniorityLevelRule.java`
- 생성: `core/src/main/java/dev/juhyeonl/atscheck/core/rule/SkillRule.java`
- 변경: `core/src/main/java/dev/juhyeonl/atscheck/core/AtsChecker.java`
- 변경: `core/src/test/java/dev/juhyeonl/atscheck/core/AtsCheckerTest.java`
- 생성: `core/src/test/java/dev/juhyeonl/atscheck/core/model/JobPostingTest.java`
- 생성: `core/src/test/java/dev/juhyeonl/atscheck/core/rule/SeniorityLevelRuleTest.java`
- 생성: `core/src/test/java/dev/juhyeonl/atscheck/core/rule/SkillRuleTest.java`
- 생성: `_briefs/task-3-report.md`

참고: 작업 중 `_reviews/2026-08-15.md`가 수정된 상태로 감지됐지만, 수정 금지 경로라 건드리지 않았다. 이 보고서의 작업 목록에는 포함하지 않는다.

3. AtsChecker의 공개 API와 판정 흐름

- 신규 API:
  - `AtsChecker.check(JobPosting posting, Profile profile) -> CheckResult`
  - `AtsChecker.check(String jobText, Profile profile) -> CheckResult`
- `check(String, Profile)`은 `JobPosting.fromText`를 사용한다. 첫 번째 비어 있지 않은 줄을 title로, 전체 원문을 body로 보존한다.
- 실행 흐름:
  - `SectionClassifier.classify(posting.body())`
  - Stage 1 `LanguageRule`, `ExperienceYearsRule`, `DegreeRule`을 항상 모두 실행
  - Stage 1 중 하나라도 `FAIL`이면 `Verdict.SKIP`, `stoppedAtHardFilter=true`, `skillGap=null`로 즉시 반환
  - Stage 1 실패가 없으면 `SeniorityLevelRule`, `SkillRule` 실행
  - Stage 1에 `REVIEW`가 있으면 `Verdict.REVIEW`, 아니면 `Verdict.APPLY`
  - `WARN`은 최종 verdict를 바꾸지 않는다.
- 기존 `AtsChecker.echo(String)`는 `cli/` 수정 금지 조건과 전체 빌드 성공 조건 때문에 호환용으로 남겼다. 새 판정 API는 `check(...)`다.

4. 스킬 매칭에서 c/go/c++/c#/.net/spring boot를 어떻게 처리했는가

- 모든 스킬은 `Pattern.quote` 기반으로 이스케이프했다. 따라서 `c++`, `c#`, `.net`, `node.js`의 `+`, `#`, `.` 문자가 정규식 메타문자로 해석되지 않는다.
- 좌우 경계는 `(?<![\p{L}\p{N}_])`와 `(?![\p{L}\p{N}_])`를 사용했다. 그래서 `c`는 `can`, `clear`에 매칭되지 않고, `go`는 `going`, `algorithm`에 매칭되지 않는다.
- 스킬 후보는 길이 내림차순으로 먼저 검사한다. 이미 선택된 긴 스킬 범위와 겹치는 짧은 스킬 후보는 버린다.
- 그 결과 `Spring Boot`는 `spring boot`로 매칭되고 같은 범위의 `spring`은 누락/매칭 후보에서 제외된다.
- 선택된 후보는 최종적으로 원문 등장 순서대로 `LinkedHashSet`에 넣어 출력 순서를 안정화했다.

5. 테스트 결과 (총 개수, 기존 테스트 유지 확인)

- `./gradlew :core:test` 성공
- `./gradlew build` 성공
- `./gradlew :core:dependencies --configuration runtimeClasspath` 결과: `No dependencies`
- core 테스트 총 89개 성공, 실패/스킵 0개
- 기존 비스텁 테스트 66개 유지 확인:
  - `SectionClassifierTest` 32
  - `ExperienceYearsRuleTest` 17
  - `LanguageRuleTest` 8
  - `DegreeRuleTest` 6
  - `ProfileTest` 2
  - `StageOneRulesIntegrationTest` 1
- 기존 `AtsCheckerTest` 스텁 2개는 실제 조립 테스트 6개로 교체했다.
- 신규 테스트:
  - `SeniorityLevelRuleTest` 6
  - `SkillRuleTest` 9
  - `JobPostingTest` 2

6. 스스로 결정한 것 + 근거

- `SkillGap`의 세 set은 `LinkedHashSet` 순서를 보존한 불변 set으로 복사했다. 테스트 재현성과 외부 변경 방지를 같이 만족시키기 위해서다.
- 사전에 없는 profile 스킬은 profile set을 사전과 합쳐 탐색 대상으로 넣었다. profile set 자체의 순서는 안정적 보장이 약하므로 사전에 없는 profile 스킬은 정렬 후 추가했다.
- `SkillRule.evaluate`의 evidence는 비워뒀다. 스킬 누락은 항상 `PASS`이고 `SkillGap`이 상세 결과를 담기 때문이다.
- `SeniorityLevelRule`의 evidence도 비워뒀다. title 기반 판정이고 현재 `Finding.evidence` 타입은 `Clause` 목록이라 본문 절 근거로 표현하면 오히려 오해가 생긴다.
- `AtsChecker.echo`는 제거하지 않았다. `cli/`가 아직 참조하고 있고 이번 태스크에서 `cli/` 수정이 금지되어 전체 빌드를 깨지 않기 위해서다.

7. 남은 오탐/미탐 리스크

- `go-to-market`처럼 일반 영어에서 `go`가 독립 토큰으로 쓰인 경우는 Go 언어로 오탐할 수 있다. 현재 요구사항의 단어 경계 규칙을 그대로 따른 결과다.
- `RESTful`은 `rest`로 매칭되지 않는다. 단어 경계를 엄격히 적용했기 때문이다.
- `ASP.NET`은 `.net` 앞에 문자가 붙어 있어 매칭되지 않는다. `.NET` 독립 표기를 정확히 잡는 쪽을 우선했다.
- `Spring Boot`가 있으면 같은 범위의 `spring`은 의도적으로 제외된다. profile에 `spring`만 있는 사용자는 `spring boot`를 별도 missing으로 보게 된다.

8. 기존 분류기·규칙에서 고쳐야 한다고 생각하는 것 (보고만)

- critical한 수정 필요 사항은 발견하지 못했다.
- 다만 `ExperienceYearsRule`은 `over 5 years`를 현재 5년 요구로 본다. 기존 테스트가 이 동작을 고정하고 있어 건드리지 않았다.
- Stage 1 예시 문구에서 `3+ years`와 profile 2/tolerance 1이 체크 표시처럼 보이는 부분은 기존 규칙 기준으로는 `WARN`이다. 구현은 기존 `ExperienceYearsRule` 동작을 그대로 따랐다.

9. 다음 추천 작업

- CLI에서 `AtsChecker.check(...)` 결과를 출력 형식으로 렌더링하는 Task 4/CLI 작업을 진행한다.
- 골든 파일에는 `SKIP` 시 Stage 1 Finding 3개만 표시되고 `skillGap`이 없는 케이스를 반드시 넣는다.
- 스킬 매칭 골든에는 `spring boot`, `c++`, `c#`, `.net`, `go`, `c` 경계 케이스를 포함한다.

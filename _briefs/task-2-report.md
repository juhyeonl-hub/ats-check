[Codex 결과 보고서] Task 2

1. 수행한 작업 요약

- Stage 1 하드 필터용 모델(`Profile`, `Finding`, `RuleId`, `Status`, `Degree`, `Seniority`)을 추가했다.
- `LanguageRule`, `ExperienceYearsRule`, `DegreeRule`을 추가하고 모두 `public static Finding evaluate(List<Clause>, Profile)` API로 구현했다.
- `SectionClassifier.classify(String)`가 반환한 `Clause.level()`과 원문 절을 그대로 입력/evidence로 사용했다.
- `core/section/`, `cli/`, 빌드 의존성은 수정하지 않았다.
- YAML 파싱은 추가하지 않았고 `core` 런타임 의존성 0개를 유지했다.

2. 생성한 파일 목록

- `core/src/main/java/dev/juhyeonl/atscheck/core/model/Seniority.java`
- `core/src/main/java/dev/juhyeonl/atscheck/core/model/Degree.java`
- `core/src/main/java/dev/juhyeonl/atscheck/core/model/Profile.java`
- `core/src/main/java/dev/juhyeonl/atscheck/core/model/RuleId.java`
- `core/src/main/java/dev/juhyeonl/atscheck/core/model/Status.java`
- `core/src/main/java/dev/juhyeonl/atscheck/core/model/Finding.java`
- `core/src/main/java/dev/juhyeonl/atscheck/core/rule/LanguageRule.java`
- `core/src/main/java/dev/juhyeonl/atscheck/core/rule/ExperienceYearsRule.java`
- `core/src/main/java/dev/juhyeonl/atscheck/core/rule/DegreeRule.java`
- `core/src/test/java/dev/juhyeonl/atscheck/core/model/ProfileTest.java`
- `core/src/test/java/dev/juhyeonl/atscheck/core/rule/LanguageRuleTest.java`
- `core/src/test/java/dev/juhyeonl/atscheck/core/rule/ExperienceYearsRuleTest.java`
- `core/src/test/java/dev/juhyeonl/atscheck/core/rule/DegreeRuleTest.java`
- `core/src/test/java/dev/juhyeonl/atscheck/core/rule/StageOneRulesIntegrationTest.java`
- `_briefs/task-2-report.md`

3. 각 규칙의 공개 API와 판정 로직 요약

공통 공개 API:

```java
public static Finding evaluate(List<Clause> clauses, Profile profile)
```

- `LanguageRule`
  - Finnish/Swedish 표기 변형만 검사하고 English는 검사하지 않는다.
  - 프로필에 해당 언어가 있으면 `PASS`.
  - 없으면 `Clause.level()`에 따라 `REQUIRED -> FAIL`, `AMBIGUOUS -> REVIEW`, `NEGATED/NICE/UNKNOWN -> PASS`.
  - `NEGATED`는 명시적으로 `PASS` 처리한다.

- `ExperienceYearsRule`
  - `REQUIRED`와 `AMBIGUOUS` 절만 검사한다.
  - 추출된 연차 중 최댓값을 요구 연차로 보고 프로필 연차+tolerance와 비교한다.
  - tolerance 초과 시 `REQUIRED` 근거는 `FAIL`, `AMBIGUOUS` 근거는 `REVIEW`.
  - profile보다 크지만 tolerance 안이면 `WARN`, 그 외는 `PASS`.

- `DegreeRule`
  - 감지된 최고 학위와 `profile.degree()`를 enum 선언 순서로 비교한다.
  - 부족한 학위가 `REQUIRED` 절이면 `FAIL`, `AMBIGUOUS` 절이면 `REVIEW`.
  - `or equivalent`, `or equivalent experience`, `or comparable experience`가 같은 절에 있으면 부족한 학위 요구를 `WARN`으로 낮춘다.
  - `NICE`/`NEGATED`/`UNKNOWN` 절은 `PASS`.

4. 연차 추출 정규식/파싱 전략

- 숫자 범위: `3-5 years`, `3 to 5 years`는 별도 정규식으로 먼저 잡고 첫 숫자를 하한으로 사용한다.
- `5 or more years`, `3+ years`, `at least 3 years`, `minimum of 3 years`, `minimum 3 years`, `over 5 years`, `more than 5 years`를 각각 숫자 그룹으로 잡는다.
- 일반 `3 years`/`3 year`도 잡되, 앞 단어가 `to`/`or`이거나 앞 문자가 `-`인 경우는 범위의 상한으로 보고 버린다.
- 영어 숫자 `one`부터 `ten`까지는 `three years` 같은 단어 표현으로 파싱한다.
- 같은 절에서 여러 패턴이 중복 매칭되어도 최종 판정은 최댓값 기준으로만 수행한다.

5. `master` 오탐 방지를 어떻게 구현했는가

- `MSc`, `M.Sc`, `graduate degree`, `master's`는 학위 표현으로 바로 인정한다.
- 단독 `master`는 같은 절에 `degree`, `education`, `studies`, `university`, `msc`, `m.sc` 중 하나가 있을 때만 학위로 인정한다.
- `master of your craft`, `mastery`, `master branch`는 위 문맥 조건을 만족하지 않으면 학위로 보지 않는다.

6. 테스트 결과 (총 개수, 기존 32개 유지 확인)

실행:

```text
source "$HOME/.sdkman/bin/sdkman-init.sh" && ./gradlew :core:test
source "$HOME/.sdkman/bin/sdkman-init.sh" && ./gradlew build
source "$HOME/.sdkman/bin/sdkman-init.sh" && ./gradlew :core:dependencies --configuration runtimeClasspath
```

결과:

- `./gradlew :core:test`: 성공
- `./gradlew build`: 성공
- `:core:dependencies --configuration runtimeClasspath`: `No dependencies`
- core 테스트 총 68개 성공, 실패/스킵/에러 0개
- 기존 `SectionClassifierTest` 32개 성공 유지
- 기존 `AtsCheckerTest` 2개 성공 유지
- 신규 테스트 34개 성공

테스트 XML 기준:

- `SectionClassifierTest`: 32
- `AtsCheckerTest`: 2
- `ProfileTest`: 2
- `LanguageRuleTest`: 8
- `ExperienceYearsRuleTest`: 17
- `DegreeRuleTest`: 6
- `StageOneRulesIntegrationTest`: 1

7. 사양이 모호해서 스스로 결정한 것 + 근거

- `Profile`의 `yearsExperience`, `yearsTolerance`는 음수를 거부했다. 연차와 tolerance가 음수이면 판정 의미가 깨지므로 모델 경계에서 막는 편이 단순하다.
- `Profile.languages()`는 canonical 이름뿐 아니라 `suomi`, `svenska` 같은 언어 변형이 들어와도 보유 언어로 인정했다. 입력 정규화 계층이 아직 없기 때문에 사용자가 변형 표기를 넣어도 불필요한 FAIL을 내지 않기 위해서다.
- `Finding.evidence()`는 `PASS`일 때 비울 수 있게 했다. 요구사항상 FAIL/REVIEW/WARN evidence 보존이 핵심이고, PASS evidence는 선택 사항으로 해석했다.
- 학위의 equivalent 완화는 `REQUIRED`뿐 아니라 `AMBIGUOUS` 부족 학위에도 적용해 `WARN`으로 낮췄다. "학위가 없어도 경력으로 대체 가능"하다는 의미를 동일하게 반영하기 위해서다.
- 연차 evidence는 최댓값 요구 연차를 만든 절로 제한했다. 사양이 "추출된 값 중 최댓값을 요구 연차로 본다"고 했기 때문이다.

8. 남은 오탐/미탐 리스크

- `finish`를 Finnish 오타로 인정하므로 "finish the project" 같은 일반 동사 문맥에서 오탐 가능성이 있다.
- `3 yrs`, `3 yoe`, `several years`, `five+ years`, `three to five years`는 아직 미탐이다.
- `more than 5 years`는 사양대로 5로 처리했지만, 엄밀한 수학 의미로는 6년 이상일 수 있다.
- equivalent 완화 표현은 명시된 세 패턴만 지원한다. `or relevant experience`, `or equivalent work experience` 등은 미탐이다.
- `master` 문맥 판단은 같은 절 안의 단어 기반이라, 드문 문장에서는 여전히 오탐/미탐이 가능하다.

9. 섹션 분류기에서 고쳐야 한다고 생각하는 것

- 이번 태스크 구현 중 `core/section/` 수정이 필요하다고 판단한 결함은 없었다.
- `NEGATED`가 `LanguageRule`에서 `PASS`로 정상 연결되는 것도 확인했다.

10. 다음 추천 작업

- Task 3에서 Stage 2 seniority rule과 Stage 3 skills rule을 같은 `Finding` 계약으로 추가한다.
- 그 다음 `AtsChecker` 조립 단계에서 Stage 1의 `FAIL`을 즉시 `SKIP`으로 연결하고, `REVIEW`/`WARN`은 원문 evidence와 함께 출력하게 한다.

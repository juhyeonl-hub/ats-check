[Codex 결과 보고서] Task 4.5

1. 각 규칙에서 PASS evidence를 어떤 조건으로 채웠는가

- `LanguageRule`
  - 언어 언급이 전혀 없으면 기존처럼 `PASS / English only / evidence=[]`.
  - 언어 언급을 실제로 평가한 경우에는 최종 status와 같은 assessment의 `Clause`를 evidence에 담는다.
  - 따라서 `NEGATED`, `NICE`, `UNKNOWN`, profile 보유로 인한 PASS 모두 해당 절이 evidence에 들어간다.
  - `summary` 문구는 변경하지 않았다.

- `DegreeRule`
  - 학위 언급이 전혀 없으면 기존처럼 `PASS / not required / evidence=[]`.
  - 감지된 학위가 profile 이하라서 PASS인 경우와 `NICE`, `NEGATED`, `UNKNOWN` 절의 학위 언급으로 PASS인 경우, 최종 PASS assessment의 `Clause`를 evidence에 담는다.
  - `summary` 문구는 기존 PASS summary인 `not required` 그대로 유지했다.

- `ExperienceYearsRule`
  - 연차 언급이 전혀 없으면 기존처럼 `PASS / not specified / evidence=[]`.
  - 요구 연차가 profile 이하라서 PASS인 경우, 최댓값 요구 연차를 만든 절을 evidence에 담는다.
  - 기존 `evidenceFor(...)`를 PASS에도 사용하므로 같은 최댓값 요구 절이 여러 개면 모두 보존된다.
  - `summary` 문구는 변경하지 않았다.

2. 변경한 파일 목록

- `core/src/main/java/dev/juhyeonl/atscheck/core/rule/LanguageRule.java`
- `core/src/main/java/dev/juhyeonl/atscheck/core/rule/DegreeRule.java`
- `core/src/main/java/dev/juhyeonl/atscheck/core/rule/ExperienceYearsRule.java`
- `core/src/test/java/dev/juhyeonl/atscheck/core/GoldenFileTest.java`
- `core/src/test/java/dev/juhyeonl/atscheck/core/rule/LanguageRuleTest.java`
- `core/src/test/java/dev/juhyeonl/atscheck/core/rule/DegreeRuleTest.java`
- `core/src/test/java/dev/juhyeonl/atscheck/core/rule/ExperienceYearsRuleTest.java`
- `core/src/test/resources/golden/03-finnish-negated/expected.json`
- `core/src/test/resources/golden/05-finnish-in-benefits/expected.json`
- `core/src/test/resources/golden/07-finnish-required-but-profile-has-it/expected.json`
- `core/src/test/resources/golden/14-phd-nice-to-have/expected.json`
- `core/src/test/resources/golden/15-master-of-your-craft/expected.json`
- `_briefs/task-4.5-report.md`

3. evidenceContains를 추가한 골든 케이스와 그 값

- `01-finnish-required` / `LANGUAGE`: 기존 값 유지, `"Fluent Finnish is required"`
- `03-finnish-negated` / `LANGUAGE`: `"Finnish is not required"`
- `05-finnish-in-benefits` / `LANGUAGE`: `"Optional Finnish lessons at lunch"`
- `07-finnish-required-but-profile-has-it` / `LANGUAGE`: `"Fluent Finnish is required"`
- `14-phd-nice-to-have` / `DEGREE`: `"PhD in distributed systems is a plus"`
- `15-master-of-your-craft` / `DEGREE`: `evidenceEmpty: true`

4. 일부러 틀리게 만들어 실패를 확인한 출력

검증 방법:

- `03-finnish-negated`의 `LANGUAGE.evidenceContains`를 임시로 `"THIS SHOULD FAIL"`로 변경
- `./gradlew :core:test --tests dev.juhyeonl.atscheck.core.GoldenFileTest` 실행
- 실패 확인 후 값을 `"Finnish is not required"`로 복구

Gradle 출력:

```text
> Task :core:test FAILED

GoldenFileTest > goldenFileCaseMatchesExpectedResult(GoldenCase) > 03-finnish-negated FAILED
    java.lang.AssertionError at GoldenFileTest.java:261

20 tests completed, 1 failed

FAILURE: Build failed with an exception.
* What went wrong:
Execution failed for task ':core:test'.
> There were failing tests.
```

Assertion 메시지:

```text
java.lang.AssertionError: [case 03-finnish-negated evidence for LANGUAGE expected to contain <THIS SHOULD FAIL> actual evidence <The product has Finnish users, but the engineering team works in English.
Finnish is not required for this position.>]
Expecting actual:
  "The product has Finnish users, but the engineering team works in English.
Finnish is not required for this position."
to contain:
  "THIS SHOULD FAIL"
```

5. 테스트 결과

- `./gradlew build`: 성공
- 테스트 결과 XML 합산: `tests=123 failures=0 skipped=0 errors=0`
- 기존 122개 테스트는 통과했고, 복지 섹션 PASS evidence 단위 테스트 1개를 추가했다.
- `./gradlew :core:dependencies --configuration runtimeClasspath`: `No dependencies`
- `core` 런타임 의존성: 0개

6. 스스로 결정한 것 + 근거

- `evidenceEmpty`를 골든 finding별 optional boolean으로 추가했다.
  - `true`면 실제 evidence가 비어 있어야 한다.
  - `false`면 실제 evidence가 비어 있지 않아야 한다.
  - 값이 없으면 기존처럼 해당 finding의 evidence 검증을 건너뛴다.
  - 15번은 degree mention 자체가 없어야 하는 회귀 케이스라 `evidenceContains`보다 `evidenceEmpty: true`가 요구사항에 맞다.

- PASS evidence는 기존 `evidenceFor(...)` 헬퍼를 재사용했다.
  - FAIL/REVIEW/WARN에서 쓰던 “최종 status와 같은 근거 절들을 보존한다”는 정책을 PASS에도 그대로 적용하는 것이 가장 단순하고 일관적이다.
  - 이 때문에 한 케이스에서 같은 최종 PASS status를 만든 언어 언급 절이 여러 개면 evidence에 여러 절이 들어갈 수 있다.

- `LanguageRuleTest`에는 복지 섹션 PASS evidence 테스트를 새로 추가했다.
  - 기존 테스트 파일에 복지 섹션 케이스가 없어서, 지시된 강화 범위를 만족하려면 새 테스트가 필요했다.

7. 다음 추천 작업

- 다음 작업에서 필요하면 PASS finding을 사용자에게 표시하는 출력 계층의 문구/노출 정책을 별도 검토한다. 이번 작업에서는 `cli/` 수정 금지 조건 때문에 core evidence만 채웠다.

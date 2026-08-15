[Codex 결과 보고서] Task 5.5

1. 고친 항목 6가지 각각의 구현 방법

- 라벨 컬럼 폭: `TerminalRenderer`에서 기호와 라벨 패딩을 분리했다. 출력 구조는 `2칸 indent + symbol + 공백 + 12칸 label + summary`다.
- 표시 순서: `DISPLAY_ORDER = LANGUAGE -> SENIORITY_LEVEL -> EXPERIENCE_YEARS -> DEGREE`를 렌더러에 두고, `CheckResult.findings()` 자체는 변경하지 않는다. `SKILLS`는 기존처럼 별도 skill gap 블록으로만 출력한다.
- evidence 들여쓰기: `EVIDENCE_INDENT`를 `INDENT.length() + 1 + 1 + LABEL_WIDTH`로 계산하게 바꿨다.
- `Level` 직무명 포함: `SeniorityLevelRule` summary가 title을 포함한다. 마커가 없으면 `Backend Engineer (no seniority marker)`, 마커가 있으면 기존 `profile max` 형식을 유지한다. 빈 title은 기존 `no seniority marker`를 유지하고, 40자 초과 title은 `…`으로 줄인다.
- WARN 접미사: `EXPERIENCE_YEARS`가 `WARN`일 때 터미널 렌더러에서만 ` — borderline`을 붙인다. core summary와 JSON renderer는 건드리지 않았다.
- 스킬 이름 표시: 터미널 전용 표시 이름 맵을 추가했다. `java -> Java`, `postgresql -> PostgreSQL`, `.net -> .NET` 등 지정된 값만 변환하고, 목록에 없는 값은 원본 그대로 둔다. skill gap 라벨 폭은 21칸으로 맞췄다.

2. 변경한 파일 목록

- `cli/src/main/java/dev/juhyeonl/atscheck/cli/render/TerminalRenderer.java`
- `cli/src/test/java/dev/juhyeonl/atscheck/cli/render/TerminalRendererTest.java`
- `core/src/main/java/dev/juhyeonl/atscheck/core/rule/SeniorityLevelRule.java`
- `core/src/test/java/dev/juhyeonl/atscheck/core/rule/SeniorityLevelRuleTest.java`
- `_briefs/task-5.5-report.md`

3. 네이티브 바이너리 실제 출력 (SKIP / APPLY 두 개)

검증은 `cli/build/native/nativeCompile/ats-check`로 했다. 로컬에 프로필 파일이 없어 지시서의 전제와 같은 임시 `XDG_CONFIG_HOME` 프로필을 사용했다: `years_experience: 2`, `years_tolerance: 1`, `max_seniority: mid`, `languages: [english, korean]`, `degree: bachelor`, `skills: [java, spring boot, postgresql, rest, docker]`.

SKIP, exit code 2:

```text
VERDICT: SKIP

  ✗ Language    Finnish required
                "Fluent Finnish and English are required."
  ⚠ Seniority   3+ years (profile: 2, tolerance: 1) — borderline
                "3+ years of experience."
  ✓ Degree      not required

  Analysis stopped at hard filter.
```

APPLY, exit code 0:

```text
VERDICT: APPLY

  ✓ Language    English only
  ✓ Level       Backend Engineer (no seniority marker)
  ⚠ Seniority   3+ years (profile: 2, tolerance: 1) — borderline
                "3+ years of experience."
  ✓ Degree      not required

  MISSING (required)   Kotlin, Kubernetes
  MISSING (nice)       Terraform
  MATCHED              Java, Spring Boot, PostgreSQL, REST
```

4. --json 출력이 변하지 않았음을 어떻게 확인했는가

- `JsonRenderer.java`는 수정하지 않았다.
- 같은 APPLY 입력을 네이티브 바이너리 `--json`으로 실행했다.
- `findings` 순서는 기존 실행 순서인 `LANGUAGE -> EXPERIENCE_YEARS -> DEGREE -> SENIORITY_LEVEL -> SKILLS` 그대로였다.
- skill gap JSON 값은 `["java", "spring boot", "postgresql", "rest"]`, `["kotlin", "kubernetes"]`, `["terraform"]`처럼 소문자 정규화 값을 유지했다.
- 단, `SENIORITY_LEVEL.summary`의 title 포함은 지시서 4번의 허용된 core 변경이라 JSON에도 반영된다.

5. 테스트 결과 (총 개수, 골든 20개 유지 확인)

- `./gradlew :cli:test :core:test` 성공
- `./gradlew build` 성공
- 테스트 XML 기준 총 142개, failures 0, errors 0, skipped 0
- 기존 137개 + 신규 5개다.
- `GoldenFileTest`는 20개 그대로 통과했다.
- `source "$HOME/.sdkman/bin/sdkman-init.sh"; ./gradlew :cli:nativeCompile` 성공
- native output 디렉터리의 파일은 `ats-check` 하나이며, 실행 파일 크기는 약 19MB다.

6. 스스로 결정한 것 + 근거

- WARN summary는 지시서 문구대로 core 문자열을 재작성하지 않고 터미널에서 접미사만 붙였다. 그래서 JSON summary 안정성과 기존 evidence 출력 조건을 유지했다.
- `MATCHED`에 Docker를 강제로 추가하지 않았다. 검증 APPLY 입력에는 Docker가 없고, 스킬 판정 동작 변경은 금지 범위라서 터미널 표시 이름 변환만 적용했다.
- 표시 순서는 렌더러에서만 바꿨다. JSON과 골든 테스트가 `findings()` 순서에 의존하므로 core 결과 순서는 건드리지 않았다.

7. 다음 추천 작업

- 다음 태스크 범위인 `save` / `open` / 배치 모드 구현으로 넘어가면 된다.

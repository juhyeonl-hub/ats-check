# [Codex 작업지시서] Task 5 — CLI 실전화 (Day 10-13의 1/3)

## 목표

`cli`를 실제 도구로 만든다. Spike A의 "파일을 읽어서 그대로 출력하는" 스텁을 대체한다.

1. `profile.yml` 로딩
2. `--job` / stdin 입력
3. `CLAUDE.md` §8 형식의 터미널 출력
4. `--json` 출력
5. **종료 코드 계약**

`save`/`open` 서브커맨드와 배치 모드는 **다음 태스크**다.

## 배경 (확인된 사실 — 다시 조사하지 말 것)

- `main` 브랜치, 커밋 `88f3b4a`. 테스트 123개 통과. **`core`는 완성됐다.**
- 공개 API:
  ```java
  AtsChecker.check(String jobText, Profile) -> CheckResult
  AtsChecker.check(JobPosting, Profile) -> CheckResult
  CheckResult(Verdict verdict, List<Finding> findings, SkillGap skillGap, boolean stoppedAtHardFilter)
  Finding(RuleId rule, Status status, String summary, List<Clause> evidence)
  SkillGap(Set<String> matched, Set<String> missingRequired, Set<String> missingNice)
  Profile(int yearsExperience, int yearsTolerance, Seniority maxSeniority,
          Set<String> languages, Degree degree, Set<String> skills)
  Profile.defaults()
  Clause.text(), Clause.lineNumber()
  ```
- `RuleId`: `LANGUAGE`, `EXPERIENCE_YEARS`, `DEGREE`, `SENIORITY_LEVEL`, `SKILLS`
- `Status`: `PASS`, `WARN`, `REVIEW`, `FAIL`
- `Verdict`: `APPLY`, `REVIEW`, `SKIP`
- 툴체인: `source "$HOME/.sdkman/bin/sdkman-init.sh"` 후 `./gradlew`
- `cli`는 이미 Picocli 4.7.6 + GraalVM Native Build Tools 0.10.4가 설정돼 있다

## 의존성

`cli`에 **SnakeYAML 2.3만** 추가한다 (`implementation("org.yaml:snakeyaml:2.3")`).

- `CLAUDE.md` §3의 승인된 스택이고 `core`의 테스트에서 이미 쓰는 버전이다
- **`core`에는 아무것도 추가하지 마라.** 런타임 의존성 0개를 유지한다
- **AWT / `java.desktop`을 끌어오는 어떤 것도 추가하지 마라** (§8 규칙, ADR-005)

## 만들 것

```
cli/src/main/java/dev/juhyeonl/atscheck/cli/
├── AtsCheckCli.java          # 최상위 @Command. 나중에 서브커맨드를 붙일 자리
├── command/
│   └── CheckCommand.java     # 기존 파일을 여기로 옮기고 실전화
├── config/
│   └── ProfileLoader.java    # profile.yml -> Profile
└── render/
    ├── TerminalRenderer.java
    └── JsonRenderer.java
```

기존 `cli/.../CheckCommand.java`는 Spike A 스텁이다. 대체하라.
`AtsChecker.echo(String)`도 이제 쓰이지 않으므로 **`core`에서 제거하라**
(이것만은 `core` 수정을 허용한다. `AtsCheckerTest`에서 관련 테스트가 있으면 함께 정리하라).

---

## 1. ProfileLoader

**탐색 순서:**
1. `--profile <path>` 옵션으로 지정된 경로
2. `$XDG_CONFIG_HOME/ats-check/profile.yml` (환경변수가 있으면)
3. `~/.config/ats-check/profile.yml`
4. 없으면 `Profile.defaults()` + **stderr에 한 줄 안내**
   (예: `no profile found, using defaults - run 'ats-check init' to create one`)

**파싱 규칙 (§7):**
- **SnakeYAML로 `Map<String, Object>`까지만 읽고 수동 매핑하라.**
  `new Constructor(Profile.class)` 같은 클래스 바인딩은 금지다 —
  네이티브 이미지에서 리플렉션 메타데이터를 요구한다
- `years_experience`, `max_seniority`, `languages`, `degree`, `skills` 키를 읽는다
  (YAML은 snake_case, 자바는 camelCase다)
- `years_tolerance`도 읽되 없으면 **1**
- 값이 없는 키는 `Profile.defaults()`의 값을 쓴다
- 문자열은 **소문자로 정규화**한다
- **잘못된 값은 조용히 무시하지 마라.** 예: `max_seniority: wizard` →
  stderr에 경고 + 기본값 사용. 파일 자체가 깨졌으면 **exit 64**

**`--profile`로 지정한 경로가 없으면 exit 64.** 사용자가 명시한 파일이 없는 것은 사용법 오류다.
(탐색 경로에 파일이 없는 것과 다르다)

---

## 2. 입력

```bash
ats-check --job job.txt        # 파일
pbpaste | ats-check            # stdin
ats-check                      # stdin도 없으면 -> 사용법 안내 + exit 64
```

- `--job`이 있으면 파일을 UTF-8로 읽는다. 없으면 **exit 64**
- `--job`이 없고 **stdin이 파이프**면 stdin을 읽는다
- 둘 다 없으면 사용법을 출력하고 exit 64
- stdin 판별: `System.console() == null`이면 파이프로 간주한다
- 입력이 공백뿐이면 exit 64 (`empty job posting`)

`JobPosting.fromText(text)`로 만들어 `AtsChecker.check(posting, profile)`을 호출한다.

---

## 3. 터미널 출력 — §8 형식을 그대로 지켜라

### SKIP 예시

```
VERDICT: SKIP

  ✗ Language    Finnish required
                "Fluent Finnish and English are required"
  ✓ Seniority   3+ years (profile: 2, tolerance: 1)
  ✓ Degree      not required

  Analysis stopped at hard filter.
```

### APPLY 예시

```
VERDICT: APPLY

  ✓ Language    English only
  ✓ Level       Backend Engineer (no seniority marker)
  ⚠ Seniority   "3+ years" — borderline
  ✓ Degree      not required

  MISSING (required)   Kotlin, Kubernetes
  MISSING (nice)       Terraform
  MATCHED              Java, Spring Boot, PostgreSQL, REST, Docker
```

**라벨 매핑 (§8 예시에서 그대로 읽은 것이다. 헷갈리지 마라):**

| `RuleId` | 라벨 |
|---|---|
| `LANGUAGE` | `Language` |
| `EXPERIENCE_YEARS` | `Seniority` |
| `DEGREE` | `Degree` |
| `SENIORITY_LEVEL` | `Level` |
| `SKILLS` | (라벨 없음 — `MISSING`/`MATCHED` 블록으로 출력) |

**상태 기호:**

| `Status` | 기호 |
|---|---|
| `PASS` | `✓` |
| `WARN` | `⚠` |
| `REVIEW` | `?` |
| `FAIL` | `✗` |

**규칙:**
- 라벨 컬럼은 정렬한다 (기호 + 공백 + 라벨을 12칸으로 패딩)
- **`evidence`가 있고 상태가 `PASS`가 아니면** 다음 줄에 큰따옴표로 감싼 원문을 들여쓰기해서 출력한다.
  §6이 요구하는 "원문 문장을 반드시 함께 출력한다"이다
- `stoppedAtHardFilter`면 마지막에 `Analysis stopped at hard filter.`
- `skillGap`이 있을 때만 `MISSING`/`MATCHED` 블록을 출력한다. 빈 집합은 그 줄을 생략한다
- 스킬 이름은 **표시할 때 첫 글자를 대문자로** 만들지 마라. 내부 값 그대로 쓰되
  쉼표+공백으로 join한다 (`kotlin, kubernetes`).
  §8 예시는 `Kotlin, Kubernetes`지만 정규화된 소문자를 그대로 쓰는 편이
  `postgresql`/`c++` 같은 값에서 안전하다. **이 결정을 보고서에 적어라**
- **색상 코드를 넣지 마라.** 이번 태스크에서는 기호만 쓴다

---

## 4. `--json`

**JSON은 손으로 만들어라.** SnakeYAML의 dump는 YAML을 뱉으므로 쓰지 마라.
작은 JSON writer를 직접 구현하고 **문자열 이스케이프**(`"`, `\`, 제어문자)를 정확히 처리하라.

```json
{
  "verdict": "SKIP",
  "stoppedAtHardFilter": true,
  "findings": [
    {
      "rule": "LANGUAGE",
      "status": "FAIL",
      "summary": "Finnish required",
      "evidence": ["Fluent Finnish and English are required."]
    }
  ],
  "skillGap": null
}
```

`skillGap`이 있으면:

```json
"skillGap": {
  "matched": ["java"],
  "missingRequired": ["kotlin"],
  "missingNice": ["terraform"]
}
```

- **스키마는 안정적으로 유지한다** (§8). 필드를 빼지 마라
- `--json`일 때 **stdout에는 JSON만** 나가야 한다. 안내 메시지는 전부 stderr로
- 배열 순서는 `CheckResult`의 순서를 그대로 따른다

---

## 5. 종료 코드 — 제품 계약이다

`CLAUDE.md` §8이 개정되어 **서브커맨드별로 분리**되었다. 이번 태스크는 `check`만 다룬다.

| 코드 | 조건 |
|---|---|
| 0 | `APPLY` |
| 1 | `REVIEW` |
| 2 | `SKIP` |
| 64 | 사용법 오류 (입력 없음, 파일 없음, 빈 입력, 잘못된 옵션, `--profile` 경로 없음) |
| 70 | 내부 오류 (예상 못 한 예외) |

- `--version`, `--help`는 **0**
- Picocli의 예외를 **그대로 흘리지 마라.** 파라미터 오류는 64로 매핑하라
- 예상 못 한 예외는 70으로 매핑하고, stderr에 짧은 메시지를 낸다.
  **스택 트레이스를 기본 출력하지 마라.** `--debug` 플래그가 있을 때만 출력한다

---

## 6. 테스트 — 종료 코드는 반드시 덮어라

`CLAUDE.md` §9가 "**종료 코드는 제품 계약이다. `cli`에 최소한의 통합 테스트를 둔다**"고 명시한다.
`cli/src/test/java/`에 만들어라 (현재 `cli`에는 테스트가 하나도 없다).

Picocli의 `CommandLine.execute()`가 반환하는 int를 검증하고,
stdout/stderr는 `ByteArrayOutputStream`으로 가로채라. **실제 프로세스를 띄우지 마라.**

1. APPLY 공고 → exit **0**
2. REVIEW 공고 → exit **1**
3. SKIP 공고 → exit **2**
4. `--job` 파일이 없음 → exit **64**, stderr에 메시지
5. 입력이 아예 없음 → exit **64**
6. 빈 입력(공백만) → exit **64**
7. `--version` → exit **0**
8. `--profile` 경로가 없음 → exit **64**
9. `--json` 출력이 **유효한 JSON**이고 `verdict` 필드가 기대값이다
   (SnakeYAML로 파싱해서 검증하면 된다 — JSON은 YAML의 부분집합이다)
10. `--json`일 때 stdout에 JSON 외의 것이 섞이지 않는다
11. 터미널 출력에 `VERDICT: ` 줄과 상태 기호가 포함된다
12. FAIL일 때 근거 원문이 큰따옴표로 출력된다
13. `profile.yml`을 읽어 `years_experience`가 판정에 반영된다
    (임시 파일 + `--profile`로 검증)
14. 잘못된 `max_seniority` 값 → 경고 후 기본값으로 동작 (exit는 정상)

## 수정 금지

- `CLAUDE.md`, `docs/adr/`, `_reviews/`, `_briefs/` 기존 파일
- **`core/section/`, `core/rule/`, `core/model/`** — 판정 로직은 완성됐다.
  문제를 발견하면 고치지 말고 **보고서에 적어라**
- `core/src/test/resources/golden/` — 골든 케이스
- `core`에 런타임 의존성 추가 금지
- `git commit` / `git push` 금지
- `save` / `open` / `init` 서브커맨드, 배치 모드(`--job-dir`), 프론트매터 — **다음 태스크**
  (`AtsCheckCli`에 서브커맨드를 붙일 **자리만** 만들어 두라)

**예외:** `AtsChecker.echo(String)` 제거는 허용한다.

## 완료 조건

1. `./gradlew build` 성공
2. **기존 123개 테스트가 그대로 통과**
3. 위 14개 CLI 테스트가 모두 통과
4. `./gradlew :core:dependencies --configuration runtimeClasspath` → `No dependencies`
5. **`./gradlew :cli:nativeCompile` 성공**
6. **네이티브 바이너리를 다른 디렉토리로 복사해서 실행해도 동작한다** (ADR-005의 교훈)
   ```bash
   cp cli/build/native/nativeCompile/ats-check /tmp/ac-test
   /tmp/ac-test --version
   echo "Requirements:\nFluent Finnish required." | /tmp/ac-test ; echo "exit=$?"   # 2 기대
   ls cli/build/native/nativeCompile/   # 사이드카 .so가 없어야 한다
   ```

**5번과 6번이 가장 중요하다.** SnakeYAML을 추가한 뒤에도 네이티브 빌드가 단일 파일로
유지되는지 반드시 확인하라. 사이드카 `.so`가 하나라도 생기면 **즉시 보고하고 멈춰라.**

## 보고서

`_briefs/task-5-report.md`

```
[Codex 결과 보고서] Task 5

1. 수행한 작업 요약
2. 생성/변경한 파일 목록
3. ProfileLoader의 탐색 순서와 오류 처리
4. 종료 코드를 Picocli에서 어떻게 매핑했는가
5. JSON writer의 이스케이프 처리 방식
6. 스킬 이름 표시 방식에 대한 결정 (소문자 유지 여부)
7. 테스트 결과 (총 개수, 기존 123개 유지 확인)
8. **네이티브 빌드 결과: 빌드 시간 / 바이너리 크기 / 사이드카 유무 / 격리 실행 결과**  ← 실제 출력
9. 스스로 결정한 것 + 근거
10. core에서 고쳐야 한다고 생각하는 것 (보고만)
11. 다음 추천 작업
```

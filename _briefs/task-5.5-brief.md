# [Codex 작업지시서] Task 5.5 — 출력 형식을 §8 사양에 맞춘다

## 목표

`TerminalRenderer`의 출력이 `CLAUDE.md` §8 예시와 어긋난다. 맞춘다.

동작·판정·종료 코드는 전부 정확하다. **표시만 고치는 작업이다.** 짧게 끝내라.

## 현재 출력 (실제 네이티브 바이너리 실행 결과)

```
VERDICT: SKIP

  ✗ Language  Finnish required
                "Fluent Finnish and English are required."
  ⚠ Seniority 3+ years (profile: 2, tolerance: 1)
                "3+ years of experience."
  ✓ Degree    not required

  Analysis stopped at hard filter.
```

```
VERDICT: APPLY

  ✓ Language  English only
  ⚠ Seniority 3+ years (profile: 2, tolerance: 1)
                "3+ years of experience."
  ✓ Degree    not required
  ✓ Level     no seniority marker

  MISSING (required)  kotlin, kubernetes
  MISSING (nice)      terraform
  MATCHED             java, spring boot, postgresql, rest
```

## §8 사양

```
VERDICT: SKIP

  ✗ Language    Finnish required
                "Fluent Finnish and English are required"
  ✓ Seniority   3+ years (profile: 2, tolerance: 1)
  ✓ Degree      not required

  Analysis stopped at hard filter.
```

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

## 고칠 것

### 1. 라벨 컬럼 폭

라벨 필드를 **12칸**으로 패딩하라. §8 예시에서 역산한 값이다:

```
"Language"(8) + 4 = 12
"Seniority"(9) + 3 = 12
"Degree"(6) + 6 = 12
"Level"(5) + 7 = 12
```

전체 줄 구조: `2칸 들여쓰기` + `기호(1)` + `공백(1)` + `라벨(12칸 패딩)` + `summary`

### 2. 표시 순서

§8 APPLY 예시의 순서를 따르라:

```
Language → Level → Seniority → Degree
```

현재는 `CheckResult.findings()`의 실행 순서(Stage 1 → 2 → 3)를 그대로 쓰고 있다.
**실행 순서와 표시 순서는 별개다.** 렌더러가 고정 순서로 정렬하라.

- 존재하지 않는 규칙은 건너뛴다 (SKIP이면 `Level`이 없다)
- `SKILLS`는 이 목록에 넣지 말고 지금처럼 `MISSING`/`MATCHED` 블록으로 출력한다
- **`findings` 목록 자체의 순서는 바꾸지 마라.** `--json`은 실행 순서를 유지해야 한다
  (골든 테스트와 JSON 스키마가 그 순서에 의존한다)

### 3. evidence 들여쓰기

evidence 줄이 **summary 시작 위치와 정렬**되어야 한다.

```
  ✗ Language    Finnish required
                "Fluent Finnish and English are required"
  ^^^^^^^^^^^^^^
  2 + 1 + 1 + 12 = 16칸
```

현재는 16칸으로 하드코딩된 듯한데 라벨 폭이 10이라 어긋나 보인다.
**라벨 폭과 연동되도록 상수 하나로 계산하라.**

### 4. `Level`에 직무명 포함

§8: `✓ Level       Backend Engineer (no seniority marker)`
현재: `✓ Level     no seniority marker`

`SeniorityLevelRule`의 `summary`에 **직무명을 포함**하라.

- 마커 없음: `"Backend Engineer (no seniority marker)"`
- 마커 있음: `"Senior Backend Engineer (profile max: mid)"` (현재 형식 유지)
- 직무명이 비어 있으면 지금처럼 `"no seniority marker"`

**이것만 `core` 수정을 허용한다** (`SeniorityLevelRule` + 관련 테스트).
직무명이 너무 길면 (40자 초과) 잘라내고 `…`을 붙여라.

### 5. WARN 접미사

§8 APPLY 예시: `⚠ Seniority   "3+ years" — borderline`

`EXPERIENCE_YEARS`가 `WARN`일 때 summary 끝에 ` — borderline`을 붙여라.
**렌더러에서 붙여라.** `core`의 summary 문자열은 바꾸지 마라 (`--json`과 골든 테스트에 영향).

### 6. 스킬 이름 표시

§8 예시는 `Kotlin, Kubernetes`처럼 대문자로 시작한다.
그러나 `postgresql`, `c++`, `.net`, `node.js` 같은 값은 단순 capitalize가 어색하다.

**표시 이름 맵을 만들어라.** 사전에 있는 항목은 정식 표기로, 없으면 원본 그대로:

```
java -> Java,  kotlin -> Kotlin,  spring boot -> Spring Boot,
postgresql -> PostgreSQL,  rest -> REST,  docker -> Docker,
kubernetes -> Kubernetes,  terraform -> Terraform,  typescript -> TypeScript,
javascript -> JavaScript,  node.js -> Node.js,  c++ -> C++,  c# -> C#,
.net -> .NET,  aws -> AWS,  gcp -> GCP,  sql -> SQL,  graphql -> GraphQL,
grpc -> gRPC,  ci/cd -> CI/CD,  html -> HTML,  css -> CSS,  api -> API,
mysql -> MySQL,  mongodb -> MongoDB,  redis -> Redis,  kafka -> Kafka,
python -> Python,  go -> Go,  rust -> Rust,  scala -> Scala,  swift -> Swift
```

**목록에 없으면 원본 소문자를 그대로 쓰라.** 억지로 capitalize하지 마라.
`--json`에는 **정규화된 소문자**를 유지하라 (스키마 안정성).

`MISSING (required)` / `MISSING (nice)` / `MATCHED` 라벨도 **21칸으로 정렬**하라
(§8 예시에서 `MISSING (required)`(18) + 3 = 21).

## 검증

수정 후 **네이티브 바이너리를 다시 빌드해서** 아래 두 공고를 실행하고,
출력을 §8 예시와 나란히 보고서에 붙여라.

```bash
source "$HOME/.sdkman/bin/sdkman-init.sh"
./gradlew :cli:nativeCompile
BIN=cli/build/native/nativeCompile/ats-check

printf 'Java Developer\n\nRequirements:\nFluent Finnish and English are required.\n3+ years of experience.\n' | $BIN
printf 'Backend Engineer\n\nRequirements:\nStrong Java, Spring Boot and PostgreSQL.\nExperience building REST APIs.\nKotlin and Kubernetes experience.\n3+ years of experience.\n\nNice to have:\nTerraform knowledge.\n' | $BIN
```

## 수정 금지

- `CLAUDE.md`, `docs/adr/`, `_reviews/`, `_briefs/` 기존 파일
- `core/section/`, `core/rule/`의 **다른 규칙**, `AtsChecker`, `core/model/`
- **골든 파일과 `--json` 출력** — 두 곳 다 현재 형식을 유지해야 한다.
  이번 수정은 **터미널 렌더링에만** 영향을 줘야 한다
- `save`/`open`/배치 모드 — 다음 태스크
- `git commit` / `git push` 금지

**예외:** `SeniorityLevelRule`의 summary(4번)와 그 테스트만 수정 허용.

## 완료 조건

1. `./gradlew build` 성공
2. **기존 137개 테스트가 통과** (`SeniorityLevelRule` summary 변경으로 깨지는 테스트는 갱신)
3. **골든 20개가 그대로 통과** — 골든은 status만 보므로 깨지면 안 된다
4. `--json` 출력이 변하지 않았다 (스킬 이름은 소문자 유지)
5. `./gradlew :cli:nativeCompile` 성공, **단일 파일 유지**
6. 렌더링 테스트 추가: 라벨 정렬, 표시 순서, evidence 들여쓰기, 표시 이름 변환

## 보고서

`_briefs/task-5.5-report.md`

```
[Codex 결과 보고서] Task 5.5

1. 고친 항목 6가지 각각의 구현 방법
2. 변경한 파일 목록
3. **네이티브 바이너리 실제 출력 (SKIP / APPLY 두 개)**  ← 반드시 붙일 것
4. --json 출력이 변하지 않았음을 어떻게 확인했는가
5. 테스트 결과 (총 개수, 골든 20개 유지 확인)
6. 스스로 결정한 것 + 근거
7. 다음 추천 작업
```

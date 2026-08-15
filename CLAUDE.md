# ats-check

채용 공고를 읽고 **"지원할 가치가 있는가"**를 판단하는 로컬 CLI 도구.

---

## 0. 이 문서의 목적

이 파일은 프로젝트의 **단일 진실 공급원(single source of truth)**이다.
Claude Code는 매 세션 이 파일을 먼저 읽고, 여기 적힌 범위 안에서만 작업한다.

**중요: 이 프로젝트의 최우선 목표는 기능이 아니라 완주다.**
3주 안에 GitHub Releases에 v0.1.0을 올리는 것이 성공 기준이다.

### 역할 분담 (상시)

- **Claude** = Technical PM / Tech Lead / Architect. 설계·작업지시서·리뷰·문서·커밋. 프로덕션 코드 직접 작성 금지.
- **Codex** = 구현자. 코드 작성·테스트 실행·변경 보고.
- **사용자** = 최종 결정자.

---

## 1. 제품 정의

### 무엇인가

공고 텍스트를 입력받아 세 단계로 검사하고 `APPLY` / `REVIEW` / `SKIP` 판정을 내린다.

Jobscan 같은 "이력서 최적화" 도구가 **아니다.**
이력서를 고치는 게 아니라 **공고를 거르는** 도구다.

### 왜 필요한가

구직자는 지원할 가치가 없는 공고를 읽는 데 시간을 낭비한다.
핀란드어 필수 요건을 공고 마지막 문단에서 발견하고 닫는 경험이 반복된다.
이 도구는 그 판단을 앞으로 당긴다.

- 공고 하나 정독: 2~3분
- 이 도구: 10초 (복사 → 붙여넣기 → 판정)

### 차별점

| | 경쟁 도구 | ats-check |
|---|---|---|
| 데이터 | 서버 업로드 | **로컬에서만 처리, 네트워크 호출 0** |
| 목적 | 이력서 최적화 | **공고 필터링** |
| 시장 | 미국 ATS 기준 | **핀란드/EU 채용 맥락** |
| 비용 | 유료 구독 | 무료 |

---

## 2. 스코프 잠금 (SCOPE LOCK)

**아래 항목은 v0.1에서 절대 구현하지 않는다.**
사용자가 요청하더라도 이 문서를 근거로 되묻고, 명시적으로 잠금을 해제하기 전까지 진행하지 않는다.

### 구현 금지 목록

- ❌ 웹 스크래핑 (LinkedIn, Indeed 등) — ToS 위반, 계정 정지 위험, 포폴에 게시 불가
- ❌ 브라우저 확장 프로그램
- ❌ LLM / AI API 호출 — 규칙 기반이 더 정확하고 빠르고 무료다
- ❌ 네트워크 통신 일체 (`--url` 포함, v0.2로 연기)
- ❌ Docker 배포 — 네이티브 바이너리를 만드는 이유가 컨테이너를 안 쓰기 위해서다
- ❌ 웹 UI, 서버, 데이터베이스
- ❌ 사용자 계정, 인증
- ❌ 공고 자동 수집 (fetch) — v0.2
- ❌ 다국어 UI

### 판단 기준

새 기능 제안이 들어오면 다음을 묻는다:

1. 이게 없으면 v0.1이 쓸모없어지는가? → 아니면 백로그
2. 3주 일정을 넘기는가? → 넘기면 백로그
3. 네트워크나 외부 의존이 생기는가? → 생기면 백로그

---

## 3. 기술 스택

| 항목 | 선택 | 이유 |
|---|---|---|
| 언어 | **Java 21 (LTS)** | 사용자의 주 타깃이 JVM 백엔드 직무 |
| 빌드 | **Gradle (Kotlin DSL)** | 멀티모듈, GraalVM 플러그인 지원 |
| CLI | **Picocli** | GraalVM native-image 공식 지원 |
| PDF | **Apache PDFBox** | 이력서 파싱 (선택 경로) |
| DOCX | **Apache POI** | 이력서 파싱 (선택 경로) |
| YAML | **SnakeYAML** | profile.yml |
| 테스트 | **JUnit 5 + AssertJ** | |
| 네이티브 | **GraalVM native-image** | JVM 없이 실행, 시작 속도 |

### 의존성 원칙

- `core` 모듈은 **의존성 최소화**. 이상적으로는 SnakeYAML만.
- PDFBox/POI는 `cli` 모듈에만. `core`는 순수 텍스트만 다룬다.
- 모든 의존성 버전은 **고정(pin)**한다. 동적 버전 금지.

---

## 4. 아키텍처

```
ats-check/
├── core/                    # 순수 로직. I/O 없음. 네트워크 없음.
│   └── src/main/java/
│       ├── model/           # JobPosting, Profile, Verdict, Finding
│       ├── section/         # 섹션 분류 (Requirements vs Nice-to-have)
│       ├── rule/            # LanguageRule, SeniorityRule, DegreeRule, SkillRule
│       └── AtsChecker.java  # 진입점: check(String jobText, Profile) -> Result
├── cli/                     # Picocli 래퍼. 파일 I/O, 출력 포매팅.
│   └── src/main/java/
│       ├── CheckCommand.java
│       ├── extract/         # PDF/DOCX 텍스트 추출
│       └── render/          # 터미널 출력, JSON 출력
└── build.gradle.kts
```

### 설계 원칙

- **`core`는 문자열을 받아 판정을 반환하는 순수 함수다.** 파일도 네트워크도 모른다.
- 이 분리 덕분에 나중에 웹/API/확장을 얹을 때 로직 재사용이 가능하다.
- `core`에 테스트를 집중한다. `cli`는 얇게 유지한다.

---

## 5. 판정 알고리즘

### Stage 1 — 하드 필터 (이력서 불필요)

하나라도 걸리면 즉시 `SKIP`. 이후 단계를 실행하지 않는다.

| 검사 | 탈락 조건 |
|---|---|
| **언어** | 핀란드어/스웨덴어가 **필수 요건**으로 명시 |
| **연차** | 요구 연차 > `profile.years_experience` + 허용 오차 |
| **학위** | 석사/박사 학위가 **필수 요건**으로 명시 |

### Stage 2 — 레벨

직무명에서 시니어리티 추출:

```
junior | entry | graduate | trainee   → JUNIOR
(마커 없음)                            → MID (기본값)
senior | sr.                          → SENIOR
lead | staff | principal | head       → LEAD
```

`profile.max_seniority`를 초과하면 `WARN` (SKIP 아님).

### Stage 3 — 스킬 갭 (이력서 또는 profile 필요)

공고에서 기술 키워드를 추출하고 profile/이력서와 대조.
**필수(required)와 우대(nice-to-have)를 구분해서 집계한다.**

---

## 6. 핵심 난제: 섹션 분류

**이 프로젝트의 유일한 기술적 난제이자 핵심 가치다.**

단순 키워드 매칭은 오작동한다:

```
"Fluent Finnish required"           → 탈락시켜야 함
"Finnish is a plus"                 → 통과시켜야 함
"Working knowledge of Finnish"      → 애매 → REVIEW
```

### 해결: 2층 신호

**신호 1 — 섹션 헤더 감지**

| 필수 섹션 | 우대 섹션 |
|---|---|
| Requirements | Nice to have |
| Must have | Bonus |
| We expect | Advantages |
| Qualifications | Preferred |
| You have | Plus |
| Essential | Good to have |

**신호 2 — 문장 어조**

| 필수 표현 | 우대 표현 |
|---|---|
| must, required, essential | is a plus, nice to have |
| fluent, native | beneficial, advantageous |
| mandatory, expected | preferred, ideally |
| you will need | familiarity with |

### 충돌 처리

두 신호가 어긋나면 **단정하지 않고 `REVIEW`로 내보낸다.**
모호한 것을 억지로 판정하지 않는 것이 신뢰를 만든다.

**원문 문장을 반드시 함께 출력한다.** 사용자가 직접 판단할 수 있어야 한다.

---

## 7. profile.yml

위치: `~/.config/ats-check/profile.yml`

```yaml
years_experience: 2
max_seniority: mid          # junior | mid | senior | lead
languages: [english, korean]
degree: bachelor            # none | bachelor | master | phd
skills:
  - java
  - spring
  - typescript
  - react
  - postgresql
  - docker
```

**중요:** profile만 있으면 이력서 파일 없이도 Stage 1~2가 동작한다.
PDF 파싱이 GraalVM에서 문제가 생겨도 제품이 성립하도록 하는 안전장치다.

---

## 8. 입출력

### 입력 방식 (v0.1)

```bash
ats-check --job job.txt                    # 파일
pbpaste | ats-check                        # stdin (주 사용 경로)
ats-check --job-dir ./jobs                 # 배치
ats-check --job job.txt --resume cv.pdf    # 이력서 병행
```

### 단일 공고 출력

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

### 배치 출력

```
VERDICT  FILE                   REASON
SKIP     alten.txt              Finnish required
SKIP     siili.txt              Senior (7+ years)
APPLY    wolt.txt               missing 2 required skills
APPLY    ravogen.txt            full match
REVIEW   solita.txt             Finnish "working knowledge" — ambiguous

5 jobs · 2 apply · 1 review · 2 skip
```

### JSON 출력

`--json` 플래그로 스크립트 연동 가능하게. 스키마는 안정적으로 유지.

### 종료 코드

| 코드 | 의미 |
|---|---|
| 0 | APPLY |
| 1 | REVIEW |
| 2 | SKIP |
| 64 | 사용법 오류 |
| 70 | 내부 오류 |

---

## 9. 테스트 전략

### 골든 파일 테스트 (핵심)

```
core/src/test/resources/golden/
├── finnish-required/
│   ├── input.txt          # 실제 공고 텍스트 (익명화)
│   └── expected.json      # 기대 판정
├── finnish-optional/
├── senior-role/
├── ambiguous-language/
└── ...
```

**최소 30개 케이스를 실제 공고로 만든다.**
규칙을 수정할 때마다 CI가 회귀를 잡아준다.

익명화: 회사명과 개인정보는 치환. 요건 문장 구조는 원본 유지.

### 커버리지 목표

- `core`: 80% 이상
- `cli`: 목표 없음 (얇게 유지)

---

## 10. CI/CD

**이 프로젝트의 포트폴리오 가치 중 절반이 여기 있다.**

### PR 파이프라인

```yaml
on: pull_request
jobs:
  test:
    strategy:
      matrix:
        os: [ubuntu-latest, macos-latest, windows-latest]
        java: [21]
    steps:
      - 컴파일
      - 단위 테스트 + 골든 파일 테스트
      - 커버리지 리포트를 PR에 코멘트
```

### 릴리스 파이프라인

```yaml
on:
  push:
    tags: ['v*']
jobs:
  native-build:
    strategy:
      matrix:
        os: [ubuntu-latest, macos-latest, windows-latest]
    steps:
      - GraalVM native-image 빌드
      - 바이너리 스모크 테스트 (--version, 샘플 공고 판정)
      - SHA256 체크섬 생성
      - 아티팩트 업로드
  release:
    - GitHub Release 생성
    - 3개 OS 바이너리 + checksums.txt 첨부
    - 체인지로그 자동 생성
```

### 주의: Linux 빌드는 컨테이너 안에서

최신 Ubuntu 러너에서 직접 빌드하면 glibc 버전이 높아서 구버전 배포판에서 실행되지 않는다.
**구버전 베이스 이미지 컨테이너 안에서 native-image를 실행한다.**
이것이 이 프로젝트에서 Docker를 쓰는 유일하고 정당한 이유다. ADR로 기록할 것.

### 문서화

레포에 다음을 유지한다:

- `docs/adr/` — 설계 결정 기록
  - ADR-001: 왜 스크래핑을 하지 않는가
  - ADR-002: 왜 LLM을 쓰지 않는가
  - ADR-003: 왜 컨테이너 안에서 네이티브 빌드를 하는가
  - ADR-004: 왜 core/cli를 분리했는가

---

## 11. 3주 일정

| 기간 | 목표 | 완료 기준 |
|---|---|---|
| **Day 1-3** | **스파이크** | `./gradlew nativeCompile` 성공. 바이너리가 파일 읽어서 텍스트 출력 |
| Day 4-9 | 판정 로직 | 3단계 알고리즘 + 섹션 분류 + 골든 파일 20개 |
| Day 10-13 | CLI 완성 | stdin, 배치, `--json`, 출력 포매팅, 이력서 파싱 |
| Day 14-17 | CI/CD | 매트릭스 테스트, 릴리스 자동화, 체크섬 |
| Day 18-21 | 공개 | README, 데모 GIF, ADR, v0.1.0 릴리스 |

### Day 1-3이 관문이다

PDFBox는 리플렉션을 많이 사용해서 GraalVM native-image에서 문제가 생길 수 있다.
**기능을 다 만든 뒤에 네이티브 빌드를 시도하지 말 것.**

3일 안에 판단:
- 네이티브 빌드 성공 → 그대로 진행
- PDFBox만 문제 → PDF 지원을 v0.2로 미루고 profile.yml 모드로 진행
- 전면 실패 → fat JAR + jpackage로 폴백

---

## 12. 백로그 (v0.1 이후)

**v0.1이 릴리스되기 전까지 절대 착수하지 않는다.**

### v0.2 — 공고 수집

- `ats-check fetch` 서브커맨드로 **분리**
- TE-palvelut (핀란드 고용경제부) 공공 구인 API
- RSS 피드 (Duunitori, Oikotie 등)
- **`check`와 반드시 분리**: fetch는 외부 API 의존이라 깨질 수 있고, check는 순수 로컬이라 안 깨진다

### v0.3 이후 후보

- Homebrew tap
- Maven Central에 `core` 모듈 배포 (GPG 서명)
- 브라우저 확장 (수동 트리거 방식만)
- 규칙 커스터마이징

---

## 13. 완료 정의 (Definition of Done)

v0.1.0은 다음을 모두 만족할 때 릴리스한다:

- [ ] 3개 OS 네이티브 바이너리가 GitHub Releases에 있다
- [ ] 체크섬이 첨부되어 있다
- [ ] 골든 파일 테스트 30개 이상이 CI에서 통과한다
- [ ] README에 설치 방법과 데모가 있다
- [ ] ADR 4개가 작성되어 있다
- [ ] 태그를 푸시하면 릴리스가 자동 생성된다

---

## 14. 작업 원칙

- **완주 > 완성도.** 부족해도 릴리스하고 개선한다.
- **스코프 추가 요청이 오면 이 문서 2절을 근거로 되묻는다.**
- 커밋은 작게, 자주. 각 커밋은 테스트를 통과한 상태.
- 막히면 폴백 경로를 택하고 백로그에 남긴다. 멈추지 않는다.

---

## 15. 진행 로그

> 각 단계 완료 시 여기에 사실만 기록한다. 계획은 위 절에, 결과는 여기에.

### 2026-08-15 — Day 0: 착수

- 레포 생성, `git init` (branch `main`)
- 로컬 환경 점검: Java Temurin 21.0.10 있음 / Gradle 없음 / **GraalVM `native-image` 없음** / Codex 0.133.0 있음
- Day 1-3 스파이크 착수 (Codex 위임)

### 2026-08-15 — Spike A 통과 ✅ (§11 "네이티브 빌드 성공 → 그대로 진행" 경로 확정)

**결과: `./gradlew :cli:nativeCompile` 성공. Day 1-3 관문 통과.**

| 항목 | 실측값 |
|---|---|
| native 빌드 시간 | 17초 (clean 기준) |
| 바이너리 크기 | 17MB |
| 시작 시간 | 0.004s |
| core 테스트 | 2개 통과 (clean 빌드로 실제 실행 확인) |

- 툴체인 (모두 홈 디렉토리, sudo 미사용):
  - SDKMAN 5.23.0
  - **GraalVM CE 21.0.2** (`21.0.2-graalce`) — SDKMAN이 제공하는 **21 LTS 라인의 유일한 버전**. 상위는 25.2.4(JDK 25 기반)라 §3의 Java 21 고정과 충돌하므로 채택하지 않음
  - Gradle 8.10.2
- `zip`이 시스템에 없어 SDKMAN 설치가 1차 실패 → Info-ZIP 3.0을 `~/.local/bin`에 빌드해 해결 (sudo 금지 제약 때문)
- picocli-codegen이 `reflect-config.json` / `proxy-config.json` / `resource-config.json`을 자동 생성 → 네이티브 리플렉션 이슈 없음
- exit code 규격 동작 확인: 정상 0 / 파일 없음 64
- `core`에 네트워크·파일 I/O·stdout 없음 (grep 검증)

**알려진 제약 (사실):**
- `org.gradle.configuration-cache=true`는 사용 불가. Native Build Tools 0.10.4의 `generateResourcesConfigFile`이 실패한다. **CI에서도 켜지 말 것.**
- `cli` 빌드에서 Gradle 9.0 deprecation 경고가 발생한다 (`core`에서는 발생하지 않음 → NBT 플러그인 또는 `application` 플러그인 경로). Gradle 8.x 고정으로 회피 중.
- GraalVM toolchain 자동 감지가 꺼져 있고 `JAVA_HOME`에 의존한다. CI에서 명시적으로 설정해야 한다.

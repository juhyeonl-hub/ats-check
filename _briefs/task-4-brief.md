# [Codex 작업지시서] Task 4 — 골든 파일 테스트 20개

## 목표

`CLAUDE.md` §9의 **골든 파일 테스트**를 구축한다.
공고 텍스트와 기대 판정을 파일로 두고, 규칙을 고칠 때마다 CI가 회귀를 잡게 한다.

이 태스크가 끝나면 **Day 4-9(판정 로직)가 완료**된다.

## 배경 (확인된 사실 — 다시 조사하지 말 것)

- `main` 브랜치, 커밋 `a0a963a`. 테스트 102개 통과 중. `core` 완성됨.
- 공개 API:
  - `AtsChecker.check(String jobText, Profile) -> CheckResult`
  - `AtsChecker.check(JobPosting, Profile) -> CheckResult`
  - `CheckResult(verdict, findings, skillGap, stoppedAtHardFilter)`
  - `Finding(rule, status, summary, evidence)`
  - `SkillGap(matched, missingRequired, missingNice)`
- `core` 런타임 의존성 **0개. 런타임은 계속 0개를 유지한다.**

### 공고 텍스트에 대한 제약 (중요)

§9는 "실제 공고로 만든다"고 하지만, **실제 공고를 가져올 수 없다.**
네트워크 호출은 §2가 금지하고, 스크래핑은 ADR-001이 금지한다.

따라서 **실제 핀란드/EU 채용 공고의 문체와 구조를 모방한 합성 텍스트**를 작성하라.

- 회사명은 가상으로 (`Northgate Systems`, `Aurora Labs` 등). 실존 회사명을 쓰지 마라
- 문장 구조는 실제 공고처럼: 짧은 소개 → `Requirements:` → `Nice to have:` → `Benefits:`
- 불릿, 대소문자 혼용, 약간의 장황함 등 **실제 공고의 지저분함을 반영하라.**
  깔끔한 텍스트만 있으면 테스트가 현실을 못 잡는다
- 각 공고는 12~30줄 정도

**`golden/README.md`에 "이 케이스들은 합성이며, 실제 공고로 교체·추가할 수 있다"고
명시하라.** 나중에 사용자가 진짜 공고를 익명화해서 넣을 것이다.

## 구조

```
core/src/test/resources/golden/
├── README.md
├── 01-finnish-required/
│   ├── input.txt
│   └── expected.json
├── 02-finnish-nice-to-have/
└── ...
```

### expected.json 스키마

```json
{
  "verdict": "SKIP",
  "stoppedAtHardFilter": true,
  "findings": [
    { "rule": "LANGUAGE",         "status": "FAIL" },
    { "rule": "EXPERIENCE_YEARS", "status": "PASS" },
    { "rule": "DEGREE",           "status": "PASS" }
  ],
  "skillGap": null
}
```

`skillGap`이 있는 경우:

```json
"skillGap": {
  "missingRequired": ["kotlin", "kubernetes"],
  "missingNice": ["terraform"],
  "matched": ["java", "spring boot"]
}
```

**선택적 필드:**
- `"evidenceContains"`: 특정 규칙의 근거 절에 포함돼야 하는 문자열.
  §6이 요구하는 "원문 문장 출력"을 검증한다
  ```json
  { "rule": "LANGUAGE", "status": "FAIL",
    "evidenceContains": "Fluent Finnish" }
  ```
- `"profile"`: 케이스별 프로필 override. 없으면 아래 기본 프로필을 쓴다
  ```json
  "profile": { "yearsExperience": 5, "degree": "MASTER", "languages": ["english", "finnish"] }
  ```

### 기본 테스트 프로필 (고정)

```
yearsExperience: 2
yearsTolerance:  1
maxSeniority:    MID
languages:       [english, korean]
degree:          BACHELOR
skills:          [java, spring boot, postgresql, rest, docker]
```

## JSON 파싱

`expected.json`을 읽으려면 파서가 필요하다.

**`testImplementation("org.yaml:snakeyaml:2.3")`을 `core`에 추가하라.**

- YAML 1.2는 JSON의 상위집합이므로 SnakeYAML로 JSON을 그대로 파싱할 수 있다
- **`testImplementation`이므로 `core`의 런타임 의존성은 0개로 유지된다.** 반드시 확인하라
- SnakeYAML은 `CLAUDE.md` §3의 승인된 스택이고, `cli`에서도 쓸 예정이므로 버전을 2.3으로 통일한다
- **`Map<String, Object>`로만 읽어라.** 클래스 바인딩(`Constructor(X.class)`)은 쓰지 마라 (§7 규칙)

## 만들 케이스 20개

| # | 이름 | 검증 대상 | 기대 verdict |
|---|---|---|---|
| 01 | `finnish-required` | 핀란드어 필수 | `SKIP` |
| 02 | `finnish-nice-to-have` | `Finnish is a plus` | `APPLY` |
| 03 | `finnish-negated` | **`Finnish is not required`** | `APPLY` |
| 04 | `finnish-ambiguous` | `Working knowledge of Finnish` | `REVIEW` |
| 05 | `finnish-in-benefits` | **복지 섹션의 `Finnish lessons`** | `APPLY` |
| 06 | `swedish-required` | 스웨덴어 필수 | `SKIP` |
| 07 | `finnish-required-but-profile-has-it` | profile에 finnish (override) | `APPLY` |
| 08 | `senior-seven-years` | `7+ years` | `SKIP` |
| 09 | `borderline-three-years` | `3+ years` (tolerance 안) | `APPLY` + `WARN` |
| 10 | `experience-not-stated` | 연차 언급 없음 | `APPLY` |
| 11 | `experience-in-nice-section` | `5+ years`가 우대 섹션에 | `APPLY` |
| 12 | `msc-required` | 석사 필수 | `SKIP` |
| 13 | `msc-or-equivalent` | `MSc or equivalent experience` | `APPLY` + `WARN` |
| 14 | `phd-nice-to-have` | 박사 우대 | `APPLY` |
| 15 | `master-of-your-craft` | **학위 오탐 방지** | `APPLY` |
| 16 | `senior-title` | `Senior Backend Engineer` | `APPLY` + `WARN` |
| 17 | `lead-title` | `Head of Engineering` | `APPLY` + `WARN` |
| 18 | `junior-title` | `Junior Developer` | `APPLY` |
| 19 | `skill-gap` | 필수/우대 스킬 갭 | `APPLY` + skillGap |
| 20 | `multiple-hard-fails` | 언어+연차+학위 동시 실패 | `SKIP` (FAIL 3개) |

**03, 05, 15는 회귀 방지의 핵심이다.** 리뷰에서 실제로 잡힌 결함들이다.
이 세 케이스는 특히 현실적으로 작성하라.

## 테스트 하네스

`core/src/test/java/dev/juhyeonl/atscheck/core/GoldenFileTest.java`

- `@ParameterizedTest` + 디렉토리 스캔으로 케이스를 **자동 발견**하라.
  케이스를 추가할 때 자바 코드를 고치지 않아도 되어야 한다
- 케이스 디렉토리 이름을 테스트 표시 이름으로 쓰라 (실패 시 어느 케이스인지 바로 보이게)
- 검증 항목: `verdict`, `stoppedAtHardFilter`, 각 `finding`의 `rule`+`status`,
  `skillGap`의 세 집합, `evidenceContains`
- **`skillGap` 집합 비교는 순서 무관**으로 하라 (`containsExactlyInAnyOrder`)
- 골든 디렉토리가 비어 있으면 **테스트가 실패**해야 한다 (조용히 0개 통과 금지)
- 실패 메시지에 **기대값과 실제값을 모두** 담아라. 디버깅 가능해야 한다

## 수정 금지

- `CLAUDE.md`, `docs/adr/`, `_reviews/`, `_briefs/` 기존 파일
- `cli/` 전체
- **`core/section/`, `core/rule/`, `AtsChecker` — 프로덕션 코드 전체.**
  이번 태스크는 **테스트와 리소스만** 만든다
- **골든 케이스가 실패하면 프로덕션 코드를 고치지 마라.**
  기대값이 틀렸는지 코드가 틀렸는지 판단해서 **보고서에 적어라.**
  이것이 이번 태스크에서 가장 중요한 규칙이다
- `core`에 **런타임** 의존성 추가 금지 (`testImplementation`은 허용)
- `git commit` / `git push` 금지

## 완료 조건

1. `./gradlew build` 성공
2. 골든 케이스 20개가 모두 통과
3. **기존 102개 테스트가 그대로 통과**
4. `./gradlew :core:dependencies --configuration runtimeClasspath` → `No dependencies`
5. `golden/README.md`에 합성 케이스라는 사실과 교체 방법이 적혀 있다

## 보고서

`_briefs/task-4-report.md`

```
[Codex 결과 보고서] Task 4

1. 수행한 작업 요약
2. 생성한 파일 목록 (케이스 20개 + 하네스)
3. 테스트 하네스 설계 (자동 발견, 비교 방식)
4. 테스트 결과 (총 개수, 골든 20개 통과 확인, 기존 102개 유지 확인)
5. **기대값과 실제 동작이 어긋난 케이스** ← 있으면 반드시 적어라.
   어느 쪽이 틀렸다고 보는지 네 판단도 함께
6. 스스로 결정한 것 + 근거
7. 케이스를 만들면서 발견한 규칙의 약점
8. 다음 추천 작업
```

**5번과 7번이 이 태스크의 진짜 산출물이다.** 케이스 20개를 쓰다 보면
규칙이 못 잡는 패턴이 보인다. 고치지 말고 전부 적어라.

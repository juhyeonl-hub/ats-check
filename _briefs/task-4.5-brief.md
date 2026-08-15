# [Codex 작업지시서] Task 4.5 — PASS 판정에도 근거를 남긴다

## 목표

`Finding.evidence()`가 **`PASS`일 때도 근거 절을 담도록** 한다.
그리고 골든 케이스 03/05/15에 `evidenceContains`를 추가해 회귀 방지력을 높인다.

작은 작업이다. 30분 안에 끝나야 한다.

## 왜 필요한가

네 Task 4 보고서 §7의 첫 번째 지적이 맞다:

> PASS finding은 evidence가 비어 있다. 그래서 `Finnish is not required`,
> 복지 섹션 `Finnish lessons`, `master your craft` 같은 PASS 회귀 케이스는
> 원문 evidence까지는 검증할 수 없고 최종 status로만 검증한다.

문제는 **PASS의 이유를 구분할 수 없다**는 것이다:

| 상황 | 현재 결과 |
|---|---|
| `"Finnish is not required"` → 부정 신호를 정확히 읽고 PASS | `PASS`, evidence 비어 있음 |
| 규칙이 핀란드어 **언급 자체를 못 찾아서** PASS | `PASS`, evidence 비어 있음 |

두 경우가 테스트에서 똑같이 보인다. 앞의 케이스가 뒤의 케이스로 퇴화해도
골든 테스트가 잡지 못한다. **03/05/15는 리뷰에서 실제로 잡은 결함의 회귀 방지 케이스이므로,
가장 강하게 검증되어야 하는데 지금은 가장 약하게 검증되고 있다.**

이전 지시서에서 내가 "PASS는 evidence가 비어도 된다"고 한 것이 원인이다. 그 판단을 바꾼다.

## 수정 1 — PASS에도 근거를 담는다

**규칙이 특정 절을 근거로 PASS를 판정했다면 그 절을 `evidence`에 담아라.**

| 규칙 | PASS 상황 | evidence |
|---|---|---|
| `LanguageRule` | `NEGATED` 절 발견 (`"Finnish is not required"`) | **담는다** |
| `LanguageRule` | `NICE` 절 발견 (`"Finnish is a plus"`) | **담는다** |
| `LanguageRule` | `UNKNOWN` 절에서 언어 언급 (복지 섹션) | **담는다** |
| `LanguageRule` | profile에 해당 언어가 있어서 통과 | **담는다** (언어를 요구한 절) |
| `LanguageRule` | 언어 언급이 아예 없음 (`"English only"`) | 비운다 |
| `DegreeRule` | `NICE` 절의 학위 (`"PhD is a plus"`) | **담는다** |
| `DegreeRule` | 요구 학위 ≤ profile (`"Bachelor's degree"` + BACHELOR) | **담는다** |
| `DegreeRule` | 학위 언급 없음 | 비운다 |
| `ExperienceYearsRule` | 요구 연차가 profile 이하 | **담는다** |
| `ExperienceYearsRule` | 연차 언급 없음 | 비운다 |
| `SeniorityLevelRule` | — | 지금처럼 비워도 된다 (근거가 절이 아니라 title이다) |
| `SkillRule` | — | 지금처럼 비워도 된다 (근거가 `SkillGap`에 있다) |

**규칙:** "이 절을 봤기 때문에 이 판정을 내렸다"면 담는다. "아무것도 못 찾아서 통과"면 비운다.

**`summary` 문구는 바꾸지 마라.** 기존 테스트가 깨진다.

## 수정 2 — 골든 케이스에 `evidenceContains` 추가

Task 4에서 만든 `evidenceContains` 필드를 실제로 쓴다.

최소한 아래 케이스에 추가하라:

| 케이스 | 규칙 | `evidenceContains` (해당 공고의 실제 문장 일부) |
|---|---|---|
| `03-finnish-negated` | `LANGUAGE` | 부정 문장의 일부 (예: `"not required"`) |
| `05-finnish-in-benefits` | `LANGUAGE` | 복지 문장의 일부 (예: `"Finnish lessons"`) |
| `14-phd-nice-to-have` | `DEGREE` | 박사 우대 문장의 일부 |
| `07-finnish-required-but-profile-has-it` | `LANGUAGE` | 핀란드어를 요구한 문장의 일부 |
| `01-finnish-required` | `LANGUAGE` | 이미 있으면 유지, 없으면 추가 |

`15-master-of-your-craft`는 `DEGREE`가 "학위 언급 없음" PASS이므로 evidence가 비는 것이 맞다.
**대신 evidence가 비어 있음을 명시적으로 검증하라** — `"evidenceEmpty": true` 같은 필드를
추가하거나, 하네스가 `evidenceContains` 부재를 그냥 넘기지 않도록 하라. 방식은 네가 정해라.

**각 케이스의 실제 `input.txt` 문장을 확인하고 정확히 일치하는 부분 문자열을 쓰라.**
추측으로 쓰지 마라.

## 수정 3 — 기존 테스트 보강

`LanguageRuleTest`의 아래 테스트를 강화하라:

- `"Finnish is not required"` → `PASS`이면서 **evidence에 그 문장이 들어 있다**
- 복지 섹션 케이스 → `PASS`이면서 **evidence에 그 문장이 들어 있다**

## 수정 금지

- `CLAUDE.md`, `docs/adr/`, `_reviews/`, `_briefs/` 기존 파일
- `cli/` 전체
- `core/section/` 전체 — 분류기는 건드리지 않는다
- `Finding` record의 **구조 변경 금지** — `evidence` 필드는 이미 있다. 채우기만 하라
- `summary` 문구 변경 금지
- `core`에 런타임 의존성 추가 금지
- `git commit` / `git push` 금지

## 완료 조건

1. `./gradlew build` 성공
2. **기존 122개 테스트가 그대로 통과** (`summary`를 안 바꿨으면 깨질 이유가 없다)
3. 골든 케이스 5개 이상에 `evidenceContains`가 들어가고 통과한다
4. `core` 런타임 의존성 0개

## 검증 요청

**`evidenceContains`를 일부러 틀린 값으로 바꿔서 테스트가 실패하는지 확인하고,
그 출력을 보고서에 붙여라.** 검증하지 않는 검증 필드는 없느니만 못하다.

## 보고서

`_briefs/task-4.5-report.md`

```
[Codex 결과 보고서] Task 4.5

1. 각 규칙에서 PASS evidence를 어떤 조건으로 채웠는가
2. 변경한 파일 목록
3. evidenceContains를 추가한 골든 케이스와 그 값
4. **일부러 틀리게 만들어 실패를 확인한 출력**   ← 반드시 붙일 것
5. 테스트 결과 (총 개수, 기존 122개 유지 확인)
6. 스스로 결정한 것 + 근거
7. 다음 추천 작업
```

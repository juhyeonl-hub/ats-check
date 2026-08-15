# [Codex 작업지시서] Task 7.5 — 배치 출력에서 URL을 지킨다

## 목표

배치 출력에 **URL 컬럼이 나타나지 않는 결함**을 고친다. 짧은 작업이다.

## 재현 (네이티브 바이너리, 기본 폭)

```
VERDICT  COMPANY             TITLE                              REASON
SKIP     Alten               Java Developer                     Finnish required
APPLY    no-frontmatter.txt  Plain Posting Without Frontmatter  full match
APPLY    Ravogen             Fullstack Developer                full match
SKIP     Siili               Backend Architect                  At least 7 years (profile: 2, toler…
REVIEW   Solita              Node.js Developer                  Finnish - ambiguous requirement
APPLY    Wolt                Backend Engineer                   missing: Kotlin, Kubernetes

6 jobs · 3 apply · 1 review · 2 skip
```

**URL 컬럼이 헤더째로 사라졌다.** 공고 파일에는 전부 `url`이 있다.

## 왜 이게 심각한가

§8 배치 출력 절의 첫 문장이다:

> **URL을 반드시 포함한다.** 판정 결과에서 원래 공고로 돌아갈 수 있어야 한다.

이것이 `save` 서브커맨드를 만든 이유이기도 하다 (§8):

> 본문만 저장하면 나중에 판정 결과를 봐도 원래 공고로 돌아갈 방법이 없어서
> 배치 모드가 무용지물이 된다.

**URL 없는 배치 출력은 무용지물이라는 뜻이다.**

## 원인

컬럼 폭에 **상한이 없다.** 각 컬럼이 내용 최대 길이만큼 넓어진다.

- `no-frontmatter.txt` (18자)가 COMPANY 폭을 18로 밀었다
- `Plain Posting Without Frontmatter` (33자)가 TITLE 폭을 33으로 밀었다

앞 네 컬럼만으로 기본 폭 100을 채워서 URL 생략 로직이 발동했다.
**§8의 "좁으면 URL부터 생략"은 정말 좁을 때를 위한 것이지, 기본 폭에서 발동할 규칙이 아니다.**

## 수정

### 1. 컬럼 최대 폭 상한

| 컬럼 | 최대 폭 |
|---|---|
| `VERDICT` | 7 (`REVIEW`가 가장 길다) |
| `COMPANY` | **16** |
| `TITLE` | **24** |
| `REASON` | **22** |
| `URL` | 남은 공간 전부 |

- 내용이 상한보다 짧으면 **실제 내용 길이를 쓴다** (지금처럼). 상한은 천장일 뿐이다
- 상한을 넘으면 자르고 `…`을 붙인다

§8 예시에서 역산한 값이다:
```
SKIP     Alten     Java Developer        Finnish required      linkedin.com/jobs/view/111
```

### 2. URL에 최소 폭을 보장한다

URL 컬럼에 **최소 20자**를 확보한다. 확보되면 URL을 표시하고, 길면 자르고 `…`을 붙인다.

앞 컬럼들이 상한까지 차도 `7+2+16+2+24+2+22+2 = 77`이므로,
기본 폭 100에서 URL에 23자가 남는다. **기본 폭에서는 URL이 항상 보여야 한다.**

### 3. 20자를 확보할 수 없을 때만 URL을 생략한다 (§8)

`--width 60` 같은 좁은 폭에서는 §8대로 URL 컬럼을 생략한다.
그 경우에도 **남은 컬럼들이 `…` 하나로 뭉개지지 않아야 한다.**

현재 좁은 폭 출력이 이렇다:
```
VERDICT  COMPANY             TITLE                              …
SKIP     Alten               Java Developer                     …
```

URL을 생략했으면 그 자리에 `…`를 남기지 말고 **컬럼 자체를 없애라.**
그리고 남은 폭에 맞춰 TITLE과 REASON을 줄여라.

### 4. URL이 없는 공고

프론트매터에 `url`이 없으면 그 칸은 **빈칸**으로 둔다.
모든 공고에 URL이 없으면 URL 컬럼 전체를 생략해도 된다.

## 테스트

기존 배치 테스트를 유지하면서 추가하라:

1. **기본 폭(100)에서 URL 컬럼이 출력된다** ← 핵심
2. COMPANY가 16자를 넘으면 잘리고 `…`이 붙는다
3. TITLE이 24자를 넘으면 잘리고 `…`이 붙는다
4. 긴 COMPANY/TITLE이 있어도 **URL이 여전히 표시된다** ← 재현 케이스
5. `--width 60`에서 URL 컬럼이 사라지고, **남은 컬럼에 `…` 잔재가 없다**
6. URL이 없는 공고는 그 칸이 빈칸이다
7. 모든 공고에 URL이 없으면 URL 컬럼이 생략된다
8. 스킴(`https://`)이 생략되어 표시된다 (회귀 방지)

## 수정 금지

- `CLAUDE.md`, `docs/adr/`, `_reviews/`, `_briefs/` 기존 파일
- **`core/` 전체**
- `core/src/test/resources/golden/`
- 단일 판정의 출력·종료 코드·`--json` 스키마
- **배치 `--json` 출력** — JSON에는 컬럼 개념이 없다. 건드리지 마라
- `save` / `open` / `init`
- `git commit` / `git push` 금지

## 완료 조건

1. `./gradlew build` 성공
2. **기존 188개 테스트가 그대로 통과**
3. 위 8개 테스트가 통과
4. `./gradlew :cli:nativeCompile` 성공, 단일 파일 유지
5. **재현 케이스를 네이티브 바이너리로 다시 실행해 URL이 보이는 출력을 보고서에 붙인다**

## 보고서

`_briefs/task-7.5-report.md`

```
[Codex 결과 보고서] Task 7.5

1. 컬럼 폭 계산을 어떻게 바꿨는가
2. 변경한 파일 목록
3. **수정 전후 배치 출력 비교 (네이티브 바이너리, 기본 폭 + --width 60)**  ← 반드시 붙일 것
4. 테스트 결과 (총 개수, 기존 188개 유지 확인)
5. 스스로 결정한 것 + 근거
6. 다음 추천 작업
```

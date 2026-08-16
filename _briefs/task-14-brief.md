# [Codex 작업지시서] Task 14 — "considered as an advantage" 계열 오탐 수정

## 목표

실사용 중 **지원 가능한 공고가 SKIP된** 오탐을 발견했다. 가장 나쁜 유형의 오류다.

## 재현 (실제 공고에서 사용자가 만난 문장)

```
$ jc
VERDICT: SKIP
  ✗ Language    Finnish required
                "fluent Finnish skills are considered as an advantage."
```

`considered as an advantage`는 **우대**다. 핀란드어가 없어도 지원할 수 있는 공고인데
하드 필터로 잘라냈다.

분류기 직접 호출로 확인한 결과, 같은 계열이 전부 오탐이다:

```
[REQUIRED] Fluent Finnish skills are considered as an advantage.   ← 사용자가 만난 것
[REQUIRED] Finnish is considered a plus.
[REQUIRED] Finnish skills are seen as an advantage.
[REQUIRED] Finnish is regarded as a benefit.
[REQUIRED] Finnish would be considered an asset.
[REQUIRED] Knowledge of Finnish counts as a plus.
```

전부 `NICE`여야 한다.

## 원인 두 가지

### 1. `동사 + as + 명사` 패턴이 없다

Task 13에서 `(?:is|are)\s+(?:a|an)\s+(?:plus|bonus|advantage|asset)`를 추가했지만,
`are considered as an advantage`는 `are` 다음에 `considered as`가 와서 매칭되지 않는다.

### 2. `fluent`가 우대 서술을 이긴다

`fluent`가 필수 어조 마커라서, 문장이 명시적으로 우대를 진술해도 `REQUIRED`가 된다.

**`fluent`, `native`, `proficiency` 같은 표현은 언어 "수준"을 말하는 수식어이지
강제성 자체가 아니다.** 강제성은 `required`, `must`, `mandatory` 같은 서술에서 온다.

## 수정

### 1. 우대 관용구 정규식 확장

아래 형태를 모두 잡아라:

```
(is|are|would be|will be|can be)? (considered|seen|regarded|viewed|counted|treated) (as)? (a|an)? <명사>
counts as (a|an) <명사>
```

`<명사>`: `plus`, `bonus`, `advantage`, `asset`, `benefit`, `merit`, `strength`

Task 13에서 만든 기존 정규식(`is/are a plus` 등)은 **유지**하라.

### 2. 명시적 우대 관용구는 수식어형 필수 마커를 이긴다

한 절에 **명시적 우대 관용구**와 **수식어형 필수 마커**(`fluent`, `native`,
`proficiency in`, `working proficiency`)가 함께 있으면 → **`NICE`**

**단, 서술형 필수 마커**(`required`, `must`, `mandatory`, `essential`,
`we expect`, `you will need`)와 함께 있으면 → **`AMBIGUOUS`**
(진짜 신호 충돌이므로 단정하지 않는다)

구현 방법은 네가 정하되, **필수 어조 마커를 "수식어형"과 "서술형"으로 나누는 것**이
가장 단순할 것이다. 다른 방법이 낫다고 판단하면 그렇게 하고 근거를 보고서에 적어라.

## 반드시 지킬 회귀

아래는 지금처럼 동작해야 한다:

```
[REQUIRED] Fluent Finnish is required.
[REQUIRED] Fluent Finnish and English are required.
[REQUIRED] We expect fluent Finnish.
[REQUIRED] Java is mandatory.
[NICE    ] Finnish is a plus.                        (Task 13)
[NICE    ] Kotlin and Kubernetes are a plus.         (Task 13)
[NEGATED ] Finnish is not required.
[AMBIGUOUS] Working knowledge of Finnish.
[AMBIGUOUS] Ideally 5+ years of experience.
```

**기존 208개 테스트와 골든 31개가 전부 통과해야 한다.**

## 추가할 것

1. 분류기 테스트: 위 재현 6문장 전부 `NICE`
2. 충돌 케이스 테스트: `"Fluent Finnish is required, though Swedish is considered a plus."`
   같은 문장에서 어떻게 되는지 (네 판단대로 하고 보고서에 적어라)
3. **골든 케이스 `32-finnish-considered-advantage`**
   - 사용자가 만난 실제 문장을 포함한 공고
   - `LANGUAGE` finding이 `PASS`, verdict가 `SKIP`이 **아니어야** 한다
   - `evidenceContains`로 해당 문장을 고정하라

## 수정 금지

- `CLAUDE.md`, `docs/adr/`, `_reviews/`, `_briefs/` 기존 파일
- `.github/workflows/`, `cli/` 전체
- 기존 골든 `01-*` ~ `31-*`
- `core/rule/`의 판정 규칙과 `AtsChecker` — 이번 대상은 **어조 분석/분류기**뿐이다
- `git commit` / `git push` 금지

## 완료 조건

1. `./gradlew build` 성공
2. 재현 6문장이 전부 `NICE`
3. 위 회귀 목록이 전부 그대로
4. 기존 208개 + 신규 테스트 통과, **골든 32개 통과**
5. `core` 런타임 의존성 0개

## 보고서

`_briefs/task-14-report.md`

```
[Codex 결과 보고서] Task 14
1. 정규식을 어떻게 확장했는가
2. 수식어형/서술형 필수 마커를 어떻게 구분했는가 (구분 목록 포함)
3. 충돌 케이스를 어떻게 처리하기로 했고 왜인가
4. 재현 6문장 + 회귀 9문장 전후 출력
5. 테스트 결과 (총 개수, 골든 32개)
6. 남은 미탐/오탐 리스크 — 실제 공고에서 더 나올 법한 우대 표현
```

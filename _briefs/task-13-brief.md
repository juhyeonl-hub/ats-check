# [Codex 작업지시서] Task 13 — 우대 어조 사전의 복수형 미탐 수정

## 목표

실제 공고로 도구를 써보다 미탐을 발견했다. 어조 사전을 보강한다.

## 재현 (설치된 v0.1.0 바이너리)

```
$ printf 'Developer\nWolt\n\nRequirements:\nJava, Spring Boot and SQL.\nREST APIs.\nKotlin and Kubernetes are a plus.\n' | ats-check

VERDICT: APPLY
  MISSING (required)   Kotlin, Kubernetes     ← 우대인데 필수로 분류됨
  MATCHED              Java, Spring Boot, SQL, REST
```

`Kotlin and Kubernetes are a plus.`는 **우대**로 읽혀야 한다.

## 원인

어조 사전의 우대 표현에 **`is a plus`만 있고 `are a plus`가 없다.**
주어가 복수면 동사가 `are`가 되는데 그 형태를 못 잡는다.
그래서 어조 신호가 없는 것으로 처리되고, `Requirements:` 섹션 안이라는 이유만으로
`REQUIRED`가 된다.

## 수정

우대 어조 사전에 아래 변형을 추가하라. **단수/복수를 모두** 다뤄야 한다.

```
are a plus          is a plus (기존)
are a bonus         is a bonus
are an advantage    is an advantage
are an asset        is an asset
are appreciated     is appreciated
are beneficial      is beneficial (기존 beneficial)
are welcome         is welcome
are nice to have    is nice to have
```

**정규식으로 `(is|are)\s+(a|an)\s+(plus|bonus|advantage|asset)` 형태를 한 번에 처리하는 편이
사전을 늘리는 것보다 유지보수하기 쉽다.** 어느 쪽이든 네가 판단해서 구현하고,
왜 그렇게 했는지 보고서에 적어라.

같은 방식으로 **필수 어조**에도 복수형 누락이 있는지 점검하라.
예: `is required` / `are required`(기존 `required`로 이미 잡힘), `is mandatory` / `are mandatory`.
이미 잡히고 있다면 건드리지 마라. **없는 문제를 만들지 마라.**

## 회귀 방지 — 반드시 지켜라

- **기존 206개 테스트가 전부 통과**해야 한다
- **골든 30개가 전부 통과**해야 한다
- 특히 `02-finnish-nice-to-have`(`Finnish is a plus`)가 깨지면 안 된다

## 추가할 것

1. `ToneAnalyzer`/`SectionClassifier` 테스트에 복수형 케이스
   - `"Kotlin and Kubernetes are a plus."` → `NICE`
   - `"Kotlin is a plus."` → `NICE` (회귀 방지)
   - `"Docker and Terraform are an advantage."` → `NICE`
2. **골든 케이스 `31-plural-nice-to-have`** 신설
   - 재현 공고를 그대로 쓰라 (`Requirements:` 안에 `... are a plus.`가 있는 형태)
   - `missingNice`에 해당 스킬이 들어가고 `missingRequired`에는 없어야 한다
   - `evidenceContains`도 넣어라

## 수정 금지

- `CLAUDE.md`, `docs/adr/`, `_reviews/`, `_briefs/` 기존 파일
- `.github/workflows/` 전체
- `cli/` 전체
- 기존 골든 케이스 `01-*` ~ `30-*`
- 판정 규칙(`core/rule/`)과 `AtsChecker` — 이번 수정 대상은 **어조 사전/분석기**뿐이다
- `git commit` / `git push` 금지

## 완료 조건

1. `./gradlew build` 성공
2. 기존 206개 + 신규 테스트 전부 통과, **골든 31개 통과**
3. 재현 케이스가 `missingNice`로 분류된다
4. `core` 런타임 의존성 0개

## 보고서

`_briefs/task-13-report.md`

```
[Codex 결과 보고서] Task 13
1. 사전 확장 vs 정규식 — 어느 쪽을 택했고 왜인가
2. 필수 어조 쪽 점검 결과 (문제가 있었나 없었나)
3. 재현 케이스 수정 전후 출력
4. 테스트 결과 (총 개수, 골든 31개 확인)
5. 남은 미탐 리스크 — 실제 공고에서 더 나올 법한 변형
```

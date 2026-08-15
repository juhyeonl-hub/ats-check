# [Codex 작업지시서] Task 3.5 — 스킬 매칭 정밀도 수정

## 목표

`SkillRule`의 오탐 4종과 미탐 1종을 고친다.

**왜 지금인가:** 다음 태스크가 골든 파일 20개다. 골든 파일은 회귀 방지의 기준선이므로,
**잘못된 동작을 기준선으로 굳히면 나중에 되돌리기 어렵다.**

심각도는 낮다 — 스킬 갭은 verdict를 바꾸지 않으므로 SKIP/APPLY 오류는 없다.
그러나 `MISSING (required): go`가 출력되면 사용자가 혼란스럽고, 출력 품질이 제품 신뢰다.

## 재현 (실제 실행 출력)

```
--- 오탐 ---
"We can go fast and ship often."                  -> [go]          ❌ 동사 go
"You will go through a structured onboarding."    -> [go]          ❌ 동사 go
"A swift and clear communication style."          -> [swift]       ❌ 형용사 swift
"You are a rust-free engineer with clean habits." -> [rust]        ❌ 하이픈 결합
"We work in a scala-ble architecture."            -> [scala]       ❌ 하이픈 결합

--- 미탐 ---
"Golang microservices experience."                -> [microservices] ❌ golang이 go로 인식 안 됨

--- 정상 (회귀 금지) ---
"Experience with Go and Rust."                    -> [go, rust]    ✅
"Swift and Objective-C for iOS."                  -> [swift, objective-c] ✅
"C++ and C# experience."                          -> [c++, c#]     ✅
"Spring Boot, not just Spring."                   -> [spring boot, spring] ✅
"Node.js and TypeScript."                         -> [node.js, typescript] ✅
"Experience with the C programming language."     -> [c]           ✅ (진짜 C 언어)
```

## 수정 1 — 하이픈·특수문자 경계 강화

`rust-free`, `scala-ble`처럼 기술명이 다른 단어와 하이픈으로 붙으면 **매칭하지 않는다.**

현재 단어 경계(`\b`)는 하이픈을 경계로 보기 때문에 `rust-free`에서 `rust`가 걸린다.
매칭 앞뒤 문자가 `[A-Za-z0-9+#._-]` 중 하나면 **거부**하도록 바꿔라.

**단, 사전 항목 자체에 특수문자가 있는 경우는 예외다:**
`c++`, `c#`, `.net`, `node.js`, `next.js`, `nest.js`, `objective-c`, `event-driven`,
`gitlab ci`, `sql server` 등은 지금처럼 정확히 매칭되어야 한다.

## 수정 2 — 모호한 기술명은 문맥을 요구한다

아래 항목은 **일상 영단어와 충돌**한다:

```
go, swift, rust, scala, c, r, dart, julia, groovy, elixir
```

이 항목들은 같은 절에 **다음 중 하나**가 있을 때만 스킬로 인정한다:

**(a) 기술 문맥 단어**
```
programming, language, lang, framework, stack, codebase, runtime,
backend, frontend, fullstack, microservice, microservices, api, apis,
service, services, development, developing, code, coding
```

**(b) `experience with` / `proficient in` / `knowledge of` / `familiar with` /
`skills in` / `expertise in` 패턴 뒤에 나오는 경우**

**(c) 같은 절에 모호하지 않은 다른 기술명이 있는 경우**
(예: `"Swift and Objective-C for iOS"` — `objective-c`가 명확하므로 `swift`도 인정)

**`engineer`, `developer`는 문맥 단어에 넣지 마라.** 너무 흔해서
`"You are a rust-free engineer"`가 통과해버린다.

## 수정 3 — 별칭 추가

| 별칭 | 정규형 |
|---|---|
| `golang` | `go` |
| `k8s` | `kubernetes` |
| `postgres` | `postgresql` |
| `dotnet`, `.net core` | `.net` |
| `cpp` | `c++` |
| `csharp` | `c#` |
| `node` | `node.js` |

**별칭은 모호하지 않으므로 수정 2의 문맥 요구를 적용하지 않는다.**
`golang`은 언제나 Go 언어다. 출력에는 **정규형**을 쓴다 (`golang` → `go`).

`js`, `ts` 같은 위험한 축약은 **넣지 마라.** 오탐이 더 커진다.

## 테스트

기존 89개를 유지하면서 추가하라. **위 재현 케이스를 그대로 쓰라:**

1. `"We can go fast and ship often."` → `go` 없음
2. `"You will go through a structured onboarding."` → `go` 없음
3. `"A swift and clear communication style."` → `swift` 없음
4. `"You are a rust-free engineer with clean habits."` → `rust` 없음
5. `"We work in a scala-ble architecture."` → `scala` 없음
6. `"Golang microservices experience."` → `go` 포함
7. `"Experience with Go and Rust."` → `go`, `rust` 포함 (회귀 방지)
8. `"Swift and Objective-C for iOS."` → `swift`, `objective-c` 포함 (문맥 규칙 (c))
9. `"C++ and C# experience."` → `c++`, `c#` 포함 (회귀 방지)
10. `"Experience with the C programming language."` → `c` 포함 (문맥 규칙 (a))
11. `"Spring Boot, not just Spring."` → `spring boot`, `spring` 포함 (회귀 방지)
12. `"k8s and postgres experience"` → `kubernetes`, `postgresql`로 **정규화되어** 출력
13. `"Node.js and TypeScript."` → `node.js`, `typescript` 포함 (회귀 방지)

## 수정 금지

- `CLAUDE.md`, `docs/adr/`, `_reviews/`, `_briefs/` 기존 파일
- `cli/` 전체
- `core/section/`, `core/rule/`의 **다른 규칙 3개**(Language/ExperienceYears/Degree), `AtsChecker`
  — 이번 수정 대상은 `SkillRule`(과 필요하면 스킬 사전 상수)뿐이다
- `core`에 의존성 추가 금지
- `git commit` / `git push` 금지

## 완료 조건

1. `./gradlew build` 성공
2. `./gradlew :core:test` 통과. **기존 89개가 그대로 통과**
3. 위 13개 테스트 요구가 모두 구현됨
4. `core` 런타임 의존성 0개

## 보고서

`_briefs/task-3.5-report.md`

```
[Codex 결과 보고서] Task 3.5

1. 수정 방법 (경계 강화 / 문맥 요구 / 별칭) 각각
2. 변경한 파일 목록
3. 재현 케이스 수정 전후 비교 (실제 출력)
4. 테스트 결과 (총 개수, 기존 89개 유지 확인)
5. 스스로 결정한 것 + 근거
6. 남은 오탐/미탐 리스크
7. 다음 추천 작업
```

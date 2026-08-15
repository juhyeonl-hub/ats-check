[Codex 결과 보고서] Task 3.5

1. 수정 방법

- 경계 강화: 기존 정규식 단어 경계 의존을 제거하고, 후보 매칭 후 앞뒤 문자를 직접 검사하도록 바꿨다. 앞뒤에 ASCII 영문/숫자, `+`, `#`, `_`, `-`가 붙으면 거부한다. `.`은 `Node.js.`나 `Spring.` 같은 문장부호 회귀를 막기 위해, 다음/이전 문자가 ASCII 영문/숫자인 토큰 결합 상황에서만 거부한다.
- 문맥 요구: `go`, `swift`, `rust`, `scala`, `c`, `r`, `dart`, `julia`, `groovy`, `elixir`는 모호어로 분리했다. 모호어는 같은 절에 기술 문맥 단어가 있거나, `experience with` 등 명시 패턴 뒤에 있거나, 같은 절에 모호하지 않은 다른 기술 후보가 있을 때만 통과한다. `engineer`, `developer`는 문맥 단어에 넣지 않았다.
- 별칭: `golang -> go`, `k8s -> kubernetes`, `postgres -> postgresql`, `dotnet -> .net`, `.net core -> .net`, `cpp -> c++`, `csharp -> c#`, `node -> node.js`를 추가했다. 별칭 후보는 모호어 문맥 요구를 적용하지 않고, 출력 및 프로필 비교는 정규형으로 처리한다.

2. 변경한 파일 목록

- `core/src/main/java/dev/juhyeonl/atscheck/core/rule/SkillRule.java`
- `core/src/test/java/dev/juhyeonl/atscheck/core/rule/SkillRuleTest.java`
- `_briefs/task-3.5-report.md`

3. 재현 케이스 수정 전후 비교 (실제 출력)

수정 전:

```text
We can go fast and ship often. -> [go]
You will go through a structured onboarding. -> [go]
A swift and clear communication style. -> [swift]
You are a rust-free engineer with clean habits. -> [rust]
We work in a scala-ble architecture. -> [scala]
Golang microservices experience. -> [microservices]
Experience with Go and Rust. -> [go, rust]
Swift and Objective-C for iOS. -> [swift, objective-c]
C++ and C# experience. -> [c++, c#]
Experience with the C programming language. -> [c]
Spring Boot, not just Spring. -> [spring boot, spring]
k8s and postgres experience -> []
Node.js and TypeScript. -> [node.js, typescript]
```

수정 후:

```text
We can go fast and ship often. -> []
You will go through a structured onboarding. -> []
A swift and clear communication style. -> []
You are a rust-free engineer with clean habits. -> []
We work in a scala-ble architecture. -> []
Golang microservices experience. -> [go, microservices]
Experience with Go and Rust. -> [go, rust]
Swift and Objective-C for iOS. -> [swift, objective-c]
C++ and C# experience. -> [c++, c#]
Experience with the C programming language. -> [c]
Spring Boot, not just Spring. -> [spring boot, spring]
k8s and postgres experience -> [kubernetes, postgresql]
Node.js and TypeScript. -> [node.js, typescript]
```

4. 테스트 결과

- `./gradlew :core:test`: 성공
- `./gradlew build`: 성공
- 테스트 리포트 기준 `core` 테스트 총 102개, 실패 0개, 에러 0개, 스킵 0개
- 기존 89개 테스트에 Task 3.5 명시 케이스 13개를 추가해 102개 통과 확인
- `./gradlew :core:dependencies --configuration runtimeClasspath`: `No dependencies`

5. 스스로 결정한 것 + 근거

- `.` 경계는 무조건 거부하지 않고 토큰 결합일 때만 거부했다. 지시서의 특수문자 경계 강화 의도는 `node.jsx` 같은 결합 토큰의 부분 매칭 방지로 해석했고, 명시 회귀 케이스인 `Spring Boot, not just Spring.`과 `Node.js and TypeScript.`의 문장 끝 마침표 매칭을 유지해야 했기 때문이다.
- 별칭은 사전 본문에 섞지 않고 별도 후보 패턴으로 두었다. 이렇게 해야 `golang`은 문맥 없이 `go`로 정규화하면서도, 직접 표기 `go`에는 모호어 문맥 규칙을 계속 적용할 수 있다.
- 프로필 스킬도 별칭 정규화를 적용했다. 공고에서 `golang`이 `go`로 출력될 때, 프로필이 `go` 또는 `golang` 어느 쪽을 갖고 있어도 같은 정규형으로 비교하는 편이 출력 정규화와 일관된다.

6. 남은 오탐/미탐 리스크

- 문맥 단어가 같은 절에 있으면 모호어가 통과하므로, `"go fast and build APIs"` 같은 문장은 여전히 `go` 오탐 가능성이 있다. 이는 지시서의 같은 절 기술 문맥 규칙을 따른 결과다.
- `"Scala experience"`처럼 기술적으로 자연스럽지만 지시서의 문맥 단어 또는 명시 패턴에 걸리지 않는 문장은 미탐 가능성이 있다. `experience` 단독은 요청된 문맥 단어가 아니어서 추가하지 않았다.
- `rest`, `spring`, `shell` 같은 사전 내 비모호 처리 항목은 이번 태스크 범위 밖이라 기존 동작을 유지했다.

7. 다음 추천 작업

- Task 4의 골든 파일 20개 작성 전에 이번 13개 문장을 포함한 스킬 매칭 골든 케이스를 먼저 기준선에 반영한다.
- 실제 공고 샘플에서 모호어 문맥 규칙의 오탐/미탐 빈도를 확인한 뒤, 필요하면 절 내부 거리 제한이나 리스트 패턴 인식을 별도 태스크로 분리한다.

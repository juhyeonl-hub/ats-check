[Codex 결과 보고서] Task 1

1. 수행한 작업 요약

- 공고 텍스트를 줄/문장 단위 절로 분할하고, 섹션 헤더 상태를 추적하며, 절별 어조/완화 표현을 분석하는 `core` 섹션 분류 모듈을 추가했다.
- `REQUIRED`, `NICE`, `AMBIGUOUS`, `UNKNOWN` 결과와 섹션/신호 근거를 불변 모델로 보존하도록 구현했다.
- 결합 규칙 표 10개 행, §6 예시 3개, 절 분할/헤더 지속/단어 경계/약어/빈 입력을 JUnit 5 + AssertJ 테스트로 덮었다.
- `core` 런타임 의존성은 추가하지 않았고, 파일 I/O/네트워크/`System.out` 사용도 없다.

2. 생성한 파일 목록

- `core/src/main/java/dev/juhyeonl/atscheck/core/model/RequirementLevel.java`
- `core/src/main/java/dev/juhyeonl/atscheck/core/model/SectionKind.java`
- `core/src/main/java/dev/juhyeonl/atscheck/core/model/Signal.java`
- `core/src/main/java/dev/juhyeonl/atscheck/core/model/Clause.java`
- `core/src/main/java/dev/juhyeonl/atscheck/core/section/ClauseSplitter.java`
- `core/src/main/java/dev/juhyeonl/atscheck/core/section/SectionScanner.java`
- `core/src/main/java/dev/juhyeonl/atscheck/core/section/ToneAnalyzer.java`
- `core/src/main/java/dev/juhyeonl/atscheck/core/section/SectionClassifier.java`
- `core/src/test/java/dev/juhyeonl/atscheck/core/section/SectionClassifierTest.java`
- `_briefs/task-1-report.md`

3. 공개 API 시그니처 (SectionClassifier)

```java
public static List<Clause> classify(String jobText)
```

- 위치: `dev.juhyeonl.atscheck.core.section.SectionClassifier`
- `null` 입력은 `NullPointerException("jobText")`로 거부한다.
- 빈 문자열/공백-only 입력은 `List.of()`를 반환한다.
- 반환된 `Clause`와 내부 `signals` 목록은 불변이다.

4. 결합 규칙 표를 어떻게 구현했는가 + 각 행을 덮는 테스트 이름

`SectionClassifier.combine(SectionKind section, RequirementLevel tone, boolean hasHedge)`에서 결합 규칙을 한 곳에 모았다.

- 같은 방향의 섹션/어조 신호는 해당 레벨로 확정한다.
- `REQUIRED_SECTION + NICE tone`, `NICE_SECTION + REQUIRED tone`은 `AMBIGUOUS`로 둔다.
- hedge가 있고 섹션이 `REQUIRED_SECTION` 또는 `NONE`, 어조가 `REQUIRED` 또는 `UNKNOWN`이면 `AMBIGUOUS`로 낮춘다.
- 어조가 `NICE`이고 강한 섹션 충돌이 없으면 hedge가 있어도 `NICE`로 둔다.

| 결합 규칙 행 | 테스트 이름 |
|---|---|
| REQUIRED 섹션 + REQUIRED 어조 + hedge 없음 => REQUIRED | `REQUIRED 섹션과 REQUIRED 어조에 hedge가 없으면 REQUIRED로 분류한다` |
| REQUIRED 섹션 + 어조 없음 + hedge 없음 => REQUIRED | `REQUIRED 섹션과 어조 신호 없음에 hedge가 없으면 REQUIRED로 분류한다` |
| REQUIRED 섹션 + NICE 어조 => AMBIGUOUS | `REQUIRED 섹션과 NICE 어조가 충돌하면 AMBIGUOUS로 분류한다` |
| NICE 섹션 + NICE 어조 => NICE | `NICE 섹션과 NICE 어조는 NICE로 분류한다` |
| NICE 섹션 + 어조 없음 => NICE | `NICE 섹션과 어조 신호 없음은 NICE로 분류한다` |
| NICE 섹션 + REQUIRED 어조 + hedge 없음 => AMBIGUOUS | `NICE 섹션과 REQUIRED 어조가 충돌하면 AMBIGUOUS로 분류한다` |
| 섹션 없음 + REQUIRED 어조 + hedge 없음 => REQUIRED | `섹션 신호 없음과 REQUIRED 어조에 hedge가 없으면 REQUIRED로 분류한다` |
| 섹션 없음 + NICE 어조 => NICE | `섹션 신호 없음과 NICE 어조는 NICE로 분류한다` |
| 섹션 없음 + 어조 없음 + hedge 없음 => UNKNOWN | `섹션 신호와 어조 신호와 hedge가 모두 없으면 UNKNOWN으로 분류한다` |
| REQUIRED 또는 없음 섹션 + REQUIRED 또는 없음 어조 + hedge 있음 => AMBIGUOUS | `REQUIRED 또는 없음 섹션에서 REQUIRED 또는 없음 어조에 hedge가 있으면 AMBIGUOUS로 분류한다` |

추가로 `어조가 NICE이면 hedge가 있어도 NICE로 분류한다` 테스트로 NICE tone + hedge 예외를 검증했다.

5. 실행한 테스트와 실제 출력 (테스트 개수 포함)

```text
$ source "$HOME/.sdkman/bin/sdkman-init.sh" && ./gradlew :core:test
> Task :core:compileJava
> Task :core:compileTestJava
> Task :core:test
BUILD SUCCESSFUL in 2s
3 actionable tasks: 3 executed
```

Gradle XML 결과:

```text
SectionClassifierTest: tests="18" skipped="0" failures="0" errors="0"
AtsCheckerTest:        tests="2"  skipped="0" failures="0" errors="0"
core 합계: 20개 테스트 성공
```

전체 빌드:

```text
$ source "$HOME/.sdkman/bin/sdkman-init.sh" && ./gradlew build
BUILD SUCCESSFUL in 1s
9 actionable tasks: 4 executed, 5 up-to-date
```

런타임 의존성 확인:

```text
$ source "$HOME/.sdkman/bin/sdkman-init.sh" && ./gradlew :core:dependencies --configuration runtimeClasspath
runtimeClasspath - Runtime classpath of source set 'main'.
No dependencies
BUILD SUCCESSFUL in 4s
```

6. 사양이 모호해서 스스로 결정한 것 + 그 근거

- `SectionClassifier.classify`는 정적 메서드로 만들었다. 지시서가 `SectionClassifier.classify(String jobText) -> List<Clause>`만 공개 API로 충분하다고 했으므로, 인스턴스 생성 API를 열지 않는 쪽이 더 좁고 단순하다.
- `ClauseSplitter`, `SectionScanner`, `ToneAnalyzer`는 package-private으로 두었다. 다음 태스크에서 내부 구현을 갈아끼우더라도 외부 계약은 `SectionClassifier.classify` 하나로 유지하기 위해서다.
- 같은 절에서 REQUIRED 어조와 NICE 어조가 동시에 잡히면 `AMBIGUOUS`로 처리했다. 결합 규칙 표에는 별도 행이 없지만, §6의 "두 신호가 어긋나면 단정하지 않는다"는 원칙에 맞춘 결정이다.
- REQUIRED 섹션에서 NICE 어조와 hedge가 같이 잡히는 경우는 `AMBIGUOUS`로 본다. 표의 `REQUIRED | NICE | - => AMBIGUOUS` 행이 hedge 여부를 무관하게 충돌로 다룬다고 해석했다. "어조가 NICE인데 hedge가 있으면 그냥 NICE"는 강한 섹션 충돌이 없는 경우에만 적용했다.
- 새 헤더를 만날 때까지 섹션 상태를 유지하되, 사전에 없는 일반 헤더(`Benefits:`, `About us:` 등)는 상태를 `NONE`으로 리셋하지 않는다. 지시서가 필수/우대 헤더 사전만 제공했고 "헤더를 만나면 상태가 바뀐다"고 했기 때문이다.
- 숫자 불릿은 `1. Java`처럼 숫자+점 뒤에 공백이 있는 형태만 제거한다. `2.5 years` 같은 수치 표현을 불릿으로 오인해 텍스트를 훼손하지 않기 위한 결정이다.
- 약어(`e.g.`, `i.e.`, `etc.`, `vs.`, `Inc.`, `Ltd.`, `Sr.`, `Jr.`)는 항상 문장 경계가 아닌 것으로 처리한다. 이 약어가 실제 문장 끝에 오는 경우에는 under-split이 생길 수 있지만, 지시서의 우선 요구인 약어 오분할 방지를 택했다.
- 문장 분할은 `.`, `!`, `?` 뒤에 공백 또는 줄 끝이 있는 경우만 대상으로 했다. 세미콜론, 콜론, 괄호 등은 절 경계로 보지 않았다. 의존성 0 조건에서 과도한 NLP 규칙을 넣지 않기 위해서다.
- "짧은 줄이 헤더 표현으로 시작하면 헤더 후보" 규칙을 그대로 적용했다. 따라서 `Requirements include Java`처럼 60자 이하이고 헤더 표현으로 시작하는 줄은 절이 아니라 헤더로 제외될 수 있다.

7. 실패했거나 확인 못 한 것

- 실패한 테스트는 없다.
- `core` 런타임 의존성 0개는 Gradle `runtimeClasspath`로 확인했다.
- 실제 대규모 공고 corpus에 대한 오탐/미탐률은 이번 태스크 범위 밖이라 확인하지 않았다.

8. 리스크 (오탐/미탐이 예상되는 공고 패턴)

- 사전에 없는 헤더 아래에서는 이전 섹션 상태가 과도하게 지속될 수 있다.
- `Requirements include...`처럼 헤더 단어로 시작하는 짧은 내용 문장은 헤더로 오탐되어 절 목록에서 빠질 수 있다.
- 약어가 실제 문장 끝인 경우 다음 문장과 합쳐질 수 있다.
- `1.Java`처럼 숫자 불릿 뒤 공백이 없는 형식은 불릿 제거가 되지 않는다.
- `must-have`, `native-level`처럼 하이픈으로 이어진 표현은 단어 경계상 신호로 잡힐 수 있다. 이 경우 대체로 의미상 맞지만 공고 문맥에 따라 오탐 가능성이 있다.
- 사전 기반 매칭이므로 동의어, 부정문(`not required`), 복합 조건(`Finnish is not mandatory but...`)은 정확히 처리하지 못한다.

9. 다음 추천 작업

- 다음 태스크의 언어/연차/학위/스킬 판정은 `Clause.level()`이 `REQUIRED`인 절만 hard fail 후보로 보고, `AMBIGUOUS`는 REVIEW 경로로 분리하는 방식이 좋다.
- 실제 샘플 공고를 더 모아 헤더 사전과 hedge 사전을 확장할 필요가 있다.
- 부정 표현(`not required`, `not mandatory`)은 별도 신호로 설계하지 않으면 현재 REQUIRED 어조에 걸릴 가능성이 높다.

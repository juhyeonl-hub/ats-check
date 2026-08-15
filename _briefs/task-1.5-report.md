[Codex 결과 보고서] Task 1.5

1. 수정한 결함과 수정 방법 (A/B/C 각각)

- A. 부정문 오탐
  - `RequirementLevel.NEGATED`를 추가했다.
  - `ToneAnalyzer`에 부정 마커 사전을 추가하고, 부정 마커를 REQUIRED/NICE 어조보다 먼저 검사하도록 했다.
  - 부정 마커가 있으면 기존 어조 신호는 추가하지 않고 `NEGATED`를 반환한다.
  - 같은 절에 `but`, `however`, `although`, `though`, `while`가 있으면 부정 범위를 단정하지 않고 `AMBIGUOUS`로 반환한다.
  - `SectionClassifier.combine`에서 `NEGATED`를 섹션 상태보다 우선 반환하도록 했다.

- B. 미지/중립 헤더 아래 섹션 상태 지속
  - `SectionScanner`에 중립 헤더 사전을 추가했다.
  - 중립 헤더 또는 사전에 없는 콜론 종료 헤더를 만나면 `SectionKind.NONE`으로 리셋한다.
  - 헤더로 판정된 줄은 기존처럼 절 목록에 포함하지 않는다.

- C. 헤더 오탐
  - 헤더 판정을 형태 기반으로 강화했다.
  - 헤더는 길이 60자 이하, 문장 종결 부호(`.`, `!`, `?`)로 끝나지 않음, 콜론 종료 또는 줄 전체가 헤더 사전 표현과 일치하는 경우에만 인정한다.
  - `Requirements include Java and Kotlin.`과 `We expect you to ship code.`는 마침표로 끝나므로 헤더가 아니라 절로 분류된다.
  - `we expect`를 REQUIRED 어조 사전에 추가해 문장 절에서도 REQUIRED로 잡히도록 했다.

2. 변경한 파일 목록

- `core/src/main/java/dev/juhyeonl/atscheck/core/model/RequirementLevel.java`
- `core/src/main/java/dev/juhyeonl/atscheck/core/model/Signal.java`
- `core/src/main/java/dev/juhyeonl/atscheck/core/section/ToneAnalyzer.java`
- `core/src/main/java/dev/juhyeonl/atscheck/core/section/SectionClassifier.java`
- `core/src/main/java/dev/juhyeonl/atscheck/core/section/SectionScanner.java`
- `core/src/test/java/dev/juhyeonl/atscheck/core/section/SectionClassifierTest.java`
- `_briefs/task-1.5-report.md`

3. NEGATED 도입이 기존 결합 규칙에 미친 영향

- 기존 REQUIRED/NICE/AMBIGUOUS/UNKNOWN 결합 규칙은 바꾸지 않았다.
- `NEGATED`는 새 축으로만 추가했고, `combine` 초반에서 우선 반환한다.
- 따라서 `Requirements:` 섹션 안의 `Finnish is not required.`도 REQUIRED로 승격되지 않고 `NEGATED`로 유지된다.
- 기존 §6 세 예시는 그대로 통과한다.
  - `Fluent Finnish and English are required` -> `REQUIRED`
  - `Finnish is a plus` -> `NICE`
  - `Working knowledge of Finnish` -> `AMBIGUOUS`

4. 재현 케이스 A/B/C의 수정 전/후 출력 비교

수정 전:

```text
### A. 부정문
clauses=3
2 | Strong Java skills.                           | REQUIRED  | REQUIRED_SECTION
3 | Finnish is not required.                      | REQUIRED  | REQUIRED_SECTION
4 | English is mandatory.                         | REQUIRED  | REQUIRED_SECTION

### B. 미지 헤더 아래 섹션 상태 지속
clauses=4
2 | Java experience.                              | REQUIRED  | REQUIRED_SECTION
4 | Benefits:                                     | REQUIRED  | REQUIRED_SECTION
5 | Free Finnish lessons.                         | REQUIRED  | REQUIRED_SECTION
6 | Lunch benefit.                                | REQUIRED  | REQUIRED_SECTION

### C. 헤더 오탐
clauses=0
```

수정 후:

```text
### A. 부정문
clauses=3
2 | Strong Java skills.                           | REQUIRED  | REQUIRED_SECTION
3 | Finnish is not required.                      | NEGATED   | REQUIRED_SECTION
4 | English is mandatory.                         | REQUIRED  | REQUIRED_SECTION

### B. 미지 헤더 아래 섹션 상태 지속
clauses=3
2 | Java experience.                              | REQUIRED  | REQUIRED_SECTION
5 | Free Finnish lessons.                         | UNKNOWN   | NONE
6 | Lunch benefit.                                | UNKNOWN   | NONE

### C. 헤더 오탐
clauses=2
1 | Requirements include Java and Kotlin.         | UNKNOWN   | NONE
2 | We expect you to ship code.                   | REQUIRED  | NONE
```

5. 테스트 결과 (개수 포함, 기존 18개 유지 확인)

- `./gradlew :core:test` 성공
  - `SectionClassifierTest`: 32개 성공
  - 기존 18개 유지 + 신규 14개 추가
  - `AtsCheckerTest`: 2개 성공
  - core 전체 테스트: 34개 성공
- `./gradlew build` 성공
- `./gradlew :core:dependencies --configuration runtimeClasspath` 성공
  - `runtimeClasspath`: `No dependencies`

6. 스스로 결정한 것 + 근거

- `Signal.Type.NEGATION`을 추가했다. `NEGATED` 판정의 근거를 `Clause.signals()`에 남겨야 기존 신호 보존 설계와 맞기 때문이다.
- 중립/미지 헤더의 섹션 신호는 `null`로 두고 절에는 추가하지 않았다. 중립 헤더는 REQUIRED/NICE 판단 근거가 아니며, 요구사항도 리셋을 요구했기 때문이다.
- `Our Stack:`은 중립 사전에 포함되어 있지만, 요구사항의 "사전에 없는 미지 헤더도 NONE"까지 보호하려고 별도 테스트로 `Team Rituals:`를 추가했다.

7. 여전히 남아 있는 오탐/미탐 리스크

- 부정 마커는 명시 사전 기반이므로 `not strictly required`, `Finnish won't be required` 같은 변형은 아직 미탐될 수 있다.
- `while`는 시간 의미로 쓰인 경우에도 대조 접속사로 처리되어 `AMBIGUOUS`가 될 수 있다.
- 콜론으로 끝나는 짧은 줄은 사전에 없어도 헤더로 본다. `C++:` 같은 기술 라벨을 절로 보고 싶을 때는 미탐 가능성이 있다.
- 현재 출력 계층은 아직 구현 범위 밖이다. 이번 태스크에서는 분류 레벨과 신호까지만 반영했다.

8. 다음 추천 작업

- Task 2 판정 규칙에서 `NEGATED`를 명시적으로 처리해 "Finnish: explicitly not required" 같은 별도 출력 문구로 연결한다.
- 실제 공고 샘플을 추가해 복리후생의 `Finnish lessons`, `language courses`, `Finnish support` 문구가 SKIP으로 이어지지 않는지 통합 검증한다.

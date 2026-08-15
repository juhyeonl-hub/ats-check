[Codex 결과 보고서] Task 13

1. 사전 확장 vs 정규식 — 어느 쪽을 택했고 왜인가
- 정규식을 택했다.
- `(?:is|are)\s+(?:a|an)\s+(?:plus|bonus|advantage|asset)`로 단수/복수와 a/an 명사형 우대 표현을 한 번에 처리했다.
- `(?:is|are)\s+(?:appreciated|beneficial|welcome|nice\s+to\s+have)`도 별도 정규식으로 묶었다.
- 단순 사전 항목을 8개 이상 늘리는 것보다 변형 추가 위치가 명확하고, 이후 `counts as a plus` 같은 패턴을 별도 정규식으로 확장하기 쉽다.
- 기존 `NICE_TONES` 문자열 사전은 유지했다. 기존 `beneficial`, `appreciated`, `nice to have`, `bonus`류 동작을 불필요하게 줄이지 않기 위해서다.
- `SectionClassifier`에는 정규식으로 잡힌 명시적 우대 관용구만 `Requirements:` 섹션보다 우선하는 플래그를 전달했다. 그래서 `Ideally 5+ years...` 같은 기존 완곡 필수 후보는 계속 `AMBIGUOUS`로 남는다.

2. 필수 어조 쪽 점검 결과 (문제가 있었나 없었나)
- 문제 없었다.
- 필수 어조 사전은 이미 `required`, `mandatory`를 독립 토큰으로 잡는다.
- 따라서 `is required`, `are required`, `is mandatory`, `are mandatory`는 별도 `is/are` 항목 없이도 매칭된다.
- 필수 어조 사전은 변경하지 않았다.

3. 재현 케이스 수정 전후 출력

수정 전, 설치된 v0.1.0 바이너리(`/home/juhyeonl/.local/bin/ats-check`):

```text
VERDICT: APPLY

  ✓ Language    English only
  ✓ Level       Developer (no seniority marker)
  ✓ Seniority   not specified
  ✓ Degree      not required

  MISSING (required)   Kotlin, Kubernetes
  MATCHED              Java, Spring Boot, SQL, REST
```

수정 후, 최신 클래스패스 설치 스크립트(`cli/build/install/cli/bin/cli`):

```text
VERDICT: APPLY

  ✓ Language    English only
  ✓ Level       Developer (no seniority marker)
  ✓ Seniority   not specified
  ✓ Degree      not required

  MISSING (nice)       Kotlin, Kubernetes
  MATCHED              Java, Spring Boot, SQL, REST
```

4. 테스트 결과 (총 개수, 골든 31개 확인)
- `./gradlew :core:test` 성공
- `./gradlew build` 성공
- 테스트 XML 기준 총 208개, failures 0, errors 0, skipped 0
- 골든 케이스 디렉터리 31개 확인
- `GoldenFileTest` 31개 통과
- `02-finnish-nice-to-have` 유지 확인
- `./gradlew :core:dependencies --configuration runtimeClasspath` 결과: `No dependencies`

5. 남은 미탐 리스크 — 실제 공고에서 더 나올 법한 변형
- `would be a plus`, `would be an advantage`
- `count as a plus`, `counts as a plus`
- `considered a plus`, `seen as an asset`
- `bonus points for Kotlin`
- `helpful`, `desirable`, `valued`, `good to have`

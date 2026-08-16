[Codex 결과 보고서] Task 14

1. 정규식을 어떻게 확장했는가

- 기존 Task 13 패턴 `(?:is|are)\s+(?:a|an)\s+(?:plus|bonus|advantage|asset)`은 유지했다.
- 우대 명사 집합을 `plus`, `bonus`, `advantage`, `asset`, `benefit`, `merit`, `strength`로 정의했다.
- 다음 명시적 우대 관용구 패턴을 추가했다.
  - `(?:(?:is|are|would be|will be|can be)\s+)?(?:considered|seen|regarded|viewed|counted|treated)\s+(?:as\s+)?(?:(?:a|an)\s+)?<우대명사>`
  - `counts\s+as\s+(?:a|an)\s+<우대명사>`

2. 수식어형/서술형 필수 마커를 어떻게 구분했는가

- 서술형 필수 마커: `must`, `required`, `is a requirement`, `essential`, `mandatory`, `expected`, `we expect`, `you will need`, `minimum`
- 수식어형 필수 마커: `fluent`, `native`, `proficiency in`, `working proficiency`
- `ideally`는 강제도 우대도 단정하지 않는 표현이라 `NICE_TONES`에서 빼고 `HEDGES`로 옮겼다. 그래서 `Ideally 5+ years of experience.`는 `AMBIGUOUS`로 고정된다.

3. 충돌 케이스를 어떻게 처리하기로 했고 왜인가

- 같은 절에 명시적 우대 관용구와 수식어형 필수 마커만 있으면 `NICE`로 판정한다.
- 같은 절에 명시적 우대 관용구와 서술형 필수 마커가 함께 있으면 `AMBIGUOUS`로 판정한다.
- 충돌 테스트 문장 `Fluent Finnish is required, though Swedish is considered a plus.`는 `AMBIGUOUS`로 고정했다. 현재 분류기는 절 단위 판정이라 한 절 안의 서로 다른 언어에 신호를 안전하게 귀속하지 못한다. 이 경우 단정적으로 `REQUIRED`나 `NICE`를 내지 않는 것이 false SKIP 방지에 더 맞다.

4. 재현 6문장 + 회귀 9문장 전후 출력

재현 6문장:

| 문장 | 수정 전 관측 | 수정 후 |
| --- | --- | --- |
| Fluent Finnish skills are considered as an advantage. | REQUIRED | NICE |
| Finnish is considered a plus. | REQUIRED | NICE |
| Finnish skills are seen as an advantage. | REQUIRED | NICE |
| Finnish is regarded as a benefit. | REQUIRED | NICE |
| Finnish would be considered an asset. | REQUIRED | NICE |
| Knowledge of Finnish counts as a plus. | REQUIRED | NICE |

회귀 9문장:

| 문장 | 유지해야 할 출력 | 수정 후 |
| --- | --- | --- |
| Fluent Finnish is required. | REQUIRED | REQUIRED |
| Fluent Finnish and English are required. | REQUIRED | REQUIRED |
| We expect fluent Finnish. | REQUIRED | REQUIRED |
| Java is mandatory. | REQUIRED | REQUIRED |
| Finnish is a plus. | NICE | NICE |
| Kotlin and Kubernetes are a plus. | NICE | NICE |
| Finnish is not required. | NEGATED | NEGATED |
| Working knowledge of Finnish. | AMBIGUOUS | AMBIGUOUS |
| Ideally 5+ years of experience. | AMBIGUOUS | AMBIGUOUS |

5. 테스트 결과

- `./gradlew build`: 성공
- 전체 테스트: 221개, failures 0, errors 0, skipped 0
- core 테스트: 150개 통과
- cli 테스트: 71개 통과
- 골든 케이스: 32개 통과
- `./gradlew :core:dependencies --configuration runtimeClasspath`: `No dependencies`

6. 남은 미탐/오탐 리스크

- 아직 명시적으로 잡지 않는 우대 표현: `would be valuable`, `is valued`, `will count in your favor`, `helpful but not required`, `an asset to have`, `good to have`, `beneficial to have`
- `considered beneficial`처럼 형용사형 보어가 오는 변형은 이번 명사형 관용구 범위 밖이다.
- 한 절 안에서 서로 다른 대상에 REQUIRED와 NICE가 섞인 문장은 현재 절 단위 한계 때문에 `AMBIGUOUS`로 남긴다. 더 줄이려면 언어/스킬 엔티티별 신호 귀속이 필요하다.

[Codex 결과 보고서] Task 4

1. 수행한 작업 요약

- `CLAUDE.md` §9용 골든 파일 테스트 하네스를 추가했다.
- `core/src/test/resources/golden/` 아래 합성 채용 공고 20개와 기대 결과 JSON 20개를 추가했다.
- `expected.json` 파싱용으로 `core`에 `testImplementation("org.yaml:snakeyaml:2.3")`만 추가했다.
- `core` 런타임 의존성은 계속 0개로 유지되는 것을 확인했다.

2. 생성한 파일 목록 (케이스 20개 + 하네스)

- `core/src/test/java/dev/juhyeonl/atscheck/core/GoldenFileTest.java`
- `core/src/test/resources/golden/README.md`
- `core/src/test/resources/golden/01-finnish-required/input.txt`
- `core/src/test/resources/golden/01-finnish-required/expected.json`
- `core/src/test/resources/golden/02-finnish-nice-to-have/input.txt`
- `core/src/test/resources/golden/02-finnish-nice-to-have/expected.json`
- `core/src/test/resources/golden/03-finnish-negated/input.txt`
- `core/src/test/resources/golden/03-finnish-negated/expected.json`
- `core/src/test/resources/golden/04-finnish-ambiguous/input.txt`
- `core/src/test/resources/golden/04-finnish-ambiguous/expected.json`
- `core/src/test/resources/golden/05-finnish-in-benefits/input.txt`
- `core/src/test/resources/golden/05-finnish-in-benefits/expected.json`
- `core/src/test/resources/golden/06-swedish-required/input.txt`
- `core/src/test/resources/golden/06-swedish-required/expected.json`
- `core/src/test/resources/golden/07-finnish-required-but-profile-has-it/input.txt`
- `core/src/test/resources/golden/07-finnish-required-but-profile-has-it/expected.json`
- `core/src/test/resources/golden/08-senior-seven-years/input.txt`
- `core/src/test/resources/golden/08-senior-seven-years/expected.json`
- `core/src/test/resources/golden/09-borderline-three-years/input.txt`
- `core/src/test/resources/golden/09-borderline-three-years/expected.json`
- `core/src/test/resources/golden/10-experience-not-stated/input.txt`
- `core/src/test/resources/golden/10-experience-not-stated/expected.json`
- `core/src/test/resources/golden/11-experience-in-nice-section/input.txt`
- `core/src/test/resources/golden/11-experience-in-nice-section/expected.json`
- `core/src/test/resources/golden/12-msc-required/input.txt`
- `core/src/test/resources/golden/12-msc-required/expected.json`
- `core/src/test/resources/golden/13-msc-or-equivalent/input.txt`
- `core/src/test/resources/golden/13-msc-or-equivalent/expected.json`
- `core/src/test/resources/golden/14-phd-nice-to-have/input.txt`
- `core/src/test/resources/golden/14-phd-nice-to-have/expected.json`
- `core/src/test/resources/golden/15-master-of-your-craft/input.txt`
- `core/src/test/resources/golden/15-master-of-your-craft/expected.json`
- `core/src/test/resources/golden/16-senior-title/input.txt`
- `core/src/test/resources/golden/16-senior-title/expected.json`
- `core/src/test/resources/golden/17-lead-title/input.txt`
- `core/src/test/resources/golden/17-lead-title/expected.json`
- `core/src/test/resources/golden/18-junior-title/input.txt`
- `core/src/test/resources/golden/18-junior-title/expected.json`
- `core/src/test/resources/golden/19-skill-gap/input.txt`
- `core/src/test/resources/golden/19-skill-gap/expected.json`
- `core/src/test/resources/golden/20-multiple-hard-fails/input.txt`
- `core/src/test/resources/golden/20-multiple-hard-fails/expected.json`

3. 테스트 하네스 설계 (자동 발견, 비교 방식)

- `@ParameterizedTest(name = "{0}")`와 `@MethodSource`로 `golden/` 하위 디렉터리를 자동 발견한다.
- 디렉터리 이름을 테스트 표시 이름으로 사용해 실패 시 케이스를 바로 알 수 있다.
- 골든 디렉터리가 없거나 케이스가 0개면 테스트가 실패한다.
- SnakeYAML 2.3 `SafeConstructor`로 `expected.json`을 `Map<String, Object>`로만 읽는다. 클래스 바인딩은 쓰지 않았다.
- `profile`은 케이스별 부분 override를 허용하고, 누락 필드는 기본 프로필을 유지한다.
- `verdict`, `stoppedAtHardFilter`, `findings`의 `rule/status`, `skillGap` 세 집합, `evidenceContains`를 검증한다.
- `findings`는 실제 실행 순서까지 회귀 신호로 삼기 위해 정확한 리스트로 비교한다.
- `skillGap`의 `matched`, `missingRequired`, `missingNice`는 순서 무관으로 비교한다.
- 주요 assertion 메시지에는 케이스명, 기대값, 실제값을 같이 넣었다.

4. 테스트 결과 (총 개수, 골든 20개 통과 확인, 기존 102개 유지 확인)

- `./gradlew :core:test --tests dev.juhyeonl.atscheck.core.GoldenFileTest`: 성공
- `GoldenFileTest`: 20개, 실패 0, 에러 0, 스킵 0
- `./gradlew build`: 성공
- 전체 `core` 테스트: 122개, 실패 0, 에러 0, 스킵 0
- 기존 테스트: 102개 유지, 실패 0
- `./gradlew :core:dependencies --configuration runtimeClasspath`: `No dependencies`

5. 기대값과 실제 동작이 어긋난 케이스

- 없음.
- 20개 골든 케이스 모두 요구한 verdict/status/skillGap 기대값과 실제 동작이 일치했다.
- 프로덕션 코드는 수정하지 않았다.

6. 스스로 결정한 것 + 근거

- 모든 회사명은 가상 이름으로 작성했다. 네트워크/스크래핑 금지와 ADR-001 제약을 지키기 위해서다.
- 각 `input.txt`는 12-30줄 범위 안의 17-18줄 공고로 만들었다. 실제 공고 구조를 유지하면서도 테스트 의도를 읽기 쉽게 하기 위해서다.
- 하드 필터 실패 케이스는 Stage 1 finding 3개만 기대값에 넣었다. 현재 공개 API가 하드 필터 실패 시 Stage 2/3을 중단하기 때문이다.
- APPLY/REVIEW 케이스는 finding 5개 전체를 기대값에 넣었다. Stage 2/3 실행 여부의 회귀도 잡기 위해서다.
- 03, 05, 15는 핵심 회귀 방지 케이스라 각각 `Finnish is not required`, 복지 섹션 `Finnish lessons`, `master your craft` 표현을 자연스러운 공고 문맥에 넣었다.

7. 케이스를 만들면서 발견한 규칙의 약점

- PASS finding은 evidence가 비어 있다. 그래서 `Finnish is not required`, 복지 섹션 `Finnish lessons`, `master your craft` 같은 PASS 회귀 케이스는 원문 evidence까지는 검증할 수 없고 최종 status로만 검증한다.
- `SeniorityLevelRule`은 제목만 본다. 본문 Requirements에 "lead the team"류의 seniority 신호가 있어도 현재 구조에서는 반영되지 않는다.
- `DegreeRule`의 `master` 판정은 같은 clause 안의 문맥 단어에 의존한다. 한 문장 안에 `master your craft`와 `degree`류 단어가 같이 있으면 오탐 가능성이 남아 있다.
- `SkillRule`은 REQUIRED/NICE 섹션의 사전 단어를 폭넓게 본다. 실제 공고의 일반 단어가 기술 토큰과 겹치는 경우에는 스킬 갭이 의도보다 커질 수 있다.
- 우대 섹션의 연차는 현재 의도대로 하드 필터가 아니지만, 우대 섹션 안에 `required` 같은 강한 톤이 섞이면 `AMBIGUOUS`로 올라갈 수 있다. 지저분한 실제 공고에서는 섹션 제목과 문장 톤 충돌 처리를 더 넓게 볼 필요가 있다.

8. 다음 추천 작업

- PASS finding에도 선택적으로 evidence를 남길지 결정하라. 회귀 테스트의 설명력이 좋아진다.
- 실제 익명화 공고가 준비되면 `golden/README.md` 절차대로 합성 케이스 옆에 추가하라.
- seniority 본문 신호를 별도 규칙 또는 현재 규칙 확장으로 다룰지 설계 검토하라.
- 스킬 사전 오탐/과탐 케이스를 실제 공고 기반으로 더 추가하라.

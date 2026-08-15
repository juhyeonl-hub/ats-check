[Codex 결과 보고서] Task 9

1. 추가한 케이스 10개 목록과 각각이 노리는 것

- `21-finnish-required-and-senior`: 핀란드어 필수와 7+년 경력 요구가 동시에 `FAIL`인 하드 필터 케이스. `SKIP` 기대.
- `22-swedish-nice-finnish-required`: Finnish는 필수, Swedish는 우대로 등장하는 서로 다른 강도 언어 케이스. `SKIP` 기대.
- `23-messy-formatting`: 대소문자 혼용 헤더, 불릿 종류 혼합, 과다 공백, 들여쓰기 불규칙 포맷. `APPLY` 기대.
- `24-requirements-without-header`: 섹션 헤더 없이 `you will need`, `mandatory`, `expected`, `required` 어조만으로 요건을 판단하는 케이스. `SKIP` 기대.
- `25-benefits-heavy`: 긴 복지 섹션 안에 Finnish와 Master's studies 언급이 섞인 케이스. `APPLY` 기대.
- `26-experience-range`: `3-5 years` 범위 표기를 하한 3년으로 보는지 확인. `APPLY` + `EXPERIENCE_YEARS WARN` 기대.
- `27-degree-in-nice-section`: Master's degree가 우대 섹션에 있을 때 하드 실패하지 않는지 확인. `APPLY` 기대.
- `28-many-skills`: 10개 이상 스킬이 필수/우대에 섞이고 skill gap이 생기는 케이스. `APPLY` 기대.
- `29-lead-role-with-finnish-plus`: Lead title과 3+년 경력으로 WARN 두 개가 생기고, Finnish 우대는 SKIP이 아닌지 확인. `APPLY` 기대.
- `30-everything-passes`: 기본 프로필과 완전히 맞는 공고. `APPLY`, skill gap full match 기대.

2. 기대와 실제가 어긋난 케이스

- 없음.
- `GoldenFileTest` 단독 실행에서 신규 10개 포함 30개 골든 케이스가 모두 기대값과 일치했다.
- 생산 코드 수정 없이 `expected.json` 기준 그대로 통과했다.

3. 새 케이스를 쓰면서 발견한 규칙의 약점

- 하드 필터에서 하나라도 `FAIL`이면 Stage 2/3가 실행되지 않는다. 그래서 `21-finnish-required-and-senior`처럼 제목에 Senior가 있어도 결과에는 seniority WARN이 드러나지 않는다. 현재 설계와는 일치하지만, 사용자가 "왜 안 맞는지"를 모두 보고 싶다면 정보 손실이 있다.
- 섹션 헤더가 없는 공고는 어조 사전에 있는 강한 표현에 의존한다. `24`는 `you will need`, `mandatory`, `expected`, `required`를 써서 잘 잡히지만, 실제 공고의 완곡한 문장이나 `Requirements include ...`처럼 사전에 없는 표현은 요구로 약하게 잡힐 수 있다.
- 커스텀 콜론 헤더는 중립 섹션으로 리셋된다. 실제 회사가 `What you will own:` 같은 필수성 헤더를 쓰면, 뒤따르는 항목이 자체 어조를 갖지 않는 한 필수 요건으로 분류되지 않을 수 있다.
- SkillRule은 누락 스킬이 많아도 Finding 상태가 항상 `PASS`다. `28-many-skills`는 `missingRequired`가 있어도 verdict가 `APPLY`인 현재 설계를 고정한다.

4. 테스트 결과

- 골든 케이스 수: 30개.
- `./gradlew :core:test --tests dev.juhyeonl.atscheck.core.GoldenFileTest`: 성공, `GoldenFileTest` 30개 통과.
- `./gradlew build`: 성공.
- 테스트 리포트 기준 전체 테스트: 206개 통과, 실패 0, 스킵 0.
- 모듈별 테스트 수: `core` 135개, `cli` 71개.
- 기존 196개에 신규 골든 파라미터 케이스 10개가 추가된 상태다.
- `./gradlew :core:dependencies --configuration runtimeClasspath`: `No dependencies`, core 런타임 의존성 0개 확인.

5. 스스로 결정한 것 + 근거

- 모든 신규 케이스는 기본 골든 테스트 프로필을 사용했다. 요구한 시나리오가 기본 프로필의 언어, 경력, 학위, 스킬 한계로 충분히 표현됐다.
- `29-lead-role-with-finnish-plus`의 WARN 두 개는 `Lead Backend Engineer` 제목의 seniority WARN과 `3+ years`의 experience WARN으로 구성했다. Finnish는 우대 문구로만 둬서 SKIP이 되지 않게 했다.
- `28-many-skills`는 skill gap이 있어도 `APPLY`로 기대했다. 현재 설계에서 스킬 누락은 `skillGap`으로만 보고되고 verdict를 바꾸지 않기 때문이다.
- `21-finnish-required-and-senior`는 제목에 Senior를 넣었지만 expected findings에는 Stage 1 세 Finding만 넣었다. 하드 필터 `FAIL` 이후 Stage 2가 실행되지 않는 것이 현재 `AtsChecker` 동작이기 때문이다.
- `evidenceContains`는 각 `input.txt`에 실제로 존재하는 부분 문자열만 사용했다.

6. 다음 추천 작업

- 섹션 헤더 없는 실제 공고 문체를 더 수집해 ToneAnalyzer 사전이 놓치는 요구 표현을 별도 태스크로 보강한다.
- 하드 필터 실패 시에도 Stage 2/3 진단 정보를 선택적으로 보여줄지 제품 요구를 정한다.
- 커스텀 요구 헤더를 보수적으로 필수 섹션으로 볼 수 있는지 ADR 또는 규칙 변경 태스크로 검토한다.

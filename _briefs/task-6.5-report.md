[Codex 결과 보고서] Task 6.5

1. 수정 방법

- `CheckCommand`가 `--job` 파일과 stdin 입력을 모두 `JobFileParser`로 먼저 파싱하도록 변경했다.
- 파싱된 프론트매터에 `title`이 있으면 `new JobPosting(frontMatterTitle, body)`로 판정한다.
- 프론트매터 `title`이 없으면 `JobPosting.fromText(body)`를 사용해 body의 첫 비어 있지 않은 줄을 title로 삼는다.
- 프론트매터가 없으면 `JobFileParser`가 원문 전체를 body로 반환하므로 기존 `JobPosting.fromText(text)`와 같은 동작을 유지한다.
- `core/`, `save`, `open`, `init`, `--json` 스키마는 변경하지 않았다.

2. 변경한 파일 목록

- `cli/src/main/java/dev/juhyeonl/atscheck/cli/command/CheckCommand.java`
- `cli/src/test/java/dev/juhyeonl/atscheck/cli/AtsCheckCliTest.java`
- `_briefs/task-6.5-report.md`

3. 재현 케이스 수정 전후 출력 비교 (네이티브 바이너리)

수정 전 출력은 작업지시서에 제공된 네이티브 바이너리 실측이다.

```text
# 수정 전: 순수 본문 stdin
VERDICT: APPLY
  ⚠ Level       Senior Backend Engineer (profile max: mid)

# 수정 전: save 후 --job
VERDICT: APPLY
  ✓ Level       --- (no seniority marker)
```

수정 후에는 `./gradlew :cli:nativeCompile`로 생성한
`cli/build/native/nativeCompile/ats-check`를 사용했다.

```text
# 수정 후: 순수 본문 stdin
VERDICT: APPLY

  ✓ Language    English only
  ⚠ Level       Senior Backend Engineer (profile max: mid)
  ✓ Seniority   not specified
  ✓ Degree      not required

  MATCHED              Java, Spring Boot
```

```text
# save 출력
/tmp/ats-check-task-6.5-repro/jobs/aurora-labs-senior-backend-engineer.md
```

```text
# 수정 후: save 후 --job
VERDICT: APPLY

  ✓ Language    English only
  ⚠ Level       Senior Backend Engineer (profile max: mid)
  ✓ Seniority   not specified
  ✓ Degree      not required

  MATCHED              Java, Spring Boot
```

`diff -u stdin.out saved-file.out` 결과는 빈 출력이었다.

4. 왕복 동등성 테스트를 어떻게 작성했는가

- `AtsCheckCliTest.savedJobCheckMatchesPlainStdinForVerdictAndFindingStatuses`를 추가했다.
- 같은 공고를 첫 번째는 순수 본문 stdin + `--json`, 두 번째는 `save --jobs-dir <temp>` 후 저장된 markdown을 `--job` + `--json`으로 실행한다.
- JSON을 파싱한 뒤 `verdict`와 모든 finding의 `rule/status` 목록만 `JsonCheck` 레코드로 추출해 비교한다.

5. 테스트 결과 (총 개수, 기존 164개 유지 확인)

- `./gradlew :cli:test`: 성공
- `./gradlew build`: 성공
- `./gradlew :cli:nativeCompile`: 성공, 산출물은 `cli/build/native/nativeCompile/ats-check` 단일 실행 파일
- `./gradlew test --rerun-tasks`: 성공
- 테스트 XML 집계: 총 170개, failures=0, errors=0, skipped=0
- 기존 164개 테스트는 유지되고, Task 6.5용 CLI 테스트 6개가 추가되어 170개가 되었다.

6. 스스로 결정한 것 + 근거

- Seniority WARN 재현에는 임시 프로필 `max_seniority: mid`를 사용했다. 현재 기본 프로필의 `max_seniority`는 `lead`라 Senior title만으로 WARN이 발생하지 않기 때문이다.
- evidence 오염 방지 테스트의 `url` 값에는 `Finnish is required.`를 넣었다. 이전처럼 프론트매터가 본문에 섞이면 Language evidence에 바로 나타나므로 회귀를 확실히 잡을 수 있다.
- 프론트매터 파싱 오류는 `UsageException`으로 변환해 사용 오류(exit 64) 경로로 보냈다. check 입력 파싱 문제이며 내부 오류로 처리할 이유가 없다.

7. 다음 추천 작업

- 다음 Task에서 `--job-dir` 배치 모드도 동일하게 `JobFileParser` 기반 body/title 규칙을 공유하도록 맞추는 것이 좋다.

# [Codex 작업지시서] Task 15 이어서 완성

## 상황

`_briefs/task-15-brief.md` 작업이 **시간 초과로 중단됐다.** 작업 대부분은 이미 워킹트리에 있다.

생성됨: `DisplayWidth.java`, `LocalizedText.java`, `SummaryTranslator.java`, `TerminalLanguage.java`
수정됨: `CheckCommand`, `TerminalRenderer`, `BatchTerminalRenderer`와 관련 테스트

**컴파일은 통과하고 테스트 하나만 실패한다:**

```
BatchCheckCommandTest > koreanBatchOutputTranslatesHeadersVerdictsReasonsAndSummary() FAILED
    java.lang.AssertionError at BatchCheckCommandTest.java:365
85 tests completed, 1 failed
```

## 할 일

1. **위 실패를 고쳐라.**
   - 기대값이 틀렸는지, 구현이 틀렸는지 판단해서 맞는 쪽을 고쳐라
   - 한국어 배치 출력의 **정렬**이 문제라면 `DisplayWidth`가 배치 컬럼 계산에
     제대로 적용됐는지 확인하라 (한글은 표시폭 2)
2. `_briefs/task-15-brief.md`의 **완료 조건과 테스트 10개를 다시 확인**하고
   빠진 것이 있으면 마저 구현하라
3. 보고서 `_briefs/task-15-report.md`를 작성하라 (원래 지시서의 형식대로)

## 반드시 지킬 것 (원 지시서에서 재확인)

- **기본값(영어) 출력이 한 글자도 바뀌면 안 된다**
- **`--lang ko --json`은 영어 JSON을 낸다**
- **evidence 원문은 번역하지 않는다**
- **`core/`를 수정하지 마라**
- `git commit` / `git push` 금지

## 완료 조건

1. `./gradlew build` 성공 — **모든 테스트 통과**
2. `./gradlew :cli:nativeCompile` 성공, 단일 파일 유지
3. 네이티브 바이너리로 아래를 실제 실행해서 출력을 보고서에 붙여라

```bash
source "$HOME/.sdkman/bin/sdkman-init.sh"
./gradlew :cli:nativeCompile
BIN=cli/build/native/nativeCompile/ats-check
POST='Developer\nVisma\n\nRequirements:\nStrong React and TypeScript.\n3+ years of frontend work.\nFluent Finnish skills are considered as an advantage.\n'
printf "$POST" | $BIN                 # 영어
printf "$POST" | $BIN --lang ko       # 한국어
printf "$POST" | $BIN --lang ko --json | head -5   # 영어 JSON 확인
```

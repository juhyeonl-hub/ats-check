[Codex 결과 보고서] Task 5

1. 수행한 작업 요약

- Spike A의 echo 기반 CLI를 실제 단일 공고 판정 CLI로 교체했다.
- `AtsCheckCli`를 최상위 Picocli 커맨드로 추가하고, 기본 실행 경로는 `CheckCommand`가 맡도록 분리했다.
- `profile.yml` 로딩, `--job`/stdin 입력, 터미널 출력, `--json`, 종료 코드 계약을 구현했다.
- `core`의 더 이상 쓰지 않는 `AtsChecker.echo(String)`를 제거했다.
- CLI 통합 테스트 14개를 추가했다.

2. 생성/변경한 파일 목록

- 생성: `cli/src/main/java/dev/juhyeonl/atscheck/cli/AtsCheckCli.java`
- 생성: `cli/src/main/java/dev/juhyeonl/atscheck/cli/command/CheckCommand.java`
- 생성: `cli/src/main/java/dev/juhyeonl/atscheck/cli/config/ProfileLoader.java`
- 생성: `cli/src/main/java/dev/juhyeonl/atscheck/cli/render/TerminalRenderer.java`
- 생성: `cli/src/main/java/dev/juhyeonl/atscheck/cli/render/JsonRenderer.java`
- 생성: `cli/src/test/java/dev/juhyeonl/atscheck/cli/AtsCheckCliTest.java`
- 생성: `_briefs/task-5-report.md`
- 변경: `cli/build.gradle.kts`
- 변경: `core/src/main/java/dev/juhyeonl/atscheck/core/AtsChecker.java`
- 삭제: `cli/src/main/java/dev/juhyeonl/atscheck/cli/CheckCommand.java`

3. ProfileLoader의 탐색 순서와 오류 처리

탐색 순서:

1. `--profile <path>`로 명시된 경로
2. `$XDG_CONFIG_HOME/ats-check/profile.yml`
3. `~/.config/ats-check/profile.yml`
4. 없으면 `Profile.defaults()` 사용 및 stderr 안내

처리 방식:

- SnakeYAML `SafeConstructor`로 루트 `Map<String, Object>`까지만 읽고 수동 매핑했다. `Profile` 클래스 바인딩은 쓰지 않았다.
- 읽는 키는 `years_experience`, `years_tolerance`, `max_seniority`, `languages`, `degree`, `skills`다.
- 누락된 값은 `Profile.defaults()` 값을 사용한다. `years_tolerance` 누락 기본값은 core 기본값인 `1`이다.
- 문자열 값은 `Locale.ROOT` 기준 소문자로 정규화한다.
- 잘못된 필드 값은 stderr 경고 후 해당 필드만 기본값을 사용한다.
- 명시한 `--profile` 경로가 없거나 YAML 자체가 깨졌거나 루트가 Map이 아니면 exit 64로 처리한다.

4. 종료 코드를 Picocli에서 어떻게 매핑했는가

- `AtsCheckCli.commandLine(...)`에서 Picocli `ParameterExceptionHandler`를 등록해 잘못된 옵션/파라미터 오류를 64로 매핑했다.
- 예상 못 한 실행 예외는 `ExecutionExceptionHandler`에서 70으로 매핑하고, 기본 출력은 짧은 메시지만 stderr에 낸다. `--debug`가 있을 때만 stack trace를 출력한다.
- `CheckCommand` 내부의 사용법 오류와 profile 로딩 오류는 직접 stderr 메시지를 출력하고 64를 반환한다.
- 판정 결과는 `APPLY -> 0`, `REVIEW -> 1`, `SKIP -> 2`로 반환한다.
- `--help`, `--version`은 Picocli 기본 help/version 처리로 0을 반환한다.

5. JSON writer의 이스케이프 처리 방식

- SnakeYAML dump를 쓰지 않고 `JsonRenderer`에서 직접 JSON 문자열을 조립했다.
- 문자열 이스케이프는 `"`, `\`, backspace, form feed, newline, carriage return, tab을 각각 JSON escape로 처리한다.
- 그 외 `0x20` 미만 제어문자는 `\u00xx` 형식으로 출력한다.
- 스키마 필드 `verdict`, `stoppedAtHardFilter`, `findings`, `skillGap`은 항상 출력한다.

6. 스킬 이름 표시 방식에 대한 결정 (소문자 유지 여부)

- 터미널과 JSON 모두 core 내부 값 그대로 출력한다.
- 지시서의 명시대로 표시 시 첫 글자 대문자화를 하지 않았다.
- 이유: `postgresql`, `c++`, `c#`, `.net` 같은 값에서 title-case 변환이 오히려 부정확할 수 있다.

7. 테스트 결과 (총 개수, 기존 123개 유지 확인)

- `./gradlew :cli:test`: 성공, CLI 테스트 14개 통과
- `./gradlew build`: 성공
- 테스트 리포트 기준:
  - core: 123개, 실패 0, 에러 0
  - cli: 14개, 실패 0, 에러 0
  - 총 137개 통과
- `./gradlew :core:dependencies --configuration runtimeClasspath` 결과:

```text
runtimeClasspath - Runtime classpath of source set 'main'.
No dependencies
```

8. 네이티브 빌드 결과: 빌드 시간 / 바이너리 크기 / 사이드카 유무 / 격리 실행 결과

실행 명령:

```bash
source "$HOME/.sdkman/bin/sdkman-init.sh" && /usr/bin/time -p ./gradlew :cli:nativeCompile
```

핵심 출력:

```text
Finished generating 'ats-check' in 15.8s.
Produced artifacts:
 /home/juhyeonl/workspace/ats-check/cli/build/native/nativeCompile/ats-check (executable)
BUILD SUCCESSFUL in 18s
real 18.13
user 1.17
sys 0.41
```

바이너리 크기 및 사이드카 확인:

```text
ats-check 19359536 bytes
```

`cli/build/native/nativeCompile/`에는 `ats-check` 파일만 있었고 `.so` 사이드카는 없었다.

격리 실행:

```text
$ cp cli/build/native/nativeCompile/ats-check /tmp/ac-test
$ /tmp/ac-test --version
ats-check 0.1.0-SNAPSHOT

$ printf 'Requirements:\nFluent Finnish required.\n' | /tmp/ac-test ; printf 'exit=%s\n' "$?"
no profile found, using defaults - run 'ats-check init' to create one
VERDICT: SKIP

  ✗ Language  Finnish required
                "Fluent Finnish required."
  ✓ Seniority not specified
  ✓ Degree    not required

  Analysis stopped at hard filter.
exit=2
```

9. 스스로 결정한 것 + 근거

- 최상위 `AtsCheckCli`는 기본 실행을 `CheckCommand` mixin으로 위임했다. 현재 사용법인 `ats-check --job ...`와 `pbpaste | ats-check`를 유지하면서 다음 태스크에서 서브커맨드를 붙일 자리를 만들기 위해서다.
- 테스트용으로 `AtsCheckCli.commandLine(InputStream, BooleanSupplier, ProfileLoader)` 팩토리를 열어 두었다. 실제 프로세스 없이 Picocli `execute()` 반환값과 stdout/stderr를 검증하고, 개발자 머신의 실제 profile 파일 영향을 배제하기 위해서다.
- 입력 오류를 먼저 검사한 뒤 profile을 로딩한다. `ats-check`만 실행한 경우 불필요한 profile 기본값 안내보다 사용법 안내가 먼저 나오는 편이 제품 동작에 맞다고 판단했다.

10. core에서 고쳐야 한다고 생각하는 것 (보고만)

- 이번 작업 중 core 판정 로직에서 수정이 필요하다고 판단한 항목은 없다.
- 허용된 범위인 `AtsChecker.echo(String)` 제거만 수행했다.

11. 다음 추천 작업

- 다음 태스크 범위인 `init` 또는 `save`/`open` 서브커맨드를 `AtsCheckCli`에 연결한다.
- profile 생성 명령이 추가되면 현재 기본값 안내 문구의 `ats-check init` 경로를 실제 동작과 함께 통합 테스트로 고정한다.

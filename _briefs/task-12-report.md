[Codex 결과 보고서] Task 12

1. 버전을 어디서 어떻게 주입했는가

- 루트 `build.gradle.kts`의 `allprojects.version`을 `-PatsCheckVersion`으로 주입 가능하게 변경했다.
- 주입값이 없으면 기존 기본값인 `0.1.0-SNAPSHOT`을 그대로 사용한다.
- CLI 모듈은 빌드 시점의 `project.version`으로 `BuildInfo.java`를 생성한다.
- 생성 경로는 `cli/build/generated/sources/atsCheckVersion/java/dev/juhyeonl/atscheck/cli/BuildInfo.java`이며, `compileJava`가 이 생성 작업에 의존한다.

2. Picocli가 버전을 읽는 방식과, 그것이 네이티브에서 동작함을 어떻게 확인했는가

- `AtsCheckCli`의 `@Command(version = "ats-check 0.1.0-SNAPSHOT")` 하드코딩을 제거했다.
- `CommandLine` 생성 직후 `commandLine.getCommandSpec().version("ats-check " + BuildInfo.version())`로 Picocli 버전 문자열을 설정한다.
- `BuildInfo`는 Gradle이 생성한 Java 소스이므로 네이티브 이미지에 컴파일된 코드로 포함된다. 매니페스트나 런타임 리소스 조회에 의존하지 않는다.
- 아래 두 네이티브 바이너리 실행으로 실제 동작을 확인했다.

3. Linux 컨테이너로 값을 전달한 방법

- `native-build` job에 `RELEASE_TAG: ${{ github.ref_name }}`를 추가했다.
- Linux 스텝의 호스트 셸에서 `ats_check_version="${RELEASE_TAG#v}"`로 `v` 접두사를 제거한다.
- Docker 실행 시 `-e ATS_CHECK_VERSION="$ats_check_version"`로 컨테이너에 전달한다.
- 컨테이너 내부 Gradle 호출은 `./gradlew --no-configuration-cache -PatsCheckVersion="$ATS_CHECK_VERSION" :cli:nativeCompile`로 변경했다.
- macOS/Windows 스텝도 같은 방식으로 `RELEASE_TAG`에서 `v`를 제거한 뒤 `-PatsCheckVersion="$ats_check_version"`을 넘긴다.

4. 네이티브 바이너리 `--version` 출력 2개

주입 없음:

```text
ats-check 0.1.0-SNAPSHOT
```

주입:

```text
ats-check 0.1.0
```

5. 변경 파일 목록과 diff 요약

- `build.gradle.kts`
  - `atsCheckVersion` Gradle 프로퍼티를 읽고, 없으면 `0.1.0-SNAPSHOT`을 사용하게 변경.
- `cli/build.gradle.kts`
  - 빌드 버전 기반 `BuildInfo.java` 생성 작업 추가.
  - 생성 소스 디렉터리를 main Java source set에 추가.
- `cli/src/main/java/dev/juhyeonl/atscheck/cli/AtsCheckCli.java`
  - Picocli 버전 하드코딩 제거.
  - 생성된 `BuildInfo.version()`을 `CommandSpec.version(...)`에 연결.
- `cli/src/test/java/dev/juhyeonl/atscheck/cli/AtsCheckCliTest.java`
  - `--version` 테스트가 생성된 빌드 버전을 기준으로 검증하게 변경.
- `.github/workflows/release.yml`
  - Linux 컨테이너, macOS, Windows의 모든 `nativeCompile` 호출에 태그 버전 전달.

Diff stat:

```text
.github/workflows/release.yml                      | 11 ++++--
build.gradle.kts                                   |  2 +-
cli/build.gradle.kts                               | 42 ++++++++++++++++++++++
cli/src/main/java/dev/juhyeonl/atscheck/cli/AtsCheckCli.java     |  2 +-
cli/src/test/java/dev/juhyeonl/atscheck/cli/AtsCheckCliTest.java |  2 +-
5 files changed, 54 insertions(+), 5 deletions(-)
```

6. 테스트 결과

- `source "$HOME/.sdkman/bin/sdkman-init.sh"; ./gradlew build`
  - 성공.
  - 테스트 결과 XML 기준 총 206개 테스트 기록.
- `source "$HOME/.sdkman/bin/sdkman-init.sh"; ./gradlew :cli:nativeCompile`
  - 성공.
  - `cli/build/native/nativeCompile/ats-check --version` 출력: `ats-check 0.1.0-SNAPSHOT`.
- `source "$HOME/.sdkman/bin/sdkman-init.sh"; ./gradlew -PatsCheckVersion=0.1.0 :cli:nativeCompile`
  - 성공.
  - `cli/build/native/nativeCompile/ats-check --version` 출력: `ats-check 0.1.0`.
- `python3 -c "import yaml; yaml.safe_load(open('.github/workflows/release.yml')); print('ok')"`
  - `ok`.
- `git diff --check` 대상 변경 파일
  - 통과.

7. 남는 리스크

- GitHub Actions 실제 릴리스 워크플로는 로컬에서 실행하지 못했고, YAML 파싱과 스크립트 경로 검토로 확인했다.
- 작업트리에 본 작업 범위 밖의 `docs/adr/ADR-003-native-build-in-container.md` 변경이 잡혀 있다. 작업 지시의 문서 수정 금지 범위라서 이 작업에서는 수정하지 않았다.

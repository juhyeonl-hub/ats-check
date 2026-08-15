[Codex 결과 보고서] Task 8

1. 수행한 작업 요약

- PR용 GitHub Actions 워크플로를 추가했다.
- 태그 릴리스용 GitHub Actions 워크플로를 추가했다.
- `core` 모듈에 JaCoCo 리포트 생성을 추가했다.
- 로컬에서 Gradle build, JaCoCo, nativeCompile, YAML 파싱, core 런타임 의존성 0개를 검증했다.

2. 생성/변경한 파일 목록

- 생성: `.github/workflows/pr.yml`
- 생성: `.github/workflows/release.yml`
- 생성: `_briefs/task-8-report.md`
- 변경: `core/build.gradle.kts`

3. PR 워크플로 구조 (매트릭스, 단계)

- 트리거: `pull_request`, `push` to `main`
- 매트릭스: `ubuntu-latest`, `macos-latest`, `windows-latest` x Java `21`
- 단계:
  - `actions/checkout@v4`
  - `graalvm/setup-graalvm@v1` with `distribution: graalvm-community`
  - `gradle/actions/setup-gradle@v4`
  - `./gradlew --no-configuration-cache build`
  - `./gradlew --no-configuration-cache :core:jacocoTestReport`
  - `$GITHUB_STEP_SUMMARY`에 core JaCoCo instruction/branch 요약 작성
  - 테스트 리포트와 JaCoCo 리포트 업로드

4. 릴리스 워크플로 구조 (Linux 컨테이너 빌드 방식, 스모크 테스트, 체크섬)

- 트리거: `push` tag `v*`
- `native-build` job:
  - 매트릭스: `ubuntu-latest`, `macos-latest`, `windows-latest`
  - Linux는 `docker run ubuntu:20.04` 안에서 빌드한다.
  - 컨테이너 안에서 `build-essential`, `zlib1g-dev`, `curl`, `zip`, `unzip`, `binutils` 등을 설치하고 SDKMAN으로 `21.0.2-graalce`를 설치한다.
  - macOS/Windows는 러너에서 `graalvm/setup-graalvm@v1`로 GraalVM Community `21.0.2`를 설정하고 직접 빌드한다.
  - 모든 OS에서 native build 디렉터리의 사이드카 파일을 검사한다.
  - 모든 OS에서 바이너리를 `/tmp/smoke`로 복사한 뒤 `--version`, hard fail exit `2`, pass exit `0`을 검증한다.
  - 산출물 이름은 `ats-check-<os>-<arch>` 또는 Windows의 `ats-check-<os>-<arch>.exe`다. macOS/Windows/Linux 모두 `uname -m` 기반으로 arch를 감지한다.
- `release` job:
  - `native-build` 성공 후 실행한다.
  - `actions/download-artifact@v4`로 바이너리를 내려받는다.
  - `sha256sum ats-check-* > checksums.txt`로 체크섬을 생성한다.
  - `softprops/action-gh-release@v2`로 3개 바이너리와 `checksums.txt`를 첨부하고 `generate_release_notes: true`를 사용한다.
  - `contents: write` 권한은 release job에만 부여했다.

5. Windows에서 UTF-8 문제를 어떻게 다뤘는가

- 두 워크플로 모두 전역 env에 `JAVA_TOOL_OPTIONS: -Dfile.encoding=UTF-8`을 설정했다.
- 두 워크플로 모두 `defaults.run.shell: bash`를 설정해 Windows에서도 `./gradlew`와 `printf` 기반 스모크 테스트를 같은 방식으로 실행하도록 했다.
- 실제 Windows runner의 콘솔/파일 인코딩 문제는 로컬 Linux 환경에서 재현할 수 없어 CI 최초 실행 때 확인이 필요하다.

6. glibc 검증을 어떻게 넣었는가

- Linux 릴리스 빌드는 `ubuntu:20.04` 컨테이너 안에서 실행한다.
- 빌드 후 다음 형태의 검사를 워크플로에 넣었다.

```bash
objdump -T "$binary" | grep -o 'GLIBC_[0-9.]*' | sort -Vu | tail -1
```

- 검출된 최대 glibc 버전이 `GLIBC_2.31`보다 높으면 release build를 실패시킨다.
- 참고: 로컬 WSL 산출물은 `GLIBC_2.34`로 확인되어, 로컬 직접 빌드로는 ADR-003 요구사항을 만족할 수 없다.

7. JaCoCo 커버리지 수치 (core 전체 + 낮은 클래스)

- core instruction coverage: 97.22% (`covered=4474`, `missed=128`)
- core branch coverage: 86.21% (`covered=400`, `missed=64`)
- core line coverage: 96.65% (`covered=778`, `missed=27`)
- core method coverage: 97.71% (`covered=128`, `missed=3`)
- core class coverage: 100.00% (`covered=35`, `missed=0`)

낮은 클래스, instruction 기준:

- `dev.juhyeonl.atscheck.core.rule.SkillRule$Candidate`: 71.43%
- `dev.juhyeonl.atscheck.core.section.ClauseSplitter$SplitClause`: 76.19%
- `dev.juhyeonl.atscheck.core.section.ClauseSplitter`: 83.46%
- `dev.juhyeonl.atscheck.core.model.Clause`: 88.64%
- `dev.juhyeonl.atscheck.core.model.Profile`: 89.47%

8. YAML 검증 결과 (actionlint 실행 여부 포함)

- 실행: `python3 -c "import yaml,sys; [yaml.safe_load(open(f)) for f in sys.argv[1:]]; print('yaml ok')" .github/workflows/pr.yml .github/workflows/release.yml`
- 결과: `yaml ok`
- 실행: `which actionlint && actionlint .github/workflows/*.yml`
- 결과: `actionlint not found`
- 지시대로 `actionlint` 설치는 시도하지 않았다.

9. 테스트 결과 + 네이티브 빌드 결과

- `./gradlew --no-configuration-cache build`: 성공
- 테스트 XML 집계: 196개 테스트, failures 0, errors 0, skipped 0
- `./gradlew --no-configuration-cache :core:jacocoTestReport`: 성공
- `./gradlew --no-configuration-cache :cli:nativeCompile`: 성공
- 로컬 native 산출물: `cli/build/native/nativeCompile/ats-check`, 19,794,656 bytes, 단일 파일
- 로컬 스모크 테스트:
  - `/tmp/ats-check-smoke-local`로 복사 후 `./ats-check --version`: 성공
  - `Fluent Finnish required.` 샘플: exit `2`
  - `Java.` 샘플: exit `0`
- `./gradlew --no-configuration-cache :core:dependencies --configuration runtimeClasspath`: `No dependencies`

10. 스스로 결정한 것 + 근거

- Linux 릴리스 빌드는 job-level `container:` 대신 `docker run ubuntu:20.04` 분기로 구현했다. 하나의 `native-build` 매트릭스 job에서 macOS/Windows 직접 빌드와 Linux 컨테이너 빌드를 함께 유지하기 위한 선택이다.
- 모든 Gradle 실행에 `--no-configuration-cache`를 명시했다. 로컬/CI 환경 설정이 바뀌어도 Native Build Tools 0.10.4의 configuration cache 문제를 피하기 위한 선택이다.
- PR 커버리지는 실패 게이트로 만들지 않았다. v0.1에서는 리포트만 요구된 범위이기 때문이다.
- PR 코멘트 액션은 추가하지 않고 `$GITHUB_STEP_SUMMARY`와 artifact upload만 사용했다. 외부 Action 의존성을 늘리지 말라는 요구를 따른 것이다.
- macOS/Windows 릴리스 빌드는 GraalVM Community `21.0.2`로 pin했다. 확인된 로컬 툴체인과 릴리스 산출물을 맞추기 위한 선택이다.

11. CI에서 실제로 돌려봐야만 알 수 있는 리스크

- GitHub 원격이 없고 push/tag push를 하지 않았으므로 두 워크플로는 아직 실제 GitHub Actions에서 실행되지 않았다.
- `actionlint`가 로컬에 없어 GitHub Actions 문법의 정적 검증은 YAML 파싱까지만 완료했다.
- Linux Docker 빌드에서 SDKMAN candidate `21.0.2-graalce` 다운로드가 CI 네트워크나 SDKMAN 상태에 영향을 받을 수 있다.
- `ubuntu:20.04` 컨테이너 빌드가 실제로 `GLIBC_2.31` 이하를 만드는지는 CI에서만 확인된다. 로컬 직접 빌드는 `GLIBC_2.34`였다.
- Windows runner에서 Git Bash, `./gradlew`, `printf`, UTF-8 골든 출력이 모두 기대대로 동작하는지는 실제 Windows CI에서만 확인된다.
- Windows native-image가 `.exe` 외 추가 파일을 만들지 않는지는 실제 Windows CI에서만 확인된다.
- `macos-latest`의 실제 아키텍처와 GraalVM native-image 빌드 성공 여부는 실제 macOS CI에서만 확인된다. artifact 이름은 런타임 arch 감지로 맞추도록 했다.
- GitHub Release 생성은 tag push, repository permissions, `GITHUB_TOKEN` 권한이 있어야 검증된다.

12. 다음 추천 작업

- 원격 생성 후 draft PR에서 `pr.yml`을 먼저 실행해 Windows UTF-8과 macOS runner 동작을 확인한다.
- `v0.1.0-test` 같은 임시 태그로 release workflow를 한 번 실행해 Linux glibc, OS별 native sidecar 검사, GitHub Release 첨부물을 검증한다.
- CI 첫 실행 후 문제가 없으면 `actionlint`를 개발 환경 또는 별도 검증 job에 도입할지 결정한다.

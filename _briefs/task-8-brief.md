# [Codex 작업지시서] Task 8 — CI/CD 파이프라인 (Day 14-17)

## 목표

`CLAUDE.md` §10의 CI/CD를 구축한다.

> **이 프로젝트의 포트폴리오 가치 중 절반이 여기 있다.**

1. PR 파이프라인 — 3개 OS 매트릭스 테스트 + 커버리지
2. 릴리스 파이프라인 — 3개 OS 네이티브 빌드 + 체크섬 + GitHub Release

**이 레포에는 아직 git 원격이 없다.** 워크플로 파일을 작성하고 문법을 검증하는 것까지가
이번 범위다. 원격 생성과 push는 사용자가 결정한다. **`git push`를 시도하지 마라.**

## 배경 (확인된 사실 — 다시 조사하지 말 것)

- `main` 브랜치, 커밋 `8c8be0f`. 테스트 196개 통과.
- 툴체인: GraalVM CE **21.0.2** (`21.0.2-graalce`), Gradle **8.10.2**
- 네이티브 빌드: 약 17초, **단일 파일 19MB**, 사이드카 없음
- `core` 런타임 의존성 0개, `cli`는 Picocli 4.7.6 + SnakeYAML 2.3

### 실측된 CI 제약 (반드시 반영하라)

1. **`org.gradle.configuration-cache`를 켜지 마라.**
   Native Build Tools 0.10.4의 `generateResourcesConfigFile`이 저장 중 실패한다
2. **Gradle은 8.x로 고정.** `cli` 빌드에서 Gradle 9.0 deprecation 경고가 난다
3. **`JAVA_HOME`을 명시하라.** GraalVM toolchain 자동 감지가 꺼져 있고 `JAVA_HOME`에 의존한다
4. **glibc**: 로컬(Ubuntu 22.04) 빌드 산출물이 `GLIBC_2.34`를 요구한다.
   Ubuntu 20.04·Debian 11·RHEL 8에서 실행 불가다. `docs/adr/ADR-003-native-build-in-container.md` 참조

## 1. PR 파이프라인 — `.github/workflows/pr.yml`

```yaml
on:
  pull_request:
  push:
    branches: [main]
```

**매트릭스:** `ubuntu-latest`, `macos-latest`, `windows-latest` × Java 21

**단계:**
1. `actions/checkout@v4`
2. `graalvm/setup-graalvm@v1` — GraalVM CE 21, `java-version: '21'`, `distribution: 'graalvm-community'`
3. Gradle 캐시 (`gradle/actions/setup-gradle@v4`)
4. `./gradlew build` — 컴파일 + 단위 테스트 + 골든 파일 테스트
5. 테스트 리포트 업로드 (실패 시에도: `if: always()`)

**Windows 주의:**
- `./gradlew`가 아니라 `gradlew.bat`이 필요할 수 있다. `gradle/actions/setup-gradle`을 쓰거나
  `shell: bash`를 명시해서 통일하라
- 골든 파일과 출력에 **UTF-8 문자(`✓ ⚠ ✗ …  ·`)가 있다.**
  `JAVA_TOOL_OPTIONS: -Dfile.encoding=UTF-8`을 설정하고, Windows에서 테스트가 깨지는지 확인하라.
  **이것이 Windows에서 가장 깨지기 쉬운 지점이다**

**커버리지:**
- JaCoCo를 `core`에 추가하라 (`jacocoTestReport`)
- `CLAUDE.md` §9의 목표: `core` 80% 이상
- **커버리지가 목표 미만이면 빌드를 실패시키지 마라** (v0.1에서는 리포트만)
- 리포트를 아티팩트로 업로드한다. **PR 코멘트를 위해 서드파티 액션을 추가하지 마라** —
  외부 의존을 늘리지 않는다. 요약을 `$GITHUB_STEP_SUMMARY`에 쓰는 것으로 충분하다

## 2. 릴리스 파이프라인 — `.github/workflows/release.yml`

```yaml
on:
  push:
    tags: ['v*']
```

### job 1: `native-build` (매트릭스)

| OS | 빌드 방식 |
|---|---|
| Linux | **`ubuntu:20.04` 컨테이너 안에서** (ADR-003) |
| macOS | 러너에서 직접 |
| Windows | 러너에서 직접 |

**Linux 컨테이너 빌드:**
- `container: ubuntu:20.04`를 job에 지정하거나, 러너에서 `docker run`으로 실행하라
- 컨테이너 안에 필요한 것: `gcc`, `zlib1g-dev`, `curl`, `zip`(GraalVM/SDKMAN 설치용), GraalVM CE 21
- **`ubuntu:20.04`에는 아무것도 없다.** `apt-get update && apt-get install -y ...`가 필요하다
- **빌드 후 glibc 요구 버전을 검증하라:**
  ```bash
  objdump -T ats-check | grep -o 'GLIBC_[0-9.]*' | sort -Vu | tail -1
  ```
  `GLIBC_2.31`보다 높으면 **빌드를 실패시켜라.** ADR-003이 지키려는 것이 이것이다

**모든 OS 공통 — 스모크 테스트 (반드시):**

```bash
# 1) 다른 디렉토리로 복사해서 실행한다  ← ADR-005의 교훈
cp <binary> /tmp/smoke/ats-check
cd /tmp/smoke

# 2) 사이드카가 없는지 확인한다
#    빌드 디렉토리에 ats-check 외의 파일이 있으면 실패시켜라

# 3) 버전
./ats-check --version

# 4) 판정 + 종료 코드
printf 'Java Developer\n\nRequirements:\nFluent Finnish required.\n' | ./ats-check
# exit code가 2여야 한다

printf 'Backend Engineer\n\nRequirements:\nJava.\n' | ./ats-check
# exit code가 0이어야 한다
```

**종료 코드는 제품 계약이다.** 스모크 테스트가 이것을 검증해야 한다.
Windows에서는 `printf` 대신 다른 방법이 필요할 수 있다 — `shell: bash`로 통일하라.

**아티팩트 이름:** `ats-check-<os>-<arch>` (예: `ats-check-linux-amd64`, `ats-check-macos-arm64`,
`ats-check-windows-amd64.exe`)

### job 2: `release`

- 모든 `native-build` job이 성공한 뒤 실행 (`needs`)
- 아티팩트를 내려받아 **SHA256 체크섬 생성** → `checksums.txt`
- `softprops/action-gh-release@v2` 또는 `gh release create`로 릴리스 생성
- 3개 바이너리 + `checksums.txt` 첨부
- **체인지로그 자동 생성** (`generate_release_notes: true` 또는 `gh release create --generate-notes`)
- `permissions: contents: write` 필요

## 3. 문법 검증

**워크플로를 작성만 하고 끝내지 마라.** 아래를 실행해서 확인하라:

```bash
# YAML 파싱
python3 -c "import yaml,sys; [yaml.safe_load(open(f)) for f in sys.argv[1:]]; print('yaml ok')" \
  .github/workflows/pr.yml .github/workflows/release.yml

# actionlint가 설치돼 있으면 실행 (없으면 건너뛰고 보고서에 적어라)
which actionlint && actionlint .github/workflows/*.yml
```

`actionlint`를 **설치하려고 sudo를 쓰지 마라.** 없으면 없는 대로 보고하라.

## 4. JaCoCo

`core`에 JaCoCo 플러그인을 추가하라.

- **`core`의 런타임 의존성은 여전히 0개여야 한다.** JaCoCo는 테스트 도구다
- `./gradlew :core:jacocoTestReport`로 XML+HTML 리포트 생성
- **현재 커버리지 수치를 측정해서 보고서에 적어라.** §9 목표는 80%다.
  미달이면 어느 클래스가 낮은지 함께 보고하라 (고치지는 마라)
- **`./gradlew :cli:nativeCompile`이 여전히 성공하는지 확인하라.**
  JaCoCo가 네이티브 빌드에 영향을 주면 안 된다

## 수정 금지

- `CLAUDE.md`, `docs/adr/`, `_reviews/`, `_briefs/` 기존 파일
- **`core/`, `cli/`의 프로덕션 코드 전체** — 이번 태스크는 **빌드 설정과 워크플로만** 만든다
  (JaCoCo 플러그인 추가는 예외)
- `core/src/test/resources/golden/`
- **`git push` 절대 금지.** 원격이 없고, 생성 여부는 사용자가 결정한다
- `git commit` 금지
- `sudo`, `apt` 사용 금지
- 서드파티 GitHub Action 추가 최소화 — checkout / setup-graalvm / setup-gradle /
  upload-artifact / download-artifact / action-gh-release 외에는 쓰지 마라

## 완료 조건

1. `.github/workflows/pr.yml`과 `.github/workflows/release.yml`이 존재한다
2. 두 파일이 **YAML로 파싱된다**
3. `./gradlew build` 성공, **기존 196개 테스트 통과**
4. `./gradlew :core:jacocoTestReport` 성공, 커버리지 수치를 보고서에 적었다
5. **`./gradlew :cli:nativeCompile` 성공, 단일 파일 유지**
6. `core` 런타임 의존성 0개

## 보고서

`_briefs/task-8-report.md`

```
[Codex 결과 보고서] Task 8

1. 수행한 작업 요약
2. 생성/변경한 파일 목록
3. PR 워크플로 구조 (매트릭스, 단계)
4. 릴리스 워크플로 구조 (Linux 컨테이너 빌드 방식, 스모크 테스트, 체크섬)
5. Windows에서 UTF-8 문제를 어떻게 다뤘는가
6. glibc 검증을 어떻게 넣었는가
7. **JaCoCo 커버리지 수치 (core 전체 + 낮은 클래스)**   ← 실제 숫자
8. YAML 검증 결과 (actionlint 실행 여부 포함)
9. 테스트 결과 + 네이티브 빌드 결과
10. 스스로 결정한 것 + 근거
11. **CI에서 실제로 돌려봐야만 알 수 있는 리스크**   ← 정직하게 적어라
12. 다음 추천 작업
```

**11번을 성실히 채워라.** 이 워크플로는 아직 한 번도 실행되지 않았다.
로컬에서 검증할 수 없는 것이 무엇인지 명확히 아는 것이 중요하다.

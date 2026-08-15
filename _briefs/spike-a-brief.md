# [Codex 작업지시서] Spike A — 네이티브 빌드 관문

## 목표

`ats-check` 프로젝트의 **Day 1-3 스파이크**를 통과시킨다.
성공 기준은 단 하나: **`./gradlew :cli:nativeCompile`이 성공하고, 만들어진 바이너리가 텍스트 파일을 읽어 출력한다.**

판정 로직은 이번 작업의 대상이 **아니다**. 골격과 툴체인만 세운다.

## 배경

- 프로젝트 헌장은 `/home/juhyeonl/workspace/ats-check/CLAUDE.md`. **먼저 읽어라.**
- 이 프로젝트의 최대 리스크는 "기능을 다 만든 뒤 네이티브 빌드가 안 되는 것"이다.
  그래서 코드보다 빌드 파이프라인을 먼저 세운다.
- 로컬 환경 사전 점검 결과 (이미 확인됨, 다시 조사하지 말 것):
  - Java Temurin 21.0.10 있음 / **GraalVM 없음** / **Gradle 없음** / SDKMAN 없음
  - gcc 11.4.0, `/usr/include/zlib.h`, `libz.a` 모두 있음 → native-image 전제조건 충족
  - 메모리 30G, 디스크 882G 여유
  - **`sudo`는 비밀번호를 요구한다. `apt`/`sudo`를 쓰지 말 것.** 홈 디렉토리 설치만 허용.

## 범위

### 1. 툴체인 (홈 디렉토리에만 설치)

1. SDKMAN 설치: `curl -s "https://get.sdkman.io" | bash`
2. GraalVM Community JDK 21 설치.
   - `sdk list java | grep -i graal`로 **실제 존재하는 식별자를 확인한 뒤** 21.x GraalVM CE(`-graalce`)를 설치.
   - `native-image`가 GraalVM 21 배포판에 기본 포함되어 있는지 확인. 없으면 `gu install native-image`.
3. Gradle 설치: `sdk install gradle 8.10.2` (없으면 8.x 최신 안정판).
4. 설치 후 `native-image --version`, `gradle --version`이 동작하는지 확인.
5. **비대화형 셸에서 SDKMAN을 쓰려면** `source "$HOME/.sdkman/bin/sdkman-init.sh"`가 필요하다. 각 명령 앞에 붙여라.

### 2. 프로젝트 골격

작업 디렉토리: `/home/juhyeonl/workspace/ats-check` (이미 `git init` 완료, 브랜치 `main`)

Gradle Kotlin DSL 멀티모듈:

```
ats-check/
├── settings.gradle.kts          # rootProject.name = "ats-check", include("core", "cli")
├── build.gradle.kts             # 공통 설정 (Java 21 toolchain, JUnit5, repositories)
├── gradle.properties
├── gradlew / gradlew.bat / gradle/wrapper/   # `gradle wrapper --gradle-version 8.10.2`로 생성
├── .gitignore                   # build/, .gradle/, *.class, bin/
├── core/
│   ├── build.gradle.kts         # 의존성: 테스트 외 없음
│   └── src/main/java/dev/juhyeonl/atscheck/core/AtsChecker.java
│   └── src/test/java/dev/juhyeonl/atscheck/core/AtsCheckerTest.java
└── cli/
    ├── build.gradle.kts         # Picocli + GraalVM Native Build Tools
    └── src/main/java/dev/juhyeonl/atscheck/cli/CheckCommand.java
```

**패키지 루트는 `dev.juhyeonl.atscheck`로 통일.**

### 3. 의존성 (모두 고정 버전. 동적 버전 `+` 금지)

| 대상 | 버전 | 위치 |
|---|---|---|
| Picocli | 4.7.6 | `cli` |
| picocli-codegen | 4.7.6 | `cli` (annotationProcessor) |
| GraalVM Native Build Tools 플러그인 | `org.graalvm.buildtools.native` 0.10.4 | `cli` |
| JUnit BOM | 5.11.3 | 루트 |
| AssertJ | 3.26.3 | 루트 |

**지정 버전이 실제로 존재하지 않으면** 가장 가까운 안정 버전으로 조정하고, **무엇을 왜 바꿨는지 보고서에 명시**하라. 임의로 메이저 버전을 올리지 말 것.

`core`에는 **어떤 런타임 의존성도 추가하지 마라.** SnakeYAML도 이번에는 넣지 않는다.

### 4. 구현할 코드 (최소한으로)

**`AtsChecker.java` (core)** — 스텁. 아직 판정하지 않는다.
```java
public final class AtsChecker {
    public static String echo(String jobText) { ... }   // 입력을 그대로 반환하거나 trim 정도
}
```
정확한 API는 네가 정하되, **core는 문자열 in / 문자열 out 순수 함수여야 하고 파일·네트워크·System.out을 몰라야 한다.**

**`CheckCommand.java` (cli)** — Picocli 커맨드.
- `--job <path>` : 파일을 UTF-8로 읽어 `AtsChecker`에 넘기고 결과를 stdout에 출력
- `--version` / `-V` : `ats-check 0.1.0-SNAPSHOT` 출력
- `--help` / `-h`
- `--job`이 없고 stdin이 파이프면 stdin을 읽는다 (여유 되면. 안 되면 보고서에 남기고 넘어가라)
- 파일이 없으면 stderr에 메시지 + **exit code 64**
- 정상 종료는 exit code 0
- **네트워크 호출을 절대 넣지 마라.**

**테스트** — `core`에 JUnit5 + AssertJ 스모크 테스트 1~2개. 커버리지는 이번엔 신경 쓰지 마라.

**샘플 파일** — `samples/sample-job.txt`에 짧은 영문 채용공고 텍스트 하나 (10~20줄, 가공 텍스트로 충분).

### 5. 검증 (반드시 직접 실행하고 실제 출력을 보고서에 붙일 것)

```bash
source "$HOME/.sdkman/bin/sdkman-init.sh"
cd /home/juhyeonl/workspace/ats-check
./gradlew build
./gradlew :cli:nativeCompile
BIN=cli/build/native/nativeCompile/ats-check
$BIN --version
$BIN --job samples/sample-job.txt
echo "exit=$?"
$BIN --job /nonexistent.txt ; echo "exit=$?"     # 64 기대
ls -lh $BIN
time $BIN --version                              # 시작 시간 측정
```

`nativeCompile`은 몇 분 걸린다. 중간에 포기하지 마라.

## 수정 금지

- `CLAUDE.md` — 프로젝트 헌장. 절대 건드리지 마라.
- `_briefs/spike-a-brief.md` — 이 지시서.
- **git commit / git push 금지.** 커밋은 Claude(PM)가 검토 후 직접 한다. 작업 트리에 변경만 남겨라.
- `sudo`, `apt`, 시스템 디렉토리 수정 금지.
- 판정 로직(언어/연차/학위/스킬 규칙), 섹션 분류, JSON 출력, PDF/DOCX 파싱, CI 워크플로우 — **전부 이번 범위 밖.** 만들지 마라.

## 완료 조건

1. `./gradlew build` 성공 (테스트 통과 포함)
2. `./gradlew :cli:nativeCompile` 성공
3. 네이티브 바이너리가 `--version`, `--job <파일>`에 정상 동작하고 exit code가 규격대로
4. 위 검증 명령들의 **실제 출력**이 보고서에 있다

## 막혔을 때 (중요)

**멈추지 말고 폴백 경로를 택한 뒤 보고하라.** 이 프로젝트의 원칙은 "완주 > 완성도"다.

- GraalVM CE 설치 실패 → Liberica NIK 21 시도 → 그래도 실패면 실패 원인 상세히 기록하고 JVM 빌드(`./gradlew :cli:installDist`)까지만 완성
- `nativeCompile` 실패 → 오류 전문을 보고서에 붙이고, reflect-config 등 시도한 조치를 기록
- 어떤 경우든 `./gradlew build`(JVM 경로)는 반드시 성공시켜라

## 보고서

`/home/juhyeonl/workspace/ats-check/_briefs/spike-a-report.md`에 아래 형식으로 작성:

```
[Codex 결과 보고서] Spike A

1. 수행한 작업 요약
2. 설치한 툴체인 (정확한 버전 문자열)
3. 변경/생성한 파일 목록
4. 핵심 설계 결정 (패키지 구조, 빌드 스크립트에서 주의한 점)
5. 실행한 검증과 **실제 출력 전문**
   - gradlew build
   - nativeCompile (소요 시간, 바이너리 크기)
   - 바이너리 스모크 테스트 결과 + exit code
   - 시작 시간
6. 지시서와 다르게 한 것 + 이유 (버전 조정 등)
7. 실패했거나 확인 못 한 것
8. 리스크 (특히 PDFBox를 나중에 추가할 때 예상되는 문제)
9. 다음 추천 작업
```

보고서는 **사실만** 적어라. 추정과 사실을 구분해서 표기하라.

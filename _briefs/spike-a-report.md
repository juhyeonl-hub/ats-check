[Codex 결과 보고서] Spike A

1. 수행한 작업 요약

- SDKMAN을 홈 디렉토리에 설치했다.
- SDKMAN으로 GraalVM CE JDK 21과 Gradle 8.10.2를 설치했다.
- Gradle Kotlin DSL 멀티모듈 골격을 생성했다.
- `core`에 순수 문자열 in/out 스텁 `AtsChecker.echo`를 추가했다.
- `cli`에 Picocli 기반 `ats-check` 커맨드를 추가했다.
- `--job <path>`, `--version`/`-V`, `--help`/`-h`, stdin 입력, 파일 없음 exit code 64를 구현했다.
- `./gradlew build`와 `./gradlew :cli:nativeCompile`을 성공시켰다.
- 생성된 네이티브 실행 파일로 샘플 텍스트 파일 읽기와 출력 동작을 확인했다.

2. 설치한 툴체인 (정확한 버전 문자열)

SDKMAN:

```text
SDKMAN!
script: 5.23.0
native: 0.7.34 (linux x86_64)
```

GraalVM Java:

```text
openjdk version "21.0.2" 2024-01-16
OpenJDK Runtime Environment GraalVM CE 21.0.2+13.1 (build 21.0.2+13-jvmci-23.1-b30)
OpenJDK 64-Bit Server VM GraalVM CE 21.0.2+13.1 (build 21.0.2+13-jvmci-23.1-b30, mixed mode, sharing)
```

native-image:

```text
native-image 21.0.2 2024-01-16
GraalVM Runtime Environment GraalVM CE 21.0.2+13.1 (build 21.0.2+13-jvmci-23.1-b30)
Substrate VM GraalVM CE 21.0.2+13.1 (build 21.0.2+13, serial gc)
```

Gradle:

```text
Welcome to Gradle 8.10.2!

Here are the highlights of this release:
 - Support for Java 23
 - Faster configuration cache
 - Better configuration cache reports

For more details see https://docs.gradle.org/8.10.2/release-notes.html


------------------------------------------------------------
Gradle 8.10.2
------------------------------------------------------------

Build time:    2024-09-23 21:28:39 UTC
Revision:      415adb9e06a516c44b391edff552fd42139443f7

Kotlin:        1.9.24
Groovy:        3.0.22
Ant:           Apache Ant(TM) version 1.10.14 compiled on August 16 2023
Launcher JVM:  21.0.2 (GraalVM Community 21.0.2+13-jvmci-23.1-b30)
Daemon JVM:    /home/juhyeonl/.sdkman/candidates/java/21.0.2-graalce (no JDK specified, using current Java home)
OS:            Linux 6.6.87.2-microsoft-standard-WSL2 amd64
```

SDKMAN 설치 전 선행 도구:

- 사실: 시스템에 `zip` 명령이 없어 SDKMAN 설치 스크립트가 1차 실패했다.
- 조치: `sudo`/`apt` 없이 `$HOME/.local/bin/zip`에 Info-ZIP `Zip 3.0 (July 5th 2008)`을 빌드해 배치한 뒤 SDKMAN 설치를 재실행했다.

3. 변경/생성한 파일 목록

- `.gitignore`
- `settings.gradle.kts`
- `build.gradle.kts`
- `gradle.properties`
- `gradlew`
- `gradlew.bat`
- `gradle/wrapper/gradle-wrapper.jar`
- `gradle/wrapper/gradle-wrapper.properties`
- `core/build.gradle.kts`
- `core/src/main/java/dev/juhyeonl/atscheck/core/AtsChecker.java`
- `core/src/test/java/dev/juhyeonl/atscheck/core/AtsCheckerTest.java`
- `cli/build.gradle.kts`
- `cli/src/main/java/dev/juhyeonl/atscheck/cli/CheckCommand.java`
- `samples/sample-job.txt`
- `_briefs/spike-a-report.md`

수정 금지 파일 확인:

- `CLAUDE.md`는 수정하지 않았다.
- `_briefs/spike-a-brief.md`는 수정하지 않았다.

4. 핵심 설계 결정 (패키지 구조, 빌드 스크립트에서 주의한 점)

- 패키지 루트는 `dev.juhyeonl.atscheck`로 통일했다.
- `core`는 `dev.juhyeonl.atscheck.core.AtsChecker`만 두고, 파일 I/O, 네트워크, stdout을 넣지 않았다.
- `AtsChecker.echo(String)`는 입력 문자열을 `strip()`해서 반환하는 순수 함수다.
- `cli`는 `dev.juhyeonl.atscheck.cli.CheckCommand`에서 Picocli 커맨드, 파일 I/O, stdin, stdout/stderr, exit code를 담당한다.
- `core`에는 런타임 의존성을 추가하지 않았다.
- 루트 빌드는 Java 플러그인이 적용된 서브프로젝트에 Java 21 toolchain, Maven Central, JUnit BOM 5.11.3, AssertJ 3.26.3, JUnit Platform 설정을 적용한다.
- `cli` 의존성은 Picocli 4.7.6, picocli-codegen 4.7.6, GraalVM Native Build Tools 0.10.4로 고정했다.
- Picocli native metadata 생성을 위해 `annotationProcessor`와 `-Aproject=${project.group}/${project.name}`를 설정했다.
- Native image 이름은 `ats-check`로 고정했다.
- `sharedLibrary.set(false)`를 명시했다. 이유는 `cli`에 `java-library`가 같이 적용됐을 때 최초 native 산출물이 `ats-check.so` 공유 라이브러리로 생성됐기 때문이다.
- `org.gradle.configuration-cache=true`는 설정하지 않았다. 이유는 GraalVM Native Build Tools 0.10.4의 `generateResourcesConfigFile` 작업이 configuration cache 저장 중 실패했기 때문이다.

5. 실행한 검증과 실제 출력 전문

5.1 `./gradlew build`

```text
> Task :cli:processResources NO-SOURCE
> Task :cli:processTestResources NO-SOURCE
> Task :core:compileJava UP-TO-DATE
> Task :core:processResources NO-SOURCE
> Task :core:classes UP-TO-DATE
> Task :core:jar UP-TO-DATE
> Task :core:assemble UP-TO-DATE
> Task :cli:compileJava UP-TO-DATE
> Task :cli:classes UP-TO-DATE
> Task :cli:jar UP-TO-DATE
> Task :cli:startScripts UP-TO-DATE
> Task :cli:distTar UP-TO-DATE
> Task :cli:distZip UP-TO-DATE
> Task :cli:assemble UP-TO-DATE
> Task :core:compileTestJava UP-TO-DATE
> Task :cli:compileTestJava NO-SOURCE
> Task :core:processTestResources NO-SOURCE
> Task :cli:testClasses UP-TO-DATE
> Task :core:testClasses UP-TO-DATE
> Task :cli:test NO-SOURCE
> Task :cli:check UP-TO-DATE
> Task :cli:build UP-TO-DATE
> Task :core:test UP-TO-DATE
> Task :core:check UP-TO-DATE
> Task :core:build UP-TO-DATE

BUILD SUCCESSFUL in 1s
9 actionable tasks: 9 up-to-date
```

5.2 `./gradlew :cli:nativeCompile`

실측 소요 시간: 17초.

```text
> Task :cli:processResources NO-SOURCE
> Task :core:compileJava UP-TO-DATE
> Task :core:processResources NO-SOURCE
> Task :core:classes UP-TO-DATE
> Task :core:jar UP-TO-DATE
> Task :cli:compileJava UP-TO-DATE
> Task :cli:classes UP-TO-DATE
> Task :cli:jar UP-TO-DATE
> Task :cli:generateResourcesConfigFile UP-TO-DATE

> Task :cli:nativeCompile
[native-image-plugin] GraalVM Toolchain detection is disabled
[native-image-plugin] GraalVM location read from environment variable: JAVA_HOME
[native-image-plugin] Native Image executable path: /home/juhyeonl/.sdkman/candidates/java/21.0.2-graalce/lib/svm/bin/native-image
========================================================================================================================
GraalVM Native Image: Generating 'ats-check' (executable)...
========================================================================================================================
For detailed information and explanations on the build output, visit:
https://github.com/oracle/graal/blob/master/docs/reference-manual/native-image/BuildOutput.md
------------------------------------------------------------------------------------------------------------------------
[1/8] Initializing...                                                                                    (2.8s @ 0.08GB)
 Java version: 21.0.2+13, vendor version: GraalVM CE 21.0.2+13.1
 Graal compiler: optimization level: 2, target machine: x86-64-v3
 C compiler: gcc (linux, x86_64, 11.4.0)
 Garbage collector: Serial GC (max heap size: 80% of RAM)
 1 user-specific feature(s):
 - com.oracle.svm.thirdparty.gson.GsonFeature
------------------------------------------------------------------------------------------------------------------------
Build resources:
 - 17.62GB of memory (57.0% of 30.90GB system memory, determined at start)
 - 16 thread(s) (100.0% of 16 available processor(s), determined at start)
[2/8] Performing analysis...  [*****]                                                                    (4.3s @ 0.30GB)
    3,744 reachable types   (75.2% of    4,982 total)
    4,718 reachable fields  (52.6% of    8,967 total)
   19,162 reachable methods (48.6% of   39,457 total)
    1,172 types,    96 fields, and   860 methods registered for reflection
       57 types,    57 fields, and    52 methods registered for JNI access
        4 native libraries: dl, pthread, rt, z
[3/8] Building universe...                                                                               (0.9s @ 0.37GB)
[4/8] Parsing methods...      [*]                                                                        (0.5s @ 0.41GB)
[5/8] Inlining methods...     [***]                                                                      (0.3s @ 0.47GB)
[6/8] Compiling methods...    [**]                                                                       (3.7s @ 0.32GB)
[7/8] Layouting methods...    [*]                                                                        (1.0s @ 0.40GB)
[8/8] Creating image...       [*]                                                                        (1.3s @ 0.56GB)
   7.23MB (42.53%) for code area:    11,083 compilation units
   9.02MB (53.11%) for image heap:  108,364 objects and 47 resources
 758.84kB ( 4.36%) for other data
  16.99MB in total
------------------------------------------------------------------------------------------------------------------------
Top 10 origins of code area:                                Top 10 object types in image heap:
   4.83MB java.base                                            2.19MB byte[] for code metadata
 985.35kB svm.jar (Native Image)                               1.45MB byte[] for java.lang.String
 946.94kB picocli-4.7.6.jar                                    1.07MB java.lang.String
 117.40kB java.logging                                       871.51kB java.lang.Class
  65.00kB org.graalvm.nativeimage.base                       375.53kB heap alignment
  58.89kB jdk.proxy4                                         321.75kB com.oracle.svm.core.hub.DynamicHubCompanion
  50.77kB jdk.proxy1                                         291.35kB byte[] for general heap data
  45.84kB jdk.proxy3                                         276.52kB java.util.HashMap$Node
  27.06kB jdk.internal.vm.ci                                 228.78kB java.lang.Object[]
  24.91kB java.sql                                           211.17kB java.lang.String[]
  51.27kB for 6 more packages                                  1.80MB for 1003 more object types
------------------------------------------------------------------------------------------------------------------------
Recommendations:
 INIT: Adopt '--strict-image-heap' to prepare for the next GraalVM release.
 HEAP: Set max heap for improved and more predictable memory usage.
 CPU:  Enable more CPU features with '-march=native' for improved performance.
------------------------------------------------------------------------------------------------------------------------
                        0.9s (6.0% of total time) in 152 GCs | Peak RSS: 1.13GB | CPU load: 8.91
------------------------------------------------------------------------------------------------------------------------
Produced artifacts:
 /home/juhyeonl/workspace/ats-check/cli/build/native/nativeCompile/ats-check (executable)
========================================================================================================================
Finished generating 'ats-check' in 15.2s.
[native-image-plugin] Native Image written to: /home/juhyeonl/workspace/ats-check/cli/build/native/nativeCompile

BUILD SUCCESSFUL in 16s
6 actionable tasks: 1 executed, 5 up-to-date
nativeCompile_elapsed_seconds=17
```

5.3 바이너리 스모크 테스트 결과 + exit code

```text
ats-check 0.1.0-SNAPSHOT
Backend Engineer
Acme Hiring Lab
Helsinki, Finland

We are building internal tools for hiring teams across Europe.
The team works with Java 21, Spring Boot, PostgreSQL, and REST APIs.
You will design backend services, review pull requests, and improve deployment quality.

Requirements:
Three or more years of backend engineering experience.
Strong Java skills and practical experience with SQL databases.
Clear written and spoken English.
Comfortable collaborating with product managers and designers.

Nice to have:
Experience with Docker, Kubernetes, or cloud platforms.
Interest in developer tooling and observability.
exit=0
Job file not found: /nonexistent.txt
exit=64
-rwxr-xr-x 1 juhyeonl juhyeonl 17M Aug 15 03:48 cli/build/native/nativeCompile/ats-check
ats-check 0.1.0-SNAPSHOT
$BIN --version  0.00s user 0.00s system 98% cpu 0.004 total
```

5.4 stdin 스모크 테스트

```text
Piped job text
Java backend role
exit=0
```

6. 지시서와 다르게 한 것 + 이유 (버전 조정 등)

- GraalVM CE는 SDKMAN 목록에서 실제 확인된 `21.0.2-graalce`를 설치했다. 지시서의 21.x GraalVM CE 조건과 일치한다.
- Gradle은 지시된 `8.10.2`를 그대로 설치했다.
- Picocli, picocli-codegen, GraalVM Native Build Tools, JUnit BOM, AssertJ 버전은 지시서 버전을 그대로 사용했다.
- SDKMAN 설치 전 `zip` 명령 부재로 설치 스크립트가 실패했다. `sudo`/`apt` 금지 조건 때문에 홈 디렉토리에 Info-ZIP `zip`을 빌드해 PATH에 넣고 재시도했다.
- `org.gradle.configuration-cache=true`는 최종 설정에 넣지 않았다. Native Build Tools 0.10.4의 `generateResourcesConfigFile` 작업이 configuration cache 저장 중 실패했기 때문이다.
- 최초 native 산출물이 공유 라이브러리로 생성되는 문제가 있어 `cli`에 `java-library`가 적용되지 않도록 빌드 구성을 분리하고 `sharedLibrary.set(false)`를 명시했다.

7. 실패했거나 확인 못 한 것

- 최종 기준 실패한 검증은 없다.
- 확인하지 않은 것: macOS/Windows 빌드, CI, PDF/DOCX 파싱, 실제 판정 로직.
- 중간 실패 1: SDKMAN 설치 1차 시도는 `zip` 명령 부재로 실패했다. 홈 디렉토리 Info-ZIP 설치 후 재시도 성공.
- 중간 실패 2: configuration cache 활성화 상태에서 `:cli:nativeCompile`이 실패했다. configuration cache 설정 제거 후 성공.
- 중간 실패 3: 최초 native 산출물이 `ats-check.so` 공유 라이브러리였다. `core`/`cli` 플러그인 적용 분리와 `sharedLibrary.set(false)` 후 실행 파일 생성 성공.

8. 리스크 (특히 PDFBox를 나중에 추가할 때 예상되는 문제)

- 사실: 현재 native image에는 PDFBox/POI/SnakeYAML이 없다.
- 추정: PDFBox 추가 시 reflection config, resource config, font 관련 리소스, `java.desktop` 도달성 때문에 native image 분석 대상과 바이너리 크기가 증가할 수 있다.
- 추정: PDFBox/POI를 `cli`에만 넣더라도 nativeCompile 시간이 길어지고, 일부 parser 경로는 GraalVM에서 추가 metadata가 필요할 수 있다.
- 사실: Native Build Tools 0.10.4와 Gradle configuration cache는 현재 조합에서 충돌했다. CI에 configuration cache를 켜면 같은 실패가 재발할 수 있다.

9. 다음 추천 작업

- `cli`의 exit code와 stderr를 검증하는 최소 통합 테스트를 추가한다.
- 다음 기능 작업 전에 PDFBox만 넣은 별도 native spike를 수행해 리플렉션/리소스 이슈를 먼저 확인한다.
- Day 4-9 범위에서는 `core`에 판정 로직을 추가하되, 파일 I/O와 출력 포매팅은 계속 `cli`에 둔다.

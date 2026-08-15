[Codex 결과 보고서] Spike B

0. 결론: FAIL

1. 수행한 작업 요약

- `cli` 모듈에만 `org.apache.pdfbox:pdfbox:3.0.3`을 추가했다.
- `PdfTextExtractor`를 추가해 PDFBox `Loader.loadPDF` + `PDFTextStripper`로 텍스트를 추출하게 했다.
- `CheckCommand`에 `--resume <path>` 옵션을 추가했다. `.pdf` 입력이면 추출 텍스트 앞 500자를 stdout으로 출력한다.
- PDFBox API로 작은 샘플 PDF 2개를 생성했다.
- JVM 경로는 성공했지만, GraalVM native-image 바이너리에서 PDF 추출은 런타임 fatal error로 실패했다.
- 기존 `--version`, `--job` 네이티브 경로는 정상 동작했다.

2. 변경/생성한 파일 목록 + 브랜치명

- 브랜치: `spike/pdfbox`
- 변경: `cli/build.gradle.kts`
- 변경: `cli/src/main/java/dev/juhyeonl/atscheck/cli/CheckCommand.java`
- 생성: `cli/src/main/java/dev/juhyeonl/atscheck/cli/extract/PdfTextExtractor.java`
- 생성: `cli/src/test/java/dev/juhyeonl/atscheck/cli/extract/PdfTextExtractorTest.java`
- 생성: `samples/resume-simple.pdf` (810 bytes)
- 생성: `samples/resume-multipage.pdf` (1121 bytes)
- 생성: `_briefs/spike-b-report.md`
- `core/`는 수정하지 않았다.

3. JVM 경로 결과 (실제 출력)

명령:

```bash
./gradlew :cli:installDist
cli/build/install/ats-check/bin/ats-check --resume samples/resume-simple.pdf
cli/build/install/ats-check/bin/ats-check --resume samples/resume-multipage.pdf
```

출력:

```text
Jane Candidate
Software Engineer
Java, Gradle, native-image

exit=0
Jane Candidate
Profile
Builds maintainable command line tools.
Focuses on correctness before performance.
Experience
Implemented JVM services for hiring workflows.
Reduced release risk with targeted tests.
Maintained concise technical documentation.

exit=0
```

4. nativeCompile 결과 (실제 출력, 소요 시간)

최종 설정:

- `graalvmNative.metadataRepository.enabled=true`
- `-H:+AddAllCharsets`

최종 명령:

```bash
/usr/bin/time -p ./gradlew :cli:nativeCompile --rerun-tasks
```

핵심 출력:

```text
GraalVM Native Image: Generating 'ats-check' (executable)...
[2/8] Performing analysis...  [***]                                                                      (7.3s @ 0.63GB)
    6,767 reachable types   (80.1% of    8,444 total)
   10,883 reachable fields  (56.8% of   19,156 total)
   32,287 reachable methods (53.0% of   60,934 total)
    2,017 types,   100 fields, and 1,336 methods registered for reflection
       58 types,    58 fields, and    52 methods registered for JNI access
        4 native libraries: dl, pthread, rt, z
  15.65MB (41.61%) for code area:    19,071 compilation units
  20.80MB (55.29%) for image heap:  191,817 objects and 52 resources
   1.17MB ( 3.10%) for other data
  37.62MB in total
Produced artifacts:
 /home/juhyeonl/workspace/ats-check/cli/build/native/nativeCompile/ats-check (executable)
 /home/juhyeonl/workspace/ats-check/cli/build/native/nativeCompile/libawt.so (jdk_library)
 /home/juhyeonl/workspace/ats-check/cli/build/native/nativeCompile/libawt_headless.so (jdk_library)
 /home/juhyeonl/workspace/ats-check/cli/build/native/nativeCompile/libawt_xawt.so (jdk_library)
 /home/juhyeonl/workspace/ats-check/cli/build/native/nativeCompile/libfontmanager.so (jdk_library)
 /home/juhyeonl/workspace/ats-check/cli/build/native/nativeCompile/libjava.so (jdk_library_shim)
 /home/juhyeonl/workspace/ats-check/cli/build/native/nativeCompile/libjavajpeg.so (jdk_library)
 /home/juhyeonl/workspace/ats-check/cli/build/native/nativeCompile/libjvm.so (jdk_library_shim)
 /home/juhyeonl/workspace/ats-check/cli/build/native/nativeCompile/liblcms.so (jdk_library)
Finished generating 'ats-check' in 23.9s.
BUILD SUCCESSFUL in 25s
real 25.21
user 1.25
sys 0.37
```

5. 네이티브 바이너리 PDF 추출 결과 (실제 출력)

명령:

```bash
BIN=cli/build/native/nativeCompile/ats-check
$BIN --resume samples/resume-simple.pdf
$BIN --resume samples/resume-multipage.pdf
```

`samples/resume-simple.pdf` 출력:

```text
simple_exit=99
Fatal error reported via JNI: Could not allocate library name

Printing instructions (ip=0x000055fc7954ea78):
  0x000055fc7954e978: 0x24 0x10 0xeb 0x88 0x48 0x89 0x6c 0x24 0x40 0x48 0x89 0x54 0x24 0x38 0x48 0x89
...
Heap chunks: E=eden, S=survivor, O=old, F=free; A=aligned chunk, U=unaligned chunk; T=to space
```

`samples/resume-multipage.pdf` 출력:

```text
multipage_exit=99
Fatal error reported via JNI: Could not allocate library name

Printing instructions (ip=0x000058e48752ca78):
  0x000058e48752c978: 0x24 0x10 0xeb 0x88 0x48 0x89 0x6c 0x24 0x40 0x48 0x89 0x54 0x24 0x38 0x48 0x89
...
Heap chunks: E=eden, S=survivor, O=old, F=free; A=aligned chunk, U=unaligned chunk; T=to space
```

사실: 최종 네이티브 바이너리는 두 PDF 모두에서 텍스트를 출력하지 못했다.

6. 회귀 확인 (--version, --job이 여전히 동작하는가)

명령:

```bash
BIN=cli/build/native/nativeCompile/ats-check
$BIN --version
$BIN --job samples/sample-job.txt
```

출력:

```text
ats-check 0.1.0-SNAPSHOT
version_exit=0
job_exit=0
Backend Engineer
Acme Hiring Lab
Helsinki, Finland

We are building internal tools for hiring teams across Europe.
The team works with Java 21, Spring Boot, PostgreSQL, and REST APIs.
...
```

7. 비용 비교 표: baseline(17s/17MB/0.004s) vs PDFBox 추가 후

| 항목 | baseline | PDFBox 추가 후 | 비고 |
|---|---:|---:|---|
| `nativeCompile` | 17s | 25.21s | 약 +8.21s |
| 바이너리 크기 | 17MB | 38M (`ls -lh`) | Native Image 출력상 image total 37.62MB |
| sidecar 포함 산출물 | 없음 또는 미확인 | 43M | `libawt*.so`, `libfontmanager.so`, `liblcms.so` 등 생성 |
| `time $BIN --version` | 0.004s | `real 0.00` | `time -p` 해상도 한계 |
| `--version` 20회 평균 | 미측정 | 0.006751s | `date +%s%N` 기반 보조 측정 |

8. 시도한 조치와 각각의 결과

1. PDFBox 제공 GraalVM metadata 확인
   - `pdfbox-3.0.3.jar`, `fontbox-3.0.3.jar`, `pdfbox-io-3.0.3.jar`에서 `META-INF/native-image` 항목 없음.

2. GraalVM Reachability Metadata Repository 활성화
   - `graalvmNative { metadataRepository { enabled.set(true) } }` 추가.
   - `nativeCompile`은 성공했지만 PDF 실행 실패는 동일했다.

3. 오류가 지목하는 클래스만 수동 metadata 추가
   - `MissingReflectionRegistrationError`, `ClassNotFoundException`, `NoClassDefFoundError`는 발생하지 않았다.
   - 수동 `reflect-config.json` / `resource-config.json`로 넣을 단일 클래스 후보가 없었다.

4. charset 조정
   - 초기 네이티브 실행은 아래 경고 후 fatal error가 났다.

```text
WARNING: Charset is not supported: Windows-1252, falling back to ISO-8859-1
java.nio.charset.UnsupportedCharsetException: Windows-1252
...
Fatal error reported via JNI: Could not allocate library name
```

   - `-H:+AddAllCharsets` 추가 후 `Windows-1252` 경고는 사라졌지만 fatal error는 유지됐다.

5. `--initialize-at-build-time` 조정
   - 시도: `--initialize-at-build-time=java.awt.image.ColorModel,java.awt.image.Raster,org.apache.pdfbox.pdmodel.PDDocument`
   - 결과: nativeCompile 실패.
   - GraalVM이 build-time에 의도치 않게 초기화된 클래스를 22개 이상 보고했다. 대표 출력:

```text
Error: Classes that should be initialized at run time got initialized during image building:
 org.apache.commons.logging.impl.LogFactoryImpl was unintentionally initialized at build time.
java.awt.color.ICC_ProfileRGB was unintentionally initialized at build time.
java.awt.image.SampleModel was unintentionally initialized at build time.
org.apache.pdfbox.pdmodel.graphics.color.PDColor was unintentionally initialized at build time.
sun.java2d.StateTrackableDelegate$2 was unintentionally initialized at build time.
java.awt.Image was unintentionally initialized at build time.
java.awt.image.DataBufferByte was unintentionally initialized at build time.
java.awt.image.DataBuffer was unintentionally initialized at build time.
java.awt.image.DataBufferInt was unintentionally initialized at build time.
java.awt.color.ICC_ProfileGray was unintentionally initialized at build time.
java.awt.image.SinglePixelPackedSampleModel was unintentionally initialized at build time.
sun.awt.image.IntegerInterleavedRaster was unintentionally initialized at build time.
java.awt.Toolkit was unintentionally initialized at build time.
java.awt.image.BufferedImage was unintentionally initialized at build time.
sun.java2d.StateTrackableDelegate was unintentionally initialized at build time.
org.apache.commons.logging.LogFactory was unintentionally initialized at build time.
org.apache.commons.logging.impl.Jdk14Logger was unintentionally initialized at build time.
sun.awt.image.IntegerComponentRaster was unintentionally initialized at build time.
java.awt.color.ColorSpace$BuiltInSpace was unintentionally initialized at build time.
java.awt.Rectangle was unintentionally initialized at build time.
java.awt.color.ICC_Profile was unintentionally initialized at build time.
java.awt.image.ComponentSampleModel was unintentionally initialized at build time.
```

   - 판단: 이 방향은 연쇄 클래스 수가 많고 `java.desktop`/AWT 초기화까지 끌고 들어와 유지보수 가능성이 낮다. 더 넓은 수동 metadata/초기화 확장은 중단했다.

9. 리스크 / 확인 못 한 것

- 사실: JVM에서는 PDFBox 텍스트 추출이 정상이다.
- 사실: native-image 빌드 자체는 `metadataRepository` + `-H:+AddAllCharsets` 상태에서 성공한다.
- 사실: 네이티브 런타임 PDF 추출은 `java.awt.image.ColorModel`/`Raster`/`PDDocument` 초기화 경로에서 JNI fatal error로 실패한다.
- 사실: PDFBox 추가 후 native image가 `java.desktop`을 포함하고 AWT 관련 sidecar `.so` 파일들을 생성한다.
- 확인 못 한 것: tracing agent로 AWT 관련 metadata를 대량 수집했을 때 성공하는지 여부. 타임박스와 유지보수성 기준상 진행하지 않았다.
- 확인 못 한 것: PDFBox 버전 변경(예: 다른 3.0.x 또는 2.x)으로 `PDDocument` static initializer/AWT 문제가 완화되는지 여부. 이번 지시 범위에서는 3.0.3을 사용했다.

10. 내 판단: v0.1에 PDF를 넣어야 하는가, 빼야 하는가 + 근거

판단: v0.1에서는 PDF 지원을 빼는 것이 맞다.

근거:

- 네이티브 바이너리에서 두 샘플 PDF 모두 실제 텍스트 추출에 실패했다.
- 실패 지점이 단순 reflection/resource 누락이 아니라 PDFBox의 `PDDocument` static initializer가 `java.desktop`/AWT 이미지 처리 경로를 끌고 들어오는 문제다.
- `--initialize-at-build-time` 조정은 20개가 넘는 연쇄 초기화 클래스를 만들었고, 이 방향은 작은 CLI 도구의 native-image 유지보수 비용에 맞지 않는다.
- 비용도 baseline 대비 커졌다: `nativeCompile` 17s -> 25.21s, 바이너리 17MB -> 38M, sidecar 포함 43M.
- `profile.yml` 기반 Stage 1~2는 PDF 없이 동작해야 하므로, v0.1에서는 PDF를 제외하고 텍스트 입력 경로를 유지하는 편이 안전하다.

추가 테스트:

```text
./gradlew :cli:test
BUILD SUCCESSFUL in 3s

./gradlew test
BUILD SUCCESSFUL in 623ms
```

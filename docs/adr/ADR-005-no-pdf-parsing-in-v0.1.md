# ADR-005: v0.1에서 이력서 파싱(PDF/DOCX)을 지원하지 않는다

- 상태: **채택됨**
- 날짜: 2026-08-15
- 근거 실험: [`_briefs/spike-b-report.md`](../../_briefs/spike-b-report.md)

## 맥락

`ats-check`는 GraalVM native-image로 빌드한 **단일 실행 파일**을 GitHub Releases에 올린다.
사용자가 JVM 설치 없이 바이너리 하나를 내려받아 실행하는 것이 배포 모델이다.

초기 기술 스택은 이력서 파싱에 Apache PDFBox(PDF)와 Apache POI(DOCX)를 포함했다.
PDFBox가 리플렉션을 광범위하게 사용한다는 점이 알려져 있었으므로,
**기능 개발 이전에** 네이티브 호환성을 검증하는 스파이크를 수행했다.

## 실험

PDFBox 3.0.3을 `cli` 모듈에만 추가하고, 텍스트 PDF 2종(1페이지 / 다중 페이지)으로 검증했다.

| 경로 | 결과 |
|---|---|
| JVM (`installDist`) | ✅ 텍스트 추출 성공 |
| `nativeCompile` 빌드 | ✅ 성공 (25.2s) |
| **네이티브 바이너리 실행** | ❌ `Fatal error reported via JNI: Could not allocate library name` |

시도한 조치와 결과:

1. PDFBox JAR의 `META-INF/native-image` 확인 → **메타데이터 없음**
2. GraalVM Reachability Metadata Repository 활성화 → 빌드는 통과, **런타임 실패 동일**
3. 수동 `reflect-config.json` 추가 → **불가.** `MissingReflectionRegistrationError`나
   `ClassNotFoundException`이 발생하지 않아 등록할 대상 클래스를 특정할 수 없었다
4. `-H:+AddAllCharsets` → `Windows-1252` 경고는 해소, **fatal error는 유지**
5. `--initialize-at-build-time` 조정 → **빌드 실패.** 의도치 않게 build-time 초기화된 클래스가
   22개 이상 연쇄 보고됨 (`java.awt.Toolkit`, `java.awt.image.BufferedImage`,
   `java.awt.color.ICC_Profile`, `sun.java2d.StateTrackableDelegate` …)

## 결정

**v0.1에서 이력서 파일 파싱을 지원하지 않는다.** PDF·DOCX 모두 v0.2 백로그로 옮긴다.

## 근거

### 1. 단일 바이너리 배포 모델이 깨진다 (결정적)

PDFBox를 추가하면 native-image가 실행 파일 옆에 **AWT 사이드카 라이브러리 8개**를 생성한다:

```
ats-check  libawt.so  libawt_headless.so  libawt_xawt.so
libfontmanager.so  libjavajpeg.so  libjava.so  libjvm.so  liblcms.so
```

바이너리만 다른 위치로 복사해 실행하면 이렇게 죽는다:

```
$ cp ats-check /tmp/ && /tmp/ats-check --resume resume.pdf
Exception in thread "main" java.lang.UnsatisfiedLinkError: No awt in java.library.path
```

**즉 런타임 JNI 오류를 고치더라도 이 문제는 남는다.** GitHub Releases에 파일 1개가 아니라
9개를 올리고 사용자에게 압축 해제와 경로 유지를 요구해야 한다.
이는 "바이너리 하나 받아서 바로 실행"이라는 제품 전제와 정면으로 충돌한다.

### 2. 실패 지점이 메타데이터 누락이 아니다

단순한 리플렉션 등록 누락이었다면 대상 클래스를 지정해 해결할 수 있다.
그러나 실제 원인은 PDFBox의 `PDDocument` 초기화 경로가 `java.desktop`/AWT 이미지 처리
스택 전체를 끌고 들어오는 구조적 문제다. 텍스트만 추출하는데 컬러 프로파일과 폰트 매니저가
따라온다. 수동 메타데이터로 좁혀 나갈 수 있는 종류의 실패가 아니다.

### 3. 비용이 이득에 비해 크다

| 항목 | baseline | PDFBox 추가 후 |
|---|---:|---:|
| `nativeCompile` | 17s | 25.2s |
| 바이너리 | 17MB | 38MB |
| 배포 산출물 총합 | 17MB (파일 1개) | 43MB (파일 9개) |

### 4. 제품이 PDF 없이도 성립한다

`CLAUDE.md` §7이 이미 이 안전장치를 설계해두었다.
`profile.yml`만 있으면 Stage 1(언어·연차·학위)과 Stage 2(시니어리티)가 완전히 동작한다.
이 두 단계가 도구의 핵심 가치인 **하드 필터**이며, 이력서 파일 없이 판정된다.
Stage 3(스킬 갭)도 `profile.skills`로 수행할 수 있다.

이력서 파싱은 편의 기능이지 제품의 전제가 아니다.

### 5. DOCX(POI)도 함께 연기한다

POI는 별도로 검증하지 않았다. 그러나 PDFBox보다 의존성이 무겁고 AWT 결합도가 높아
같은 실패 유형이 예상된다. 3주 일정에서 두 번째 스파이크에 시간을 쓰는 것보다,
이력서 파싱 경로 전체를 v0.2로 일관되게 미루는 편이 낫다.
(추정이며 실측하지 않았다. v0.2에서 검증한다.)

## 결과

- `--resume` 옵션은 v0.1에서 제공하지 않는다
- `cli/extract/` 패키지는 v0.1 아키텍처에서 제외한다
- 스킬 매칭은 `profile.yml`의 `skills` 목록을 사용한다
- 실험 코드는 `spike/pdfbox` 브랜치에 보존하며 `main`에 병합하지 않는다

## v0.2에서 재검토할 선택지

1. **tracing agent로 AWT 메타데이터 대량 수집** — 이번엔 유지보수성 문제로 중단했다
2. **PDF 텍스트 추출기 직접 구현** — 텍스트 레이어만 읽으면 AWT가 불필요하다
3. **의존성 없는 경량 라이브러리 탐색**
4. **JVM 폴백 배포 분리** — 네이티브 바이너리는 텍스트 전용, PDF는 별도 JAR
   (JVM 경로에서는 PDFBox가 정상 동작함을 확인했다)

## 교훈

기능을 완성한 뒤 네이티브 빌드를 시도했다면 Day 18에 이 문제를 발견했을 것이다.
**Day 1-3에 스파이크로 검증했기 때문에 Day 2에 3줄짜리 스코프 결정으로 끝났다.**

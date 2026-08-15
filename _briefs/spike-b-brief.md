# [Codex 작업지시서] Spike B — PDFBox 네이티브 호환성 판단

## 목표

**PDFBox를 GraalVM native-image에 넣을 수 있는가**를 판단한다.

이것은 기능 구현이 아니라 **의사결정용 실험**이다.
"된다"도 성공이고 **"안 된다"도 성공이다.** 실패하면 v0.1에서 PDF 지원을 빼기로 결정하면 된다
(`CLAUDE.md` §7이 이미 그 안전장치를 명시하고 있다: profile.yml만으로 Stage 1~2가 동작해야 한다).

**억지로 성공시키려고 시간을 쓰지 마라. 타임박스는 아래 §타임박스를 지켜라.**

## 배경 (이미 확인된 사실 — 다시 조사하지 말 것)

- Spike A 통과. `main` 브랜치에 커밋 `2e78961`로 골격이 있다.
- 툴체인: GraalVM CE 21.0.2 (`~/.sdkman/candidates/java/21.0.2-graalce`), Gradle 8.10.2
- 비대화형 셸에서는 `source "$HOME/.sdkman/bin/sdkman-init.sh"` 필요
- **현재 baseline: nativeCompile 17초, 바이너리 17MB, 시작 0.004s.** 이 값과 비교해서 보고하라.
- `org.gradle.configuration-cache`는 NBT 0.10.4와 충돌한다. 켜지 마라.
- `sudo` / `apt` 사용 금지 (비밀번호 요구됨).

## 작업 브랜치 (중요)

```bash
git checkout -b spike/pdfbox
```

**모든 작업을 이 브랜치에서 하라. `main`을 건드리지 마라.**
실패하면 브랜치째 버릴 수 있어야 한다. 커밋은 해도 되지만 **push 금지.**

## 범위

### 1. PDFBox 추가

- `cli` 모듈에만 `org.apache.pdfbox:pdfbox:3.0.3` 추가 (버전이 없으면 3.0.x 최신 안정판, 조정 시 보고서에 명시)
- **`core`에는 절대 넣지 마라.** core는 의존성 0을 유지한다.
- `cli/src/main/java/dev/juhyeonl/atscheck/cli/extract/PdfTextExtractor.java` 신설
  - `String extract(Path pdf)` — PDFBox `Loader.loadPDF` + `PDFTextStripper`
- `CheckCommand`에 `--resume <path>` 옵션 추가
  - `.pdf`면 `PdfTextExtractor`로 텍스트 추출해서 stdout에 출력 (앞 500자 정도면 충분)
  - 판정 로직은 여전히 만들지 마라. 추출만 확인한다.

### 2. 테스트용 PDF 준비

시스템에 적당한 PDF가 없으면 **PDFBox API로 직접 생성**하라 (테스트 코드 또는 일회성 스크립트).
- 최소 2종: (a) 단순 1페이지 텍스트, (b) 여러 단락 + 여러 페이지
- 생성한 PDF는 `samples/`에 두고 커밋하라 (크기가 작아야 함, 각 100KB 이하)

### 3. 핵심 검증 — JVM에서 먼저, 그다음 네이티브

**반드시 이 순서로 하라.** JVM에서 안 되는 걸 네이티브에서 디버깅하면 원인 분리가 안 된다.

```bash
source "$HOME/.sdkman/bin/sdkman-init.sh"
cd /home/juhyeonl/workspace/ats-check

# (1) JVM 경로에서 PDF 추출이 되는가
./gradlew :cli:installDist
cli/build/install/ats-check/bin/ats-check --resume samples/<생성한>.pdf
echo "exit=$?"

# (2) 네이티브 빌드가 되는가
time ./gradlew :cli:nativeCompile

# (3) 네이티브 바이너리에서 PDF 추출이 되는가  ← 진짜 관문
BIN=cli/build/native/nativeCompile/ats-check
$BIN --resume samples/<생성한>.pdf ; echo "exit=$?"
$BIN --resume samples/<복잡한>.pdf ; echo "exit=$?"

# (4) 기존 기능이 안 깨졌는가 (회귀 확인)
$BIN --version ; $BIN --job samples/sample-job.txt >/dev/null ; echo "exit=$?"

# (5) 비용 측정
ls -lh $BIN
time $BIN --version
```

**(3)이 이 스파이크의 전부다.** native-image는 빌드가 성공해도 **런타임에** `ClassNotFoundException` /
`MissingReflectionRegistrationError` / 폰트 로딩 오류로 죽는 경우가 흔하다. 빌드 성공만 보고
"된다"고 보고하지 마라. 반드시 바이너리를 실행해서 실제 텍스트가 나오는 것을 확인하라.

### 4. 실패했을 때 (타임박스 안에서만)

실패 시 아래를 **순서대로** 시도하고, 각 시도의 결과를 기록하라:

1. PDFBox가 제공하는 GraalVM metadata가 있는지 확인 (`META-INF/native-image` 포함 여부)
2. GraalVM Reachability Metadata Repository 활성화:
   `graalvmNative { metadataRepository { enabled.set(true) } }`
3. 오류가 지목하는 클래스만 `reflect-config.json` / `resource-config.json`에 수동 추가
4. `--initialize-at-build-time` / `--initialize-at-run-time` 조정
5. 그래도 안 되면 **중단하고 실패로 보고하라**

### 타임박스

- **PDFBox 관련 총 작업 시간 90분.** 넘으면 그 시점 상태로 보고서를 쓰고 멈춰라.
- 위 4단계 중 3번(수동 metadata)에서 오류 클래스가 **10개를 넘으면 즉시 중단**하고 실패로 판정하라.
  그건 유지보수 불가능한 방향이라는 신호다.

## 수정 금지

- `CLAUDE.md` — 프로젝트 헌장
- `_briefs/spike-a-brief.md`, `_briefs/spike-a-report.md`
- `core/` 전체 — 이번 작업에서 core는 손대지 않는다
- `git push` 금지, `main` 브랜치 변경 금지
- `sudo`, `apt` 금지
- **판정 로직, 섹션 분류, JSON 출력, POI/DOCX, CI 워크플로우 — 전부 범위 밖**

## 완료 조건

아래 중 하나로 **명확히 결론**을 내는 것이 완료 조건이다:

- **PASS** — 네이티브 바이너리가 두 PDF 모두에서 텍스트를 추출했다
- **PASS(조건부)** — 특정 metadata 설정을 넣으면 동작한다 (설정 내용을 정확히 기록)
- **FAIL** — 타임박스 안에 동작시키지 못했다 (오류 전문과 시도 목록 기록)

## 보고서

`/home/juhyeonl/workspace/ats-check/_briefs/spike-b-report.md`

```
[Codex 결과 보고서] Spike B

0. 결론: PASS / PASS(조건부) / FAIL   ← 맨 위에, 한 줄로
1. 수행한 작업 요약
2. 변경/생성한 파일 목록 + 브랜치명
3. JVM 경로 결과 (실제 출력)
4. nativeCompile 결과 (실제 출력, 소요 시간)
5. **네이티브 바이너리 PDF 추출 결과 (실제 출력)** ← 가장 중요
6. 회귀 확인 (--version, --job이 여전히 동작하는가)
7. 비용 비교 표: baseline(17s/17MB/0.004s) vs PDFBox 추가 후
8. 시도한 조치와 각각의 결과 (실패했다면)
9. 리스크 / 확인 못 한 것
10. 내 판단: v0.1에 PDF를 넣어야 하는가, 빼야 하는가 + 근거
```

사실과 추정을 구분해서 표기하라. **바이너리를 실제로 실행한 출력이 없으면 PASS로 보고하지 마라.**

# [Codex 작업지시서] Task 6 — save / open / init 서브커맨드 (Day 10-13의 2/3)

## 목표

`CLAUDE.md` §8의 서브커맨드 3개와 공고 파일 저장소를 구현한다.

**`save`가 배치 모드의 전제다.** 본문만 저장하면 나중에 판정 결과를 봐도
원래 공고로 돌아갈 방법이 없어서 배치 모드가 무용지물이 된다.

배치 모드(`--job-dir`)와 배치 출력은 **다음 태스크**다.

## 배경 (확인된 사실 — 다시 조사하지 말 것)

- `main` 브랜치, 커밋 `11cde0e`. 테스트 142개 통과.
- `cli`는 Picocli 4.7.6 + SnakeYAML 2.3 + GraalVM Native Build Tools 0.10.4
- `AtsCheckCli`에 서브커맨드를 붙일 자리가 이미 있다
- `AtsChecker.check(JobPosting, Profile) -> CheckResult`, `ProfileLoader`가 동작한다
- 네이티브 바이너리는 현재 **단일 파일 19MB**다. 이 상태를 유지해야 한다
- `core`는 런타임 의존성 0개. **`core`를 건드리지 마라.**

## 절대 제약 — AWT 금지 (§8, ADR-005)

**`java.awt.Toolkit.getSystemClipboard()`와 `java.awt.Desktop.browse()`를 쓰지 마라.**

`java.desktop`을 끌어와 네이티브 이미지에 사이드카 `.so`를 만들고,
단일 바이너리 배포가 깨진다. headless 환경(SSH·CI·X 없는 WSL)에서도 죽는다.
PDFBox에서 이미 겪은 실패다 (ADR-005).

**외부 명령에 위임하라:**

| 용도 | 우선순위대로 시도 |
|---|---|
| 클립보드 읽기 | `wl-paste` → `xclip -selection clipboard -o` → `pbpaste` → `powershell.exe -c Get-Clipboard` |
| URL 열기 | `wslview` → `xdg-open` → `open` → `cmd.exe /c start` |

- `ProcessBuilder`로 실행하고, **명령이 없으면 다음 후보로 넘어간다**
- 전부 실패하면 **stdin으로 폴백**하고 stderr에 이유를 안내한다
- 타임아웃을 걸어라 (5초). 명령이 멈추면 CLI가 같이 멈춘다
- **인터페이스로 추상화하라.** 테스트에서 가짜 구현을 주입할 수 있어야 한다

## 만들 것

```
cli/src/main/java/dev/juhyeonl/atscheck/cli/
├── command/
│   ├── SaveCommand.java
│   ├── OpenCommand.java
│   └── InitCommand.java
├── store/
│   ├── FrontMatter.java      # record
│   ├── JobFile.java          # record: FrontMatter + body
│   ├── JobFileParser.java
│   ├── JobFileWriter.java
│   └── JobStore.java         # jobs/ 디렉토리 접근
└── platform/
    ├── ClipboardReader.java  # 인터페이스 + 기본 구현
    └── BrowserOpener.java    # 인터페이스 + 기본 구현
```

---

## 1. 공고 파일 포맷 (§8)

```
---
url: https://linkedin.com/jobs/view/12345
company: Wolt
title: Backend Engineer (Kotlin)
saved_at: 2026-08-15T14:32:00+03:00
status: new
---

We are looking for a Backend Engineer...
```

**파싱 규칙 (§8이 명시):**
- 프론트매터가 없으면 **본문 전체를 공고로 취급** (하위 호환)
- **알 수 없는 키는 무시하되 재저장 시 보존한다** ← 반드시 구현하라
- `status` 기본값은 `new`

**구현 규칙:**
- 프론트매터는 파일이 `---` 로 시작할 때만 인정한다. 닫는 `---`가 없으면 프론트매터가 아니다
- SnakeYAML로 **`Map<String, Object>`까지만** 읽어라 (§7 규칙, 클래스 바인딩 금지)
- `FrontMatter`에 알려진 필드 + `Map<String,Object> extra`를 두고 왕복에서 보존하라
- 쓸 때는 알려진 키를 **고정 순서**로 쓰고(`url, company, title, saved_at, status`),
  그 뒤에 `extra`를 쓴다. 순서가 안정적이어야 diff가 읽힌다
- 값에 콜론·따옴표가 있으면 정확히 인용하라. **직접 문자열 조립을 하되 이스케이프를 지켜라**

---

## 2. `save`

```bash
ats-check save --url "https://linkedin.com/jobs/view/12345"
pbpaste | ats-check save --url "..."
ats-check save --url "..." --jobs-dir ./myjobs
```

**동작:**
1. 본문을 읽는다: **stdin이 파이프면 stdin**, 아니면 클립보드
   (파이프 입력이 있으면 그것이 명시적 의도이므로 우선한다)
2. 본문이 공백뿐이면 **exit 64**
3. `--url`은 **선택**이다. 없으면 프론트매터에서 생략한다
   (URL 없이 저장하는 것도 유효하다. 나중에 손으로 채울 수 있다)
4. `company` / `title` 추출:
   - `title` = 첫 번째 비어 있지 않은 줄
   - `company` = 두 번째 비어 있지 않은 줄이 **50자 이하이고 문장 종결 부호로 끝나지 않으면** 채택.
     아니면 **빈 값으로 둔다**
   - **추출에 실패해도 오류가 아니다.** §8이 "실패하면 빈 값으로 둔다"고 명시한다
5. `saved_at` = 현재 시각, ISO-8601 오프셋 포함
6. 파일명: `jobs/<company>-<title>.md`
   - slug화: 소문자, 공백→`-`, 영숫자와 `-`만 남김, 연속 `-` 축약, 앞뒤 `-` 제거
   - `company`가 비면 `<title>.md`, 둘 다 비면 `job-<타임스탬프>.md`
   - 길이 제한 80자
   - **충돌 시 `-2`, `-3` 접미사**
7. 저장 후 stdout에 저장 경로 한 줄을 출력한다

`jobs/` 디렉토리가 없으면 만든다. 위치는 현재 작업 디렉토리 기준, `--jobs-dir`로 override.

---

## 3. `open`

```bash
ats-check open wolt          # 파일명 부분 일치
ats-check open --all-apply   # APPLY 판정 전부
```

**`open <pattern>`:**
- `jobs/` 안에서 **파일명에 pattern이 포함된** 파일을 찾는다 (대소문자 무시)
- 정확히 하나면 그 파일의 `url`을 연다
- 여러 개면 **목록을 stderr에 출력하고 exit 64** (모호한 것을 임의로 고르지 않는다)
- 없으면 exit 64
- `url`이 비어 있으면 stderr에 안내하고 exit 64

**`open --all-apply`:**
- `jobs/`의 모든 공고를 판정하고 `APPLY`인 것의 URL을 연다
- **열기 전에 개수를 stderr에 출력한다** (`opening 7 postings...`)
- **10개를 넘으면 열지 말고 목록만 출력하고 exit 64.**
  브라우저 탭 폭탄을 막는다. `--force`를 주면 그래도 연다
- 하나도 없으면 안내 후 exit 0 (오류가 아니다)

`--all-apply`와 pattern을 동시에 주면 exit 64.

---

## 4. `init`

```bash
ats-check init
ats-check init --force
```

- `~/.config/ats-check/profile.yml`을 만든다 (`XDG_CONFIG_HOME` 존중)
- **이미 있으면 덮어쓰지 않고 exit 64** + 안내. `--force`면 덮어쓴다
- 상위 디렉토리가 없으면 만든다
- 내용은 §7의 예시를 그대로 쓰되 **주석으로 각 필드를 설명**하라:

```yaml
# ats-check profile
# https://github.com/juhyeonl/ats-check

years_experience: 2         # your years of professional experience
years_tolerance: 1          # how many years above yours still counts as a match
max_seniority: mid          # junior | mid | senior | lead
languages: [english]        # languages you can work in
degree: bachelor            # none | bachelor | master | phd
skills:
  - java
  - spring boot
```

- 생성 후 stdout에 경로를 출력한다
- **대화형 질문을 하지 마라.** 파이프·CI에서 멈춘다. 템플릿을 쓰고 편집을 안내한다

---

## 5. 종료 코드 (§8 개정판)

`check`만 판정 코드를 쓴다. **`save`/`open`/`init`은 다르다:**

| 코드 | 의미 |
|---|---|
| 0 | 성공 |
| 64 | 사용법 오류 |
| 70 | 내부 오류 |

`save` 성공이 0을 반환하는 것이 "이 공고는 APPLY"와 구분되어야 한다.

---

## 6. 테스트

**클립보드·브라우저는 인터페이스를 통해 가짜로 주입하라. 실제 명령을 실행하지 마라.**
파일 시스템은 JUnit의 `@TempDir`를 쓰라.

### 프론트매터
1. 프론트매터가 있는 파일을 파싱한다
2. **프론트매터가 없는 파일은 본문 전체가 body가 된다**
3. **알 수 없는 키가 왕복(파싱→쓰기→파싱)에서 보존된다** ← §8 요구
4. `status`가 없으면 `new`가 기본값이다
5. 값에 콜론이 포함돼도 왕복이 깨지지 않는다 (`title: Engineer: Backend`)
6. 여는 `---`만 있고 닫는 `---`가 없으면 프론트매터로 취급하지 않는다

### save
7. stdin 파이프로 본문을 주면 파일이 만들어지고 exit **0**
8. 클립보드(가짜)에서 읽는다 — stdin이 없을 때
9. 빈 본문 → exit **64**
10. `--url` 없이도 저장된다
11. 파일명이 slug화된다 (`Wolt` + `Backend Engineer (Kotlin)` → `wolt-backend-engineer-kotlin.md`)
12. **같은 이름이 있으면 `-2` 접미사가 붙는다**
13. company를 추출할 수 없으면 빈 값으로 저장된다 (오류 아님)

### open
14. 정확히 하나 매칭 → 가짜 opener가 그 URL로 호출된다, exit **0**
15. 여러 개 매칭 → exit **64**, 목록이 stderr에 나온다
16. 매칭 없음 → exit **64**
17. `--all-apply` → APPLY인 것만 열린다
18. `--all-apply`로 10개 초과 → 열지 않고 exit **64**

### init
19. 파일이 없으면 생성하고 exit **0**
20. 이미 있으면 exit **64**, 파일이 변경되지 않는다
21. `--force`면 덮어쓴다
22. 생성된 파일을 `ProfileLoader`가 읽을 수 있다 ← 왕복 검증

## 수정 금지

- `CLAUDE.md`, `docs/adr/`, `_reviews/`, `_briefs/` 기존 파일
- **`core/` 전체** — 판정 로직은 완성됐다. 문제를 발견하면 보고만 하라
- `core/src/test/resources/golden/`
- `CheckCommand`의 **기존 동작과 종료 코드** — 서브커맨드 등록을 위한 변경만 허용
- `--json` 스키마
- `git commit` / `git push` 금지
- 배치 모드(`--job-dir`), 배치 출력, OSC 8, `list`/`mark` — **다음 태스크**

## 완료 조건

1. `./gradlew build` 성공
2. **기존 142개 테스트가 그대로 통과**
3. 위 22개 테스트가 모두 통과
4. `core` 런타임 의존성 0개
5. `./gradlew :cli:nativeCompile` 성공
6. **네이티브 산출물이 여전히 단일 파일이다** (`ls cli/build/native/nativeCompile/` → `ats-check` 하나)
   — 사이드카 `.so`가 하나라도 생기면 **즉시 멈추고 보고하라**
7. 네이티브 바이너리로 `save` → `open` 왕복이 동작한다
   (실제 브라우저를 띄우지 말고, `open`이 URL을 찾아내는 것까지만 확인하면 된다)

## 보고서

`_briefs/task-6-report.md`

```
[Codex 결과 보고서] Task 6

1. 수행한 작업 요약
2. 생성/변경한 파일 목록
3. 클립보드/브라우저를 어떤 명령으로 어떻게 위임했는가 (폴백 순서, 타임아웃)
4. 프론트매터 왕복에서 알 수 없는 키를 어떻게 보존했는가
5. 파일명 slug화와 충돌 처리 방식
6. 테스트 결과 (총 개수, 기존 142개 유지 확인)
7. **네이티브 빌드 결과: 산출물 목록 / 바이너리 크기**   ← 실제 출력
8. 스스로 결정한 것 + 근거
9. 남은 리스크
10. 다음 추천 작업
```

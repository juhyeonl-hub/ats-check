[Codex 결과 보고서] Task 6

1. 수행한 작업 요약
- `save`, `open`, `init` Picocli 서브커맨드를 추가했다.
- `jobs/` 공고 저장소와 YAML front matter 파싱/쓰기 계층을 추가했다.
- 클립보드 읽기와 브라우저 열기를 `java.desktop` 없이 외부 명령 위임 인터페이스로 분리했다.
- 요구된 22개 테스트를 추가했고 전체 빌드, 네이티브 빌드, 네이티브 `save` -> `open` 왕복을 검증했다.

2. 생성/변경한 파일 목록
- 변경: `cli/src/main/java/dev/juhyeonl/atscheck/cli/AtsCheckCli.java`
- 생성: `cli/src/main/java/dev/juhyeonl/atscheck/cli/command/SaveCommand.java`
- 생성: `cli/src/main/java/dev/juhyeonl/atscheck/cli/command/OpenCommand.java`
- 생성: `cli/src/main/java/dev/juhyeonl/atscheck/cli/command/InitCommand.java`
- 생성: `cli/src/main/java/dev/juhyeonl/atscheck/cli/platform/ClipboardReader.java`
- 생성: `cli/src/main/java/dev/juhyeonl/atscheck/cli/platform/BrowserOpener.java`
- 생성: `cli/src/main/java/dev/juhyeonl/atscheck/cli/store/FrontMatter.java`
- 생성: `cli/src/main/java/dev/juhyeonl/atscheck/cli/store/JobFile.java`
- 생성: `cli/src/main/java/dev/juhyeonl/atscheck/cli/store/JobFileParser.java`
- 생성: `cli/src/main/java/dev/juhyeonl/atscheck/cli/store/JobFileWriter.java`
- 생성: `cli/src/main/java/dev/juhyeonl/atscheck/cli/store/JobStore.java`
- 생성: `cli/src/test/java/dev/juhyeonl/atscheck/cli/store/JobFileParserTest.java`
- 생성: `cli/src/test/java/dev/juhyeonl/atscheck/cli/command/SaveCommandTest.java`
- 생성: `cli/src/test/java/dev/juhyeonl/atscheck/cli/command/OpenCommandTest.java`
- 생성: `cli/src/test/java/dev/juhyeonl/atscheck/cli/command/InitCommandTest.java`
- 생성: `_briefs/task-6-report.md`

3. 클립보드/브라우저를 어떤 명령으로 어떻게 위임했는가
- 클립보드: `wl-paste` -> `xclip -selection clipboard -o` -> `pbpaste` -> `powershell.exe -c Get-Clipboard`
- 브라우저: `wslview` -> `xdg-open` -> `open` -> `cmd.exe /c start "" <url>`
- 둘 다 `ProcessBuilder`로 실행한다. 명령이 없거나, 비정상 종료하거나, 5초 안에 끝나지 않으면 다음 후보로 넘어간다.
- 클립보드 후보가 모두 실패하면 `save`가 stderr에 이유를 출력하고 stdin 읽기로 폴백한다.
- 테스트는 `ClipboardReader`, `BrowserOpener` 가짜 구현을 주입해 실제 외부 명령을 실행하지 않았다.

4. 프론트매터 왕복에서 알 수 없는 키를 어떻게 보존했는가
- `JobFileParser`가 SnakeYAML `SafeConstructor`로 top-level `Map<String, Object>`만 읽는다.
- `url`, `company`, `title`, `saved_at`, `status`를 제거하고 남은 `LinkedHashMap`을 `FrontMatter.extra`에 보관한다.
- `JobFileWriter`는 알려진 키를 `url, company, title, saved_at, status` 순서로 쓴 뒤 `extra`를 원래 삽입 순서로 쓴다.
- 문자열 값은 필요 시 double quote로 감싸고 `\`, `"`, newline, carriage return, tab을 이스케이프한다.

5. 파일명 slug화와 충돌 처리 방식
- 파일명 기본형은 `<company>-<title>.md`, company가 비면 `<title>.md`, 둘 다 비면 `job-<timestamp>.md`다.
- slug는 소문자화, 공백/기타 구분자 `-` 변환, 영문/숫자/`-` 외 문자 제거, 연속 `-` 축약, 앞뒤 `-` 제거를 적용한다.
- 전체 파일명은 `.md` 포함 80자 이하가 되도록 자른다.
- 같은 이름이 있으면 `-2`, `-3` 순서로 접미사를 붙이며, 접미사 길이까지 고려해 다시 자른다.

6. 테스트 결과
- `./gradlew build`: 성공
- 전체 테스트: 164개 통과
- 모듈별 집계: `core` 125개, `cli` 39개
- 기존 142개 + 신규 22개 구성으로 기존 테스트 수 유지 확인
- `./gradlew :core:dependencies --configuration runtimeClasspath`: `No dependencies`

7. 네이티브 빌드 결과: 산출물 목록 / 바이너리 크기
- `./gradlew :cli:nativeCompile`: 성공
- Native Image 출력 요약: `18.76MB in total`
- 산출물 목록 실제 출력:
```text
total 19224
drwxr-xr-x 2 juhyeonl juhyeonl     4096 Aug 15 06:01 .
drwxr-xr-x 5 juhyeonl juhyeonl     4096 Aug 15 05:48 ..
-rwxr-xr-x 1 juhyeonl juhyeonl 19675872 Aug 15 06:01 ats-check
```
- 크기 실제 출력:
```text
19M	cli/build/native/nativeCompile/ats-check
```
- 산출물은 `ats-check` 단일 파일이며 사이드카 `.so`는 생성되지 않았다.

8. 스스로 결정한 것 + 근거
- `open --all-apply`에서 APPLY지만 URL이 빈 공고는 열 수 없으므로 stderr에 skip 안내 후 제외했다.
- 프론트매터의 알 수 없는 복합 값은 의미 보존을 우선해 inline YAML 형태로 다시 쓴다. 원본 주석/공백 포맷 보존은 요구 범위 밖으로 보았다.
- `open`의 외부 명령 실패는 브라우저 환경 문제로 보고 `70`을 반환하게 했다. `open`의 검색/모호성/빈 URL은 사용법 오류 `64`로 처리했다.

9. 남은 리스크
- stdin 파이프 여부 판정은 기존 CLI와 동일하게 `System.console() == null` 기반이다. 일부 비대화형 환경에서는 닫힌 stdin도 파이프로 볼 수 있다.
- 외부 브라우저 명령은 프로세스 종료 코드 0까지만 확인한다. 실제 브라우저 탭 표시 성공 여부는 플랫폼별로 다를 수 있다.
- 알 수 없는 프론트매터 키의 값은 보존하지만 원래 YAML 스타일, 주석, 줄바꿈 형태까지 보존하지는 않는다.

10. 다음 추천 작업
- 다음 태스크에서 `--job-dir` 배치 모드를 구현할 때 `JobStore.listJobFiles()`와 `JobFileParser`를 재사용하면 된다.
- `open --all-apply`의 URL 없는 APPLY 공고 처리 정책을 Claude가 원하면 batch 출력 설계와 함께 명확히 문서화하면 좋다.

추가 검증
- 네이티브 `save` -> `open` 왕복 검증은 임시 `wslview` 스크립트로 실제 브라우저 실행 없이 URL 호출만 기록했다.
```text
jobs/wolt-backend-engineer.md
opened=https://example.com/native-job
```

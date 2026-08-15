# [Codex 작업지시서] Task 11 — 릴리스 파이프라인 실패 2건 수정

## 목표
`v0.1.0-rc1` 태그로 릴리스 워크플로를 처음 돌렸고 **Linux와 Windows가 실패**했다.
두 원인을 고친다. macOS는 성공했다.

**릴리스 job은 skip되어 잘못된 릴리스가 공개되지는 않았다.**

## 실패 1 — Linux: `set -u`가 SDKMAN과 충돌

컨테이너 안에서 27초 만에 실패했다:

```
/root/.sdkman/bin/sdkman-init.sh: line 20: SDKMAN_CANDIDATES_API: unbound variable
##[error]Process completed with exit code 1.
```

워크플로가 `set -euo pipefail`로 시작하는데, SDKMAN의 `sdkman-init.sh`는
미설정 변수를 참조한다. `-u` 때문에 즉시 죽는다.

**수정:** SDKMAN을 source하는 구간만 `-u`를 끈다.

```bash
set +u
source "$SDKMAN_DIR/bin/sdkman-init.sh"
set -u
```

`sdk install` 같은 SDKMAN 명령도 같은 문제를 낼 수 있으니, **SDKMAN을 쓰는 구간 전체**를
`set +u` ... `set -u`로 감싸는 편이 안전하다.
`-e`와 `-o pipefail`은 **그대로 유지하라.** 오류를 삼키면 안 된다.

## 실패 2 — Windows: 사이드카 검사가 과도하다

```
Unexpected native build sidecar files:
cli/build/native/nativeCompile/native-image-7158355251078474146.args
##[error]Process completed with exit code 1.
```

`native-image-*.args`는 native-image가 남기는 **빌드 인자 기록 파일**이다.
런타임 의존이 아니다. Windows 빌드에서만 생성된다.

**이 검사가 원래 잡으려던 것**(ADR-005)은 `libawt.so`, `libfontmanager.so` 같은
**공유 라이브러리**다. PDFBox를 넣었을 때 실행 파일 옆에 8개가 생겼고,
바이너리만 복사하면 `UnsatisfiedLinkError`로 죽었다.

**수정:** 사이드카 검사를 **공유 라이브러리 확장자로 한정**하라.

- 실패시킬 것: `*.so`, `*.so.*`, `*.dylib`, `*.dll`
- 무시할 것: `*.args`, `*.txt`, `*.json`, 빌드 리포트 디렉토리, 실행 파일 자신

**격리 실행 검사(`/tmp/smoke`로 복사해서 실행)는 그대로 유지하라.**
그것이 진짜 검사이고, 파일 목록 검사는 보조 수단이다.

## 배경 (확인된 사실)

- 레포: github.com/juhyeonl-hub/ats-check, 원격 `origin`, `main` 브랜치
- 실패한 run: 31864704504 (`v0.1.0-rc1`)
- macOS job은 **성공**했다 — macOS 경로는 건드리지 마라
- PR 워크플로(3 OS)는 두 번 연속 성공했다 — `pr.yml`은 건드리지 마라
- Windows 바이너리 이름은 `ats-check.exe`다

## 수정 금지

- `.github/workflows/pr.yml`
- `release.yml`의 **다른 모든 로직**: 액션 버전, glibc 검증(2.31 기준),
  격리 스모크 테스트, exit code 검증(2/0), 컨테이너 이미지(`ubuntu:20.04`),
  체크섬 생성, `prerelease` 처리, `--no-configuration-cache`, UTF-8 설정
- `core/`, `cli/`, 문서 등 그 외 모든 파일
- `git commit` / `git push` / 태그 조작 금지

## 완료 조건

1. 두 수정이 반영됐다
2. YAML 파싱: `python3 -c "import yaml;yaml.safe_load(open('.github/workflows/release.yml'));print('ok')"`
3. `git diff --stat`이 `release.yml` **한 파일만** 보여준다
4. diff를 직접 읽고 **의도한 두 곳 외에 바뀐 것이 없는지 확인**하라

## 보고서

`_briefs/task-11-report.md`

```
[Codex 결과 보고서] Task 11
1. Linux 수정: set +u 범위를 어디로 잡았고 왜 그렇게 했는가
2. Windows 수정: 어떤 패턴을 실패로 보고 어떤 것을 무시하는가
3. 전체 diff
4. YAML 검증 결과
5. 이 수정으로도 남는 리스크 (다시 돌려봐야만 알 수 있는 것)
```

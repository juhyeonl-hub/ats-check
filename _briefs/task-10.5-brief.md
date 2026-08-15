# [Codex 작업지시서] Task 10.5 — 프리릴리스 태그 처리

## 목표
`v0.1.0-rc1` 같은 태그가 **프리릴리스로 표시**되게 한다. 한 줄 작업이다.

## 문제
`release.yml`의 `softprops/action-gh-release@v3` 단계에 `prerelease` 입력이 없다.
현재 상태로 `v0.1.0-rc1`을 push하면 **정식 릴리스로 공개된다.**

곧 `v0.1.0-rc1`을 push해서 릴리스 파이프라인을 검증할 예정이므로 지금 필요하다.

## 작업
`release.yml`의 릴리스 생성 단계에 아래를 추가한다:

```yaml
prerelease: ${{ contains(github.ref_name, '-') }}
```

semver 관례상 태그에 하이픈이 있으면 프리릴리스다 (`v0.1.0-rc1`, `v0.1.0-beta.2`).
`v0.1.0`은 하이픈이 없으므로 정식 릴리스가 된다.

릴리스 이름/제목을 설정하는 입력이 이미 있다면 그대로 두라. 없으면 추가하지 마라.

## 수정 금지
- `.github/workflows/release.yml`의 **다른 모든 것** — 액션 버전, glibc 검증,
  스모크 테스트, 컨테이너 빌드, 체크섬, `--no-configuration-cache`, UTF-8 설정
- `.github/workflows/pr.yml`
- 그 외 모든 파일
- `git commit` / `git push` 금지

## 완료 조건
1. `prerelease` 입력이 추가됐다
2. YAML 파싱: `python3 -c "import yaml;yaml.safe_load(open('.github/workflows/release.yml'));print('ok')"`
3. 다른 변경이 없다 (`git diff --stat`으로 확인해서 보고서에 붙여라)

## 보고서
`_briefs/task-10.5-report.md` — 변경 diff, YAML 검증 결과

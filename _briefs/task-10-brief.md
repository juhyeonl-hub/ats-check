# [Codex 작업지시서] Task 10 — GitHub Actions 버전 갱신

## 목표
첫 CI 실행에서 Node.js 20 deprecation 경고가 나왔다. 액션들을 최신 메이저로 올린다.

## 배경 (확인된 사실)
- 레포: github.com/juhyeonl-hub/ats-check, 원격 `origin` 연결됨, `main` 브랜치
- 첫 CI 실행 성공: ubuntu/macos/windows 3개 job 전부 통과
- 경고: `actions/checkout@v4`, `actions/upload-artifact@v4`, `gradle/actions/setup-gradle@v4`가 Node 20 대상

## 확인된 최신 버전
| 액션 | 현재 | 최신 |
|---|---|---|
| actions/checkout | v4 | **v7** |
| actions/upload-artifact | v4 | **v7** |
| actions/download-artifact | v4 | **v8** |
| gradle/actions/setup-gradle | v4 | **v6** |
| softprops/action-gh-release | v2 | **v3** |
| graalvm/setup-graalvm | v1 | v1 (메이저 태그, 유지) |

## 작업
1. `.github/workflows/pr.yml`과 `release.yml`의 액션 버전을 위 표대로 올린다
2. **각 액션의 입력 파라미터가 새 메이저에서 바뀌었는지 확인하라.**
   특히 `upload-artifact`/`download-artifact`(v4에서 artifact 불변성 변경)와
   `action-gh-release`(v2→v3)를 확인하고, 필요하면 파라미터를 맞춰라
3. YAML 파싱 검증:
   `python3 -c "import yaml,sys;[yaml.safe_load(open(f)) for f in sys.argv[1:]];print('ok')" .github/workflows/*.yml`

## 수정 금지
- `.github/workflows/` 외의 모든 파일
- `git commit` / `git push` 금지 — 커밋과 push는 Claude가 한다
- 워크플로의 **로직 변경 금지**. 버전과 (필요시) 파라미터 이름만 바꾼다.
  특히 glibc 검증, 스모크 테스트, `--no-configuration-cache`, UTF-8 설정은 그대로 둔다

## 완료 조건
1. 두 워크플로가 YAML로 파싱된다
2. 위 표의 버전이 반영됐다
3. 파라미터 호환성을 확인했고, 바꾼 것이 있으면 이유를 보고서에 적었다

## 보고서
`_briefs/task-10-report.md` — 바꾼 버전 목록, 파라미터 변경 여부와 근거, YAML 검증 결과

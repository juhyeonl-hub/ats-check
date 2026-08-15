# Task 10.5 Report - 프리릴리스 태그 처리

## 변경 요약

`.github/workflows/release.yml`의 `softprops/action-gh-release@v3` 단계에 `prerelease` 입력을 추가했다.

```yaml
          prerelease: ${{ contains(github.ref_name, '-') }}
```

## 변경 diff

Task 10.5에서 추가한 변경:

```diff
       - name: Create GitHub Release
         uses: softprops/action-gh-release@v3
         with:
           files: |
             dist/ats-check-*
             dist/checksums.txt
           generate_release_notes: true
+          prerelease: ${{ contains(github.ref_name, '-') }}
```

참고: 작업 시작 시점에 `.github/workflows/release.yml`의 액션 버전 변경과 `.github/workflows/pr.yml` 변경이 이미 작업트리에 존재했다. 해당 기존 변경은 수정하지 않았다.

## YAML 검증 결과

명령:

```bash
python3 -c "import yaml;yaml.safe_load(open('.github/workflows/release.yml'));print('ok')"
```

결과:

```text
ok
```

## 테스트 결과

명령:

```bash
./gradlew test
```

결과:

```text
BUILD SUCCESSFUL in 871ms
8 actionable tasks: 8 up-to-date
```

## git diff --stat

명령:

```bash
git diff --stat
```

결과:

```text
 .github/workflows/pr.yml      |  8 ++++----
 .github/workflows/release.yml | 11 ++++++-----
 2 files changed, 10 insertions(+), 9 deletions(-)
```

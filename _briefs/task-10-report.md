# Task 10 Report — GitHub Actions 버전 갱신

## 1. 바꾼 버전 목록

### `.github/workflows/pr.yml`
- `actions/checkout@v4` -> `actions/checkout@v7`
- `gradle/actions/setup-gradle@v4` -> `gradle/actions/setup-gradle@v6`
- `actions/upload-artifact@v4` -> `actions/upload-artifact@v7`
- `graalvm/setup-graalvm@v1` 유지

### `.github/workflows/release.yml`
- `actions/checkout@v4` -> `actions/checkout@v7`
- `gradle/actions/setup-gradle@v4` -> `gradle/actions/setup-gradle@v6`
- `actions/upload-artifact@v4` -> `actions/upload-artifact@v7`
- `actions/download-artifact@v4` -> `actions/download-artifact@v8`
- `softprops/action-gh-release@v2` -> `softprops/action-gh-release@v3`
- `graalvm/setup-graalvm@v1` 유지

## 2. 파라미터 변경 여부와 근거

파라미터 변경 없음.

- `actions/checkout@v7`: 현재 워크플로에서 별도 `with` 입력을 사용하지 않으므로 조정 불필요.
- `gradle/actions/setup-gradle@v6`: 현재 워크플로에서 별도 `with` 입력을 사용하지 않으므로 조정 불필요.
- `actions/upload-artifact@v7`: 기존 입력 `name`, `path`, `if-no-files-found`가 v7 `action.yml`에 존재한다. v4 이후 artifact 불변성 모델에서도 현재 artifact 이름은 job/matrix 기준으로 중복되지 않아 `overwrite` 추가가 필요 없다.
- `actions/download-artifact@v8`: 기존 입력 `path`, `merge-multiple`이 v8 `action.yml`에 존재한다. release job은 같은 workflow run의 모든 native artifact를 `dist`로 병합하는 기존 동작을 유지한다.
- `softprops/action-gh-release@v3`: 기존 입력 `files`, `generate_release_notes`가 v3 `action.yml`에 존재한다.

확인한 공식 원본:
- https://raw.githubusercontent.com/actions/checkout/v7/action.yml
- https://raw.githubusercontent.com/gradle/actions/v6/setup-gradle/action.yml
- https://raw.githubusercontent.com/actions/upload-artifact/v7/action.yml
- https://raw.githubusercontent.com/actions/download-artifact/v8/action.yml
- https://raw.githubusercontent.com/softprops/action-gh-release/v3/action.yml

## 3. YAML 검증 결과

명령:

```bash
python3 -c "import yaml,sys;[yaml.safe_load(open(f)) for f in sys.argv[1:]];print('ok')" .github/workflows/*.yml
```

결과:

```text
ok
```

## 4. 추가 로컬 테스트

명령:

```bash
./gradlew --no-configuration-cache test
```

결과:

```text
BUILD SUCCESSFUL
```

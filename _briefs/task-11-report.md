[Codex 결과 보고서] Task 11

1. Linux 수정: set +u 범위를 어디로 잡았고 왜 그렇게 했는가

- `curl -s "https://get.sdkman.io" | bash` 직후부터 `source "$HOME/.sdkman/bin/sdkman-init.sh"`와 `sdk install java 21.0.2-graalce`가 끝날 때까지 `set +u`로 감쌌고, 이후 즉시 `set -u`를 복구했다.
- SDKMAN 초기화 스크립트뿐 아니라 `sdk install`도 SDKMAN shell 함수와 환경 변수를 사용하므로, `nounset` 충돌 가능성이 있는 SDKMAN 사용 구간 전체를 같은 블록에 넣었다.
- `set -e`와 `set -o pipefail`은 끄지 않았으므로 SDKMAN 설치 실패는 그대로 job 실패로 전파된다.

2. Windows 수정: 어떤 패턴을 실패로 보고 어떤 것을 무시하는가

- 실패로 보는 패턴: `*.so`, `*.so.*`, `*.dylib`, `*.dll`
- 무시하는 항목: `ats-check.exe` 실행 파일 자신, `*.args`, `*.txt`, `*.json`, 빌드 리포트 디렉토리, 그 외 공유 라이브러리 확장자가 아닌 파일
- 검사는 `find ... -type f`와 공유 라이브러리 확장자 패턴으로 한정했다. 따라서 Windows에서 생성된 `native-image-*.args`는 더 이상 실패 대상이 아니며, ADR-005가 의도한 런타임 공유 라이브러리 사이드카만 보조 검사에서 잡는다.

3. 전체 diff

```diff
diff --git a/.github/workflows/release.yml b/.github/workflows/release.yml
index 92baa01..4a8a71e 100644
--- a/.github/workflows/release.yml
+++ b/.github/workflows/release.yml
@@ -63,9 +63,11 @@ jobs:
           rm -rf /var/lib/apt/lists/*
 
           curl -s "https://get.sdkman.io" | bash
+          set +u
           # shellcheck disable=SC1091
           source "$HOME/.sdkman/bin/sdkman-init.sh"
           sdk install java 21.0.2-graalce
+          set -u
 
           export JAVA_HOME="$SDKMAN_DIR/candidates/java/current"
           export PATH="$JAVA_HOME/bin:$PATH"
@@ -179,7 +181,7 @@ jobs:
 
           binary="cli/build/native/nativeCompile/ats-check${ext}"
           bin_dir="$(dirname "$binary")"
-          extras="$(find "$bin_dir" -maxdepth 1 -type f ! -name "ats-check${ext}" -print)"
+          extras="$(find "$bin_dir" -maxdepth 1 -type f ! -name "ats-check${ext}" \( -name "*.so" -o -name "*.so.*" -o -name "*.dylib" -o -name "*.dll" \) -print)"
           if [ -n "$extras" ]; then
             echo "Unexpected native build sidecar files:"
             echo "$extras"
```

4. YAML 검증 결과

```text
$ python3 -c "import yaml;yaml.safe_load(open('.github/workflows/release.yml'));print('ok')"
ok
```

추가 확인:

```text
$ git diff --stat
 .github/workflows/release.yml | 4 +++-
 1 file changed, 3 insertions(+), 1 deletion(-)
```

```text
$ ./gradlew test
BUILD SUCCESSFUL in 882ms
8 actionable tasks: 8 up-to-date
```

5. 이 수정으로도 남는 리스크 (다시 돌려봐야만 알 수 있는 것)

- GitHub Actions의 Ubuntu 20.04 컨테이너 안에서 SDKMAN 설치와 GraalVM 설치가 실제로 끝까지 통과하는지는 릴리스 워크플로 재실행으로 확인해야 한다.
- Windows runner에서 `native-image-*.args`가 무시되고, 실제 공유 라이브러리 사이드카가 없으며, 격리 스모크 테스트가 계속 통과하는지는 릴리스 워크플로 재실행으로 확인해야 한다.
- SDKMAN 원격 설치 스크립트나 GraalVM 배포 상태처럼 외부 네트워크에 의존하는 부분은 로컬 YAML/Gradle 검증만으로는 보장할 수 없다.

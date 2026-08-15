# [Codex 작업지시서] Task 12 — 릴리스 바이너리에 태그 버전을 심는다

## 목표

릴리스된 바이너리가 자기 버전을 **`0.1.0-SNAPSHOT`**으로 보고한다. 태그 버전을 반영하게 한다.

## 재현 (GitHub Releases에서 내려받은 실제 바이너리)

```
$ gh release download v0.1.0-rc2
$ ./ats-check-linux-amd64 --version
ats-check 0.1.0-SNAPSHOT      ← v0.1.0-rc2 여야 한다
```

`build.gradle.kts`의 `allprojects { version = "0.1.0-SNAPSHOT" }`가 하드코딩되어 있고,
릴리스 워크플로가 이를 덮어쓰지 않는다.

정식 v0.1.0을 내기 전에 고쳐야 한다. 사용자가 `--version`을 쳤을 때
`SNAPSHOT`이 나오면 릴리스를 신뢰할 수 없다.

## 수정

### 1. `build.gradle.kts`

버전을 **주입 가능**하게 한다. 주입이 없으면 지금처럼 `0.1.0-SNAPSHOT`을 쓴다.

```kotlin
version = (findProperty("atsCheckVersion") as String?) ?: "0.1.0-SNAPSHOT"
```

프로퍼티 이름은 네가 정해도 되지만 **기본값 동작은 반드시 유지하라.**
로컬에서 `./gradlew build`를 그냥 돌렸을 때 지금과 똑같이 동작해야 한다.

### 2. `release.yml`

태그명에서 `v` 접두사를 떼고 Gradle에 넘긴다.

- `v0.1.0-rc2` → `0.1.0-rc2`
- `v0.1.0` → `0.1.0`

`nativeCompile`을 호출하는 **모든 곳**(Linux 컨테이너 안, macOS, Windows)에 적용하라.
Linux는 컨테이너 안에서 돌므로 값을 컨테이너로 전달해야 한다.

`github.ref_name`으로 태그명을 얻을 수 있다.

### 3. Picocli 버전 문자열

`CheckCommand`(또는 `AtsCheckCli`)의 `@Command(version = ...)`가 어떻게 버전을 얻는지 확인하라.
하드코딩되어 있다면 **빌드 시점의 프로젝트 버전을 읽도록** 바꿔라.

- 매니페스트(`Implementation-Version`)를 읽는 방식은 **네이티브 이미지에서 동작하지 않을 수 있다.**
  확인하지 않은 채로 쓰지 마라
- 확실한 방법은 빌드가 생성한 상수나 리소스 파일을 읽는 것이다.
  어느 쪽이든 **네이티브 바이너리에서 실제로 검증하라**

## 검증 (반드시 직접 실행하라)

```bash
source "$HOME/.sdkman/bin/sdkman-init.sh"

# 1) 기본값 유지 확인
./gradlew :cli:nativeCompile
cli/build/native/nativeCompile/ats-check --version    # 0.1.0-SNAPSHOT 기대

# 2) 주입 확인
./gradlew -PatsCheckVersion=0.1.0 :cli:nativeCompile
cli/build/native/nativeCompile/ats-check --version    # 0.1.0 기대
```

**두 출력을 모두 보고서에 붙여라.** 네이티브 바이너리로 확인하지 않은 채
"됐다"고 보고하지 마라.

## 배경 (확인된 사실)

- 레포: github.com/juhyeonl-hub/ats-check, `main` 브랜치
- `v0.1.0-rc2` 릴리스는 **성공**했다 (3 OS + 체크섬). 파이프라인 자체는 건강하다
- Linux 바이너리는 `GLIBC_2.17`을 요구한다 (ADR-003 목표 달성)
- 테스트 206개 통과 중

## 수정 금지

- `release.yml`의 다른 로직: 액션 버전, glibc 검증, 격리 스모크 테스트,
  exit code 검증, 컨테이너 이미지, 체크섬, `prerelease`, `set +u` 범위, 사이드카 검사
- `.github/workflows/pr.yml`
- `core/`의 판정 로직, 골든 파일, 문서
- `git commit` / `git push` / 태그 조작 금지

## 완료 조건

1. `./gradlew build` 성공, **기존 206개 테스트 통과**
2. 주입 없이 빌드하면 `0.1.0-SNAPSHOT`
3. `-PatsCheckVersion=0.1.0`으로 빌드하면 `0.1.0`
4. **둘 다 네이티브 바이너리로 확인했다**
5. `release.yml`이 3개 OS 전부에 버전을 전달한다
6. YAML 파싱 통과

## 보고서

`_briefs/task-12-report.md`

```
[Codex 결과 보고서] Task 12
1. 버전을 어디서 어떻게 주입했는가
2. Picocli가 버전을 읽는 방식과, 그것이 네이티브에서 동작함을 어떻게 확인했는가
3. Linux 컨테이너로 값을 전달한 방법
4. **네이티브 바이너리 --version 출력 2개 (주입 없음 / 주입)**   ← 실제 출력
5. 변경 파일 목록과 diff 요약
6. 테스트 결과
7. 남는 리스크
```

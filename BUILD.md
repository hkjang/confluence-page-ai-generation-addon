# 빌드 가이드 - Expert Document Page AI Generation Plugin

이 문서는 Confluence Server 7.2.1 플러그인의 빌드 환경 구성부터 배포까지 전 과정을 설명합니다.

---

## 목차

1. [사전 요구사항](#1-사전-요구사항)
2. [개발 환경 설정](#2-개발-환경-설정)
3. [빌드 실행](#3-빌드-실행)
4. [빌드 결과물](#4-빌드-결과물)
5. [Confluence에 설치](#5-confluence에-설치)
6. [개발 모드 실행](#6-개발-모드-실행)
7. [버전 관리](#7-버전-관리)
8. [CI/CD 연동](#8-cicd-연동)
9. [빌드 트러블슈팅](#9-빌드-트러블슈팅)

---

## 1. 사전 요구사항

### 1.1 필수 소프트웨어

| 소프트웨어 | 버전 | 필수 여부 | 비고 |
|---|---|---|---|
| **JDK** | 1.8.x (Java 8) | 필수 | JDK 11 이상은 호환되지 않음 |
| **Maven** | 3.6.0 이상 | 필수 | 3.9.x 권장 |
| **Git** | 2.x 이상 | 권장 | 소스 관리 |

> **주의**: 반드시 **JDK 8**을 사용해야 합니다. Confluence Server 7.2.1은 Java 8 기반이며, 이 플러그인의 모든 의존성(OkHttp 3.14.9 등)도 Java 8 호환 버전으로 선택되었습니다.

### 1.2 네트워크 요구사항

빌드 시 다음 Maven 저장소에 접근할 수 있어야 합니다:

| 저장소 | URL | 용도 |
|---|---|---|
| Maven Central | `https://repo.maven.apache.org/maven2/` | 일반 라이브러리 |
| Atlassian Public | `https://packages.atlassian.com/mvn/maven-atlassian-external/` | Confluence API, AMPS 플러그인 |

### 1.3 디스크 공간

- Maven 로컬 리포지토리: 최초 빌드 시 약 **1.5 GB** 필요 (Confluence 의존성 다운로드)
- 프로젝트 빌드: 약 **50 MB**

---

## 2. 개발 환경 설정

### 2.1 JDK 8 설치

#### Windows

```batch
:: Oracle JDK 8 다운로드 후 설치
:: https://www.oracle.com/java/technologies/javase/javase8-archive-downloads.html

:: 환경 변수 설정
set JAVA_HOME=C:\Program Files\Java\jdk1.8.0_231
set PATH=%JAVA_HOME%\bin;%PATH%

:: 설치 확인
java -version
:: 출력 예: java version "1.8.0_231"
```

#### Linux / macOS

```bash
# Ubuntu/Debian
sudo apt install openjdk-8-jdk

# macOS (Homebrew)
brew tap homebrew/cask-versions
brew install --cask temurin8

# 환경 변수 설정 (~/.bashrc 또는 ~/.zshrc)
export JAVA_HOME=/usr/lib/jvm/java-8-openjdk-amd64  # Linux
export JAVA_HOME=$(/usr/libexec/java_home -v 1.8)    # macOS
export PATH=$JAVA_HOME/bin:$PATH

# 설치 확인
java -version
```

### 2.2 Maven 설치

#### Windows

```batch
:: 방법 1: Chocolatey
choco install maven

:: 방법 2: 수동 설치
:: 1. https://maven.apache.org/download.cgi 에서 zip 다운로드
:: 2. 원하는 디렉토리에 압축 해제 (예: D:\tools\apache-maven-3.9.9)
:: 3. 환경 변수 설정
set MAVEN_HOME=D:\tools\apache-maven-3.9.9
set PATH=%MAVEN_HOME%\bin;%PATH%

:: 설치 확인
mvn -version
```

#### Linux / macOS

```bash
# Ubuntu/Debian
sudo apt install maven

# macOS (Homebrew)
brew install maven

# 수동 설치
wget https://dlcdn.apache.org/maven/maven-3/3.9.9/binaries/apache-maven-3.9.9-bin.tar.gz
tar xzf apache-maven-3.9.9-bin.tar.gz -C /opt
export PATH=/opt/apache-maven-3.9.9/bin:$PATH

# 설치 확인
mvn -version
```

### 2.3 소스 코드 받기

```bash
git clone <repository-url>
cd confluence-page-ai-generation-addon
```

### 2.4 IDE 설정 (선택사항)

#### IntelliJ IDEA (권장)

1. **File** → **Open** → `pom.xml` 선택 → **Open as Project**
2. **File** → **Project Structure** → **Project SDK** → JDK 1.8 선택
3. **File** → **Project Structure** → **Project language level** → 8 선택
4. Maven 자동 임포트 활성화: **Settings** → **Build, Execution, Deployment** → **Build Tools** → **Maven** → **Importing** → **Import Maven projects automatically** 체크

#### Eclipse

1. **File** → **Import** → **Maven** → **Existing Maven Projects**
2. 프로젝트 루트 디렉토리 선택
3. JRE를 JDK 1.8로 설정: **Window** → **Preferences** → **Java** → **Installed JREs**

#### VS Code

1. **Java Extension Pack** 설치
2. 프로젝트 폴더 열기
3. `settings.json`에 추가:
```json
{
    "java.configuration.runtimes": [
        {
            "name": "JavaSE-1.8",
            "path": "C:\\Program Files\\Java\\jdk1.8.0_231",
            "default": true
        }
    ]
}
```

---

## 3. 빌드 실행

### 3.1 기본 빌드 (JAR + OBR 생성)

```bash
mvn clean package -DskipTests
```

이 명령은 다음 단계를 순서대로 실행합니다:

| 단계 | 플러그인 | 설명 |
|---|---|---|
| 1 | `clean` | `target/` 디렉토리 삭제 |
| 2 | `compress-resources` | JS/CSS 파일 압축 |
| 3 | `resources` | 리소스 파일 복사 |
| 4 | `filter-plugin-descriptor` | `atlassian-plugin.xml` 변수 치환 |
| 5 | `compile` | Java 소스 컴파일 (67개 파일) |
| 6 | `generate-rest-docs` | REST API 문서 자동 생성 |
| 7 | `atlassian-spring-scanner` | Spring 빈 스캐닝 |
| 8 | `validate-banned-dependencies` | 금지된 의존성 검증 |
| 9 | `copy-bundled-dependencies` | OkHttp, Gson, Okio를 JAR에 번들링 |
| 10 | `generate-manifest` | OSGi MANIFEST.MF 생성 |
| 11 | `validate-manifest` | 매니페스트 검증 |
| 12 | `jar` | JAR 패키징 |
| 13 | `generate-obr-artifact` | OBR 파일 생성 |

### 3.2 컴파일만 실행

```bash
mvn clean compile
```

소스 코드 변경 후 컴파일 오류만 빠르게 확인할 때 사용합니다.

### 3.3 테스트 포함 빌드

```bash
# 단위 테스트 실행
mvn clean test

# 전체 빌드 + 테스트
mvn clean package
```

### 3.4 특정 환경 변수가 필요한 경우

JDK가 여러 버전 설치된 환경에서는 `JAVA_HOME`을 명시적으로 지정합니다:

#### Windows (Git Bash / MSYS2)

```bash
export JAVA_HOME="C:/Program Files/Java/jdk1.8.0_231"
export PATH="/d/tools/apache-maven-3.9.9/bin:$PATH"
mvn clean package -DskipTests
```

#### Windows (Command Prompt)

```batch
set JAVA_HOME=C:\Program Files\Java\jdk1.8.0_231
set PATH=D:\tools\apache-maven-3.9.9\bin;%PATH%
mvn clean package -DskipTests
```

#### Windows (PowerShell)

```powershell
$env:JAVA_HOME = "C:\Program Files\Java\jdk1.8.0_231"
$env:PATH = "D:\tools\apache-maven-3.9.9\bin;$env:PATH"
mvn clean package -DskipTests
```

### 3.5 오프라인 빌드

의존성이 이미 로컬 리포지토리에 캐시된 경우:

```bash
mvn clean package -DskipTests -o
```

### 3.6 빌드 속도 최적화

```bash
# 병렬 빌드 (CPU 코어 수에 따라)
mvn clean package -DskipTests -T 4

# REST 문서 생성 스킵 (개발 중)
mvn clean package -DskipTests -Drest.doc.skip=true
```

---

## 4. 빌드 결과물

빌드 성공 시 `target/` 디렉토리에 다음 파일이 생성됩니다:

| 파일 | 크기(약) | 설명 | 용도 |
|---|---|---|---|
| `ai-page-generation-1.0.1.jar` | ~1 MB | Confluence P2 플러그인 | UPM을 통한 설치 |
| `ai-page-generation-1.0.1.obr` | ~940 KB | OSGi Bundle Repository | OBR 기반 배포 |

### 4.1 JAR 내부 구조

```
ai-page-generation-1.0.1.jar
├── META-INF/
│   ├── MANIFEST.MF              # OSGi 매니페스트
│   ├── spring/                   # Spring Scanner 메타데이터
│   └── maven/                    # Maven POM 정보
├── com/koreacb/confluence/       # 컴파일된 Java 클래스 (67개)
├── com/google/gson/              # 번들된 Gson 라이브러리
├── okhttp3/                      # 번들된 OkHttp 라이브러리
├── okio/                         # 번들된 Okio 라이브러리
├── atlassian-plugin.xml          # 플러그인 디스크립터
├── css/                          # 스타일시트 (3개)
├── js/                           # JavaScript (5개)
├── soy/                          # Soy 템플릿 (4개)
├── templates/                    # Velocity 관리자 템플릿 (4개)
├── prompts/                      # AI 프롬프트 파일 (7개)
├── images/                       # 아이콘 SVG
└── i18n/                         # 국제화 리소스 (한국어/영어)
```

### 4.2 번들된 의존성 vs Provided 의존성

| 구분 | 라이브러리 | JAR에 포함 |
|---|---|---|
| **번들(compile)** | Gson 2.8.9, OkHttp 3.14.9, Okio 1.17.5 | O |
| **제공(provided)** | Confluence API, Active Objects, SAL, Spring Scanner, SLF4J 등 | X |

> `provided` 의존성은 Confluence 런타임에서 제공되므로 JAR에 포함되지 않습니다. `compile` 의존성만 JAR에 번들링됩니다.

---

## 5. Confluence에 설치

### 5.1 UPM (Universal Plugin Manager) 설치

1. Confluence 관리자 계정으로 로그인
2. **톱니바퀴(설정)** → **앱 관리** (또는 URL: `http://<서버>/plugins/servlet/upm`)
3. **앱 업로드** 버튼 클릭
4. `target/ai-page-generation-1.0.1.jar` 파일 선택
5. **업로드** 클릭
6. 설치 완료 후 플러그인 상태가 **"활성화됨"** 인지 확인

### 5.2 CLI를 통한 설치 (curl)

```bash
# UPM REST API를 통한 설치
curl -u admin:admin \
  -X POST \
  -H "Content-Type: application/octet-stream" \
  --data-binary @target/ai-page-generation-1.0.1.jar \
  "http://localhost:8090/rest/plugins/1.0/?token=$(curl -s -u admin:admin 'http://localhost:8090/rest/plugins/1.0/?os_authType=basic' -I | grep 'upm-token' | cut -d: -f2 | tr -d '[:space:]')"
```

### 5.3 업그레이드

기존 버전이 설치된 경우 동일한 방법으로 새 JAR을 업로드하면 자동으로 업그레이드됩니다.

> **주의**: 메이저 버전 업그레이드 시 Active Objects 스키마 변경이 있을 수 있습니다. AO 프레임워크가 자동으로 마이그레이션을 처리합니다.

### 5.4 제거

1. **앱 관리** 페이지 접속
2. "Expert Document Page AI Generation" 플러그인 찾기
3. **제거** 클릭

> **참고**: 플러그인 제거 시 Active Objects 테이블(`AI_GEN_*`)은 자동으로 삭제되지 않습니다. 완전한 정리가 필요하면 DB에서 수동 삭제해야 합니다.

---

## 6. 개발 모드 실행

### 6.1 Atlassian SDK를 이용한 로컬 Confluence 실행

> Atlassian SDK는 선택사항입니다. 이미 운영 중인 Confluence에 직접 설치하여 테스트할 수도 있습니다.

#### Atlassian SDK 설치

```bash
# Windows (Chocolatey)
choco install atlassian-plugin-sdk

# macOS (Homebrew)
brew tap atlassian/tap
brew install atlassian-plugin-sdk

# Linux
# https://developer.atlassian.com/server/framework/atlassian-sdk/install-the-atlassian-sdk-on-a-linux-or-mac-system/
```

#### 실행

```bash
# 로컬 Confluence 실행 (포트 1990)
atlas-run

# 디버그 모드 실행 (포트 5005에서 원격 디버거 대기)
atlas-debug

# 접속
# http://localhost:1990/confluence
# 기본 계정: admin / admin
```

### 6.2 QuickReload (핫 리로드)

`pom.xml`에 `enableQuickReload`가 `true`로 설정되어 있으므로, `atlas-run` 실행 중에 아래 명령으로 변경사항을 즉시 반영할 수 있습니다:

```bash
# 별도 터미널에서 실행
mvn package -DskipTests
```

JAR이 생성되면 Confluence가 자동으로 플러그인을 리로드합니다.

### 6.3 원격 디버깅 (IntelliJ IDEA)

1. `atlas-debug`로 Confluence 실행
2. IntelliJ에서 **Run** → **Edit Configurations** → **+** → **Remote JVM Debug**
3. 호스트: `localhost`, 포트: `5005`
4. **Debug** 클릭

---

## 7. 버전 관리

### 7.1 버전 번호 변경

`pom.xml`의 `<version>` 태그를 수정합니다:

```xml
<version>1.0.1</version>  <!-- 현재 버전 -->
```

빌드 결과물 파일명이 자동으로 반영됩니다:
- `ai-page-generation-{version}.jar`
- `ai-page-generation-{version}.obr`

### 7.2 플러그인 키

현재 플러그인 키: `com.koreacb.confluence.ai-page-generation`

이 키는 `pom.xml`의 `atlassian.plugin.key` 속성에서 자동 생성됩니다:
```
${project.groupId}.${project.artifactId}
= com.koreacb.confluence.ai-page-generation
```

> **주의**: 플러그인 키를 변경하면 기존 설치된 플러그인과 별개의 플러그인으로 인식됩니다. Active Objects 데이터도 새로 생성됩니다.

---

## 8. CI/CD 연동

### 8.1 Jenkins Pipeline 예시

```groovy
pipeline {
    agent any

    tools {
        jdk 'JDK8'
        maven 'Maven3'
    }

    stages {
        stage('Checkout') {
            steps {
                checkout scm
            }
        }

        stage('Build') {
            steps {
                sh 'mvn clean package -DskipTests'
            }
        }

        stage('Test') {
            steps {
                sh 'mvn test'
            }
            post {
                always {
                    junit 'target/surefire-reports/*.xml'
                }
            }
        }

        stage('Archive') {
            steps {
                archiveArtifacts artifacts: 'target/ai-page-generation-*.jar', fingerprint: true
                archiveArtifacts artifacts: 'target/ai-page-generation-*.obr', fingerprint: true
            }
        }

        stage('Deploy to Staging') {
            when { branch 'main' }
            steps {
                sh '''
                    curl -u ${CONFLUENCE_ADMIN_USER}:${CONFLUENCE_ADMIN_PASS} \
                         -X POST \
                         -H "Content-Type: application/octet-stream" \
                         --data-binary @target/ai-page-generation-*.jar \
                         "${CONFLUENCE_URL}/rest/plugins/1.0/?token=$(curl -s -u ${CONFLUENCE_ADMIN_USER}:${CONFLUENCE_ADMIN_PASS} '${CONFLUENCE_URL}/rest/plugins/1.0/?os_authType=basic' -I | grep 'upm-token' | cut -d: -f2 | tr -d '[:space:]')"
                '''
            }
        }
    }
}
```

### 8.2 GitHub Actions 예시

```yaml
name: Build Confluence Plugin

on:
  push:
    branches: [ main, develop ]
  pull_request:
    branches: [ main ]

jobs:
  build:
    runs-on: ubuntu-latest

    steps:
    - uses: actions/checkout@v4

    - name: Set up JDK 8
      uses: actions/setup-java@v4
      with:
        java-version: '8'
        distribution: 'temurin'
        cache: maven

    - name: Build with Maven
      run: mvn clean package -DskipTests

    - name: Run Tests
      run: mvn test

    - name: Upload JAR Artifact
      uses: actions/upload-artifact@v4
      with:
        name: confluence-plugin
        path: |
          target/ai-page-generation-*.jar
          target/ai-page-generation-*.obr
        retention-days: 30
```

### 8.3 GitLab CI 예시

```yaml
image: maven:3.9-eclipse-temurin-8

stages:
  - build
  - test
  - deploy

variables:
  MAVEN_OPTS: "-Dmaven.repo.local=$CI_PROJECT_DIR/.m2/repository"

cache:
  paths:
    - .m2/repository/

build:
  stage: build
  script:
    - mvn clean package -DskipTests
  artifacts:
    paths:
      - target/ai-page-generation-*.jar
      - target/ai-page-generation-*.obr
    expire_in: 1 week

test:
  stage: test
  script:
    - mvn test

deploy_staging:
  stage: deploy
  only:
    - main
  script:
    - |
      curl -u $CONFLUENCE_USER:$CONFLUENCE_PASS \
           -X POST \
           -H "Content-Type: application/octet-stream" \
           --data-binary @target/ai-page-generation-*.jar \
           "$CONFLUENCE_URL/rest/plugins/1.0/?token=$(curl -s -u $CONFLUENCE_USER:$CONFLUENCE_PASS "$CONFLUENCE_URL/rest/plugins/1.0/?os_authType=basic" -I | grep 'upm-token' | cut -d: -f2 | tr -d '[:space:]')"
```

---

## 9. 빌드 트러블슈팅

### 9.1 `javax.activation:activation:jar:1.0.2` 해결 실패

**증상:**
```
Could not find artifact javax.activation:activation:jar:1.0.2
```

**원인:** Confluence의 전이 의존성이 참조하는 `1.0.2` 버전이 Maven Central에서 제거됨

**해결:** `pom.xml`에 이미 명시적으로 호환 버전이 선언되어 있습니다:
```xml
<dependency>
    <groupId>javax.activation</groupId>
    <artifactId>activation</artifactId>
    <version>1.1.1</version>
    <scope>provided</scope>
</dependency>
```

### 9.2 `component` / `component-import` 매니페스트 오류

**증상:**
```
Manifest validation error: component is not allowed when Atlassian-Plugin-Key is set
```

**원인:** Spring Scanner 2.x 사용 시 `atlassian-plugin.xml`에 `<component>` 또는 `<component-import>` 요소가 있으면 안 됨

**해결:** 모든 빈은 `@Named` + `@Inject` 어노테이션으로 자동 발견됩니다. `atlassian-plugin.xml`에서 해당 요소를 제거해야 합니다.

### 9.3 Java 버전 불일치

**증상:**
```
unsupported class file major version XX
```
또는
```
source release 11 requires target release 11
```

**해결:** `JAVA_HOME`이 JDK 8을 가리키는지 확인합니다:
```bash
java -version
# 반드시 1.8.x 출력 확인

echo $JAVA_HOME
# JDK 8 경로 확인
```

### 9.4 Atlassian 저장소 연결 불가

**증상:**
```
Could not resolve dependencies... Failed to collect dependencies at com.atlassian.confluence:confluence:jar:7.2.1
```

**해결:**

1. 네트워크/프록시 확인:
```bash
curl -I https://packages.atlassian.com/mvn/maven-atlassian-external/
```

2. Maven 프록시 설정 (`~/.m2/settings.xml`):
```xml
<settings>
  <proxies>
    <proxy>
      <id>company-proxy</id>
      <active>true</active>
      <protocol>https</protocol>
      <host>proxy.company.com</host>
      <port>8080</port>
      <username>user</username>
      <password>pass</password>
    </proxy>
  </proxies>
</settings>
```

3. SSL 인증서 문제인 경우:
```bash
mvn clean package -DskipTests -Dmaven.wagon.http.ssl.insecure=true -Dmaven.wagon.http.ssl.allowall=true
```

### 9.5 메모리 부족 (OutOfMemoryError)

**증상:**
```
java.lang.OutOfMemoryError: Java heap space
```

**해결:**
```bash
export MAVEN_OPTS="-Xms512m -Xmx2g"
mvn clean package -DskipTests
```

### 9.6 로컬 리포지토리 손상

**증상:** 다운로드는 성공했으나 의존성 해결 실패, 체크섬 오류

**해결:**
```bash
# 특정 의존성 재다운로드
mvn dependency:purge-local-repository -DmanualInclude=com.atlassian.confluence:confluence

# 전체 Atlassian 의존성 재다운로드
rm -rf ~/.m2/repository/com/atlassian
mvn clean package -DskipTests
```

### 9.7 Spring Scanner 경고

**증상:**
```
Unused Import-Package instructions: [com.atlassian.plugins.rest.*, javax.xml.*, ...]
```

**심각도:** WARNING (무시 가능)

**설명:** Import-Package에 선언했지만 실제 코드에서 직접 사용하지 않는 패키지입니다. 런타임에 필요할 수 있으므로 제거하지 않는 것이 안전합니다.

### 9.8 Javadoc 경고

**증상:** REST 문서 생성 단계에서 Javadoc 관련 경고가 대량 출력됨

**심각도:** WARNING (무시 가능)

**설명:** `generate-rest-docs` 단계에서 Javadoc을 이용해 REST API 문서를 생성하는데, 내부 패키지 간 참조가 Javadoc 컨텍스트에서 해결되지 않아 발생합니다. 실제 빌드에는 영향 없습니다.

---

## 부록: 의존성 구성 요약

### Provided 의존성 (Confluence 런타임 제공)

| GroupId | ArtifactId | Version |
|---|---|---|
| `com.atlassian.confluence` | `confluence` | 7.2.1 |
| `com.atlassian.activeobjects` | `activeobjects-plugin` | 3.2.1 |
| `com.atlassian.sal` | `sal-api` | 4.0.0 |
| `com.atlassian.plugins.rest` | `atlassian-rest-common` | 6.1.2 |
| `com.atlassian.plugin` | `atlassian-spring-scanner-annotation` | 2.2.1 |
| `javax.inject` | `javax.inject` | 1 |
| `javax.activation` | `activation` | 1.1.1 |
| `javax.xml.bind` | `jaxb-api` | 2.3.0 |
| `javax.servlet` | `javax.servlet-api` | 3.1.0 |
| `javax.ws.rs` | `jsr311-api` | 1.1.1 |
| `com.atlassian.scheduler` | `atlassian-scheduler-api` | 1.7.0 |
| `com.atlassian.event` | `atlassian-event` | 4.0.0 |
| `org.slf4j` | `slf4j-api` | 1.7.26 |

### Compile 의존성 (JAR에 번들링)

| GroupId | ArtifactId | Version | 용도 |
|---|---|---|---|
| `com.google.code.gson` | `gson` | 2.8.9 | JSON 직렬화/역직렬화 |
| `com.squareup.okhttp3` | `okhttp` | 3.14.9 | vLLM HTTP 클라이언트 |
| `com.squareup.okio` | `okio` | 1.17.5 | OkHttp I/O 지원 |

### Test 의존성

| GroupId | ArtifactId | Version |
|---|---|---|
| `junit` | `junit` | 4.13.2 |
| `org.mockito` | `mockito-core` | 3.12.4 |

### Maven 플러그인

| GroupId | ArtifactId | Version | 역할 |
|---|---|---|---|
| `com.atlassian.maven.plugins` | `confluence-maven-plugin` | 8.1.2 | AMPS (Atlassian Maven Plugin Suite) |
| `com.atlassian.plugin` | `atlassian-spring-scanner-maven-plugin` | 2.2.1 | Spring 빈 스캐닝 |
| `org.apache.maven.plugins` | `maven-compiler-plugin` | 3.8.1 | Java 컴파일러 |

---

*이 문서는 Expert Document Page AI Generation Plugin v1.0.1 기준으로 작성되었습니다.*

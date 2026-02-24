# Expert Document Page AI Generation - 상세 가이드

## 목차

1. [아키텍처 개요](#1-아키텍처-개요)
2. [설치 및 설정 가이드](#2-설치-및-설정-가이드)
3. [사용자 가이드](#3-사용자-가이드)
4. [관리자 가이드](#4-관리자-가이드)
5. [개발자 가이드](#5-개발자-가이드)
6. [보안 아키텍처](#6-보안-아키텍처)
7. [데이터베이스 스키마](#7-데이터베이스-스키마)
8. [트러블슈팅](#8-트러블슈팅)
9. [성능 튜닝](#9-성능-튜닝)
10. [FAQ](#10-faq)

---

## 1. 아키텍처 개요

### 1.1 시스템 구성도

```
┌─────────────────────────────────────────────────┐
│                 Confluence Server                │
│  ┌───────────────────────────────────────────┐  │
│  │         AI Page Generation Plugin         │  │
│  │                                           │  │
│  │  ┌─────────┐  ┌──────────┐  ┌─────────┐  │  │
│  │  │ Wizard  │  │ Preview  │  │  Admin  │  │  │
│  │  │   UI    │  │   UI     │  │   UI    │  │  │
│  │  │(Soy+JS) │  │(Soy+JS)  │  │(Vel+JS) │  │  │
│  │  └────┬────┘  └────┬─────┘  └────┬────┘  │  │
│  │       │             │             │       │  │
│  │  ┌────┴─────────────┴─────────────┴────┐  │  │
│  │  │         REST API Layer (JAX-RS)      │  │  │
│  │  │  Generation / Template / Context /   │  │  │
│  │  │  Preview / Admin / Health            │  │  │
│  │  └────────────────┬────────────────────┘  │  │
│  │                   │                       │  │
│  │  ┌────────────────┴────────────────────┐  │  │
│  │  │         Service Layer               │  │  │
│  │  │  AiGeneration / PromptBuilder /     │  │  │
│  │  │  ContextBuilder / PostProcessor /   │  │  │
│  │  │  Policy / Audit / Usage / Template  │  │  │
│  │  └─────┬──────────┬──────────┬────────┘  │  │
│  │        │          │          │            │  │
│  │  ┌─────┴───┐ ┌────┴───┐ ┌───┴─────────┐ │  │
│  │  │ vLLM   │ │Active  │ │  Bandana    │ │  │
│  │  │ Client │ │Objects │ │ (Settings)  │ │  │
│  │  │(OkHttp)│ │ (AO)   │ │             │ │  │
│  │  └────┬───┘ └───┬────┘ └─────────────┘ │  │
│  └───────┼─────────┼────────────────────────┘  │
│          │         │                            │
└──────────┼─────────┼────────────────────────────┘
           │         │
           ▼         ▼
    ┌──────────┐  ┌──────────┐
    │  vLLM    │  │Confluence│
    │  Server  │  │    DB    │
    │ (GPU)    │  │(H2/PgSQL)│
    └──────────┘  └──────────┘
```

### 1.2 핵심 흐름

```
사용자 요청 → REST API → AiGenerationService
    → JobQueueService (AO에 작업 저장)
    → GenerationJobRunner (5초 폴링)
        → ContextBuilder (페이지/첨부/라벨 수집)
        → PromptBuilder (시스템 + 섹션 프롬프트 조립)
        → VllmClient (OkHttp → vLLM API 호출)
        → PostProcessor (스토리지 포맷 변환 + XSS 제거)
        → AO에 섹션별 결과 저장
    → 클라이언트 폴링으로 진행률 확인
    → 완료 시 미리보기 → 페이지 저장
```

### 1.3 설계 결정 근거

| 결정 | 근거 |
|---|---|
| AO 기반 작업 큐 | 외부 MQ 불필요, DB 기반 클러스터 안전, 추가 인프라 불필요 |
| 섹션 단위 생성 | 부분 실패 복구, 섹션별 재생성, 세밀한 진행률 |
| OkHttp 3.14.9 | Java 8 호환 마지막 버전, 커넥션풀/SSE/타임아웃 |
| Bandana + AO | 설정은 Bandana(key-value), 트랜잭션 데이터는 AO |
| AES-256-GCM | Confluence에 플러그인용 암호화 API 없음, 인스턴스별 키 파생 |
| 기본 미저장 정책 | 생성 콘텐츠 보존 리스크 최소화 |
| Spring Scanner 2.x | `@Named`/`@Inject`로 DI 자동 발견, XML component 불필요 |

---

## 2. 설치 및 설정 가이드

### 2.1 시스템 요구사항

| 항목 | 요구사항 |
|---|---|
| Confluence Server | 7.2.1 이상 (7.x 계열) |
| Java | JDK 8 (1.8.x) |
| 데이터베이스 | Confluence 지원 DB 모두 가능 (H2, PostgreSQL, MySQL, Oracle, MSSQL) |
| vLLM 서버 | OpenAI Compatible API 활성화 (`/v1/chat/completions`) |
| 네트워크 | Confluence → vLLM 서버 간 HTTP/HTTPS 통신 가능 |

### 2.2 빌드

```bash
# 1. 소스 클론
git clone <repository-url>
cd confluence-page-ai-generation-addon

# 2. 빌드 (테스트 스킵)
mvn clean package -DskipTests

# 3. 빌드 결과 확인
ls -la target/ai-page-generation-1.0.0.jar
ls -la target/ai-page-generation-1.0.0.obr
```

#### 빌드 문제 해결

**Maven이 없는 경우:**
```bash
# Windows (Chocolatey)
choco install maven

# macOS (Homebrew)
brew install maven

# 수동 설치
# https://maven.apache.org/download.cgi 에서 다운로드 후 PATH에 추가
```

**Atlassian 저장소 연결 오류:**
`pom.xml`에 다음 저장소가 포함되어 있는지 확인:
```xml
<repositories>
    <repository>
        <id>atlassian-public</id>
        <url>https://packages.atlassian.com/mvn/maven-atlassian-external/</url>
    </repository>
</repositories>
```

### 2.3 설치

1. Confluence 관리 콘솔 로그인
2. **일반 구성** → **앱 관리** (또는 URL: `/plugins/servlet/upm`)
3. **앱 업로드** 버튼 클릭
4. `target/ai-page-generation-1.0.0.jar` 선택
5. 업로드 완료 후 플러그인이 **활성화** 상태인지 확인

### 2.4 초기 설정

설치 후 **반드시** 관리자 설정을 완료해야 합니다.

#### 2.4.1 vLLM 연결 설정

1. **Confluence 관리** → **AI 문서 생성 설정** (또는 URL: `/plugins/servlet/ai-generation/admin/config`)
2. 필수 항목 입력:

| 항목 | 설명 | 예시 |
|---|---|---|
| vLLM 엔드포인트 | vLLM 서버 URL | `http://gpu-server:8000` |
| 모델명 | 사용할 LLM 모델 | `Qwen/Qwen2.5-72B-Instruct` |
| API 키 | Bearer 인증 키 (선택) | `sk-xxx...` |

3. **연결 테스트** 클릭 → "연결 성공" 확인
4. **저장**

#### 2.4.2 생성 기본값 설정

| 항목 | 기본값 | 설명 |
|---|---|---|
| 최대 토큰 | 4096 | 섹션당 최대 생성 토큰 수 |
| Temperature | 0.7 | 생성 다양성 (0.0~1.0) |
| 타임아웃 | 60초 | vLLM 호출 타임아웃 |
| 최대 동시 요청 | 5 | 노드당 동시 처리 작업 수 |
| 사용자별 일일 제한 | 50 | 사용자당 하루 최대 생성 횟수 |

#### 2.4.3 vLLM 서버 설정 예시

```bash
# vLLM 서버 실행 (OpenAI Compatible API 모드)
python -m vllm.entrypoints.openai.api_server \
    --model Qwen/Qwen2.5-72B-Instruct \
    --host 0.0.0.0 \
    --port 8000 \
    --tensor-parallel-size 4 \
    --max-model-len 32768 \
    --api-key "your-api-key"
```

---

## 3. 사용자 가이드

### 3.1 문서 생성 시작하기

문서 생성은 두 가지 방법으로 시작할 수 있습니다:

**방법 1: 스페이스 도구 메뉴**
1. 원하는 스페이스 접속
2. 좌측 사이드바 → **스페이스 도구** → **AI로 페이지 만들기**

**방법 2: 페이지 생성 대화상자**
1. 상단 **만들기** 버튼 클릭
2. **AI로 페이지 만들기** 선택

### 3.2 마법사 4단계

#### 1단계: 템플릿 선택

6종의 내장 템플릿 중 하나를 선택합니다:

| 템플릿 | 적합한 상황 |
|---|---|
| 회의록 | 팀 회의, 의사결정 회의 |
| 설계 문서 | 시스템/기능 설계 |
| RFC | 기술적 제안, 아키텍처 변경 |
| 장애 보고서 | 서비스 장애 사후 분석 |
| 실행 계획 | 프로젝트/이니셔티브 계획 |
| 주간 보고 | 주간 팀 상황 보고 |

#### 2단계: 목적 설정

| 항목 | 설명 | 예시 |
|---|---|---|
| 문서 목적 | 문서의 핵심 목적 | "신규 결제 시스템 마이크로서비스 아키텍처 설계" |
| 대상 독자 | 누가 읽을 것인가 | "개발팀 리드 및 아키텍트" |
| 톤 | 문서 어조 | 공식적 / 비공식적 / 기술적 / 설명적 |
| 길이 | 원하는 분량 | 간결 / 표준 / 상세 / 포괄적 |

#### 3단계: 컨텍스트 수집

AI가 더 정확한 문서를 생성하도록 관련 자료를 제공합니다:

- **참조 페이지**: 스페이스 내 관련 Confluence 페이지 선택 (최대 10개)
- **첨부 파일**: 관련 첨부 파일 선택
- **라벨**: 관련 라벨로 컨텍스트 범위 지정
- **추가 컨텍스트**: 자유 텍스트로 추가 배경 정보 입력

#### 4단계: 검토 및 생성

설정 내용을 최종 확인하고 **생성 시작** 버튼을 클릭합니다.

### 3.3 진행률 확인

생성이 시작되면 실시간 진행률이 표시됩니다:

```
[==========          ] 50% (3/6 섹션 완료)
현재 생성 중: 설계 → 상세 설계
```

- 각 섹션의 완료/실패 상태가 개별 표시됩니다
- **취소** 버튼으로 언제든 중단 가능합니다
- 부분 완료 시 완성된 섹션만 미리보기 가능합니다

### 3.4 미리보기 및 편집

생성 완료 후 미리보기 화면에서:

1. **섹션 네비게이션**: 좌측에서 섹션 클릭하여 이동
2. **인라인 편집**: 각 섹션의 "편집" 버튼으로 내용 수정
3. **섹션 재생성**: 특정 섹션만 AI로 다시 생성
4. **품질 패널**: 누락 섹션, 경고 사항 확인
5. **차이 비교**: 원본 대비 수정 내용 비교

### 3.5 저장

미리보기에서 최종 확인 후:

- **새 페이지로 저장**: 현재 스페이스에 새 페이지 생성
  - 제목 입력
  - 부모 페이지 선택 (선택사항)
- **기존 페이지에 삽입**: 기존 페이지 끝에 생성 내용 추가

---

## 4. 관리자 가이드

### 4.1 관리자 페이지 접근

- URL: `/plugins/servlet/ai-generation/admin/config`
- 또는 **Confluence 관리** → **AI 문서 생성 설정**
- **시스템 관리자** 권한 필요

### 4.2 설정 페이지 (`/config`)

#### vLLM 연결

| 항목 | 설명 |
|---|---|
| vLLM 엔드포인트 | vLLM 서버 기본 URL (예: `http://server:8000`) |
| 모델명 | 사용할 모델 ID |
| API 키 | AES-256-GCM으로 암호화 저장됨 |
| 연결 테스트 | vLLM `/v1/models` API 호출하여 연결 확인 |

#### 생성 기본값

| 항목 | 설명 | 권장값 |
|---|---|---|
| 최대 토큰 | 섹션당 최대 토큰 | 4096 |
| Temperature | 생성 다양성 | 0.5~0.8 |
| 타임아웃 | API 호출 제한시간 | 60~120초 |
| 동시 요청 수 | 노드당 병렬 생성 | 3~5 |

#### 사용량 제한

| 항목 | 설명 |
|---|---|
| 사용자별 일일 제한 | 사용자당 하루 최대 생성 횟수 |
| 스페이스별 일일 제한 | 스페이스당 하루 최대 생성 횟수 |
| 감사 로그 보존 기간 | 일수 (기본 30일) |

### 4.3 프롬프트 관리 (`/prompts`)

각 템플릿의 AI 프롬프트를 커스터마이징할 수 있습니다:

- **시스템 프롬프트**: AI의 역할과 행동 지침 정의
- **섹션 프롬프트**: 섹션별 생성 지시문
- **활성/비활성**: 커스텀 프롬프트 토글

기본 프롬프트는 `src/main/resources/prompts/` 디렉토리의 텍스트 파일에 정의되어 있습니다.

### 4.4 스페이스 정책 (`/policies`)

스페이스별로 세밀한 접근 제어가 가능합니다:

| 항목 | 설명 |
|---|---|
| 활성화 | 해당 스페이스에서 AI 생성 사용 가능 여부 |
| 허용 그룹 | 생성 가능한 Confluence 그룹 목록 (JSON 배열) |
| 허용 템플릿 | 사용 가능한 템플릿 키 목록 |
| 일일 요청 제한 | 스페이스별 일일 최대 요청 수 |
| 요청당 토큰 제한 | 스페이스별 요청당 최대 토큰 |

### 4.5 용어집 및 금칙어

#### 용어집
- 조직/도메인 특화 용어와 정의를 등록
- AI 프롬프트에 자동 주입되어 용어 일관성 유지
- 스페이스별 또는 글로벌 설정 가능

#### 금칙어
- 생성 결과에서 자동 치환되는 패턴 정의
- 정규식(regex) 또는 단순 문자열 매칭 지원
- 예: 경쟁사명 → "***", 내부 프로젝트 코드명 → 공식 명칭

### 4.6 감사 로그 (`/audit`)

모든 주요 액션이 로깅됩니다:

| 액션 | 설명 |
|---|---|
| `GENERATE_STARTED` | 생성 시작 |
| `GENERATE_COMPLETED` | 생성 완료 |
| `GENERATE_FAILED` | 생성 실패 |
| `GENERATE_CANCELLED` | 생성 취소 |
| `PAGE_SAVED` | 새 페이지 저장 |
| `PAGE_INSERTED` | 기존 페이지 삽입 |
| `CONFIG_UPDATED` | 설정 변경 |
| `API_KEY_UPDATED` | API 키 변경 |
| `POLICY_SAVED` / `DELETED` | 정책 변경 |

필터링 옵션: 사용자, 액션 유형, 스페이스, 날짜 범위

### 4.7 사용량 통계

대시보드에서 확인 가능한 지표:

- 총 생성 요청 수
- 총 사용 토큰 수
- 사용자별 통계
- 스페이스별 통계
- 일별 추이

---

## 5. 개발자 가이드

### 5.1 개발 환경 설정

```bash
# 1. JDK 8 설치 확인
java -version  # 1.8.x 확인

# 2. Maven 설치 확인
mvn -version   # 3.6+ 확인

# 3. 의존성 다운로드
mvn dependency:resolve

# 4. IDE에서 프로젝트 임포트 (IntelliJ IDEA 권장)
# File → Open → pom.xml 선택 → "Open as Project"
```

### 5.2 로컬 Confluence 실행 (Atlassian SDK)

```bash
# Atlassian SDK 설치 후
atlas-run

# 또는 디버그 모드 (원격 디버거 포트 5005)
atlas-debug
```

접속: `http://localhost:1990/confluence` (기본 admin/admin)

### 5.3 패키지 구조 상세

#### `ao/` - Active Objects 엔티티

| 클래스 | 테이블 | 설명 |
|---|---|---|
| `AoGenerationJob` | `AI_GEN_JOB` | 생성 작업 (상태, 설정, 진행률) |
| `AoGenerationSection` | `AI_GEN_SECTION` | 섹션별 결과 (내용, 토큰수, 상태) |
| `AoAuditLog` | `AI_GEN_AUDIT` | 감사 로그 |
| `AoUsageRecord` | `AI_GEN_USAGE` | 사용량 통계 |
| `AoPromptTemplate` | `AI_GEN_PROMPT` | 커스텀 프롬프트 |
| `AoGlossaryTerm` | `AI_GEN_GLOSSARY` | 용어집 |
| `AoForbiddenWord` | `AI_GEN_FORBIDDEN` | 금칙어 |
| `AoSpacePolicy` | `AI_GEN_POLICY` | 스페이스 정책 |

#### `service/` - 서비스 계층

| 인터페이스 | 구현체 | 역할 |
|---|---|---|
| `AiGenerationService` | `DefaultAiGenerationService` | 생성 오케스트레이션 |
| `VllmClientService` | `OkHttpVllmClientService` | vLLM HTTP 통신 |
| `PromptBuilderService` | `DefaultPromptBuilderService` | 프롬프트 조립 |
| `ContextBuilderService` | `DefaultContextBuilderService` | 컨텍스트 수집 |
| `PostProcessorService` | `DefaultPostProcessorService` | 후처리/검증 |
| `TemplateRegistryService` | `DefaultTemplateRegistryService` | 템플릿 관리 |
| `AdminConfigService` | `BandanaAdminConfigService` | 설정 CRUD |
| `JobQueueService` | `ActiveObjectsJobQueueService` | 작업 큐 |
| `PolicyService` | `DefaultPolicyService` | 정책 관리 |
| `AuditService` | `ActiveObjectsAuditService` | 감사 로그 |
| `UsageTrackingService` | `ActiveObjectsUsageTrackingService` | 사용량 추적 |

#### `security/` - 보안 모듈

| 클래스 | 역할 |
|---|---|
| `AesEncryptionService` | AES-256-GCM 암호화/복호화 (API 키 저장용) |
| `PermissionChecker` | Confluence 권한 검증 래퍼 |
| `ContentSanitizer` | XSS 벡터 제거 (script, onclick 등) |
| `LogMasker` | API 키/이메일/주민번호 로그 마스킹 |
| `RateLimiter` | 노드별 Semaphore + AO 기반 일간 제한 |

### 5.4 새 문서 템플릿 추가하기

1. **`BuiltInTemplates.java`에 팩토리 메서드 추가:**

```java
public static DocumentTemplate createMyTemplate() {
    DocumentTemplate t = new DocumentTemplate();
    t.setKey("my-template");
    t.setName("나의 템플릿");
    t.setDescription("커스텀 문서 템플릿");
    t.setCategory("custom");

    List<SectionDefinition> sections = new ArrayList<>();
    sections.add(new SectionDefinition("overview", "section",
        "개요", "문서의 전체 개요를 작성하세요", true, 0));
    sections.add(new SectionDefinition("details", "section",
        "상세 내용", "구체적인 내용을 작성하세요", true, 1));
    t.setSections(sections);

    return t;
}
```

2. **`DefaultTemplateRegistryService`의 `initBuiltInTemplates()`에 등록:**

```java
registerTemplate(BuiltInTemplates.createMyTemplate());
```

3. **프롬프트 파일 추가**: `src/main/resources/prompts/my-template-prompt.txt`

4. **i18n 키 추가**: `ai-generation.properties`에 관련 키 추가

### 5.5 REST API 확장하기

새 REST 리소스 추가 방법:

```java
package com.koreacb.confluence.aigeneration.rest;

import javax.inject.Inject;
import javax.inject.Named;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

@Path("/my-resource")
@Produces(MediaType.APPLICATION_JSON)
@Named
public class MyResource {

    @Inject
    public MyResource(/* 의존성 주입 */) { }

    @GET
    public Response getData() {
        return Response.ok(/* 데이터 */).build();
    }
}
```

`com.koreacb.confluence.aigeneration.rest` 패키지에 추가하면 `atlassian-plugin.xml`의 REST 모듈이 자동으로 스캔합니다.

### 5.6 빌드 및 테스트

```bash
# 컴파일만
mvn clean compile

# 패키지 (JAR + OBR)
mvn clean package -DskipTests

# 단위 테스트 실행
mvn test

# 통합 테스트 (Confluence 인스턴스 필요)
mvn integration-test

# QuickReload로 개발 중 핫 리로드
# atlas-run 실행 중인 상태에서:
mvn package -DskipTests
# → target/ai-page-generation-1.0.0.jar가 자동 리로드됨
```

---

## 6. 보안 아키텍처

### 6.1 API 키 보호

```
사용자 입력 API 키
    ↓
AesEncryptionService.encrypt()
    ↓ AES-256-GCM
    ↓ 키 파생: PBKDF2(serverId + 고정 솔트)
    ↓
Bandana(PluginSettings)에 암호화된 값 저장
    ↓
사용 시: AesEncryptionService.decrypt()
    ↓
OkHttp → Authorization: Bearer <plain-key>
```

- 각 Confluence 인스턴스의 `serverId`를 기반으로 고유 암호화 키 파생
- 데이터베이스에 평문 API 키가 저장되지 않음
- REST API 응답에 API 키 값이 포함되지 않음 (설정 여부만 반환)

### 6.2 XSS 방지

`ContentSanitizer`가 모든 생성/편집 콘텐츠에서 제거하는 패턴:

- `<script>` 태그
- `onclick`, `onload` 등 이벤트 핸들러
- `javascript:` URI 스키마
- `<iframe>`, `<object>`, `<embed>` 태그
- `data:` URI (이미지 제외)
- CSS `expression()` / `url(javascript:)`

### 6.3 권한 모델

| 작업 | 필요 권한 |
|---|---|
| 문서 생성 | 스페이스 EDIT 권한 |
| 참조 페이지 조회 | 페이지 VIEW 권한 |
| 새 페이지 저장 | 스페이스 CREATE 권한 |
| 기존 페이지 삽입 | 페이지 EDIT 권한 |
| 관리자 설정 | Confluence 시스템 관리자 |

### 6.4 로그 마스킹

`LogMasker`가 자동으로 마스킹하는 패턴:

- API 키: `Bearer sk-xxx...` → `***API_KEY***`
- 이메일: `user@company.com` → `***EMAIL***`
- 주민번호 패턴: `860101-1234567` → `***RRN***`
- IP 주소: `192.168.1.100` → `***IP***`

### 6.5 요청 제한 (Rate Limiting)

```
요청 진입
    ↓
1. 노드별 동시 처리 제한 (Semaphore)
    ↓ tryAcquireNodeSlot()
2. 사용자별 동시 요청 제한 (최대 3)
    ↓ tryAcquireUserSlot()
3. 사용자별 일일 요청 제한 (AO 기반)
    ↓ checkDailyLimit()
4. 스페이스별 정책 제한
    ↓ PolicyService.check()
5. 작업 큐 진입
```

---

## 7. 데이터베이스 스키마

### 7.1 AI_GEN_JOB (생성 작업)

| 컬럼 | 타입 | 설명 |
|---|---|---|
| ID | INT (PK) | 자동 증가 |
| JOB_UUID | VARCHAR (indexed) | 작업 고유 ID |
| USER_KEY | VARCHAR (indexed) | 요청 사용자 |
| SPACE_KEY | VARCHAR (indexed) | 대상 스페이스 |
| TEMPLATE_KEY | VARCHAR | 사용 템플릿 |
| STATUS | VARCHAR (indexed) | QUEUED/PROCESSING/COMPLETED/FAILED/CANCELLED/PARTIAL |
| PURPOSE | TEXT | 문서 목적 |
| AUDIENCE | VARCHAR | 대상 독자 |
| TONE | VARCHAR | 문서 톤 |
| LENGTH_PREFERENCE | VARCHAR | 길이 선호 |
| CONTEXT_PAGE_IDS | TEXT | JSON 배열 |
| ATTACHMENT_IDS | TEXT | JSON 배열 |
| LABELS | TEXT | JSON 배열 |
| PARENT_PAGE_ID | BIGINT | 부모 페이지 ID |
| TOTAL_SECTIONS | INT | 총 섹션 수 |
| COMPLETED_SECTIONS | INT | 완료된 섹션 수 |
| CURRENT_SECTION | VARCHAR | 현재 처리 중 섹션 |
| TOTAL_TOKENS | INT | 총 사용 토큰 |
| DURATION_MS | BIGINT | 총 소요시간(ms) |
| NODE_ID | VARCHAR | 처리 노드 ID |
| ERROR_MESSAGE | TEXT | 오류 메시지 |
| CREATED_AT | TIMESTAMP | 생성 시각 |
| STARTED_AT | TIMESTAMP | 처리 시작 시각 |
| COMPLETED_AT | TIMESTAMP | 완료 시각 |

### 7.2 AI_GEN_SECTION (섹션 결과)

| 컬럼 | 타입 | 설명 |
|---|---|---|
| ID | INT (PK) | 자동 증가 |
| JOB | FK → AI_GEN_JOB | 소속 작업 |
| SECTION_KEY | VARCHAR | 섹션 키 |
| SECTION_TITLE | VARCHAR | 섹션 제목 |
| ORDER_INDEX | INT | 정렬 순서 |
| CONTENT | TEXT (UNLIMITED) | 생성된 내용 |
| STATUS | VARCHAR | PENDING/COMPLETED/FAILED/FALLBACK |
| TOKEN_COUNT | INT | 섹션 토큰 수 |
| ERROR_MESSAGE | TEXT | 섹션 오류 메시지 |
| GENERATED_AT | TIMESTAMP | 생성 시각 |

### 7.3 기타 테이블

- **AI_GEN_AUDIT**: 감사 로그 (사용자, 액션, 스페이스, 페이지ID, 상세, 타임스탬프, 노드)
- **AI_GEN_USAGE**: 사용량 (사용자, 스페이스, 일자, 요청수, 토큰수)
- **AI_GEN_PROMPT**: 커스텀 프롬프트 (템플릿키, 시스템프롬프트, 섹션프롬프트, 활성여부)
- **AI_GEN_GLOSSARY**: 용어집 (용어, 정의, 스페이스키)
- **AI_GEN_FORBIDDEN**: 금칙어 (패턴, 대체어, 정규식여부, 스페이스키)
- **AI_GEN_POLICY**: 스페이스 정책 (스페이스키, 일일제한, 토큰제한, 허용그룹, 허용템플릿, 활성여부)

---

## 8. 트러블슈팅

### 8.1 공통 문제

#### 플러그인이 활성화되지 않음

**증상**: UPM에서 플러그인이 "비활성" 상태

**해결 방법**:
1. `atlassian-confluence.log`에서 오류 확인
2. OSGi 번들 의존성 문제일 수 있음 → Confluence 버전 호환성 확인
3. `java.lang.NoClassDefFoundError` → Import-Package 설정 확인

#### "AI 문서 생성" 메뉴가 보이지 않음

**원인 및 해결**:
1. 관리자 설정이 완료되지 않음 → `/plugins/servlet/ai-generation/admin/config`에서 설정
2. 스페이스 정책에서 비활성화됨 → 정책 확인
3. 사용자가 스페이스 EDIT 권한이 없음 → 권한 확인

#### vLLM 연결 실패

**확인 사항**:
```bash
# 1. 네트워크 연결 확인
curl http://vllm-server:8000/v1/models

# 2. API 키 확인
curl -H "Authorization: Bearer <key>" http://vllm-server:8000/v1/models

# 3. Confluence 서버에서 접근 가능한지 확인
# (방화벽, 프록시 설정)
```

#### 생성이 QUEUED에서 멈춤

**원인**: GenerationJobRunner가 실행되지 않거나 작업을 클레임하지 못함

**확인 사항**:
1. 로그에서 `GenerationJobRunner` 관련 메시지 확인
2. 동시 요청 제한에 걸린 경우 → 설정에서 `maxConcurrentRequests` 증가
3. 일일 사용량 한도 초과 → 설정 확인

#### 생성 결과가 깨짐 (HTML 태그 표시)

**원인**: vLLM이 Confluence Storage Format 대신 Markdown이나 일반 HTML을 반환

**해결**: 시스템 프롬프트에서 출력 형식을 명확히 지정:
- 관리자 → 프롬프트 관리에서 시스템 프롬프트 수정
- "Confluence Storage Format XHTML" 출력을 명시적으로 요청

### 8.2 로그 확인

```bash
# Confluence 로그 파일 위치
# Linux: /var/atlassian/application-data/confluence/logs/atlassian-confluence.log
# Windows: C:\Atlassian\ApplicationData\Confluence\logs\atlassian-confluence.log

# 플러그인 관련 로그 필터링
grep "aigeneration" atlassian-confluence.log
grep "GenerationJobRunner" atlassian-confluence.log
grep "VllmClient" atlassian-confluence.log
```

### 8.3 Health Check API

```bash
# 전체 상태 확인
curl -u admin:admin http://localhost:1990/confluence/rest/ai-generation/1.0/health

# vLLM 연결 테스트
curl -u admin:admin http://localhost:1990/confluence/rest/ai-generation/1.0/health/vllm
```

---

## 9. 성능 튜닝

### 9.1 vLLM 서버 최적화

| 파라미터 | 권장값 | 설명 |
|---|---|---|
| `--tensor-parallel-size` | GPU 수 | 모델 병렬 처리 |
| `--max-model-len` | 32768 | 최대 시퀀스 길이 |
| `--gpu-memory-utilization` | 0.9 | GPU 메모리 활용률 |
| `--max-num-seqs` | 256 | 동시 시퀀스 수 |

### 9.2 플러그인 설정 최적화

| 항목 | 소규모 팀 (10명) | 중규모 팀 (50명) | 대규모 (100명+) |
|---|---|---|---|
| 최대 동시 요청 | 3 | 5 | 10 |
| 사용자별 일일 제한 | 20 | 50 | 30 |
| 요청당 최대 토큰 | 4096 | 4096 | 2048 |
| Temperature | 0.7 | 0.7 | 0.5 |
| 타임아웃 | 60s | 90s | 120s |

### 9.3 데이터 정리

`CleanupJobRunner`가 매일 자동으로 실행되지만, 수동 정리도 가능합니다:

- 감사 로그 보존 기간 조정 (기본 30일)
- 사용량 데이터는 90일 보존
- 완료/실패/취소된 작업은 보존 기간 경과 후 자동 삭제

### 9.4 Data Center 고려사항

- 작업 큐는 AO 기반으로 클러스터 안전 (낙관적 동시성)
- 각 노드가 독립적으로 GenerationJobRunner 실행
- `nodeId`로 작업 소유권 추적
- 정체 작업(30분 이상 처리 중)은 자동 복구

---

## 10. FAQ

### Q: 어떤 LLM 모델을 지원하나요?

OpenAI Compatible API(`/v1/chat/completions`)를 제공하는 모든 모델/서버를 지원합니다:
- vLLM (권장)
- text-generation-inference (TGI)
- LocalAI
- LiteLLM (프록시)
- OpenAI API 직접 연결도 가능

### Q: 한국어 문서 생성 품질은 어떤가요?

한국어 성능이 좋은 모델을 사용해야 합니다:
- Qwen2.5 시리즈 (72B 이상 권장)
- SOLAR 10.7B
- Llama 3.1 (한국어 미세 조정 버전)

시스템 프롬프트에 "한국어로 작성"이 포함되어 있으며, 프롬프트 파일도 한국어로 작성되어 있습니다.

### Q: 생성된 콘텐츠가 서버에 저장되나요?

기본 정책은 **NO_STORE**입니다:
- 생성 작업(AO)은 보존 기간 후 자동 삭제
- 사용자가 명시적으로 "페이지로 저장"을 클릭해야만 Confluence 페이지로 영구 저장
- 감사 로그에는 생성 내용이 아닌 메타데이터만 기록

### Q: 오프라인(인터넷 없음) 환경에서도 사용 가능한가요?

네. vLLM 서버가 사내 네트워크에 있다면 완전한 오프라인 환경에서도 동작합니다. Confluence Server와 vLLM 서버 간 통신만 가능하면 됩니다.

### Q: Confluence Cloud에서도 사용 가능한가요?

아니요. 이 플러그인은 Confluence **Server / Data Center** 전용 P2 플러그인입니다. Confluence Cloud는 Forge 또는 Connect 앱 프레임워크가 필요합니다.

### Q: 커스텀 템플릿을 추가할 수 있나요?

두 가지 방법이 있습니다:

1. **관리자 프롬프트 관리**: 기존 템플릿의 프롬프트를 수정
2. **코드 수정**: `BuiltInTemplates.java`에 새 템플릿 추가 (5.4절 참고)

### Q: 생성 중 vLLM 서버가 다운되면?

- 현재 처리 중인 섹션은 실패로 마크됨
- 이미 완료된 섹션은 보존됨 (부분 결과)
- 실패한 섹션에는 폴백 초안(작성 가이드 포함)이 생성됨
- 사용자는 미리보기에서 실패 섹션만 재생성 가능

### Q: API 키는 안전하게 저장되나요?

네. API 키는 AES-256-GCM으로 암호화되어 Bandana(PluginSettings)에 저장됩니다:
- 암호화 키는 Confluence 인스턴스의 `serverId`를 기반으로 파생
- REST API 응답에 키 값이 노출되지 않음
- 로그에도 마스킹되어 기록됨

### Q: 다중 노드(Data Center)에서 동시 생성이 충돌하지 않나요?

AO의 낙관적 동시성 제어를 사용합니다:
- 각 노드는 `nodeId`를 기록하며 작업을 클레임
- 동시에 같은 작업을 클레임하면 하나만 성공
- 30분 이상 처리 중인 작업은 정체로 간주하여 다른 노드가 재클레임

---

## 버전 히스토리

| 버전 | 날짜 | 변경 사항 |
|---|---|---|
| 1.0.0 | 2025-02 | 최초 릴리스 |

---

*이 문서는 Expert Document Page AI Generation Plugin v1.0.0 기준으로 작성되었습니다.*

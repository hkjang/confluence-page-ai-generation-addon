# Expert Document Page AI Generation - Confluence Server Plugin

Confluence Server 7.2.1 P2 플러그인으로, vLLM(OpenAI Compatible API)을 활용하여 전문가 수준의 문서를 자동 생성합니다.

## 주요 기능

- **6종 내장 문서 템플릿**: 회의록, 설계서, RFC, 장애 보고서, 실행 계획, 주간보고
- **4단계 마법사 UI**: 템플릿 선택 → 목적/대상/톤/길이 설정 → 컨텍스트 수집 → 생성
- **섹션별 AI 생성**: 부분 실패 복구, 개별 섹션 재생성 가능
- **미리보기/편집**: 생성 결과 미리보기, 인라인 편집, 차이 비교
- **스페이스별 정책**: 그룹 제한, 템플릿 제한, 일일 사용량 제한
- **보안**: AES-256-GCM API 키 암호화, XSS 방지, 로그 마스킹
- **감사 로그**: 모든 주요 액션 로깅, 사용량 통계

## 기술 스택

| 항목 | 기술 |
|---|---|
| 언어 | Java 8 |
| 빌드 | Maven 3.x + Atlassian Plugin SDK (AMPS 8.1.2) |
| 대상 플랫폼 | Confluence Server / Data Center 7.2.1+ |
| AI 엔진 | vLLM (OpenAI Compatible `/v1/chat/completions` API) |
| HTTP 클라이언트 | OkHttp 3.14.9 |
| JSON | Gson 2.8.9 |
| 데이터 저장 | Active Objects (관계형 DB) |
| 설정 저장 | Bandana (PluginSettings) |
| 프론트엔드 | Soy 템플릿 + jQuery/AJS + AUI |
| DI | Atlassian Spring Scanner 2.2.1 |

## 빠른 시작

### 사전 요구사항

- JDK 8 (1.8.x)
- Maven 3.6+
- Atlassian Plugin SDK (선택사항, `atlas-mvn` 사용 시)
- vLLM 서버 (OpenAI Compatible API 활성화)

### 빌드

```bash
# 일반 Maven 빌드
mvn clean package -DskipTests

# Atlassian SDK 사용 시
atlas-mvn clean package -DskipTests
```

빌드 성공 시 `target/` 디렉토리에 아래 파일이 생성됩니다:

| 파일 | 설명 |
|---|---|
| `ai-page-generation-1.0.0.jar` | Confluence P2 플러그인 JAR |
| `ai-page-generation-1.0.0.obr` | OSGi Bundle Repository 파일 |

### 설치

1. Confluence 관리자 콘솔 → **앱 관리(UPM)** 접속
2. **앱 업로드** 클릭
3. `ai-page-generation-1.0.0.jar` 파일 선택 후 업로드
4. 플러그인 활성화 확인

### 초기 설정

1. Confluence 관리자 콘솔 → **AI 문서 생성 설정** 메뉴 클릭
2. vLLM 엔드포인트 입력 (예: `http://vllm-server:8000`)
3. 모델명 입력 (예: `Qwen/Qwen2.5-72B-Instruct`)
4. API 키 설정 (선택사항)
5. **연결 테스트** 클릭하여 정상 연결 확인
6. 저장

## 프로젝트 구조

```
src/main/
├── java/com/mycompany/confluence/aigeneration/
│   ├── ao/                 # Active Objects 엔티티 (8개)
│   ├── condition/          # Web-item 표시 조건 (2개)
│   ├── job/                # 스케줄링 작업 러너 (3개)
│   ├── listener/           # 플러그인 라이프사이클 리스너
│   ├── model/              # DTO/도메인 모델 (12개)
│   ├── rest/               # JAX-RS REST 리소스 (6개)
│   ├── security/           # 암호화/권한/위생처리/로그마스킹 (5개)
│   ├── service/            # 서비스 인터페이스 (11개)
│   │   └── impl/           # 서비스 구현체 (11개)
│   ├── servlet/            # 관리자 서블릿
│   ├── template/           # 내장 문서 템플릿 팩토리
│   └── util/               # 유틸리티 (3개)
└── resources/
    ├── atlassian-plugin.xml    # 플러그인 디스크립터
    ├── css/                    # 스타일시트 (3개)
    ├── i18n/                   # 국제화 (한국어/영어)
    ├── images/                 # 아이콘 SVG
    ├── js/                     # JavaScript (5개)
    ├── prompts/                # AI 프롬프트 (7개)
    ├── soy/                    # Soy 템플릿 (4개)
    └── templates/              # Velocity 관리자 템플릿 (4개)
```

**총 67개 Java 파일 / 28개 리소스 파일 / ~5,200 라인 Java 코드**

## REST API

기본 경로: `/rest/ai-generation/1.0`

| 메서드 | 경로 | 설명 |
|---|---|---|
| POST | `/generation/start` | 생성 작업 시작 |
| GET | `/generation/status/{jobId}` | 작업 상태 조회 |
| GET | `/generation/progress/{jobId}` | 진행률 조회 |
| GET | `/generation/result/{jobId}` | 생성 결과 조회 |
| POST | `/generation/cancel/{jobId}` | 작업 취소 |
| POST | `/generation/retry/{jobId}` | 실패 작업 재시도 |
| GET | `/templates` | 템플릿 목록 |
| GET | `/templates/{key}` | 템플릿 상세 |
| GET | `/context/pages` | 컨텍스트 페이지 검색 |
| GET | `/context/attachments` | 첨부 파일 조회 |
| GET | `/context/labels` | 라벨 조회 |
| POST | `/preview/save` | 새 페이지로 저장 |
| POST | `/preview/insert` | 기존 페이지에 삽입 |
| PUT | `/preview/section` | 섹션 내용 수정 |
| GET | `/health` | 플러그인 상태 확인 |
| GET | `/health/vllm` | vLLM 연결 테스트 |
| GET/PUT | `/admin/config` | 관리자 설정 CRUD |
| GET/POST/PUT/DELETE | `/admin/prompts` | 프롬프트 관리 |
| GET/POST/PUT/DELETE | `/admin/glossary` | 용어집 관리 |
| GET/POST/DELETE | `/admin/forbidden-words` | 금칙어 관리 |
| GET/PUT/DELETE | `/admin/policies/{spaceKey}` | 스페이스 정책 |
| GET | `/admin/audit` | 감사 로그 조회 |
| GET | `/admin/usage` | 사용량 통계 |

## 내장 문서 템플릿

| 키 | 이름 | 필수 섹션 |
|---|---|---|
| `meeting-notes` | 회의록 | 일시/참석자, 안건, 논의사항, 결정사항, 액션아이템 |
| `design-doc` | 설계 문서 | 개요, 배경, 설계, 대안분석, 구현계획 |
| `rfc` | RFC | 요약, 동기, 상세설계, 하위호환성, 구현일정 |
| `incident-report` | 장애 보고서 | 요약, 타임라인, 영향, 근본원인, 재발방지 |
| `execution-plan` | 실행 계획 | 목표, 범위, 마일스톤, 리소스, 리스크 |
| `weekly-report` | 주간 보고 | 요약, 금주성과, 진행중, 차주계획, 이슈 |

## 라이선스

Proprietary - MyCompany Internal Use Only

package com.mycompany.confluence.aigeneration.template;

import com.mycompany.confluence.aigeneration.model.DocumentTemplate;
import com.mycompany.confluence.aigeneration.model.SectionDefinition;

import java.util.*;

/**
 * Factory providing built-in document templates (6 types).
 * Each template defines required/recommended sections, quality checklist, and example phrases.
 */
public final class BuiltInTemplates {

    private BuiltInTemplates() {}

    public static List<DocumentTemplate> getAll() {
        List<DocumentTemplate> templates = new ArrayList<>();
        templates.add(meetingNotes());
        templates.add(designDoc());
        templates.add(rfc());
        templates.add(incidentReport());
        templates.add(executionPlan());
        templates.add(weeklyReport());
        return Collections.unmodifiableList(templates);
    }

    // ─────────────────────────── 1. 회의록 ───────────────────────────
    private static DocumentTemplate meetingNotes() {
        DocumentTemplate t = new DocumentTemplate();
        t.setKey("meeting-notes");
        t.setNameI18nKey("ai.template.meeting.notes.name");
        t.setDescriptionI18nKey("ai.template.meeting.notes.description");
        t.setIconClass("aui-iconfont-page-blogpost");

        List<SectionDefinition> required = new ArrayList<>();
        required.add(sec("meeting-info", "ai.section.meeting.info", "회의 정보",
                "참석자, 일시, 장소, 회의 유형을 포함합니다.", true, 0));
        required.add(sec("agenda", "ai.section.meeting.agenda", "안건",
                "번호를 매긴 안건 목록. 각 안건에 담당자와 예상 소요시간을 포함합니다.", true, 1));
        required.add(sec("discussion", "ai.section.meeting.discussion", "논의 내용",
                "각 안건별 핵심 논의사항, 의견 대립점, 합의 사항을 구조적으로 정리합니다.", true, 2));
        required.add(sec("decisions", "ai.section.meeting.decisions", "결정 사항",
                "회의에서 확정된 결정사항을 명확하게 기술합니다. 결정 배경도 간략히 포함합니다.", true, 3));
        required.add(sec("action-items", "ai.section.meeting.actions", "실행 항목",
                "담당자, 기한, 구체적 내용을 포함한 실행 항목 표를 작성합니다.", true, 4));

        List<SectionDefinition> recommended = new ArrayList<>();
        recommended.add(sec("next-meeting", "ai.section.meeting.next", "다음 회의",
                "다음 회의 일정, 사전 준비 사항을 안내합니다.", false, 5));
        recommended.add(sec("appendix", "ai.section.meeting.appendix", "부록",
                "참고 자료, 데이터, 첨부 파일 목록을 정리합니다.", false, 6));

        t.setRequiredSections(required);
        t.setRecommendedSections(recommended);

        t.setQualityChecklist(Arrays.asList(
                "모든 안건에 대한 논의 내용이 포함되었는가",
                "결정 사항이 명확히 기록되었는가",
                "실행 항목에 담당자와 기한이 있는가",
                "참석자 정보가 정확한가",
                "중립적이고 객관적인 톤인가"
        ));

        Map<String, String> examples = new LinkedHashMap<>();
        examples.put("decisions", "결정: 신규 CI/CD 파이프라인은 Jenkins에서 GitHub Actions로 마이그레이션하기로 결정");
        examples.put("action-items", "| 담당자 | 내용 | 기한 |\n|---|---|---|\n| 김철수 | 마이그레이션 계획서 작성 | 2024-03-15 |");
        t.setExamplePhrases(examples);
        return t;
    }

    // ─────────────────────────── 2. 설계서 ───────────────────────────
    private static DocumentTemplate designDoc() {
        DocumentTemplate t = new DocumentTemplate();
        t.setKey("design-doc");
        t.setNameI18nKey("ai.template.design.doc.name");
        t.setDescriptionI18nKey("ai.template.design.doc.description");
        t.setIconClass("aui-iconfont-devtools-file");

        List<SectionDefinition> required = new ArrayList<>();
        required.add(sec("overview", "ai.section.design.overview", "개요",
                "프로젝트/기능의 배경, 목적, 범위를 설명합니다.", true, 0));
        required.add(sec("requirements", "ai.section.design.requirements", "요구사항",
                "기능적/비기능적 요구사항을 분류하여 기술합니다.", true, 1));
        required.add(sec("architecture", "ai.section.design.architecture", "아키텍처",
                "시스템 구조, 컴포넌트 다이어그램, 데이터 흐름을 설명합니다.", true, 2));
        required.add(sec("detailed-design", "ai.section.design.detail", "상세 설계",
                "클래스 설계, API 명세, 데이터 모델 등 구현 수준의 설계를 기술합니다.", true, 3));
        required.add(sec("implementation-plan", "ai.section.design.impl.plan", "구현 계획",
                "단계별 구현 계획, 마일스톤, 의존성을 정리합니다.", true, 4));

        List<SectionDefinition> recommended = new ArrayList<>();
        recommended.add(sec("alternatives", "ai.section.design.alternatives", "대안 분석",
                "고려한 대안과 각 장단점, 선택 이유를 설명합니다.", false, 5));
        recommended.add(sec("risks", "ai.section.design.risks", "리스크 및 완화 방안",
                "기술적 리스크와 완화 전략을 식별합니다.", false, 6));
        recommended.add(sec("testing-strategy", "ai.section.design.testing", "테스트 전략",
                "단위/통합/E2E 테스트 전략과 품질 기준을 기술합니다.", false, 7));
        recommended.add(sec("glossary", "ai.section.design.glossary", "용어 정의",
                "문서에서 사용하는 주요 용어를 정의합니다.", false, 8));

        t.setRequiredSections(required);
        t.setRecommendedSections(recommended);

        t.setQualityChecklist(Arrays.asList(
                "요구사항이 명확하고 측정 가능한가",
                "아키텍처가 요구사항을 충족하는가",
                "상세 설계가 구현 가능한 수준인가",
                "대안 분석이 포함되었는가",
                "리스크가 식별되고 완화 방안이 있는가"
        ));

        Map<String, String> examples = new LinkedHashMap<>();
        examples.put("overview", "본 설계서는 사용자 인증 시스템의 OAuth 2.0 기반 리팩토링을 다룹니다.");
        examples.put("requirements", "NFR-001: 로그인 응답시간 500ms 이하 (P99 기준)");
        t.setExamplePhrases(examples);
        return t;
    }

    // ─────────────────────────── 3. RFC ───────────────────────────
    private static DocumentTemplate rfc() {
        DocumentTemplate t = new DocumentTemplate();
        t.setKey("rfc");
        t.setNameI18nKey("ai.template.rfc.name");
        t.setDescriptionI18nKey("ai.template.rfc.description");
        t.setIconClass("aui-iconfont-arrows-right");

        List<SectionDefinition> required = new ArrayList<>();
        required.add(sec("summary", "ai.section.rfc.summary", "요약",
                "제안의 핵심을 1-2문단으로 요약합니다.", true, 0));
        required.add(sec("motivation", "ai.section.rfc.motivation", "배경 및 동기",
                "현재 문제점, 왜 변경이 필요한지를 설명합니다.", true, 1));
        required.add(sec("proposal", "ai.section.rfc.proposal", "제안",
                "구체적인 변경 내용, 설계, 구현 방법을 상세히 기술합니다.", true, 2));
        required.add(sec("impact", "ai.section.rfc.impact", "영향 범위",
                "변경으로 인한 영향을 받는 시스템, 팀, 프로세스를 식별합니다.", true, 3));

        List<SectionDefinition> recommended = new ArrayList<>();
        recommended.add(sec("alternatives", "ai.section.rfc.alternatives", "대안",
                "검토한 대안들과 채택하지 않은 이유를 설명합니다.", false, 4));
        recommended.add(sec("rollout-plan", "ai.section.rfc.rollout", "적용 계획",
                "단계별 적용 계획, 롤백 전략을 기술합니다.", false, 5));
        recommended.add(sec("open-questions", "ai.section.rfc.questions", "미결 사항",
                "추가 논의가 필요한 사항을 나열합니다.", false, 6));

        t.setRequiredSections(required);
        t.setRecommendedSections(recommended);

        t.setQualityChecklist(Arrays.asList(
                "제안의 동기가 명확한가",
                "구체적인 해결 방안이 제시되었는가",
                "영향 범위가 충분히 분석되었는가",
                "대안이 검토되었는가",
                "실행 가능한 적용 계획이 있는가"
        ));

        Map<String, String> examples = new LinkedHashMap<>();
        examples.put("summary", "본 RFC는 모놀리스 아키텍처에서 마이크로서비스로의 점진적 전환을 제안합니다.");
        examples.put("motivation", "현재 배포 주기가 2주이며, 서비스 간 결합도가 높아 독립적 배포가 불가능합니다.");
        t.setExamplePhrases(examples);
        return t;
    }

    // ─────────────────────────── 4. 장애 보고서 ───────────────────────────
    private static DocumentTemplate incidentReport() {
        DocumentTemplate t = new DocumentTemplate();
        t.setKey("incident-report");
        t.setNameI18nKey("ai.template.incident.report.name");
        t.setDescriptionI18nKey("ai.template.incident.report.description");
        t.setIconClass("aui-iconfont-warning");

        List<SectionDefinition> required = new ArrayList<>();
        required.add(sec("incident-summary", "ai.section.incident.summary", "장애 요약",
                "장애 유형, 심각도, 영향 범위, 지속 시간을 요약합니다.", true, 0));
        required.add(sec("timeline", "ai.section.incident.timeline", "타임라인",
                "시간 순서대로 장애 감지, 대응, 복구 과정을 기록합니다.", true, 1));
        required.add(sec("root-cause", "ai.section.incident.root.cause", "근본 원인",
                "장애의 근본 원인을 기술적으로 분석합니다.", true, 2));
        required.add(sec("impact-analysis", "ai.section.incident.impact", "영향 분석",
                "사용자/비즈니스 영향을 정량적으로 분석합니다.", true, 3));
        required.add(sec("remediation", "ai.section.incident.remediation", "조치 사항",
                "단기 조치와 장기 개선 방안을 구분하여 기술합니다.", true, 4));

        List<SectionDefinition> recommended = new ArrayList<>();
        recommended.add(sec("lessons-learned", "ai.section.incident.lessons", "교훈",
                "이번 장애에서 배운 점과 개선 방향을 기술합니다.", false, 5));
        recommended.add(sec("prevention", "ai.section.incident.prevention", "재발 방지",
                "동일 장애 재발 방지를 위한 구체적 방안을 제시합니다.", false, 6));

        t.setRequiredSections(required);
        t.setRecommendedSections(recommended);

        t.setQualityChecklist(Arrays.asList(
                "장애 타임라인이 정확하고 완전한가",
                "근본 원인이 깊이 분석되었는가",
                "영향이 정량적으로 측정되었는가",
                "재발 방지 대책이 구체적인가",
                "비난하지 않는 객관적 톤인가"
        ));

        Map<String, String> examples = new LinkedHashMap<>();
        examples.put("incident-summary", "2024-03-10 14:00 ~ 15:30 KST, 결제 서비스 장애 (심각도: P1, 영향 사용자: 약 15,000명)");
        examples.put("timeline", "14:00 - 모니터링 알림 발생 (결제 성공률 30% 하락)\n14:05 - 온콜 엔지니어 호출");
        t.setExamplePhrases(examples);
        return t;
    }

    // ─────────────────────────── 5. 실행 계획 ───────────────────────────
    private static DocumentTemplate executionPlan() {
        DocumentTemplate t = new DocumentTemplate();
        t.setKey("execution-plan");
        t.setNameI18nKey("ai.template.execution.plan.name");
        t.setDescriptionI18nKey("ai.template.execution.plan.description");
        t.setIconClass("aui-iconfont-plan");

        List<SectionDefinition> required = new ArrayList<>();
        required.add(sec("objective", "ai.section.plan.objective", "목표",
                "프로젝트/이니셔티브의 목표와 기대 성과를 정의합니다.", true, 0));
        required.add(sec("scope", "ai.section.plan.scope", "범위",
                "포함/제외 범위를 명확히 정의합니다.", true, 1));
        required.add(sec("milestones", "ai.section.plan.milestones", "마일스톤",
                "주요 마일스톤과 기한을 표로 정리합니다.", true, 2));
        required.add(sec("tasks", "ai.section.plan.tasks", "세부 작업",
                "마일스톤별 세부 작업, 담당자, 기한, 의존성을 정리합니다.", true, 3));
        required.add(sec("resources", "ai.section.plan.resources", "리소스",
                "필요한 인력, 예산, 인프라 등 리소스를 기술합니다.", true, 4));

        List<SectionDefinition> recommended = new ArrayList<>();
        recommended.add(sec("risks", "ai.section.plan.risks", "리스크",
                "예상 리스크와 완화 방안을 식별합니다.", false, 5));
        recommended.add(sec("success-criteria", "ai.section.plan.success", "성공 기준",
                "프로젝트 성공을 판단하는 정량적 기준을 정의합니다.", false, 6));
        recommended.add(sec("communication", "ai.section.plan.communication", "커뮤니케이션 계획",
                "보고 주기, 이해관계자, 커뮤니케이션 채널을 정의합니다.", false, 7));

        t.setRequiredSections(required);
        t.setRecommendedSections(recommended);

        t.setQualityChecklist(Arrays.asList(
                "목표가 SMART 기준을 충족하는가",
                "마일스톤이 현실적인가",
                "리소스 요구사항이 명확한가",
                "리스크가 식별되고 완화 방안이 있는가",
                "성공 기준이 측정 가능한가"
        ));

        Map<String, String> examples = new LinkedHashMap<>();
        examples.put("objective", "2024년 2분기까지 레거시 모놀리스에서 3개 핵심 도메인을 마이크로서비스로 분리");
        examples.put("milestones", "| 마일스톤 | 기한 | 담당팀 |\n|---|---|---|\n| 도메인 분석 완료 | 4/15 | 아키텍처팀 |");
        t.setExamplePhrases(examples);
        return t;
    }

    // ─────────────────────────── 6. 주간 보고 ───────────────────────────
    private static DocumentTemplate weeklyReport() {
        DocumentTemplate t = new DocumentTemplate();
        t.setKey("weekly-report");
        t.setNameI18nKey("ai.template.weekly.report.name");
        t.setDescriptionI18nKey("ai.template.weekly.report.description");
        t.setIconClass("aui-iconfont-calendar");

        List<SectionDefinition> required = new ArrayList<>();
        required.add(sec("summary", "ai.section.weekly.summary", "주간 요약",
                "이번 주 핵심 성과와 이슈를 1-2문단으로 요약합니다.", true, 0));
        required.add(sec("accomplishments", "ai.section.weekly.accomplishments", "금주 성과",
                "이번 주 완료된 작업, 달성한 목표를 정리합니다.", true, 1));
        required.add(sec("in-progress", "ai.section.weekly.inprogress", "진행 중",
                "현재 진행 중인 작업과 진척도를 기술합니다.", true, 2));
        required.add(sec("next-week", "ai.section.weekly.next", "차주 계획",
                "다음 주 예정된 작업과 목표를 기술합니다.", true, 3));

        List<SectionDefinition> recommended = new ArrayList<>();
        recommended.add(sec("blockers", "ai.section.weekly.blockers", "이슈 및 장애 요소",
                "진행을 방해하는 이슈와 해결 방안을 기술합니다.", false, 4));
        recommended.add(sec("metrics", "ai.section.weekly.metrics", "주요 지표",
                "핵심 성과 지표(KPI) 현황을 정리합니다.", false, 5));
        recommended.add(sec("team-updates", "ai.section.weekly.team", "팀 소식",
                "팀 관련 소식, 인사, 공지 등을 기술합니다.", false, 6));

        t.setRequiredSections(required);
        t.setRecommendedSections(recommended);

        t.setQualityChecklist(Arrays.asList(
                "성과가 구체적이고 측정 가능한가",
                "진행 중인 작업의 완료 예상일이 있는가",
                "차주 계획이 실행 가능한가",
                "이슈에 대한 해결 방안이 있는가",
                "간결하고 읽기 쉬운가"
        ));

        Map<String, String> examples = new LinkedHashMap<>();
        examples.put("accomplishments", "- API v2 엔드포인트 개발 완료 (예정 대비 2일 앞당겨 완료)\n- 성능 테스트 결과: 응답시간 P99 200ms → 150ms 개선");
        examples.put("blockers", "- 외부 API 연동: 파트너사 API 문서 미제공 (담당: 김철수, 기한: 3/15)");
        t.setExamplePhrases(examples);
        return t;
    }

    // ─────────────────────────── Helper ───────────────────────────
    private static SectionDefinition sec(String key, String i18nKey, String defaultTitle,
                                         String promptHint, boolean required, int orderIndex) {
        return new SectionDefinition(key, i18nKey, defaultTitle, promptHint, required, orderIndex);
    }
}

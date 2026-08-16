package dev.juhyeonl.atscheck.cli.render;

import static org.assertj.core.api.Assertions.assertThat;

import dev.juhyeonl.atscheck.core.model.CheckResult;
import dev.juhyeonl.atscheck.core.model.Clause;
import dev.juhyeonl.atscheck.core.model.Finding;
import dev.juhyeonl.atscheck.core.model.RequirementLevel;
import dev.juhyeonl.atscheck.core.model.RuleId;
import dev.juhyeonl.atscheck.core.model.SectionKind;
import dev.juhyeonl.atscheck.core.model.SkillGap;
import dev.juhyeonl.atscheck.core.model.Status;
import dev.juhyeonl.atscheck.core.model.Verdict;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class TerminalRendererTest {
    @Test
    void rendersLabelsAndEvidenceAtConfiguredColumns() {
        CheckResult result = new CheckResult(
                Verdict.SKIP,
                List.of(
                        finding(
                                RuleId.LANGUAGE,
                                Status.FAIL,
                                "Finnish required",
                                evidence("Fluent Finnish and English are required")
                        ),
                        finding(RuleId.EXPERIENCE_YEARS, Status.PASS, "3+ years (profile: 2, tolerance: 1)"),
                        finding(RuleId.DEGREE, Status.PASS, "not required")
                ),
                null,
                true
        );

        assertThat(TerminalRenderer.render(result)).isEqualTo("""
                VERDICT: SKIP

                  ✗ Language    Finnish required
                                "Fluent Finnish and English are required"
                  ✓ Seniority   3+ years (profile: 2, tolerance: 1)
                  ✓ Degree      not required

                  Analysis stopped at hard filter.
                """);
    }

    @Test
    void rendersFindingsInTerminalDisplayOrderWithoutChangingResultOrder() {
        CheckResult result = new CheckResult(
                Verdict.APPLY,
                List.of(
                        finding(RuleId.LANGUAGE, Status.PASS, "English only"),
                        finding(RuleId.EXPERIENCE_YEARS, Status.WARN, "3+ years (profile: 2, tolerance: 1)"),
                        finding(RuleId.DEGREE, Status.PASS, "not required"),
                        finding(RuleId.SENIORITY_LEVEL, Status.PASS, "Backend Engineer (no seniority marker)"),
                        finding(RuleId.SKILLS, Status.PASS, "missing 2 required, 1 nice")
                ),
                new SkillGap(
                        orderedSet("java", "spring boot", "postgresql", "rest", "docker"),
                        orderedSet("kotlin", "kubernetes"),
                        orderedSet("terraform")
                ),
                false
        );

        String rendered = TerminalRenderer.render(result);

        assertThat(result.findings())
                .extracting(Finding::rule)
                .containsExactly(
                        RuleId.LANGUAGE,
                        RuleId.EXPERIENCE_YEARS,
                        RuleId.DEGREE,
                        RuleId.SENIORITY_LEVEL,
                        RuleId.SKILLS
                );
        assertThat(rendered).isEqualTo("""
                VERDICT: APPLY

                  ✓ Language    English only
                  ✓ Level       Backend Engineer (no seniority marker)
                  ⚠ Seniority   3+ years (profile: 2, tolerance: 1) — borderline
                  ✓ Degree      not required

                  MISSING (required)   Kotlin, Kubernetes
                  MISSING (nice)       Terraform
                  MATCHED              Java, Spring Boot, PostgreSQL, REST, Docker
                """);
    }

    @Test
    void rendersKnownSkillDisplayNamesAndLeavesUnknownSkillsUnchanged() {
        CheckResult result = new CheckResult(
                Verdict.APPLY,
                List.of(),
                new SkillGap(
                        orderedSet("typescript", "node.js", "c++", ".net", "unknown runtime"),
                        orderedSet("aws", "graphql"),
                        orderedSet("grpc", "ci/cd")
                ),
                false
        );

        assertThat(TerminalRenderer.render(result)).contains("""
                  MISSING (required)   AWS, GraphQL
                  MISSING (nice)       gRPC, CI/CD
                  MATCHED              TypeScript, Node.js, C++, .NET, unknown runtime
                """);
    }

    @Test
    void rendersKoreanVerdictLabelsSkillBlockAndHardFilterWithoutTranslatingEvidence() {
        CheckResult result = new CheckResult(
                Verdict.SKIP,
                List.of(
                        finding(
                                RuleId.LANGUAGE,
                                Status.FAIL,
                                "Finnish required",
                                evidence("Fluent Finnish and English are required")
                        ),
                        finding(RuleId.EXPERIENCE_YEARS, Status.PASS, "not specified"),
                        finding(RuleId.DEGREE, Status.PASS, "not required")
                ),
                new SkillGap(
                        orderedSet("java"),
                        orderedSet("kotlin"),
                        orderedSet("terraform")
                ),
                true
        );

        String rendered = TerminalRenderer.render(result, TerminalLanguage.KO);

        assertThat(rendered)
                .contains("판정: 제외")
                .contains("✗ 언어        핀란드어 필수")
                .contains("✓ 연차        명시 없음")
                .contains("✓ 학위        요구 없음")
                .contains("\"Fluent Finnish and English are required\"")
                .contains("부족 (필수)", "부족 (우대)", "보유")
                .contains("하드 필터에서 분석을 중단했습니다.");
        assertThat(rendered).doesNotContain("VERDICT:", "Language", "Analysis stopped at hard filter.");
    }

    @Test
    void koreanFindingSummariesStartAtSameDisplayColumn() {
        CheckResult result = new CheckResult(
                Verdict.APPLY,
                List.of(
                        finding(RuleId.LANGUAGE, Status.PASS, "English only"),
                        finding(RuleId.SENIORITY_LEVEL, Status.PASS, "Backend Engineer (no seniority marker)"),
                        finding(RuleId.EXPERIENCE_YEARS, Status.WARN, "3+ years (profile: 2, tolerance: 1)"),
                        finding(RuleId.DEGREE, Status.PASS, "not required")
                ),
                null,
                false
        );

        String rendered = TerminalRenderer.render(result, TerminalLanguage.KO);

        assertThat(List.of(
                displayIndexOf(rendered, "영어만 요구"),
                displayIndexOf(rendered, "Backend Engineer (레벨 표시 없음)"),
                displayIndexOf(rendered, "3년 이상 (내 경력: 2, 허용: 1) — 경계선"),
                displayIndexOf(rendered, "요구 없음")
        )).containsOnly(16);
    }

    @Test
    void unmappedKoreanSummaryFallsBackToEnglish() {
        CheckResult result = new CheckResult(
                Verdict.APPLY,
                List.of(finding(RuleId.LANGUAGE, Status.PASS, "custom summary")),
                null,
                false
        );

        assertThat(TerminalRenderer.render(result, TerminalLanguage.KO)).contains("custom summary");
    }

    private static Finding finding(RuleId rule, Status status, String summary) {
        return finding(rule, status, summary, List.of());
    }

    private static Finding finding(RuleId rule, Status status, String summary, List<Clause> evidence) {
        return new Finding(rule, status, summary, evidence);
    }

    private static List<Clause> evidence(String text) {
        return List.of(new Clause(
                text,
                1,
                RequirementLevel.REQUIRED,
                SectionKind.REQUIRED_SECTION,
                List.of()
        ));
    }

    private static Set<String> orderedSet(String... values) {
        return new LinkedHashSet<>(List.of(values));
    }

    private static int displayIndexOf(String rendered, String text) {
        String line = rendered.lines()
                .filter(candidate -> candidate.contains(text))
                .findFirst()
                .orElseThrow();
        return DisplayWidth.width(line.substring(0, line.indexOf(text)));
    }
}

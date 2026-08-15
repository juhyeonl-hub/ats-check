package dev.juhyeonl.atscheck.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import dev.juhyeonl.atscheck.core.model.CheckResult;
import dev.juhyeonl.atscheck.core.model.Degree;
import dev.juhyeonl.atscheck.core.model.Finding;
import dev.juhyeonl.atscheck.core.model.Profile;
import dev.juhyeonl.atscheck.core.model.RuleId;
import dev.juhyeonl.atscheck.core.model.Seniority;
import dev.juhyeonl.atscheck.core.model.Status;
import dev.juhyeonl.atscheck.core.model.Verdict;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AtsCheckerTest {
    @Test
    @DisplayName("핀란드어 필수 공고는 SKIP이고 하드 필터에서 Stage 2와 3을 멈춘다")
    void finnishRequiredJobIsSkippedAtHardFilter() {
        CheckResult result = AtsChecker.check("""
                Backend Engineer
                Requirements:
                Fluent Finnish is required.
                Java.
                """, profile(5, 0, Seniority.LEAD, Degree.NONE, Set.of("english"), "java"));

        assertThat(result.verdict()).isEqualTo(Verdict.SKIP);
        assertThat(result.stoppedAtHardFilter()).isTrue();
        assertThat(result.skillGap()).isNull();
        assertThat(result.findings()).extracting(Finding::rule)
                .containsExactly(RuleId.LANGUAGE, RuleId.EXPERIENCE_YEARS, RuleId.DEGREE);
    }

    @Test
    @DisplayName("SKIP이어도 Stage 1의 세 Finding은 모두 들어 있다")
    void skipStillIncludesAllStageOneFindings() {
        CheckResult result = AtsChecker.check("""
                Backend Engineer
                Requirements:
                Fluent Finnish is required.
                10+ years with Java.
                PhD in Computer Science.
                """, profile(0, 0, Seniority.LEAD, Degree.NONE, Set.of("english"), "java"));

        assertThat(result.verdict()).isEqualTo(Verdict.SKIP);
        assertThat(result.findings())
                .extracting(Finding::rule, Finding::status)
                .containsExactly(
                        tuple(RuleId.LANGUAGE, Status.FAIL),
                        tuple(RuleId.EXPERIENCE_YEARS, Status.FAIL),
                        tuple(RuleId.DEGREE, Status.FAIL)
                );
    }

    @Test
    @DisplayName("애매한 핀란드어 공고는 REVIEW이다")
    void ambiguousFinnishJobNeedsReview() {
        CheckResult result = AtsChecker.check("""
                Backend Engineer
                Requirements:
                Working knowledge of Finnish.
                Java.
                """, profile(5, 0, Seniority.LEAD, Degree.NONE, Set.of("english"), "java"));

        assertThat(result.verdict()).isEqualTo(Verdict.REVIEW);
        assertThat(result.stoppedAtHardFilter()).isFalse();
        assertThat(result.skillGap()).isNotNull();
    }

    @Test
    @DisplayName("하드 필터가 깨끗한 공고는 APPLY이고 skillGap이 있다")
    void cleanJobAppliesWithSkillGap() {
        CheckResult result = AtsChecker.check("""
                Backend Engineer
                Requirements:
                Java and Kotlin.
                """, profile(5, 0, Seniority.MID, Degree.NONE, Set.of("english"), "java", "kotlin"));

        assertThat(result.verdict()).isEqualTo(Verdict.APPLY);
        assertThat(result.skillGap()).isNotNull();
        assertThat(result.skillGap().matched()).containsExactly("java", "kotlin");
        assertThat(result.findings()).extracting(Finding::rule)
                .containsExactly(
                        RuleId.LANGUAGE,
                        RuleId.EXPERIENCE_YEARS,
                        RuleId.DEGREE,
                        RuleId.SENIORITY_LEVEL,
                        RuleId.SKILLS
                );
    }

    @Test
    @DisplayName("WARN만 있는 공고는 APPLY이다")
    void warnOnlyDoesNotChangeVerdict() {
        CheckResult result = AtsChecker.check("""
                Senior Backend Engineer
                Requirements:
                Java.
                """, profile(5, 0, Seniority.MID, Degree.NONE, Set.of("english"), "java"));

        assertThat(result.verdict()).isEqualTo(Verdict.APPLY);
        assertThat(result.findings())
                .extracting(Finding::rule, Finding::status)
                .contains(tuple(RuleId.SENIORITY_LEVEL, Status.WARN));
    }

    @Test
    @DisplayName("SKIP과 APPLY 통합 예시는 기대 verdict와 Finding 목록을 낸다")
    void integrationExamplesProduceExpectedVerdictsAndFindingLists() {
        CheckResult skip = AtsChecker.check("""
                Backend Engineer
                Requirements:
                Fluent Finnish is required.
                3+ years with Java.
                """, profile(2, 1, Seniority.LEAD, Degree.NONE, Set.of("english"), "java"));

        assertThat(skip.verdict()).isEqualTo(Verdict.SKIP);
        assertThat(skip.findings())
                .extracting(Finding::rule, Finding::status)
                .containsExactly(
                        tuple(RuleId.LANGUAGE, Status.FAIL),
                        tuple(RuleId.EXPERIENCE_YEARS, Status.WARN),
                        tuple(RuleId.DEGREE, Status.PASS)
                );

        CheckResult apply = AtsChecker.check("""
                Backend Engineer
                Requirements:
                Java and Kotlin.
                Nice to have:
                Terraform.
                """, profile(5, 0, Seniority.MID, Degree.NONE, Set.of("english"), "java"));

        assertThat(apply.verdict()).isEqualTo(Verdict.APPLY);
        assertThat(apply.findings()).extracting(Finding::rule)
                .containsExactly(
                        RuleId.LANGUAGE,
                        RuleId.EXPERIENCE_YEARS,
                        RuleId.DEGREE,
                        RuleId.SENIORITY_LEVEL,
                        RuleId.SKILLS
                );
        assertThat(apply.skillGap().matched()).containsExactly("java");
        assertThat(apply.skillGap().missingRequired()).containsExactly("kotlin");
        assertThat(apply.skillGap().missingNice()).containsExactly("terraform");
    }

    private Profile profile(
            int yearsExperience,
            int yearsTolerance,
            Seniority maxSeniority,
            Degree degree,
            Set<String> languages,
            String... skills
    ) {
        return new Profile(
                yearsExperience,
                yearsTolerance,
                maxSeniority,
                languages,
                degree,
                Set.of(skills)
        );
    }
}

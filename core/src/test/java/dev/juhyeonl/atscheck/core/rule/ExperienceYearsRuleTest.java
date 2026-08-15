package dev.juhyeonl.atscheck.core.rule;

import static org.assertj.core.api.Assertions.assertThat;

import dev.juhyeonl.atscheck.core.model.Clause;
import dev.juhyeonl.atscheck.core.model.Degree;
import dev.juhyeonl.atscheck.core.model.Finding;
import dev.juhyeonl.atscheck.core.model.Profile;
import dev.juhyeonl.atscheck.core.model.Seniority;
import dev.juhyeonl.atscheck.core.model.Status;
import dev.juhyeonl.atscheck.core.section.SectionClassifier;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ExperienceYearsRuleTest {
    @ParameterizedTest(name = "{0} 표현은 {1}년 요구로 해석된다")
    @CsvSource({
            "3+ years, 3",
            "3 years, 3",
            "at least 3 years, 3",
            "minimum of 3 years, 3",
            "minimum 3 years, 3",
            "over 5 years, 5",
            "more than 5 years, 5",
            "5 or more years, 5",
            "3-5 years, 3",
            "3 to 5 years, 3",
            "three years, 3"
    })
    @DisplayName("연차 추출 표의 모든 형태를 요구 연차로 파싱한다")
    void parsesEveryRequiredExperiencePattern(String phrase, int expectedYears) {
        assertThat(evaluate(required(phrase + " with Java"), profile(expectedYears, 0)).status())
                .isEqualTo(Status.PASS);
        assertThat(evaluate(required(phrase + " with Java"), profile(expectedYears - 1, 0)).status())
                .isEqualTo(Status.FAIL);
    }

    @Test
    @DisplayName("3-5 years 범위 표현은 하한인 3년으로 판정한다")
    void rangeUsesTheLowerBoundAsTheRequiredYears() {
        Finding finding = evaluate(required("3-5 years with Java"), profile(3, 0));

        assertThat(finding.status()).isEqualTo(Status.PASS);
        assertThat(finding.summary()).startsWith("3-5 years");
        assertThat(finding.evidence()).extracting(Clause::text)
                .containsExactly("3-5 years with Java");
    }

    @Test
    @DisplayName("프로필 2년과 tolerance 1에서 5년은 FAIL이고 3년은 WARN이며 2년은 PASS이다")
    void comparesRequiredYearsAgainstProfileAndTolerance() {
        assertThat(evaluate(required("5+ years with Java"), profile(2, 1)).status())
                .isEqualTo(Status.FAIL);
        assertThat(evaluate(required("3+ years with Java"), profile(2, 1)).status())
                .isEqualTo(Status.WARN);
        assertThat(evaluate(required("2 years with Java"), profile(2, 1)).status())
                .isEqualTo(Status.PASS);
    }

    @Test
    @DisplayName("NICE 절의 5년 요구 표현은 무시되어 PASS이다")
    void ignoresExperienceYearsInNiceClauses() {
        Finding finding = evaluate("Nice to have:\n5+ years with Java", profile(0, 0));

        assertThat(finding.status()).isEqualTo(Status.PASS);
        assertThat(finding.evidence()).isEmpty();
    }

    @Test
    @DisplayName("여러 연차 표현이 있으면 최댓값을 요구 연차로 사용한다")
    void usesTheMaximumExtractedExperienceRequirement() {
        Finding finding = evaluate("""
                Requirements:
                2 years with Java
                5+ years with Kotlin
                """, profile(3, 1));

        assertThat(finding.status()).isEqualTo(Status.FAIL);
        assertThat(finding.summary()).startsWith("5+ years");
        assertThat(finding.evidence()).extracting(Clause::text)
                .containsExactly("5+ years with Kotlin");
    }

    @Test
    @DisplayName("연차 언급이 없으면 PASS이다")
    void passesWhenNoExperienceYearsAreMentioned() {
        Finding finding = evaluate("Requirements:\nJava and Kotlin", profile(0, 0));

        assertThat(finding.status()).isEqualTo(Status.PASS);
        assertThat(finding.summary()).isEqualTo("not specified");
        assertThat(finding.evidence()).isEmpty();
    }

    @Test
    @DisplayName("tolerance를 초과한 AMBIGUOUS 절의 연차 요구는 REVIEW이다")
    void ambiguousExperienceRequirementOverToleranceNeedsReview() {
        Finding finding = evaluate("Requirements:\nIdeally 5+ years with Java", profile(2, 1));

        assertThat(finding.status()).isEqualTo(Status.REVIEW);
        assertThat(finding.evidence()).extracting(Clause::text)
                .containsExactly("Ideally 5+ years with Java");
    }

    private Finding evaluate(String jobText, Profile profile) {
        return ExperienceYearsRule.evaluate(SectionClassifier.classify(jobText), profile);
    }

    private String required(String clause) {
        return "Requirements:\n" + clause;
    }

    private Profile profile(int yearsExperience, int yearsTolerance) {
        return new Profile(
                yearsExperience,
                yearsTolerance,
                Seniority.LEAD,
                Set.of("english"),
                Degree.NONE,
                Set.of()
        );
    }
}

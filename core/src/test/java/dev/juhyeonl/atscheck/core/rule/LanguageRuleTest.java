package dev.juhyeonl.atscheck.core.rule;

import static org.assertj.core.api.Assertions.assertThat;

import dev.juhyeonl.atscheck.core.model.Clause;
import dev.juhyeonl.atscheck.core.model.Degree;
import dev.juhyeonl.atscheck.core.model.Finding;
import dev.juhyeonl.atscheck.core.model.Profile;
import dev.juhyeonl.atscheck.core.model.Seniority;
import dev.juhyeonl.atscheck.core.model.Status;
import dev.juhyeonl.atscheck.core.section.SectionClassifier;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class LanguageRuleTest {
    @Test
    @DisplayName("필수 섹션의 Fluent Finnish is required는 Finnish 미보유 프로필에서 FAIL이다")
    void fluentFinnishRequiredFailsWhenProfileDoesNotHaveFinnish() {
        Finding finding = evaluate("Requirements:\nFluent Finnish is required", profileWithLanguages("english"));

        assertThat(finding.status()).isEqualTo(Status.FAIL);
        assertThat(finding.summary()).isEqualTo("Finnish required");
    }

    @Test
    @DisplayName("필수 Finnish 요구는 Finnish 보유 프로필에서 PASS이다")
    void fluentFinnishRequiredPassesWhenProfileHasFinnish() {
        Finding finding = evaluate(
                "Requirements:\nFluent Finnish is required",
                profileWithLanguages("english", "finnish")
        );

        assertThat(finding.status()).isEqualTo(Status.PASS);
        assertThat(finding.summary()).isEqualTo("Finnish required (you have it)");
        assertThat(finding.evidence()).extracting(Clause::text)
                .containsExactly("Fluent Finnish is required");
    }

    @Test
    @DisplayName("Finnish is a plus는 PASS이다")
    void finnishIsAPlusPasses() {
        Finding finding = evaluate("Finnish is a plus", profileWithLanguages("english"));

        assertThat(finding.status()).isEqualTo(Status.PASS);
        assertThat(finding.summary()).isEqualTo("Finnish is a plus");
        assertThat(finding.evidence()).extracting(Clause::text)
                .containsExactly("Finnish is a plus");
    }

    @Test
    @DisplayName("Finnish is not required는 PASS이다")
    void finnishIsNotRequiredPasses() {
        Finding finding = evaluate("Finnish is not required", profileWithLanguages("english"));

        assertThat(finding.status()).isEqualTo(Status.PASS);
        assertThat(finding.summary()).isEqualTo("Finnish explicitly not required");
        assertThat(finding.evidence()).extracting(Clause::text)
                .containsExactly("Finnish is not required");
    }

    @Test
    @DisplayName("복지 섹션의 Finnish lessons는 PASS이며 evidence에 원문 절을 보존한다")
    void finnishLessonsInBenefitsPassesWithEvidence() {
        Finding finding = evaluate(
                "Benefits:\nOptional Finnish lessons at lunch, no homework and no test at the end.",
                profileWithLanguages("english")
        );

        assertThat(finding.status()).isEqualTo(Status.PASS);
        assertThat(finding.summary()).isEqualTo("Finnish mentioned, no requirement signal");
        assertThat(finding.evidence()).extracting(Clause::text)
                .containsExactly("Optional Finnish lessons at lunch, no homework and no test at the end.");
    }

    @Test
    @DisplayName("Working knowledge of Finnish는 REVIEW이다")
    void workingKnowledgeOfFinnishNeedsReview() {
        Finding finding = evaluate("Working knowledge of Finnish", profileWithLanguages("english"));

        assertThat(finding.status()).isEqualTo(Status.REVIEW);
        assertThat(finding.summary()).isEqualTo("Finnish - ambiguous requirement");
        assertThat(finding.evidence()).extracting(Clause::text)
                .containsExactly("Working knowledge of Finnish");
    }

    @Test
    @DisplayName("svenska 필수 요구는 Swedish 미보유 프로필에서 FAIL이고 보유 프로필에서 PASS이다")
    void svenskaRequiredUsesTheSameLanguageDecision() {
        String jobText = "Requirements:\nFluent svenska is required";

        assertThat(evaluate(jobText, profileWithLanguages("english")).status())
                .isEqualTo(Status.FAIL);
        assertThat(evaluate(jobText, profileWithLanguages("english", "swedish")).status())
                .isEqualTo(Status.PASS);
    }

    @Test
    @DisplayName("검사 대상 언어 언급이 전혀 없으면 PASS이다")
    void jobWithoutTargetLanguageMentionsPasses() {
        Finding finding = evaluate("Requirements:\nJava and Kotlin", profileWithLanguages("english"));

        assertThat(finding.status()).isEqualTo(Status.PASS);
        assertThat(finding.summary()).isEqualTo("English only");
        assertThat(finding.evidence()).isEmpty();
    }

    @Test
    @DisplayName("FAIL 판정은 evidence에 원문 절을 보존한다")
    void failFindingPreservesOriginalClauseEvidence() {
        Finding finding = evaluate("Requirements:\nFluent Finnish is required", profileWithLanguages("english"));

        assertThat(finding.status()).isEqualTo(Status.FAIL);
        assertThat(finding.evidence()).extracting(Clause::text)
                .containsExactly("Fluent Finnish is required");
    }

    private Finding evaluate(String jobText, Profile profile) {
        return LanguageRule.evaluate(SectionClassifier.classify(jobText), profile);
    }

    private Profile profileWithLanguages(String... languages) {
        return new Profile(
                0,
                1,
                Seniority.LEAD,
                Set.of(languages),
                Degree.NONE,
                Set.of()
        );
    }
}

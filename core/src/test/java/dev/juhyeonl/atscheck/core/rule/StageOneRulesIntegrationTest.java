package dev.juhyeonl.atscheck.core.rule;

import static org.assertj.core.api.Assertions.assertThat;

import dev.juhyeonl.atscheck.core.model.Clause;
import dev.juhyeonl.atscheck.core.model.Degree;
import dev.juhyeonl.atscheck.core.model.Profile;
import dev.juhyeonl.atscheck.core.model.Seniority;
import dev.juhyeonl.atscheck.core.model.Status;
import dev.juhyeonl.atscheck.core.section.SectionClassifier;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class StageOneRulesIntegrationTest {
    @Test
    @DisplayName("실제 공고 형태 텍스트에서 세 Stage 1 규칙이 기대 상태를 함께 낸다")
    void evaluatesAllStageOneRulesAgainstARealisticJobPosting() {
        String jobText = """
                Backend Engineer
                Acme Hiring Lab

                Requirements:
                Ideally 3+ years with Java.
                MSc in Computer Science or equivalent experience.
                Working knowledge of Finnish.

                Nice to have:
                PhD is a plus.

                Benefits:
                Finnish lessons.
                """;
        Profile profile = new Profile(
                2,
                1,
                Seniority.LEAD,
                Set.of("english"),
                Degree.BACHELOR,
                Set.of("java")
        );

        List<Clause> clauses = SectionClassifier.classify(jobText);

        assertThat(LanguageRule.evaluate(clauses, profile).status()).isEqualTo(Status.REVIEW);
        assertThat(ExperienceYearsRule.evaluate(clauses, profile).status()).isEqualTo(Status.WARN);
        assertThat(DegreeRule.evaluate(clauses, profile).status()).isEqualTo(Status.WARN);
    }
}

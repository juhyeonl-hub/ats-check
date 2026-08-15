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

class DegreeRuleTest {
    @Test
    @DisplayName("REQUIRED 절의 MSc는 bachelor 프로필에서 FAIL이다")
    void requiredMscFailsForBachelorProfile() {
        Finding finding = evaluate("Requirements:\nMSc in Computer Science", profile(Degree.BACHELOR));

        assertThat(finding.status()).isEqualTo(Status.FAIL);
        assertThat(finding.evidence()).extracting(Clause::text)
                .containsExactly("MSc in Computer Science");
    }

    @Test
    @DisplayName("REQUIRED 절의 MSc는 master 프로필에서 PASS이다")
    void requiredMscPassesForMasterProfile() {
        Finding finding = evaluate("Requirements:\nMSc in Computer Science", profile(Degree.MASTER));

        assertThat(finding.status()).isEqualTo(Status.PASS);
        assertThat(finding.evidence()).extracting(Clause::text)
                .containsExactly("MSc in Computer Science");
    }

    @Test
    @DisplayName("Bachelor's degree는 bachelor 프로필에서 PASS이다")
    void bachelorsDegreePassesForBachelorProfile() {
        Finding finding = evaluate("Requirements:\nBachelor's degree", profile(Degree.BACHELOR));

        assertThat(finding.status()).isEqualTo(Status.PASS);
    }

    @Test
    @DisplayName("master of your craft는 학위로 인식하지 않고 PASS이다")
    void masterOfYourCraftIsNotRecognizedAsADegree() {
        Finding finding = evaluate("Requirements:\nBe a master of your craft", profile(Degree.NONE));

        assertThat(finding.status()).isEqualTo(Status.PASS);
        assertThat(finding.summary()).isEqualTo("not required");
    }

    @Test
    @DisplayName("MSc or equivalent experience는 bachelor 프로필에서 WARN이다")
    void mscOrEquivalentExperienceWarnsInsteadOfFailing() {
        Finding finding = evaluate(
                "Requirements:\nMSc or equivalent experience",
                profile(Degree.BACHELOR)
        );

        assertThat(finding.status()).isEqualTo(Status.WARN);
        assertThat(finding.summary()).startsWith("Master or equivalent experience");
        assertThat(finding.evidence()).extracting(Clause::text)
                .containsExactly("MSc or equivalent experience");
    }

    @Test
    @DisplayName("PhD is a plus는 PASS이다")
    void phdIsAPlusPasses() {
        Finding finding = evaluate("PhD is a plus", profile(Degree.NONE));

        assertThat(finding.status()).isEqualTo(Status.PASS);
        assertThat(finding.evidence()).extracting(Clause::text)
                .containsExactly("PhD is a plus");
    }

    private Finding evaluate(String jobText, Profile profile) {
        return DegreeRule.evaluate(SectionClassifier.classify(jobText), profile);
    }

    private Profile profile(Degree degree) {
        return new Profile(
                0,
                1,
                Seniority.LEAD,
                Set.of("english"),
                degree,
                Set.of()
        );
    }
}

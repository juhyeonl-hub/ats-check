package dev.juhyeonl.atscheck.core.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ProfileTest {
    @Test
    @DisplayName("defaults는 명시된 기본 프로필 값을 제공한다")
    void defaultsProvidesTheSpecifiedProfileValues() {
        Profile profile = Profile.defaults();

        assertThat(profile.yearsExperience()).isZero();
        assertThat(profile.yearsTolerance()).isEqualTo(1);
        assertThat(profile.maxSeniority()).isEqualTo(Seniority.LEAD);
        assertThat(profile.languages()).containsExactly("english");
        assertThat(profile.degree()).isEqualTo(Degree.NONE);
        assertThat(profile.skills()).isEmpty();
    }

    @Test
    @DisplayName("Profile 컬렉션은 소문자로 정규화하고 불변 복사본으로 보존한다")
    void profileCollectionsAreNormalizedAndDefensivelyCopied() {
        Set<String> languages = new HashSet<>(Set.of(" English ", "KOREAN"));
        Set<String> skills = new HashSet<>(Set.of(" Java ", "SPRING"));

        Profile profile = new Profile(3, 1, Seniority.SENIOR, languages, Degree.BACHELOR, skills);
        languages.add("finnish");
        skills.add("kotlin");

        assertThat(profile.languages()).containsExactlyInAnyOrder("english", "korean");
        assertThat(profile.skills()).containsExactlyInAnyOrder("java", "spring");
        assertThatExceptionOfType(UnsupportedOperationException.class)
                .isThrownBy(() -> profile.languages().add("finnish"));
        assertThatExceptionOfType(UnsupportedOperationException.class)
                .isThrownBy(() -> profile.skills().add("kotlin"));
    }
}

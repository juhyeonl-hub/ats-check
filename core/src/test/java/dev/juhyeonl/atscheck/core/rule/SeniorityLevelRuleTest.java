package dev.juhyeonl.atscheck.core.rule;

import static org.assertj.core.api.Assertions.assertThat;

import dev.juhyeonl.atscheck.core.model.Degree;
import dev.juhyeonl.atscheck.core.model.Finding;
import dev.juhyeonl.atscheck.core.model.JobPosting;
import dev.juhyeonl.atscheck.core.model.Profile;
import dev.juhyeonl.atscheck.core.model.Seniority;
import dev.juhyeonl.atscheck.core.model.Status;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SeniorityLevelRuleTest {
    @Test
    @DisplayName("Senior title은 MID 최대 프로필에서 WARN이다")
    void seniorTitleWarnsWhenProfileMaxIsMid() {
        Finding finding = evaluate("Senior Backend Engineer", Seniority.MID);

        assertThat(finding.status()).isEqualTo(Status.WARN);
        assertThat(finding.summary()).isEqualTo("Senior Backend Engineer (profile max: mid)");
    }

    @Test
    @DisplayName("마커 없는 title은 MID로 보고 MID 최대 프로필에서 PASS이다")
    void titleWithoutMarkerIsMidAndPassesForMidProfile() {
        Finding finding = evaluate("Backend Engineer", Seniority.MID);

        assertThat(finding.status()).isEqualTo(Status.PASS);
        assertThat(finding.summary()).isEqualTo("no seniority marker");
    }

    @Test
    @DisplayName("Junior title은 MID 최대 프로필에서 PASS이다")
    void juniorTitlePassesForMidProfile() {
        Finding finding = evaluate("Junior Developer", Seniority.MID);

        assertThat(finding.status()).isEqualTo(Status.PASS);
    }

    @Test
    @DisplayName("여러 마커가 있으면 가장 높은 LEAD를 사용한다")
    void usesTheHighestSeniorityMarker() {
        Finding finding = evaluate("Senior Lead Engineer", Seniority.SENIOR);

        assertThat(finding.status()).isEqualTo(Status.WARN);
    }

    @Test
    @DisplayName("본문의 senior 표현은 시니어리티 추출에 쓰지 않는다")
    void ignoresSeniorityMarkersInBody() {
        JobPosting posting = new JobPosting(
                "Backend Engineer",
                "Backend Engineer\nYou will work with senior engineers."
        );

        Finding finding = SeniorityLevelRule.evaluate(posting, profile(Seniority.MID));

        assertThat(finding.status()).isEqualTo(Status.PASS);
        assertThat(finding.summary()).isEqualTo("no seniority marker");
    }

    @Test
    @DisplayName("Head of Engineering은 LEAD로 인식한다")
    void headOfEngineeringIsLead() {
        Finding finding = evaluate("Head of Engineering", Seniority.SENIOR);

        assertThat(finding.status()).isEqualTo(Status.WARN);
    }

    private Finding evaluate(String title, Seniority maxSeniority) {
        return SeniorityLevelRule.evaluate(new JobPosting(title, title), profile(maxSeniority));
    }

    private Profile profile(Seniority maxSeniority) {
        return new Profile(
                0,
                1,
                maxSeniority,
                Set.of("english"),
                Degree.NONE,
                Set.of()
        );
    }
}

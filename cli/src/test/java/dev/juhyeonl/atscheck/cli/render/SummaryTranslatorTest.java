package dev.juhyeonl.atscheck.cli.render;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SummaryTranslatorTest {
    @Test
    void translatesFixedSummaries() {
        assertThat(translate("English only")).isEqualTo("영어만 요구");
        assertThat(translate("Finnish required")).isEqualTo("핀란드어 필수");
        assertThat(translate("Swedish required")).isEqualTo("스웨덴어 필수");
        assertThat(translate("Finnish required (you have it)")).isEqualTo("핀란드어 필수 (보유함)");
        assertThat(translate("not specified")).isEqualTo("명시 없음");
        assertThat(translate("not required")).isEqualTo("요구 없음");
        assertThat(translate("no seniority marker")).isEqualTo("레벨 표시 없음");
        assertThat(translate("full match")).isEqualTo("모두 충족");
    }

    @Test
    void translatesPatternSummaries() {
        assertThat(translate("3+ years (profile: 2, tolerance: 1)"))
                .isEqualTo("3년 이상 (내 경력: 2, 허용: 1)");
        assertThat(translate("At least 7 years (profile: 2, tolerance: 1)"))
                .isEqualTo("최소 7년 (내 경력: 2, 허용: 1)");
        assertThat(translate("Backend Engineer (no seniority marker)"))
                .isEqualTo("Backend Engineer (레벨 표시 없음)");
        assertThat(translate("Senior Backend Engineer (profile max: mid)"))
                .isEqualTo("Senior Backend Engineer (내 상한: mid)");
        assertThat(translate("Master required (profile: NONE)"))
                .isEqualTo("석사 필수 (내 학위: 없음)");
        assertThat(translate("Bachelor or equivalent experience (profile: NONE)"))
                .isEqualTo("학사 또는 동등 경력 (내 학위: 없음)");
        assertThat(translate("missing 2 required, 1 nice"))
                .isEqualTo("필수 2개, 우대 1개 부족");
    }

    @Test
    void leavesEnglishAndUnknownSummariesUnchanged() {
        assertThat(SummaryTranslator.translate(
                "3+ years (profile: 2, tolerance: 1)",
                TerminalLanguage.EN
        )).isEqualTo("3+ years (profile: 2, tolerance: 1)");
        assertThat(translate("custom summary")).isEqualTo("custom summary");
    }

    private static String translate(String summary) {
        return SummaryTranslator.translate(summary, TerminalLanguage.KO);
    }
}

package dev.juhyeonl.atscheck.cli.render;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class DisplayWidthTest {
    @Test
    void countsWideCharactersAsTwoColumns() {
        assertThat(DisplayWidth.width("언어")).isEqualTo(4);
        assertThat(DisplayWidth.width("Language")).isEqualTo(8);
        assertThat(DisplayWidth.width("Backend")).isEqualTo(7);
        assertThat(DisplayWidth.width("Backend 언어")).isEqualTo(12);
        assertThat(DisplayWidth.width("日Aあ")).isEqualTo(5);
    }

    @Test
    void truncatesWithoutExceedingDisplayWidth() {
        String truncated = DisplayWidth.truncate("회사Backend", 7);

        assertThat(truncated).endsWith("…");
        assertThat(DisplayWidth.width(truncated)).isLessThanOrEqualTo(7);
    }
}

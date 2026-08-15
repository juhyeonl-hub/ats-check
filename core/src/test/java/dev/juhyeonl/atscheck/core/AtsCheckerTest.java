package dev.juhyeonl.atscheck.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import org.junit.jupiter.api.Test;

class AtsCheckerTest {
    @Test
    void echoReturnsTrimmedJobText() {
        String jobText = "  Backend Engineer\nJava and Spring experience required.  \n";

        String result = AtsChecker.echo(jobText);

        assertThat(result).isEqualTo("Backend Engineer\nJava and Spring experience required.");
    }

    @Test
    void echoRejectsNullInput() {
        assertThatNullPointerException()
                .isThrownBy(() -> AtsChecker.echo(null))
                .withMessage("jobText");
    }
}

package dev.juhyeonl.atscheck.core;

import java.util.Objects;

public final class AtsChecker {
    private AtsChecker() {
    }

    public static String echo(String jobText) {
        return Objects.requireNonNull(jobText, "jobText").strip();
    }
}

package dev.juhyeonl.atscheck.core.model;

import java.util.List;
import java.util.Objects;

public record Clause(
        String text,
        int lineNumber,
        RequirementLevel level,
        SectionKind section,
        List<Signal> signals
) {
    public Clause {
        Objects.requireNonNull(text, "text");
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(section, "section");
        signals = List.copyOf(Objects.requireNonNull(signals, "signals"));

        if (lineNumber < 1) {
            throw new IllegalArgumentException("lineNumber must be positive");
        }
    }
}

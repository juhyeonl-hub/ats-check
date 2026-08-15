package dev.juhyeonl.atscheck.core.model;

import java.util.List;
import java.util.Objects;

public record Finding(
        RuleId rule,
        Status status,
        String summary,
        List<Clause> evidence
) {
    public Finding {
        Objects.requireNonNull(rule, "rule");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(summary, "summary");
        evidence = List.copyOf(Objects.requireNonNull(evidence, "evidence"));
    }
}

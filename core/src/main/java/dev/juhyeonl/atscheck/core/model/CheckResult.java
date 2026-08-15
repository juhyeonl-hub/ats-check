package dev.juhyeonl.atscheck.core.model;

import java.util.List;
import java.util.Objects;

public record CheckResult(
        Verdict verdict,
        List<Finding> findings,
        SkillGap skillGap,
        boolean stoppedAtHardFilter
) {
    public CheckResult {
        Objects.requireNonNull(verdict, "verdict");
        findings = List.copyOf(Objects.requireNonNull(findings, "findings"));
    }
}

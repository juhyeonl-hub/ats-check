package dev.juhyeonl.atscheck.cli.render;

import dev.juhyeonl.atscheck.core.model.CheckResult;
import dev.juhyeonl.atscheck.core.model.Clause;
import dev.juhyeonl.atscheck.core.model.Finding;
import dev.juhyeonl.atscheck.core.model.RuleId;
import dev.juhyeonl.atscheck.core.model.SkillGap;
import dev.juhyeonl.atscheck.core.model.Status;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

public final class TerminalRenderer {
    private static final String INDENT = "  ";
    private static final int LABEL_WIDTH = 12;
    private static final int SKILL_LABEL_WIDTH = 21;
    private static final String EVIDENCE_INDENT = " ".repeat(INDENT.length() + 1 + 1 + LABEL_WIDTH);
    private static final List<RuleId> DISPLAY_ORDER = List.of(
            RuleId.LANGUAGE,
            RuleId.SENIORITY_LEVEL,
            RuleId.EXPERIENCE_YEARS,
            RuleId.DEGREE
    );
    private static final Map<String, String> SKILL_DISPLAY_NAMES = Map.ofEntries(
            Map.entry("java", "Java"),
            Map.entry("kotlin", "Kotlin"),
            Map.entry("spring boot", "Spring Boot"),
            Map.entry("postgresql", "PostgreSQL"),
            Map.entry("rest", "REST"),
            Map.entry("docker", "Docker"),
            Map.entry("kubernetes", "Kubernetes"),
            Map.entry("terraform", "Terraform"),
            Map.entry("typescript", "TypeScript"),
            Map.entry("javascript", "JavaScript"),
            Map.entry("node.js", "Node.js"),
            Map.entry("c++", "C++"),
            Map.entry("c#", "C#"),
            Map.entry(".net", ".NET"),
            Map.entry("aws", "AWS"),
            Map.entry("gcp", "GCP"),
            Map.entry("sql", "SQL"),
            Map.entry("graphql", "GraphQL"),
            Map.entry("grpc", "gRPC"),
            Map.entry("ci/cd", "CI/CD"),
            Map.entry("html", "HTML"),
            Map.entry("css", "CSS"),
            Map.entry("api", "API"),
            Map.entry("mysql", "MySQL"),
            Map.entry("mongodb", "MongoDB"),
            Map.entry("redis", "Redis"),
            Map.entry("kafka", "Kafka"),
            Map.entry("python", "Python"),
            Map.entry("go", "Go"),
            Map.entry("rust", "Rust"),
            Map.entry("scala", "Scala"),
            Map.entry("swift", "Swift")
    );

    private TerminalRenderer() {
    }

    public static String render(CheckResult result) {
        StringBuilder output = new StringBuilder();
        output.append("VERDICT: ").append(result.verdict()).append("\n\n");

        for (RuleId rule : DISPLAY_ORDER) {
            findingFor(result, rule).ifPresent(finding -> appendFinding(output, finding));
        }

        appendSkillGap(output, result.skillGap());
        if (result.stoppedAtHardFilter()) {
            output.append("\n  Analysis stopped at hard filter.\n");
        }

        return output.toString();
    }

    private static Optional<Finding> findingFor(CheckResult result, RuleId rule) {
        return result.findings().stream()
                .filter(finding -> finding.rule() == rule)
                .findFirst();
    }

    private static void appendFinding(StringBuilder output, Finding finding) {
        output.append(INDENT)
                .append(symbolFor(finding.status()))
                .append(" ")
                .append(paddedLabel(labelFor(finding.rule())))
                .append(summaryForTerminal(finding))
                .append("\n");
        appendEvidence(output, finding);
    }

    private static String paddedLabel(String label) {
        return String.format("%-" + LABEL_WIDTH + "s", label);
    }

    private static String summaryForTerminal(Finding finding) {
        String summary = finding.summary();
        if (finding.rule() == RuleId.EXPERIENCE_YEARS && finding.status() == Status.WARN) {
            return summary + " — borderline";
        }
        return summary;
    }

    private static String labelFor(RuleId rule) {
        return switch (rule) {
            case LANGUAGE -> "Language";
            case EXPERIENCE_YEARS -> "Seniority";
            case DEGREE -> "Degree";
            case SENIORITY_LEVEL -> "Level";
            case SKILLS -> "";
        };
    }

    private static String symbolFor(Status status) {
        return switch (status) {
            case PASS -> "\u2713";
            case WARN -> "\u26a0";
            case REVIEW -> "?";
            case FAIL -> "\u2717";
        };
    }

    private static void appendEvidence(StringBuilder output, Finding finding) {
        if (finding.status() == Status.PASS || finding.evidence().isEmpty()) {
            return;
        }

        for (Clause clause : finding.evidence()) {
            output.append(EVIDENCE_INDENT)
                    .append("\"")
                    .append(escapeTerminalQuote(clause.text()))
                    .append("\"")
                    .append("\n");
        }
    }

    private static String escapeTerminalQuote(String text) {
        return text.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static void appendSkillGap(StringBuilder output, SkillGap skillGap) {
        if (skillGap == null) {
            return;
        }

        StringBuilder block = new StringBuilder();
        appendSkillLine(block, "MISSING (required)", skillGap.missingRequired());
        appendSkillLine(block, "MISSING (nice)", skillGap.missingNice());
        appendSkillLine(block, "MATCHED", skillGap.matched());

        if (!block.isEmpty()) {
            output.append("\n").append(block);
        }
    }

    private static void appendSkillLine(StringBuilder output, String label, Set<String> skills) {
        if (skills.isEmpty()) {
            return;
        }

        output.append("  ")
                .append(String.format("%-" + SKILL_LABEL_WIDTH + "s", label))
                .append(join(skills))
                .append("\n");
    }

    private static String join(Set<String> skills) {
        return skills.stream()
                .map(TerminalRenderer::displayNameFor)
                .collect(Collectors.joining(", "));
    }

    private static String displayNameFor(String skill) {
        return SKILL_DISPLAY_NAMES.getOrDefault(skill, skill);
    }
}

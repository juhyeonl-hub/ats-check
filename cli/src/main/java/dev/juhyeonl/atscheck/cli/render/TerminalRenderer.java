package dev.juhyeonl.atscheck.cli.render;

import dev.juhyeonl.atscheck.core.model.CheckResult;
import dev.juhyeonl.atscheck.core.model.Clause;
import dev.juhyeonl.atscheck.core.model.Finding;
import dev.juhyeonl.atscheck.core.model.RuleId;
import dev.juhyeonl.atscheck.core.model.SkillGap;
import dev.juhyeonl.atscheck.core.model.Status;
import java.util.Set;
import java.util.stream.Collectors;

public final class TerminalRenderer {
    private static final int LABEL_WIDTH = 12;
    private static final String EVIDENCE_INDENT = "                ";

    private TerminalRenderer() {
    }

    public static String render(CheckResult result) {
        StringBuilder output = new StringBuilder();
        output.append("VERDICT: ").append(result.verdict()).append("\n\n");

        for (Finding finding : result.findings()) {
            if (finding.rule() == RuleId.SKILLS) {
                continue;
            }
            output.append("  ")
                    .append(paddedLabel(finding.status(), labelFor(finding.rule())))
                    .append(finding.summary())
                    .append("\n");
            appendEvidence(output, finding);
        }

        appendSkillGap(output, result.skillGap());
        if (result.stoppedAtHardFilter()) {
            output.append("\n  Analysis stopped at hard filter.\n");
        }

        return output.toString();
    }

    private static String paddedLabel(Status status, String label) {
        return String.format("%-" + LABEL_WIDTH + "s", symbolFor(status) + " " + label);
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
                .append(String.format("%-20s", label))
                .append(join(skills))
                .append("\n");
    }

    private static String join(Set<String> skills) {
        return skills.stream().collect(Collectors.joining(", "));
    }
}

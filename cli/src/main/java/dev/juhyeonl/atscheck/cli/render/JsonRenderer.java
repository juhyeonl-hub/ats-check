package dev.juhyeonl.atscheck.cli.render;

import dev.juhyeonl.atscheck.core.model.CheckResult;
import dev.juhyeonl.atscheck.core.model.Clause;
import dev.juhyeonl.atscheck.core.model.Finding;
import dev.juhyeonl.atscheck.core.model.SkillGap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

public final class JsonRenderer {
    private JsonRenderer() {
    }

    public static String render(CheckResult result) {
        StringBuilder output = new StringBuilder();
        output.append("{\n");
        output.append("  \"verdict\": ");
        appendString(output, result.verdict().name());
        output.append(",\n");
        output.append("  \"stoppedAtHardFilter\": ").append(result.stoppedAtHardFilter()).append(",\n");
        output.append("  \"findings\": [\n");
        appendFindings(output, result, 4);
        output.append("  ],\n");
        output.append("  \"skillGap\": ");
        appendSkillGap(output, result.skillGap(), 2);
        output.append("\n");
        output.append("}\n");
        return output.toString();
    }

    static void appendFindings(StringBuilder output, CheckResult result, int indent) {
        for (int index = 0; index < result.findings().size(); index++) {
            Finding finding = result.findings().get(index);
            output.append(" ".repeat(indent)).append("{\n");
            output.append(" ".repeat(indent + 2)).append("\"rule\": ");
            appendString(output, finding.rule().name());
            output.append(",\n");
            output.append(" ".repeat(indent + 2)).append("\"status\": ");
            appendString(output, finding.status().name());
            output.append(",\n");
            output.append(" ".repeat(indent + 2)).append("\"summary\": ");
            appendString(output, finding.summary());
            output.append(",\n");
            output.append(" ".repeat(indent + 2)).append("\"evidence\": ");
            appendEvidence(output, finding);
            output.append("\n");
            output.append(" ".repeat(indent)).append("}");
            if (index < result.findings().size() - 1) {
                output.append(",");
            }
            output.append("\n");
        }
    }

    static void appendEvidence(StringBuilder output, Finding finding) {
        output.append("[");
        for (int index = 0; index < finding.evidence().size(); index++) {
            Clause clause = finding.evidence().get(index);
            if (index > 0) {
                output.append(", ");
            }
            appendString(output, clause.text());
        }
        output.append("]");
    }

    static void appendSkillGap(StringBuilder output, SkillGap skillGap, int indent) {
        if (skillGap == null) {
            output.append("null");
            return;
        }

        output.append("{\n");
        output.append(" ".repeat(indent + 2)).append("\"matched\": ");
        appendStringArray(output, skillGap.matched());
        output.append(",\n");
        output.append(" ".repeat(indent + 2)).append("\"missingRequired\": ");
        appendStringArray(output, skillGap.missingRequired());
        output.append(",\n");
        output.append(" ".repeat(indent + 2)).append("\"missingNice\": ");
        appendStringArray(output, skillGap.missingNice());
        output.append("\n");
        output.append(" ".repeat(indent)).append("}");
    }

    static void appendStringArray(StringBuilder output, Set<String> values) {
        output.append("[");
        Iterator<String> iterator = values.iterator();
        while (iterator.hasNext()) {
            appendString(output, iterator.next());
            if (iterator.hasNext()) {
                output.append(", ");
            }
        }
        output.append("]");
    }

    static void appendNullableString(StringBuilder output, String value) {
        if (value == null || value.isBlank()) {
            output.append("null");
            return;
        }
        appendString(output, value);
    }

    static void appendJsonValue(StringBuilder output, Object value) {
        if (value == null) {
            output.append("null");
        } else if (value instanceof String text) {
            appendString(output, text);
        } else if (value instanceof Number || value instanceof Boolean) {
            output.append(value);
        } else if (value instanceof Map<?, ?> map) {
            appendMap(output, map);
        } else if (value instanceof Iterable<?> iterable) {
            appendIterable(output, iterable);
        } else {
            appendString(output, value.toString());
        }
    }

    static void appendString(StringBuilder output, String value) {
        output.append("\"");
        for (int index = 0; index < value.length(); index++) {
            char ch = value.charAt(index);
            switch (ch) {
                case '"' -> output.append("\\\"");
                case '\\' -> output.append("\\\\");
                case '\b' -> output.append("\\b");
                case '\f' -> output.append("\\f");
                case '\n' -> output.append("\\n");
                case '\r' -> output.append("\\r");
                case '\t' -> output.append("\\t");
                default -> {
                    if (ch < 0x20) {
                        output.append(String.format("\\u%04x", (int) ch));
                    } else {
                        output.append(ch);
                    }
                }
            }
        }
        output.append("\"");
    }

    private static void appendMap(StringBuilder output, Map<?, ?> map) {
        output.append("{");
        Iterator<? extends Map.Entry<?, ?>> iterator = map.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<?, ?> entry = iterator.next();
            appendString(output, entry.getKey().toString());
            output.append(": ");
            appendJsonValue(output, entry.getValue());
            if (iterator.hasNext()) {
                output.append(", ");
            }
        }
        output.append("}");
    }

    private static void appendIterable(StringBuilder output, Iterable<?> values) {
        output.append("[");
        Iterator<?> iterator = values.iterator();
        while (iterator.hasNext()) {
            appendJsonValue(output, iterator.next());
            if (iterator.hasNext()) {
                output.append(", ");
            }
        }
        output.append("]");
    }
}

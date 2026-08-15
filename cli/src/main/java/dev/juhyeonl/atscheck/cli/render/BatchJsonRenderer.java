package dev.juhyeonl.atscheck.cli.render;

import dev.juhyeonl.atscheck.cli.batch.BatchCheckResult;
import dev.juhyeonl.atscheck.cli.batch.BatchJobResult;
import dev.juhyeonl.atscheck.cli.store.FrontMatter;
import java.util.LinkedHashMap;
import java.util.Map;

public final class BatchJsonRenderer {
    private BatchJsonRenderer() {
    }

    public static String render(BatchCheckResult batch) {
        StringBuilder output = new StringBuilder();
        output.append("{\n");
        output.append("  \"jobs\": [\n");
        appendJobs(output, batch);
        output.append("  ],\n");
        output.append("  \"summary\": ");
        appendSummary(output, batch);
        output.append("\n");
        output.append("}\n");
        return output.toString();
    }

    private static void appendJobs(StringBuilder output, BatchCheckResult batch) {
        for (int index = 0; index < batch.jobs().size(); index++) {
            appendJob(output, batch.jobs().get(index));
            if (index < batch.jobs().size() - 1) {
                output.append(",");
            }
            output.append("\n");
        }
    }

    private static void appendJob(StringBuilder output, BatchJobResult job) {
        FrontMatter frontMatter = job.frontMatter();
        output.append("    {\n");
        output.append("      \"file\": ");
        JsonRenderer.appendString(output, job.fileName());
        output.append(",\n");
        output.append("      \"company\": ");
        JsonRenderer.appendNullableString(output, frontMatter.company());
        output.append(",\n");
        output.append("      \"title\": ");
        JsonRenderer.appendNullableString(output, frontMatter.title());
        output.append(",\n");
        output.append("      \"url\": ");
        JsonRenderer.appendNullableString(output, frontMatter.url());
        output.append(",\n");
        output.append("      \"status\": ");
        JsonRenderer.appendNullableString(output, frontMatter.status());
        output.append(",\n");
        output.append("      \"metadata\": ");
        JsonRenderer.appendJsonValue(output, metadataFor(frontMatter));
        output.append(",\n");
        output.append("      \"verdict\": ");
        JsonRenderer.appendString(output, job.result().verdict().name());
        output.append(",\n");
        output.append("      \"stoppedAtHardFilter\": ").append(job.result().stoppedAtHardFilter()).append(",\n");
        output.append("      \"findings\": [\n");
        JsonRenderer.appendFindings(output, job.result(), 8);
        output.append("      ],\n");
        output.append("      \"skillGap\": ");
        JsonRenderer.appendSkillGap(output, job.result().skillGap(), 6);
        output.append("\n");
        output.append("    }");
    }

    private static Map<String, Object> metadataFor(FrontMatter frontMatter) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("url", blankToNull(frontMatter.url()));
        metadata.put("company", blankToNull(frontMatter.company()));
        metadata.put("title", blankToNull(frontMatter.title()));
        metadata.put("saved_at", blankToNull(frontMatter.savedAt()));
        metadata.put("status", blankToNull(frontMatter.status()));
        metadata.putAll(frontMatter.extra());
        return metadata;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private static void appendSummary(StringBuilder output, BatchCheckResult batch) {
        output.append("{ ");
        output.append("\"total\": ").append(batch.total()).append(", ");
        output.append("\"apply\": ").append(batch.applyCount()).append(", ");
        output.append("\"review\": ").append(batch.reviewCount()).append(", ");
        output.append("\"skip\": ").append(batch.skipCount());
        output.append(" }");
    }
}

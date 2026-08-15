package dev.juhyeonl.atscheck.cli.store;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

public final class JobFileWriter {
    private static final Pattern PLAIN_SCALAR = Pattern.compile("[A-Za-z0-9][A-Za-z0-9_./+-]*");

    public void write(Path path, JobFile jobFile) throws IOException {
        Objects.requireNonNull(path, "path");
        Files.writeString(path, render(jobFile), StandardCharsets.UTF_8);
    }

    public String render(JobFile jobFile) {
        Objects.requireNonNull(jobFile, "jobFile");

        StringBuilder builder = new StringBuilder();
        FrontMatter frontMatter = jobFile.frontMatter();
        builder.append("---\n");
        appendKnown(builder, "url", frontMatter.url(), false);
        appendKnown(builder, "company", frontMatter.company(), true);
        appendKnown(builder, "title", frontMatter.title(), true);
        appendKnown(builder, "saved_at", frontMatter.savedAt(), true);
        appendKnown(builder, "status", frontMatter.status(), true);
        for (Map.Entry<String, Object> entry : frontMatter.extra().entrySet()) {
            builder.append(yamlKey(entry.getKey()))
                    .append(": ")
                    .append(yamlValue(entry.getValue()))
                    .append('\n');
        }
        builder.append("---\n\n");
        builder.append(jobFile.body());
        return builder.toString();
    }

    private void appendKnown(StringBuilder builder, String key, String value, boolean includeBlank) {
        if (!includeBlank && value.isBlank()) {
            return;
        }
        builder.append(key).append(": ").append(yamlValue(value)).append('\n');
    }

    private String yamlKey(String key) {
        if (key.matches("[A-Za-z0-9_-]+")) {
            return key;
        }
        return doubleQuoted(key);
    }

    private String yamlValue(Object value) {
        if (value == null) {
            return "null";
        }
        if (value instanceof String text) {
            return yamlString(text);
        }
        if (value instanceof Number || value instanceof Boolean) {
            return value.toString();
        }
        if (value instanceof Iterable<?> iterable) {
            StringBuilder builder = new StringBuilder("[");
            Iterator<?> iterator = iterable.iterator();
            while (iterator.hasNext()) {
                builder.append(yamlValue(iterator.next()));
                if (iterator.hasNext()) {
                    builder.append(", ");
                }
            }
            return builder.append(']').toString();
        }
        if (value instanceof Map<?, ?> map) {
            StringBuilder builder = new StringBuilder("{");
            Iterator<? extends Map.Entry<?, ?>> iterator = map.entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry<?, ?> entry = iterator.next();
                builder.append(yamlKey(String.valueOf(entry.getKey())))
                        .append(": ")
                        .append(yamlValue(entry.getValue()));
                if (iterator.hasNext()) {
                    builder.append(", ");
                }
            }
            return builder.append('}').toString();
        }
        return yamlString(value.toString());
    }

    private String yamlString(String value) {
        if (isPlainScalar(value)) {
            return value;
        }
        return doubleQuoted(value);
    }

    private boolean isPlainScalar(String value) {
        String lower = value.toLowerCase();
        return PLAIN_SCALAR.matcher(value).matches()
                && !lower.equals("true")
                && !lower.equals("false")
                && !lower.equals("null")
                && !lower.equals("yes")
                && !lower.equals("no")
                && !value.contains(":")
                && value.equals(value.strip());
    }

    private String doubleQuoted(String value) {
        StringBuilder builder = new StringBuilder("\"");
        for (int index = 0; index < value.length(); index++) {
            char current = value.charAt(index);
            switch (current) {
                case '\\' -> builder.append("\\\\");
                case '"' -> builder.append("\\\"");
                case '\n' -> builder.append("\\n");
                case '\r' -> builder.append("\\r");
                case '\t' -> builder.append("\\t");
                default -> builder.append(current);
            }
        }
        return builder.append('"').toString();
    }
}

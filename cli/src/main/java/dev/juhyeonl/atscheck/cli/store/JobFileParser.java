package dev.juhyeonl.atscheck.cli.store;

import java.io.StringReader;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import org.yaml.snakeyaml.error.YAMLException;

public final class JobFileParser {
    private static final String DELIMITER = "---";

    public JobFile parse(String text) throws JobFileParseException {
        Objects.requireNonNull(text, "text");

        if (!startsWithOpeningDelimiter(text)) {
            return new JobFile(FrontMatter.empty(), text);
        }

        int yamlStart = firstLineEnd(text, 0);
        ClosingDelimiter closingDelimiter = findClosingDelimiter(text, yamlStart);
        if (closingDelimiter == null) {
            return new JobFile(FrontMatter.empty(), text);
        }

        String yamlText = text.substring(yamlStart, closingDelimiter.start());
        String body = stripOneBlankSeparator(text.substring(closingDelimiter.afterLineEnd()));
        return new JobFile(frontMatterFrom(yamlText), body);
    }

    private boolean startsWithOpeningDelimiter(String text) {
        if (!text.startsWith(DELIMITER)) {
            return false;
        }
        if (text.length() == DELIMITER.length()) {
            return false;
        }
        char next = text.charAt(DELIMITER.length());
        return next == '\n' || next == '\r';
    }

    private int firstLineEnd(String text, int start) {
        int lineContentEnd = lineContentEnd(text, start);
        return afterLineEnd(text, lineContentEnd);
    }

    private ClosingDelimiter findClosingDelimiter(String text, int start) {
        int lineStart = start;
        while (lineStart < text.length()) {
            int lineContentEnd = lineContentEnd(text, lineStart);
            if (text.substring(lineStart, lineContentEnd).equals(DELIMITER)) {
                return new ClosingDelimiter(lineStart, afterLineEnd(text, lineContentEnd));
            }
            int nextLineStart = afterLineEnd(text, lineContentEnd);
            if (nextLineStart == lineContentEnd) {
                break;
            }
            lineStart = nextLineStart;
        }
        return null;
    }

    private int lineContentEnd(String text, int start) {
        int newline = text.indexOf('\n', start);
        int carriageReturn = text.indexOf('\r', start);
        if (newline == -1 && carriageReturn == -1) {
            return text.length();
        }
        if (newline == -1) {
            return carriageReturn;
        }
        if (carriageReturn == -1) {
            return newline;
        }
        return Math.min(newline, carriageReturn);
    }

    private int afterLineEnd(String text, int lineContentEnd) {
        if (lineContentEnd >= text.length()) {
            return lineContentEnd;
        }
        if (text.charAt(lineContentEnd) == '\r'
                && lineContentEnd + 1 < text.length()
                && text.charAt(lineContentEnd + 1) == '\n') {
            return lineContentEnd + 2;
        }
        return lineContentEnd + 1;
    }

    private String stripOneBlankSeparator(String body) {
        if (body.startsWith("\r\n")) {
            return body.substring(2);
        }
        if (body.startsWith("\n")) {
            return body.substring(1);
        }
        return body;
    }

    @SuppressWarnings("unchecked")
    private FrontMatter frontMatterFrom(String yamlText) throws JobFileParseException {
        Object loaded;
        try {
            loaded = new Yaml(new SafeConstructor(new LoaderOptions())).load(new StringReader(yamlText));
        } catch (YAMLException exception) {
            throw new JobFileParseException("invalid job front matter: " + exception.getMessage(), exception);
        }

        if (loaded == null) {
            return FrontMatter.empty();
        }
        if (!(loaded instanceof Map<?, ?> rawMap)) {
            throw new JobFileParseException("invalid job front matter: root must be a map");
        }

        Map<String, Object> values = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : rawMap.entrySet()) {
            if (entry.getKey() instanceof String key) {
                values.put(key, entry.getValue());
            }
        }

        Map<String, Object> extra = new LinkedHashMap<>(values);
        String url = stringValue(extra.remove("url"));
        String company = stringValue(extra.remove("company"));
        String title = stringValue(extra.remove("title"));
        String savedAt = stringValue(extra.remove("saved_at"));
        String status = values.containsKey("status")
                ? stringValue(extra.remove("status"))
                : FrontMatter.DEFAULT_STATUS;

        return new FrontMatter(url, company, title, savedAt, status, extra);
    }

    private String stringValue(Object value) {
        return value == null ? "" : value.toString();
    }

    private record ClosingDelimiter(int start, int afterLineEnd) {
    }

    public static final class JobFileParseException extends Exception {
        public JobFileParseException(String message) {
            super(message);
        }

        public JobFileParseException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}

package dev.juhyeonl.atscheck.core.section;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

final class ClauseSplitter {
    private static final Pattern LEADING_BULLET = Pattern.compile(
            "^\\s*(?:[-*\\u2022\\u00b7\\u2013]|\\d+\\.)\\s+"
    );
    private static final List<String> ABBREVIATIONS = List.of(
            "e.g.",
            "i.e.",
            "etc.",
            "vs.",
            "inc.",
            "ltd.",
            "sr.",
            "jr."
    );

    public List<SplitClause> split(String jobText) {
        Objects.requireNonNull(jobText, "jobText");

        List<SplitClause> clauses = new ArrayList<>();
        String[] lines = jobText.split("\\R", -1);
        for (int i = 0; i < lines.length; i++) {
            clauses.addAll(splitLine(lines[i], i + 1));
        }
        return List.copyOf(clauses);
    }

    List<SplitClause> splitLine(String line, int lineNumber) {
        Objects.requireNonNull(line, "line");
        if (lineNumber < 1) {
            throw new IllegalArgumentException("lineNumber must be positive");
        }

        String withoutBullet = stripLeadingBullet(line).strip();
        if (withoutBullet.isEmpty()) {
            return List.of();
        }

        List<SplitClause> clauses = new ArrayList<>();
        for (String sentence : splitSentences(withoutBullet)) {
            String text = sentence.strip();
            if (!text.isEmpty()) {
                clauses.add(new SplitClause(text, lineNumber));
            }
        }
        return List.copyOf(clauses);
    }

    static String stripLeadingBullet(String line) {
        return LEADING_BULLET.matcher(line).replaceFirst("");
    }

    private List<String> splitSentences(String text) {
        List<String> sentences = new ArrayList<>();
        int start = 0;
        for (int i = 0; i < text.length(); i++) {
            if (isSentenceBoundary(text, i)) {
                sentences.add(text.substring(start, i + 1));
                start = nextNonWhitespaceIndex(text, i + 1);
                i = start - 1;
            }
        }

        if (start < text.length()) {
            sentences.add(text.substring(start));
        }
        return sentences;
    }

    private boolean isSentenceBoundary(String text, int index) {
        char current = text.charAt(index);
        if (current != '.' && current != '!' && current != '?') {
            return false;
        }

        if (current == '.' && endsWithKnownAbbreviation(text, index)) {
            return false;
        }

        int next = nextNonWhitespaceIndex(text, index + 1);
        return next >= text.length() || Character.isWhitespace(text.charAt(index + 1));
    }

    private int nextNonWhitespaceIndex(String text, int start) {
        int index = start;
        while (index < text.length() && Character.isWhitespace(text.charAt(index))) {
            index++;
        }
        return index;
    }

    private boolean endsWithKnownAbbreviation(String text, int dotIndex) {
        String prefix = text.substring(0, dotIndex + 1).toLowerCase(Locale.ROOT);
        for (String abbreviation : ABBREVIATIONS) {
            if (prefix.endsWith(abbreviation)) {
                return true;
            }
        }
        return false;
    }

    public record SplitClause(String text, int lineNumber) {
        public SplitClause {
            Objects.requireNonNull(text, "text");
            if (lineNumber < 1) {
                throw new IllegalArgumentException("lineNumber must be positive");
            }
        }
    }
}

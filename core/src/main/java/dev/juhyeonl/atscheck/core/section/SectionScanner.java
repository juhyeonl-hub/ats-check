package dev.juhyeonl.atscheck.core.section;

import dev.juhyeonl.atscheck.core.model.SectionKind;
import dev.juhyeonl.atscheck.core.model.Signal;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.Arrays;
import java.util.stream.Collectors;

final class SectionScanner {
    static final List<String> REQUIRED_HEADERS = List.of(
            "requirements",
            "must have",
            "we expect",
            "qualifications",
            "you have",
            "essential",
            "what we expect",
            "what you bring",
            "your profile",
            "skills required"
    );
    static final List<String> NICE_HEADERS = List.of(
            "nice to have",
            "bonus",
            "advantages",
            "preferred",
            "plus",
            "good to have",
            "we appreciate",
            "extra credit",
            "would be a plus"
    );
    static final List<String> NEUTRAL_HEADERS = List.of(
            "benefits",
            "perks",
            "what we offer",
            "we offer",
            "compensation",
            "salary",
            "about us",
            "about the team",
            "about the company",
            "who we are",
            "why join",
            "our stack",
            "tech stack",
            "technologies",
            "how to apply",
            "application process",
            "interview process",
            "location",
            "working hours",
            "contact"
    );

    private static final int SHORT_HEADER_LIMIT = 60;

    public Optional<Header> scan(String line) {
        Objects.requireNonNull(line, "line");

        String normalizedLine = ClauseSplitter.stripLeadingBullet(line).strip();
        if (normalizedLine.isEmpty()) {
            return Optional.empty();
        }
        if (!isHeaderShaped(normalizedLine)) {
            return Optional.empty();
        }

        Optional<Header> required = scanForKind(
                normalizedLine,
                REQUIRED_HEADERS,
                SectionKind.REQUIRED_SECTION,
                Signal.Type.REQUIRED_SECTION
        );
        if (required.isPresent()) {
            return required;
        }

        Optional<Header> nice = scanForKind(
                normalizedLine,
                NICE_HEADERS,
                SectionKind.NICE_SECTION,
                Signal.Type.NICE_SECTION
        );
        if (nice.isPresent()) {
            return nice;
        }

        if (matchesAnyWholeDictionaryEntry(headerTitle(normalizedLine), NEUTRAL_HEADERS)
                || normalizedLine.endsWith(":")) {
            return Optional.of(new Header(SectionKind.NONE, null));
        }

        return Optional.empty();
    }

    private Optional<Header> scanForKind(
            String line,
            List<String> dictionary,
            SectionKind sectionKind,
            Signal.Type signalType
    ) {
        String title = headerTitle(line);
        for (String entry : dictionary) {
            if (matchesWholeDictionaryEntry(title, entry)) {
                return Optional.of(new Header(sectionKind, new Signal(signalType, entry)));
            }
        }
        return Optional.empty();
    }

    private boolean isHeaderShaped(String line) {
        return line.length() <= SHORT_HEADER_LIMIT
                && !endsWithSentenceTerminator(line)
                && (line.endsWith(":") || isKnownBareHeader(line));
    }

    private boolean isKnownBareHeader(String line) {
        String title = headerTitle(line);
        return matchesAnyWholeDictionaryEntry(title, REQUIRED_HEADERS)
                || matchesAnyWholeDictionaryEntry(title, NICE_HEADERS)
                || matchesAnyWholeDictionaryEntry(title, NEUTRAL_HEADERS);
    }

    private boolean matchesAnyWholeDictionaryEntry(String line, List<String> dictionary) {
        for (String entry : dictionary) {
            if (matchesWholeDictionaryEntry(line, entry)) {
                return true;
            }
        }
        return false;
    }

    private boolean matchesWholeDictionaryEntry(String line, String entry) {
        Pattern pattern = Pattern.compile(
                "^" + bodyFor(entry) + "$",
                Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
        );
        return pattern.matcher(line).find();
    }

    private boolean endsWithSentenceTerminator(String line) {
        char last = line.charAt(line.length() - 1);
        return last == '.' || last == '!' || last == '?';
    }

    private String headerTitle(String line) {
        if (line.endsWith(":")) {
            return line.substring(0, line.length() - 1).strip();
        }
        return line.strip();
    }

    private String bodyFor(String phrase) {
        return Arrays.stream(phrase.strip().split("\\s+"))
                .map(Pattern::quote)
                .collect(Collectors.joining("\\s+"));
    }

    public record Header(SectionKind sectionKind, Signal signal) {
        public Header {
            Objects.requireNonNull(sectionKind, "sectionKind");
        }
    }
}

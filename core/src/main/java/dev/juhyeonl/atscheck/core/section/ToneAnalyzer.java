package dev.juhyeonl.atscheck.core.section;

import dev.juhyeonl.atscheck.core.model.RequirementLevel;
import dev.juhyeonl.atscheck.core.model.Signal;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

final class ToneAnalyzer {
    static final List<String> REQUIRED_TONES = List.of(
            "must",
            "required",
            "is a requirement",
            "essential",
            "mandatory",
            "expected",
            "we expect",
            "you will need",
            "fluent",
            "native",
            "proficiency in",
            "minimum"
    );
    static final List<String> NICE_TONES = List.of(
            "is a plus",
            "nice to have",
            "beneficial",
            "advantageous",
            "preferred",
            "ideally",
            "familiarity with",
            "would be nice",
            "appreciated",
            "bonus"
    );
    static final List<String> HEDGES = List.of(
            "working knowledge",
            "basic",
            "some knowledge",
            "conversational",
            "understanding of",
            "exposure to",
            "willingness to learn",
            "ability to learn"
    );
    static final List<String> NEGATIONS = List.of(
            "not required",
            "not mandatory",
            "not necessary",
            "not essential",
            "not needed",
            "not expected",
            "not a requirement",
            "no need for",
            "without the need for",
            "isn't required",
            "is not a must",
            "not obligatory"
    );
    private static final List<String> CONTRAST_CONNECTORS = List.of(
            "but",
            "however",
            "although",
            "though",
            "while"
    );

    public Analysis analyze(String clauseText) {
        Objects.requireNonNull(clauseText, "clauseText");

        List<Signal> signals = new ArrayList<>();
        boolean hasNegation = addMatches(clauseText, NEGATIONS, Signal.Type.NEGATION, signals);
        if (hasNegation) {
            if (containsAnyDictionaryEntry(clauseText, CONTRAST_CONNECTORS)) {
                return new Analysis(RequirementLevel.AMBIGUOUS, false, signals);
            }
            return new Analysis(RequirementLevel.NEGATED, false, signals);
        }

        boolean hasRequiredTone = addMatches(clauseText, REQUIRED_TONES, Signal.Type.REQUIRED_TONE, signals);
        boolean hasNiceTone = addMatches(clauseText, NICE_TONES, Signal.Type.NICE_TONE, signals);
        boolean hasHedge = addMatches(clauseText, HEDGES, Signal.Type.HEDGE, signals);

        RequirementLevel toneLevel = toneLevel(hasRequiredTone, hasNiceTone);
        return new Analysis(toneLevel, hasHedge, signals);
    }

    private boolean addMatches(
            String text,
            List<String> dictionary,
            Signal.Type signalType,
            List<Signal> signals
    ) {
        boolean matched = false;
        for (String entry : dictionary) {
            if (containsDictionaryEntry(text, entry)) {
                signals.add(new Signal(signalType, entry));
                matched = true;
            }
        }
        return matched;
    }

    private boolean containsAnyDictionaryEntry(String text, List<String> dictionary) {
        for (String entry : dictionary) {
            if (containsDictionaryEntry(text, entry)) {
                return true;
            }
        }
        return false;
    }

    private boolean containsDictionaryEntry(String text, String entry) {
        Pattern pattern = Pattern.compile(
                "(?<![\\p{L}\\p{N}_])" + bodyFor(entry) + "(?![\\p{L}\\p{N}_])",
                Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
        );
        return pattern.matcher(text).find();
    }

    private String bodyFor(String phrase) {
        return Arrays.stream(phrase.strip().split("\\s+"))
                .map(Pattern::quote)
                .collect(Collectors.joining("\\s+"));
    }

    private RequirementLevel toneLevel(boolean hasRequiredTone, boolean hasNiceTone) {
        if (hasRequiredTone && hasNiceTone) {
            return RequirementLevel.AMBIGUOUS;
        }
        if (hasRequiredTone) {
            return RequirementLevel.REQUIRED;
        }
        if (hasNiceTone) {
            return RequirementLevel.NICE;
        }
        return RequirementLevel.UNKNOWN;
    }

    public record Analysis(RequirementLevel toneLevel, boolean hasHedge, List<Signal> signals) {
        public Analysis {
            Objects.requireNonNull(toneLevel, "toneLevel");
            signals = List.copyOf(Objects.requireNonNull(signals, "signals"));
        }
    }
}

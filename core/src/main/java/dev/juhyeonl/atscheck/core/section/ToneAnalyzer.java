package dev.juhyeonl.atscheck.core.section;

import dev.juhyeonl.atscheck.core.model.RequirementLevel;
import dev.juhyeonl.atscheck.core.model.Signal;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

final class ToneAnalyzer {
    private static final int PATTERN_FLAGS = Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE;
    private static final String BOUNDARY_BEFORE = "(?<![\\p{L}\\p{N}_])";
    private static final String BOUNDARY_AFTER = "(?![\\p{L}\\p{N}_])";
    static final List<String> DECLARATIVE_REQUIRED_TONES = List.of(
            "must",
            "required",
            "is a requirement",
            "essential",
            "mandatory",
            "expected",
            "we expect",
            "you will need",
            "minimum"
    );
    static final List<String> MODIFIER_REQUIRED_TONES = List.of(
            "fluent",
            "native",
            "proficiency in",
            "working proficiency"
    );
    static final List<String> REQUIRED_TONES = requiredTones();
    static final List<String> NICE_TONES = List.of(
            "is a plus",
            "nice to have",
            "beneficial",
            "advantageous",
            "preferred",
            "familiarity with",
            "would be nice",
            "appreciated",
            "bonus"
    );
    private static final String NICE_NOUNS = "(?:plus|bonus|advantage|asset|benefit|merit|strength)";
    private static final List<Pattern> NICE_TONE_PATTERNS = List.of(
            boundedPattern("(?:is|are)\\s+(?:a|an)\\s+(?:plus|bonus|advantage|asset)"),
            boundedPattern(
                    "(?:(?:is|are|would\\s+be|will\\s+be|can\\s+be)\\s+)?"
                            + "(?:considered|seen|regarded|viewed|counted|treated)\\s+"
                            + "(?:as\\s+)?(?:(?:a|an)\\s+)?"
                            + NICE_NOUNS
            ),
            boundedPattern("counts\\s+as\\s+(?:a|an)\\s+" + NICE_NOUNS),
            boundedPattern("(?:is|are)\\s+(?:appreciated|beneficial|welcome|nice\\s+to\\s+have)")
    );
    static final List<String> HEDGES = List.of(
            "working knowledge",
            "basic",
            "some knowledge",
            "conversational",
            "understanding of",
            "exposure to",
            "willingness to learn",
            "ability to learn",
            "ideally"
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
                return new Analysis(RequirementLevel.AMBIGUOUS, false, false, signals);
            }
            return new Analysis(RequirementLevel.NEGATED, false, false, signals);
        }

        boolean hasDeclarativeRequiredTone = addMatches(
                clauseText,
                DECLARATIVE_REQUIRED_TONES,
                Signal.Type.REQUIRED_TONE,
                signals
        );
        boolean hasModifierRequiredTone = addMatches(
                clauseText,
                MODIFIER_REQUIRED_TONES,
                Signal.Type.REQUIRED_TONE,
                signals
        );
        boolean hasNiceTone = addMatches(clauseText, NICE_TONES, Signal.Type.NICE_TONE, signals);
        boolean hasExplicitNiceTone = addPatternMatches(
                clauseText,
                NICE_TONE_PATTERNS,
                Signal.Type.NICE_TONE,
                signals
        );
        hasNiceTone = hasExplicitNiceTone || hasNiceTone;
        boolean hasHedge = addMatches(clauseText, HEDGES, Signal.Type.HEDGE, signals);

        RequirementLevel toneLevel = toneLevel(
                hasDeclarativeRequiredTone,
                hasModifierRequiredTone,
                hasNiceTone,
                hasExplicitNiceTone
        );
        return new Analysis(toneLevel, hasHedge, hasExplicitNiceTone, signals);
    }

    private static List<String> requiredTones() {
        List<String> tones = new ArrayList<>(DECLARATIVE_REQUIRED_TONES);
        tones.addAll(MODIFIER_REQUIRED_TONES);
        return List.copyOf(tones);
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
                addSignal(signals, new Signal(signalType, entry));
                matched = true;
            }
        }
        return matched;
    }

    private boolean addPatternMatches(
            String text,
            List<Pattern> patterns,
            Signal.Type signalType,
            List<Signal> signals
    ) {
        boolean matched = false;
        for (Pattern pattern : patterns) {
            Matcher matcher = pattern.matcher(text);
            while (matcher.find()) {
                addSignal(signals, new Signal(signalType, normalizedMatch(matcher.group())));
                matched = true;
            }
        }
        return matched;
    }

    private void addSignal(List<Signal> signals, Signal signal) {
        if (!signals.contains(signal)) {
            signals.add(signal);
        }
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
        Pattern pattern = boundedPattern(bodyFor(entry));
        return pattern.matcher(text).find();
    }

    private static Pattern boundedPattern(String body) {
        return Pattern.compile(BOUNDARY_BEFORE + body + BOUNDARY_AFTER, PATTERN_FLAGS);
    }

    private String bodyFor(String phrase) {
        return Arrays.stream(phrase.strip().split("\\s+"))
                .map(Pattern::quote)
                .collect(Collectors.joining("\\s+"));
    }

    private String normalizedMatch(String text) {
        return text.strip().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    private RequirementLevel toneLevel(
            boolean hasDeclarativeRequiredTone,
            boolean hasModifierRequiredTone,
            boolean hasNiceTone,
            boolean hasExplicitNiceTone
    ) {
        if (hasDeclarativeRequiredTone && hasNiceTone) {
            return RequirementLevel.AMBIGUOUS;
        }
        if (hasModifierRequiredTone && hasExplicitNiceTone) {
            return RequirementLevel.NICE;
        }
        if (hasModifierRequiredTone && hasNiceTone) {
            return RequirementLevel.AMBIGUOUS;
        }
        if (hasDeclarativeRequiredTone || hasModifierRequiredTone) {
            return RequirementLevel.REQUIRED;
        }
        if (hasNiceTone) {
            return RequirementLevel.NICE;
        }
        return RequirementLevel.UNKNOWN;
    }

    public record Analysis(
            RequirementLevel toneLevel,
            boolean hasHedge,
            boolean hasExplicitNiceTone,
            List<Signal> signals
    ) {
        public Analysis {
            Objects.requireNonNull(toneLevel, "toneLevel");
            signals = List.copyOf(Objects.requireNonNull(signals, "signals"));
        }
    }
}

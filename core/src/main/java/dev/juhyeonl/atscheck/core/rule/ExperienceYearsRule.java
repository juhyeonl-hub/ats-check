package dev.juhyeonl.atscheck.core.rule;

import dev.juhyeonl.atscheck.core.model.Clause;
import dev.juhyeonl.atscheck.core.model.Finding;
import dev.juhyeonl.atscheck.core.model.Profile;
import dev.juhyeonl.atscheck.core.model.RequirementLevel;
import dev.juhyeonl.atscheck.core.model.RuleId;
import dev.juhyeonl.atscheck.core.model.Status;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ExperienceYearsRule {
    private static final List<Pattern> NUMERIC_PATTERNS = List.of(
            Pattern.compile(
                    "(?<![\\p{L}\\p{N}_])(\\d{1,2})\\s*(?:-|to)\\s*\\d{1,2}\\s+years?\\b",
                    Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
            ),
            Pattern.compile(
                    "(?<![\\p{L}\\p{N}_])(\\d{1,2})\\s+or\\s+more\\s+years?\\b",
                    Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
            ),
            Pattern.compile(
                    "(?<![\\p{L}\\p{N}_])(\\d{1,2})\\+\\s*years?\\b",
                    Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
            ),
            Pattern.compile(
                    "\\b(?:at\\s+least|minimum(?:\\s+of)?|over|more\\s+than)\\s+(\\d{1,2})\\s+years?\\b",
                    Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
            ),
            Pattern.compile(
                    "(?<![\\p{L}\\p{N}_])(\\d{1,2})\\s+years?\\b",
                    Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
            )
    );
    private static final Pattern WORD_NUMBER_YEARS = Pattern.compile(
            "(?<![\\p{L}\\p{N}_])(one|two|three|four|five|six|seven|eight|nine|ten)\\s+years?\\b",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
    );
    private static final Map<String, Integer> WORD_NUMBERS = Map.ofEntries(
            Map.entry("one", 1),
            Map.entry("two", 2),
            Map.entry("three", 3),
            Map.entry("four", 4),
            Map.entry("five", 5),
            Map.entry("six", 6),
            Map.entry("seven", 7),
            Map.entry("eight", 8),
            Map.entry("nine", 9),
            Map.entry("ten", 10)
    );

    private ExperienceYearsRule() {
    }

    public static Finding evaluate(List<Clause> clauses, Profile profile) {
        Objects.requireNonNull(clauses, "clauses");
        Objects.requireNonNull(profile, "profile");

        List<YearRequirement> requirements = new ArrayList<>();
        for (Clause clause : clauses) {
            Objects.requireNonNull(clause, "clauses element");
            if (shouldInspect(clause.level())) {
                requirements.addAll(extractRequirements(clause));
            }
        }

        if (requirements.isEmpty()) {
            return new Finding(RuleId.EXPERIENCE_YEARS, Status.PASS, "not specified", List.of());
        }

        int requiredYears = requirements.stream()
                .mapToInt(YearRequirement::years)
                .max()
                .orElseThrow();
        List<YearRequirement> strongestRequirements = requirements.stream()
                .filter(requirement -> requirement.years() == requiredYears)
                .toList();

        Status status = statusFor(requiredYears, strongestRequirements, profile);
        String summary = firstPhraseFor(strongestRequirements, status)
                + " (profile: " + profile.yearsExperience()
                + ", tolerance: " + profile.yearsTolerance() + ")";
        if (status == Status.PASS) {
            return new Finding(RuleId.EXPERIENCE_YEARS, status, summary, List.of());
        }

        return new Finding(
                RuleId.EXPERIENCE_YEARS,
                status,
                summary,
                evidenceFor(strongestRequirements, status, requiredYears, profile)
        );
    }

    private static boolean shouldInspect(RequirementLevel level) {
        return level == RequirementLevel.REQUIRED || level == RequirementLevel.AMBIGUOUS;
    }

    private static List<YearRequirement> extractRequirements(Clause clause) {
        List<YearRequirement> requirements = new ArrayList<>();
        String text = clause.text();
        for (Pattern pattern : NUMERIC_PATTERNS) {
            Matcher matcher = pattern.matcher(text);
            while (matcher.find()) {
                if (isUpperBoundMention(text, matcher.start(1))) {
                    continue;
                }
                requirements.add(new YearRequirement(
                        Integer.parseInt(matcher.group(1)),
                        matcher.group(),
                        clause
                ));
            }
        }

        Matcher wordMatcher = WORD_NUMBER_YEARS.matcher(text);
        while (wordMatcher.find()) {
            if (isUpperBoundMention(text, wordMatcher.start(1))) {
                continue;
            }
            requirements.add(new YearRequirement(
                    WORD_NUMBERS.get(wordMatcher.group(1).toLowerCase(Locale.ROOT)),
                    wordMatcher.group(),
                    clause
            ));
        }

        return List.copyOf(requirements);
    }

    private static boolean isUpperBoundMention(String text, int numberStart) {
        int index = numberStart - 1;
        while (index >= 0 && Character.isWhitespace(text.charAt(index))) {
            index--;
        }
        if (index >= 0 && text.charAt(index) == '-') {
            return true;
        }

        int wordEnd = index + 1;
        while (index >= 0 && Character.isLetter(text.charAt(index))) {
            index--;
        }
        String previousWord = text.substring(index + 1, wordEnd).toLowerCase(Locale.ROOT);
        return previousWord.equals("to") || previousWord.equals("or");
    }

    private static Status statusFor(
            int requiredYears,
            List<YearRequirement> strongestRequirements,
            Profile profile
    ) {
        long toleratedYears = (long) profile.yearsExperience() + profile.yearsTolerance();
        if (requiredYears > toleratedYears) {
            Status status = Status.PASS;
            for (YearRequirement requirement : strongestRequirements) {
                Status candidate = requirement.clause().level() == RequirementLevel.REQUIRED
                        ? Status.FAIL
                        : Status.REVIEW;
                if (candidate.strongerThan(status)) {
                    status = candidate;
                }
            }
            return status;
        }
        if (requiredYears > profile.yearsExperience()) {
            return Status.WARN;
        }
        return Status.PASS;
    }

    private static String firstPhraseFor(List<YearRequirement> requirements, Status status) {
        for (YearRequirement requirement : requirements) {
            if (status == Status.PASS || candidateStatusForEvidence(requirement) == status) {
                return requirement.phrase();
            }
        }
        return requirements.get(0).phrase();
    }

    private static List<Clause> evidenceFor(
            List<YearRequirement> requirements,
            Status status,
            int requiredYears,
            Profile profile
    ) {
        LinkedHashSet<Clause> evidence = new LinkedHashSet<>();
        for (YearRequirement requirement : requirements) {
            Status candidate = status;
            long toleratedYears = (long) profile.yearsExperience() + profile.yearsTolerance();
            if (requiredYears > toleratedYears) {
                candidate = candidateStatusForEvidence(requirement);
            }
            if (candidate == status) {
                evidence.add(requirement.clause());
            }
        }
        return List.copyOf(evidence);
    }

    private static Status candidateStatusForEvidence(YearRequirement requirement) {
        return requirement.clause().level() == RequirementLevel.REQUIRED
                ? Status.FAIL
                : Status.REVIEW;
    }

    private record YearRequirement(int years, String phrase, Clause clause) {
    }
}

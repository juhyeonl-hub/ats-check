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
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public final class LanguageRule {
    private static final List<Language> LANGUAGES = List.of(
            new Language("Finnish", "finnish", List.of(
                    "finnish",
                    "finish",
                    "suomi",
                    "suomen kieli",
                    "finska"
            )),
            new Language("Swedish", "swedish", List.of(
                    "swedish",
                    "ruotsi",
                    "svenska"
            ))
    );

    private LanguageRule() {
    }

    public static Finding evaluate(List<Clause> clauses, Profile profile) {
        Objects.requireNonNull(clauses, "clauses");
        Objects.requireNonNull(profile, "profile");

        List<Assessment> assessments = new ArrayList<>();
        for (Clause clause : clauses) {
            Objects.requireNonNull(clause, "clauses element");
            for (Language language : LANGUAGES) {
                if (language.isMentionedIn(clause.text())) {
                    assessments.add(assess(language, clause, profile));
                }
            }
        }

        if (assessments.isEmpty()) {
            return new Finding(RuleId.LANGUAGE, Status.PASS, "English only", List.of());
        }

        Status status = strongestStatus(assessments);
        String summary = firstSummaryFor(assessments, status);
        if (status == Status.PASS) {
            return new Finding(RuleId.LANGUAGE, status, summary, List.of());
        }

        return new Finding(RuleId.LANGUAGE, status, summary, evidenceFor(assessments, status));
    }

    private static Assessment assess(Language language, Clause clause, Profile profile) {
        if (language.isIn(profile.languages())) {
            return new Assessment(Status.PASS, language.displayName() + " required (you have it)", clause);
        }

        return switch (clause.level()) {
            case REQUIRED -> new Assessment(Status.FAIL, language.displayName() + " required", clause);
            case AMBIGUOUS -> new Assessment(
                    Status.REVIEW,
                    language.displayName() + " - ambiguous requirement",
                    clause
            );
            case NEGATED -> new Assessment(
                    Status.PASS,
                    language.displayName() + " explicitly not required",
                    clause
            );
            case NICE -> new Assessment(Status.PASS, language.displayName() + " is a plus", clause);
            case UNKNOWN -> new Assessment(
                    Status.PASS,
                    language.displayName() + " mentioned, no requirement signal",
                    clause
            );
        };
    }

    private static Status strongestStatus(List<Assessment> assessments) {
        Status status = Status.PASS;
        for (Assessment assessment : assessments) {
            if (assessment.status().strongerThan(status)) {
                status = assessment.status();
            }
        }
        return status;
    }

    private static String firstSummaryFor(List<Assessment> assessments, Status status) {
        for (Assessment assessment : assessments) {
            if (assessment.status() == status) {
                return assessment.summary();
            }
        }
        throw new IllegalStateException("no assessment for status " + status);
    }

    private static List<Clause> evidenceFor(List<Assessment> assessments, Status status) {
        LinkedHashSet<Clause> evidence = new LinkedHashSet<>();
        for (Assessment assessment : assessments) {
            if (assessment.status() == status) {
                evidence.add(assessment.clause());
            }
        }
        return List.copyOf(evidence);
    }

    private record Language(String displayName, String canonicalName, List<String> variants) {
        private boolean isMentionedIn(String text) {
            for (String variant : variants) {
                if (containsPhrase(text, variant)) {
                    return true;
                }
            }
            return false;
        }

        private boolean isIn(Set<String> profileLanguages) {
            if (profileLanguages.contains(canonicalName)) {
                return true;
            }
            for (String variant : variants) {
                if (profileLanguages.contains(variant)) {
                    return true;
                }
            }
            return false;
        }
    }

    private record Assessment(Status status, String summary, Clause clause) {
    }

    private static boolean containsPhrase(String text, String phrase) {
        Pattern pattern = Pattern.compile(
                "(?<![\\p{L}\\p{N}_])" + phraseBody(phrase) + "(?![\\p{L}\\p{N}_])",
                Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
        );
        return pattern.matcher(text).find();
    }

    private static String phraseBody(String phrase) {
        return Pattern.compile("\\s+")
                .splitAsStream(phrase.strip())
                .map(Pattern::quote)
                .collect(Collectors.joining("\\s+"));
    }
}

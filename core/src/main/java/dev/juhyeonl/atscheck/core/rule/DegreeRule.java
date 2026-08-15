package dev.juhyeonl.atscheck.core.rule;

import dev.juhyeonl.atscheck.core.model.Clause;
import dev.juhyeonl.atscheck.core.model.Degree;
import dev.juhyeonl.atscheck.core.model.Finding;
import dev.juhyeonl.atscheck.core.model.Profile;
import dev.juhyeonl.atscheck.core.model.RequirementLevel;
import dev.juhyeonl.atscheck.core.model.RuleId;
import dev.juhyeonl.atscheck.core.model.Status;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

public final class DegreeRule {
    private static final Pattern BACHELOR = phrasePattern(
            "bachelor(?:['\\u2019]s)?|b\\.?sc\\.?|undergraduate\\s+degree"
    );
    private static final Pattern MASTER_POSSESSIVE = phrasePattern("master['\\u2019]s");
    private static final Pattern MSC = phrasePattern("m\\.?sc\\.?");
    private static final Pattern GRADUATE_DEGREE = phrasePattern("graduate\\s+degree");
    private static final Pattern MASTER_WORD = phrasePattern("master");
    private static final List<Pattern> MASTER_CONTEXT = List.of(
            phrasePattern("degree"),
            phrasePattern("education"),
            phrasePattern("studies"),
            phrasePattern("university"),
            MSC
    );
    private static final Pattern PHD = phrasePattern("ph\\.?d|doctorate|doctoral");
    private static final Pattern EQUIVALENT_EXPERIENCE = Pattern.compile(
            "\\bor\\s+(?:equivalent(?:\\s+experience)?|comparable\\s+experience)\\b",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
    );

    private DegreeRule() {
    }

    public static Finding evaluate(List<Clause> clauses, Profile profile) {
        Objects.requireNonNull(clauses, "clauses");
        Objects.requireNonNull(profile, "profile");

        List<Assessment> assessments = new ArrayList<>();
        for (Clause clause : clauses) {
            Objects.requireNonNull(clause, "clauses element");
            Degree degree = detectDegree(clause.text());
            if (degree != Degree.NONE) {
                assessments.add(assess(clause, degree, profile));
            }
        }

        if (assessments.isEmpty()) {
            return new Finding(RuleId.DEGREE, Status.PASS, "not required", List.of());
        }

        Status status = strongestStatus(assessments);
        if (status == Status.PASS) {
            return new Finding(RuleId.DEGREE, status, "not required", List.of());
        }

        return new Finding(
                RuleId.DEGREE,
                status,
                firstSummaryFor(assessments, status),
                evidenceFor(assessments, status)
        );
    }

    private static Assessment assess(Clause clause, Degree degree, Profile profile) {
        if (degree.compareTo(profile.degree()) <= 0) {
            return new Assessment(Status.PASS, "not required", clause);
        }

        Status status = switch (clause.level()) {
            case REQUIRED -> Status.FAIL;
            case AMBIGUOUS -> Status.REVIEW;
            case NICE, NEGATED, UNKNOWN -> Status.PASS;
        };

        if (hasEquivalentExperience(clause.text())
                && (status == Status.FAIL || status == Status.REVIEW)) {
            status = Status.WARN;
        }

        return new Assessment(status, summaryFor(degree, status, profile), clause);
    }

    private static Degree detectDegree(String text) {
        Degree degree = Degree.NONE;
        if (BACHELOR.matcher(text).find()) {
            degree = Degree.BACHELOR;
        }
        if (containsMasterDegree(text)) {
            degree = Degree.MASTER;
        }
        if (PHD.matcher(text).find()) {
            degree = Degree.PHD;
        }
        return degree;
    }

    private static boolean containsMasterDegree(String text) {
        if (MASTER_POSSESSIVE.matcher(text).find()
                || MSC.matcher(text).find()
                || GRADUATE_DEGREE.matcher(text).find()) {
            return true;
        }
        return MASTER_WORD.matcher(text).find() && hasMasterContext(text);
    }

    private static boolean hasMasterContext(String text) {
        for (Pattern pattern : MASTER_CONTEXT) {
            if (pattern.matcher(text).find()) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasEquivalentExperience(String text) {
        return EQUIVALENT_EXPERIENCE.matcher(text).find();
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

    private static String summaryFor(Degree degree, Status status, Profile profile) {
        String label = switch (degree) {
            case NONE -> "No degree";
            case BACHELOR -> "Bachelor";
            case MASTER -> "Master";
            case PHD -> "PhD";
        };
        String requirement = status == Status.WARN
                ? " or equivalent experience"
                : " required";
        return label + requirement + " (profile: " + profile.degree() + ")";
    }

    private static Pattern phrasePattern(String body) {
        return Pattern.compile(
                "(?<![\\p{L}\\p{N}_])(?:" + body + ")(?![\\p{L}\\p{N}_])",
                Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
        );
    }

    private record Assessment(Status status, String summary, Clause clause) {
    }
}

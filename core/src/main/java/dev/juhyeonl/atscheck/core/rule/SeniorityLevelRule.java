package dev.juhyeonl.atscheck.core.rule;

import dev.juhyeonl.atscheck.core.model.Finding;
import dev.juhyeonl.atscheck.core.model.JobPosting;
import dev.juhyeonl.atscheck.core.model.Profile;
import dev.juhyeonl.atscheck.core.model.RuleId;
import dev.juhyeonl.atscheck.core.model.Seniority;
import dev.juhyeonl.atscheck.core.model.Status;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public final class SeniorityLevelRule {
    private static final int MAX_TITLE_LENGTH = 40;
    private static final List<Marker> MARKERS = List.of(
            marker(Seniority.JUNIOR, "junior"),
            marker(Seniority.JUNIOR, "entry"),
            marker(Seniority.JUNIOR, "entry-level"),
            marker(Seniority.JUNIOR, "graduate"),
            marker(Seniority.JUNIOR, "trainee"),
            marker(Seniority.JUNIOR, "intern"),
            marker(Seniority.JUNIOR, "apprentice"),
            marker(Seniority.SENIOR, "senior"),
            marker(Seniority.SENIOR, "sr."),
            marker(Seniority.SENIOR, "sr"),
            marker(Seniority.LEAD, "lead"),
            marker(Seniority.LEAD, "staff"),
            marker(Seniority.LEAD, "principal"),
            marker(Seniority.LEAD, "head of"),
            marker(Seniority.LEAD, "director"),
            marker(Seniority.LEAD, "chief"),
            marker(Seniority.LEAD, "architect")
    );

    private SeniorityLevelRule() {
    }

    public static Finding evaluate(JobPosting posting, Profile profile) {
        Objects.requireNonNull(posting, "posting");
        Objects.requireNonNull(profile, "profile");

        Seniority detected = null;
        for (Marker marker : MARKERS) {
            if (marker.isMentionedIn(posting.title())
                    && (detected == null || marker.seniority().rank() > detected.rank())) {
                detected = marker.seniority();
            }
        }

        boolean hasMarker = detected != null;
        if (detected == null) {
            detected = Seniority.MID;
        }

        Status status = detected.rank() > profile.maxSeniority().rank()
                ? Status.WARN
                : Status.PASS;
        return new Finding(
                RuleId.SENIORITY_LEVEL,
                status,
                summaryFor(posting.title(), profile.maxSeniority(), hasMarker),
                List.of()
        );
    }

    private static String summaryFor(String title, Seniority maxSeniority, boolean hasMarker) {
        String normalizedTitle = normalizedTitle(title);
        if (normalizedTitle.isEmpty()) {
            return "no seniority marker";
        }
        if (!hasMarker) {
            return normalizedTitle + " (no seniority marker)";
        }
        return normalizedTitle + " (profile max: " + label(maxSeniority) + ")";
    }

    private static String normalizedTitle(String title) {
        String normalized = title.strip();
        if (normalized.length() > MAX_TITLE_LENGTH) {
            return normalized.substring(0, MAX_TITLE_LENGTH) + "…";
        }
        return normalized;
    }

    private static String label(Seniority seniority) {
        return seniority.name().toLowerCase(Locale.ROOT);
    }

    private static Marker marker(Seniority seniority, String phrase) {
        return new Marker(seniority, phrasePattern(phrase));
    }

    private static Pattern phrasePattern(String phrase) {
        String body = Pattern.compile("\\s+")
                .splitAsStream(phrase.strip())
                .map(Pattern::quote)
                .collect(Collectors.joining("\\s+"));
        return Pattern.compile(
                "(?<![\\p{L}\\p{N}_])" + body + "(?![\\p{L}\\p{N}_])",
                Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
        );
    }

    private record Marker(Seniority seniority, Pattern pattern) {
        private boolean isMentionedIn(String text) {
            return pattern.matcher(text).find();
        }
    }
}

package dev.juhyeonl.atscheck.core.model;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

public record Profile(
        int yearsExperience,
        int yearsTolerance,
        Seniority maxSeniority,
        Set<String> languages,
        Degree degree,
        Set<String> skills
) {
    public Profile {
        if (yearsExperience < 0) {
            throw new IllegalArgumentException("yearsExperience must not be negative");
        }
        if (yearsTolerance < 0) {
            throw new IllegalArgumentException("yearsTolerance must not be negative");
        }
        Objects.requireNonNull(maxSeniority, "maxSeniority");
        Objects.requireNonNull(degree, "degree");
        languages = normalizeSet(languages, "languages");
        skills = normalizeSet(skills, "skills");
    }

    public static Profile defaults() {
        return new Profile(
                0,
                1,
                Seniority.LEAD,
                Set.of("english"),
                Degree.NONE,
                Set.of()
        );
    }

    private static Set<String> normalizeSet(Set<String> values, String name) {
        Objects.requireNonNull(values, name);

        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (String value : values) {
            normalized.add(Objects.requireNonNull(value, name + " element")
                    .strip()
                    .toLowerCase(Locale.ROOT));
        }
        return Set.copyOf(normalized);
    }
}

package dev.juhyeonl.atscheck.core.model;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

public record SkillGap(
        Set<String> matched,
        Set<String> missingRequired,
        Set<String> missingNice
) {
    public SkillGap {
        matched = orderedCopyOf(matched, "matched");
        missingRequired = orderedCopyOf(missingRequired, "missingRequired");
        missingNice = orderedCopyOf(missingNice, "missingNice");
    }

    private static Set<String> orderedCopyOf(Set<String> values, String name) {
        Objects.requireNonNull(values, name);

        LinkedHashSet<String> copy = new LinkedHashSet<>();
        for (String value : values) {
            copy.add(Objects.requireNonNull(value, name + " element"));
        }
        return Collections.unmodifiableSet(copy);
    }
}

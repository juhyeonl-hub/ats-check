package dev.juhyeonl.atscheck.core.model;

import java.util.Objects;

public record Signal(Type type, String dictionaryEntry) {
    public Signal {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(dictionaryEntry, "dictionaryEntry");
    }

    public enum Type {
        REQUIRED_SECTION,
        NICE_SECTION,
        REQUIRED_TONE,
        NICE_TONE,
        NEGATION,
        HEDGE
    }
}

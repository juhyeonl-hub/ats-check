package dev.juhyeonl.atscheck.core.model;

public enum Status {
    PASS,
    WARN,
    REVIEW,
    FAIL;

    public boolean strongerThan(Status other) {
        return compareTo(other) > 0;
    }
}

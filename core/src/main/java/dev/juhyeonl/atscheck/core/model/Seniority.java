package dev.juhyeonl.atscheck.core.model;

public enum Seniority {
    JUNIOR,
    MID,
    SENIOR,
    LEAD;

    public int rank() {
        return ordinal();
    }
}

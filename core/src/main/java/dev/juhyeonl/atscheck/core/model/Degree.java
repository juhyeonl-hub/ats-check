package dev.juhyeonl.atscheck.core.model;

public enum Degree {
    NONE,
    BACHELOR,
    MASTER,
    PHD;

    public int rank() {
        return ordinal();
    }
}

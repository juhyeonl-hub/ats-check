package dev.juhyeonl.atscheck.core.model;

import java.util.Objects;

public record JobPosting(String title, String body) {
    public JobPosting {
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(body, "body");
    }

    public static JobPosting fromText(String text) {
        Objects.requireNonNull(text, "text");

        String title = "";
        String[] lines = text.split("\\R", -1);
        for (String line : lines) {
            if (!line.isBlank()) {
                title = line.strip();
                break;
            }
        }

        return new JobPosting(title, text);
    }
}

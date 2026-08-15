package dev.juhyeonl.atscheck.cli.store;

import java.util.Objects;

public record JobFile(FrontMatter frontMatter, String body) {
    public JobFile {
        frontMatter = Objects.requireNonNull(frontMatter, "frontMatter");
        body = Objects.requireNonNull(body, "body");
    }
}

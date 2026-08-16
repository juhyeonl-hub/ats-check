package dev.juhyeonl.atscheck.cli.render;

import java.util.Locale;
import java.util.Optional;

public enum TerminalLanguage {
    EN,
    KO;

    public static Optional<TerminalLanguage> parse(String value) {
        if (value == null || value.isBlank()) {
            return Optional.of(EN);
        }

        return switch (value.strip().toLowerCase(Locale.ROOT)) {
            case "en" -> Optional.of(EN);
            case "ko" -> Optional.of(KO);
            default -> Optional.empty();
        };
    }
}

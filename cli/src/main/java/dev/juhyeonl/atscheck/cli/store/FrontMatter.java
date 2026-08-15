package dev.juhyeonl.atscheck.cli.store;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public record FrontMatter(
        String url,
        String company,
        String title,
        String savedAt,
        String status,
        Map<String, Object> extra
) {
    public static final String DEFAULT_STATUS = "new";

    public FrontMatter {
        url = nullToEmpty(url);
        company = nullToEmpty(company);
        title = nullToEmpty(title);
        savedAt = nullToEmpty(savedAt);
        status = nullToEmpty(status).isBlank() ? DEFAULT_STATUS : status;
        extra = Collections.unmodifiableMap(new LinkedHashMap<>(Objects.requireNonNull(extra, "extra")));
    }

    public static FrontMatter empty() {
        return new FrontMatter("", "", "", "", DEFAULT_STATUS, Map.of());
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}

package dev.juhyeonl.atscheck.cli.store;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.Normalizer;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public final class JobStore {
    private static final int MAX_FILENAME_LENGTH = 80;
    private static final String EXTENSION = ".md";
    private static final Pattern COMBINING_MARKS = Pattern.compile("\\p{M}+");
    private static final Pattern NON_DIGIT = Pattern.compile("\\D+");

    private final Path jobsDirectory;
    private final JobFileParser parser;
    private final JobFileWriter writer;

    public JobStore(Path jobsDirectory) {
        this(jobsDirectory, new JobFileParser(), new JobFileWriter());
    }

    public JobStore(Path jobsDirectory, JobFileParser parser, JobFileWriter writer) {
        this.jobsDirectory = Objects.requireNonNull(jobsDirectory, "jobsDirectory");
        this.parser = Objects.requireNonNull(parser, "parser");
        this.writer = Objects.requireNonNull(writer, "writer");
    }

    public Path jobsDirectory() {
        return jobsDirectory;
    }

    public Path save(JobFile jobFile) throws IOException {
        Objects.requireNonNull(jobFile, "jobFile");
        Files.createDirectories(jobsDirectory);

        String baseName = baseNameFor(jobFile.frontMatter());
        Path path = uniquePath(baseName);
        writer.write(path, jobFile);
        return path;
    }

    public List<Path> findByFilenamePattern(String pattern) throws IOException {
        String normalizedPattern = Objects.requireNonNull(pattern, "pattern").toLowerCase(Locale.ROOT);
        if (!Files.isDirectory(jobsDirectory)) {
            return List.of();
        }
        try (Stream<Path> paths = Files.list(jobsDirectory)) {
            return paths
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).contains(normalizedPattern))
                    .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                    .toList();
        }
    }

    public List<Path> listJobFiles() throws IOException {
        if (!Files.isDirectory(jobsDirectory)) {
            return List.of();
        }
        try (Stream<Path> paths = Files.list(jobsDirectory)) {
            return paths
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(EXTENSION))
                    .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                    .toList();
        }
    }

    public JobFile read(Path path) throws IOException, JobFileParser.JobFileParseException {
        return parser.parse(Files.readString(path, StandardCharsets.UTF_8));
    }

    private String baseNameFor(FrontMatter frontMatter) {
        String company = slug(frontMatter.company());
        String title = slug(frontMatter.title());
        if (!company.isBlank() && !title.isBlank()) {
            return company + "-" + title;
        }
        if (!title.isBlank()) {
            return title;
        }
        String timestamp = NON_DIGIT.matcher(frontMatter.savedAt()).replaceAll("");
        if (timestamp.length() > 14) {
            timestamp = timestamp.substring(0, 14);
        }
        if (timestamp.isBlank()) {
            timestamp = Long.toString(System.currentTimeMillis());
        }
        return "job-" + timestamp;
    }

    private String slug(String value) {
        String normalized = Normalizer.normalize(value, Normalizer.Form.NFKD)
                .toLowerCase(Locale.ROOT);
        normalized = COMBINING_MARKS.matcher(normalized).replaceAll("");

        StringBuilder builder = new StringBuilder();
        boolean previousDash = false;
        for (int index = 0; index < normalized.length(); index++) {
            char current = normalized.charAt(index);
            if (current >= 'a' && current <= 'z' || current >= '0' && current <= '9') {
                builder.append(current);
                previousDash = false;
            } else if (Character.isWhitespace(current) || current == '-') {
                previousDash = appendDash(builder, previousDash);
            } else {
                previousDash = appendDash(builder, previousDash);
            }
        }
        return trimDashes(builder.toString());
    }

    private boolean appendDash(StringBuilder builder, boolean previousDash) {
        if (!previousDash && !builder.isEmpty()) {
            builder.append('-');
            return true;
        }
        return previousDash;
    }

    private Path uniquePath(String baseName) {
        int attempt = 1;
        while (true) {
            String suffix = attempt == 1 ? "" : "-" + attempt;
            String truncatedBaseName = truncateBaseName(baseName, suffix);
            Path candidate = jobsDirectory.resolve(truncatedBaseName + suffix + EXTENSION);
            if (!Files.exists(candidate)) {
                return candidate;
            }
            attempt++;
        }
    }

    private String truncateBaseName(String baseName, String suffix) {
        int maxBaseLength = MAX_FILENAME_LENGTH - EXTENSION.length() - suffix.length();
        String truncated = baseName.length() <= maxBaseLength
                ? baseName
                : baseName.substring(0, maxBaseLength);
        truncated = trimDashes(truncated);
        if (!truncated.isBlank()) {
            return truncated;
        }
        return "job";
    }

    private String trimDashes(String value) {
        int start = 0;
        int end = value.length();
        while (start < end && value.charAt(start) == '-') {
            start++;
        }
        while (end > start && value.charAt(end - 1) == '-') {
            end--;
        }
        return value.substring(start, end);
    }
}

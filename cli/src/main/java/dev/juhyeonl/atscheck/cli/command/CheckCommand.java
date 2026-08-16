package dev.juhyeonl.atscheck.cli.command;

import dev.juhyeonl.atscheck.cli.AtsCheckCli;
import dev.juhyeonl.atscheck.cli.batch.BatchCheckResult;
import dev.juhyeonl.atscheck.cli.batch.BatchJobResult;
import dev.juhyeonl.atscheck.cli.batch.CheckedJob;
import dev.juhyeonl.atscheck.cli.batch.JobCheckService;
import dev.juhyeonl.atscheck.cli.config.ProfileLoader;
import dev.juhyeonl.atscheck.cli.render.BatchJsonRenderer;
import dev.juhyeonl.atscheck.cli.render.BatchTerminalRenderer;
import dev.juhyeonl.atscheck.cli.render.JsonRenderer;
import dev.juhyeonl.atscheck.cli.render.TerminalLanguage;
import dev.juhyeonl.atscheck.cli.render.TerminalRenderer;
import dev.juhyeonl.atscheck.core.model.CheckResult;
import dev.juhyeonl.atscheck.core.model.Profile;
import dev.juhyeonl.atscheck.core.model.Verdict;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;
import picocli.CommandLine;
import picocli.CommandLine.Option;

public final class CheckCommand {
    private static final int DEFAULT_TERMINAL_WIDTH = 100;
    private static final String LANGUAGE_ENVIRONMENT_VARIABLE = "ATS_CHECK_LANG";

    @Option(names = "--job", paramLabel = "<path>", description = "UTF-8 job posting text file.")
    private Path jobPath;

    @Option(names = "--job-dir", paramLabel = "<path>", description = "Directory of UTF-8 job posting files.")
    private Path jobDirectory;

    @Option(names = "--profile", paramLabel = "<path>", description = "profile.yml path.")
    private Path profilePath;

    @Option(names = "--json", description = "Print a stable JSON result.")
    private boolean json;

    @Option(names = "--lang", paramLabel = "<lang>", description = "Terminal output language: en or ko.")
    private String languageOption;

    @Option(names = "--width", paramLabel = "<n>", description = "Terminal width for batch table output.")
    private Integer width;

    @Option(names = "--no-hyperlink", description = "Disable OSC 8 hyperlinks in batch table output.")
    private boolean noHyperlink;

    @Option(names = "--debug", description = "Print stack traces for unexpected internal errors.")
    private boolean debug;

    private final ProfileLoader profileLoader;
    private final JobCheckService jobCheckService = new JobCheckService();
    private final Map<String, String> environment;

    public CheckCommand(ProfileLoader profileLoader) {
        this(profileLoader, System.getenv());
    }

    public CheckCommand(ProfileLoader profileLoader, Map<String, String> environment) {
        this.profileLoader = Objects.requireNonNull(profileLoader, "profileLoader");
        this.environment = Map.copyOf(Objects.requireNonNull(environment, "environment"));
    }

    public int execute(Context context) {
        try {
            validateOptions();
            TerminalLanguage language = terminalLanguage(context.err());
            if (jobDirectory != null) {
                return executeBatch(context, language);
            }

            String jobText = readJobText(context);
            Profile profile = profileLoader.load(profilePath, context.err());
            CheckedJob checkedJob = jobCheckService.check(jobText, profile);
            render(checkedJob.result(), context.out(), language);
            return exitCodeFor(checkedJob.result().verdict());
        } catch (ProfileLoader.ProfileLoadException exception) {
            context.err().println(exception.getMessage());
            context.err().flush();
            return AtsCheckCli.EXIT_USAGE;
        } catch (JobCheckService.JobCheckException exception) {
            context.err().println(exception.getMessage());
            context.err().flush();
            return AtsCheckCli.EXIT_USAGE;
        } catch (UsageException exception) {
            context.err().println(exception.getMessage());
            if (exception.showUsage()) {
                context.commandLine().usage(context.err());
            }
            context.err().flush();
            return AtsCheckCli.EXIT_USAGE;
        }
    }

    private void validateOptions() throws UsageException {
        if (jobPath != null && jobDirectory != null) {
            throw new UsageException("cannot use --job and --job-dir together", false);
        }

        if (width != null && width < 1) {
            throw new UsageException("--width must be positive", false);
        }
    }

    private int executeBatch(
            Context context,
            TerminalLanguage language
    ) throws ProfileLoader.ProfileLoadException, UsageException {
        if (!Files.isDirectory(jobDirectory)) {
            throw new UsageException("job directory not found: " + jobDirectory, false);
        }

        List<Path> jobFiles = listJobFiles();
        if (jobFiles.isEmpty()) {
            context.out().println("No job files found in " + jobDirectory);
            context.out().flush();
            return AtsCheckCli.EXIT_APPLY;
        }

        Profile profile = profileLoader.load(profilePath, context.err());
        BatchCheckResult batch = new BatchCheckResult(jobFiles.stream()
                .map(path -> checkBatchFile(path, profile, context.err()))
                .filter(Objects::nonNull)
                .toList());
        renderBatch(batch, context.out(), language);
        return exitCodeFor(batch.worstVerdict());
    }

    private TerminalLanguage terminalLanguage(PrintWriter err) {
        String selected = languageOption;
        String source = "--lang";
        if (selected == null || selected.isBlank()) {
            selected = environment.get(LANGUAGE_ENVIRONMENT_VARIABLE);
            source = LANGUAGE_ENVIRONMENT_VARIABLE;
        }

        if (selected == null || selected.isBlank()) {
            return TerminalLanguage.EN;
        }

        String unsupportedValue = selected;
        String unsupportedSource = source;
        return TerminalLanguage.parse(selected)
                .orElseGet(() -> {
                    err.println("warning: unsupported " + unsupportedSource + " value '" + unsupportedValue
                            + "', falling back to English");
                    err.flush();
                    return TerminalLanguage.EN;
                });
    }

    private List<Path> listJobFiles() throws UsageException {
        try (Stream<Path> paths = Files.list(jobDirectory)) {
            return paths
                    .filter(Files::isRegularFile)
                    .filter(this::isJobFile)
                    .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                    .toList();
        } catch (IOException exception) {
            throw new UsageException(
                    "failed to list job directory: " + jobDirectory + " (" + exception.getMessage() + ")",
                    false
            );
        }
    }

    private boolean isJobFile(Path path) {
        String fileName = path.getFileName().toString();
        return fileName.endsWith(".md") || fileName.endsWith(".txt");
    }

    private BatchJobResult checkBatchFile(Path path, Profile profile, PrintWriter err) {
        try {
            CheckedJob checkedJob = jobCheckService.check(Files.readString(path, StandardCharsets.UTF_8), profile);
            return new BatchJobResult(path.getFileName().toString(), checkedJob.jobFile(), checkedJob.result());
        } catch (IOException | JobCheckService.JobCheckException exception) {
            err.println("warning: skipping " + path.getFileName() + ": " + exception.getMessage());
            err.flush();
            return null;
        }
    }

    public boolean isDebug() {
        return debug;
    }

    private String readJobText(Context context) throws UsageException {
        String text;
        if (jobPath != null) {
            text = readJobFile();
        } else {
            text = readStdin(context);
        }

        if (text.isBlank()) {
            throw new UsageException("empty job posting", false);
        }
        return text;
    }

    private String readJobFile() throws UsageException {
        if (!Files.isRegularFile(jobPath)) {
            throw new UsageException("job file not found: " + jobPath, false);
        }

        try {
            return Files.readString(jobPath, StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new UsageException(
                    "failed to read job file: " + jobPath + " (" + exception.getMessage() + ")",
                    false
            );
        }
    }

    private String readStdin(Context context) throws UsageException {
        if (!context.stdinIsPiped()) {
            throw new UsageException("missing input: pass --job <path> or pipe UTF-8 text to stdin.", true);
        }

        try {
            return new String(context.stdin().readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new UsageException("failed to read stdin: " + exception.getMessage(), false);
        }
    }

    private void render(CheckResult result, PrintWriter out, TerminalLanguage language) {
        String rendered = json
                ? JsonRenderer.render(result)
                : TerminalRenderer.render(result, language);
        out.print(rendered);
        out.flush();
    }

    private void renderBatch(BatchCheckResult batch, PrintWriter out, TerminalLanguage language) {
        String rendered = json
                ? BatchJsonRenderer.render(batch)
                : BatchTerminalRenderer.render(batch, terminalWidth(), !noHyperlink && System.console() != null, language);
        out.print(rendered);
        out.flush();
    }

    private int terminalWidth() {
        if (width != null) {
            return width;
        }

        String columns = environment.get("COLUMNS");
        if (columns == null || columns.isBlank()) {
            return DEFAULT_TERMINAL_WIDTH;
        }
        try {
            int parsed = Integer.parseInt(columns.strip());
            return parsed > 0 ? parsed : DEFAULT_TERMINAL_WIDTH;
        } catch (NumberFormatException exception) {
            return DEFAULT_TERMINAL_WIDTH;
        }
    }

    private int exitCodeFor(Verdict verdict) {
        return switch (verdict) {
            case APPLY -> AtsCheckCli.EXIT_APPLY;
            case REVIEW -> AtsCheckCli.EXIT_REVIEW;
            case SKIP -> AtsCheckCli.EXIT_SKIP;
        };
    }

    public record Context(
            InputStream stdin,
            boolean stdinIsPiped,
            PrintWriter out,
            PrintWriter err,
            CommandLine commandLine
    ) {
        public Context {
            Objects.requireNonNull(stdin, "stdin");
            Objects.requireNonNull(out, "out");
            Objects.requireNonNull(err, "err");
            Objects.requireNonNull(commandLine, "commandLine");
        }
    }

    private static final class UsageException extends Exception {
        private final boolean showUsage;

        private UsageException(String message, boolean showUsage) {
            super(message);
            this.showUsage = showUsage;
        }

        private boolean showUsage() {
            return showUsage;
        }
    }
}

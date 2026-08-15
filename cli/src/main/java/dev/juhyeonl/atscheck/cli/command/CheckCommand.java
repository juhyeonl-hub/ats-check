package dev.juhyeonl.atscheck.cli.command;

import dev.juhyeonl.atscheck.cli.AtsCheckCli;
import dev.juhyeonl.atscheck.cli.config.ProfileLoader;
import dev.juhyeonl.atscheck.cli.render.JsonRenderer;
import dev.juhyeonl.atscheck.cli.render.TerminalRenderer;
import dev.juhyeonl.atscheck.core.AtsChecker;
import dev.juhyeonl.atscheck.core.model.CheckResult;
import dev.juhyeonl.atscheck.core.model.JobPosting;
import dev.juhyeonl.atscheck.core.model.Profile;
import dev.juhyeonl.atscheck.core.model.Verdict;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import picocli.CommandLine;
import picocli.CommandLine.Option;

public final class CheckCommand {
    @Option(names = "--job", paramLabel = "<path>", description = "UTF-8 job posting text file.")
    private Path jobPath;

    @Option(names = "--profile", paramLabel = "<path>", description = "profile.yml path.")
    private Path profilePath;

    @Option(names = "--json", description = "Print a stable JSON result.")
    private boolean json;

    @Option(names = "--debug", description = "Print stack traces for unexpected internal errors.")
    private boolean debug;

    private final ProfileLoader profileLoader;

    public CheckCommand(ProfileLoader profileLoader) {
        this.profileLoader = Objects.requireNonNull(profileLoader, "profileLoader");
    }

    public int execute(Context context) {
        try {
            String jobText = readJobText(context);
            Profile profile = profileLoader.load(profilePath, context.err());
            CheckResult result = AtsChecker.check(JobPosting.fromText(jobText), profile);
            render(result, context.out());
            return exitCodeFor(result.verdict());
        } catch (ProfileLoader.ProfileLoadException exception) {
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

    private void render(CheckResult result, PrintWriter out) {
        String rendered = json
                ? JsonRenderer.render(result)
                : TerminalRenderer.render(result);
        out.print(rendered);
        out.flush();
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

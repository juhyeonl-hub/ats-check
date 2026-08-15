package dev.juhyeonl.atscheck.cli.command;

import dev.juhyeonl.atscheck.cli.AtsCheckCli;
import dev.juhyeonl.atscheck.cli.config.ProfileLoader;
import dev.juhyeonl.atscheck.cli.platform.BrowserOpener;
import dev.juhyeonl.atscheck.cli.store.JobFile;
import dev.juhyeonl.atscheck.cli.store.JobFileParser;
import dev.juhyeonl.atscheck.cli.store.JobStore;
import dev.juhyeonl.atscheck.core.AtsChecker;
import dev.juhyeonl.atscheck.core.model.CheckResult;
import dev.juhyeonl.atscheck.core.model.JobPosting;
import dev.juhyeonl.atscheck.core.model.Profile;
import dev.juhyeonl.atscheck.core.model.Verdict;
import java.io.PrintWriter;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Spec;
import picocli.CommandLine.Model.CommandSpec;

@Command(
        name = "open",
        mixinStandardHelpOptions = true,
        description = "Open saved job posting URLs.")
public final class OpenCommand implements Callable<Integer> {
    @Parameters(index = "0", arity = "0..1", paramLabel = "<pattern>", description = "Filename pattern.")
    private String pattern;

    @Option(names = "--all-apply", description = "Open every saved posting that evaluates to APPLY.")
    private boolean allApply;

    @Option(names = "--force", description = "Open more than 10 APPLY postings.")
    private boolean force;

    @Option(names = "--jobs-dir", paramLabel = "<path>", description = "Directory for saved job postings.")
    private Path jobsDir = Path.of("jobs");

    @Option(names = "--profile", paramLabel = "<path>", description = "profile.yml path.")
    private Path profilePath;

    @Spec
    private CommandSpec spec;

    private final ProfileLoader profileLoader;
    private final BrowserOpener browserOpener;

    public OpenCommand(ProfileLoader profileLoader, BrowserOpener browserOpener) {
        this.profileLoader = Objects.requireNonNull(profileLoader, "profileLoader");
        this.browserOpener = Objects.requireNonNull(browserOpener, "browserOpener");
    }

    @Override
    public Integer call() throws Exception {
        PrintWriter err = spec.commandLine().getErr();
        if (allApply && pattern != null) {
            err.println("use either --all-apply or <pattern>, not both");
            err.flush();
            return AtsCheckCli.EXIT_USAGE;
        }
        if (!allApply && pattern == null) {
            err.println("missing pattern: pass a filename pattern or --all-apply");
            err.flush();
            return AtsCheckCli.EXIT_USAGE;
        }

        JobStore store = new JobStore(jobsDir);
        return allApply ? openAllApply(store, err) : openSingle(store, err);
    }

    private int openSingle(JobStore store, PrintWriter err) throws Exception {
        List<Path> matches = store.findByFilenamePattern(pattern);
        if (matches.isEmpty()) {
            err.println("no matching job file: " + pattern);
            err.flush();
            return AtsCheckCli.EXIT_USAGE;
        }
        if (matches.size() > 1) {
            err.println("multiple matching job files:");
            printPaths(err, matches);
            err.flush();
            return AtsCheckCli.EXIT_USAGE;
        }

        Path path = matches.getFirst();
        JobFile jobFile = read(store, path, err);
        if (jobFile == null) {
            return AtsCheckCli.EXIT_USAGE;
        }
        String url = jobFile.frontMatter().url();
        if (url.isBlank()) {
            err.println("job file has no url: " + path);
            err.flush();
            return AtsCheckCli.EXIT_USAGE;
        }
        return openUrl(url, err);
    }

    private int openAllApply(JobStore store, PrintWriter err) throws Exception {
        Profile profile;
        try {
            profile = profileLoader.load(profilePath, err);
        } catch (ProfileLoader.ProfileLoadException exception) {
            err.println(exception.getMessage());
            err.flush();
            return AtsCheckCli.EXIT_USAGE;
        }

        List<OpenTarget> targets = new ArrayList<>();
        for (Path path : store.listJobFiles()) {
            JobFile jobFile = read(store, path, err);
            if (jobFile == null) {
                return AtsCheckCli.EXIT_USAGE;
            }
            CheckResult result = AtsChecker.check(JobPosting.fromText(jobFile.body()), profile);
            if (result.verdict() == Verdict.APPLY) {
                if (jobFile.frontMatter().url().isBlank()) {
                    err.println("skipping APPLY posting without url: " + path);
                } else {
                    targets.add(new OpenTarget(path, jobFile.frontMatter().url()));
                }
            }
        }

        if (targets.isEmpty()) {
            err.println("no APPLY postings with url found");
            err.flush();
            return AtsCheckCli.EXIT_APPLY;
        }
        if (targets.size() > 10 && !force) {
            err.println(targets.size() + " APPLY postings matched; refusing to open more than 10 (use --force)");
            printTargets(err, targets);
            err.flush();
            return AtsCheckCli.EXIT_USAGE;
        }

        err.println("opening " + targets.size() + " postings...");
        err.flush();
        for (OpenTarget target : targets) {
            int exitCode = openUrl(target.url(), err);
            if (exitCode != AtsCheckCli.EXIT_APPLY) {
                return exitCode;
            }
        }
        return AtsCheckCli.EXIT_APPLY;
    }

    private JobFile read(JobStore store, Path path, PrintWriter err) throws Exception {
        try {
            return store.read(path);
        } catch (JobFileParser.JobFileParseException exception) {
            err.println("invalid job file: " + path + " (" + exception.getMessage() + ")");
            err.flush();
            return null;
        }
    }

    private int openUrl(String url, PrintWriter err) {
        try {
            browserOpener.open(url);
            return AtsCheckCli.EXIT_APPLY;
        } catch (BrowserOpener.BrowserOpenException exception) {
            err.println("failed to open url: " + exception.getMessage());
            err.flush();
            return AtsCheckCli.EXIT_INTERNAL;
        }
    }

    private void printPaths(PrintWriter err, List<Path> paths) {
        for (Path path : paths) {
            err.println("  " + path);
        }
    }

    private void printTargets(PrintWriter err, List<OpenTarget> targets) {
        for (OpenTarget target : targets) {
            err.println("  " + target.path() + " -> " + target.url());
        }
    }

    private record OpenTarget(Path path, String url) {
    }
}

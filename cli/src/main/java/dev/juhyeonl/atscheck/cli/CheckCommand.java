package dev.juhyeonl.atscheck.cli;

import dev.juhyeonl.atscheck.core.AtsChecker;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Callable;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Command(
        name = "ats-check",
        mixinStandardHelpOptions = true,
        version = "ats-check 0.1.0-SNAPSHOT",
        description = "Read a job posting text and echo it.")
public final class CheckCommand implements Callable<Integer> {
    private static final int USAGE_EXIT_CODE = 64;

    @Option(names = "--job", paramLabel = "<path>", description = "UTF-8 job posting text file.")
    private Path jobPath;

    public static void main(String[] args) {
        int exitCode = new CommandLine(new CheckCommand()).execute(args);
        System.exit(exitCode);
    }

    @Override
    public Integer call() {
        String jobText;
        try {
            jobText = readJobText();
        } catch (InputException exception) {
            System.err.println(exception.getMessage());
            return USAGE_EXIT_CODE;
        }

        System.out.println(AtsChecker.echo(jobText));
        return CommandLine.ExitCode.OK;
    }

    private String readJobText() throws InputException {
        if (jobPath != null) {
            return readJobFile();
        }

        try {
            byte[] stdin = System.in.readAllBytes();
            if (stdin.length == 0) {
                throw new InputException("Missing input: pass --job <path> or pipe UTF-8 text to stdin.");
            }
            return new String(stdin, StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new InputException("Failed to read stdin: " + exception.getMessage());
        }
    }

    private String readJobFile() throws InputException {
        if (!Files.isRegularFile(jobPath)) {
            throw new InputException("Job file not found: " + jobPath);
        }

        try {
            return Files.readString(jobPath, StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new InputException("Failed to read job file: " + jobPath + " (" + exception.getMessage() + ")");
        }
    }

    private static final class InputException extends Exception {
        private InputException(String message) {
            super(message);
        }
    }
}

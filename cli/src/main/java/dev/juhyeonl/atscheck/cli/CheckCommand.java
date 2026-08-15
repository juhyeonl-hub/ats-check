package dev.juhyeonl.atscheck.cli;

import dev.juhyeonl.atscheck.cli.extract.PdfTextExtractor;
import dev.juhyeonl.atscheck.core.AtsChecker;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
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
    private static final int RESUME_PREVIEW_CHARS = 500;

    @Option(names = "--job", paramLabel = "<path>", description = "UTF-8 job posting text file.")
    private Path jobPath;

    @Option(names = "--resume", paramLabel = "<path>", description = "Resume PDF to extract text from.")
    private Path resumePath;

    public static void main(String[] args) {
        int exitCode = new CommandLine(new CheckCommand()).execute(args);
        System.exit(exitCode);
    }

    @Override
    public Integer call() {
        if (resumePath != null) {
            return extractResume();
        }

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

    private Integer extractResume() {
        if (jobPath != null) {
            System.err.println("Pass either --resume <path> or --job <path>, not both.");
            return USAGE_EXIT_CODE;
        }
        if (!Files.isRegularFile(resumePath)) {
            System.err.println("Resume file not found: " + resumePath);
            return USAGE_EXIT_CODE;
        }
        if (!isPdf(resumePath)) {
            System.err.println("Unsupported resume file type: " + resumePath + " (expected .pdf)");
            return USAGE_EXIT_CODE;
        }

        try {
            String text = new PdfTextExtractor().extract(resumePath);
            System.out.println(preview(text));
            return CommandLine.ExitCode.OK;
        } catch (IOException exception) {
            System.err.println("Failed to extract resume PDF: " + resumePath + " (" + exception.getMessage() + ")");
            return USAGE_EXIT_CODE;
        }
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

    private static boolean isPdf(Path path) {
        String fileName = path.getFileName().toString().toLowerCase(Locale.ROOT);
        return fileName.endsWith(".pdf");
    }

    private static String preview(String text) {
        if (text.length() <= RESUME_PREVIEW_CHARS) {
            return text;
        }
        return text.substring(0, RESUME_PREVIEW_CHARS);
    }

    private static final class InputException extends Exception {
        private InputException(String message) {
            super(message);
        }
    }
}

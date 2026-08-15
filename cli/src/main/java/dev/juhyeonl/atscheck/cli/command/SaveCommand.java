package dev.juhyeonl.atscheck.cli.command;

import dev.juhyeonl.atscheck.cli.AtsCheckCli;
import dev.juhyeonl.atscheck.cli.platform.ClipboardReader;
import dev.juhyeonl.atscheck.cli.store.FrontMatter;
import dev.juhyeonl.atscheck.cli.store.JobFile;
import dev.juhyeonl.atscheck.cli.store.JobStore;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.function.BooleanSupplier;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;
import picocli.CommandLine.Model.CommandSpec;

@Command(
        name = "save",
        mixinStandardHelpOptions = true,
        description = "Save a job posting into the jobs directory.")
public final class SaveCommand implements Callable<Integer> {
    @Option(names = "--url", paramLabel = "<url>", description = "Original job posting URL.")
    private String url;

    @Option(names = "--jobs-dir", paramLabel = "<path>", description = "Directory for saved job postings.")
    private Path jobsDir = Path.of("jobs");

    @Spec
    private CommandSpec spec;

    private final InputStream stdin;
    private final BooleanSupplier stdinIsPiped;
    private final ClipboardReader clipboardReader;
    private final Clock clock;

    public SaveCommand(
            InputStream stdin,
            BooleanSupplier stdinIsPiped,
            ClipboardReader clipboardReader,
            Clock clock
    ) {
        this.stdin = Objects.requireNonNull(stdin, "stdin");
        this.stdinIsPiped = Objects.requireNonNull(stdinIsPiped, "stdinIsPiped");
        this.clipboardReader = Objects.requireNonNull(clipboardReader, "clipboardReader");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public Integer call() throws Exception {
        PrintWriter out = spec.commandLine().getOut();
        PrintWriter err = spec.commandLine().getErr();
        String body = readBody(err);
        if (body.isBlank()) {
            err.println("empty job posting");
            err.flush();
            return AtsCheckCli.EXIT_USAGE;
        }

        ExtractedMetadata metadata = extractMetadata(body);
        OffsetDateTime savedAt = OffsetDateTime.now(clock);
        FrontMatter frontMatter = new FrontMatter(
                clean(url),
                metadata.company(),
                metadata.title(),
                savedAt.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
                FrontMatter.DEFAULT_STATUS,
                new LinkedHashMap<>()
        );
        Path savedPath = new JobStore(jobsDir).save(new JobFile(frontMatter, body));
        out.println(savedPath);
        out.flush();
        return AtsCheckCli.EXIT_APPLY;
    }

    private String readBody(PrintWriter err) throws IOException {
        if (stdinIsPiped.getAsBoolean()) {
            return readStdin();
        }

        try {
            return clipboardReader.read();
        } catch (ClipboardReader.ClipboardReadException exception) {
            err.println("clipboard unavailable (" + exception.getMessage() + "); reading from stdin");
            err.flush();
            return readStdin();
        }
    }

    private String readStdin() throws IOException {
        return new String(stdin.readAllBytes(), StandardCharsets.UTF_8);
    }

    private ExtractedMetadata extractMetadata(String body) {
        String title = "";
        String company = "";

        for (String line : body.split("\\R", -1)) {
            String stripped = line.strip();
            if (stripped.isBlank()) {
                continue;
            }
            if (title.isBlank()) {
                title = stripped;
            } else {
                if (isCompanyLine(stripped)) {
                    company = stripped;
                }
                break;
            }
        }

        return new ExtractedMetadata(title, company);
    }

    private boolean isCompanyLine(String line) {
        return line.length() <= 50 && !endsWithSentenceTerminator(line);
    }

    private boolean endsWithSentenceTerminator(String line) {
        char last = line.charAt(line.length() - 1);
        return last == '.' || last == '!' || last == '?' || last == ';' || last == '。';
    }

    private String clean(String value) {
        return value == null ? "" : value.strip();
    }

    private record ExtractedMetadata(String title, String company) {
    }
}

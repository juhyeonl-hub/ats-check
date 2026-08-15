package dev.juhyeonl.atscheck.cli.command;

import static org.assertj.core.api.Assertions.assertThat;

import dev.juhyeonl.atscheck.cli.AtsCheckCli;
import dev.juhyeonl.atscheck.cli.config.ProfileLoader;
import dev.juhyeonl.atscheck.cli.platform.BrowserOpener;
import dev.juhyeonl.atscheck.cli.platform.ClipboardReader;
import dev.juhyeonl.atscheck.cli.store.JobFile;
import dev.juhyeonl.atscheck.cli.store.JobStore;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

class SaveCommandTest {
    private static final Clock FIXED_CLOCK = Clock.fixed(
            Instant.parse("2026-08-15T11:32:00Z"),
            ZoneOffset.ofHours(3)
    );

    @TempDir
    private Path tempDir;

    @Test
    void savesPipedStdinAndReturnsExitZero() throws Exception {
        Path jobsDir = tempDir.resolve("jobs");

        Execution execution = run("Backend Engineer\nWolt\n", true, () -> "ignored", "--jobs-dir", jobsDir.toString());

        assertThat(execution.exitCode()).isEqualTo(0);
        assertThat(Files.exists(Path.of(execution.stdout().strip()))).isTrue();
    }

    @Test
    void readsFromClipboardWhenStdinIsNotPiped() throws Exception {
        Path jobsDir = tempDir.resolve("jobs");

        Execution execution = run("", false, () -> "Backend Engineer\nWolt\n", "--jobs-dir", jobsDir.toString());

        JobFile jobFile = new JobStore(jobsDir).read(Path.of(execution.stdout().strip()));
        assertThat(execution.exitCode()).isEqualTo(0);
        assertThat(jobFile.body()).isEqualTo("Backend Engineer\nWolt\n");
    }

    @Test
    void blankBodyReturnsUsageExit() {
        Execution execution = run(" \n\t", true, () -> "ignored");

        assertThat(execution.exitCode()).isEqualTo(64);
        assertThat(execution.stderr()).contains("empty job posting");
    }

    @Test
    void savesWithoutUrl() throws Exception {
        Path jobsDir = tempDir.resolve("jobs");

        Execution execution = runWithoutUrl("Backend Engineer\nWolt\n", true, () -> "ignored", "--jobs-dir", jobsDir.toString());

        JobFile jobFile = new JobStore(jobsDir).read(Path.of(execution.stdout().strip()));
        assertThat(jobFile.frontMatter().url()).isBlank();
    }

    @Test
    void slugifiesFilename() {
        Path jobsDir = tempDir.resolve("jobs");

        Execution execution = run(
                "Backend Engineer (Kotlin)\nWolt\n",
                true,
                () -> "ignored",
                "--jobs-dir",
                jobsDir.toString()
        );

        assertThat(Path.of(execution.stdout().strip()).getFileName().toString())
                .isEqualTo("wolt-backend-engineer-kotlin.md");
    }

    @Test
    void appendsSuffixWhenFilenameCollides() {
        Path jobsDir = tempDir.resolve("jobs");

        run("Backend Engineer\nWolt\n", true, () -> "ignored", "--jobs-dir", jobsDir.toString());
        Execution second = run("Backend Engineer\nWolt\n", true, () -> "ignored", "--jobs-dir", jobsDir.toString());

        assertThat(Path.of(second.stdout().strip()).getFileName().toString())
                .isEqualTo("wolt-backend-engineer-2.md");
    }

    @Test
    void storesBlankCompanyWhenCompanyCannotBeExtracted() throws Exception {
        Path jobsDir = tempDir.resolve("jobs");

        Execution execution = run(
                "Backend Engineer\nThis sentence is too company-like to be trusted.\n",
                true,
                () -> "ignored",
                "--jobs-dir",
                jobsDir.toString()
        );

        JobFile jobFile = new JobStore(jobsDir).read(Path.of(execution.stdout().strip()));
        assertThat(execution.exitCode()).isEqualTo(0);
        assertThat(jobFile.frontMatter().company()).isBlank();
        assertThat(Path.of(execution.stdout().strip()).getFileName().toString()).isEqualTo("backend-engineer.md");
    }

    private Execution run(String stdin, boolean stdinIsPiped, ClipboardReader clipboardReader, String... args) {
        return run(true, stdin, stdinIsPiped, clipboardReader, args);
    }

    private Execution runWithoutUrl(String stdin, boolean stdinIsPiped, ClipboardReader clipboardReader, String... args) {
        return run(false, stdin, stdinIsPiped, clipboardReader, args);
    }

    private Execution run(
            boolean includeUrl,
            String stdin,
            boolean stdinIsPiped,
            ClipboardReader clipboardReader,
            String... args
    ) {
        ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        ByteArrayOutputStream stderr = new ByteArrayOutputStream();
        CommandLine commandLine = AtsCheckCli.commandLine(
                new ByteArrayInputStream(stdin.getBytes(StandardCharsets.UTF_8)),
                () -> stdinIsPiped,
                new ProfileLoader(Map.of(), tempDir.resolve("home")),
                clipboardReader,
                new RecordingBrowserOpener(),
                FIXED_CLOCK,
                Map.of(),
                tempDir.resolve("home")
        );
        commandLine.setOut(writer(stdout));
        commandLine.setErr(writer(stderr));

        List<String> arguments = new ArrayList<>();
        arguments.add("save");
        if (includeUrl) {
            arguments.add("--url");
            arguments.add("https://example.com/job");
        }
        arguments.addAll(List.of(args));

        int exitCode = commandLine.execute(arguments.toArray(String[]::new));
        return new Execution(
                exitCode,
                stdout.toString(StandardCharsets.UTF_8),
                stderr.toString(StandardCharsets.UTF_8)
        );
    }

    private PrintWriter writer(ByteArrayOutputStream output) {
        return new PrintWriter(new OutputStreamWriter(output, StandardCharsets.UTF_8), true);
    }

    private static final class RecordingBrowserOpener implements BrowserOpener {
        @Override
        public void open(String url) {
        }
    }

    private record Execution(int exitCode, String stdout, String stderr) {
    }
}

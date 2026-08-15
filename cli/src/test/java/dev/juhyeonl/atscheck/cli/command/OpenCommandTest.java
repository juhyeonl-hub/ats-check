package dev.juhyeonl.atscheck.cli.command;

import static org.assertj.core.api.Assertions.assertThat;

import dev.juhyeonl.atscheck.cli.AtsCheckCli;
import dev.juhyeonl.atscheck.cli.config.ProfileLoader;
import dev.juhyeonl.atscheck.cli.platform.BrowserOpener;
import dev.juhyeonl.atscheck.cli.platform.ClipboardReader;
import dev.juhyeonl.atscheck.cli.store.FrontMatter;
import dev.juhyeonl.atscheck.cli.store.JobFile;
import dev.juhyeonl.atscheck.cli.store.JobStore;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

class OpenCommandTest {
    @TempDir
    private Path tempDir;

    @Test
    void opensExactlyOneFilenameMatch() throws Exception {
        Path jobsDir = tempDir.resolve("jobs");
        saveJob(jobsDir, "Wolt", "Backend Engineer", "https://example.com/wolt", applyBody());
        RecordingBrowserOpener opener = new RecordingBrowserOpener();

        Execution execution = run(opener, "open", "wolt", "--jobs-dir", jobsDir.toString());

        assertThat(execution.exitCode()).isEqualTo(0);
        assertThat(opener.opened()).containsExactly("https://example.com/wolt");
    }

    @Test
    void multipleFilenameMatchesReturnUsageExitAndListMatches() throws Exception {
        Path jobsDir = tempDir.resolve("jobs");
        saveJob(jobsDir, "Wolt", "Backend Engineer", "https://example.com/one", applyBody());
        saveJob(jobsDir, "Wolt", "Platform Engineer", "https://example.com/two", applyBody());
        RecordingBrowserOpener opener = new RecordingBrowserOpener();

        Execution execution = run(opener, "open", "wolt", "--jobs-dir", jobsDir.toString());

        assertThat(execution.exitCode()).isEqualTo(64);
        assertThat(execution.stderr())
                .contains("multiple matching job files")
                .contains("wolt-backend-engineer.md")
                .contains("wolt-platform-engineer.md");
        assertThat(opener.opened()).isEmpty();
    }

    @Test
    void noFilenameMatchReturnsUsageExit() {
        RecordingBrowserOpener opener = new RecordingBrowserOpener();

        Execution execution = run(opener, "open", "missing", "--jobs-dir", tempDir.resolve("jobs").toString());

        assertThat(execution.exitCode()).isEqualTo(64);
        assertThat(execution.stderr()).contains("no matching job file");
        assertThat(opener.opened()).isEmpty();
    }

    @Test
    void allApplyOpensOnlyApplyPostings() throws Exception {
        Path jobsDir = tempDir.resolve("jobs");
        saveJob(jobsDir, "A", "Apply", "https://example.com/apply", applyBody());
        saveJob(jobsDir, "B", "Skip", "https://example.com/skip", skipBody());
        RecordingBrowserOpener opener = new RecordingBrowserOpener();

        Execution execution = run(opener, "open", "--all-apply", "--jobs-dir", jobsDir.toString());

        assertThat(execution.exitCode()).isEqualTo(0);
        assertThat(execution.stderr()).contains("opening 1 postings...");
        assertThat(opener.opened()).containsExactly("https://example.com/apply");
    }

    @Test
    void allApplyOverTenListsMatchesAndDoesNotOpenWithoutForce() throws Exception {
        Path jobsDir = tempDir.resolve("jobs");
        for (int index = 1; index <= 11; index++) {
            saveJob(jobsDir, "Company" + index, "Backend Engineer", "https://example.com/" + index, applyBody());
        }
        RecordingBrowserOpener opener = new RecordingBrowserOpener();

        Execution execution = run(opener, "open", "--all-apply", "--jobs-dir", jobsDir.toString());

        assertThat(execution.exitCode()).isEqualTo(64);
        assertThat(execution.stderr())
                .contains("11 APPLY postings matched")
                .contains("https://example.com/11");
        assertThat(opener.opened()).isEmpty();
    }

    private Path saveJob(Path jobsDir, String company, String title, String url, String body) throws Exception {
        return new JobStore(jobsDir).save(new JobFile(
                new FrontMatter(url, company, title, "2026-08-15T14:32:00+03:00", "new", new LinkedHashMap<>()),
                body
        ));
    }

    private String applyBody() {
        return """
                Backend Engineer
                Requirements:
                Java and Kotlin.
                """;
    }

    private String skipBody() {
        return """
                Backend Engineer
                Requirements:
                Fluent Finnish is required.
                Java.
                """;
    }

    private Execution run(RecordingBrowserOpener opener, String... args) {
        ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        ByteArrayOutputStream stderr = new ByteArrayOutputStream();
        CommandLine commandLine = AtsCheckCli.commandLine(
                new ByteArrayInputStream(new byte[0]),
                () -> false,
                new ProfileLoader(Map.of(), tempDir.resolve("home")),
                failingClipboard(),
                opener,
                Clock.systemUTC(),
                Map.of(),
                tempDir.resolve("home")
        );
        commandLine.setOut(writer(stdout));
        commandLine.setErr(writer(stderr));

        int exitCode = commandLine.execute(args);
        return new Execution(
                exitCode,
                stdout.toString(StandardCharsets.UTF_8),
                stderr.toString(StandardCharsets.UTF_8)
        );
    }

    private ClipboardReader failingClipboard() {
        return () -> {
            throw new ClipboardReader.ClipboardReadException("not used");
        };
    }

    private PrintWriter writer(ByteArrayOutputStream output) {
        return new PrintWriter(new OutputStreamWriter(output, StandardCharsets.UTF_8), true);
    }

    private static final class RecordingBrowserOpener implements BrowserOpener {
        private final List<String> opened = new java.util.ArrayList<>();

        @Override
        public void open(String url) {
            opened.add(url);
        }

        private List<String> opened() {
            return opened;
        }
    }

    private record Execution(int exitCode, String stdout, String stderr) {
    }
}

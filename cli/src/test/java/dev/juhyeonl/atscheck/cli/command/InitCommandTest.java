package dev.juhyeonl.atscheck.cli.command;

import static org.assertj.core.api.Assertions.assertThat;

import dev.juhyeonl.atscheck.cli.AtsCheckCli;
import dev.juhyeonl.atscheck.cli.config.ProfileLoader;
import dev.juhyeonl.atscheck.cli.platform.BrowserOpener;
import dev.juhyeonl.atscheck.cli.platform.ClipboardReader;
import dev.juhyeonl.atscheck.core.model.Profile;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

class InitCommandTest {
    @TempDir
    private Path tempDir;

    @Test
    void createsProfileWhenMissingAndReturnsExitZero() throws Exception {
        Execution execution = run(Map.of(), "init");
        Path profile = tempDir.resolve("home").resolve(".config").resolve("ats-check").resolve("profile.yml");

        assertThat(execution.exitCode()).isEqualTo(0);
        assertThat(execution.stdout()).contains(profile.toString());
        assertThat(Files.readString(profile, StandardCharsets.UTF_8)).contains("years_experience: 2");
    }

    @Test
    void existingProfileReturnsUsageExitAndDoesNotOverwrite() throws Exception {
        Path profile = tempDir.resolve("home").resolve(".config").resolve("ats-check").resolve("profile.yml");
        Files.createDirectories(profile.getParent());
        Files.writeString(profile, "skills: [custom]\n", StandardCharsets.UTF_8);

        Execution execution = run(Map.of(), "init");

        assertThat(execution.exitCode()).isEqualTo(64);
        assertThat(Files.readString(profile, StandardCharsets.UTF_8)).isEqualTo("skills: [custom]\n");
        assertThat(execution.stderr()).contains("profile already exists");
    }

    @Test
    void forceOverwritesExistingProfile() throws Exception {
        Path profile = tempDir.resolve("home").resolve(".config").resolve("ats-check").resolve("profile.yml");
        Files.createDirectories(profile.getParent());
        Files.writeString(profile, "skills: [custom]\n", StandardCharsets.UTF_8);

        Execution execution = run(Map.of(), "init", "--force");

        assertThat(execution.exitCode()).isEqualTo(0);
        assertThat(Files.readString(profile, StandardCharsets.UTF_8))
                .contains("# ats-check profile")
                .contains("spring boot");
    }

    @Test
    void generatedProfileCanBeReadByProfileLoader() throws Exception {
        Path configHome = tempDir.resolve("xdg");
        Map<String, String> environment = Map.of("XDG_CONFIG_HOME", configHome.toString());

        Execution execution = run(environment, "init");
        Path profilePath = configHome.resolve("ats-check").resolve("profile.yml");
        Profile profile = new ProfileLoader(environment, tempDir.resolve("home"))
                .load(profilePath, writer(new ByteArrayOutputStream()));

        assertThat(execution.exitCode()).isEqualTo(0);
        assertThat(profile.yearsExperience()).isEqualTo(2);
        assertThat(profile.skills()).contains("java", "spring boot");
    }

    private Execution run(Map<String, String> environment, String... args) {
        ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        ByteArrayOutputStream stderr = new ByteArrayOutputStream();
        CommandLine commandLine = AtsCheckCli.commandLine(
                new ByteArrayInputStream(new byte[0]),
                () -> false,
                new ProfileLoader(environment, tempDir.resolve("home")),
                failingClipboard(),
                noopBrowser(),
                Clock.systemUTC(),
                environment,
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

    private BrowserOpener noopBrowser() {
        return url -> {
        };
    }

    private PrintWriter writer(ByteArrayOutputStream output) {
        return new PrintWriter(new OutputStreamWriter(output, StandardCharsets.UTF_8), true);
    }

    private record Execution(int exitCode, String stdout, String stderr) {
    }
}

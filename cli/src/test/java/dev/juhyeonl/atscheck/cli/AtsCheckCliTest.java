package dev.juhyeonl.atscheck.cli;

import static org.assertj.core.api.Assertions.assertThat;

import dev.juhyeonl.atscheck.cli.config.ProfileLoader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import picocli.CommandLine;

class AtsCheckCliTest {
    private static final String APPLY_JOB = """
            Backend Engineer
            Requirements:
            Java and Kotlin.
            """;
    private static final String REVIEW_JOB = """
            Backend Engineer
            Requirements:
            Working knowledge of Finnish.
            Java.
            """;
    private static final String SKIP_JOB = """
            Backend Engineer
            Requirements:
            Fluent Finnish is required.
            Java.
            """;

    @TempDir
    private Path tempDir;

    @Test
    void applyJobReturnsExitZero() {
        Execution execution = run(APPLY_JOB, true);

        assertThat(execution.exitCode()).isEqualTo(0);
    }

    @Test
    void reviewJobReturnsExitOne() {
        Execution execution = run(REVIEW_JOB, true);

        assertThat(execution.exitCode()).isEqualTo(1);
    }

    @Test
    void skipJobReturnsExitTwo() {
        Execution execution = run(SKIP_JOB, true);

        assertThat(execution.exitCode()).isEqualTo(2);
    }

    @Test
    void missingJobFileReturnsUsageExit() {
        Path missing = tempDir.resolve("missing-job.txt");

        Execution execution = run("", false, "--job", missing.toString());

        assertThat(execution.exitCode()).isEqualTo(64);
        assertThat(execution.stderr()).contains("job file not found");
    }

    @Test
    void noInputReturnsUsageExitAndUsageMessage() {
        Execution execution = run("", false);

        assertThat(execution.exitCode()).isEqualTo(64);
        assertThat(execution.stderr())
                .contains("missing input")
                .contains("Usage:");
    }

    @Test
    void blankInputReturnsUsageExit() {
        Execution execution = run(" \n\t", true);

        assertThat(execution.exitCode()).isEqualTo(64);
        assertThat(execution.stderr()).contains("empty job posting");
    }

    @Test
    void versionReturnsExitZero() {
        Execution execution = run("", false, "--version");

        assertThat(execution.exitCode()).isEqualTo(0);
        assertThat(execution.stdout()).contains("ats-check 0.1.0-SNAPSHOT");
    }

    @Test
    void missingExplicitProfileReturnsUsageExit() {
        Path missing = tempDir.resolve("missing-profile.yml");

        Execution execution = run(APPLY_JOB, true, "--profile", missing.toString());

        assertThat(execution.exitCode()).isEqualTo(64);
        assertThat(execution.stderr()).contains("profile file not found");
    }

    @Test
    void jsonOutputIsValidJsonWithExpectedVerdict() {
        Execution execution = run(SKIP_JOB, true, "--json");

        Map<String, Object> parsed = parseYamlMap(execution.stdout());
        assertThat(execution.exitCode()).isEqualTo(2);
        assertThat(parsed).containsEntry("verdict", "SKIP");
    }

    @Test
    void jsonModeWritesOnlyJsonToStdout() {
        Execution execution = run(APPLY_JOB, true, "--json");

        assertThat(execution.stdout().strip()).startsWith("{").endsWith("}");
        assertThat(execution.stdout()).doesNotContain("no profile found");
        assertThat(execution.stderr()).contains("no profile found");
    }

    @Test
    void terminalOutputContainsVerdictAndStatusSymbol() {
        Execution execution = run(SKIP_JOB, true);

        assertThat(execution.stdout())
                .contains("VERDICT: SKIP")
                .contains("\u2717 Language");
    }

    @Test
    void failFindingPrintsQuotedEvidence() {
        Execution execution = run(SKIP_JOB, true);

        assertThat(execution.stdout()).contains("\"Fluent Finnish is required.\"");
    }

    @Test
    void profileYearsExperienceAffectsVerdict() throws Exception {
        Path profile = tempDir.resolve("profile.yml");
        Files.writeString(profile, "years_experience: 3\nskills: [java]\n", StandardCharsets.UTF_8);

        Execution execution = run("""
                Backend Engineer
                Requirements:
                3+ years with Java.
                """, true, "--profile", profile.toString());

        assertThat(execution.exitCode()).isEqualTo(0);
        assertThat(execution.stdout()).contains("3+ years (profile: 3, tolerance: 1)");
    }

    @Test
    void invalidMaxSeniorityWarnsAndUsesDefault() throws Exception {
        Path profile = tempDir.resolve("profile.yml");
        Files.writeString(profile, "max_seniority: wizard\nskills: [java]\n", StandardCharsets.UTF_8);

        Execution execution = run("""
                Lead Backend Engineer
                Requirements:
                Java.
                """, true, "--profile", profile.toString());

        assertThat(execution.exitCode()).isEqualTo(0);
        assertThat(execution.stderr()).contains("warning: invalid max_seniority");
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseYamlMap(String text) {
        Yaml yaml = new Yaml(new SafeConstructor(new LoaderOptions()));
        return (Map<String, Object>) yaml.load(text);
    }

    private Execution run(String stdin, boolean stdinIsPiped, String... args) {
        ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        ByteArrayOutputStream stderr = new ByteArrayOutputStream();
        CommandLine commandLine = AtsCheckCli.commandLine(
                new ByteArrayInputStream(stdin.getBytes(StandardCharsets.UTF_8)),
                () -> stdinIsPiped,
                new ProfileLoader(Map.of(), tempDir.resolve("home"))
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

    private PrintWriter writer(ByteArrayOutputStream output) {
        return new PrintWriter(new OutputStreamWriter(output, StandardCharsets.UTF_8), true);
    }

    private record Execution(int exitCode, String stdout, String stderr) {
    }
}

package dev.juhyeonl.atscheck.cli;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeFalse;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import dev.juhyeonl.atscheck.cli.config.ProfileLoader;
import dev.juhyeonl.atscheck.cli.render.DisplayWidth;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import picocli.CommandLine;

class BatchCheckCommandTest {
    private static final String ELLIPSIS = "\u2026";
    private static final String APPLY_FULL_MATCH = """
            ---
            company: Ravogen
            title: Fullstack Developer
            url: https://ravogen.fi/careers/12
            status: new
            ---

            Fullstack Developer
            Requirements:
            Java.
            """;
    private static final String APPLY_WITH_GAP = """
            ---
            company: Wolt
            title: Backend Engineer
            url: https://linkedin.com/jobs/view/333
            status: new
            ---

            Backend Engineer
            Requirements:
            Java, Kotlin, k8s.
            """;
    private static final String REVIEW_JOB = """
            ---
            company: Solita
            title: Node.js Developer
            url: https://solita.fi/careers/456
            status: new
            ---

            Node.js Developer
            Requirements:
            Working knowledge of Finnish.
            Java.
            """;
    private static final String SKIP_JOB = """
            ---
            company: Alten
            title: Java Developer
            url: https://linkedin.com/jobs/view/111
            status: new
            ---

            Java Developer
            Requirements:
            Fluent Finnish is required.
            Java.
            """;

    @TempDir
    private Path tempDir;

    private Path profile;

    @BeforeEach
    void setUp() throws Exception {
        profile = writeProfile("skills: [java]\n");
    }

    @Test
    void batchWithApplyReviewAndSkipPrintsThreeRowsAndReturnsSkipExit() throws Exception {
        Path jobs = jobsDir();
        writeJob(jobs, "01-apply.md", APPLY_FULL_MATCH);
        writeJob(jobs, "02-review.md", REVIEW_JOB);
        writeJob(jobs, "03-skip.md", SKIP_JOB);

        Execution execution = runBatch(jobs);

        assertThat(execution.exitCode()).isEqualTo(2);
        assertThat(dataRows(execution.stdout())).hasSize(3);
        assertThat(execution.stdout()).contains("APPLY", "REVIEW", "SKIP");
    }

    @Test
    void allApplyReturnsExitZero() throws Exception {
        Path jobs = jobsDir();
        writeJob(jobs, "01-ravogen.md", APPLY_FULL_MATCH);
        writeJob(jobs, "02-wolt.md", APPLY_WITH_GAP);

        Execution execution = runBatch(jobs);

        assertThat(execution.exitCode()).isEqualTo(0);
    }

    @Test
    void applyAndReviewReturnsReviewExit() throws Exception {
        Path jobs = jobsDir();
        writeJob(jobs, "01-apply.md", APPLY_FULL_MATCH);
        writeJob(jobs, "02-review.md", REVIEW_JOB);

        Execution execution = runBatch(jobs);

        assertThat(execution.exitCode()).isEqualTo(1);
    }

    @Test
    void emptyDirectoryReturnsExitZeroWithMessage() throws Exception {
        Path jobs = jobsDir();

        Execution execution = runBatch(jobs);

        assertThat(execution.exitCode()).isEqualTo(0);
        assertThat(execution.stdout()).contains("No job files found");
    }

    @Test
    void missingDirectoryReturnsUsageExit() {
        Path missing = tempDir.resolve("missing-jobs");

        Execution execution = run("--job-dir", missing.toString(), "--profile", profile.toString());

        assertThat(execution.exitCode()).isEqualTo(64);
        assertThat(execution.stderr()).contains("job directory not found");
    }

    @Test
    void jobAndJobDirTogetherReturnUsageExit() throws Exception {
        Path jobs = jobsDir();
        Path jobFile = writeJob(jobs, "one.md", APPLY_FULL_MATCH);

        Execution execution = run(
                "--job", jobFile.toString(),
                "--job-dir", jobs.toString(),
                "--profile", profile.toString()
        );

        assertThat(execution.exitCode()).isEqualTo(64);
        assertThat(execution.stderr()).contains("cannot use --job and --job-dir together");
    }

    @Test
    void jobFilesAreSortedByFilenameAscending() throws Exception {
        Path jobs = jobsDir();
        writeJob(jobs, "b-job.md", jobWithCompany("Beta"));
        writeJob(jobs, "a-job.md", jobWithCompany("Alpha"));

        Execution execution = runBatch(jobs);

        assertThat(execution.stdout().indexOf("Alpha")).isLessThan(execution.stdout().indexOf("Beta"));
    }

    @Test
    void fileWithoutFrontMatterUsesFilenameAsCompany() throws Exception {
        Path jobs = jobsDir();
        writeJob(jobs, "plain-job.txt", """
                Backend Engineer
                Requirements:
                Java.
                """);

        Execution execution = runBatch(jobs);

        assertThat(dataRows(execution.stdout()).getFirst()).contains("plain-job.txt");
    }

    @Test
    void terminalUrlOmitsScheme() throws Exception {
        Path jobs = jobsDir();
        writeJob(jobs, "wolt.md", APPLY_WITH_GAP);

        Execution execution = runBatch(jobs);

        assertThat(execution.stdout()).contains("linkedin.com/jobs/view/333");
        assertThat(execution.stdout()).doesNotContain("https://linkedin.com/jobs/view/333");
    }

    @Test
    void defaultWidthPrintsUrlColumn() throws Exception {
        Path jobs = jobsDir();
        writeJob(jobs, "ravogen.md", APPLY_FULL_MATCH);

        Execution execution = runBatch(jobs);

        assertThat(execution.stdout().lines().findFirst().orElseThrow()).contains("URL");
        assertThat(execution.stdout()).contains("ravogen.fi/careers/12");
    }

    @Test
    void companyLongerThanSixteenCharactersIsTruncatedWithEllipsis() throws Exception {
        Path jobs = jobsDir();
        writeJob(jobs, "long-company.md", jobWithFrontMatter(
                "ABCDEFGHIJKLMNOPQ",
                "Developer",
                "https://example.com/jobs/company"
        ));

        Execution execution = runBatch(jobs);

        assertThat(dataRows(execution.stdout()).getFirst())
                .contains("ABCDEFGHIJKLMNO" + ELLIPSIS)
                .doesNotContain("ABCDEFGHIJKLMNOPQ");
    }

    @Test
    void titleLongerThanTwentyFourCharactersIsTruncatedWithEllipsis() throws Exception {
        Path jobs = jobsDir();
        writeJob(jobs, "long-title.md", jobWithFrontMatter(
                "Acme",
                "ABCDEFGHIJKLMNOPQRSTUVWXY",
                "https://example.com/jobs/title"
        ));

        Execution execution = runBatch(jobs);

        assertThat(dataRows(execution.stdout()).getFirst())
                .contains("ABCDEFGHIJKLMNOPQRSTUVW" + ELLIPSIS)
                .doesNotContain("ABCDEFGHIJKLMNOPQRSTUVWXY");
    }

    @Test
    void longCompanyAndTitleStillLeaveUrlAtDefaultWidth() throws Exception {
        Path jobs = jobsDir();
        writeJob(jobs, "long-both.md", jobWithFrontMatter(
                "ExtremelyLongCompanyName",
                "Plain Posting Without Frontmatter",
                "https://example.com/jobs/long-title"
        ));

        Execution execution = runBatch(jobs);

        assertThat(execution.stdout().lines().findFirst().orElseThrow()).contains("URL");
        assertThat(execution.stdout()).contains("example.com/jobs/long-title");
    }

    @Test
    void narrowWidthOmitsUrlColumnWithoutStandaloneEllipsisResidue() throws Exception {
        Path jobs = jobsDir();
        writeJob(jobs, "long-both.md", jobWithFrontMatter(
                "ExtremelyLongCompanyName",
                "Plain Posting Without Frontmatter",
                "https://example.com/jobs/long-title-that-would-not-fit"
        ));

        Execution execution = runBatch(jobs, "--width", "60");

        String header = execution.stdout().lines().findFirst().orElseThrow();
        assertThat(header).contains("REASON");
        assertThat(header).doesNotContain("URL", ELLIPSIS);
        assertThat(execution.stdout()).doesNotContain("example.com/jobs");
        assertThat(tableLines(execution.stdout()))
                .noneMatch(line -> line.matches(".*  " + ELLIPSIS + "\\s*$"));
    }

    @Test
    void missingUrlLeavesBlankUrlCellWhenOtherRowsHaveUrls() throws Exception {
        Path jobs = jobsDir();
        writeJob(jobs, "01-no-url.md", jobWithoutUrl("NoUrlCo", "Backend Engineer"));
        writeJob(jobs, "02-with-url.md", APPLY_FULL_MATCH);

        Execution execution = runBatch(jobs);

        assertThat(execution.stdout().lines().findFirst().orElseThrow()).contains("URL");
        String noUrlLine = lineContaining(execution.stdout(), "NoUrlCo");
        int reasonEnd = noUrlLine.indexOf("full match") + "full match".length();
        assertThat(noUrlLine.substring(reasonEnd).strip()).isEmpty();
    }

    @Test
    void allJobsWithoutUrlsOmitUrlColumn() throws Exception {
        Path jobs = jobsDir();
        writeJob(jobs, "01-no-url.md", jobWithoutUrl("NoUrlOne", "Backend Engineer"));
        writeJob(jobs, "02-no-url.md", jobWithoutUrl("NoUrlTwo", "Java Developer"));

        Execution execution = runBatch(jobs);

        assertThat(execution.stdout().lines().findFirst().orElseThrow()).doesNotContain("URL");
    }

    @Test
    void urlDisplayOmitsSchemeRegression() throws Exception {
        Path jobs = jobsDir();
        writeJob(jobs, "wolt.md", APPLY_WITH_GAP);

        Execution execution = runBatch(jobs);

        assertThat(execution.stdout()).contains("linkedin.com/jobs/view/333");
        assertThat(execution.stdout()).doesNotContain("https://linkedin.com/jobs/view/333");
    }

    @Test
    void reasonsUseFirstFailFirstReviewMissingRequiredAndFullMatch() throws Exception {
        Path jobs = jobsDir();
        writeJob(jobs, "01-skip.md", SKIP_JOB);
        writeJob(jobs, "02-gap.md", APPLY_WITH_GAP);
        writeJob(jobs, "03-full.md", APPLY_FULL_MATCH);
        writeJob(jobs, "04-review.md", REVIEW_JOB);

        Execution execution = runBatch(jobs);

        assertThat(lineContaining(execution.stdout(), "Alten")).contains("Finnish required");
        assertThat(lineContaining(execution.stdout(), "Wolt")).contains("missing: Kotlin");
        assertThat(lineContaining(execution.stdout(), "Ravogen")).contains("full match");
        assertThat(lineContaining(execution.stdout(), "Solita")).contains("Finnish - ambiguous");
    }

    @Test
    void summaryLineIncludesAllNonZeroCounts() throws Exception {
        Path jobs = jobsDir();
        writeJob(jobs, "01-apply.md", APPLY_FULL_MATCH);
        writeJob(jobs, "02-review.md", REVIEW_JOB);
        writeJob(jobs, "03-skip.md", SKIP_JOB);

        Execution execution = runBatch(jobs);

        assertThat(lastNonBlankLine(execution.stdout())).isEqualTo("3 jobs \u00b7 1 apply \u00b7 1 review \u00b7 1 skip");
    }

    @Test
    void summaryLineOmitsZeroCounts() throws Exception {
        Path jobs = jobsDir();
        writeJob(jobs, "01-apply.md", APPLY_FULL_MATCH);
        writeJob(jobs, "02-apply.md", jobWithCompany("Wolt"));

        Execution execution = runBatch(jobs);

        assertThat(lastNonBlankLine(execution.stdout())).isEqualTo("2 jobs \u00b7 2 apply");
    }

    @Test
    void koreanBatchOutputTranslatesHeadersVerdictsReasonsAndSummary() throws Exception {
        Path jobs = jobsDir();
        writeJob(jobs, "01-apply.md", APPLY_FULL_MATCH);
        writeJob(jobs, "02-review.md", REVIEW_JOB);
        writeJob(jobs, "03-skip.md", SKIP_JOB);

        Execution execution = runBatch(jobs, "--lang", "ko", "--width", "100", "--no-hyperlink");

        String header = execution.stdout().lines().findFirst().orElseThrow();
        assertThat(header).contains("판정", "회사", "직무", "사유", "URL");
        assertThat(header).doesNotContain("VERDICT", "COMPANY", "TITLE", "REASON");
        assertThat(execution.stdout())
                .contains("지원 가능", "확인 필요", "제외")
                .contains("모두 충족", "핀란드어 - 요구 여부 불명확", "핀란드어 필수");
        assertThat(lastNonBlankLine(execution.stdout()))
                .isEqualTo("공고 3건 \u00b7 지원 1 \u00b7 확인 1 \u00b7 제외 1");
        assertThat(List.of(
                displayIndexOf(header, "회사"),
                displayIndexOf(lineContaining(execution.stdout(), "Ravogen"), "Ravogen"),
                displayIndexOf(lineContaining(execution.stdout(), "Solita"), "Solita"),
                displayIndexOf(lineContaining(execution.stdout(), "Alten"), "Alten")
        )).containsOnly(11);
    }

    @Test
    void koreanBatchNarrowWidthTruncatesByDisplayWidth() throws Exception {
        Path jobs = jobsDir();
        writeJob(jobs, "long-korean.md", jobWithFrontMatter(
                "가나다라마바사아자차카타",
                "백엔드엔지니어직무제목길게",
                "https://example.com/jobs/korean-long-title"
        ));

        Execution execution = runBatch(jobs, "--lang", "ko", "--width", "42", "--no-hyperlink");

        assertThat(execution.stdout().lines().findFirst().orElseThrow()).doesNotContain("URL");
        assertThat(koreanTableLines(execution.stdout()))
                .allSatisfy(line -> assertThat(DisplayWidth.width(line)).isLessThanOrEqualTo(42));
        assertThat(execution.stdout())
                .contains(ELLIPSIS)
                .doesNotContain("가나다라마바사아자차카타", "백엔드엔지니어직무제목길게");
    }

    @Test
    void noHyperlinkOptionDisablesOsc8Output() throws Exception {
        Path jobs = jobsDir();
        writeJob(jobs, "wolt.md", APPLY_WITH_GAP);

        Execution execution = runBatch(jobs, "--no-hyperlink");

        assertThat(execution.stdout()).doesNotContain("\033]8");
    }

    @Test
    void jsonBatchOutputNeverContainsOsc8Output() throws Exception {
        Path jobs = jobsDir();
        writeJob(jobs, "wolt.md", APPLY_WITH_GAP);

        Execution execution = runBatch(jobs, "--json");

        assertThat(execution.stdout()).doesNotContain("\033]8");
    }

    @Test
    void jsonBatchItemUsesSingleCheckJsonResultStructure() throws Exception {
        Path jobs = jobsDir();
        Path job = writeJob(jobs, "wolt.md", """
                ---
                company: Wolt
                title: Backend Engineer
                url: https://linkedin.com/jobs/view/333
                status: new
                source: linkedin
                ---

                Backend Engineer
                Requirements:
                Java, Kotlin, k8s.
                """);

        Execution single = run("--job", job.toString(), "--profile", profile.toString(), "--json");
        Execution batch = runBatch(jobs, "--json");
        Map<String, Object> singleJson = parseJsonObject(single.stdout());
        Map<String, Object> batchJob = firstBatchJob(batch);

        assertThat(batchJob)
                .containsKeys(
                        "file",
                        "company",
                        "title",
                        "url",
                        "status",
                        "metadata",
                        "verdict",
                        "stoppedAtHardFilter",
                        "findings",
                        "skillGap"
                );
        assertThat(batchJob.get("verdict")).isEqualTo(singleJson.get("verdict"));
        assertThat(batchJob.get("stoppedAtHardFilter")).isEqualTo(singleJson.get("stoppedAtHardFilter"));
        assertThat(batchJob.get("findings")).isEqualTo(singleJson.get("findings"));
        assertThat(batchJob.get("skillGap")).isEqualTo(singleJson.get("skillGap"));
        assertThat(metadata(batchJob)).containsEntry("source", "linkedin");
    }

    @Test
    void narrowWidthOmitsUrlColumnFirst() throws Exception {
        Path jobs = jobsDir();
        writeJob(jobs, "wolt.md", """
                ---
                company: Wolt
                title: Backend Engineer
                url: https://linkedin.com/jobs/view/333333333333333333333333333
                status: new
                ---

                Backend Engineer
                Requirements:
                Java, Kotlin, k8s.
                """);

        Execution execution = runBatch(jobs, "--width", "60");

        assertThat(execution.stdout().lines().findFirst().orElseThrow()).doesNotContain("URL");
        assertThat(execution.stdout()).doesNotContain("linkedin.com/jobs/view");
    }

    @Test
    void unreadableFileIsSkippedAndOtherFilesAreProcessed() throws Exception {
        assumeTrue(FileSystems.getDefault().supportedFileAttributeViews().contains("posix"));
        Path jobs = jobsDir();
        writeJob(jobs, "01-readable.md", APPLY_FULL_MATCH);
        Path unreadable = writeJob(jobs, "02-unreadable.md", APPLY_WITH_GAP);
        Set<PosixFilePermission> originalPermissions = Files.getPosixFilePermissions(unreadable);
        Files.setPosixFilePermissions(unreadable, Set.of());

        Execution execution;
        try {
            assumeFalse(Files.isReadable(unreadable));
            execution = runBatch(jobs);
        } finally {
            Files.setPosixFilePermissions(unreadable, originalPermissions);
        }

        assertThat(execution.exitCode()).isEqualTo(0);
        assertThat(lastNonBlankLine(execution.stdout())).isEqualTo("1 jobs \u00b7 1 apply");
        assertThat(execution.stderr()).contains("warning: skipping 02-unreadable.md");
    }

    @Test
    void batchAndSingleCheckReturnSameVerdictForSameFile() throws Exception {
        Path jobs = jobsDir();
        Path job = writeJob(jobs, "review.md", REVIEW_JOB);

        Execution single = run("--job", job.toString(), "--profile", profile.toString(), "--json");
        Execution batch = runBatch(jobs, "--json");

        assertThat(firstBatchJob(batch).get("verdict")).isEqualTo(parseJsonObject(single.stdout()).get("verdict"));
    }

    private Path jobsDir() throws Exception {
        Path jobs = tempDir.resolve("jobs");
        Files.createDirectories(jobs);
        return jobs;
    }

    private Path writeProfile(String text) throws Exception {
        Path path = tempDir.resolve("profile.yml");
        Files.writeString(path, text, StandardCharsets.UTF_8);
        return path;
    }

    private Path writeJob(Path jobs, String fileName, String text) throws Exception {
        Path path = jobs.resolve(fileName);
        Files.writeString(path, text, StandardCharsets.UTF_8);
        return path;
    }

    private String jobWithCompany(String company) {
        return """
                ---
                company: %s
                title: Backend Engineer
                url: https://example.com/%s
                status: new
                ---

                Backend Engineer
                Requirements:
                Java.
                """.formatted(company, company.toLowerCase());
    }

    private String jobWithFrontMatter(String company, String title, String url) {
        return """
                ---
                company: %s
                title: %s
                url: %s
                status: new
                ---

                %s
                Requirements:
                Java.
                """.formatted(company, title, url, title);
    }

    private String jobWithoutUrl(String company, String title) {
        return """
                ---
                company: %s
                title: %s
                status: new
                ---

                %s
                Requirements:
                Java.
                """.formatted(company, title, title);
    }

    private Execution runBatch(Path jobs, String... extraArgs) {
        String[] args = new String[4 + extraArgs.length];
        args[0] = "--job-dir";
        args[1] = jobs.toString();
        args[2] = "--profile";
        args[3] = profile.toString();
        System.arraycopy(extraArgs, 0, args, 4, extraArgs.length);
        return run(args);
    }

    private Execution run(String... args) {
        ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        ByteArrayOutputStream stderr = new ByteArrayOutputStream();
        CommandLine commandLine = AtsCheckCli.commandLine(
                new ByteArrayInputStream(new byte[0]),
                () -> false,
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

    private List<String> dataRows(String stdout) {
        return stdout.lines()
                .filter(line -> line.startsWith("APPLY") || line.startsWith("REVIEW") || line.startsWith("SKIP"))
                .toList();
    }

    private List<String> tableLines(String stdout) {
        return stdout.lines()
                .filter(line -> line.startsWith("VERDICT")
                        || line.startsWith("APPLY")
                        || line.startsWith("REVIEW")
                        || line.startsWith("SKIP"))
                .toList();
    }

    private List<String> koreanTableLines(String stdout) {
        return stdout.lines()
                .filter(line -> line.startsWith("판정")
                        || line.startsWith("지원")
                        || line.startsWith("확인")
                        || line.startsWith("제외"))
                .toList();
    }

    private String lineContaining(String stdout, String text) {
        return stdout.lines()
                .filter(line -> line.contains(text))
                .findFirst()
                .orElseThrow();
    }

    private String lastNonBlankLine(String stdout) {
        return stdout.lines()
                .filter(line -> !line.isBlank())
                .reduce((first, second) -> second)
                .orElseThrow();
    }

    private int displayIndexOf(String line, String text) {
        return DisplayWidth.width(line.substring(0, line.indexOf(text)));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseJsonObject(String text) {
        Yaml yaml = new Yaml(new SafeConstructor(new LoaderOptions()));
        return (Map<String, Object>) yaml.load(text);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> firstBatchJob(Execution execution) {
        Map<String, Object> batch = parseJsonObject(execution.stdout());
        return ((List<Map<String, Object>>) batch.get("jobs")).getFirst();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> metadata(Map<String, Object> batchJob) {
        return (Map<String, Object>) batchJob.get("metadata");
    }

    private record Execution(int exitCode, String stdout, String stderr) {
    }
}

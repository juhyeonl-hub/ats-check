package dev.juhyeonl.atscheck.cli.store;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;

class JobFileParserTest {
    private final JobFileParser parser = new JobFileParser();
    private final JobFileWriter writer = new JobFileWriter();

    @Test
    void parsesFileWithFrontMatter() throws Exception {
        JobFile jobFile = parser.parse("""
                ---
                url: https://linkedin.com/jobs/view/12345
                company: Wolt
                title: Backend Engineer
                saved_at: 2026-08-15T14:32:00+03:00
                status: new
                ---

                We are looking for a Backend Engineer.
                """);

        assertThat(jobFile.frontMatter().url()).isEqualTo("https://linkedin.com/jobs/view/12345");
        assertThat(jobFile.frontMatter().company()).isEqualTo("Wolt");
        assertThat(jobFile.frontMatter().title()).isEqualTo("Backend Engineer");
        assertThat(jobFile.frontMatter().status()).isEqualTo("new");
        assertThat(jobFile.body()).isEqualTo("We are looking for a Backend Engineer.\n");
    }

    @Test
    void treatsFileWithoutFrontMatterAsBody() throws Exception {
        String text = "Backend Engineer\nWolt\n";

        JobFile jobFile = parser.parse(text);

        assertThat(jobFile.frontMatter()).isEqualTo(FrontMatter.empty());
        assertThat(jobFile.body()).isEqualTo(text);
    }

    @Test
    void preservesUnknownKeysAcrossParseWriteParseRoundTrip() throws Exception {
        JobFile original = parser.parse("""
                ---
                url: https://example.com/job
                company: Wolt
                title: Backend Engineer
                saved_at: 2026-08-15T14:32:00+03:00
                status: new
                source: linkedin
                recruiter: "Ada Lovelace"
                ---

                Body
                """);

        JobFile roundTripped = parser.parse(writer.render(original));

        assertThat(roundTripped.frontMatter().extra())
                .containsEntry("source", "linkedin")
                .containsEntry("recruiter", "Ada Lovelace");
        assertThat(roundTripped.body()).isEqualTo("Body\n");
    }

    @Test
    void defaultsStatusToNewWhenMissing() throws Exception {
        JobFile jobFile = parser.parse("""
                ---
                url: https://example.com/job
                ---

                Body
                """);

        assertThat(jobFile.frontMatter().status()).isEqualTo("new");
    }

    @Test
    void roundTripsValuesWithColon() throws Exception {
        JobFile original = new JobFile(
                new FrontMatter(
                        "https://example.com/job",
                        "Wolt",
                        "Engineer: Backend",
                        "2026-08-15T14:32:00+03:00",
                        "new",
                        Map.of()
                ),
                "Body\n"
        );

        JobFile roundTripped = parser.parse(writer.render(original));

        assertThat(roundTripped.frontMatter().title()).isEqualTo("Engineer: Backend");
        assertThat(roundTripped.frontMatter().savedAt()).isEqualTo("2026-08-15T14:32:00+03:00");
    }

    @Test
    void treatsUnclosedOpeningDelimiterAsBody() throws Exception {
        String text = """
                ---
                url: https://example.com/job

                Body
                """;

        JobFile jobFile = parser.parse(text);

        assertThat(jobFile.frontMatter()).isEqualTo(FrontMatter.empty());
        assertThat(jobFile.body()).isEqualTo(text);
    }
}

package dev.juhyeonl.atscheck.cli.batch;

import dev.juhyeonl.atscheck.cli.store.FrontMatter;
import dev.juhyeonl.atscheck.cli.store.JobFile;
import dev.juhyeonl.atscheck.core.model.CheckResult;
import dev.juhyeonl.atscheck.core.model.JobPosting;
import java.util.Objects;

public record BatchJobResult(String fileName, JobFile jobFile, CheckResult result) {
    public BatchJobResult {
        fileName = Objects.requireNonNull(fileName, "fileName");
        jobFile = Objects.requireNonNull(jobFile, "jobFile");
        result = Objects.requireNonNull(result, "result");
    }

    public FrontMatter frontMatter() {
        return jobFile.frontMatter();
    }

    public String displayCompany() {
        String company = frontMatter().company().strip();
        return company.isBlank() ? fileName : company;
    }

    public String displayTitle() {
        String title = frontMatter().title().strip();
        return title.isBlank() ? JobPosting.fromText(jobFile.body()).title() : title;
    }
}

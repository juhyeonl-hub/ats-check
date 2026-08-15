package dev.juhyeonl.atscheck.cli.batch;

import dev.juhyeonl.atscheck.cli.store.JobFile;
import dev.juhyeonl.atscheck.core.model.CheckResult;
import java.util.Objects;

public record CheckedJob(JobFile jobFile, CheckResult result) {
    public CheckedJob {
        jobFile = Objects.requireNonNull(jobFile, "jobFile");
        result = Objects.requireNonNull(result, "result");
    }
}

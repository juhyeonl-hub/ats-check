package dev.juhyeonl.atscheck.cli.batch;

import dev.juhyeonl.atscheck.cli.store.JobFile;
import dev.juhyeonl.atscheck.cli.store.JobFileParser;
import dev.juhyeonl.atscheck.core.AtsChecker;
import dev.juhyeonl.atscheck.core.model.JobPosting;
import dev.juhyeonl.atscheck.core.model.Profile;
import java.util.Objects;

public final class JobCheckService {
    private final JobFileParser jobFileParser;

    public JobCheckService() {
        this(new JobFileParser());
    }

    public JobCheckService(JobFileParser jobFileParser) {
        this.jobFileParser = Objects.requireNonNull(jobFileParser, "jobFileParser");
    }

    public CheckedJob check(String jobText, Profile profile) throws JobCheckException {
        Objects.requireNonNull(jobText, "jobText");
        Objects.requireNonNull(profile, "profile");

        if (jobText.isBlank()) {
            throw new JobCheckException("empty job posting");
        }

        JobFile jobFile;
        try {
            jobFile = jobFileParser.parse(jobText);
        } catch (JobFileParser.JobFileParseException exception) {
            throw new JobCheckException(exception.getMessage(), exception);
        }

        return new CheckedJob(jobFile, AtsChecker.check(toJobPosting(jobFile), profile));
    }

    private JobPosting toJobPosting(JobFile jobFile) {
        String frontMatterTitle = jobFile.frontMatter().title().strip();
        if (!frontMatterTitle.isBlank()) {
            return new JobPosting(frontMatterTitle, jobFile.body());
        }

        return JobPosting.fromText(jobFile.body());
    }

    public static final class JobCheckException extends Exception {
        public JobCheckException(String message) {
            super(message);
        }

        public JobCheckException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}

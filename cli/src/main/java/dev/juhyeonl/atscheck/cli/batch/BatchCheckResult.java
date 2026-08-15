package dev.juhyeonl.atscheck.cli.batch;

import dev.juhyeonl.atscheck.core.model.Verdict;
import java.util.List;
import java.util.Objects;

public record BatchCheckResult(List<BatchJobResult> jobs) {
    public BatchCheckResult {
        jobs = List.copyOf(Objects.requireNonNull(jobs, "jobs"));
    }

    public int total() {
        return jobs.size();
    }

    public int applyCount() {
        return count(Verdict.APPLY);
    }

    public int reviewCount() {
        return count(Verdict.REVIEW);
    }

    public int skipCount() {
        return count(Verdict.SKIP);
    }

    public Verdict worstVerdict() {
        if (skipCount() > 0) {
            return Verdict.SKIP;
        }
        if (reviewCount() > 0) {
            return Verdict.REVIEW;
        }
        return Verdict.APPLY;
    }

    private int count(Verdict verdict) {
        int count = 0;
        for (BatchJobResult job : jobs) {
            if (job.result().verdict() == verdict) {
                count++;
            }
        }
        return count;
    }
}

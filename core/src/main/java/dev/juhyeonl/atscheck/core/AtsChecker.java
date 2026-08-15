package dev.juhyeonl.atscheck.core;

import dev.juhyeonl.atscheck.core.model.CheckResult;
import dev.juhyeonl.atscheck.core.model.Clause;
import dev.juhyeonl.atscheck.core.model.Finding;
import dev.juhyeonl.atscheck.core.model.JobPosting;
import dev.juhyeonl.atscheck.core.model.Profile;
import dev.juhyeonl.atscheck.core.model.SkillGap;
import dev.juhyeonl.atscheck.core.model.Status;
import dev.juhyeonl.atscheck.core.model.Verdict;
import dev.juhyeonl.atscheck.core.rule.DegreeRule;
import dev.juhyeonl.atscheck.core.rule.ExperienceYearsRule;
import dev.juhyeonl.atscheck.core.rule.LanguageRule;
import dev.juhyeonl.atscheck.core.rule.SeniorityLevelRule;
import dev.juhyeonl.atscheck.core.rule.SkillRule;
import dev.juhyeonl.atscheck.core.section.SectionClassifier;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class AtsChecker {
    private AtsChecker() {
    }

    public static CheckResult check(JobPosting posting, Profile profile) {
        Objects.requireNonNull(posting, "posting");
        Objects.requireNonNull(profile, "profile");

        List<Clause> clauses = SectionClassifier.classify(posting.body());
        List<Finding> stageOneFindings = List.of(
                LanguageRule.evaluate(clauses, profile),
                ExperienceYearsRule.evaluate(clauses, profile),
                DegreeRule.evaluate(clauses, profile)
        );

        List<Finding> findings = new ArrayList<>(stageOneFindings);
        if (hasStatus(stageOneFindings, Status.FAIL)) {
            return new CheckResult(Verdict.SKIP, findings, null, true);
        }

        findings.add(SeniorityLevelRule.evaluate(posting, profile));
        SkillGap skillGap = SkillRule.analyze(clauses, profile);
        findings.add(SkillRule.evaluate(clauses, profile));

        Verdict verdict = hasStatus(stageOneFindings, Status.REVIEW)
                ? Verdict.REVIEW
                : Verdict.APPLY;
        return new CheckResult(verdict, findings, skillGap, false);
    }

    public static CheckResult check(String jobText, Profile profile) {
        return check(JobPosting.fromText(jobText), profile);
    }

    private static boolean hasStatus(List<Finding> findings, Status status) {
        for (Finding finding : findings) {
            if (finding.status() == status) {
                return true;
            }
        }
        return false;
    }
}

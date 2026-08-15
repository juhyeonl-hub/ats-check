package dev.juhyeonl.atscheck.core.section;

import dev.juhyeonl.atscheck.core.model.Clause;
import dev.juhyeonl.atscheck.core.model.RequirementLevel;
import dev.juhyeonl.atscheck.core.model.SectionKind;
import dev.juhyeonl.atscheck.core.model.Signal;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class SectionClassifier {
    private static final ClauseSplitter CLAUSE_SPLITTER = new ClauseSplitter();
    private static final SectionScanner SECTION_SCANNER = new SectionScanner();
    private static final ToneAnalyzer TONE_ANALYZER = new ToneAnalyzer();

    private SectionClassifier() {
    }

    public static List<Clause> classify(String jobText) {
        Objects.requireNonNull(jobText, "jobText");
        if (jobText.isBlank()) {
            return List.of();
        }

        List<Clause> clauses = new ArrayList<>();
        SectionKind currentSection = SectionKind.NONE;
        Signal currentSectionSignal = null;
        String[] lines = jobText.split("\\R", -1);

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            int lineNumber = i + 1;
            if (ClauseSplitter.stripLeadingBullet(line).isBlank()) {
                continue;
            }

            var header = SECTION_SCANNER.scan(line);
            if (header.isPresent()) {
                currentSection = header.get().sectionKind();
                currentSectionSignal = header.get().signal();
                continue;
            }

            for (ClauseSplitter.SplitClause splitClause : CLAUSE_SPLITTER.splitLine(line, lineNumber)) {
                ToneAnalyzer.Analysis analysis = TONE_ANALYZER.analyze(splitClause.text());
                List<Signal> signals = new ArrayList<>();
                if (currentSectionSignal != null) {
                    signals.add(currentSectionSignal);
                }
                signals.addAll(analysis.signals());

                clauses.add(new Clause(
                        splitClause.text(),
                        splitClause.lineNumber(),
                        combine(currentSection, analysis.toneLevel(), analysis.hasHedge()),
                        currentSection,
                        signals
                ));
            }
        }

        return List.copyOf(clauses);
    }

    private static RequirementLevel combine(SectionKind section, RequirementLevel tone, boolean hasHedge) {
        if (tone == RequirementLevel.NEGATED) {
            return RequirementLevel.NEGATED;
        }
        if (tone == RequirementLevel.AMBIGUOUS) {
            return RequirementLevel.AMBIGUOUS;
        }

        if (section == SectionKind.REQUIRED_SECTION && tone == RequirementLevel.NICE) {
            return RequirementLevel.AMBIGUOUS;
        }
        if (section == SectionKind.NICE_SECTION && tone == RequirementLevel.REQUIRED) {
            return RequirementLevel.AMBIGUOUS;
        }

        if (hasHedge
                && tone != RequirementLevel.NICE
                && (section == SectionKind.REQUIRED_SECTION || section == SectionKind.NONE)
                && (tone == RequirementLevel.REQUIRED || tone == RequirementLevel.UNKNOWN)) {
            return RequirementLevel.AMBIGUOUS;
        }

        if (section == SectionKind.REQUIRED_SECTION) {
            return RequirementLevel.REQUIRED;
        }
        if (section == SectionKind.NICE_SECTION) {
            return RequirementLevel.NICE;
        }
        if (tone == RequirementLevel.REQUIRED) {
            return RequirementLevel.REQUIRED;
        }
        if (tone == RequirementLevel.NICE) {
            return RequirementLevel.NICE;
        }
        return RequirementLevel.UNKNOWN;
    }
}

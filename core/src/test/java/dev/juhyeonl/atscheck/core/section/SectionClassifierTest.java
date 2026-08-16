package dev.juhyeonl.atscheck.core.section;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import dev.juhyeonl.atscheck.core.model.Clause;
import dev.juhyeonl.atscheck.core.model.RequirementLevel;
import dev.juhyeonl.atscheck.core.model.SectionKind;
import dev.juhyeonl.atscheck.core.model.Signal;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class SectionClassifierTest {
    @Test
    @DisplayName("REQUIRED 섹션과 REQUIRED 어조에 hedge가 없으면 REQUIRED로 분류한다")
    void classifiesRequiredSectionAndRequiredToneWithoutHedgeAsRequired() {
        Clause clause = onlyClause("Requirements:\nFluent Finnish is required");

        assertThat(clause.level()).isEqualTo(RequirementLevel.REQUIRED);
        assertThat(clause.section()).isEqualTo(SectionKind.REQUIRED_SECTION);
    }

    @Test
    @DisplayName("REQUIRED 섹션과 어조 신호 없음에 hedge가 없으면 REQUIRED로 분류한다")
    void classifiesRequiredSectionAndNoToneWithoutHedgeAsRequired() {
        Clause clause = onlyClause("Requirements:\nJava and Kotlin");

        assertThat(clause.level()).isEqualTo(RequirementLevel.REQUIRED);
        assertThat(clause.section()).isEqualTo(SectionKind.REQUIRED_SECTION);
    }

    @Test
    @DisplayName("REQUIRED 섹션에서도 명시적 NICE 어조는 NICE로 분류한다")
    void classifiesExplicitNiceToneInRequiredSectionAsNice() {
        Clause clause = onlyClause("Requirements:\nFinnish is a plus");

        assertThat(clause.level()).isEqualTo(RequirementLevel.NICE);
    }

    @Test
    @DisplayName("NICE 섹션과 NICE 어조는 NICE로 분류한다")
    void classifiesNiceSectionAndNiceToneAsNice() {
        Clause clause = onlyClause("Nice to have:\nFinnish is a plus");

        assertThat(clause.level()).isEqualTo(RequirementLevel.NICE);
        assertThat(clause.section()).isEqualTo(SectionKind.NICE_SECTION);
    }

    @Test
    @DisplayName("NICE 섹션과 어조 신호 없음은 NICE로 분류한다")
    void classifiesNiceSectionAndNoToneAsNice() {
        Clause clause = onlyClause("Nice to have:\nFinnish");

        assertThat(clause.level()).isEqualTo(RequirementLevel.NICE);
        assertThat(clause.section()).isEqualTo(SectionKind.NICE_SECTION);
    }

    @Test
    @DisplayName("NICE 섹션과 REQUIRED 어조가 충돌하면 AMBIGUOUS로 분류한다")
    void classifiesNiceSectionAndRequiredToneAsAmbiguous() {
        Clause clause = onlyClause("Nice to have:\nFluent Finnish is required");

        assertThat(clause.level()).isEqualTo(RequirementLevel.AMBIGUOUS);
    }

    @Test
    @DisplayName("섹션 신호 없음과 REQUIRED 어조에 hedge가 없으면 REQUIRED로 분류한다")
    void classifiesNoSectionAndRequiredToneWithoutHedgeAsRequired() {
        Clause clause = onlyClause("Fluent Finnish and English are required");

        assertThat(clause.level()).isEqualTo(RequirementLevel.REQUIRED);
        assertThat(clause.section()).isEqualTo(SectionKind.NONE);
    }

    @Test
    @DisplayName("섹션 신호 없음과 NICE 어조는 NICE로 분류한다")
    void classifiesNoSectionAndNiceToneAsNice() {
        Clause clause = onlyClause("Finnish is a plus");

        assertThat(clause.level()).isEqualTo(RequirementLevel.NICE);
        assertThat(clause.section()).isEqualTo(SectionKind.NONE);
    }

    @Test
    @DisplayName("단수와 복수 우대 어조를 NICE로 분류한다")
    void classifiesSingularAndPluralNiceTonesAsNice() {
        Clause pluralPlus = onlyClause("Kotlin and Kubernetes are a plus.");
        Clause singularPlus = onlyClause("Kotlin is a plus.");
        Clause pluralAdvantage = onlyClause("Docker and Terraform are an advantage.");

        assertThat(pluralPlus.level()).isEqualTo(RequirementLevel.NICE);
        assertThat(singularPlus.level()).isEqualTo(RequirementLevel.NICE);
        assertThat(pluralAdvantage.level()).isEqualTo(RequirementLevel.NICE);
        assertThat(pluralPlus.signals())
                .extracting(Signal::type, Signal::dictionaryEntry)
                .contains(tuple(Signal.Type.NICE_TONE, "are a plus"));
        assertThat(singularPlus.signals())
                .extracting(Signal::type, Signal::dictionaryEntry)
                .contains(tuple(Signal.Type.NICE_TONE, "is a plus"));
        assertThat(pluralAdvantage.signals())
                .extracting(Signal::type, Signal::dictionaryEntry)
                .contains(tuple(Signal.Type.NICE_TONE, "are an advantage"));
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {
            "Fluent Finnish skills are considered as an advantage.",
            "Finnish is considered a plus.",
            "Finnish skills are seen as an advantage.",
            "Finnish is regarded as a benefit.",
            "Finnish would be considered an asset.",
            "Knowledge of Finnish counts as a plus."
    })
    @DisplayName("considered/seen/regarded/counts as 우대 관용구를 NICE로 분류한다")
    void classifiesConsideredAdvantageFamilyAsNice(String sentence) {
        Clause clause = onlyClause(sentence);

        assertThat(clause.level()).isEqualTo(RequirementLevel.NICE);
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {
            "Fluent Finnish is considered a strength.",
            "Native Finnish is regarded as an advantage.",
            "Proficiency in Finnish is considered a plus.",
            "Working proficiency in Finnish is considered a merit."
    })
    @DisplayName("명시적 우대 관용구는 수식어형 필수 마커를 이긴다")
    void explicitNiceIdiomsBeatModifierRequiredTones(String sentence) {
        Clause clause = onlyClause(sentence);

        assertThat(clause.level()).isEqualTo(RequirementLevel.NICE);
    }

    @Test
    @DisplayName("서술형 필수 마커와 명시적 우대 관용구가 같은 절에 있으면 AMBIGUOUS로 분류한다")
    void explicitNiceIdiomWithDeclarativeRequiredToneIsAmbiguous() {
        Clause clause = onlyClause("Fluent Finnish is required, though Swedish is considered a plus.");

        assertThat(clause.level()).isEqualTo(RequirementLevel.AMBIGUOUS);
        assertThat(clause.signals())
                .extracting(Signal::type, Signal::dictionaryEntry)
                .contains(
                        tuple(Signal.Type.REQUIRED_TONE, "required"),
                        tuple(Signal.Type.NICE_TONE, "is considered a plus")
                );
    }

    @Test
    @DisplayName("Task 14 회귀 문장들은 기존 기대 수준을 유지한다")
    void keepsTask14RegressionSentenceLevels() {
        assertThat(onlyClause("Fluent Finnish is required.").level())
                .isEqualTo(RequirementLevel.REQUIRED);
        assertThat(onlyClause("Fluent Finnish and English are required.").level())
                .isEqualTo(RequirementLevel.REQUIRED);
        assertThat(onlyClause("We expect fluent Finnish.").level())
                .isEqualTo(RequirementLevel.REQUIRED);
        assertThat(onlyClause("Java is mandatory.").level())
                .isEqualTo(RequirementLevel.REQUIRED);
        assertThat(onlyClause("Finnish is a plus.").level())
                .isEqualTo(RequirementLevel.NICE);
        assertThat(onlyClause("Kotlin and Kubernetes are a plus.").level())
                .isEqualTo(RequirementLevel.NICE);
        assertThat(onlyClause("Finnish is not required.").level())
                .isEqualTo(RequirementLevel.NEGATED);
        assertThat(onlyClause("Working knowledge of Finnish.").level())
                .isEqualTo(RequirementLevel.AMBIGUOUS);
        assertThat(onlyClause("Ideally 5+ years of experience.").level())
                .isEqualTo(RequirementLevel.AMBIGUOUS);
    }

    @Test
    @DisplayName("섹션 신호와 어조 신호와 hedge가 모두 없으면 UNKNOWN으로 분류한다")
    void classifiesNoSectionNoToneAndNoHedgeAsUnknown() {
        Clause clause = onlyClause("Finnish");

        assertThat(clause.level()).isEqualTo(RequirementLevel.UNKNOWN);
        assertThat(clause.section()).isEqualTo(SectionKind.NONE);
    }

    @Test
    @DisplayName("REQUIRED 또는 없음 섹션에서 REQUIRED 또는 없음 어조에 hedge가 있으면 AMBIGUOUS로 분류한다")
    void classifiesHedgedRequiredOrUnknownSignalsAsAmbiguous() {
        assertThat(onlyClause("Requirements:\nWorking knowledge of Finnish").level())
                .isEqualTo(RequirementLevel.AMBIGUOUS);
        assertThat(onlyClause("Requirements:\nWorking knowledge and fluent Finnish").level())
                .isEqualTo(RequirementLevel.AMBIGUOUS);
        assertThat(onlyClause("Working knowledge of Finnish").level())
                .isEqualTo(RequirementLevel.AMBIGUOUS);
        assertThat(onlyClause("Working knowledge and fluent Finnish").level())
                .isEqualTo(RequirementLevel.AMBIGUOUS);
    }

    @Test
    @DisplayName("§6의 세 예시는 REQUIRED NICE AMBIGUOUS로 분류한다")
    void classifiesTheThreeClaudeSectionSixExamples() {
        assertThat(onlyClause("Fluent Finnish and English are required").level())
                .isEqualTo(RequirementLevel.REQUIRED);
        assertThat(onlyClause("Finnish is a plus").level())
                .isEqualTo(RequirementLevel.NICE);
        assertThat(onlyClause("Working knowledge of Finnish").level())
                .isEqualTo(RequirementLevel.AMBIGUOUS);
    }

    @Test
    @DisplayName("어조가 NICE이면 hedge가 있어도 NICE로 분류한다")
    void classifiesNiceToneWithHedgeAsNice() {
        Clause clause = onlyClause("Basic familiarity with Finnish");

        assertThat(clause.level()).isEqualTo(RequirementLevel.NICE);
    }

    @Test
    @DisplayName("섹션 헤더 상태는 다음 섹션 헤더를 만날 때까지 유지된다")
    void keepsSectionHeaderStateUntilTheNextSectionHeader() {
        List<Clause> clauses = SectionClassifier.classify("""
                Requirements:
                Java
                Kotlin
                Nice to have:
                Finnish
                Swedish
                """);

        assertThat(clauses)
                .extracting(Clause::text, Clause::lineNumber, Clause::level, Clause::section)
                .containsExactly(
                        tuple("Java", 2, RequirementLevel.REQUIRED, SectionKind.REQUIRED_SECTION),
                        tuple("Kotlin", 3, RequirementLevel.REQUIRED, SectionKind.REQUIRED_SECTION),
                        tuple("Finnish", 5, RequirementLevel.NICE, SectionKind.NICE_SECTION),
                        tuple("Swedish", 6, RequirementLevel.NICE, SectionKind.NICE_SECTION)
                );
    }

    @Test
    @DisplayName("불릿 기호를 제거하고 절 텍스트와 줄 번호를 보존한다")
    void removesBulletMarkersAndPreservesClauseTextAndLineNumbers() {
        List<Clause> clauses = SectionClassifier.classify("""
                - Java
                * Kotlin
                \u2022 Scala
                \u00b7 Go
                \u2013 Rust
                1. Python
                """);

        assertThat(clauses)
                .extracting(Clause::text, Clause::lineNumber)
                .containsExactly(
                        tuple("Java", 1),
                        tuple("Kotlin", 2),
                        tuple("Scala", 3),
                        tuple("Go", 4),
                        tuple("Rust", 5),
                        tuple("Python", 6)
                );
    }

    @Test
    @DisplayName("plus는 surplus 내부에서 단어 경계 매칭으로 오탐하지 않는다")
    void doesNotMatchPlusInsideSurplus() {
        Clause clause = onlyClause("A surplus of curiosity helps");

        assertThat(clause.level()).isEqualTo(RequirementLevel.UNKNOWN);
        assertThat(clause.signals()).isEmpty();
    }

    @Test
    @DisplayName("e.g. 약어는 문장 끝으로 오인하지 않고 다음 실제 문장에서 분할한다")
    void doesNotSplitSentenceAtEgAbbreviation() {
        List<Clause> clauses = SectionClassifier.classify("Experience with e.g. Java. Fluent English required.");

        assertThat(clauses)
                .extracting(Clause::text, Clause::level)
                .containsExactly(
                        tuple("Experience with e.g. Java.", RequirementLevel.UNKNOWN),
                        tuple("Fluent English required.", RequirementLevel.REQUIRED)
                );
    }

    @Test
    @DisplayName("빈 입력이나 공백만 있는 입력은 빈 목록을 반환한다")
    void returnsEmptyListForBlankInput() {
        assertThat(SectionClassifier.classify("")).isEmpty();
        assertThat(SectionClassifier.classify(" \n\t\n ")).isEmpty();
    }

    @Test
    @DisplayName("절은 판정 근거가 된 섹션 어조 hedge 신호를 보존한다")
    void preservesSignalsThatExplainTheClassification() {
        Clause clause = onlyClause("Requirements:\nWorking knowledge of Finnish is required");

        assertThat(clause.level()).isEqualTo(RequirementLevel.AMBIGUOUS);
        assertThat(clause.signals())
                .extracting(Signal::type, Signal::dictionaryEntry)
                .containsExactly(
                        tuple(Signal.Type.REQUIRED_SECTION, "requirements"),
                        tuple(Signal.Type.REQUIRED_TONE, "required"),
                        tuple(Signal.Type.HEDGE, "working knowledge")
                );
    }

    @Test
    @DisplayName("not required 부정문은 NEGATED로 분류한다")
    void classifiesNotRequiredAsNegated() {
        Clause clause = onlyClause("Finnish is not required.");

        assertThat(clause.level()).isEqualTo(RequirementLevel.NEGATED);
        assertThat(clause.signals())
                .extracting(Signal::type, Signal::dictionaryEntry)
                .contains(tuple(Signal.Type.NEGATION, "not required"));
    }

    @Test
    @DisplayName("not mandatory 부정문은 NEGATED로 분류한다")
    void classifiesNotMandatoryAsNegated() {
        Clause clause = onlyClause("Finnish is not mandatory.");

        assertThat(clause.level()).isEqualTo(RequirementLevel.NEGATED);
        assertThat(clause.signals())
                .extracting(Signal::type, Signal::dictionaryEntry)
                .contains(tuple(Signal.Type.NEGATION, "not mandatory"));
    }

    @Test
    @DisplayName("부정문에 대조 접속사가 있으면 AMBIGUOUS로 분류한다")
    void classifiesNegationWithContrastConnectorAsAmbiguous() {
        Clause clause = onlyClause("Finnish is not required, but it helps.");

        assertThat(clause.level()).isEqualTo(RequirementLevel.AMBIGUOUS);
    }

    @Test
    @DisplayName("REQUIRED 섹션 안에서도 부정문은 NEGATED로 분류한다")
    void classifiesNegationInsideRequiredSectionAsNegated() {
        Clause clause = onlyClause("Requirements:\nFinnish is not required.");

        assertThat(clause.level()).isEqualTo(RequirementLevel.NEGATED);
        assertThat(clause.section()).isEqualTo(SectionKind.REQUIRED_SECTION);
    }

    @Test
    @DisplayName("Benefits 헤더는 섹션 상태를 NONE으로 리셋한다")
    void resetsSectionAtBenefitsHeader() {
        List<Clause> clauses = SectionClassifier.classify("""
                Requirements:
                Java experience.

                Benefits:
                Free Finnish lessons.
                Lunch benefit.
                """);

        assertThat(clauses)
                .extracting(Clause::text, Clause::level, Clause::section)
                .containsExactly(
                        tuple("Java experience.", RequirementLevel.REQUIRED, SectionKind.REQUIRED_SECTION),
                        tuple("Free Finnish lessons.", RequirementLevel.UNKNOWN, SectionKind.NONE),
                        tuple("Lunch benefit.", RequirementLevel.UNKNOWN, SectionKind.NONE)
                );
    }

    @Test
    @DisplayName("Benefits 헤더 줄 자체는 절 목록에 포함하지 않는다")
    void doesNotIncludeBenefitsHeaderAsClause() {
        List<Clause> clauses = SectionClassifier.classify("""
                Requirements:
                Java experience.

                Benefits:
                Free Finnish lessons.
                """);

        assertThat(clauses)
                .extracting(Clause::text)
                .doesNotContain("Benefits:");
    }

    @Test
    @DisplayName("Our Stack 헤더 아래에서도 섹션 상태를 NONE으로 리셋한다")
    void resetsSectionAtOurStackHeader() {
        List<Clause> clauses = SectionClassifier.classify("""
                Requirements:
                Java experience.
                Our Stack:
                Kotlin.
                """);

        assertThat(clauses)
                .extracting(Clause::text, Clause::level, Clause::section)
                .containsExactly(
                        tuple("Java experience.", RequirementLevel.REQUIRED, SectionKind.REQUIRED_SECTION),
                        tuple("Kotlin.", RequirementLevel.UNKNOWN, SectionKind.NONE)
                );
    }

    @Test
    @DisplayName("사전에 없는 콜론 헤더 아래에서도 섹션 상태를 NONE으로 리셋한다")
    void resetsSectionAtUnknownColonHeader() {
        List<Clause> clauses = SectionClassifier.classify("""
                Requirements:
                Java experience.
                Team Rituals:
                Finnish lessons.
                """);

        assertThat(clauses)
                .extracting(Clause::text, Clause::level, Clause::section)
                .containsExactly(
                        tuple("Java experience.", RequirementLevel.REQUIRED, SectionKind.REQUIRED_SECTION),
                        tuple("Finnish lessons.", RequirementLevel.UNKNOWN, SectionKind.NONE)
                );
    }

    @Test
    @DisplayName("마침표로 끝나는 Requirements include 문장은 헤더가 아니라 절이다")
    void treatsRequirementsIncludeSentenceAsClause() {
        List<Clause> clauses = SectionClassifier.classify("Requirements include Java and Kotlin.");

        assertThat(clauses)
                .extracting(Clause::text, Clause::level, Clause::section)
                .containsExactly(
                        tuple("Requirements include Java and Kotlin.", RequirementLevel.UNKNOWN, SectionKind.NONE)
                );
    }

    @Test
    @DisplayName("마침표로 끝나는 We expect 문장은 절이며 REQUIRED 어조로 분류한다")
    void classifiesWeExpectSentenceAsRequiredClause() {
        Clause clause = onlyClause("We expect you to ship code.");

        assertThat(clause.level()).isEqualTo(RequirementLevel.REQUIRED);
        assertThat(clause.section()).isEqualTo(SectionKind.NONE);
        assertThat(clause.signals())
                .extracting(Signal::type, Signal::dictionaryEntry)
                .contains(tuple(Signal.Type.REQUIRED_TONE, "we expect"));
    }

    @Test
    @DisplayName("부정 마커는 어조 마커보다 우선한다")
    void givesNegationPrecedenceOverRequiredTone() {
        Clause clause = onlyClause("Finnish is not required.");

        assertThat(clause.level()).isEqualTo(RequirementLevel.NEGATED);
        assertThat(clause.signals())
                .extracting(Signal::type, Signal::dictionaryEntry)
                .contains(tuple(Signal.Type.NEGATION, "not required"))
                .doesNotContain(tuple(Signal.Type.REQUIRED_TONE, "required"));
    }

    @Test
    @DisplayName("재현 A: REQUIRED 섹션의 부정문은 NEGATED로 유지한다")
    void regressionAClassifiesNegationInRequirementsSection() {
        List<Clause> clauses = SectionClassifier.classify("""
                Requirements:
                Strong Java skills.
                Finnish is not required.
                English is mandatory.
                """);

        assertThat(clauses)
                .extracting(Clause::text, Clause::level, Clause::section)
                .containsExactly(
                        tuple("Strong Java skills.", RequirementLevel.REQUIRED, SectionKind.REQUIRED_SECTION),
                        tuple("Finnish is not required.", RequirementLevel.NEGATED, SectionKind.REQUIRED_SECTION),
                        tuple("English is mandatory.", RequirementLevel.REQUIRED, SectionKind.REQUIRED_SECTION)
                );
    }

    @Test
    @DisplayName("재현 B: Benefits 헤더는 절이 아니며 이후 복지 절은 REQUIRED가 아니다")
    void regressionBResetsAtBenefitsHeader() {
        List<Clause> clauses = SectionClassifier.classify("""
                Requirements:
                Java experience.

                Benefits:
                Free Finnish lessons.
                Lunch benefit.
                """);

        assertThat(clauses)
                .extracting(Clause::text, Clause::level, Clause::section)
                .containsExactly(
                        tuple("Java experience.", RequirementLevel.REQUIRED, SectionKind.REQUIRED_SECTION),
                        tuple("Free Finnish lessons.", RequirementLevel.UNKNOWN, SectionKind.NONE),
                        tuple("Lunch benefit.", RequirementLevel.UNKNOWN, SectionKind.NONE)
                );
    }

    @Test
    @DisplayName("재현 C: 문장 종결 부호가 있는 요구 문장은 헤더로 오탐하지 않는다")
    void regressionCDoesNotTreatSentencesAsHeaders() {
        List<Clause> clauses = SectionClassifier.classify("""
                Requirements include Java and Kotlin.
                We expect you to ship code.
                """);

        assertThat(clauses)
                .extracting(Clause::text, Clause::level, Clause::section)
                .containsExactly(
                        tuple("Requirements include Java and Kotlin.", RequirementLevel.UNKNOWN, SectionKind.NONE),
                        tuple("We expect you to ship code.", RequirementLevel.REQUIRED, SectionKind.NONE)
                );
    }

    private Clause onlyClause(String jobText) {
        List<Clause> clauses = SectionClassifier.classify(jobText);

        assertThat(clauses).hasSize(1);
        return clauses.get(0);
    }
}

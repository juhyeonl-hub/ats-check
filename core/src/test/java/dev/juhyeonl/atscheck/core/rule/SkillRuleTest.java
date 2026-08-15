package dev.juhyeonl.atscheck.core.rule;

import static org.assertj.core.api.Assertions.assertThat;

import dev.juhyeonl.atscheck.core.model.Degree;
import dev.juhyeonl.atscheck.core.model.Finding;
import dev.juhyeonl.atscheck.core.model.Profile;
import dev.juhyeonl.atscheck.core.model.Seniority;
import dev.juhyeonl.atscheck.core.model.SkillGap;
import dev.juhyeonl.atscheck.core.model.Status;
import dev.juhyeonl.atscheck.core.section.SectionClassifier;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SkillRuleTest {
    @Test
    @DisplayName("REQUIRED 절의 Java and Kotlin에서 프로필에 없는 Kotlin은 missingRequired이다")
    void missingRequiredContainsSkillsNotInProfile() {
        SkillGap skillGap = analyze("Requirements:\nJava and Kotlin", profile("java"));

        assertThat(skillGap.matched()).containsExactly("java");
        assertThat(skillGap.missingRequired()).containsExactly("kotlin");
        assertThat(skillGap.missingNice()).isEmpty();
    }

    @Test
    @DisplayName("NICE 절의 Terraform은 missingNice이다")
    void niceSkillsMissingFromProfileAreMissingNice() {
        SkillGap skillGap = analyze("Nice to have:\nTerraform", profile());

        assertThat(skillGap.missingRequired()).isEmpty();
        assertThat(skillGap.missingNice()).containsExactly("terraform");
    }

    @Test
    @DisplayName("프로필에 있는 스킬은 matched에 들어가고 사전에 없는 프로필 스킬도 매칭한다")
    void profileSkillsAreMatchedIncludingSkillsOutsideDictionary() {
        SkillGap skillGap = analyze("Requirements:\nRatpack and Java", profile("ratpack"));

        assertThat(skillGap.matched()).containsExactly("ratpack");
        assertThat(skillGap.missingRequired()).containsExactly("java");
    }

    @Test
    @DisplayName("spring boot는 spring으로 잘리지 않는다")
    void springBootIsMatchedAsTheLongerSkill() {
        SkillGap skillGap = analyze("Requirements:\nSpring Boot", profile("spring"));

        assertThat(skillGap.matched()).isEmpty();
        assertThat(skillGap.missingRequired()).containsExactly("spring boot");
        assertThat(skillGap.missingNice()).isEmpty();
    }

    @Test
    @DisplayName("go는 going과 algorithm에, c는 can과 clear에 매칭되지 않는다")
    void shortSkillsRespectWordBoundaries() {
        SkillGap skillGap = analyze(
                "Requirements:\nWe are going to improve the algorithm and can build clear APIs.",
                profile()
        );

        assertThat(skillGap.matched()).isEmpty();
        assertThat(skillGap.missingRequired()).isEmpty();
        assertThat(skillGap.missingNice()).isEmpty();
    }

    @Test
    @DisplayName("c++, c#, .net은 정규식 특수문자를 문자 그대로 매칭한다")
    void punctuationHeavySkillsAreMatchedExactly() {
        SkillGap skillGap = analyze("Requirements:\nC++, C# and .NET", profile());

        assertThat(skillGap.missingRequired()).containsExactly("c++", "c#", ".net");
        assertThat(skillGap.missingRequired()).doesNotContain("c");
    }

    @Test
    @DisplayName("동사 go는 스킬로 매칭하지 않는다")
    void verbGoIsNotMatchedAsSkill() {
        SkillGap skillGap = analyze("Requirements:\nWe can go fast and ship often.", profile());

        assertThat(skillGap.missingRequired()).doesNotContain("go");
    }

    @Test
    @DisplayName("go through의 go는 스킬로 매칭하지 않는다")
    void goThroughIsNotMatchedAsSkill() {
        SkillGap skillGap = analyze("Requirements:\nYou will go through a structured onboarding.", profile());

        assertThat(skillGap.missingRequired()).doesNotContain("go");
    }

    @Test
    @DisplayName("형용사 swift는 스킬로 매칭하지 않는다")
    void adjectiveSwiftIsNotMatchedAsSkill() {
        SkillGap skillGap = analyze("Requirements:\nA swift and clear communication style.", profile());

        assertThat(skillGap.missingRequired()).doesNotContain("swift");
    }

    @Test
    @DisplayName("rust-free처럼 하이픈으로 붙은 rust는 스킬로 매칭하지 않는다")
    void hyphenatedRustIsNotMatchedAsSkill() {
        SkillGap skillGap = analyze("Requirements:\nYou are a rust-free engineer with clean habits.", profile());

        assertThat(skillGap.missingRequired()).doesNotContain("rust");
    }

    @Test
    @DisplayName("scala-ble처럼 하이픈으로 붙은 scala는 스킬로 매칭하지 않는다")
    void hyphenatedScalaIsNotMatchedAsSkill() {
        SkillGap skillGap = analyze("Requirements:\nWe work in a scala-ble architecture.", profile());

        assertThat(skillGap.missingRequired()).doesNotContain("scala");
    }

    @Test
    @DisplayName("golang은 go로 정규화되어 매칭한다")
    void golangAliasIsNormalizedToGo() {
        SkillGap skillGap = analyze("Requirements:\nGolang microservices experience.", profile());

        assertThat(skillGap.missingRequired()).contains("go");
    }

    @Test
    @DisplayName("experience with 뒤의 Go와 Rust는 모호어여도 매칭한다")
    void ambiguousSkillsAfterExperienceWithAreMatched() {
        SkillGap skillGap = analyze("Requirements:\nExperience with Go and Rust.", profile());

        assertThat(skillGap.missingRequired()).containsExactly("go", "rust");
    }

    @Test
    @DisplayName("같은 절의 Objective-C가 명확하면 Swift도 매칭한다")
    void ambiguousSwiftIsMatchedWithUnambiguousSkillInSameClause() {
        SkillGap skillGap = analyze("Requirements:\nSwift and Objective-C for iOS.", profile());

        assertThat(skillGap.missingRequired()).containsExactly("swift", "objective-c");
    }

    @Test
    @DisplayName("C++와 C#은 C로 잘리지 않고 각각 매칭한다")
    void cPlusPlusAndCSharpDoNotMatchC() {
        SkillGap skillGap = analyze("Requirements:\nC++ and C# experience.", profile());

        assertThat(skillGap.missingRequired()).containsExactly("c++", "c#");
    }

    @Test
    @DisplayName("기술 문맥 단어가 있으면 C 언어를 매칭한다")
    void cProgrammingLanguageIsMatchedWithTechnicalContext() {
        SkillGap skillGap = analyze("Requirements:\nExperience with the C programming language.", profile());

        assertThat(skillGap.missingRequired()).containsExactly("c");
    }

    @Test
    @DisplayName("Spring Boot와 별도 Spring은 둘 다 매칭한다")
    void springBootAndSeparateSpringAreBothMatched() {
        SkillGap skillGap = analyze("Requirements:\nSpring Boot, not just Spring.", profile());

        assertThat(skillGap.missingRequired()).containsExactly("spring boot", "spring");
    }

    @Test
    @DisplayName("k8s와 postgres는 정규형으로 출력한다")
    void infrastructureAliasesAreNormalized() {
        SkillGap skillGap = analyze("Requirements:\nk8s and postgres experience", profile());

        assertThat(skillGap.missingRequired()).containsExactly("kubernetes", "postgresql");
    }

    @Test
    @DisplayName("Node.js와 TypeScript는 회귀 없이 매칭한다")
    void nodeJsAndTypeScriptAreMatched() {
        SkillGap skillGap = analyze("Requirements:\nNode.js and TypeScript.", profile());

        assertThat(skillGap.missingRequired()).containsExactly("node.js", "typescript");
    }

    @Test
    @DisplayName("같은 스킬이 required와 nice 양쪽에 있으면 required를 우선한다")
    void requiredSkillsWinOverNiceSkills() {
        SkillGap skillGap = analyze("""
                Nice to have:
                Docker
                Requirements:
                Docker
                """, profile());

        assertThat(skillGap.missingRequired()).containsExactly("docker");
        assertThat(skillGap.missingNice()).isEmpty();
    }

    @Test
    @DisplayName("NEGATED와 UNKNOWN 절의 스킬은 missing에 넣지 않고 matched 판정에만 쓴다")
    void negatedAndUnknownSkillsOnlyContributeToMatched() {
        SkillGap skillGap = analyze("""
                Java is not required.
                Our Stack:
                Kafka
                """, profile("java", "kafka"));

        assertThat(skillGap.matched()).containsExactly("java", "kafka");
        assertThat(skillGap.missingRequired()).isEmpty();
        assertThat(skillGap.missingNice()).isEmpty();
    }

    @Test
    @DisplayName("SkillRule Finding 상태는 스킬 누락이 있어도 항상 PASS이다")
    void evaluateAlwaysPasses() {
        Finding finding = SkillRule.evaluate(
                SectionClassifier.classify("Requirements:\nKotlin"),
                profile("java")
        );

        assertThat(finding.status()).isEqualTo(Status.PASS);
        assertThat(finding.summary()).isEqualTo("missing 1 required, 0 nice");
    }

    private SkillGap analyze(String jobText, Profile profile) {
        return SkillRule.analyze(SectionClassifier.classify(jobText), profile);
    }

    private Profile profile(String... skills) {
        return new Profile(
                0,
                1,
                Seniority.LEAD,
                Set.of("english"),
                Degree.NONE,
                Set.of(skills)
        );
    }
}

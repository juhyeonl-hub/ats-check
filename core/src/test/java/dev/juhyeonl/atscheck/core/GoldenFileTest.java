package dev.juhyeonl.atscheck.core;

import static org.assertj.core.api.Assertions.assertThat;

import dev.juhyeonl.atscheck.core.model.CheckResult;
import dev.juhyeonl.atscheck.core.model.Clause;
import dev.juhyeonl.atscheck.core.model.Degree;
import dev.juhyeonl.atscheck.core.model.Finding;
import dev.juhyeonl.atscheck.core.model.Profile;
import dev.juhyeonl.atscheck.core.model.RuleId;
import dev.juhyeonl.atscheck.core.model.Seniority;
import dev.juhyeonl.atscheck.core.model.SkillGap;
import dev.juhyeonl.atscheck.core.model.Status;
import dev.juhyeonl.atscheck.core.model.Verdict;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import org.yaml.snakeyaml.error.YAMLException;

class GoldenFileTest {
    private static final Profile DEFAULT_PROFILE = new Profile(
            2,
            1,
            Seniority.MID,
            Set.of("english", "korean"),
            Degree.BACHELOR,
            Set.of("java", "spring boot", "postgresql", "rest", "docker")
    );
    private static final Yaml YAML = new Yaml(new SafeConstructor(new LoaderOptions()));

    @ParameterizedTest(name = "{0}")
    @MethodSource("goldenCases")
    void goldenFileCaseMatchesExpectedResult(GoldenCase goldenCase) throws IOException {
        String input = Files.readString(goldenCase.inputPath());
        Map<String, Object> expected = readExpected(goldenCase.expectedPath());
        Profile profile = profileFrom(expected);

        CheckResult result = AtsChecker.check(input, profile);

        assertVerdict(goldenCase, expected, result);
        assertStoppedAtHardFilter(goldenCase, expected, result);
        assertFindings(goldenCase, expected, result);
        assertSkillGap(goldenCase, expected, result);
        assertEvidenceContains(goldenCase, expected, result);
    }

    static Stream<GoldenCase> goldenCases() throws IOException, URISyntaxException {
        var resource = GoldenFileTest.class.getClassLoader().getResource("golden");
        assertThat(resource)
                .as("golden resource directory must exist")
                .isNotNull();

        Path goldenRoot = Path.of(resource.toURI());
        List<GoldenCase> cases;
        try (Stream<Path> paths = Files.list(goldenRoot)) {
            cases = paths
                    .filter(Files::isDirectory)
                    .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                    .map(path -> new GoldenCase(
                            path.getFileName().toString(),
                            path.resolve("input.txt"),
                            path.resolve("expected.json")
                    ))
                    .toList();
        }

        assertThat(cases)
                .as("golden cases must not be empty")
                .isNotEmpty();
        return cases.stream();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> readExpected(Path expectedPath) throws IOException {
        assertThat(expectedPath)
                .as("expected file must exist: %s", expectedPath)
                .exists();

        try (InputStream input = Files.newInputStream(expectedPath)) {
            Object loaded = YAML.load(input);
            assertThat(loaded)
                    .as("expected file must be a JSON/YAML object: %s", expectedPath)
                    .isInstanceOf(Map.class);
            return (Map<String, Object>) loaded;
        } catch (YAMLException exception) {
            throw new AssertionError("failed to parse expected file: " + expectedPath, exception);
        }
    }

    private static Profile profileFrom(Map<String, Object> expected) {
        Map<String, Object> override = optionalMap(expected, "profile");
        if (override == null) {
            return DEFAULT_PROFILE;
        }

        return new Profile(
                intValue(override, "yearsExperience", DEFAULT_PROFILE.yearsExperience()),
                intValue(override, "yearsTolerance", DEFAULT_PROFILE.yearsTolerance()),
                enumValue(override, "maxSeniority", Seniority.class, DEFAULT_PROFILE.maxSeniority()),
                stringSet(override, "languages", DEFAULT_PROFILE.languages()),
                enumValue(override, "degree", Degree.class, DEFAULT_PROFILE.degree()),
                stringSet(override, "skills", DEFAULT_PROFILE.skills())
        );
    }

    private static void assertVerdict(
            GoldenCase goldenCase,
            Map<String, Object> expected,
            CheckResult result
    ) {
        Verdict expectedVerdict = enumValue(expected, "verdict", Verdict.class);
        assertThat(result.verdict())
                .as(
                        "case %s verdict expected <%s> actual <%s>",
                        goldenCase.name(),
                        expectedVerdict,
                        result.verdict()
                )
                .isEqualTo(expectedVerdict);
    }

    private static void assertStoppedAtHardFilter(
            GoldenCase goldenCase,
            Map<String, Object> expected,
            CheckResult result
    ) {
        boolean expectedStopped = booleanValue(expected, "stoppedAtHardFilter");
        assertThat(result.stoppedAtHardFilter())
                .as(
                        "case %s stoppedAtHardFilter expected <%s> actual <%s>",
                        goldenCase.name(),
                        expectedStopped,
                        result.stoppedAtHardFilter()
                )
                .isEqualTo(expectedStopped);
    }

    private static void assertFindings(
            GoldenCase goldenCase,
            Map<String, Object> expected,
            CheckResult result
    ) {
        List<FindingExpectation> expectedFindings = findingExpectations(expected);
        List<FindingPair> expectedPairs = expectedFindings.stream()
                .map(FindingExpectation::pair)
                .toList();
        List<FindingPair> actualPairs = result.findings().stream()
                .map(finding -> new FindingPair(finding.rule(), finding.status()))
                .toList();

        assertThat(actualPairs)
                .as(
                        "case %s findings expected <%s> actual <%s>",
                        goldenCase.name(),
                        expectedPairs,
                        actualPairs
                )
                .containsExactlyElementsOf(expectedPairs);
    }

    private static void assertSkillGap(
            GoldenCase goldenCase,
            Map<String, Object> expected,
            CheckResult result
    ) {
        assertThat(expected)
                .as("case %s expected.json must declare skillGap", goldenCase.name())
                .containsKey("skillGap");

        Object expectedSkillGap = expected.get("skillGap");
        if (expectedSkillGap == null) {
            assertThat(result.skillGap())
                    .as(
                            "case %s skillGap expected <null> actual <%s>",
                            goldenCase.name(),
                            result.skillGap()
                    )
                    .isNull();
            return;
        }

        assertThat(expectedSkillGap)
                .as("case %s skillGap must be an object or null", goldenCase.name())
                .isInstanceOf(Map.class);
        SkillGap actual = result.skillGap();
        assertThat(actual)
                .as("case %s skillGap expected <%s> actual <null>", goldenCase.name(), expectedSkillGap)
                .isNotNull();

        Map<String, Object> expectedGap = castMap(expectedSkillGap);
        assertSet(
                goldenCase,
                "skillGap.matched",
                stringList(expectedGap, "matched"),
                actual.matched()
        );
        assertSet(
                goldenCase,
                "skillGap.missingRequired",
                stringList(expectedGap, "missingRequired"),
                actual.missingRequired()
        );
        assertSet(
                goldenCase,
                "skillGap.missingNice",
                stringList(expectedGap, "missingNice"),
                actual.missingNice()
        );
    }

    private static void assertEvidenceContains(
            GoldenCase goldenCase,
            Map<String, Object> expected,
            CheckResult result
    ) {
        Map<RuleId, Finding> actualByRule = result.findings().stream()
                .collect(Collectors.toMap(Finding::rule, finding -> finding));
        for (FindingExpectation expectation : findingExpectations(expected)) {
            if (expectation.evidenceContains() == null) {
                continue;
            }

            Finding actual = actualByRule.get(expectation.rule());
            assertThat(actual)
                    .as(
                            "case %s finding for evidence rule %s expected substring <%s> actual finding <null>",
                            goldenCase.name(),
                            expectation.rule(),
                            expectation.evidenceContains()
                    )
                    .isNotNull();

            String evidence = actual.evidence().stream()
                    .map(Clause::text)
                    .collect(Collectors.joining("\n"));
            assertThat(evidence)
                    .as(
                            "case %s evidence for %s expected to contain <%s> actual evidence <%s>",
                            goldenCase.name(),
                            expectation.rule(),
                            expectation.evidenceContains(),
                            evidence
                    )
                    .contains(expectation.evidenceContains());
        }
    }

    private static void assertSet(
            GoldenCase goldenCase,
            String field,
            List<String> expected,
            Set<String> actual
    ) {
        assertThat(actual)
                .as(
                        "case %s %s expected <%s> actual <%s>",
                        goldenCase.name(),
                        field,
                        expected,
                        actual
                )
                .containsExactlyInAnyOrderElementsOf(expected);
    }

    @SuppressWarnings("unchecked")
    private static List<FindingExpectation> findingExpectations(Map<String, Object> expected) {
        Object value = Objects.requireNonNull(expected.get("findings"), "findings");
        assertThat(value)
                .as("findings must be an array")
                .isInstanceOf(List.class);

        List<FindingExpectation> expectations = new ArrayList<>();
        for (Object item : (List<Object>) value) {
            assertThat(item)
                    .as("finding entry must be an object: %s", item)
                    .isInstanceOf(Map.class);
            Map<String, Object> finding = castMap(item);
            expectations.add(new FindingExpectation(
                    enumValue(finding, "rule", RuleId.class),
                    enumValue(finding, "status", Status.class),
                    optionalString(finding, "evidenceContains")
            ));
        }
        return List.copyOf(expectations);
    }

    private static boolean booleanValue(Map<String, Object> values, String key) {
        Object value = Objects.requireNonNull(values.get(key), key);
        assertThat(value)
                .as("%s must be a boolean", key)
                .isInstanceOf(Boolean.class);
        return (Boolean) value;
    }

    private static int intValue(Map<String, Object> values, String key, int defaultValue) {
        Object value = values.get(key);
        if (value == null) {
            return defaultValue;
        }
        assertThat(value)
                .as("%s must be a number", key)
                .isInstanceOf(Number.class);
        return ((Number) value).intValue();
    }

    private static <E extends Enum<E>> E enumValue(
            Map<String, Object> values,
            String key,
            Class<E> enumType
    ) {
        Object value = Objects.requireNonNull(values.get(key), key);
        assertThat(value)
                .as("%s must be a string", key)
                .isInstanceOf(String.class);
        return Enum.valueOf(enumType, ((String) value).strip().toUpperCase(Locale.ROOT));
    }

    private static <E extends Enum<E>> E enumValue(
            Map<String, Object> values,
            String key,
            Class<E> enumType,
            E defaultValue
    ) {
        if (!values.containsKey(key)) {
            return defaultValue;
        }
        return enumValue(values, key, enumType);
    }

    private static String optionalString(Map<String, Object> values, String key) {
        Object value = values.get(key);
        if (value == null) {
            return null;
        }
        assertThat(value)
                .as("%s must be a string", key)
                .isInstanceOf(String.class);
        return (String) value;
    }

    private static Set<String> stringSet(
            Map<String, Object> values,
            String key,
            Set<String> defaultValue
    ) {
        if (!values.containsKey(key)) {
            return defaultValue;
        }
        return new LinkedHashSet<>(stringList(values, key));
    }

    @SuppressWarnings("unchecked")
    private static List<String> stringList(Map<String, Object> values, String key) {
        Object value = Objects.requireNonNull(values.get(key), key);
        assertThat(value)
                .as("%s must be an array", key)
                .isInstanceOf(List.class);

        List<String> result = new ArrayList<>();
        for (Object item : (List<Object>) value) {
            assertThat(item)
                    .as("%s entries must be strings", key)
                    .isInstanceOf(String.class);
            result.add((String) item);
        }
        return List.copyOf(result);
    }

    private static Map<String, Object> optionalMap(Map<String, Object> values, String key) {
        Object value = values.get(key);
        if (value == null) {
            return null;
        }
        assertThat(value)
                .as("%s must be an object", key)
                .isInstanceOf(Map.class);
        return castMap(value);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> castMap(Object value) {
        return (Map<String, Object>) value;
    }

    private record GoldenCase(String name, Path inputPath, Path expectedPath) {
        @Override
        public String toString() {
            return name;
        }
    }

    private record FindingExpectation(RuleId rule, Status status, String evidenceContains) {
        private FindingPair pair() {
            return new FindingPair(rule, status);
        }
    }

    private record FindingPair(RuleId rule, Status status) {
    }
}

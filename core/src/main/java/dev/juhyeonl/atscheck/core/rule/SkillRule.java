package dev.juhyeonl.atscheck.core.rule;

import dev.juhyeonl.atscheck.core.model.Clause;
import dev.juhyeonl.atscheck.core.model.Finding;
import dev.juhyeonl.atscheck.core.model.Profile;
import dev.juhyeonl.atscheck.core.model.RequirementLevel;
import dev.juhyeonl.atscheck.core.model.RuleId;
import dev.juhyeonl.atscheck.core.model.SkillGap;
import dev.juhyeonl.atscheck.core.model.Status;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public final class SkillRule {
    private static final List<String> DICTIONARY = List.of(
            "java", "kotlin", "scala", "groovy", "clojure",
            "spring", "spring boot", "hibernate", "jpa", "quarkus", "micronaut", "jakarta ee",
            "python", "django", "flask", "fastapi", "pandas", "numpy",
            "javascript", "typescript", "node.js", "deno", "express", "nest.js",
            "react", "vue", "angular", "svelte", "next.js", "redux",
            "go", "rust", "c", "c++", "c#", ".net", "php", "laravel", "ruby", "rails", "elixir",
            "swift", "objective-c",
            "sql", "postgresql", "mysql", "mariadb", "oracle", "sql server", "sqlite",
            "mongodb", "redis", "cassandra", "dynamodb", "elasticsearch", "neo4j",
            "kafka", "rabbitmq", "activemq", "sqs", "pulsar",
            "docker", "kubernetes", "helm", "terraform", "ansible", "pulumi", "vagrant",
            "aws", "azure", "gcp", "google cloud", "digitalocean", "heroku",
            "jenkins", "github actions", "gitlab ci", "circleci", "teamcity", "argocd",
            "git", "svn", "mercurial",
            "rest", "graphql", "grpc", "soap", "websocket", "openapi",
            "microservices", "event-driven", "ddd", "tdd", "cqrs", "serverless",
            "linux", "unix", "bash", "shell", "powershell", "nginx", "apache",
            "html", "css", "sass", "less", "tailwind", "bootstrap",
            "junit", "jest", "pytest", "mockito", "testng", "selenium", "cypress", "playwright",
            "testcontainers",
            "maven", "gradle", "npm", "yarn", "pnpm", "webpack", "vite",
            "agile", "scrum", "kanban", "jira", "confluence",
            "prometheus", "grafana", "datadog", "splunk", "opentelemetry",
            "kibana", "logstash", "sentry"
    );
    private static final Map<String, String> ALIASES = Map.ofEntries(
            Map.entry("golang", "go"),
            Map.entry("k8s", "kubernetes"),
            Map.entry("postgres", "postgresql"),
            Map.entry("dotnet", ".net"),
            Map.entry(".net core", ".net"),
            Map.entry("cpp", "c++"),
            Map.entry("csharp", "c#"),
            Map.entry("node", "node.js")
    );
    private static final Set<String> AMBIGUOUS_SKILLS = Set.of(
            "go", "swift", "rust", "scala", "c", "r", "dart", "julia", "groovy", "elixir"
    );
    private static final List<Pattern> TECH_CONTEXT_PATTERNS = List.of(
            "programming", "language", "lang", "framework", "stack", "codebase", "runtime",
            "backend", "frontend", "fullstack", "microservice", "microservices", "api", "apis",
            "service", "services", "development", "developing", "code", "coding"
    ).stream()
            .map(SkillRule::phrasePattern)
            .toList();
    private static final List<Pattern> EXPERIENCE_PREFIX_PATTERNS = List.of(
            "experience with", "proficient in", "knowledge of", "familiar with", "skills in", "expertise in"
    ).stream()
            .map(SkillRule::phrasePattern)
            .toList();

    private SkillRule() {
    }

    public static SkillGap analyze(List<Clause> clauses, Profile profile) {
        Objects.requireNonNull(clauses, "clauses");
        Objects.requireNonNull(profile, "profile");

        List<SkillPattern> skillPatterns = skillPatterns(profile);
        Set<String> profileSkills = canonicalSkills(profile.skills());
        LinkedHashSet<String> matched = new LinkedHashSet<>();
        LinkedHashSet<String> missingRequired = new LinkedHashSet<>();
        LinkedHashSet<String> missingNice = new LinkedHashSet<>();

        for (Clause clause : clauses) {
            Objects.requireNonNull(clause, "clauses element");
            for (String skill : findSkills(clause.text(), skillPatterns)) {
                if (profileSkills.contains(skill)) {
                    matched.add(skill);
                    continue;
                }

                if (clause.level() == RequirementLevel.REQUIRED || clause.level() == RequirementLevel.AMBIGUOUS) {
                    missingRequired.add(skill);
                    missingNice.remove(skill);
                } else if (clause.level() == RequirementLevel.NICE && !missingRequired.contains(skill)) {
                    missingNice.add(skill);
                }
            }
        }

        return new SkillGap(matched, missingRequired, missingNice);
    }

    public static Finding evaluate(List<Clause> clauses, Profile profile) {
        SkillGap skillGap = analyze(clauses, profile);
        return new Finding(RuleId.SKILLS, Status.PASS, summaryFor(skillGap), List.of());
    }

    private static String summaryFor(SkillGap skillGap) {
        if (skillGap.missingRequired().isEmpty() && skillGap.missingNice().isEmpty()) {
            return "full match";
        }
        return "missing " + skillGap.missingRequired().size()
                + " required, " + skillGap.missingNice().size() + " nice";
    }

    private static List<SkillPattern> skillPatterns(Profile profile) {
        LinkedHashSet<String> skills = new LinkedHashSet<>(DICTIONARY);
        profile.skills().stream()
                .filter(skill -> !skill.isBlank())
                .map(SkillRule::canonicalSkill)
                .sorted()
                .forEach(skills::add);

        List<SkillPattern> skillPatterns = new ArrayList<>();
        skills.stream()
                .filter(skill -> !skill.isBlank())
                .forEach(skill -> skillPatterns.add(new SkillPattern(
                        skill,
                        skill,
                        phrasePattern(skill),
                        AMBIGUOUS_SKILLS.contains(skill)
                )));
        ALIASES.forEach((alias, canonical) -> skillPatterns.add(new SkillPattern(
                alias,
                canonical,
                phrasePattern(alias),
                false
        )));

        return skillPatterns.stream()
                .sorted(Comparator
                        .comparingInt(SkillPattern::phraseLength).reversed()
                        .thenComparing(SkillPattern::phrase)
                        .thenComparing(SkillPattern::skill))
                .toList();
    }

    private static LinkedHashSet<String> findSkills(String text, List<SkillPattern> skillPatterns) {
        List<Candidate> selected = new ArrayList<>();

        for (SkillPattern skillPattern : skillPatterns) {
            Matcher matcher = skillPattern.pattern().matcher(text);
            while (matcher.find()) {
                if (!hasSkillBoundaries(text, matcher.start(), matcher.end())) {
                    continue;
                }

                Candidate candidate = new Candidate(
                        skillPattern.skill(),
                        matcher.start(),
                        matcher.end(),
                        skillPattern.requiresContext()
                );
                if (!overlapsAny(candidate, selected)) {
                    selected.add(candidate);
                }
            }
        }

        selected = filterAmbiguousCandidates(text, selected);
        selected.sort(Comparator
                .comparingInt(Candidate::start)
                .thenComparing(Comparator.comparingInt(Candidate::length).reversed())
                .thenComparing(Candidate::skill));

        LinkedHashSet<String> skills = new LinkedHashSet<>();
        for (Candidate candidate : selected) {
            skills.add(candidate.skill());
        }
        return skills;
    }

    private static boolean overlapsAny(Candidate candidate, List<Candidate> selected) {
        for (Candidate existing : selected) {
            if (candidate.start() < existing.end() && existing.start() < candidate.end()) {
                return true;
            }
        }
        return false;
    }

    private static List<Candidate> filterAmbiguousCandidates(String text, List<Candidate> selected) {
        boolean hasTechnicalContext = hasAnyMatch(text, TECH_CONTEXT_PATTERNS);
        List<Candidate> filtered = new ArrayList<>();
        for (Candidate candidate : selected) {
            if (!candidate.requiresContext()
                    || hasTechnicalContext
                    || hasExperiencePrefixBefore(text, candidate)
                    || hasOtherUnambiguousSkill(candidate, selected)) {
                filtered.add(candidate);
            }
        }
        return filtered;
    }

    private static boolean hasExperiencePrefixBefore(String text, Candidate candidate) {
        for (Pattern pattern : EXPERIENCE_PREFIX_PATTERNS) {
            Matcher matcher = pattern.matcher(text);
            while (matcher.find()) {
                if (matcher.end() <= candidate.start()
                        && hasSkillBoundaries(text, matcher.start(), matcher.end())) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean hasOtherUnambiguousSkill(Candidate candidate, List<Candidate> selected) {
        for (Candidate other : selected) {
            if (!other.requiresContext() && !other.skill().equals(candidate.skill())) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasAnyMatch(String text, List<Pattern> patterns) {
        for (Pattern pattern : patterns) {
            Matcher matcher = pattern.matcher(text);
            while (matcher.find()) {
                if (hasSkillBoundaries(text, matcher.start(), matcher.end())) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean hasSkillBoundaries(String text, int start, int end) {
        return !hasForbiddenBoundaryBefore(text, start) && !hasForbiddenBoundaryAfter(text, end);
    }

    private static boolean hasForbiddenBoundaryBefore(String text, int start) {
        if (start <= 0) {
            return false;
        }

        char previous = text.charAt(start - 1);
        if (isHardBoundaryChar(previous)) {
            return true;
        }
        return previous == '.' && start >= 2 && isAsciiLetterOrDigit(text.charAt(start - 2));
    }

    private static boolean hasForbiddenBoundaryAfter(String text, int end) {
        if (end >= text.length()) {
            return false;
        }

        char next = text.charAt(end);
        if (isHardBoundaryChar(next)) {
            return true;
        }
        // A sentence-final period should not block a match, but a token-joining dot should.
        return next == '.' && end + 1 < text.length() && isAsciiLetterOrDigit(text.charAt(end + 1));
    }

    private static boolean isHardBoundaryChar(char value) {
        return isAsciiLetterOrDigit(value) || value == '+' || value == '#' || value == '_' || value == '-';
    }

    private static boolean isAsciiLetterOrDigit(char value) {
        return (value >= 'a' && value <= 'z')
                || (value >= 'A' && value <= 'Z')
                || (value >= '0' && value <= '9');
    }

    private static Set<String> canonicalSkills(Set<String> skills) {
        LinkedHashSet<String> canonicalSkills = new LinkedHashSet<>();
        for (String skill : skills) {
            if (!skill.isBlank()) {
                canonicalSkills.add(canonicalSkill(skill));
            }
        }
        return canonicalSkills;
    }

    private static String canonicalSkill(String skill) {
        return ALIASES.getOrDefault(skill, skill);
    }

    private static Pattern phrasePattern(String phrase) {
        String body = Pattern.compile("\\s+")
                .splitAsStream(phrase.strip())
                .map(Pattern::quote)
                .collect(Collectors.joining("\\s+"));
        return Pattern.compile(
                body,
                Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
        );
    }

    private record SkillPattern(String phrase, String skill, Pattern pattern, boolean requiresContext) {
        private int phraseLength() {
            return phrase.length();
        }
    }

    private record Candidate(String skill, int start, int end, boolean requiresContext) {
        private int length() {
            return end - start;
        }
    }
}

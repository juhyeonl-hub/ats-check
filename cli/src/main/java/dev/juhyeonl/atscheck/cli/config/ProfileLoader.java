package dev.juhyeonl.atscheck.cli.config;

import dev.juhyeonl.atscheck.core.model.Degree;
import dev.juhyeonl.atscheck.core.model.Profile;
import dev.juhyeonl.atscheck.core.model.Seniority;
import java.io.IOException;
import java.io.Reader;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import org.yaml.snakeyaml.error.YAMLException;

public final class ProfileLoader {
    private final Map<String, String> environment;
    private final Path homeDirectory;

    public ProfileLoader() {
        this(System.getenv(), Path.of(System.getProperty("user.home")));
    }

    public ProfileLoader(Map<String, String> environment, Path homeDirectory) {
        this.environment = Map.copyOf(Objects.requireNonNull(environment, "environment"));
        this.homeDirectory = Objects.requireNonNull(homeDirectory, "homeDirectory");
    }

    public Profile load(Path explicitPath, PrintWriter err) throws ProfileLoadException {
        Objects.requireNonNull(err, "err");

        if (explicitPath != null) {
            if (!Files.isRegularFile(explicitPath)) {
                throw new ProfileLoadException("profile file not found: " + explicitPath);
            }
            return loadFile(explicitPath, err);
        }

        for (Path candidate : searchPaths()) {
            if (Files.isRegularFile(candidate)) {
                return loadFile(candidate, err);
            }
        }

        err.println("no profile found, using defaults - run 'ats-check init' to create one");
        err.flush();
        return Profile.defaults();
    }

    private List<Path> searchPaths() {
        LinkedHashSet<Path> paths = new LinkedHashSet<>();
        String xdgConfigHome = environment.get("XDG_CONFIG_HOME");
        if (xdgConfigHome != null && !xdgConfigHome.isBlank()) {
            paths.add(Path.of(xdgConfigHome).resolve("ats-check").resolve("profile.yml"));
        }
        paths.add(homeDirectory.resolve(".config").resolve("ats-check").resolve("profile.yml"));
        return List.copyOf(paths);
    }

    private Profile loadFile(Path path, PrintWriter err) throws ProfileLoadException {
        Map<String, Object> values = readMap(path);
        Profile defaults = Profile.defaults();

        int yearsExperience = intValue(values, "years_experience", defaults.yearsExperience(), err);
        int yearsTolerance = intValue(values, "years_tolerance", defaults.yearsTolerance(), err);
        Seniority maxSeniority = enumValue(
                values,
                "max_seniority",
                Seniority.class,
                defaults.maxSeniority(),
                err
        );
        Set<String> languages = stringSet(values, "languages", defaults.languages(), err);
        Degree degree = enumValue(values, "degree", Degree.class, defaults.degree(), err);
        Set<String> skills = stringSet(values, "skills", defaults.skills(), err);

        return new Profile(yearsExperience, yearsTolerance, maxSeniority, languages, degree, skills);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> readMap(Path path) throws ProfileLoadException {
        LoaderOptions options = new LoaderOptions();
        Yaml yaml = new Yaml(new SafeConstructor(options));
        Object loaded;
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            loaded = yaml.load(reader);
        } catch (YAMLException exception) {
            throw new ProfileLoadException("invalid profile.yml: " + path + " (" + exception.getMessage() + ")");
        } catch (IOException exception) {
            throw new ProfileLoadException("failed to read profile.yml: " + path + " (" + exception.getMessage() + ")");
        }

        if (loaded == null) {
            return Map.of();
        }
        if (!(loaded instanceof Map<?, ?> rawMap)) {
            throw new ProfileLoadException("invalid profile.yml: " + path + " (root must be a map)");
        }

        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : rawMap.entrySet()) {
            if (entry.getKey() instanceof String key) {
                result.put(key, entry.getValue());
            }
        }
        return result;
    }

    private int intValue(Map<String, Object> values, String key, int defaultValue, PrintWriter err) {
        if (!values.containsKey(key)) {
            return defaultValue;
        }

        Optional<Integer> parsed = parseInt(values.get(key));
        if (parsed.isPresent() && parsed.get() >= 0) {
            return parsed.get();
        }

        warn(err, key, values.get(key), defaultValue);
        return defaultValue;
    }

    private Optional<Integer> parseInt(Object value) {
        if (value instanceof Byte || value instanceof Short || value instanceof Integer || value instanceof Long) {
            long parsed = ((Number) value).longValue();
            if (parsed >= Integer.MIN_VALUE && parsed <= Integer.MAX_VALUE) {
                return Optional.of((int) parsed);
            }
            return Optional.empty();
        }
        if (value instanceof String text) {
            try {
                return Optional.of(Integer.parseInt(text.strip()));
            } catch (NumberFormatException exception) {
                return Optional.empty();
            }
        }
        return Optional.empty();
    }

    private <T extends Enum<T>> T enumValue(
            Map<String, Object> values,
            String key,
            Class<T> enumType,
            T defaultValue,
            PrintWriter err
    ) {
        if (!values.containsKey(key)) {
            return defaultValue;
        }

        Object value = values.get(key);
        if (value instanceof String text) {
            String normalized = text.strip().toUpperCase(Locale.ROOT);
            for (T candidate : enumType.getEnumConstants()) {
                if (candidate.name().equals(normalized)) {
                    return candidate;
                }
            }
        }

        warn(err, key, value, defaultValue.name().toLowerCase(Locale.ROOT));
        return defaultValue;
    }

    private Set<String> stringSet(
            Map<String, Object> values,
            String key,
            Set<String> defaultValue,
            PrintWriter err
    ) {
        if (!values.containsKey(key)) {
            return defaultValue;
        }

        Object value = values.get(key);
        if (!(value instanceof Iterable<?> iterable)) {
            warn(err, key, value, defaultValue);
            return defaultValue;
        }

        LinkedHashSet<String> result = new LinkedHashSet<>();
        for (Object item : iterable) {
            if (!(item instanceof String text)) {
                warn(err, key, value, defaultValue);
                return defaultValue;
            }
            String normalized = text.strip().toLowerCase(Locale.ROOT);
            if (!normalized.isEmpty()) {
                result.add(normalized);
            }
        }
        return result;
    }

    private void warn(PrintWriter err, String key, Object actual, Object defaultValue) {
        err.println("warning: invalid " + key + " value '" + actual + "', using default '" + defaultValue + "'");
        err.flush();
    }

    public static final class ProfileLoadException extends Exception {
        public ProfileLoadException(String message) {
            super(message);
        }
    }
}

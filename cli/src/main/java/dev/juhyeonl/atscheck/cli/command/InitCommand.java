package dev.juhyeonl.atscheck.cli.command;

import dev.juhyeonl.atscheck.cli.AtsCheckCli;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;
import picocli.CommandLine.Model.CommandSpec;

@Command(
        name = "init",
        mixinStandardHelpOptions = true,
        description = "Create a default ats-check profile.")
public final class InitCommand implements Callable<Integer> {
    private static final String TEMPLATE = """
            # ats-check profile
            # https://github.com/juhyeonl/ats-check

            years_experience: 2         # your years of professional experience
            years_tolerance: 1          # how many years above yours still counts as a match
            max_seniority: mid          # junior | mid | senior | lead
            languages: [english]        # languages you can work in
            degree: bachelor            # none | bachelor | master | phd
            skills:
              - java
              - spring boot
            """;

    @Option(names = "--force", description = "Overwrite an existing profile.yml.")
    private boolean force;

    @Spec
    private CommandSpec spec;

    private final Map<String, String> environment;
    private final Path homeDirectory;

    public InitCommand(Map<String, String> environment, Path homeDirectory) {
        this.environment = Map.copyOf(Objects.requireNonNull(environment, "environment"));
        this.homeDirectory = Objects.requireNonNull(homeDirectory, "homeDirectory");
    }

    @Override
    public Integer call() throws Exception {
        PrintWriter out = spec.commandLine().getOut();
        PrintWriter err = spec.commandLine().getErr();
        Path profilePath = profilePath();

        if (Files.exists(profilePath) && !force) {
            err.println("profile already exists: " + profilePath + " (use --force to overwrite)");
            err.flush();
            return AtsCheckCli.EXIT_USAGE;
        }

        Files.createDirectories(profilePath.getParent());
        Files.writeString(profilePath, TEMPLATE, StandardCharsets.UTF_8);
        out.println(profilePath);
        out.flush();
        return AtsCheckCli.EXIT_APPLY;
    }

    private Path profilePath() {
        String xdgConfigHome = environment.get("XDG_CONFIG_HOME");
        if (xdgConfigHome != null && !xdgConfigHome.isBlank()) {
            return Path.of(xdgConfigHome).resolve("ats-check").resolve("profile.yml");
        }
        return homeDirectory.resolve(".config").resolve("ats-check").resolve("profile.yml");
    }
}

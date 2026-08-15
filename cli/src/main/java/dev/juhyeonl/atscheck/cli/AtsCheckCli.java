package dev.juhyeonl.atscheck.cli;

import dev.juhyeonl.atscheck.cli.command.CheckCommand;
import dev.juhyeonl.atscheck.cli.config.ProfileLoader;
import java.io.InputStream;
import java.io.PrintWriter;
import java.util.concurrent.Callable;
import java.util.function.BooleanSupplier;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.ParameterException;
import picocli.CommandLine.Spec;

@Command(
        name = "ats-check",
        mixinStandardHelpOptions = true,
        version = "ats-check 0.1.0-SNAPSHOT",
        description = "Check a single job posting against your ATS profile.")
public final class AtsCheckCli implements Callable<Integer> {
    public static final int EXIT_APPLY = 0;
    public static final int EXIT_REVIEW = 1;
    public static final int EXIT_SKIP = 2;
    public static final int EXIT_USAGE = 64;
    public static final int EXIT_INTERNAL = 70;

    @Spec
    private CommandSpec spec;

    @Mixin
    private CheckCommand checkCommand;

    private final InputStream stdin;
    private final BooleanSupplier stdinIsPiped;

    public AtsCheckCli() {
        this(System.in, () -> System.console() == null, new ProfileLoader());
    }

    public AtsCheckCli(InputStream stdin, BooleanSupplier stdinIsPiped, ProfileLoader profileLoader) {
        this.stdin = stdin;
        this.stdinIsPiped = stdinIsPiped;
        this.checkCommand = new CheckCommand(profileLoader);
    }

    public static void main(String[] args) {
        int exitCode = commandLine().execute(args);
        System.exit(exitCode);
    }

    public static CommandLine commandLine() {
        return commandLine(System.in, () -> System.console() == null, new ProfileLoader());
    }

    public static CommandLine commandLine(
            InputStream stdin,
            BooleanSupplier stdinIsPiped,
            ProfileLoader profileLoader
    ) {
        CommandLine commandLine = new CommandLine(new AtsCheckCli(stdin, stdinIsPiped, profileLoader));
        commandLine.setParameterExceptionHandler(AtsCheckCli::handleParameterException);
        commandLine.setExecutionExceptionHandler(AtsCheckCli::handleExecutionException);
        return commandLine;
    }

    @Override
    public Integer call() throws Exception {
        return checkCommand.execute(new CheckCommand.Context(
                stdin,
                stdinIsPiped.getAsBoolean(),
                spec.commandLine().getOut(),
                spec.commandLine().getErr(),
                spec.commandLine()
        ));
    }

    private static int handleParameterException(ParameterException exception, String[] args) {
        CommandLine commandLine = exception.getCommandLine();
        PrintWriter err = commandLine.getErr();
        err.println(exception.getMessage());
        commandLine.usage(err);
        err.flush();
        return EXIT_USAGE;
    }

    private static int handleExecutionException(
            Exception exception,
            CommandLine commandLine,
            CommandLine.ParseResult parseResult
    ) {
        PrintWriter err = commandLine.getErr();
        err.println("internal error: " + messageFor(exception));
        if (isDebug(commandLine)) {
            exception.printStackTrace(err);
        }
        err.flush();
        return EXIT_INTERNAL;
    }

    private static boolean isDebug(CommandLine commandLine) {
        Object command = commandLine.getCommand();
        return command instanceof AtsCheckCli cli && cli.checkCommand.isDebug();
    }

    private static String messageFor(Throwable throwable) {
        String message = throwable.getMessage();
        if (message == null || message.isBlank()) {
            return throwable.getClass().getSimpleName();
        }
        return message;
    }
}

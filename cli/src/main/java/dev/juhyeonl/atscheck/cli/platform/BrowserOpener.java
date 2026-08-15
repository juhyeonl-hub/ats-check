package dev.juhyeonl.atscheck.cli.platform;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

@FunctionalInterface
public interface BrowserOpener {
    void open(String url) throws BrowserOpenException;

    static BrowserOpener system() {
        return new ExternalCommandBrowserOpener(Duration.ofSeconds(5));
    }

    final class BrowserOpenException extends Exception {
        public BrowserOpenException(String message) {
            super(message);
        }
    }

    final class ExternalCommandBrowserOpener implements BrowserOpener {
        private final Duration timeout;
        private final List<List<String>> commands;

        public ExternalCommandBrowserOpener(Duration timeout) {
            this.timeout = Objects.requireNonNull(timeout, "timeout");
            this.commands = List.of(
                    List.of("wslview"),
                    List.of("xdg-open"),
                    List.of("open"),
                    List.of("cmd.exe", "/c", "start", "")
            );
        }

        @Override
        public void open(String url) throws BrowserOpenException {
            Objects.requireNonNull(url, "url");

            List<String> failures = new ArrayList<>();
            for (List<String> baseCommand : commands) {
                List<String> command = new ArrayList<>(baseCommand);
                command.add(url);
                CommandStatus status = run(command);
                if (status.succeeded()) {
                    return;
                }
                failures.add(status.failure());
            }
            throw new BrowserOpenException(String.join("; ", failures));
        }

        private CommandStatus run(List<String> command) {
            Process process;
            try {
                process = new ProcessBuilder(command)
                        .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                        .redirectError(ProcessBuilder.Redirect.DISCARD)
                        .start();
            } catch (IOException exception) {
                return CommandStatus.failure(command.getFirst() + " unavailable");
            }

            try {
                boolean finished = process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);
                if (!finished) {
                    process.destroyForcibly();
                    return CommandStatus.failure(command.getFirst() + " timed out");
                }
                if (process.exitValue() == 0) {
                    return CommandStatus.ok();
                }
                return CommandStatus.failure(command.getFirst() + " exited " + process.exitValue());
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                process.destroyForcibly();
                return CommandStatus.failure(command.getFirst() + " interrupted");
            }
        }
    }

    record CommandStatus(boolean succeeded, String failure) {
        static CommandStatus ok() {
            return new CommandStatus(true, "");
        }

        static CommandStatus failure(String failure) {
            return new CommandStatus(false, failure);
        }
    }
}

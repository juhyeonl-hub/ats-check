package dev.juhyeonl.atscheck.cli.platform;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@FunctionalInterface
public interface ClipboardReader {
    String read() throws ClipboardReadException;

    static ClipboardReader system() {
        return new ExternalCommandClipboardReader(Duration.ofSeconds(5));
    }

    final class ClipboardReadException extends Exception {
        public ClipboardReadException(String message) {
            super(message);
        }
    }

    final class ExternalCommandClipboardReader implements ClipboardReader {
        private final Duration timeout;
        private final List<List<String>> commands;

        public ExternalCommandClipboardReader(Duration timeout) {
            this.timeout = Objects.requireNonNull(timeout, "timeout");
            this.commands = List.of(
                    List.of("wl-paste"),
                    List.of("xclip", "-selection", "clipboard", "-o"),
                    List.of("pbpaste"),
                    List.of("powershell.exe", "-c", "Get-Clipboard")
            );
        }

        @Override
        public String read() throws ClipboardReadException {
            List<String> failures = new ArrayList<>();
            for (List<String> command : commands) {
                CommandResult result = run(command);
                if (result.output().isPresent()) {
                    return result.output().get();
                }
                failures.add(result.failure());
            }
            throw new ClipboardReadException(String.join("; ", failures));
        }

        private CommandResult run(List<String> command) {
            Process process;
            try {
                process = new ProcessBuilder(command)
                        .redirectError(ProcessBuilder.Redirect.DISCARD)
                        .start();
            } catch (IOException exception) {
                return CommandResult.failure(command.getFirst() + " unavailable");
            }

            ExecutorService executor = Executors.newSingleThreadExecutor();
            Future<byte[]> stdout = executor.submit(() -> process.getInputStream().readAllBytes());
            try {
                boolean finished = process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);
                if (!finished) {
                    process.destroyForcibly();
                    stdout.cancel(true);
                    return CommandResult.failure(command.getFirst() + " timed out");
                }

                byte[] output = stdout.get(1, TimeUnit.SECONDS);
                if (process.exitValue() == 0) {
                    return CommandResult.success(new String(output, StandardCharsets.UTF_8));
                }
                return CommandResult.failure(command.getFirst() + " exited " + process.exitValue());
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                process.destroyForcibly();
                return CommandResult.failure(command.getFirst() + " interrupted");
            } catch (ExecutionException | TimeoutException exception) {
                process.destroyForcibly();
                return CommandResult.failure(command.getFirst() + " failed to read output");
            } finally {
                executor.shutdownNow();
            }
        }
    }

    record CommandResult(Optional<String> output, String failure) {
        static CommandResult success(String output) {
            return new CommandResult(Optional.of(output), "");
        }

        static CommandResult failure(String failure) {
            return new CommandResult(Optional.empty(), failure);
        }
    }
}

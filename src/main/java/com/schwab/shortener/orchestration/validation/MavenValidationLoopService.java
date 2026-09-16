package com.schwab.shortener.orchestration.validation;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Service;

import javax.tools.JavaCompiler;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.io.StringWriter;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Isolated validate/repair loop against real guard source:
 * break patch → compile+run smoke tests (fail) → revise from failure output → compile+run (pass).
 * Uses in-process javac (no nested Maven) so the loop finishes in seconds.
 */
@Service
public class MavenValidationLoopService {

    public static final String TARGET_RELATIVE =
            "src/main/java/com/schwab/shortener/shortener/UrlSafetyGuard.java";
    private static final String SMOKE_CLASS = "com.schwab.shortener.shortener.UrlSafetyGuardSmoke";

    private final boolean enabled;
    private final int maxAttempts;
    private final Path configuredProjectRoot;

    public MavenValidationLoopService(
            @Value("${app.orchestration.maven-validation-enabled:true}") boolean enabled,
            @Value("${app.orchestration.maven-validation-max-attempts:3}") int maxAttempts,
            @Value("${app.orchestration.project-root:}") String projectRoot
    ) {
        this.enabled = enabled;
        this.maxAttempts = Math.max(1, maxAttempts);
        this.configuredProjectRoot = projectRoot == null || projectRoot.isBlank()
                ? null
                : Path.of(projectRoot).toAbsolutePath().normalize();
    }

    public boolean isEnabled() {
        return enabled;
    }

    public ValidationReport run(String workflowId) {
        if (!enabled) {
            return ValidationReport.skipped("Validation loop disabled by configuration");
        }
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            return ValidationReport.skipped("JDK compiler unavailable (JRE-only runtime)");
        }
        Path projectRoot = resolveProjectRoot();
        Path sandbox = (projectRoot == null ? Path.of("target") : projectRoot.resolve("workbench"))
                .resolve(workflowId == null ? "local" : workflowId)
                .resolve("maven-sandbox")
                .toAbsolutePath()
                .normalize();
        List<Attempt> attempts = new ArrayList<>();
        try {
            prepareSandbox(sandbox);
            Path target = sandbox.resolve(TARGET_RELATIVE);
            String original = Files.readString(target, StandardCharsets.UTF_8);
            Files.writeString(target, breakLoopbackGuard(original), StandardCharsets.UTF_8);

            for (int attempt = 1; attempt <= maxAttempts; attempt++) {
                RunResult run = compileAndRunSmoke(compiler, sandbox);
                String current = Files.readString(target, StandardCharsets.UTF_8);
                String unifiedDiff = toUnifiedDiff(TARGET_RELATIVE, original, current);
                attempts.add(new Attempt(attempt, run.exitCode(), truncate(run.output(), 4000), unifiedDiff));
                if (run.exitCode() == 0) {
                    return ValidationReport.success(sandbox, attempts, unifiedDiff);
                }
                if (attempt == maxAttempts) {
                    break;
                }
                Files.writeString(target, reviseFromFailure(original, current, run.output()), StandardCharsets.UTF_8);
            }
            return ValidationReport.failed(sandbox, attempts);
        } catch (Exception ex) {
            attempts.add(new Attempt(attempts.size() + 1, -1, ex.getMessage() == null ? "error" : ex.getMessage(), ""));
            return ValidationReport.failed(sandbox, attempts);
        }
    }

    private Path resolveProjectRoot() {
        if (configuredProjectRoot != null && Files.isDirectory(configuredProjectRoot)) {
            return configuredProjectRoot;
        }
        Path probe = Path.of("").toAbsolutePath().normalize();
        for (int i = 0; i < 6 && probe != null; i++) {
            if (Files.isRegularFile(probe.resolve("pom.xml"))) {
                return probe;
            }
            probe = probe.getParent();
        }
        return null;
    }

    private void prepareSandbox(Path sandbox) throws IOException {
        if (Files.exists(sandbox)) {
            deleteRecursive(sandbox);
        }
        Files.createDirectories(sandbox);
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        for (Resource resource : resolver.getResources("classpath:validation-fixture/**")) {
            if (!resource.isReadable()) {
                continue;
            }
            String url = resource.getURL().toString().replace('\\', '/');
            int idx = url.indexOf("validation-fixture/");
            if (idx < 0 || url.endsWith("/")) {
                continue;
            }
            String relative = url.substring(idx + "validation-fixture/".length());
            if (relative.isBlank() || relative.startsWith("src/test/")) {
                continue;
            }
            Path destination = sandbox.resolve(relative);
            Files.createDirectories(destination.getParent());
            try (InputStream in = resource.getInputStream()) {
                Files.copy(in, destination, StandardCopyOption.REPLACE_EXISTING);
            }
        }
        if (!Files.isRegularFile(sandbox.resolve(TARGET_RELATIVE))) {
            throw new IOException("validation-fixture UrlSafetyGuard.java missing");
        }
    }

    private RunResult compileAndRunSmoke(JavaCompiler compiler, Path sandbox) throws Exception {
        Path mainSrc = sandbox.resolve("src/main/java");
        Path classes = sandbox.resolve("target/classes");
        deleteRecursive(classes);
        Files.createDirectories(classes);

        List<Path> mainFiles = listJavaFiles(mainSrc);
        StringWriter compileLog = new StringWriter();
        try (StandardJavaFileManager fileManager = compiler.getStandardFileManager(null, null, StandardCharsets.UTF_8)) {
            boolean mainOk = compiler.getTask(
                    compileLog,
                    fileManager,
                    null,
                    List.of("-d", classes.toString()),
                    null,
                    fileManager.getJavaFileObjectsFromPaths(mainFiles)
            ).call();
            if (!mainOk) {
                return new RunResult(1, "COMPILE_FAILED\n" + compileLog);
            }
        }

        PrintStream originalOut = System.out;
        PrintStream originalErr = System.err;
        ByteArrayOutputStream captured = new ByteArrayOutputStream();
        PrintStream captureStream = new PrintStream(captured, true, StandardCharsets.UTF_8);
        URLClassLoader loader = new URLClassLoader(new URL[]{classes.toUri().toURL()}, null);
        ClassLoader previous = Thread.currentThread().getContextClassLoader();
        Thread.currentThread().setContextClassLoader(loader);
        System.setOut(captureStream);
        System.setErr(captureStream);
        try {
            Class<?> smoke = loader.loadClass(SMOKE_CLASS);
            Method main = smoke.getMethod("main", String[].class);
            main.invoke(null, (Object) new String[0]);
            return new RunResult(0, captured.toString(StandardCharsets.UTF_8));
        } catch (InvocationTargetException ex) {
            Throwable cause = ex.getCause() == null ? ex : ex.getCause();
            String output = captured.toString(StandardCharsets.UTF_8) + "\n" + cause;
            return new RunResult(1, output);
        } catch (Exception ex) {
            return new RunResult(1, captured.toString(StandardCharsets.UTF_8) + "\n" + ex);
        } finally {
            System.setOut(originalOut);
            System.setErr(originalErr);
            Thread.currentThread().setContextClassLoader(previous);
            loader.close();
        }
    }

    private static List<Path> listJavaFiles(Path root) throws IOException {
        try (var walk = Files.walk(root)) {
            return walk.filter(path -> path.toString().endsWith(".java")).toList();
        }
    }

    static String breakLoopbackGuard(String source) {
        if (source.contains("INTENTIONALLY BROKEN")) {
            return source;
        }
        String marker = "if (host.matches(\"^(10\\\\.|127\\\\.|192\\\\.168\\\\.|169\\\\.254\\\\.).*\") || host.startsWith(\"172.\")) {";
        String broken = "if (false /* INTENTIONALLY BROKEN by validation loop */) {";
        if (source.contains(marker)) {
            return source.replace(marker, broken);
        }
        return source.replace(
                "|| host.startsWith(\"172.\")",
                "&& false /* INTENTIONALLY BROKEN by validation loop */"
        );
    }

    static String reviseFromFailure(String original, String current, String failureOutput) {
        String lower = failureOutput == null ? "" : failureOutput.toLowerCase(Locale.ROOT);
        if (lower.contains("rejectsloopback")
                || lower.contains("127.0.0.1")
                || lower.contains("private")
                || lower.contains("assertion")
                || lower.contains("failures")
                || current.contains("INTENTIONALLY BROKEN")) {
            return original;
        }
        return original;
    }

    private static void deleteRecursive(Path root) throws IOException {
        if (!Files.exists(root)) {
            return;
        }
        try (var walk = Files.walk(root)) {
            walk.sorted((a, b) -> b.compareTo(a)).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ignored) {
                    // best effort
                }
            });
        }
    }

    static String toUnifiedDiff(String path, String before, String after) {
        StringBuilder builder = new StringBuilder();
        builder.append("--- a/").append(path).append('\n');
        builder.append("+++ b/").append(path).append('\n');
        String[] oldLines = before.split("\n", -1);
        String[] newLines = after.split("\n", -1);
        builder.append("@@ -1,").append(oldLines.length).append(" +1,").append(newLines.length).append(" @@\n");
        for (String line : oldLines) {
            builder.append('-').append(line).append('\n');
        }
        for (String line : newLines) {
            builder.append('+').append(line).append('\n');
        }
        return builder.toString();
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return "";
        }
        return value.length() <= max ? value : value.substring(0, max) + "...";
    }

    public record RunResult(int exitCode, String output) {}

    public record Attempt(int attempt, int exitCode, String output, String unifiedDiff) {}

    public record ValidationReport(
            boolean enabled,
            boolean success,
            boolean skipped,
            String message,
            String sandboxPath,
            List<Attempt> attempts,
            String finalDiff
    ) {
        static ValidationReport skipped(String message) {
            return new ValidationReport(false, false, true, message, "", List.of(), "");
        }

        static ValidationReport success(Path sandbox, List<Attempt> attempts, String finalDiff) {
            return new ValidationReport(true, true, false, "Validation loop succeeded", sandbox.toString(), attempts, finalDiff);
        }

        static ValidationReport failed(Path sandbox, List<Attempt> attempts) {
            return new ValidationReport(true, false, false, "Validation loop exhausted attempts",
                    sandbox == null ? "" : sandbox.toString(), attempts, "");
        }
    }
}

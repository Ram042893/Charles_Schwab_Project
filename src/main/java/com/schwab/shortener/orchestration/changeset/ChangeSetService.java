package com.schwab.shortener.orchestration.changeset;

import com.schwab.shortener.common.SimpleJson;
import com.schwab.shortener.orchestration.domain.ScenarioType;
import com.schwab.shortener.orchestration.domain.WorkflowInstance;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class ChangeSetService {

    public enum Mode {
        FULL, REDUCED, REPAIR
    }

    private final ProposedChangeSetRepository repository;
    private final Path workbenchRoot;

    public ChangeSetService(
            ProposedChangeSetRepository repository,
            @Value("${app.orchestration.workbench-dir:workbench}") String workbenchDir
    ) {
        this.repository = repository;
        Path configured = Path.of(workbenchDir).toAbsolutePath().normalize();
        this.workbenchRoot = ensureWritable(configured);
    }

    private static Path ensureWritable(Path configured) {
        try {
            Files.createDirectories(configured);
            Path probe = configured.resolve(".write-probe");
            Files.writeString(probe, "ok");
            Files.deleteIfExists(probe);
            return configured;
        } catch (IOException ex) {
            Path fallback = Path.of(System.getProperty("java.io.tmpdir"), "agentic-workbench").toAbsolutePath().normalize();
            try {
                Files.createDirectories(fallback);
            } catch (IOException ignored) {
                // still return fallback; generate() will surface a clear error
            }
            return fallback;
        }
    }

    public ProposedChangeSet generate(WorkflowInstance workflow, Mode mode, String repairHint) {
        try {
            Path root = workbenchRoot.resolve(workflow.getId());
            Files.createDirectories(root);
            boolean alias = mode != Mode.REDUCED && workflow.getScenarioType() != ScenarioType.GREENFIELD;
            boolean expiration = alias;
            boolean export = alias;
            if (mode == Mode.REPAIR && repairHint != null && repairHint.toLowerCase(Locale.ROOT).contains("alias")) {
                alias = true;
                expiration = true;
                export = true;
            }

            String requirement = workflow.getRequirementText() == null ? "" : workflow.getRequirementText().trim();
            Map<String, String> files = new LinkedHashMap<>();
            files.put(
                    "src/main/java/com/schwab/shortener/generated/WorkflowCapabilityProfile.java",
                    capabilityProfile(workflow.getId(), requirement, alias, expiration, export, mode)
            );
            files.put(
                    "docs/API_DELTA.md",
                    apiDelta(requirement, alias, expiration, export, mode)
            );
            files.put(
                    "config/feature-flags.proposed.yml",
                    featureFlagYaml(alias, expiration, export)
            );

            StringBuilder diff = new StringBuilder();
            List<String> written = new ArrayList<>();
            for (Map.Entry<String, String> entry : files.entrySet()) {
                Path target = root.resolve(entry.getKey());
                Files.createDirectories(target.getParent());
                String previous = Files.exists(target) ? Files.readString(target, StandardCharsets.UTF_8) : "";
                Files.writeString(target, entry.getValue(), StandardCharsets.UTF_8);
                diff.append(unifiedDiff(entry.getKey(), previous, entry.getValue()));
                written.add(entry.getKey());
            }

            ProposedChangeSet changeSet = new ProposedChangeSet();
            changeSet.setWorkflowId(workflow.getId());
            changeSet.setMode(mode.name());
            changeSet.setUnifiedDiff(diff.toString());
            changeSet.setFilesJson(SimpleJson.literal(written));
            changeSet.setContentHash(sha256(diff.toString()));
            return repository.save(changeSet);
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to generate reviewable change set: " + ex.getMessage(), ex);
        }
    }

    public void discard(String workflowId) {
        try {
            Path root = workbenchRoot.resolve(workflowId);
            if (Files.exists(root)) {
                try (var walk = Files.walk(root)) {
                    walk.sorted((a, b) -> b.compareTo(a)).forEach(path -> {
                        try {
                            Files.deleteIfExists(path);
                        } catch (IOException ignored) {
                            // best-effort rollback of generated files
                        }
                    });
                }
            }
        } catch (IOException ignored) {
            // best-effort
        }
    }

    public Path workbenchRoot() {
        return workbenchRoot;
    }

    private static String capabilityProfile(
            String workflowId,
            String requirement,
            boolean alias,
            boolean expiration,
            boolean export,
            Mode mode
    ) {
        return """
                package com.schwab.shortener.generated;

                /**
                 * Generated by the agentic ImplementationAgent for workflow %s.
                 * Mode=%s. Reviewers approve the accompanying unified diff before release.
                 */
                public final class WorkflowCapabilityProfile {
                    public static final String WORKFLOW_ID = "%s";
                    public static final String REQUIREMENT = "%s";
                    public static final boolean CUSTOM_ALIAS = %s;
                    public static final boolean EXPIRATION = %s;
                    public static final boolean ANALYTICS_EXPORT = %s;
                    public static final String GENERATION_MODE = "%s";

                    private WorkflowCapabilityProfile() {
                    }
                }
                """.formatted(
                workflowId,
                mode.name(),
                workflowId,
                escapeJava(requirement),
                alias,
                expiration,
                export,
                mode.name()
        );
    }

    private static String apiDelta(String requirement, boolean alias, boolean expiration, boolean export, Mode mode) {
        return """
                # API delta

                Requirement: %s
                Mode: %s

                - customAlias request field: %s
                - expiresAt request field: %s
                - analytics CSV export: %s
                """.formatted(requirement, mode.name(), alias, expiration, export);
    }

    private static String featureFlagYaml(boolean alias, boolean expiration, boolean export) {
        return """
                feature_flags:
                  CUSTOM_ALIAS: %s
                  EXPIRATION: %s
                  ANALYTICS_EXPORT: %s
                """.formatted(alias, expiration, export);
    }

    private static String unifiedDiff(String path, String before, String after) {
        StringBuilder builder = new StringBuilder();
        builder.append("--- a/").append(path).append('\n');
        builder.append("+++ b/").append(path).append('\n');
        String[] oldLines = before.isEmpty() ? new String[0] : before.split("\n", -1);
        String[] newLines = after.split("\n", -1);
        builder.append("@@ -1,").append(Math.max(oldLines.length, 1)).append(" +1,").append(newLines.length).append(" @@\n");
        for (String line : oldLines) {
            builder.append('-').append(line).append('\n');
        }
        for (String line : newLines) {
            builder.append('+').append(line).append('\n');
        }
        return builder.toString();
    }

    private static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException ex) {
            return Integer.toHexString(value.hashCode());
        }
    }

    private static String escapeJava(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\r", "\\r").replace("\n", "\\n");
    }
}

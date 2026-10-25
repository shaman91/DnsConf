package com.novibe.dns.next_dns;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class VerifiedLeafRulesTest {
    private static final List<String> LEAVES = List.of("ai.google.dev", "aistudio.google.com",
            "copilot.microsoft.com", "designer.microsoft.com", "developer.nvidia.com",
            "opal.google", "opal.google.com", "stitch.withgoogle.com", "web.archive.org");

    @Test
    void changesExactlyNineLeavesAndKeepsUnverifiedParentsAndExceptions() throws Exception {
        String source = Files.readString(Path.of("openai-working")).replace("\r\n", "\n");
        String before = source;
        for (String name : LEAVES) {
            before = before.replace("eu.geohide.ru " + name + "\n", "37.230.192.51 " + name + "\n");
        }
        var baseline = RewriteDryRun.plan(List.of(), List.of(), List.of(before), true);
        var plan = RewriteDryRun.plan(baseline.expected(), List.of(), List.of(source), true);
        assertEquals(baseline.afterCount(), plan.afterCount());
        assertEquals(9, plan.changes().size());
        for (var change : plan.changes()) {
            assertTrue(LEAVES.contains(change.name()));
            assertEquals("37.230.192.51", change.before());
            assertEquals("eu.geohide.ru", change.after());
        }
        assertTrue(RewriteDryRun.plan(plan.expected(), List.of(), List.of(source), true).changes().isEmpty());
        assertEquals(baseline.expected(), RewriteDryRun.plan(plan.expected(), List.of(), List.of(before), true).expected());
        for (String preserved : List.of("api.console.anthropic.com", "auth.anthropic.com",
                "platform.claude.ai", "statsig.anthropic.com", "notebooklm.google.com", "grok.com")) {
            assertEquals(baseline.expected().stream().filter(r -> r.name().equals(preserved)).toList(),
                    plan.expected().stream().filter(r -> r.name().equals(preserved)).toList(), preserved);
        }
    }
}

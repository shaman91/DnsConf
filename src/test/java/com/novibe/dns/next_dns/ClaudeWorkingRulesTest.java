package com.novibe.dns.next_dns;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClaudeWorkingRulesTest {
    private static final List<String> VALIDATED = List.of(
            "a-api.anthropic.com", "a-cdn.anthropic.com", "a.claude.ai",
            "anthropic.com", "api.anthropic.com", "assets-proxy.anthropic.com",
            "claude.ai", "claude.com", "console.anthropic.com", "downloads.claude.ai",
            "platform.claude.com", "s-cdn.anthropic.com", "status.anthropic.com", "support.anthropic.com");
    private static final List<String> PRESERVED = List.of(
            "api.console.anthropic.com", "api.claude.ai", "api.claude.com", "auth.anthropic.com",
            "claudemcpclient.com", "platform.claude.ai", "statsig.anthropic.com");

    @Test
    void childrenAndUnverifiedExceptionsSurviveParentsAndLaterSources() throws Exception {
        String source = Files.readString(Path.of("openai-working"));
        var plan = RewriteDryRun.plan(List.of(), List.of(), List.of(source, """
                192.0.2.1 api.anthropic.com
                192.0.2.2 auth.anthropic.com
                192.0.2.3 static.claude.ai
                192.0.2.4 bridge.claudeusercontent.com
                192.0.2.5 notclaude.ai
                """));
        Map<String, String> targets = plan.expected().stream().collect(
                Collectors.toMap(rule -> rule.name(), rule -> rule.content()));
        for (String name : VALIDATED) assertEquals("eu.geohide.ru", targets.get(name), name);
        assertEquals("eu.geohide.ru", targets.get("claudeusercontent.com"));
        for (String name : PRESERVED) assertEquals("37.230.192.51", targets.get(name), name);
        assertEquals("192.0.2.5", targets.get("notclaude.ai"));
        assertFalse(targets.containsKey("bridge.claudeusercontent.com"));
        assertFalse(targets.containsKey("static.claude.ai"));
        for (String name : List.of("openai.com", "chatgpt.com", "oaiusercontent.com")) {
            assertEquals("ai-pool.comss.one", targets.get(name), name);
        }
    }

    @Test
    void replacesOnlyFifteenTargetsWithoutAddingOrDeletingRuleNames() throws Exception {
        String source = Files.readString(Path.of("openai-working"));
        String previous = source.lines()
                .filter(line -> !line.equals("eu.geohide.ru claudeusercontent.com"))
                .collect(Collectors.joining("\n"));
        for (String name : VALIDATED) {
            previous = previous.replace("eu.geohide.ru " + name + "\n", "37.230.192.51 " + name + "\n");
        }
        String later = "193.233.112.67 claudeusercontent.com\n192.0.2.5 unrelated.example";
        var before = RewriteDryRun.plan(List.of(), List.of(), List.of(previous, later));
        var after = RewriteDryRun.plan(before.expected(), List.of(), List.of(source, later));
        assertEquals(before.afterCount(), after.afterCount());
        assertEquals(15, after.changes().size());
        assertEquals(15, after.changes().stream().filter(change -> change.before() != null).count());
        assertTrue(after.changes().stream().allMatch(change -> change.after() != null));
        for (var change : after.changes()) {
            assertTrue(VALIDATED.contains(change.name()) || change.name().equals("claudeusercontent.com"));
            assertEquals("eu.geohide.ru", change.after());
        }
        assertTrue(RewriteDryRun.plan(after.expected(), List.of(), List.of(source, later)).changes().isEmpty());
    }
}

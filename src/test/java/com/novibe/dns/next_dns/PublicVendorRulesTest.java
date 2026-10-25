package com.novibe.dns.next_dns;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PublicVendorRulesTest {
    private static final List<String> VENDORS = List.of("elgato.com", "sketchup.com",
            "sketchup.trimble.com", "tableau.com", "xerox.com");

    @Test
    void replacesOnlyFiveNamesPreservingOtherWebRoutesLoginAndUnverifiedRules() throws Exception {
        String source = Files.readString(Path.of("openai-working")).replace("\r\n", "\n");
        String before = source;
        for (String name : VENDORS) {
            before = before.replace("eu.geohide.ru " + name + "\n", "37.230.192.51 " + name + "\n");
        }
        var baseline = RewriteDryRun.plan(List.of(), List.of(), List.of(before), true);
        var updated = RewriteDryRun.plan(baseline.expected(), List.of(), List.of(source), true);
        assertEquals(baseline.afterCount(), updated.afterCount());
        assertEquals(5, updated.changes().size());
        for (var change : updated.changes()) {
            assertTrue(VENDORS.contains(change.name()));
            assertEquals("37.230.192.51", change.before());
            assertEquals("eu.geohide.ru", change.after());
        }
        assertTrue(RewriteDryRun.plan(updated.expected(), List.of(), List.of(source), true).changes().isEmpty());
        assertEquals(baseline.expected(), RewriteDryRun.plan(updated.expected(), List.of(), List.of(before), true).expected());
        for (String name : List.of("id.trimble.com", "amd.com", "intel.com", "qwant.com", "weather.com",
                "auth.anthropic.com", "api.console.anthropic.com", "chatgpt.com", "developers.openai.com")) {
            assertEquals(baseline.expected().stream().filter(r -> r.name().equals(name)).toList(),
                    updated.expected().stream().filter(r -> r.name().equals(name)).toList(), name);
        }
    }
}

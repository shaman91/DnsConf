package com.novibe.dns.next_dns;

import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class HermesInstallerRuleTest {
    @Test
    void installerRuleIsIsolatedIdempotentAndReversible() throws Exception {
        String source = Files.readString(Path.of("openai-working")).replace("\r\n", "\n");
        String rule = "dns.bezmezhau.com hermes-assets.nousresearch.com\n";
        assertEquals(1, source.split(rule, -1).length - 1);
        String before = source.replace(rule, "");
        var baseline = RewriteDryRun.plan(List.of(), List.of(), List.of(before), true);
        var updated = RewriteDryRun.plan(baseline.expected(), List.of(), List.of(source), true);
        assertEquals(List.of(new RewriteDryRun.Change("hermes-assets.nousresearch.com", null,
                "dns.bezmezhau.com")), updated.changes());
        assertTrue(RewriteDryRun.plan(updated.expected(), List.of(), List.of(source), true).changes().isEmpty());
        assertEquals(baseline.expected(), RewriteDryRun.plan(updated.expected(), List.of(), List.of(before), true).expected());
    }
}


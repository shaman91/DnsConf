package com.novibe.dns.next_dns;

import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class HermesWorkingRuleTest {
    @Test
    void addsOnlyHermesAndSharedAssetsWithReversibleIdempotentRules() throws Exception {
        String source = Files.readString(Path.of("openai-working")).replace("\r\n", "\n");
        String rule = "dns.bezmezhau.com hermes-agent.nousresearch.com\n";
        String assets = "dns.bezmezhau.com web-assets.nousresearch.com\n";
        assertEquals(1, source.split(rule, -1).length - 1);
        assertEquals(1, source.split(assets, -1).length - 1);
        String before = source.replace(rule, "").replace(assets, "");
        var baseline = RewriteDryRun.plan(List.of(), List.of(), List.of(before), true);
        var updated = RewriteDryRun.plan(baseline.expected(), List.of(), List.of(source), true);
        assertEquals(baseline.afterCount() + 2, updated.afterCount());
        assertEquals(List.of(new RewriteDryRun.Change("hermes-agent.nousresearch.com", null,
                "dns.bezmezhau.com"), new RewriteDryRun.Change("web-assets.nousresearch.com", null,
                "dns.bezmezhau.com")), updated.changes());
        assertTrue(RewriteDryRun.plan(updated.expected(), List.of(), List.of(source), true).changes().isEmpty());
        assertEquals(baseline.expected(), RewriteDryRun.plan(updated.expected(), List.of(), List.of(before), true).expected());
    }
}

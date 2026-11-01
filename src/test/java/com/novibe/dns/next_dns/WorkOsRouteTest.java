package com.novibe.dns.next_dns;

import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class WorkOsRouteTest {
    @Test
    void replacesOnlyWorkOsAndIsIdempotentAndReversible() throws Exception {
        String source = Files.readString(Path.of("openai-working")).replace("\r\n", "\n");
        String newRule = "dns.bezmezhau.com api.workos.com\n";
        String oldRule = "37.230.192.51 api.workos.com\n";
        assertEquals(1, source.split(newRule, -1).length - 1);
        assertFalse(source.contains(oldRule));
        String before = source.replace(newRule, oldRule);
        var baseline = RewriteDryRun.plan(List.of(), List.of(), List.of(before), true);
        var updated = RewriteDryRun.plan(baseline.expected(), List.of(), List.of(source), true);
        assertEquals(List.of(new RewriteDryRun.Change("api.workos.com", "37.230.192.51",
                "dns.bezmezhau.com")), updated.changes());
        assertTrue(RewriteDryRun.plan(updated.expected(), List.of(), List.of(source), true).changes().isEmpty());
        assertEquals(baseline.expected(), RewriteDryRun.plan(updated.expected(), List.of(), List.of(before), true).expected());
    }
}


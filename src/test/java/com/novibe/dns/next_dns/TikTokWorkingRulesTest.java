package com.novibe.dns.next_dns;

import com.novibe.dns.next_dns.http.dto.request.CreateRewriteDto;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TikTokWorkingRulesTest {
    @Test
    void changesOnlyExactWwwWhileKeepingRootExcludedAndOtherTargetsUnchanged() throws Exception {
        String current = Files.readString(Path.of("tiktok-working"));
        String before = current.replace("217.60.179.6", "103.27.157.38");
        List<String> exclusions = List.of("=tiktok.com");
        String later = "192.0.2.1 www.tiktok.com\n192.0.2.2 unrelated.example";
        var original = RewriteDryRun.plan(List.of(), exclusions, List.of(before, later), true);
        var updated = RewriteDryRun.plan(original.expected(), exclusions, List.of(current, later), true);
        assertEquals(2, updated.afterCount());
        assertEquals(List.of(new RewriteDryRun.Change("www.tiktok.com", "103.27.157.38", "217.60.179.6")),
                updated.changes());
        assertEquals(List.of(new CreateRewriteDto("unrelated.example", "192.0.2.2"),
                new CreateRewriteDto("www.tiktok.com", "217.60.179.6")), updated.expected());
        assertTrue(RewriteDryRun.plan(updated.expected(), exclusions, List.of(current, later), true).changes().isEmpty());
        var rollback = RewriteDryRun.plan(updated.expected(), exclusions, List.of(before, later), true);
        assertEquals(original.expected(), rollback.expected());
    }
}

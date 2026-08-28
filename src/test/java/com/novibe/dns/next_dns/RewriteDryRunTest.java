package com.novibe.dns.next_dns;

import com.novibe.dns.next_dns.http.dto.request.CreateRewriteDto;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RewriteDryRunTest {
    @Test
    void showsAddsChangesRemovalsWithoutNetworkAndIsIdempotent() {
        var old = List.of(new CreateRewriteDto("openai.com", "87.228.47.204"),
                new CreateRewriteDto("api.openai.com", "87.228.47.204"),
                new CreateRewriteDto("instagram.com", "1.2.3.4"));
        var sources = List.of("pool.example openai.com\npool.example chatgpt.com\n1.2.3.4 instagram.com",
                "87.228.47.204 api.openai.com");
        var plan = RewriteDryRun.plan(old, List.of(), sources);
        assertEquals(3, plan.beforeCount());
        assertEquals(3, plan.afterCount());
        assertEquals(List.of(new RewriteDryRun.Change("api.openai.com", "87.228.47.204", null),
                new RewriteDryRun.Change("openai.com", "87.228.47.204", "pool.example"),
                new RewriteDryRun.Change("chatgpt.com", null, "pool.example")), plan.changes());
        assertTrue(RewriteDryRun.plan(plan.expected(), List.of(), sources).changes().isEmpty());
    }
}

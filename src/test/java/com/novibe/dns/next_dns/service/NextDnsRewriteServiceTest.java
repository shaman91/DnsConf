package com.novibe.dns.next_dns.service;

import com.novibe.common.data_sources.ExcludeRedirectSettingsLoader;
import com.novibe.common.service.ExcludeRedirectCheckService;
import com.novibe.dns.next_dns.http.NextDnsRewriteClient;
import com.novibe.dns.next_dns.http.dto.request.CreateRewriteDto;
import com.novibe.dns.next_dns.http.dto.response.rewrite.RewriteDto;
import com.novibe.dns.next_dns.http.dto.response.rewrite.SingleRewriteResponse;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NextDnsRewriteServiceTest {

    @Test
    void keepsUnmanagedRewritesByDefault() {
        FakeRewriteClient client = new FakeRewriteClient(List.of(
                new RewriteDto("stale-id", "stale.example", "45.155.204.190")
        ));
        NextDnsRewriteService service = service(client, false);

        List<CreateRewriteDto> pending = service.cleanupOutdatedAndExcluded(new HashMap<>());

        assertTrue(client.deletedIds.isEmpty());
        assertTrue(pending.isEmpty());
    }

    @Test
    void removesUnmanagedRewritesWhenPruningIsEnabled() {
        FakeRewriteClient client = new FakeRewriteClient(List.of(
                new RewriteDto("stale-id", "stale.example", "45.155.204.190")
        ));
        NextDnsRewriteService service = service(client, true);

        List<CreateRewriteDto> pending = service.cleanupOutdatedAndExcluded(new HashMap<>());

        assertEquals(List.of("stale-id"), client.deletedIds);
        assertTrue(pending.isEmpty());
    }

    @Test
    void replacesChangedRewriteAndKeepsNewRequestPending() {
        FakeRewriteClient client = new FakeRewriteClient(List.of(
                new RewriteDto("old-id", "chat.example", "45.155.204.190")
        ));
        NextDnsRewriteService service = service(client, true);
        CreateRewriteDto replacement = new CreateRewriteDto("chat.example", "37.230.192.51");
        Map<String, CreateRewriteDto> requested = new HashMap<>(Map.of(replacement.name(), replacement));

        List<CreateRewriteDto> pending = service.cleanupOutdatedAndExcluded(requested);

        assertEquals(List.of("old-id"), client.deletedIds);
        assertEquals(List.of(replacement), pending);
    }

    private NextDnsRewriteService service(FakeRewriteClient client, boolean pruneUnmanagedRewrites) {
        ExcludeRedirectSettingsLoader loader = new ExcludeRedirectSettingsLoader() {
            @Override
            public List<String> loadIgnoredDomains() {
                return List.of();
            }
        };
        return new NextDnsRewriteService(
                client,
                new ExcludeRedirectCheckService(loader),
                pruneUnmanagedRewrites
        );
    }

    private static class FakeRewriteClient extends NextDnsRewriteClient {
        private final List<RewriteDto> existing;
        private final List<String> deletedIds = new ArrayList<>();

        private FakeRewriteClient(List<RewriteDto> existing) {
            this.existing = existing;
        }

        @Override
        public List<RewriteDto> fetchRewrites() {
            return existing;
        }

        @Override
        public SingleRewriteResponse deleteRewriteById(String id) {
            deletedIds.add(id);
            return null;
        }
    }
}

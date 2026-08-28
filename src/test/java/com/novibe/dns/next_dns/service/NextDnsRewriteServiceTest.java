package com.novibe.dns.next_dns.service;

import com.novibe.common.data_sources.ExcludeRedirectSettingsLoader;
import com.novibe.common.data_sources.HostsOverrideListsLoader;
import com.novibe.common.exception.UserInputException;
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
import static org.junit.jupiter.api.Assertions.assertThrows;

class NextDnsRewriteServiceTest {

    @Test
    void cnameParentSuppressesLaterChildrenButNotEarlierExceptionsOrSimilarSuffixes() {
        var service = service(new FakeRewriteClient(List.of()), true);
        var desired = service.buildNewRewrites(new HostsOverrideListsLoader().parseLists(List.of("""
                1.2.3.4 explicit.openai.com
                ai-pool.comss.one openai.com
                87.228.47.204 api.openai.com
                87.228.47.204 a.b.openai.com
                9.8.7.6 notopenai.com
                5.6.7.8 instagram.com
                4.3.2.1 api.instagram.com
                """)));
        assertEquals(List.of("explicit.openai.com", "openai.com", "notopenai.com",
                "instagram.com", "api.instagram.com"), List.copyOf(desired.keySet()));
        assertEquals("ai-pool.comss.one", desired.get("openai.com").content());
        assertEquals("4.3.2.1", desired.get("api.instagram.com").content());
    }

    @Test
    void rejectsDirectSubdomainAndMultiRuleCnameCycles() {
        var service = service(new FakeRewriteClient(List.of()), true);
        for (String source : List.of("openai.com openai.com", "api.openai.com openai.com",
                "other.example openai.com\nopenai.com other.example")) {
            assertThrows(UserInputException.class, () -> service.buildNewRewrites(
                    new HostsOverrideListsLoader().parseLists(List.of(source))));
        }
    }

    @Test
    void exactExcludedParentDoesNotSuppressAllowedChild() {
        var loader = new ExcludeRedirectSettingsLoader() {
            @Override public List<String> loadIgnoredDomains() { return List.of("=openai.com"); }
        };
        var service = new NextDnsRewriteService(null, new ExcludeRedirectCheckService(loader), true);
        var desired = service.buildNewRewrites(new HostsOverrideListsLoader().parseLists(List.of(
                "pool.example openai.com\n1.2.3.4 api.openai.com")));
        assertEquals(List.of("api.openai.com"), List.copyOf(desired.keySet()));
    }

    @Test
    void prunesOldChildAndReplacesParentWithHostname() {
        var client = new FakeRewriteClient(List.of(
                new RewriteDto("parent", "openai.com", "87.228.47.204"),
                new RewriteDto("child", "api.openai.com", "87.228.47.204"),
                new RewriteDto("other", "instagram.com", "1.2.3.4")));
        var service = service(client, true);
        var desired = service.buildNewRewrites(new HostsOverrideListsLoader().parseLists(List.of(
                "pool.example openai.com\n87.228.47.204 api.openai.com\n1.2.3.4 instagram.com")));
        var pending = service.cleanupOutdatedAndExcluded(desired);
        assertEquals(List.of("parent", "child"), client.deletedIds);
        assertEquals(1, pending.size());
        assertEquals("openai.com", pending.getFirst().name());
        assertEquals("pool.example", pending.getFirst().content());
    }

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

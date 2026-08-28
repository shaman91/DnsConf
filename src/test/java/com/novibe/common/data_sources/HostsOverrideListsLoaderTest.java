package com.novibe.common.data_sources;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class HostsOverrideListsLoaderTest {
    @Test
    void preservesFirstSourceAndLinePriorityIncludingHostnameTargets() {
        var routes = new HostsOverrideListsLoader().parseLists(List.of(
                "# header\nai-pool.comss.one openai.com\n1.2.3.4 other.example\n0.0.0.0 blocked.example",
                "5.6.7.8 openai.com\n9.8.7.6 child.openai.com\n8.8.8.8 other.example"));
        assertEquals(List.of(
                new HostsOverrideListsLoader.BypassRoute("ai-pool.comss.one", "openai.com"),
                new HostsOverrideListsLoader.BypassRoute("1.2.3.4", "other.example"),
                new HostsOverrideListsLoader.BypassRoute("9.8.7.6", "child.openai.com")), routes);
    }

    @Test
    void hostnameRedirectDoesNotBecomeABlockRule() {
        assertEquals(List.of("ads.example"), new HostsBlockListsLoader().parseLists(List.of(
                "pool.example openai.com\n0.0.0.0 ads.example\n1.2.3.4 allowed.example")));
    }
}

package com.novibe.common.util;

import com.novibe.common.base_structures.BypassRoute;
import com.novibe.common.base_structures.DnsProfile;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.SequencedSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

class DonorDnsUtilsTest {

    @Test
    void preservesHostnameTargetsWithoutQueryingDonor() {
        var route = new BypassRoute("ai-pool.comss.one", "openai.com");
        var profile = new DnsProfile("NEXTDNS", "unused", "unused", 1, "127.0.0.1");
        assertTimeoutPreemptively(Duration.ofSeconds(1), () ->
                DonorDnsUtils.replaceIPs(List.of(route), profile));
        assertEquals("ai-pool.comss.one", route.ip());
    }

    private static SequencedSet<String> donorIps(String... ips) {
        return new LinkedHashSet<>(List.of(ips));
    }

    @Test
    void keepsCurrentIpWhenDonorStillReturnsIt() {
        assertEquals("1.1.1.2", DonorDnsUtils.chooseIp("1.1.1.2", donorIps("1.1.1.1", "1.1.1.2", "1.1.1.3")));
    }

    @Test
    void takesFirstDonorIpWhenCurrentIpIsOutdated() {
        assertEquals("1.1.1.1", DonorDnsUtils.chooseIp("9.9.9.9", donorIps("1.1.1.1", "1.1.1.2")));
    }

    @Test
    void keepsCurrentIpWhenDonorReturnsNothing() {
        assertEquals("9.9.9.9", DonorDnsUtils.chooseIp("9.9.9.9", donorIps()));
    }
}

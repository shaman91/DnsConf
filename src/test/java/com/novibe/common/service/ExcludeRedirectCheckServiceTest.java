package com.novibe.common.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExcludeRedirectCheckServiceTest {

    @Test
    void exactExclusionMatchesOnlyTheExactDomain() {
        assertTrue(ExcludeRedirectCheckService.matchesIgnoredDomain("tiktok.com", "=tiktok.com"));
        assertFalse(ExcludeRedirectCheckService.matchesIgnoredDomain("www.tiktok.com", "=tiktok.com"));
        assertFalse(ExcludeRedirectCheckService.matchesIgnoredDomain("api.tiktok.com", "=tiktok.com"));
    }

    @Test
    void suffixExclusionKeepsExistingSubdomainBehavior() {
        assertTrue(ExcludeRedirectCheckService.matchesIgnoredDomain("tiktok.com", "tiktok.com"));
        assertTrue(ExcludeRedirectCheckService.matchesIgnoredDomain("www.tiktok.com", "tiktok.com"));
        assertTrue(ExcludeRedirectCheckService.matchesIgnoredDomain("api.tiktok.com", "tiktok.com"));
        assertFalse(ExcludeRedirectCheckService.matchesIgnoredDomain("example.com", "tiktok.com"));
    }
}

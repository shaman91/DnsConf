package com.novibe.common.service;

import com.novibe.common.data_sources.ExcludeRedirectSettingsLoader;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ExcludeRedirectCheckService {

    private static final String EXACT_MATCH_PREFIX = "=";

    private final List<String> ignoringList;

    public ExcludeRedirectCheckService(ExcludeRedirectSettingsLoader excludeRedirectSettingsLoader) {
        ignoringList = excludeRedirectSettingsLoader.loadIgnoredDomains();
    }

    public boolean shouldExclude(String domain) {
        for (String ignored : ignoringList) {
            if (matchesIgnoredDomain(domain, ignored)) {
                return true;
            }
        }
        return false;
    }

    static boolean matchesIgnoredDomain(String domain, String ignored) {
        if (ignored.startsWith(EXACT_MATCH_PREFIX)) {
            return domain.equals(ignored.substring(EXACT_MATCH_PREFIX.length()));
        }
        return domain.endsWith(ignored);
    }

}

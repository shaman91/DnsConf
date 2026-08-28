package com.novibe.dns.next_dns.service;

import com.novibe.common.data_sources.HostsOverrideListsLoader;
import com.novibe.common.exception.UserInputException;
import com.novibe.common.service.ExcludeRedirectCheckService;
import com.novibe.common.util.DataParser;
import com.novibe.common.util.Log;
import com.novibe.dns.next_dns.http.NextDnsRateLimitedApiProcessor;
import com.novibe.dns.next_dns.http.NextDnsRewriteClient;
import com.novibe.dns.next_dns.http.dto.request.CreateRewriteDto;
import com.novibe.dns.next_dns.http.dto.response.rewrite.RewriteDto;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class NextDnsRewriteService {

    private final NextDnsRewriteClient nextDnsRewriteClient;
    private final ExcludeRedirectCheckService excludeRedirectCheckService;
    private final boolean pruneUnmanagedRewrites;

    public NextDnsRewriteService(NextDnsRewriteClient nextDnsRewriteClient,
                                 ExcludeRedirectCheckService excludeRedirectCheckService,
                                 @Value("${PRUNE_REDIRECT:false}") boolean pruneUnmanagedRewrites) {
        this.nextDnsRewriteClient = nextDnsRewriteClient;
        this.excludeRedirectCheckService = excludeRedirectCheckService;
        this.pruneUnmanagedRewrites = pruneUnmanagedRewrites;
    }

    public Map<String, CreateRewriteDto> buildNewRewrites(List<HostsOverrideListsLoader.BypassRoute> overrides) {
        Map<String, CreateRewriteDto> rewriteDtos = new LinkedHashMap<>();
        List<String> cnameParents = new ArrayList<>();
        for (var route : overrides) {
            String name = route.website();
            if (excludeRedirectCheckService.shouldExclude(name) || rewriteDtos.containsKey(name)) continue;
            // A higher-priority CNAME parent owns later descendants. Earlier explicit
            // children remain exceptions; IP-parent semantics are deliberately unchanged.
            if (cnameParents.stream().anyMatch(parent -> name.endsWith("." + parent))) continue;
            rewriteDtos.put(name, new CreateRewriteDto(name, route.ip()));
            if (!DataParser.isValidIP(route.ip())) {
                if (!DataParser.isHostname(route.ip()) || !DataParser.isHostname(name)) {
                    throw UserInputException.noStackTrace("Invalid hostname redirect for " + name);
                }
                cnameParents.add(name);
            }
        }
        validateCnameCycles(rewriteDtos);
        return rewriteDtos;
    }

    private static void validateCnameCycles(Map<String, CreateRewriteDto> rewrites) {
        for (CreateRewriteDto start : rewrites.values()) {
            if (DataParser.isValidIP(start.content())) continue;
            var visited = new HashSet<String>();
            CreateRewriteDto current = start;
            while (current != null && !DataParser.isValidIP(current.content())) {
                if (!visited.add(current.name())) {
                    throw UserInputException.noStackTrace("CNAME rewrite cycle involving " + start.name());
                }
                String target = current.content();
                current = null;
                for (CreateRewriteDto candidate : rewrites.values()) {
                    if ((target.equals(candidate.name()) || target.endsWith("." + candidate.name()))
                            && (current == null || candidate.name().length() > current.name().length())) {
                        current = candidate;
                    }
                }
            }
        }
    }

    public List<CreateRewriteDto> cleanupOutdatedAndExcluded(Map<String, CreateRewriteDto> newRewriteRequests) {
        List<RewriteDto> existingRewrites = getExistingRewrites();

        List<String> outdatedIds = new ArrayList<>();
        List<String> ignoredIds = new ArrayList<>();

        for (RewriteDto existingRewrite : existingRewrites) {
            String domain = existingRewrite.name();
            String oldIp = existingRewrite.content();
            if (excludeRedirectCheckService.shouldExclude(domain)) {
                ignoredIds.add(existingRewrite.id());
                newRewriteRequests.remove(domain);
                continue;
            }
            CreateRewriteDto request = newRewriteRequests.get(domain);
            if (request == null) {
                if (pruneUnmanagedRewrites) {
                    outdatedIds.add(existingRewrite.id());
                }
                continue;
            }
            if (!request.content().equals(oldIp)) {
                outdatedIds.add(existingRewrite.id());
            } else {
                newRewriteRequests.remove(domain);
            }
        }
        newRewriteRequests.keySet().removeIf(excludeRedirectCheckService::shouldExclude);

        if (!outdatedIds.isEmpty()) {
            Log.io("Removing %s outdated rewrites from NextDNS".formatted(outdatedIds.size()));
            NextDnsRateLimitedApiProcessor.callApi(outdatedIds, nextDnsRewriteClient::deleteRewriteById);
        }
        if (!ignoredIds.isEmpty()) {
            Log.io("Removing %s excluded rewrites from NextDNS".formatted(ignoredIds.size()));
            NextDnsRateLimitedApiProcessor.callApi(ignoredIds, nextDnsRewriteClient::deleteRewriteById);
        }
        return List.copyOf(newRewriteRequests.values());
    }

    public List<RewriteDto> getExistingRewrites() {
        Log.io("Fetching existing rewrites from NextDNS");
        return nextDnsRewriteClient.fetchRewrites();
    }

    public void saveRewrites(List<CreateRewriteDto> createRewriteDtos) {
        Log.io("Saving %s new rewrites to NextDNS...".formatted(createRewriteDtos.size()));
        NextDnsRateLimitedApiProcessor.callApi(createRewriteDtos, nextDnsRewriteClient::saveRewrite);
    }

    public void removeAll() {
        Log.io("Fetching existing rewrites from NextDNS");
        List<RewriteDto> list = nextDnsRewriteClient.fetchRewrites();
        List<String> ids = list.stream().map(RewriteDto::id).toList();
        Log.io("Removing rewrites from NextDNS");
        NextDnsRateLimitedApiProcessor.callApi(ids, nextDnsRewriteClient::deleteRewriteById);
    }

}

package com.novibe.dns.next_dns.service;

import com.novibe.common.util.Log;
import com.novibe.dns.next_dns.http.NextDnsAllowClient;
import com.novibe.dns.next_dns.http.NextDnsRateLimitedApiProcessor;
import com.novibe.dns.next_dns.http.dto.request.CreateAllowDto;
import com.novibe.dns.next_dns.http.dto.response.allow.AllowDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class NextDnsAllowService {

    private final NextDnsAllowClient nextDnsAllowClient;

    public List<String> omitExistingAllows(List<String> requestedDomains) {
        Log.io("Fetching existing allowlist from NextDNS");
        List<AllowDto> existingAllowlist = nextDnsAllowClient.fetchAllowlist();
        Set<String> activeDomains = existingAllowlist.stream()
                .filter(AllowDto::isActive)
                .map(AllowDto::getId)
                .collect(Collectors.toSet());
        return requestedDomains.stream()
                .filter(domain -> !activeDomains.contains(domain))
                .toList();
    }

    public void saveAllowList(List<String> domains) {
        List<CreateAllowDto> requests = domains.stream().map(CreateAllowDto::new).toList();
        Log.io("Saving new allowlist entries to NextDNS...");
        NextDnsRateLimitedApiProcessor.callApi(requests, nextDnsAllowClient::saveAllow);
    }
}

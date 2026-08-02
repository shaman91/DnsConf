package com.novibe.dns.next_dns.service;

import com.novibe.dns.next_dns.http.NextDnsAllowClient;
import com.novibe.dns.next_dns.http.dto.request.CreateAllowDto;
import com.novibe.dns.next_dns.http.dto.response.allow.AllowDto;
import com.novibe.dns.next_dns.http.dto.response.allow.SingleAllowResponse;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class NextDnsAllowServiceTest {

    @Test
    void addsOnlyDomainsMissingFromActiveAllowlist() {
        FakeAllowClient client = new FakeAllowClient(List.of(
                new AllowDto("already.example", true),
                new AllowDto("inactive.example", false)
        ));
        NextDnsAllowService service = new NextDnsAllowService(client);
        List<String> requested = new ArrayList<>(List.of(
                "already.example", "inactive.example", "new.example"
        ));

        List<String> pending = service.omitExistingAllows(requested);
        service.saveAllowList(pending);

        assertEquals(List.of("inactive.example", "new.example"), client.savedDomains);
    }

    private static final class FakeAllowClient extends NextDnsAllowClient {
        private final List<AllowDto> existing;
        private final List<String> savedDomains = new ArrayList<>();

        private FakeAllowClient(List<AllowDto> existing) {
            this.existing = existing;
        }

        @Override
        public List<AllowDto> fetchAllowlist() {
            return existing;
        }

        @Override
        public SingleAllowResponse saveAllow(CreateAllowDto allowDto) {
            savedDomains.add(allowDto.getId());
            return null;
        }
    }
}

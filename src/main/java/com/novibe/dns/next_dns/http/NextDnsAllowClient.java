package com.novibe.dns.next_dns.http;

import com.novibe.dns.next_dns.http.dto.request.CreateAllowDto;
import com.novibe.dns.next_dns.http.dto.response.allow.AllowDto;
import com.novibe.dns.next_dns.http.dto.response.allow.MultiAllowResponse;
import com.novibe.dns.next_dns.http.dto.response.allow.SingleAllowResponse;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class NextDnsAllowClient extends AbstractNextDnsHttpClient {

    public List<AllowDto> fetchAllowlist() {
        return get(path(), MultiAllowResponse.class).getData();
    }

    public SingleAllowResponse saveAllow(CreateAllowDto allowDto) {
        return post(path(), allowDto, SingleAllowResponse.class);
    }

    @Override
    protected String path() {
        return "/allowlist";
    }
}

package com.novibe.dns.next_dns.http.dto.response.allow;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public final class AllowDto {

    private final String id;
    private final boolean active;
}

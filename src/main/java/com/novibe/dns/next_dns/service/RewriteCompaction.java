package com.novibe.dns.next_dns.service;

import com.novibe.dns.next_dns.http.dto.request.CreateRewriteDto;

import java.util.LinkedHashMap;
import java.util.Map;

/** NextDNS rewrites also cover descendants; keep every differing nearest-parent exception. */
public final class RewriteCompaction {
    private RewriteCompaction() { }

    public static Map<String, CreateRewriteDto> compact(Map<String, CreateRewriteDto> rewrites) {
        Map<String, CreateRewriteDto> result = new LinkedHashMap<>();
        for (var rule : rewrites.values()) {
            CreateRewriteDto parent = nearestParent(rule.name(), rewrites);
            // Compare against the complete input, not insertion order or the partially built result.
            if (parent == null || !parent.content().equals(rule.content())) {
                result.put(rule.name(), rule);
            }
        }
        return result;
    }

    private static CreateRewriteDto nearestParent(String name, Map<String, CreateRewriteDto> rewrites) {
        int dot = name.indexOf('.');
        while (dot >= 0) {
            String parent = name.substring(dot + 1);
            CreateRewriteDto rule = rewrites.get(parent);
            if (rule != null) return rule;
            dot = name.indexOf('.', dot + 1);
        }
        return null;
    }
}

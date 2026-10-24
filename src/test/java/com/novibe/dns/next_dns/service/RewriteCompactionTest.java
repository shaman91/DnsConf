package com.novibe.dns.next_dns.service;

import com.novibe.dns.next_dns.RewriteDryRun;
import com.novibe.dns.next_dns.http.dto.request.CreateRewriteDto;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class RewriteCompactionTest {
    @Test
    void compactsIpAndCnameChildrenRegardlessOfOrderWithoutMutatingInput() {
        var original = rules("x.a.example", "pool.invalid", "a.example", "pool.invalid",
                "example", "pool.invalid", "v6.example.net", "2001:db8::1",
                "example.net", "2001:db8::1", "other.net", "192.0.2.1");
        var before = new LinkedHashMap<>(original);
        var compact = RewriteCompaction.compact(original);
        assertEquals(List.of("example", "example.net", "other.net"), List.copyOf(compact.keySet()));
        assertEquals(before, original);
        assertEquals(compact, RewriteCompaction.compact(compact));
        assertTrue(RewriteCompaction.compact(Map.of()).isEmpty());
    }

    @Test
    void preservesNearestDifferingExceptionEvenWhenFartherAncestorMatches() {
        var original = rules("example", "192.0.2.1", "a.example", "192.0.2.2",
                "b.a.example", "192.0.2.1", "x.b.a.example", "192.0.2.1",
                "notexample", "192.0.2.1", "otherexample", "192.0.2.1");
        var compact = RewriteCompaction.compact(original);
        assertEquals(List.of("example", "a.example", "b.a.example", "notexample", "otherexample"),
                List.copyOf(compact.keySet()));
        assertEquals("192.0.2.1", effective(compact, "new.x.b.a.example"));
        assertEquals("192.0.2.2", effective(compact, "new.a.example"));
        assertNull(effective(compact, "not.example.net"));
    }

    @Test
    void preservesEffectiveTargetForGeneratedNestedTreesAndUnlistedDescendants() {
        var random = new Random(841);
        for (int iteration = 0; iteration < 100; iteration++) {
            var original = new LinkedHashMap<String, CreateRewriteDto>();
            for (int node = 0; node < 150; node++) {
                String name = "n" + random.nextInt(4) + ".n" + random.nextInt(4)
                        + ".n" + random.nextInt(4) + ".example";
                if (random.nextBoolean()) name = name.substring(name.indexOf('.') + 1);
                if (random.nextBoolean()) name = name.substring(name.indexOf('.') + 1);
                String target = List.of("192.0.2.1", "192.0.2.2", "pool.invalid", "2001:db8::1")
                        .get(random.nextInt(4));
                original.put(name, new CreateRewriteDto(name, target));
            }
            var compact = RewriteCompaction.compact(original);
            for (String name : original.keySet()) {
                for (String query : List.of(name, "unlisted." + name, "x.unlisted." + name)) {
                    assertEquals(effective(original, query), effective(compact, query), query);
                }
            }
            assertEquals(compact, RewriteCompaction.compact(compact));
        }
    }

    @Test
    void dryRunIsOptInShowsOnlyEquivalentRemovalsAndCanRestoreExactRules() {
        var source = List.of("192.0.2.1 example.com\n192.0.2.1 a.example.com\n"
                + "192.0.2.2 b.example.com\n192.0.2.1 c.b.example.com");
        var full = RewriteDryRun.plan(List.of(), List.of(), source);
        assertEquals(4, full.afterCount());
        var compact = RewriteDryRun.plan(full.expected(), List.of(), source, true);
        assertEquals(3, compact.afterCount());
        assertEquals(List.of(new RewriteDryRun.Change("a.example.com", "192.0.2.1", null)), compact.changes());
        assertTrue(RewriteDryRun.plan(compact.expected(), List.of(), source, true).changes().isEmpty());
        var rollback = RewriteDryRun.plan(compact.expected(), List.of(), source, false);
        assertEquals(full.expected(), rollback.expected());
        assertEquals(List.of(new RewriteDryRun.Change("a.example.com", null, "192.0.2.1")), rollback.changes());
    }

    @Test
    void exclusionsAndSourcePriorityAreAppliedBeforeCompaction() {
        var source = List.of("192.0.2.1 example.com\n192.0.2.1 a.example.com\n192.0.2.2 b.example.com",
                "192.0.2.3 a.example.com");
        var exact = RewriteDryRun.plan(List.of(), List.of("=example.com"), source, true);
        assertEquals(List.of(new CreateRewriteDto("a.example.com", "192.0.2.1"),
                new CreateRewriteDto("b.example.com", "192.0.2.2")), exact.expected());
        assertTrue(RewriteDryRun.plan(List.of(), List.of("example.com"), source, true).expected().isEmpty());
    }

    @Test
    void currentOwnSourceRetainsAllEffectiveTargetsIncludingClaudeAndOpenaiExceptions() throws Exception {
        var source = List.of(Files.readString(Path.of("openai-working")));
        var full = RewriteDryRun.plan(List.of(), List.of(), source);
        var plan = RewriteDryRun.plan(full.expected(), List.of(), source, true);
        assertFalse(plan.changes().isEmpty());
        assertTrue(plan.changes().stream().allMatch(c -> c.before() != null && c.after() == null));
        Map<String, CreateRewriteDto> before = new LinkedHashMap<>();
        Map<String, CreateRewriteDto> after = new LinkedHashMap<>();
        full.expected().forEach(r -> before.put(r.name(), r));
        plan.expected().forEach(r -> after.put(r.name(), r));
        before.keySet().forEach(name -> {
            assertEquals(effective(before, name), effective(after, name), name);
            assertEquals(effective(before, "probe." + name), effective(after, "probe." + name), name);
        });
        for (String name : List.of("api.console.anthropic.com", "api.claude.ai", "api.claude.com",
                "auth.anthropic.com", "platform.claude.ai", "statsig.anthropic.com")) {
            assertEquals("37.230.192.51", effective(after, name), name);
        }
        assertEquals("eu.geohide.ru", effective(after, "developers.openai.com"));
        assertEquals("ai-pool.comss.one", effective(after, "android.chat.openai.com"));
    }

    // Independent oracle: longest matching suffix, not the production parent-walk implementation.
    private static String effective(Map<String, CreateRewriteDto> rules, String query) {
        return rules.values().stream()
                .filter(r -> query.equals(r.name()) || query.endsWith("." + r.name()))
                .max(Comparator.comparingInt(r -> r.name().length())).map(CreateRewriteDto::content).orElse(null);
    }

    private static Map<String, CreateRewriteDto> rules(String... pairs) {
        Map<String, CreateRewriteDto> result = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            result.put(pairs[i], new CreateRewriteDto(pairs[i], pairs[i + 1]));
        }
        return result;
    }
}

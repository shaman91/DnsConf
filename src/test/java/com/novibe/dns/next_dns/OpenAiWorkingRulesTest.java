package com.novibe.dns.next_dns;

import com.novibe.dns.next_dns.http.dto.request.CreateRewriteDto;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpenAiWorkingRulesTest {
    @Test
    void dynamicEuExceptionsSurviveComssParentsAndLaterSources() throws Exception {
        String source = Files.readString(Path.of("openai-working"));
        var plan = RewriteDryRun.plan(List.of(), List.of(), List.of(source, """
                192.0.2.1 developers.openai.com
                192.0.2.2 cdn.oaistatic.com
                192.0.2.3 oaistatic.com
                192.0.2.4 android.chat.openai.com
                """));
        Map<String, CreateRewriteDto> rules = plan.expected().stream().collect(
                Collectors.toMap(CreateRewriteDto::name, Function.identity()));
        for (String domain : List.of("developers.openai.com", "cdn.oaistatic.com", "oaistatic.com")) {
            assertEquals("eu.geohide.ru", rules.get(domain).content(), domain);
        }
        for (String domain : List.of("openai.com", "chatgpt.com", "oaiusercontent.com")) {
            assertEquals("ai-pool.comss.one", rules.get(domain).content(), domain);
        }
        assertFalse(rules.containsKey("android.chat.openai.com"));
    }

    @Test
    void patchChangesOnlyThreeTargetsAndKeepsRuleNames() throws Exception {
        String source = Files.readString(Path.of("openai-working"));
        String previous = source
                .replace("eu.geohide.ru developers.openai.com", "193.233.112.68 developers.openai.com")
                .replace("eu.geohide.ru cdn.oaistatic.com", "37.230.192.51 cdn.oaistatic.com")
                .replace("eu.geohide.ru oaistatic.com", "37.230.192.51 oaistatic.com");
        var before = RewriteDryRun.plan(List.of(), List.of(), List.of(previous));
        var after = RewriteDryRun.plan(before.expected(), List.of(), List.of(source));
        assertEquals(before.afterCount(), after.afterCount());
        assertEquals(3, after.changes().size());
        assertEquals(List.of("cdn.oaistatic.com", "developers.openai.com", "oaistatic.com"),
                after.changes().stream().map(RewriteDryRun.Change::name).toList());
        assertTrue(after.changes().stream().allMatch(change ->
                change.before() != null && "eu.geohide.ru".equals(change.after())));
        assertTrue(RewriteDryRun.plan(after.expected(), List.of(), List.of(source)).changes().isEmpty());
    }
}

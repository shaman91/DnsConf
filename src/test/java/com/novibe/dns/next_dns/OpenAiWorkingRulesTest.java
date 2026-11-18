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
        for (String domain : List.of("openai.com", "oaiusercontent.com")) {
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
    @Test
    void pinsOnlyChatGptAndSignInWithDonorChildrenStillCompacted() throws Exception {
        String pinned = "206.223.247.81";
        String source = Files.readString(Path.of("openai-working")).replace("\r\n", "\n");
        String previous = source.replace(pinned + " chatgpt.com", "ai-pool.comss.one chatgpt.com")
                .replaceAll("(?m)^206\\.223\\.247\\.81 (?:auth\\.openai\\.com|[^\\s]+\\.chatgpt\\.com)\\n", "");
        String donor = """
                192.0.2.1 ab.chatgpt.com
                192.0.2.2 operator.chatgpt.com
                192.0.2.3 sora.chatgpt.com
                192.0.2.4 webrtc.chatgpt.com
                192.0.2.5 auth.openai.com
                192.0.2.6 api.openai.com
                192.0.2.7 unrelated.example.com
                """;
        var baseline = RewriteDryRun.plan(List.of(), List.of(), List.of(previous, donor), true);
        var updated = RewriteDryRun.plan(baseline.expected(), List.of(), List.of(source, donor), true);
        assertEquals(List.of(new RewriteDryRun.Change("chatgpt.com", "ai-pool.comss.one", pinned),
                new RewriteDryRun.Change("auth.openai.com", null, pinned)), updated.changes());
        assertTrue(RewriteDryRun.plan(updated.expected(), List.of(), List.of(source, donor), true).changes().isEmpty());
        assertEquals(baseline.expected(),
                RewriteDryRun.plan(updated.expected(), List.of(), List.of(previous, donor), true).expected());
    }

}

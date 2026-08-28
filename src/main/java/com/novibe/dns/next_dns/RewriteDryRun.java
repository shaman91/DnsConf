package com.novibe.dns.next_dns;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.novibe.common.data_sources.ExcludeRedirectSettingsLoader;
import com.novibe.common.data_sources.HostsOverrideListsLoader;
import com.novibe.common.service.ExcludeRedirectCheckService;
import com.novibe.dns.next_dns.http.dto.request.CreateRewriteDto;
import com.novibe.dns.next_dns.service.NextDnsRewriteService;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Offline only: no Spring context, credentials, HTTP calls or file writes. */
public final class RewriteDryRun {
    public record Change(String name, String before, String after) { }
    public record Plan(int beforeCount, int afterCount, List<Change> changes,
                       List<CreateRewriteDto> expected) { }

    public static Plan plan(List<CreateRewriteDto> existing, List<String> exclusions, List<String> sources) {
        var loader = new ExcludeRedirectSettingsLoader() {
            @Override public List<String> loadIgnoredDomains() { return exclusions; }
        };
        var service = new NextDnsRewriteService(null, new ExcludeRedirectCheckService(loader), true);
        var desired = service.buildNewRewrites(new HostsOverrideListsLoader().parseLists(sources));
        Map<String, String> old = new TreeMap<>();
        existing.forEach(r -> {
            if (old.putIfAbsent(r.name(), r.content()) != null) {
                throw new IllegalArgumentException("Duplicate existing rewrite: " + r.name());
            }
        });
        Map<String, CreateRewriteDto> sorted = new TreeMap<>(desired);
        List<Change> changes = new ArrayList<>();
        old.forEach((name, content) -> {
            var next = desired.get(name);
            if (next == null || !content.equals(next.content())) {
                changes.add(new Change(name, content, next == null ? null : next.content()));
            }
        });
        sorted.forEach((name, next) -> {
            if (!old.containsKey(name)) changes.add(new Change(name, null, next.content()));
        });
        return new Plan(existing.size(), desired.size(), changes, List.copyOf(sorted.values()));
    }

    public static void main(String[] args) throws Exception {
        if (args.length < 3) {
            throw new IllegalArgumentException("Usage: RewriteDryRun existing.json exclusions.txt source1 [source2 ...] (all local files; PRUNE_REDIRECT=true)");
        }
        Gson gson = new GsonBuilder().setPrettyPrinting().serializeNulls().create();
        var existing = Arrays.asList(gson.fromJson(Files.readString(Path.of(args[0])), CreateRewriteDto[].class));
        var exclusions = Files.readAllLines(Path.of(args[1])).stream().map(String::strip)
                .filter(s -> !s.isEmpty() && !s.startsWith("#")).toList();
        List<String> sources = new ArrayList<>();
        for (int i = 2; i < args.length; i++) sources.add(Files.readString(Path.of(args[i])));
        System.out.println(gson.toJson(plan(existing, exclusions, sources)));
    }
}

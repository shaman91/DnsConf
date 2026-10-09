package com.novibe.common.data_sources;

import com.novibe.common.base_structures.HostsLine;
import com.novibe.common.exception.UserInputException;
import com.novibe.common.util.DataParser;
import com.novibe.common.util.Log;
import lombok.Setter;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.StructuredTaskScope;
import java.util.function.Predicate;
import java.util.stream.Collectors;

@Setter(onMethod_ = @Autowired)
public abstract class ListLoader<T> {

    private HttpClient client;

    protected abstract T toObject(HostsLine hostsLine);

    protected abstract String listType();

    protected abstract Predicate<HostsLine> filterRelatedLines();

    protected HostsLine parseLine(String line) {
        return DataParser.parseHostsLine(line);
    }

    @SuppressWarnings("preview")
    public List<T> fetchWebsites(List<String> urls) {
        try (var scope = StructuredTaskScope.open()) {
            List<StructuredTaskScope.Subtask<String>> requests = new ArrayList<>();
            urls.stream()
                    .map(url -> scope.fork(() -> fetchList(url)))
                    .forEach(requests::add);
            scope.join();
            return parseLists(requests.stream().map(StructuredTaskScope.Subtask::get).toList());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        }
    }

    /** Same ordered parser for downloaded sources, tests and offline dry-runs. */
    public List<T> parseLists(List<String> contents) {
        return contents.stream()
                .flatMap(DataParser::splitByEol)
                .map(String::strip)
                .parallel()
                .filter(line -> !line.isBlank())
                .filter(line -> !DataParser.isComment(line))
                .map(line -> line.toLowerCase(Locale.ROOT))
                .map(this::parseLine)
                .filter(Objects::nonNull)
                .filter(filterRelatedLines())
                .distinct()
                .map(this::toObject)
                .collect(Collectors.toCollection(ArrayList::new));
    }

    private String fetchList(String url) throws IOException, InterruptedException {
        Log.io("Loading %s list from url: %s".formatted(listType(), url));
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .GET()
                .build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() > 299) {
            throw UserInputException.noStackTrace("Failed to load %s list, response code %s from url: %s"
                    .formatted(listType(), response.statusCode(), url));
        }
        return response.body();
    }

}

package com.novibe.common.util;

import com.novibe.common.base_structures.HostsLine;
import com.novibe.common.exception.UserInputException;
import org.jspecify.annotations.Nullable;

import java.net.InetAddress;
import java.util.Locale;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public class DataParser {

    private static final Pattern WHITESPACE = Pattern.compile("\\s+");
    private static final Pattern EOL = Pattern.compile("\\r?\\n");
    private static final Pattern HOST_LABEL = Pattern.compile("[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?");

    /** REDIRECT-only extension: target hostname followed by the source domain. */
    public static @Nullable HostsLine parseRedirectLine(String line) {
        String sanitized = stripInlineComment(line).strip().toLowerCase(Locale.ROOT);
        String[] columns = WHITESPACE.split(sanitized, 3);
        if (columns.length < 2 || isValidIP(columns[0])) {
            return parseHostsLine(sanitized);
        }
        String target = removeTrailingDot(columns[0]);
        String source = removeWWW(removeTrailingDot(columns[1]));
        if (columns.length != 2 || !isHostname(target) || !isHostname(source)) {
            // Do not silently discard a malformed CNAME and then prune working rules.
            throw UserInputException.noStackTrace("Invalid hostname redirect: " + line);
        }
        return new HostsLine(target, source);
    }

    private static String removeTrailingDot(String value) {
        return value.endsWith(".") ? value.substring(0, value.length() - 1) : value;
    }

    public static boolean isHostname(String value) {
        if (value == null || value.length() > 253 || isValidIP(value)) return false;
        String[] labels = value.split("\\.", -1);
        if (labels.length < 2 || !labels[labels.length - 1].matches(".*[a-z].*")) return false;
        for (String label : labels) {
            if (!HOST_LABEL.matcher(label).matches()) return false;
        }
        return true;
    }

    public static String removeWWW(String domain) {
        if (domain.startsWith("www.")) {
            return domain.substring("www.".length());
        }
        return domain;
    }

    public static boolean isComment(String line) {
        return line.startsWith("#");
    }

    public static @Nullable HostsLine parseHostsLine(String line) {
        String sanitizedLine = stripInlineComment(line);
        if (sanitizedLine.isBlank()) {
            return null;
        }
        String[] columns = WHITESPACE.split(sanitizedLine, 3);
        if (columns.length == 1) {
            String value = columns[0];
            return isValidIP(value) ? HostsLine.ipOnly(value) : HostsLine.domainOnly(removeWWW(value));
        } else if (columns.length == 2) {
            String ip = columns[0];
            String domain = removeWWW(columns[1]);
            if (isValidIP(ip)) {
                return new HostsLine(ip, domain);
            }
        }
        Log.fail("Failed to parse hosts line: " + line);
        return null;
    }

    public static Stream<String> splitByEol(String data) {
        return EOL.splitAsStream(data);
    }

    private static String stripInlineComment(String line) {
        int commentIndex = line.indexOf('#');
        if (commentIndex >= 0) {
            return line.substring(0, commentIndex).strip();
        }
        return line;
    }

    public static boolean isValidIP(String ip) {
        try {
            return !InetAddress.ofLiteral(ip).getHostAddress().isEmpty();
        } catch (Exception e) {
            return false;
        }
    }
}

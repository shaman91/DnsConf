package com.novibe.common.data_sources;

import com.novibe.common.base_structures.HostsLine;
import com.novibe.common.util.DataParser;
import org.springframework.stereotype.Service;

import java.util.function.Predicate;

@Service
public class HostsOverrideListsLoader extends ListLoader<HostsOverrideListsLoader.BypassRoute> {

    // ip retains its historical accessor name; NextDNS also accepts a hostname target.
    public record BypassRoute(String ip, String website) {
    }

    @Override
    protected HostsLine parseLine(String line) {
        return DataParser.parseRedirectLine(line);
    }

    @Override
    protected String listType() {
        return "Override";
    }

    @Override
    protected Predicate<HostsLine> filterRelatedLines() {
        return line -> line.hasIpAndDomain() && !HostsBlockListsLoader.isBlockIp(line.ip());

    }

    @Override
    protected BypassRoute toObject(HostsLine line) {
        return new BypassRoute(line.ip(), line.domain());
    }

}

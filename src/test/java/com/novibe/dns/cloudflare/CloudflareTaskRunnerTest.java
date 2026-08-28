package com.novibe.dns.cloudflare;

import com.novibe.common.data_sources.HostsOverrideListsLoader;
import com.novibe.common.exception.UserInputException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CloudflareTaskRunnerTest {
    @Test
    void rejectsCnameBeforeAnyServiceCanDeleteOrWrite() {
        // Null services prove that no read/delete/create service call is reached.
        var runner = new CloudflareTaskRunner(null, null);
        assertThrows(UserInputException.class, () -> runner.processLists(List.of(),
                List.of(new HostsOverrideListsLoader.BypassRoute("pool.example", "openai.com"))));
    }
}

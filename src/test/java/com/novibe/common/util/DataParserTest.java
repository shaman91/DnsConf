package com.novibe.common.util;

import com.novibe.common.exception.UserInputException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

class DataParserTest {
    @Test
    void parsesHostnameTargetWithoutResolvingOrRemovingTargetWww() {
        var line = DataParser.parseRedirectLine(" WWW.Pool.Example.  www.OpenAI.com. # comment");
        assertNotNull(line);
        assertEquals("www.pool.example", line.ip());
        assertEquals("openai.com", line.domain());
    }

    @ParameterizedTest
    @ValueSource(strings = {"87.228.47.204", "2001:db8::1"})
    void keepsLiteralIpFormat(String ip) {
        var line = DataParser.parseRedirectLine(ip + " www.example.com");
        assertNotNull(line);
        assertEquals(ip, line.ip());
        assertEquals("example.com", line.domain());
    }

    @ParameterizedTest
    @ValueSource(strings = {"https://pool.example openai.com", "pool.example:443 openai.com",
            "pool..example openai.com", "-pool.example openai.com", "pool.example *.openai.com",
            "pool.example openai.com extra", "999.999.1.1 openai.com", "localhost openai.com",
            "pool.example bad_.example", "pool.example openai.com.."})
    void rejectsMalformedHostnamePairsInsteadOfSilentlyPruning(String line) {
        assertThrows(UserInputException.class, () -> DataParser.parseRedirectLine(line));
    }

    @Test
    void blockParserStillDoesNotAcceptCnamePairs() {
        assertNull(DataParser.parseHostsLine("pool.example openai.com"));
        assertNull(DataParser.parseRedirectLine("# only a comment"));
        assertTrue(DataParser.parseRedirectLine("openai.com").hasDomainOnly());
    }
}

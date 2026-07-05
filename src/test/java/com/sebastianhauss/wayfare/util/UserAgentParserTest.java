package com.sebastianhauss.wayfare.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class UserAgentParserTest {

    private static final String CHROME_DESKTOP =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Safari/537.36";
    private static final String SAFARI_IPHONE =
            "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.0 Mobile/15E148 Safari/604.1";
    private static final String EDGE_DESKTOP =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Safari/537.36 Edg/120.0";

    @Test
    void deviceType_detectsDesktopMobileTabletAndBot() {
        assertThat(UserAgentParser.deviceType(CHROME_DESKTOP)).isEqualTo("desktop");
        assertThat(UserAgentParser.deviceType(SAFARI_IPHONE)).isEqualTo("mobile");
        assertThat(UserAgentParser.deviceType("Mozilla/5.0 (iPad; CPU OS 17_0) Safari")).isEqualTo("tablet");
        assertThat(UserAgentParser.deviceType("Googlebot/2.1 (+http://www.google.com/bot.html)")).isEqualTo("bot");
    }

    @Test
    void browser_prefersMoreSpecificTokens() {
        // Edge and Chrome UAs both contain "chrome"; Chrome also contains "safari".
        assertThat(UserAgentParser.browser(EDGE_DESKTOP)).isEqualTo("Edge");
        assertThat(UserAgentParser.browser(CHROME_DESKTOP)).isEqualTo("Chrome");
        assertThat(UserAgentParser.browser(SAFARI_IPHONE)).isEqualTo("Safari");
    }

    @Test
    void returnsNull_forBlankUserAgent() {
        assertThat(UserAgentParser.deviceType(null)).isNull();
        assertThat(UserAgentParser.deviceType("")).isNull();
        assertThat(UserAgentParser.browser(null)).isNull();
        assertThat(UserAgentParser.browser("  ")).isNull();
    }
}

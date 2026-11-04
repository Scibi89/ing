package pl.ing.tests;

import com.microsoft.playwright.Page;
import com.microsoft.playwright.TimeoutError;
import com.microsoft.playwright.options.Cookie;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

final class AnalyticsCookieAssertions {
    private static final double INITIALIZATION_TIMEOUT_MS = 30000;
    private final Page page;

    AnalyticsCookieAssertions(Page page) {
        this.page = page;
    }

    void noAnalyticsCookiesAreStored() {
        List<String> unexpected = cookies().stream()
                .filter(cookie -> Arrays.stream(AnalyticsCookie.values()).anyMatch(expected -> expected.matchesName(cookie)))
                .map(cookie -> cookie.name).toList();
        assertTrue(unexpected.isEmpty(), "Analytics cookies exist before consent: " + unexpected);
    }

    void analyticsCookiesAreStored() {
        try {
            page.waitForCondition(() -> missing(cookies()).isEmpty(),
                    new Page.WaitForConditionOptions().setTimeout(INITIALIZATION_TIMEOUT_MS));
        } catch (TimeoutError error) {
            fail("Analytics cookies did not initialize after the consent-aware page load. Missing or invalid: "
                    + missing(cookies()) + "; observed cookie names: "
                    + cookies().stream().map(cookie -> cookie.name).toList(), error);
        }
        List<Cookie> stored = cookies();
        for (AnalyticsCookie expected : AnalyticsCookie.values()) {
            Cookie cookie = stored.stream().filter(expected::matches).findFirst().orElseThrow();
            assertEquals(".ing.pl", cookie.domain, "Unexpected domain for " + cookie.name);
            assertEquals("/", cookie.path, "Unexpected path for " + cookie.name);
            if (expected.persistent()) {
                assertTrue(cookie.expires > Instant.now().getEpochSecond(), "Cookie has expired: " + cookie.name);
            } else {
                assertEquals(-1.0, cookie.expires, "Expected a session cookie: " + cookie.name);
            }
        }
        System.out.println("Verified analytics cookies: " + stored.stream()
                .filter(cookie -> Arrays.stream(AnalyticsCookie.values()).anyMatch(expected -> expected.matchesName(cookie)))
                .map(cookie -> cookie.name + "@" + cookie.domain).toList());
    }

    private List<AnalyticsCookie> missing(List<Cookie> stored) {
        return Arrays.stream(AnalyticsCookie.values())
                .filter(expected -> stored.stream().noneMatch(expected::matches)).toList();
    }

    private List<Cookie> cookies() {
        return page.context().cookies(page.url());
    }
}

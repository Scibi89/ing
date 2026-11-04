package pl.ing.tests;

import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.Cookie;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ConsentAssertions {
    private static final String CONSENT_COOKIE = "cookiePolicyGDPR";
    private static final Duration MINIMUM_COOKIE_LIFETIME = Duration.ofDays(300);
    private final Page page;
    private final CookieConsentPage consent;

    ConsentAssertions(Page page, CookieConsentPage consent) {
        this.page = page;
        this.consent = consent;
    }

    void bannerIsVisible() {
        assertThat(consent.banner()).isVisible();
    }

    void bannerIsHidden() {
        assertThat(consent.banner()).isHidden();
    }

    void noConsentIsStored() {
        assertTrue(cookies().stream().noneMatch(cookie -> cookie.name.equals(CONSENT_COOKIE)),
                "A fresh visitor must not have a saved consent decision");
    }

    void preferencesAre(ConsentPreferences preferences) {
        assertThat(consent.technicalCookies()).hasAttribute("aria-checked", "true");
        assertThat(consent.technicalCookies()).isDisabled();
        assertThat(consent.analyticsCookies()).hasAttribute("aria-checked", Boolean.toString(preferences.analytics()));
        assertThat(consent.marketingCookies()).hasAttribute("aria-checked", Boolean.toString(preferences.marketing()));
    }

    void storedConsentIs(ConsentPreferences preferences) {
        page.waitForCondition(() -> cookies().stream().anyMatch(cookie -> cookie.name.equals(CONSENT_COOKIE)
                && cookie.value.equals(preferences.cookieValue())));
        List<Cookie> consentCookies = cookies().stream().filter(cookie -> cookie.name.equals(CONSENT_COOKIE)).toList();
        assertEquals(1, consentCookies.size(), "Expected one consent cookie applicable to this page");
        Cookie cookie = consentCookies.getFirst();
        assertEquals(preferences.cookieValue(), cookie.value);
        assertEquals(".ing.pl", cookie.domain);
        assertEquals("/", cookie.path);
        assertTrue(cookie.expires > Instant.now().plus(MINIMUM_COOKIE_LIFETIME).getEpochSecond(),
                "Consent must survive the browser session");
        assertTrue(cookies().stream().anyMatch(item -> item.name.equals(CONSENT_COOKIE + "__details")
                && item.value.matches("\\{\\s*\"cookieCreateTimestamp\"\\s*:\\s*[1-9]\\d*\\s*}")),
                "Consent details must contain a positive creation timestamp");
    }

    private List<Cookie> cookies() {
        return page.context().cookies(page.url());
    }
}

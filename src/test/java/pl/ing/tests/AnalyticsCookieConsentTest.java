package pl.ing.tests;

import com.microsoft.playwright.Page;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import pl.ing.tests.support.BrowserMatrixExtension;
import pl.ing.tests.support.TestConfig;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;

@ExtendWith(BrowserMatrixExtension.class)
@Execution(ExecutionMode.SAME_THREAD)
class AnalyticsCookieConsentTest {
    @TestTemplate
    @Execution(ExecutionMode.CONCURRENT)
    void analyticsOnlyConsentPersistsAndInitializesAnalyticsOnTheNextPageLoad(Page page, TestConfig config) {
        String actualBrowser = page.context().browser().browserType().name();
        assertEquals(config.browser().directoryName(), actualBrowser, "Wrong browser engine was launched");
        System.out.println("Running recruitment scenario on " + actualBrowser + " at " + Instant.now()
                + " on thread " + Thread.currentThread().getName());
        CookieConsentPage consent = new CookieConsentPage(page, config.baseUrl());
        ConsentAssertions consentChecks = new ConsentAssertions(page, consent);
        AnalyticsCookieAssertions analyticsChecks = new AnalyticsCookieAssertions(page);

        consent.open();
        consentChecks.bannerIsVisible();
        consentChecks.noConsentIsStored();
        analyticsChecks.noAnalyticsCookiesAreStored();

        consent.customize();
        consentChecks.preferencesAre(ConsentPreferences.TECHNICAL_ONLY);
        consent.select(ConsentPreferences.ANALYTICS_ONLY);
        consentChecks.preferencesAre(ConsentPreferences.ANALYTICS_ONLY);
        consent.save();

        consentChecks.bannerIsHidden();
        consentChecks.storedConsentIs(ConsentPreferences.ANALYTICS_ONLY);

        consent.reload();
        consentChecks.bannerIsHidden();
        consentChecks.storedConsentIs(ConsentPreferences.ANALYTICS_ONLY);
        analyticsChecks.analyticsCookiesAreStored();

        consent.reopenSettings();
        consentChecks.preferencesAre(ConsentPreferences.ANALYTICS_ONLY);
        System.out.println("Completed recruitment scenario on " + actualBrowser + " at " + Instant.now());
    }
}

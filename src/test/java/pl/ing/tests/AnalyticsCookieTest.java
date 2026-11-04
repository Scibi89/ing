package pl.ing.tests;

import com.microsoft.playwright.options.Cookie;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AnalyticsCookieTest {
    @ParameterizedTest
    @CsvSource({
            "GOOGLE_VISITOR, _ga, GA1.1.123456789.1793814000",
            "GOOGLE_SESSION, _ga_6S7EZXEX42, GS2.1.s1793814000$o1$g0",
            "ADOBE_VISITOR, AMCV_example%40AdobeOrg, opaque-visitor-id",
            "ADOBE_SESSION, AMCVS_example%40AdobeOrg, 1",
            "ADOBE_COOKIE_SUPPORT, s_cc, true"
    })
    void recognizesObservedAnalyticsCookieFamilies(AnalyticsCookie expected, String name, String value) {
        assertTrue(expected.matches(new Cookie(name, value)));
    }

    @ParameterizedTest
    @CsvSource({
            "GOOGLE_VISITOR, _ga, ''",
            "GOOGLE_VISITOR, _ga, invalid-visitor-id",
            "GOOGLE_SESSION, _ga_6S7EZXEX42, invalid-session-id",
            "ADOBE_SESSION, AMCVS_example%40AdobeOrg, 0",
            "ADOBE_COOKIE_SUPPORT, s_cc, false",
            "ADOBE_VISITOR, AMCVS_example%40AdobeOrg, 1"
    })
    void rejectsWrongNamesOrInvalidValues(AnalyticsCookie expected, String name, String value) {
        assertFalse(expected.matches(new Cookie(name, value)));
    }
}

package pl.ing.tests.support;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.net.URI;
import java.nio.file.Path;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestConfigTest {
    @Test
    void missingPropertiesUseHeadlessChromiumAndTheLiveIngUrl() {
        TestConfig config = TestConfig.from(new Properties());
        assertAll(
                () -> assertEquals(BrowserEngine.CHROMIUM, config.browser()),
                () -> assertEquals(URI.create("https://www.ing.pl/"), config.baseUrl()),
                () -> assertTrue(config.headless()),
                () -> assertFalse(config.retainArtifacts()));
    }

    @Test
    void explicitConfigurationIsParsedWithoutChangingItsSource() {
        Properties properties = new Properties();
        properties.setProperty("browser", " Firefox ");
        properties.setProperty("baseUrl", " https://www.ing.pl/cookie ");
        properties.setProperty("headless", "FALSE");
        properties.setProperty("retainArtifacts", " true ");
        properties.setProperty("artifactsDirectory", "custom-artifacts");
        TestConfig config = TestConfig.from(properties);
        assertAll(
                () -> assertEquals(BrowserEngine.FIREFOX, config.browser()),
                () -> assertEquals(URI.create("https://www.ing.pl/cookie"), config.baseUrl()),
                () -> assertFalse(config.headless()),
                () -> assertTrue(config.retainArtifacts()),
                () -> assertEquals(Path.of("custom-artifacts"), config.artifactsDirectory()),
                () -> assertEquals(" Firefox ", properties.getProperty("browser")));
    }

    @ParameterizedTest
    @ValueSource(strings = {"chrome", "unknown", " "})
    void unsupportedBrowserFailsBeforeLaunchingAnything(String browser) {
        Properties properties = new Properties();
        properties.setProperty("browser", browser);
        assertThrows(IllegalArgumentException.class, () -> TestConfig.from(properties));
    }

    @ParameterizedTest
    @CsvSource({"headless, yes", "headless, 1", "retainArtifacts, typo"})
    void invalidBooleanOptionsAreNotSilentlyTreatedAsFalse(String name, String value) {
        Properties properties = new Properties();
        properties.setProperty(name, value);
        assertThrows(IllegalArgumentException.class, () -> TestConfig.from(properties));
    }

    @ParameterizedTest
    @ValueSource(strings = {"/relative", "file:///tmp/page.html", "https:///missing-host", "not a URL"})
    void baseUrlMustHaveAnHttpSchemeAndHost(String baseUrl) {
        Properties properties = new Properties();
        properties.setProperty("baseUrl", baseUrl);
        assertThrows(IllegalArgumentException.class, () -> TestConfig.from(properties));
    }
}

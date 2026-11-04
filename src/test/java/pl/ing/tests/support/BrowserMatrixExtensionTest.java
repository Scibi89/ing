package pl.ing.tests.support;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BrowserMatrixExtensionTest {
    @Test
    void unspecifiedBrowserCreatesOneNamedRunForEveryEngine() {
        assertEquals(List.of("chromium", "firefox", "webkit"), names(new Properties()));
    }

    @Test
    void explicitAllCreatesTheSameMatrixWithoutMutatingItsConfiguration() {
        Properties properties = new Properties();
        properties.setProperty("browser", "ALL");
        assertEquals(names(new Properties()), names(properties));
        assertEquals("ALL", properties.getProperty("browser"));
    }

    @ParameterizedTest
    @EnumSource(BrowserEngine.class)
    void aSingleEngineFiltersTheMatrixForPipelineJobs(BrowserEngine browser) {
        Properties properties = new Properties();
        properties.setProperty("browser", browser.directoryName());
        assertEquals(List.of(browser.directoryName()), names(properties));
    }

    @ParameterizedTest
    @ValueSource(strings = {"unknown", " ", "chromium,firefox"})
    void invalidSelectionFailsInsteadOfSilentlyRunningChromium(String selection) {
        Properties properties = new Properties();
        properties.setProperty("browser", selection);
        assertThrows(IllegalArgumentException.class, () -> names(properties));
    }

    private List<String> names(Properties properties) {
        return BrowserMatrixExtension.contexts(properties).map(context -> context.getDisplayName(1)).toList();
    }
}

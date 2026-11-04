package pl.ing.tests.support;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Playwright;

import java.util.Locale;
import java.util.Map;
import java.util.function.Function;

public enum BrowserEngine {
    CHROMIUM(Playwright::chromium),
    FIREFOX(Playwright::firefox, Map.of("widget.windows.window_occlusion_tracking.enabled", false)),
    WEBKIT(Playwright::webkit);

    private final Function<Playwright, BrowserType> browserType;
    private final Map<String, Object> headedPreferences;

    BrowserEngine(Function<Playwright, BrowserType> browserType) {
        this(browserType, Map.of());
    }

    BrowserEngine(Function<Playwright, BrowserType> browserType, Map<String, Object> headedPreferences) {
        this.browserType = browserType;
        this.headedPreferences = Map.copyOf(headedPreferences);
    }

    public Browser launch(Playwright playwright, boolean headless) {
        BrowserType.LaunchOptions options = new BrowserType.LaunchOptions().setHeadless(headless);
        if (!headless && !headedPreferences.isEmpty()) {
            options.setFirefoxUserPrefs(headedPreferences);
        }
        return browserType.apply(playwright).launch(options);
    }

    public String directoryName() {
        return name().toLowerCase(Locale.ROOT);
    }

    static BrowserEngine parse(String value) {
        try {
            return valueOf(value.strip().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException error) {
            throw new IllegalArgumentException("Unsupported browser: " + value + ". Use chromium, firefox or webkit.", error);
        }
    }
}

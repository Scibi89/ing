package pl.ing.tests.support;

import java.net.URI;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Properties;

public record TestConfig(BrowserEngine browser, URI baseUrl, boolean headless,
                         boolean retainArtifacts, Path artifactsDirectory) {
    public TestConfig {
        Objects.requireNonNull(browser, "browser");
        Objects.requireNonNull(baseUrl, "baseUrl");
        Objects.requireNonNull(artifactsDirectory, "artifactsDirectory");
        if (baseUrl.getHost() == null || !("https".equalsIgnoreCase(baseUrl.getScheme())
                || "http".equalsIgnoreCase(baseUrl.getScheme()))) {
            throw new IllegalArgumentException("baseUrl must be an absolute HTTP(S) URL: " + baseUrl);
        }
    }

    public static TestConfig from(Properties properties) {
        return new TestConfig(
                BrowserEngine.parse(properties.getProperty("browser", "chromium")),
                URI.create(properties.getProperty("baseUrl", "https://www.ing.pl/").strip()),
                booleanProperty(properties, "headless", true),
                booleanProperty(properties, "retainArtifacts", false),
                Path.of(properties.getProperty("artifactsDirectory", "target/artifacts")));
    }

    private static boolean booleanProperty(Properties properties, String name, boolean defaultValue) {
        String value = properties.getProperty(name, Boolean.toString(defaultValue)).strip();
        if (!value.equalsIgnoreCase("true") && !value.equalsIgnoreCase("false")) {
            throw new IllegalArgumentException(name + " must be true or false: " + value);
        }
        return Boolean.parseBoolean(value);
    }
}

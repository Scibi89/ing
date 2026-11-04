package pl.ing.tests.support;

import org.junit.jupiter.api.extension.Extension;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.TestTemplateInvocationContext;
import org.junit.jupiter.api.extension.TestTemplateInvocationContextProvider;

import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.stream.Stream;

public final class BrowserMatrixExtension implements TestTemplateInvocationContextProvider {
    @Override
    public boolean supportsTestTemplate(ExtensionContext context) {
        return true;
    }

    @Override
    public Stream<TestTemplateInvocationContext> provideTestTemplateInvocationContexts(ExtensionContext context) {
        return contexts(PlaywrightExtension.configurationProperties(context));
    }

    static Stream<TestTemplateInvocationContext> contexts(Properties properties) {
        String selection = properties.getProperty("browser", "all").strip();
        List<BrowserEngine> browsers = selection.equalsIgnoreCase("all")
                ? Arrays.asList(BrowserEngine.values()) : List.of(BrowserEngine.parse(selection));
        return browsers.stream().map(browser -> {
            Properties selectedProperties = new Properties();
            selectedProperties.putAll(properties);
            selectedProperties.setProperty("browser", browser.directoryName());
            TestConfig config = TestConfig.from(selectedProperties);
            return new TestTemplateInvocationContext() {
                @Override
                public String getDisplayName(int invocationIndex) {
                    return browser.directoryName();
                }

                @Override
                public List<Extension> getAdditionalExtensions() {
                    return List.of(new PlaywrightExtension(config));
                }
            };
        });
    }
}

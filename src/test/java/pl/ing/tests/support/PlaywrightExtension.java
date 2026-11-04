package pl.ing.tests.support;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolver;

import java.nio.file.Path;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;

public final class PlaywrightExtension implements BeforeEachCallback, AfterEachCallback, ParameterResolver {
    private static final ExtensionContext.Namespace NAMESPACE =
            ExtensionContext.Namespace.create(PlaywrightExtension.class);
    private static final Set<Class<?>> PARAMETER_TYPES = Set.of(Page.class, BrowserContext.class, TestConfig.class);
    private final TestConfig invocationConfig;

    public PlaywrightExtension() {
        invocationConfig = null;
    }

    PlaywrightExtension(TestConfig invocationConfig) {
        this.invocationConfig = Objects.requireNonNull(invocationConfig);
    }

    static Properties configurationProperties(ExtensionContext extensionContext) {
        Properties properties = new Properties();
        properties.putAll(System.getProperties());
        for (String name : Set.of("browser", "baseUrl", "headless", "retainArtifacts", "artifactsDirectory")) {
            extensionContext.getConfigurationParameter(name).ifPresent(value -> properties.setProperty(name, value));
        }
        return properties;
    }

    private static BrowserSession launch(TestConfig config) {
        Playwright playwright = Playwright.create();
        try {
            Browser browser = config.browser().launch(playwright, config.headless());
            return new BrowserSession(playwright, browser, config);
        } catch (RuntimeException | Error error) {
            try {
                playwright.close();
            } catch (RuntimeException closeError) {
                error.addSuppressed(closeError);
            }
            throw error;
        }
    }

    @Override
    public void beforeEach(ExtensionContext extensionContext) {
        ExtensionContext.Store store = extensionContext.getStore(NAMESPACE);
        TestConfig config = invocationConfig == null ? TestConfig.from(configurationProperties(extensionContext))
                : invocationConfig;
        store.put(BrowserSession.class, launch(config));
        BrowserSession session = session(extensionContext);
        BrowserContext context = session.browser().newContext(new Browser.NewContextOptions()
                .setLocale("pl-PL").setTimezoneId("Europe/Warsaw").setViewportSize(1280, 720));
        store.put(BrowserContext.class, context);
        context.setDefaultTimeout(15000);
        context.setDefaultNavigationTimeout(45000);
        Path directory = session.config().artifactsDirectory().resolve(session.config().browser().directoryName())
                .resolve(extensionContext.getRequiredTestMethod().getName() + "-" + UUID.randomUUID());
        ArtifactCollector collector = new ArtifactCollector(context, directory);
        store.put(ArtifactCollector.class, collector);
        collector.start();
        store.put(Page.class, context.newPage());
    }

    @Override
    public void afterEach(ExtensionContext extensionContext) {
        ExtensionContext.Store store = extensionContext.getStore(NAMESPACE);
        BrowserContext context = store.remove(BrowserContext.class, BrowserContext.class);
        store.remove(Page.class, Page.class);
        BrowserSession ownedSession = store.remove(BrowserSession.class, BrowserSession.class);
        try (ownedSession) {
            if (context != null) {
                try (context) {
                    ArtifactCollector collector = store.remove(ArtifactCollector.class, ArtifactCollector.class);
                    if (collector != null) {
                        collector.finish(ownedSession.config().retainArtifacts()
                                || extensionContext.getExecutionException().isPresent());
                    }
                }
            }
        }
    }

    @Override
    public boolean supportsParameter(ParameterContext parameterContext, ExtensionContext extensionContext) {
        return PARAMETER_TYPES.contains(parameterContext.getParameter().getType());
    }

    @Override
    public Object resolveParameter(ParameterContext parameterContext, ExtensionContext extensionContext) {
        Class<?> type = parameterContext.getParameter().getType();
        if (type == TestConfig.class) {
            return session(extensionContext).config();
        }
        return extensionContext.getStore(NAMESPACE).get(type, type);
    }

    private static BrowserSession session(ExtensionContext context) {
        return context.getStore(NAMESPACE).get(BrowserSession.class, BrowserSession.class);
    }

    private record BrowserSession(Playwright playwright, Browser browser, TestConfig config) implements AutoCloseable {
        @Override
        public void close() {
            try (playwright) {
                browser.close();
            }
        }
    }
}

package pl.ing.tests.support;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.TestTemplateInvocationContext;
import org.junit.jupiter.api.extension.TestTemplateInvocationContextProvider;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.platform.engine.TestExecutionResult;
import org.junit.platform.testkit.engine.EngineExecutionResults;
import org.junit.platform.testkit.engine.EngineTestKit;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import java.util.zip.ZipFile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.junit.platform.engine.discovery.DiscoverySelectors.selectClass;

@Execution(ExecutionMode.SAME_THREAD)
class PlaywrightExtensionTest {
    @TempDir
    Path artifacts;

    @ParameterizedTest
    @ValueSource(classes = {TestFailureFixture.class, SetupFailureFixture.class, TeardownFailureFixture.class})
    void failuresInAnyTestPhaseProduceArtifactsWithoutMaskingTheFailure(Class<?> fixture) throws IOException {
        EngineExecutionResults results = execute(fixture, false);
        results.testEvents().assertStatistics(statistics -> statistics.started(1).failed(1));
        results.containerEvents().assertStatistics(statistics -> statistics.failed(0));
        Throwable failure = results.testEvents().failed().list().getFirst()
                .getPayload(TestExecutionResult.class).orElseThrow().getThrowable().orElseThrow();
        assertTrue(failure.getMessage().startsWith("Deliberate "), "The original failure was masked: " + failure);

        Path directory = traceFiles().getFirst().getParent();
        assertEquals(1, traceFiles().size());
        assertTrue(Files.size(directory.resolve("page-1.png")) > 0);
        assertTrue(Files.size(directory.resolve("page-1.html")) > 0);
        assertTrue(Files.size(directory.resolve("storage.json")) > 0);
        assertTrue(Files.exists(directory.resolve("browser.log")));
        try (ZipFile trace = new ZipFile(directory.resolve("trace.zip").toFile())) {
            assertTrue(trace.stream().anyMatch(entry -> entry.getName().endsWith(".trace")));
            assertTrue(trace.stream().anyMatch(entry -> entry.getName().endsWith(".network")));
        }
        if (fixture == TestFailureFixture.class) {
            assertTrue(Files.size(directory.resolve("page-2.png")) > 0);
            assertTrue(Files.readString(directory.resolve("page-2.html")).contains("Second tab"));
            assertTrue(Files.readString(directory.resolve("browser.log")).contains("second-tab-diagnostic"));
            assertTrue(TestFailureFixture.failedPage.isClosed());
            assertFalse(TestFailureFixture.browser.isConnected());
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void contextIsolationBrowserCleanupAndSuccessfulArtifactRetentionWork(boolean retainArtifacts) throws IOException {
        EngineExecutionResults results = execute(IsolationFixture.class, retainArtifacts);
        results.testEvents().assertStatistics(statistics -> statistics.started(2).succeeded(2));
        results.containerEvents().assertStatistics(statistics -> statistics.failed(0));
        assertEquals(retainArtifacts ? 2 : 0, traceFiles().size());
    }

    private EngineExecutionResults execute(Class<?> fixture, boolean retainArtifacts) {
        return execute(fixture, retainArtifacts, false);
    }

    private EngineExecutionResults execute(Class<?> fixture, boolean retainArtifacts, boolean parallel) {
        String browser = System.getProperty("browser", "chromium");
        if (browser.strip().equalsIgnoreCase("all")) {
            browser = "chromium";
        }
        return EngineTestKit.engine("junit-jupiter")
                .selectors(selectClass(fixture))
                .configurationParameter("browser", browser)
                .configurationParameter("artifactsDirectory", artifacts.toString())
                .configurationParameter("headless", "true")
                .configurationParameter("retainArtifacts", Boolean.toString(retainArtifacts))
                .configurationParameter("junit.jupiter.execution.parallel.enabled", Boolean.toString(parallel))
                .configurationParameter("junit.jupiter.execution.parallel.config.strategy", "fixed")
                .configurationParameter("junit.jupiter.execution.parallel.config.fixed.parallelism", "3")
                .execute();
    }

    @Test
    void matrixInvocationsUseTheSelectedEngineAndFreshContexts() {
        EngineExecutionResults results = execute(MatrixIsolationFixture.class, false);
        results.testEvents().assertStatistics(statistics -> statistics.started(1).succeeded(1));
        results.containerEvents().assertStatistics(statistics -> statistics.failed(0));
    }

    @Test
    void failingMatrixInvocationsCloseTheirOwnBrowsersAndRetainEvidence() throws IOException {
        EngineExecutionResults results = execute(MatrixFailureFixture.class, false);
        results.testEvents().assertStatistics(statistics -> statistics.started(1).failed(1));
        results.containerEvents().assertStatistics(statistics -> statistics.failed(0));
        assertEquals(1, traceFiles().size());
        assertEquals(1, MatrixFailureFixture.pages.size());
        assertTrue(MatrixFailureFixture.pages.stream().allMatch(Page::isClosed));
        assertTrue(MatrixFailureFixture.pages.stream().allMatch(page -> !page.context().browser().isConnected()));
        assertEquals(1, MatrixFailureFixture.pages.stream().map(page -> page.context().browser()).distinct().count());
    }

    @Test
    void concurrentInvocationsOverlapAndKeepTheirResourcesOnTheirOwningThreads() {
        EngineExecutionResults results = execute(ConcurrentMatrixFixture.class, true, true);
        assertTrue(results.testEvents().failed().list().isEmpty(), () -> results.testEvents().failed().list().stream()
                .map(event -> event.getPayload(TestExecutionResult.class).orElseThrow()
                        .getThrowable().orElseThrow().toString()).toList().toString());
        results.testEvents().assertStatistics(statistics -> statistics.started(3).succeeded(3));
        results.containerEvents().assertStatistics(statistics -> statistics.failed(0));
        assertEquals(3, ConcurrentMatrixFixture.owners.values().stream().distinct().count());
        assertEquals(3, ConcurrentMatrixFixture.pages.stream().map(page -> page.context().browser()).distinct().count());
        assertTrue(ConcurrentMatrixFixture.pages.stream().allMatch(Page::isClosed));
        assertTrue(ConcurrentMatrixFixture.pages.stream().allMatch(page -> !page.context().browser().isConnected()));
    }

    private List<Path> traceFiles() throws IOException {
        try (var files = Files.walk(artifacts)) {
            return files.filter(path -> path.getFileName().toString().equals("trace.zip")).toList();
        }
    }

    @ExtendWith(PlaywrightExtension.class)
    static class TestFailureFixture {
        private static Page failedPage;
        private static Browser browser;

        @Test
        void failsWithTwoOpenTabs(Page page) {
            page.setContent("<h1>First tab</h1>");
            failedPage = page.context().newPage();
            browser = page.context().browser();
            failedPage.setContent("<h1>Second tab</h1>");
            failedPage.evaluate("console.error('second-tab-diagnostic')");
            fail("Deliberate test failure");
        }
    }

    @ExtendWith(PlaywrightExtension.class)
    static class SetupFailureFixture {
        @BeforeEach
        void setup(Page page) {
            page.setContent("<h1>Setup failed</h1>");
            fail("Deliberate setup failure");
        }

        @Test
        void mustNotRun() {
            fail("Test body ran despite setup failure");
        }
    }

    @ExtendWith(PlaywrightExtension.class)
    static class TeardownFailureFixture {
        @Test
        void succeeds(Page page) {
            page.setContent("<h1>Before teardown</h1>");
        }

        @AfterEach
        void teardown() {
            fail("Deliberate teardown failure");
        }
    }

    @ExtendWith(PlaywrightExtension.class)
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    static class IsolationFixture {
        private Browser previousBrowser;

        @ParameterizedTest
        @ValueSource(ints = {1, 2})
        void startsWithAnEmptyContext(int invocation, Page page, BrowserContext context, TestConfig config) {
            assertTrue(config.headless());
            assertTrue(context.cookies().isEmpty(), "Cookies leaked between test invocations");
            assertEquals(1, context.pages().size(), "Tabs leaked between test invocations");
            if (previousBrowser != null) {
                assertNotSame(previousBrowser, context.browser(), "Browser leaked between test invocations");
                assertFalse(previousBrowser.isConnected(), "The previous browser was not closed");
            }
            previousBrowser = context.browser();
            context.addCookies(List.of(new Cookie("isolation-test", Integer.toString(invocation))
                    .setUrl("https://example.test/")));
            page.setContent("<h1>Invocation " + invocation + "</h1>");
            context.newPage();
        }
    }

    @ExtendWith(BrowserMatrixExtension.class)
    @Execution(ExecutionMode.SAME_THREAD)
    static class MatrixIsolationFixture {
        @TestTemplate
        void receivesTheSelectedEngineInAnEmptyContext(Page page, BrowserContext context, TestConfig config) {
            assertEquals(config.browser().directoryName(), context.browser().browserType().name());
            assertTrue(context.cookies().isEmpty(), "Matrix invocations leaked cookies");
            assertEquals(1, context.pages().size(), "Matrix invocations leaked tabs");
            context.addCookies(List.of(new Cookie("matrix-isolation", "value").setUrl("https://example.test/")));
            page.setContent("<h1>Matrix invocation</h1>");
            context.newPage();
        }
    }

    @ExtendWith(BrowserMatrixExtension.class)
    @Execution(ExecutionMode.SAME_THREAD)
    static class MatrixFailureFixture {
        private static final List<Page> pages = new ArrayList<>();

        @BeforeAll
        static void resetProbe() {
            pages.clear();
        }

        @TestTemplate
        void failsWithAnOwnedBrowser(Page page) {
            pages.add(page);
            page.setContent("<h1>Matrix failure</h1>");
            fail("Deliberate matrix failure");
        }
    }

    @ExtendWith(ConcurrentInvocations.class)
    @Execution(ExecutionMode.SAME_THREAD)
    static class ConcurrentMatrixFixture {
        private static final ConcurrentMap<Page, Long> owners = new ConcurrentHashMap<>();
        private static final ConcurrentLinkedQueue<Page> pages = new ConcurrentLinkedQueue<>();
        private static CountDownLatch started;

        @BeforeAll
        static void resetProbe() {
            owners.clear();
            pages.clear();
            started = new CountDownLatch(3);
        }

        @BeforeEach
        void recordOwner(Page page) {
            owners.put(page, Thread.currentThread().threadId());
            pages.add(page);
        }

        @TestTemplate
        @Execution(ExecutionMode.CONCURRENT)
        void overlapsWithTwoOtherIsolatedInvocations(Page page, BrowserContext context, TestConfig config)
                throws InterruptedException {
            assertEquals(config.browser().directoryName(), context.browser().browserType().name());
            assertTrue(context.cookies().isEmpty());
            assertEquals(1, context.pages().size());
            page.setContent("<h1>Concurrent invocation</h1>");
            context.addCookies(List.of(new Cookie("concurrent-isolation", "value").setUrl("https://example.test/")));
            started.countDown();
            assertTrue(started.await(30, TimeUnit.SECONDS), "Three browser invocations did not overlap");
            assertEquals(3, owners.size());
            assertEquals(owners.get(page).longValue(), Thread.currentThread().threadId());
            assertEquals("Concurrent invocation", page.locator("h1").textContent());
        }

        @AfterEach
        void verifyOwner(Page page) {
            assertEquals(owners.get(page).longValue(), Thread.currentThread().threadId());
        }
    }

    public static class ConcurrentInvocations implements TestTemplateInvocationContextProvider {
        @Override
        public boolean supportsTestTemplate(ExtensionContext context) {
            return true;
        }

        @Override
        public Stream<TestTemplateInvocationContext> provideTestTemplateInvocationContexts(ExtensionContext context) {
            return IntStream.range(0, 3).boxed().flatMap(invocation ->
                    BrowserMatrixExtension.contexts(PlaywrightExtension.configurationProperties(context)));
        }
    }
}

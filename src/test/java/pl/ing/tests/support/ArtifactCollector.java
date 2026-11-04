package pl.ing.tests.support;

import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Tracing;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

final class ArtifactCollector {
    private static final System.Logger LOGGER = System.getLogger(ArtifactCollector.class.getName());
    private final BrowserContext context;
    private final Path directory;
    private final List<String> browserLog = new ArrayList<>();

    ArtifactCollector(BrowserContext context, Path directory) {
        this.context = context;
        this.directory = directory;
    }

    void start() {
        context.onPage(this::observe);
        context.tracing().start(new Tracing.StartOptions()
                .setScreenshots(true).setSnapshots(true).setSources(true));
    }

    void finish(boolean retain) {
        if (!retain) {
            context.tracing().stop();
            return;
        }
        List<Page> pages = context.pages();
        for (int index = 0; index < pages.size(); index++) {
            Page page = pages.get(index);
            String prefix = "page-" + (index + 1);
            capture(prefix + " screenshot", () -> page.screenshot(new Page.ScreenshotOptions()
                    .setPath(directory.resolve(prefix + ".png")).setFullPage(true).setTimeout(5000)));
            capture(prefix + " HTML", () -> Files.writeString(directory.resolve(prefix + ".html"), page.content()));
        }
        capture("storage", () -> context.storageState(new BrowserContext.StorageStateOptions()
                .setPath(directory.resolve("storage.json"))));
        capture("browser log", () -> Files.write(directory.resolve("browser.log"), browserLog));
        capture("trace", () -> context.tracing().stop(new Tracing.StopOptions()
                .setPath(directory.resolve("trace.zip"))));
    }

    private void observe(Page page) {
        page.onConsoleMessage(message -> browserLog.add(message.type() + ": " + message.text()));
        page.onPageError(error -> browserLog.add("pageerror: " + error));
        page.onRequestFailed(request -> browserLog.add("requestfailed: " + request.url() + " " + request.failure()));
    }

    private void capture(String description, ArtifactAction action) {
        try {
            Files.createDirectories(directory);
            action.run();
        } catch (Exception error) {
            LOGGER.log(System.Logger.Level.WARNING, "Cannot capture " + description, error);
        }
    }

    @FunctionalInterface
    private interface ArtifactAction {
        void run() throws Exception;
    }
}

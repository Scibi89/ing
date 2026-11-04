package pl.ing.tests;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import com.microsoft.playwright.options.WaitForSelectorState;
import com.microsoft.playwright.options.WaitUntilState;

import java.net.URI;

final class CookieConsentPage {
    private static final double CMP_INITIALIZATION_TIMEOUT_MS = 30000;
    private final Page page;
    private final URI baseUrl;
    private final Locator banner;
    private final Locator settings;

    CookieConsentPage(Page page, URI baseUrl) {
        this.page = page;
        this.baseUrl = baseUrl;
        banner = page.locator(".js-cookie-policy-main").getByRole(AriaRole.DIALOG);
        settings = page.locator(".js-cookie-policy-settings").getByRole(AriaRole.DIALOG);
    }

    void open() {
        page.bringToFront();
        page.navigate(baseUrl.toString(), new Page.NavigateOptions().setWaitUntil(WaitUntilState.LOAD));
        waitUntilReady();
    }

    void reload() {
        page.bringToFront();
        page.reload(new Page.ReloadOptions().setWaitUntil(WaitUntilState.LOAD));
        waitUntilReady();
    }

    Locator banner() {
        return banner;
    }

    Locator technicalCookies() {
        return category("techniczne");
    }

    Locator analyticsCookies() {
        return category("analityczne");
    }

    Locator marketingCookies() {
        return category("marketingowe");
    }

    void customize() {
        banner.getByRole(AriaRole.BUTTON,
                new Locator.GetByRoleOptions().setName("Dostosuj").setExact(true)).click();
        settings.waitFor();
    }

    void reopenSettings() {
        waitUntilReady();
        page.getByRole(AriaRole.BUTTON,
                new Page.GetByRoleOptions().setName("Ustawienia cookie").setExact(true)).click();
        settings.waitFor();
    }

    void select(ConsentPreferences preferences) {
        setCategory(analyticsCookies(), preferences.analytics());
        setCategory(marketingCookies(), preferences.marketing());
    }

    void save() {
        settings.getByRole(AriaRole.BUTTON,
                new Locator.GetByRoleOptions().setName("Zaakceptuj zaznaczone").setExact(true)).click();
        settings.waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.HIDDEN));
    }

    private void waitUntilReady() {
        page.locator("#cpm-wrapper.cpm-stylesLoaded").waitFor(
                new Locator.WaitForOptions().setState(WaitForSelectorState.ATTACHED)
                        .setTimeout(CMP_INITIALIZATION_TIMEOUT_MS));
    }

    private Locator category(String name) {
        return settings.getByRole(AriaRole.SWITCH,
                new Locator.GetByRoleOptions().setName("Cookies " + name).setExact(true));
    }

    private void setCategory(Locator control, boolean enabled) {
        String expected = Boolean.toString(enabled);
        if (!expected.equals(control.getAttribute("aria-checked"))) {
            control.click();
        }
        page.waitForCondition(() -> expected.equals(control.getAttribute("aria-checked")));
    }
}

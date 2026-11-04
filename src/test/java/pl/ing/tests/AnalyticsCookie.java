package pl.ing.tests;

import com.microsoft.playwright.options.Cookie;

import java.util.regex.Pattern;

enum AnalyticsCookie {
    GOOGLE_VISITOR("_ga", "_ga", "GA\\d+\\.\\d+\\.\\d+\\.\\d+", true),
    GOOGLE_SESSION("_ga_<measurement-id>", "_ga_.+", "GS\\d+\\.\\d+\\..+", true),
    ADOBE_VISITOR("AMCV_<organisation-id>AdobeOrg", "AMCV_.+AdobeOrg", ".+", true),
    ADOBE_SESSION("AMCVS_<organisation-id>AdobeOrg", "AMCVS_.+AdobeOrg", "1", false),
    ADOBE_COOKIE_SUPPORT("s_cc", "s_cc", "true", false);

    private final String description;
    private final Pattern namePattern;
    private final Pattern valuePattern;
    private final boolean persistent;

    AnalyticsCookie(String description, String namePattern, String valuePattern, boolean persistent) {
        this.description = description;
        this.namePattern = Pattern.compile(namePattern);
        this.valuePattern = Pattern.compile(valuePattern);
        this.persistent = persistent;
    }

    boolean matchesName(Cookie cookie) {
        return namePattern.matcher(cookie.name).matches();
    }

    boolean matches(Cookie cookie) {
        return matchesName(cookie) && cookie.value != null && valuePattern.matcher(cookie.value).matches();
    }

    boolean persistent() {
        return persistent;
    }

    @Override
    public String toString() {
        return description;
    }
}

package pl.ing.tests;

enum ConsentPreferences {
    TECHNICAL_ONLY("1", false, false),
    ANALYTICS_ONLY("3", true, false);

    private final String cookieValue;
    private final boolean analytics;
    private final boolean marketing;

    ConsentPreferences(String cookieValue, boolean analytics, boolean marketing) {
        this.cookieValue = cookieValue;
        this.analytics = analytics;
        this.marketing = marketing;
    }

    String cookieValue() {
        return cookieValue;
    }

    boolean analytics() {
        return analytics;
    }

    boolean marketing() {
        return marketing;
    }
}

# ING Cookie Consent Tests

Single Maven project using Java 25, Playwright 1.64.0 and JUnit 6.1.3.
Tests visit the live https://www.ing.pl/ website without signing in.
Every test invocation owns its Playwright instance, browser process and fresh context, and closes them afterwards.
This is a test-only project. All Java sources are in `src/test/java/pl/ing/tests`; no application server or Spring Boot runtime is required.

## Recruitment scenario

`AnalyticsCookieConsentTest` implements the requested flow:

1. Open the live ING homepage in a fresh browser context.
2. Click **Dostosuj**.
3. Enable analytics while leaving marketing disabled.
4. Click **Zaakceptuj zaznaczone**, the current label of the requested confirmation button.
5. Read cookies through `BrowserContext.cookies()` and verify `cookiePolicyGDPR=3`, consent metadata, domain, path and expiration.
6. Reload the page and verify the actual Google Analytics and Adobe cookies listed below, then check that the preferences remain analytics-only.

### Observed ING behavior and analytics assertions

Live discovery on 2026-11-04 in fresh Chromium, Firefox and WebKit contexts showed that saving the choice writes the consent cookies immediately. Analytics cookies were not present during the following 20-second observation window; they appeared on the next page load with the saved consent.

The reload is therefore an explicit additional verification step after checking the original consent flow, not a conditional retry or a replacement for checking the cookies written by the confirmation button. This test does not claim that analytics cookies are created immediately by that click.

| Observed analytics cookie | Verification |
| --- | --- |
| `_ga` | Google visitor identifier format and future expiration. |
| `_ga_<measurement-id>` | Google session state format and future expiration. |
| `AMCV_<organisation-id>AdobeOrg` | Non-empty Adobe visitor identifier and future expiration. |
| `AMCVS_<organisation-id>AdobeOrg` | Session cookie with value `1`. |
| `s_cc` | Session cookie with value `true`. |

All five families must be present with domain `.ing.pl` and path `/`. Measurement and organisation identifiers are matched as families rather than hard-coded to a single deployment. The names were observed in the browser and are listed in [ING's cookie policy](https://www.ing.pl/indywidualni/tabele-i-regulaminy/regulacje/polityka-plikow-cookie). Only first-party families observed on the homepage are required; the policy's broader list includes other services and third-party cookies that may be blocked by browsers.

The scenario uses a JUnit `@TestTemplate` browser matrix: Chromium, Firefox and WebKit, with exactly one invocation per engine. JUnit displays `chromium`, `firefox` and `webkit`. All three invocations run concurrently and must pass. Each invocation checks that the actual launched browser engine matches its selected configuration and logs start/end timestamps and its worker thread. Cookie initialization uses a bounded 30-second condition wait. Missing or invalid cookies fail the test with the expected families and observed names; no cookies, scripts or network responses are mocked in the live scenario. Live execution requires access to ING and the Google/Adobe resources loaded by its page; network or anti-bot failures remain failures and are not retried automatically.

## Getting started

Install JDK 25, set `JAVA_HOME`, and make Maven available on `PATH` (or use the Maven Wrapper described below).
Run these commands from the repository root:

```powershell
mvn test-compile exec:java '-Dexec.mainClass=com.microsoft.playwright.CLI' '-Dexec.args=install chromium firefox webkit'
mvn test '-Dtest=AnalyticsCookieConsentTest'
mvn test '-Dtest=AnalyticsCookieConsentTest' '-Dheadless=false'
mvn test '-Dtest=AnalyticsCookieConsentTest' '-Dbrowser=chromium'
mvn test '-Dtest=AnalyticsCookieConsentTest' '-Dbrowser=firefox'
mvn test '-Dtest=AnalyticsCookieConsentTest' '-Dbrowser=webkit'
```

Without `-Dbrowser`, the recruitment test runs all three engines concurrently, once each. Add `-Dheadless=false` to see their windows on Windows. Supplying `-Dbrowser=chromium`, `firefox` or `webkit` filters the matrix to one invocation on that engine; the pipeline uses this filter for its parallel jobs.

To run the recruitment scenario and infrastructure regression tests together, use `mvn verify`. Its default matrix contributes three concurrent invocations. Infrastructure tests use local HTML rather than additional ING scenarios.

In IntelliJ, open the repository's root `pom.xml` as a Maven project and use **Reload All Maven Projects** after updating the project. Maven imports `src/test/java` as the test source root; no manual source-root configuration is needed. Use JDK 25 for the project and Maven importer.
Run `AnalyticsCookieConsentTest` as a JUnit class. To see its windows, add `-Dheadless=false` in **Run → Edit Configurations → JUnit → VM options**. Remove a previously saved `-Dbrowser=chromium` option, or replace it with `-Dbrowser=all`, to run the entire matrix.
For headed Firefox, the temporary Playwright profile disables `widget.windows.window_occlusion_tracking.enabled`. This keeps covered Windows browser windows rendering so CSS transitions and Playwright stability checks can complete; see [Mozilla's automation issue](https://bugzilla.mozilla.org/show_bug.cgi?id=1802473). The setting is limited to headed Firefox test profiles and does not change the user's regular browser configuration.
On Linux, browser installation may require `install --with-deps`.
The first run downloads Maven dependencies and browser binaries into their standard local caches.

### Maven vs Maven Wrapper

`mvn` uses the Maven installation available on `PATH`.
The repository's Maven Wrapper downloads and uses the pinned Maven 3.10.0 distribution, so a separate Maven installation is not required.
Both run the same project goals. To use the wrapper, replace `mvn` with `.\mvnw.cmd` on Windows or `sh ./mvnw` on Linux/macOS:

```powershell
.\mvnw.cmd verify
```

## Coverage

The live suite intentionally covers only the recruitment scenario: fresh-visitor defaults, analytics-only consent, saved consent metadata, actual analytics cookies and persistence after reloading. It does not include accept-all, reject-all, marketing-only or consent-withdrawal scenarios.

The test interacts with the consent management platform (CMP) through its UI; it does not inject cookies or modify consent through JavaScript. Switches and buttons are located by role and accessible name; ING CSS classes distinguish the two dialogs. Navigation activates the target tab, waits for the page `load` event and then waits up to 30 seconds for the observed CMP readiness marker before interactions. This startup budget is separate from ordinary action timeouts. Persistence checks verify that the banner stays hidden before opening the settings dialog.

This is not a comprehensive privacy audit: the test does not establish that every third-party request respects consent or that previously stored tracker cookies are deleted.
ING UI changes, network failures and anti-bot blocks cause test failures rather than skipped cases.

## Architecture

The test suite uses composition instead of a shared test superclass:

| Class | Responsibility |
| --- | --- |
| `AnalyticsCookieConsentTest` | Required recruitment scenario, once per engine in concurrent isolated invocations. |
| `AnalyticsCookie` | Observed analytics cookie families and expected value formats. |
| `AnalyticsCookieAssertions` | Bounded checks for real analytics cookies, scope and expiration. |
| `CookieConsentPage` | Page Object: locators, navigation, UI actions and synchronization. Receives `Page` and the base URL through its constructor. |
| `ConsentPreferences` | Fresh-visitor and analytics-only preference states and their consent cookie values. |
| `ConsentAssertions` | Business assertions for the UI and persisted consent, separate from UI actions. |
| `TestConfig` | Immutable, validated single-engine configuration selected before launching a test invocation. |
| `BrowserEngine` | Enum-based browser selection using Playwright method references; adding a choice does not change fixture orchestration. |
| `BrowserMatrixExtension` | One named template invocation per engine, with a configurable single-engine filter. |
| `PlaywrightExtension` | Per-invocation lifecycle and parameter injection for `Page`, `BrowserContext` and `TestConfig`. Owns and closes each invocation's resources. |
| `ArtifactCollector` | Traces, logs and per-tab failure artifacts, independent of ING page objects. |

Single responsibilities keep changes to UI selectors, configuration, assertions and diagnostics separate. Dependencies are passed explicitly instead of read from global properties inside page objects. JUnit callback interfaces provide the fixture's extension points; no application-wide singleton or extra dependency-injection framework is needed.

`src/test/resources/junit-platform.properties` enables JUnit parallel execution with three workers. The template method in `AnalyticsCookieConsentTest` opts in with `@Execution(ExecutionMode.CONCURRENT)`; its containing class stays `SAME_THREAD` so other classes do not occupy its workers during a full-suite run. Every invocation creates, uses and closes its own Playwright instance, browser and context on its own worker thread. No Playwright object is shared between workers. This follows [Playwright's threading rules](https://playwright.dev/java/docs/multithreading) and [JUnit's parallel execution configuration](https://docs.junit.org/6.1.3/writing-tests/parallel-execution.html). The classpath properties file applies to both Maven and IntelliJ's JUnit runner; no IDE-only parallel setting is needed.
The fixture uses a consistent 1280×720 viewport so headed browser windows fit typical desktop work areas.

`TestConfigTest` checks valid and invalid configuration. `BrowserMatrixExtensionTest` checks that default selection produces exactly three named invocations and that explicit browser selection filters the matrix to one. `AnalyticsCookieTest` verifies that analytics cookie matching rejects incorrect names and values. `PlaywrightExtensionTest` uses JUnit Platform Test Kit and local HTML to verify context isolation, browser selection, per-invocation browser cleanup, successful artifact retention, and diagnostics after setup, test-body and teardown failures. A concurrency regression uses three isolated instances of the selected engine and a bounded latch: all three test bodies must be active together on distinct threads, with each lifecycle staying on its owning thread. It also verifies that all browsers close afterwards. Its deliberately failing nested fixtures are executed only by Test Kit; the surrounding regression tests must pass.

To run just the infrastructure regression tests, without visiting ING:

```powershell
mvn test '-Dtest=TestConfigTest,PlaywrightExtensionTest,BrowserMatrixExtensionTest,AnalyticsCookieTest'
```

## Options and diagnostics

- `-Dbrowser=all|chromium|firefox|webkit` — defaults to `all` for the recruitment matrix. Infrastructure browser tests use Chromium when `all` is selected.
- `-Dheadless=false` — shows the browser window.
- `-DretainArtifacts=true` — saves artifacts for successful tests as well.
- `-DbaseUrl=...` — overrides the URL; cookie domain assertions remain specific to ING.

Invalid browser names, malformed URLs and boolean values other than `true` or `false` fail before launching a browser. Infrastructure tests can also override these settings through JUnit configuration parameters, including `artifactsDirectory` for isolated temporary output.

The following output paths are relative to the repository root.
JUnit/Surefire reports are saved to `target/surefire-reports/<selection>/`, for example `all/` for the default local matrix and `firefox/` for a filtered run. Artifact directories always use the actual engine name.
On failure, `target/artifacts/<browser>/<case>-<id>/` contains screenshots and HTML for every open tab (`page-1.png`, `page-1.html`, etc.), cookies/localStorage state, a browser log covering all tabs and `trace.zip`.
Artifacts may contain anonymous session identifiers. Git ignores them.

To open a trace from the repository root, replace `CASE` with the actual artifact directory name:

```powershell
mvn exec:java '-Dexec.mainClass=com.microsoft.playwright.CLI' '-Dexec.args=show-trace target/artifacts/chromium/CASE/trace.zip'
```

## CI and verification

The `.github/workflows/cookie-consent.yml` workflow uses a Chromium/Firefox/WebKit matrix with `max-parallel: 3` and `fail-fast: false`. Each job installs its browser on Ubuntu with Java 25, runs the 39 infrastructure regression cases, then runs the dedicated recruitment scenario exactly once using an explicit `-Dbrowser` filter. A default local `mvn verify` runs 42 cases: three recruitment invocations and 39 infrastructure cases. With a single-engine filter, the full suite runs 40 cases.

Local concurrency comes from the three JUnit workers. CI concurrency comes from three independent matrix jobs, each running one engine. No Java test shares a Playwright instance across threads. Actual CI scheduling depends on GitHub runner availability and account limits.

The relevant pipeline configuration is below; the full workflow also installs browsers, runs infrastructure tests and uploads diagnostics:

```yaml
jobs:
  consent:
    runs-on: ubuntu-latest
    strategy:
      fail-fast: false
      max-parallel: 3
      matrix:
        browser: [chromium, firefox, webkit]
    steps:
      - uses: actions/checkout@v6
      - uses: actions/setup-java@v5
        with:
          distribution: temurin
          java-version: '25'
      - name: Install selected browser
        run: mvn -B -ntp test-compile exec:java -Dexec.mainClass=com.microsoft.playwright.CLI "-Dexec.args=install --with-deps ${{ matrix.browser }}"
      - name: Run recruitment scenario once on the selected browser
        run: mvn -B -ntp verify "-Dtest=AnalyticsCookieConsentTest" "-Dbrowser=${{ matrix.browser }}" -DretainArtifacts=true
```

The recruitment step enables artifact retention even on success. Reports, trace files, screenshots and browser storage snapshots are uploaded as `consent-chromium`, `consent-firefox` and `consent-webkit` for seven days. Successful reports also list the verified analytics cookie names without printing visitor identifiers.

Publication is performed by the repository owner. After pushing the project to GitHub, open **Actions → ING cookie consent**, check that all three matrix jobs are green, and share the repository URL and workflow-run URL with the reviewer. `workflow_dispatch` also allows a manual run. A workflow file and local test results are not evidence that GitHub Actions has executed; no remote run or publication is claimed here.

### Local verification

On Windows with Java 25, the post-cleanup default `mvn verify` passed all 42 cases on 2026-11-05, with zero failures, errors or skips. The workflow also passed `actionlint` validation.

Execution reports are saved under `target/surefire-reports/`. Historical local evidence is retained in the Git-ignored `target/verification/` and `target/artifacts/` directories; it is not part of the published repository.

An earlier concurrent headed run on Windows failed during Firefox context closure with `TargetClosedError` after all business assertions had completed. Later manual checks passed, including teardown. The cause of that intermittent failure is not established; a successful recheck does not erase the failure or guarantee that it cannot recur. No automatic test retries or cleanup-error suppression were added.

Documentation: [Playwright Java](https://playwright.dev/java/docs/intro), [JUnit test isolation](https://playwright.dev/java/docs/test-runners), [traces](https://playwright.dev/java/docs/trace-viewer), [CI](https://playwright.dev/java/docs/ci).

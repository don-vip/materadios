package eu.materadios.service;

import java.io.IOError;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.Response;

@Service
public class MateraAuthService {

    private static final Logger log = LoggerFactory.getLogger(MateraAuthService.class);

    @Value("${matera.username:}")
    private String username;

    @Value("${matera.password:}")
    private String password;

    @Value("${matera.base.url:https://app.matera.eu}")
    private String baseUrl;

    @Value("${matera.api.url:https://api-core.matera.eu}")
    private String apiUrl;

    private final AtomicReference<String> cookieHeader = new AtomicReference<>();

    public String getCookieHeader() {
        if (cookieHeader.get() == null || cookieHeader.get().isBlank()) {
            log.info("Matera cookies missing, performing login to Matera");
            loginWithCredentials();
        }
        return cookieHeader.get();
    }

    private void loginWithCredentials() {
        // fallbacks: if @Value didn't inject, read from system properties or env vars
        if (username == null || username.isBlank()) {
            String v = System.getProperty("matera.username");
            if (v == null)
                v = System.getenv("MATERA_USERNAME");
            if (v != null && !v.isBlank()) {
                username = v;
                log.info("Loaded matera.username from system/env");
            }
        }
        if (password == null || password.isBlank()) {
            String v = System.getProperty("matera.password");
            if (v == null)
                v = System.getenv("MATERA_PASSWORD");
            if (v != null && !v.isBlank()) {
                password = v;
                log.info("Loaded matera.password from system/env (hidden)");
            }
        }
        if (baseUrl == null || baseUrl.isBlank()) {
            String v = System.getProperty("matera.base.url");
            if (v == null)
                v = System.getenv("MATERA_BASE_URL");
            if (v != null && !v.isBlank()) {
                baseUrl = v;
                log.info("Loaded matera.base.url from system/env: {}", baseUrl);
            }
        }

        // as a last resort, try loading .env directly from disk (walk up parent dirs)
        if ((username == null || username.isBlank()) || (password == null || password.isBlank())
                || (baseUrl == null || baseUrl.isBlank())) {
            Path found = null;
            // 1) current dir and upwards
            Path cur = Paths.get(".").toAbsolutePath().normalize();
            Path p = cur;
            while (p != null) {
                Path cand = p.resolve(".env");
                if (Files.exists(cand)) {
                    found = cand;
                    break;
                }
                p = p.getParent();
            }
            // 2) look near code source (target/classes or target)
            if (found == null) {
                try {
                    URL src = MateraAuthService.class.getProtectionDomain().getCodeSource().getLocation();
                    if (src != null) {
                        Path codePath = Paths.get(src.toURI()).toAbsolutePath().normalize();
                        // climb up to project root heuristically
                        for (int i = 0; i < 4 && codePath != null; i++) {
                            Path cand = codePath.resolve(".env");
                            if (Files.exists(cand)) {
                                found = cand;
                                break;
                            }
                            codePath = codePath.getParent();
                        }
                    }
                } catch (Exception ignore) {
                    // ignore
                }
            }
            // 3) user.home
            if (found == null) {
                Path home = Paths.get(System.getProperty("user.home", ""));
                if (home != null) {
                    Path cand = home.resolve(".env");
                    if (Files.exists(cand)) {
                        found = cand;
                    }
                }
            }

            if (found != null) {
                try {
                    String content = Files.readString(found);
                    Pattern pattern = Pattern
                            .compile("(?m)(MATERA_[A-Z_]+)\\s*=\\s*(\"([^\"]*)\"|'([^']*)'|([^\\r\\n#]+))");
                    Matcher m = pattern.matcher(content);
                    while (m.find()) {
                        String key = m.group(1);
                        String val = null;
                        if (m.group(3) != null)
                            val = m.group(3);
                        else if (m.group(4) != null)
                            val = m.group(4);
                        else if (m.group(5) != null)
                            val = m.group(5);
                        if (val == null)
                            val = "";
                        val = val.trim();
                        if ("MATERA_USERNAME".equals(key) && (username == null || username.isBlank())) {
                            username = val;
                            log.info("Loaded MATERA_USERNAME from .env at {}", found);
                        } else if ("MATERA_PASSWORD".equals(key) && (password == null || password.isBlank())) {
                            password = val;
                            log.info("Loaded MATERA_PASSWORD from .env at {} (hidden)", found);
                        } else if ("MATERA_BASE_URL".equals(key) && (baseUrl == null || baseUrl.isBlank())) {
                            baseUrl = val;
                            log.info("Loaded MATERA_BASE_URL from .env at {}", found);
                        }
                    }
                } catch (IOException e) {
                    throw new IllegalStateException(e);
                }
            }
        }

        // Try to reuse persisted cookies from previous runs before asking credentials
        String diskCookies = readCookieFromDisk();
        if (diskCookies != null && !diskCookies.isBlank()) {
            cookieHeader.set(diskCookies);
            if (testCookieValidity(diskCookies)) {
                log.info("Loaded valid Matera cookies from disk: {}", cookieFilePath());
                return;
            } else {
                log.info("Persisted Matera cookies are invalid, will perform login");
                cookieHeader.set(null);
            }
        }

        if (username == null || username.isBlank() || password == null || password.isBlank()) {
            throw new IllegalStateException("Matera credentials (MATERA_USERNAME/MATERA_PASSWORD) are not configured");
        }

        loginWithBrowser();
    }

    private void loginWithBrowser() {
        try (Playwright playwright = Playwright.create()) {
            BrowserType.LaunchOptions lo = new BrowserType.LaunchOptions();
            lo.setHeadless(false);
            lo.setSlowMo(50);
            Browser browser = playwright.chromium().launch(lo);
            BrowserContext context = browser.newContext();
            Page page = context.newPage();

            page.navigate(baseUrl + "/users/sign_in");
            page.waitForLoadState();
            page.waitForCondition(() -> page.locator("input[type=\"password\"]").count() > 0);

            if (page.locator("input[type=\"email\"]").count() > 0) {
                page.locator("input[type=\"email\"]").first().fill(username);
            } else if (page.locator("input[name=\"email\"]").count() > 0) {
                page.locator("input[name=\"email\"]").first().fill(username);
            } else {
                throw new RuntimeException("No email input found");
            }
            if (page.locator("input[type=\"password\"]").count() > 0) {
                page.locator("input[type=\"password\"]").first().fill(password);
            } else {
                throw new RuntimeException("No password input found");
            }

            if (page.locator("button[type=submit]").count() > 0) {
                page.locator("button[type=submit]").first().click();
            } else if (page.locator("input[type=submit]").count() > 0) {
                page.locator("input[type=submit]").first().click();
            } else {
                throw new RuntimeException("No submit input found");
            }

            Response response = page.waitForResponse(
                    r -> r.ok() && "POST".equals(r.request().method()) && r.url().contains(apiUrl + "/users/sign_in"),
                    new Page.WaitForResponseOptions().setTimeout(300_000), () -> log.info("Logged in to Matera!"));

            log.info("{} {} => {}", response.request().method(), response.url(), response.ok());

            // read cookies for api-core host from browser context
            List<?> apiCookies = context.cookies(apiUrl);
            if (apiCookies != null && !apiCookies.isEmpty()) {
                StringBuilder cb = new StringBuilder();
                for (Object c : apiCookies) {
                    try {
                        String n = null, v = null;
                        try {
                            Method m = c.getClass().getMethod("name");
                            n = (String) m.invoke(c);
                        } catch (Exception ignored) {
                        }
                        if (n == null)
                            try {
                                Method m = c.getClass().getMethod("getName");
                                n = (String) m.invoke(c);
                            } catch (Exception ignored) {
                            }
                        if (n == null)
                            try {
                                Field f = c.getClass().getField("name");
                                n = (String) f.get(c);
                            } catch (Exception ignored) {
                            }
                        try {
                            Method m2 = c.getClass().getMethod("value");
                            v = (String) m2.invoke(c);
                        } catch (Exception ignored) {
                        }
                        if (v == null)
                            try {
                                Method m2 = c.getClass().getMethod("getValue");
                                v = (String) m2.invoke(c);
                            } catch (Exception ignored) {
                            }
                        if (v == null)
                            try {
                                Field f2 = c.getClass().getField("value");
                                v = (String) f2.get(c);
                            } catch (Exception ignored) {
                            }
                        if (n != null && v != null) {
                            if (cb.length() > 0)
                                cb.append(';');
                            cb.append(n).append('=').append(v);
                        }
                    } catch (Exception ignored) {
                    }
                }
                String all = cb.toString();
                if (all.contains("_matera_session")) {
                    cookieHeader.set(all);
                    log.info("Captured cookies from browser context for api-core host");
                } else {
                    throw new RuntimeException("_matera_session absent from cookies");
                }
            }

            // persist cookies captured from browser for reuse
            if (cookieHeader.get() != null && !cookieHeader.get().isBlank()) {
                persistCookiesToDisk(cookieHeader.get());
            }

            context.close();
            browser.close();
        }
    }

    private Path cookieFilePath() {
        try {
            return Paths.get("data", "matera_cookies.txt").toAbsolutePath().normalize();
        } catch (InvalidPathException | IOError e) {
            return Paths.get("data\\matera_cookies.txt").toAbsolutePath().normalize();
        }
    }

    private String readCookieFromDisk() {
        try {
            Path p = cookieFilePath();
            if (Files.exists(p)) {
                String s = Files.readString(p).trim();
                if (!s.isBlank())
                    return s;
            }
        } catch (IOException e) {
            log.debug("Failed to read cookie file: {}", e.getMessage());
        }
        return null;
    }

    private void persistCookiesToDisk(String cookies) {
        try {
            Path p = cookieFilePath();
            Files.createDirectories(p.getParent());
            Files.writeString(p, cookies, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            log.info("Persisted Matera cookies to disk: {}", p);
        } catch (IOException e) {
            log.debug("Failed to persist cookie file: {}", e.getMessage());
        }
    }

    private boolean testCookieValidity(String cookies) {
        try (HttpClient client = HttpClient.newBuilder().build()) {
            HttpResponse<String> r = MateraApiService.callApi(client, apiUrl, "context", cookies);
            return r.statusCode() >= 200 && r.statusCode() < 300;
        } catch (IOException | InterruptedException e) {
            log.debug("Cookie validity check failed: {}", e.getMessage());
            return false;
        }
    }

    // expose base url for clients
    public String getBaseUrl() {
        return this.baseUrl;
    }
}

package com.aresstack.serp4j.browser.playwright;

import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.options.Cookie;
import com.microsoft.playwright.options.SameSiteAttribute;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Keeps the persistent cookies of a leased profile in {@value #FILE} inside the profile directory. Browsers write
 * their own cookie store lazily and only on a clean shutdown, and not every driver shuts a persistent browser down
 * cleanly (the GraalJS driver leaves it running, so it has to be killed); saving the cookies explicitly on close and
 * restoring them on open makes the profile contract independent of that. Session cookies are not kept, like in a
 * browser. One line per cookie, fields URL-encoded and tab-separated.
 */
final class ProfileCookies {

    static final String FILE = ".serp4j-cookies.tsv";
    private static final Logger LOG = Logger.getLogger(ProfileCookies.class.getName());
    private static final String UTF8 = StandardCharsets.UTF_8.name();

    private ProfileCookies() {
    }

    /** Owner thread: writes the persistent cookies of the context; failures are logged, the close goes on. */
    static void save(BrowserContext context, Path directory) {
        try {
            StringBuilder out = new StringBuilder();
            for (Cookie cookie : context.cookies()) {
                if (cookie.expires == null || cookie.expires <= 0) {
                    continue;
                }
                out.append(enc(cookie.name)).append('\t').append(enc(cookie.value)).append('\t')
                        .append(enc(cookie.domain)).append('\t').append(enc(cookie.path)).append('\t')
                        .append(cookie.expires).append('\t').append(Boolean.TRUE.equals(cookie.httpOnly)).append('\t')
                        .append(Boolean.TRUE.equals(cookie.secure)).append('\t')
                        .append(cookie.sameSite == null ? "" : cookie.sameSite.name()).append('\n');
            }
            Path target = directory.resolve(FILE);
            Path temp = directory.resolve(FILE + ".tmp");
            // owner-only before the first byte is written: the file holds authentication cookies
            PrivateFiles.createPrivateFile(temp);
            Files.write(temp, out.toString().getBytes(StandardCharsets.UTF_8), StandardOpenOption.WRITE,
                    StandardOpenOption.TRUNCATE_EXISTING);
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException | RuntimeException e) {
            LOG.log(Level.WARNING, "could not save the cookies of browser profile " + directory, e);
        }
    }

    /** Owner thread: restores saved cookies that have not expired; a missing or broken file restores nothing. */
    static void restore(BrowserContext context, Path directory) {
        Path file = directory.resolve(FILE);
        if (!Files.isRegularFile(file)) {
            return;
        }
        try {
            double now = System.currentTimeMillis() / 1000.0;
            List<Cookie> cookies = new ArrayList<Cookie>();
            for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                String[] f = line.split("\t", -1);
                if (f.length != 8 || Double.parseDouble(f[4]) <= now) {
                    continue;
                }
                Cookie cookie = new Cookie(dec(f[0]), dec(f[1])).setDomain(dec(f[2])).setPath(dec(f[3]))
                        .setExpires(Double.parseDouble(f[4])).setHttpOnly(Boolean.parseBoolean(f[5]))
                        .setSecure(Boolean.parseBoolean(f[6]));
                if (!f[7].isEmpty()) {
                    cookie.setSameSite(SameSiteAttribute.valueOf(f[7]));
                }
                cookies.add(cookie);
            }
            if (!cookies.isEmpty()) {
                context.addCookies(cookies);
            }
        } catch (IOException | RuntimeException e) {
            LOG.log(Level.WARNING, "could not restore the cookies of browser profile " + directory, e);
        }
    }

    private static String enc(String value) throws UnsupportedEncodingException {
        return URLEncoder.encode(value == null ? "" : value, UTF8);
    }

    private static String dec(String value) {
        try {
            return URLDecoder.decode(value, UTF8);
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException(e);
        }
    }
}

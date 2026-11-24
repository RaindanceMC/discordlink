package de.raindancer.modules.discordlink.hush;

import java.util.Optional;
import java.util.regex.Pattern;

/**
 * An https address that is safe to put into {@code <click:open_url:'…'>}: nothing in it can close the quote or open a
 * tag, whoever wrote it.
 */
public final class SafeUrl {

    private static final Pattern HTTPS = Pattern.compile("https://[A-Za-z0-9.-]+\\.[A-Za-z]{2,}(/[A-Za-z0-9._~/?=&%#-]*)?");

    private SafeUrl() {
    }

    public static Optional<String> https(String url) {
        if (url == null) {
            return Optional.empty();
        }
        String stripped = url.strip();
        return HTTPS.matcher(stripped).matches() ? Optional.of(stripped) : Optional.empty();
    }
}

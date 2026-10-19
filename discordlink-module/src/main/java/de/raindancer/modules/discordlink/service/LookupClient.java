package de.raindancer.modules.discordlink.service;

import de.raindancer.modules.discordlink.model.LookupReport;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;

/** Asks the applications website's /api/lookup. Blocks for up to a few seconds: never on a server thread. */
public final class LookupClient implements IDiscordLinkService {

    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(TIMEOUT).build();

    public Optional<LookupReport> fetch(String baseUrl, String key, String query) {
        if (baseUrl == null || baseUrl.isBlank() || key == null || key.isBlank() || query == null || query.isBlank()) {
            return Optional.empty();
        }
        String base = baseUrl.strip().endsWith("/") ? baseUrl.strip().substring(0, baseUrl.strip().length() - 1) : baseUrl.strip();
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(base + "/api/lookup?q="
                            + URLEncoder.encode(query.strip(), StandardCharsets.UTF_8)))
                    .timeout(TIMEOUT).header("Authorization", "Bearer " + key.strip()).header("Accept", "application/json")
                    .GET().build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            return response.statusCode() == 200 ? LookupReport.parse(response.body()) : Optional.empty();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return Optional.empty();
        } catch (Exception failed) {
            return Optional.empty();
        }
    }

    @Override
    public void settings(de.raindancer.modules.discordlink.DiscordLinkSettings settings) {
        // Reads nothing itself: the URL comes with every call.
    }
}

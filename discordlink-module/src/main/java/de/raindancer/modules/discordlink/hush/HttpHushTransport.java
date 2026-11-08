package de.raindancer.modules.discordlink.hush;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/** {@link HushTransport} over java.net.http. Redirects are not followed: the token must only go where it was set. */
public final class HttpHushTransport implements HushTransport {

    private static final Duration CONNECT = Duration.ofSeconds(5);
    private static final Duration REQUEST = Duration.ofSeconds(10);

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(CONNECT)
            .followRedirects(HttpClient.Redirect.NEVER).build();

    @Override
    public Response post(String url, HushToken token, String json) {
        HttpRequest request;
        try {
            URI uri = URI.create(url);
            if (uri.getScheme() == null || !(uri.getScheme().equalsIgnoreCase("http") || uri.getScheme().equalsIgnoreCase("https"))
                    || uri.getHost() == null) {
                return new Response(BAD_URL, -1, "the url is not an http(s) address");
            }
            request = HttpRequest.newBuilder(uri).timeout(REQUEST)
                    .header("Authorization", "Bearer " + token.value())
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8)).build();
        } catch (IllegalArgumentException badUrl) {
            return new Response(BAD_URL, -1, "the url is not valid");
        }
        try {
            HttpResponse<Void> response = http.send(request, HttpResponse.BodyHandlers.discarding());
            long retryAfter = response.headers().firstValue("Retry-After").map(HttpHushTransport::seconds).orElse(-1L);
            return Response.status(response.statusCode(), retryAfter);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return new Response(IO_FAILURE, -1, "interrupted");
        } catch (IOException | RuntimeException failed) {
            return new Response(IO_FAILURE, -1, failed.getClass().getSimpleName()
                    + (failed.getMessage() == null ? "" : ": " + failed.getMessage()));
        }
    }

    private static long seconds(String header) {
        try {
            return Math.max(0, Long.parseLong(header.strip()));
        } catch (NumberFormatException httpDate) {
            return -1;
        }
    }
}

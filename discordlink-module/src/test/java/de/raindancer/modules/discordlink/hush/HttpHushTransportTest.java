package de.raindancer.modules.discordlink.hush;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/** The real HTTP client against a real local server. */
class HttpHushTransportTest {

    private HttpServer server;
    private final AtomicInteger status = new AtomicInteger(200);
    private final AtomicReference<String> retryAfter = new AtomicReference<>();
    private final AtomicReference<String> authorization = new AtomicReference<>();
    private final AtomicReference<String> contentType = new AtomicReference<>();
    private final AtomicReference<String> method = new AtomicReference<>();
    private final AtomicReference<String> body = new AtomicReference<>();
    private final AtomicReference<String> path = new AtomicReference<>();
    private String url;

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            method.set(exchange.getRequestMethod());
            path.set(exchange.getRequestURI().getPath());
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            contentType.set(exchange.getRequestHeaders().getFirst("Content-Type"));
            body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            if (retryAfter.get() != null) {
                exchange.getResponseHeaders().add("Retry-After", retryAfter.get());
            }
            byte[] answer = "{\"accepted\":1}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status.get(), answer.length);
            exchange.getResponseBody().write(answer);
            exchange.close();
        });
        server.start();
        url = "http://127.0.0.1:" + server.getAddress().getPort() + "/api/minecraft/events";
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    private final HttpHushTransport transport = new HttpHushTransport();

    @Test
    @DisplayName("POSTs the JSON with a Bearer token and a JSON content type")
    void posts() {
        HushTransport.Response response = transport.post(url, new HushToken("tok123"), "{\"v\":1}");

        assertThat(response.status()).isEqualTo(200);
        assertThat(method.get()).isEqualTo("POST");
        assertThat(path.get()).isEqualTo("/api/minecraft/events");
        assertThat(authorization.get()).isEqualTo("Bearer tok123");
        assertThat(contentType.get()).startsWith("application/json");
        assertThat(body.get()).isEqualTo("{\"v\":1}");
    }

    @Test
    @DisplayName("the status and Retry-After come back for 429, 401, 404 and 503")
    void statuses() {
        retryAfter.set("17");
        status.set(429);
        HushTransport.Response limited = transport.post(url, new HushToken("t"), "{}");
        assertThat(limited.status()).isEqualTo(429);
        assertThat(limited.retryAfterSeconds()).isEqualTo(17);

        retryAfter.set(null);
        for (int each : new int[]{401, 404, 503}) {
            status.set(each);
            HushTransport.Response response = transport.post(url, new HushToken("t"), "{}");
            assertThat(response.status()).isEqualTo(each);
            assertThat(response.retryAfterSeconds()).isEqualTo(-1);
        }
    }

    @Test
    @DisplayName("a refused connection is an IO failure, never an exception")
    void refused() {
        int port = server.getAddress().getPort();
        server.stop(0);
        HushTransport.Response response = transport.post("http://127.0.0.1:" + port + "/x", new HushToken("t"), "{}");
        assertThat(response.status()).isEqualTo(HushTransport.IO_FAILURE);
        assertThat(response.failure()).isNotBlank();
    }

    @Test
    @DisplayName("a url that is no http address is a bad url, nothing is sent")
    void badUrl() {
        assertThat(transport.post("not a url", new HushToken("t"), "{}").status()).isEqualTo(HushTransport.BAD_URL);
        assertThat(transport.post("ftp://example.org/x", new HushToken("t"), "{}").status()).isEqualTo(HushTransport.BAD_URL);
        assertThat(transport.post("", new HushToken("t"), "{}").status()).isEqualTo(HushTransport.BAD_URL);
    }

    @Test
    @DisplayName("a failure never names the token")
    void failureWithoutToken() {
        int port = server.getAddress().getPort();
        server.stop(0);
        HushTransport.Response response = transport.post("http://127.0.0.1:" + port + "/x", new HushToken("topsecret"), "{}");
        assertThat(String.valueOf(response)).doesNotContain("topsecret");
    }
}

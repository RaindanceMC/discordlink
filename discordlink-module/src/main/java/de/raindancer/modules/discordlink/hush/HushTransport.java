package de.raindancer.modules.discordlink.hush;

/** One POST of a JSON body to Hush. Blocks: only ever called off the server's threads. */
public interface HushTransport {

    /**
     * @param status            the HTTP status; {@link #IO_FAILURE} when nothing came back (timeout, refused,
     *                          reset), {@link #BAD_URL} when the url cannot be called at all
     * @param retryAfterSeconds the Retry-After header, -1 when absent
     * @param failure           what went wrong in words, for IO failures; never contains the token
     * @param body              the answer's body (protocol v2: the actions for this server); empty when there is none
     */
    record Response(int status, long retryAfterSeconds, String failure, String body) {
        public Response {
            body = body == null ? "" : body;
        }

        public Response(int status, long retryAfterSeconds, String failure) {
            this(status, retryAfterSeconds, failure, "");
        }

        public static Response status(int status, long retryAfterSeconds) {
            return new Response(status, retryAfterSeconds, null, "");
        }

        public static Response status(int status, long retryAfterSeconds, String body) {
            return new Response(status, retryAfterSeconds, null, body);
        }
    }

    int IO_FAILURE = 0;
    int BAD_URL = -1;

    Response post(String url, HushToken token, String json);
}

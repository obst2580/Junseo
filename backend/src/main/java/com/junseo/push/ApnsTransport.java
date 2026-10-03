package com.junseo.push;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

/** The HTTP layer under {@link ApnsPushSender}, separated so response handling can be tested offline. */
public interface ApnsTransport {

    record Response(int status, String body) {}

    Response post(URI uri, Map<String, String> headers, byte[] body) throws IOException, InterruptedException;

    /** APNs requires HTTP/2; one client multiplexes all requests over a shared connection. */
    static ApnsTransport http2() {
        HttpClient client = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_2)
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        return (uri, headers, body) -> {
            HttpRequest.Builder request = HttpRequest.newBuilder(uri)
                    .timeout(Duration.ofSeconds(15))
                    .POST(HttpRequest.BodyPublishers.ofByteArray(body));
            headers.forEach(request::header);
            HttpResponse<String> response = client.send(request.build(), HttpResponse.BodyHandlers.ofString());
            return new Response(response.statusCode(), response.body());
        };
    }
}

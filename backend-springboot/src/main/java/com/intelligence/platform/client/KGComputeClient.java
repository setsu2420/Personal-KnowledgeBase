package com.intelligence.platform.client;

import org.springframework.stereotype.Component;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

@Component
public class KGComputeClient {
    private final HttpClient client = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .build();
    private final String baseUrl = "http://localhost:8101";

    public String computeCommunities(String json) throws Exception {
        return post("/compute/communities", json);
    }

    public String computeCentrality(String json) throws Exception {
        return post("/compute/centrality", json);
    }

    private String post(String path, String json) throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
            .uri(URI.create(baseUrl + path))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(json))
            .build();
        HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
        return resp.body();
    }
}

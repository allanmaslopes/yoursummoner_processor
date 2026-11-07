package com.your.summoner.processor.infrastructure.adapter.in.rest;

import static com.your.summoner.processor.support.RecomputeFixtures.PATCH;
import static com.your.summoner.processor.support.RecomputeFixtures.snapshot;
import static org.assertj.core.api.Assertions.assertThat;

import com.your.summoner.processor.application.port.out.KnowledgeStore;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "server.compression.min-response-size=1")
@ActiveProfiles("test")
class KnowledgeGzipTest {
    @LocalServerPort int port;
    @Autowired KnowledgeStore store;

    @Test
    void packageIsGzippedOverHttp() throws Exception {
        store.publish(snapshot(PATCH, 1));
        try {
            var request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/processor/knowledge/" + PATCH))
                    .header("Accept-Encoding", "gzip").GET().build();
            var response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofByteArray());

            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(response.headers().firstValue("Content-Encoding")).contains("gzip");
            assertThat(response.headers().firstValue("ETag")).hasValueSatisfying(tag -> assertThat(tag).startsWith("W/\""));
        } finally {
            store.remove(PATCH);
        }
    }
}

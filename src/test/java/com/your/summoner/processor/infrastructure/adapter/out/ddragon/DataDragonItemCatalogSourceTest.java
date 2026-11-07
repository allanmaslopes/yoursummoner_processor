package com.your.summoner.processor.infrastructure.adapter.out.ddragon;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.your.summoner.processor.domain.model.PatchVersion;
import feign.Feign;
import feign.codec.StringDecoder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.cloud.openfeign.support.SpringMvcContract;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.*;

class DataDragonItemCatalogSourceTest {
    @TempDir Path cache;

    @Test void catalogIsCachedOnDisk() throws Exception {
        var server = new WireMockServer(0);
        server.start();
        try {
            String fixture;
            try (var input = getClass().getResourceAsStream("/fixtures/ddragon-items.json")) {
                fixture = new String(input.readAllBytes(), StandardCharsets.UTF_8);
            }
            server.stubFor(get(urlEqualTo("/api/versions.json")).willReturn(okJson(
                    "[\"16.23.1\",\"16.22.2\",\"16.22.10\",\"16.22.9\",\"16.2.99\"]")));
            server.stubFor(get(urlEqualTo("/cdn/16.22.10/data/en_US/item.json")).willReturn(okJson(fixture)));
            var client = Feign.builder().contract(new SpringMvcContract()).decoder(new StringDecoder())
                    .target(DataDragonClient.class, server.baseUrl());
            var patch = new PatchVersion(16, 22);
            var first = new DataDragonItemCatalogSource(client, cache).forPatch(patch);
            assertThat(first.isCompleted(3031)).isTrue();
            assertThat(first.isCompleted(3006)).isFalse();
            assertThat(first.isTreeItem(3006)).isTrue();
            assertThat(first.normalize(3172)).isEqualTo(3006);
            assertThat(Files.readString(cache.resolve("16.22/item.json"))).isEqualTo(fixture);
            server.resetRequests();
            var second = new DataDragonItemCatalogSource(client, cache).forPatch(patch);
            assertThat(second.normalize(3172)).isEqualTo(3006);
            server.verify(0, anyRequestedFor(anyUrl()));
        } finally {
            server.stop();
        }
    }

    @Test void unavailablePatchFailsWithoutFetchingAnotherPatch() {
        DataDragonClient client = new DataDragonClient() {
            public String versions() { return "[\"16.23.1\",\"16.2.10\"]"; }
            public String items(String version) { throw new AssertionError("wrong patch fetched"); }
        };
        assertThatThrownBy(() -> new DataDragonItemCatalogSource(client, cache).forPatch(new PatchVersion(16, 22)))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("16.22");
    }
}

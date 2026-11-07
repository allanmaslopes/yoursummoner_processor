package com.your.summoner.processor.infrastructure.adapter.out.ddragon;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.your.summoner.processor.application.port.out.ItemCatalogSource;
import com.your.summoner.processor.domain.model.ItemCatalog;
import com.your.summoner.processor.domain.model.ItemInfo;
import com.your.summoner.processor.domain.model.PatchVersion;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

@Component
public final class DataDragonItemCatalogSource implements ItemCatalogSource {
    private final DataDragonClient client;
    private final Path cacheRoot;
    private final JsonMapper mapper = JsonMapper.builder().build();

    @Autowired
    public DataDragonItemCatalogSource(DataDragonClient client,
            @Value("${processor.ddragon.cache-directory:${user.home}/.yoursummoner/processor/ddragon}") String cacheRoot) {
        this(client, Path.of(cacheRoot));
    }

    public DataDragonItemCatalogSource(DataDragonClient client, Path cacheRoot) {
        this.client = client;
        this.cacheRoot = cacheRoot;
    }

    @Override public synchronized ItemCatalog forPatch(PatchVersion patch) {
        var file = cacheRoot.resolve(patch.toString()).resolve("item.json");
        try {
            if (Files.isRegularFile(file)) return parse(Files.readString(file));
            String version = Arrays.stream(mapper.readValue(client.versions(), String[].class))
                    .filter(v -> v.matches("\\d+\\.\\d+\\.\\d+") && v.startsWith(patch + "."))
                    .max((left, right) -> Integer.compare(revision(left), revision(right)))
                    .orElseThrow(() -> new IllegalStateException("No Data Dragon version for patch " + patch));
            String json = client.items(version);
            ItemCatalog catalog = parse(json);
            Files.createDirectories(file.getParent());
            Path temporary = Files.createTempFile(file.getParent(), "item-", ".json");
            try {
                Files.writeString(temporary, json);
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
            } finally {
                Files.deleteIfExists(temporary);
            }
            return catalog;
        } catch (IOException exception) {
            throw new UncheckedIOException("Cannot access Data Dragon cache for " + patch, exception);
        }
    }

    private static int revision(String version) { return Integer.parseInt(version.substring(version.lastIndexOf('.') + 1)); }

    private ItemCatalog parse(String json) {
        var response = mapper.readValue(json, ItemsResponse.class);
        return new ItemCatalog(response.data().entrySet().stream().map(entry -> {
            var item = entry.getValue();
            var tags = item.tags() == null ? List.<String>of() : item.tags();
            return new ItemInfo(Integer.parseInt(entry.getKey()), item.gold().total(), tags.contains("Boots"),
                    tags.contains("Consumable"), ids(item.from()), ids(item.into()));
        }).toList());
    }

    private static List<Integer> ids(List<String> ids) {
        return ids == null ? List.of() : ids.stream().map(Integer::parseInt).toList();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ItemsResponse(Map<String, ItemData> data) {}
    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ItemData(Gold gold, List<String> tags, List<String> from, List<String> into) {}
    @JsonIgnoreProperties(ignoreUnknown = true)
    private record Gold(int total) {}
}

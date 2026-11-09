package com.your.summoner.processor.infrastructure.config;

import com.your.summoner.processor.application.service.ChampionProfileAggregator.Correction;
import java.io.IOException;
import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
public final class ChampionCorrections {
    private static final String RESOURCE = "correcoes-campeoes.json";
    private final Map<Integer, Correction> corrections;

    public ChampionCorrections(ObjectMapper mapper) {
        try (var input = new ClassPathResource(RESOURCE).getInputStream()) {
            corrections = Arrays.stream(mapper.readValue(input, Entry[].class))
                    .collect(Collectors.toUnmodifiableMap(Entry::championId,
                            entry -> new Correction(entry.tags().add(), entry.tags().remove())));
        } catch (IOException | RuntimeException exception) {
            throw new IllegalStateException("Could not read champion corrections " + RESOURCE, exception);
        }
    }

    public Map<Integer, Correction> values() { return corrections; }

    record Entry(int championId, Tags tags, String nota) {}
    record Tags(java.util.List<String> add, java.util.List<String> remove) {
        public Tags {
            add = add == null ? java.util.List.of() : java.util.List.copyOf(add);
            remove = remove == null ? java.util.List.of() : java.util.List.copyOf(remove);
        }
    }
}

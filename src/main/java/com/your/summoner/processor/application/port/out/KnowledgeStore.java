package com.your.summoner.processor.application.port.out;

import com.your.summoner.processor.application.service.ParticipantExtractor.DecisionSample;
import com.your.summoner.processor.domain.model.ChampionKnowledge;
import com.your.summoner.processor.domain.model.PatchVersion;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface KnowledgeStore {
    void publish(Snapshot snapshot);
    Optional<Snapshot> find(PatchVersion patch);
    default Optional<Snapshot> findPublished(PatchVersion patch) {
        return find(patch).map(snapshot -> new Snapshot(snapshot.patch(), snapshot.computedAt(), snapshot.matches(),
                snapshot.ignoredBySchema(), snapshot.durationMillis(), snapshot.entries(), List.of()));
    }
    List<PatchVersion> patches();
    void remove(PatchVersion patch);

    record Snapshot(PatchVersion patch, Instant computedAt, long matches, long ignoredBySchema,
                    long durationMillis, List<ChampionKnowledge> entries, List<Decision> decisions) {
        public Snapshot {
            entries = List.copyOf(entries);
            decisions = List.copyOf(decisions);
        }
    }

    record Decision(int championId, String position, DecisionSample sample) {}
}

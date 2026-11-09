package com.your.summoner.processor.application.port.out;

import com.your.summoner.processor.application.service.ParticipantExtractor.DecisionSample;
import com.your.summoner.processor.domain.model.ChampionKnowledge;
import com.your.summoner.processor.domain.model.ChampionProfile;
import com.your.summoner.processor.domain.model.PatchVersion;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface KnowledgeStore {
    void publish(Snapshot snapshot);
    Optional<Snapshot> find(PatchVersion patch);
    default Optional<Snapshot> findPublished(PatchVersion patch) {
        return find(patch).map(snapshot -> new Snapshot(snapshot.patch(), snapshot.computedAt(), snapshot.matches(),
                snapshot.ignoredBySchema(), snapshot.durationMillis(), snapshot.entries(), List.of(), snapshot.championProfiles()));
    }
    /** Metadata only (ETag, status): adapters should override this to avoid loading the entries. */
    default Optional<Summary> summary(PatchVersion patch) {
        return findPublished(patch).map(snapshot -> new Summary(snapshot.patch(), snapshot.computedAt(),
                snapshot.matches(), snapshot.ignoredBySchema(), snapshot.durationMillis(), snapshot.entries().size()));
    }
    /** One published entry; adapters should override this to avoid loading the whole package. */
    default Optional<ChampionKnowledge> findEntry(PatchVersion patch, int championId, String position) {
        return findPublished(patch).flatMap(snapshot -> snapshot.entries().stream()
                .filter(entry -> entry.championId() == championId && entry.position().equals(position)).findFirst());
    }
    List<PatchVersion> patches();
    void remove(PatchVersion patch);

    record Summary(PatchVersion patch, Instant computedAt, long matches, long ignoredBySchema,
                   long durationMillis, int entries) {}

    record Snapshot(PatchVersion patch, Instant computedAt, long matches, long ignoredBySchema,
                    long durationMillis, List<ChampionKnowledge> entries, List<Decision> decisions,
                    List<ChampionProfile> championProfiles) {
        public Snapshot(PatchVersion patch, Instant computedAt, long matches, long ignoredBySchema,
                        long durationMillis, List<ChampionKnowledge> entries, List<Decision> decisions) {
            this(patch, computedAt, matches, ignoredBySchema, durationMillis, entries, decisions, List.of());
        }
        public Snapshot {
            entries = List.copyOf(entries);
            decisions = List.copyOf(decisions);
            championProfiles = List.copyOf(championProfiles);
        }
    }

    record Decision(int championId, String position, DecisionSample sample) {}
}
